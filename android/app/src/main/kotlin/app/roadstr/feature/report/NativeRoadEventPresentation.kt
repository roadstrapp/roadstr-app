package app.roadstr.feature.report

import app.roadstr.core.format.UnitFormatter
import app.roadstr.core.protocol.nostr.NostrNip19
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.map.NativeMapPointOverlayKind
import java.util.Collections
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeRoadEventSurface { Hidden, Detail, PrivacyNotice, Composer }

enum class NativeRoadEventAgeUnit { Minutes, Hours, Days }

data class NativeRoadEventAge(
    val value: Long,
    val unit: NativeRoadEventAgeUnit,
)

data class NativeRoadEventEditRequestInput(
    val id: String,
    val eventId: String,
    val requesterPubkey: String,
    val speedLimitKmh: Int,
    val comment: String = "",
    val createdAtSeconds: Long,
)

data class NativeRoadEventEditRequestPresentation(
    val id: String,
    val requesterLabel: String,
    val speedLimitKmh: Int,
    val speedLimit: Int,
    val speedUnit: String,
)

data class NativeRoadEventInput(
    val id: String,
    val pubkey: String,
    val category: RoadCategoryWire,
    val latitude: Double,
    val longitude: Double,
    val comment: String,
    val createdAtSeconds: Long,
    val expiresAtSeconds: Long? = null,
    val speedLimitKmh: Int? = null,
    val confirmations: Int = 0,
    val denials: Int = 0,
    val zapSats: Long = 0,
    val viewerPubkey: String? = null,
    val reporterPublic: Boolean = false,
    val reporterLabel: String? = null,
    val editRequests: List<NativeRoadEventEditRequestInput> = emptyList(),
)

data class NativeRoadEventDetail(
    val id: String,
    val pubkey: String,
    val reporterNpub: String,
    val category: RoadCategoryWire,
    val markerKind: NativeMapPointOverlayKind,
    val latitude: Double,
    val longitude: Double,
    val comment: String,
    val age: NativeRoadEventAge,
    val speedLimitKmh: Int?,
    val speedLimit: Int?,
    val speedUnit: String,
    val confirmations: Int,
    val denials: Int,
    val zapSats: Long,
    val loggedIn: Boolean,
    val owner: Boolean,
    val reporterPublic: Boolean,
    val reporterLabel: String?,
    val editRequests: List<NativeRoadEventEditRequestPresentation>,
)

data class NativeRoadEventDraft(
    val latitude: Double,
    val longitude: Double,
    val category: RoadCategoryWire?,
    val comment: String,
    val speedInput: String,
    val speedUnit: String,
    val imperial: Boolean,
    val submitting: Boolean,
)

data class NativeRoadEventSubmission(
    val revision: Long,
    val latitude: Double,
    val longitude: Double,
    val category: RoadCategoryWire,
    val comment: String,
    val speedLimitKmh: Int?,
    val expiresAtSeconds: Long,
)

data class NativeRoadEventSnapshot(
    val revision: Long,
    val surface: NativeRoadEventSurface,
    val detail: NativeRoadEventDetail?,
    val draft: NativeRoadEventDraft?,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden(revision: Long = NO_REVISION) = NativeRoadEventSnapshot(
            revision = revision,
            surface = NativeRoadEventSurface.Hidden,
            detail = null,
            draft = null,
        )
    }
}

/** Pure Flutter-parity projection for road-event details and report drafts. */
object NativeRoadEventPresenter {
    const val MAX_RECEIVED_COMMENT = 500
    const val MAX_REPORT_COMMENT = 200
    const val MAX_EDIT_REQUESTS = 100
    const val MAX_SPEED_INPUT = 3
    const val MAX_PROFILE_LABEL = 200
    const val MAX_COUNTER = 1_000_000_000
    private const val MAX_FUTURE_SKEW_SECONDS = 300L
    private val HEX_64 = Regex("^[0-9a-f]{64}$")
    private val CONTROLS = Regex("[\\u0000-\\u001f]")

    fun detail(
        input: NativeRoadEventInput,
        nowSeconds: Long,
        imperial: Boolean,
    ): NativeRoadEventDetail {
        require(nowSeconds >= 0) { "Current time must be non-negative" }
        require(HEX_64.matches(input.id)) { "Road-event id must be lowercase hex" }
        require(HEX_64.matches(input.pubkey)) { "Road-event pubkey must be lowercase hex" }
        input.viewerPubkey?.let {
            require(HEX_64.matches(it)) { "Viewer pubkey must be lowercase hex" }
        }
        requirePoint(input.latitude, input.longitude)
        require(input.createdAtSeconds >= 0) { "Road-event timestamp must be non-negative" }
        require(input.createdAtSeconds <= nowSeconds + MAX_FUTURE_SKEW_SECONDS) {
            "Road-event timestamp is too far in the future"
        }
        input.expiresAtSeconds?.let {
            require(it >= 0) { "Road-event expiration must be non-negative" }
        }
        require(!expired(input, nowSeconds)) { "Road event is expired" }
        require(input.confirmations in 0..MAX_COUNTER) { "Invalid confirmation count" }
        require(input.denials in 0..MAX_COUNTER) { "Invalid denial count" }
        require(input.zapSats in 0..MAX_COUNTER.toLong()) { "Invalid zap total" }
        input.speedLimitKmh?.let { require(it in 1..300) { "Invalid speed limit" } }
        require(input.editRequests.size <= MAX_EDIT_REQUESTS) { "Too many edit requests" }

        val formatter = UnitFormatter(imperial)
        val owner = input.viewerPubkey != null && input.viewerPubkey == input.pubkey
        val requests = if (owner) {
            input.editRequests.mapNotNull { request ->
                editRequest(request, input.id, formatter)
            }
        } else {
            emptyList()
        }
        val profileLabel = clean(input.reporterLabel, MAX_PROFILE_LABEL)
        return NativeRoadEventDetail(
            id = input.id,
            pubkey = input.pubkey,
            reporterNpub = NostrNip19.encodePublicKey(input.pubkey),
            category = input.category,
            markerKind = markerKind(input.category),
            latitude = input.latitude,
            longitude = input.longitude,
            comment = truncateReceivedComment(input.comment),
            age = age(input.createdAtSeconds, nowSeconds),
            speedLimitKmh = input.speedLimitKmh,
            speedLimit = input.speedLimitKmh?.let {
                formatter.toDisplaySpeed(it.toDouble()).roundToInt()
            },
            speedUnit = formatter.speedUnit,
            confirmations = input.confirmations,
            denials = input.denials,
            zapSats = input.zapSats,
            loggedIn = input.viewerPubkey != null,
            owner = owner,
            reporterPublic = input.reporterPublic,
            reporterLabel = profileLabel,
            editRequests = immutable(requests),
        )
    }

    fun newDraft(
        latitude: Double,
        longitude: Double,
        imperial: Boolean,
    ): NativeRoadEventDraft {
        requirePoint(latitude, longitude)
        return NativeRoadEventDraft(
            latitude = latitude,
            longitude = longitude,
            category = null,
            comment = "",
            speedInput = "",
            speedUnit = UnitFormatter(imperial).speedUnit,
            imperial = imperial,
            submitting = false,
        )
    }

    fun submission(
        revision: Long,
        draft: NativeRoadEventDraft,
        nowSeconds: Long,
    ): NativeRoadEventSubmission? {
        if (draft.submitting || nowSeconds < 0) return null
        val category = draft.category ?: return null
        val rawSpeed = draft.speedInput.trim().toIntOrNull()
            ?.takeIf { it in 1..300 }
        val speedKmh = if (category == RoadCategoryWire.SPEED_CAMERA) {
            rawSpeed?.let { if (draft.imperial) (it * 1.60934).roundToInt() else it }
        } else {
            null
        }
        return NativeRoadEventSubmission(
            revision = revision,
            latitude = draft.latitude,
            longitude = draft.longitude,
            category = category,
            comment = draft.comment.trim(),
            speedLimitKmh = speedKmh,
            expiresAtSeconds = safeClientExpiration(nowSeconds, category.ttlSeconds),
        )
    }

    fun age(createdAtSeconds: Long, nowSeconds: Long): NativeRoadEventAge {
        val minutes = max(0L, (nowSeconds - createdAtSeconds) / 60L)
        return when {
            minutes < 60 -> NativeRoadEventAge(minutes, NativeRoadEventAgeUnit.Minutes)
            minutes < 1_440 -> NativeRoadEventAge(minutes / 60, NativeRoadEventAgeUnit.Hours)
            else -> NativeRoadEventAge(minutes / 1_440, NativeRoadEventAgeUnit.Days)
        }
    }

    fun markerKind(category: RoadCategoryWire): NativeMapPointOverlayKind = when (category) {
        RoadCategoryWire.POLICE -> NativeMapPointOverlayKind.RoadPolice
        RoadCategoryWire.POLICE_STATION -> NativeMapPointOverlayKind.RoadPoliceStation
        RoadCategoryWire.SPEED_CAMERA -> NativeMapPointOverlayKind.RoadSpeedCamera
        RoadCategoryWire.TRAFFIC_JAM -> NativeMapPointOverlayKind.RoadTrafficJam
        RoadCategoryWire.ACCIDENT -> NativeMapPointOverlayKind.RoadAccident
        RoadCategoryWire.ROAD_CLOSURE -> NativeMapPointOverlayKind.RoadClosure
        RoadCategoryWire.CONSTRUCTION -> NativeMapPointOverlayKind.RoadConstruction
        RoadCategoryWire.HAZARD -> NativeMapPointOverlayKind.RoadHazard
        RoadCategoryWire.ROAD_CONDITION -> NativeMapPointOverlayKind.RoadCondition
        RoadCategoryWire.POTHOLE -> NativeMapPointOverlayKind.RoadPothole
        RoadCategoryWire.FOG -> NativeMapPointOverlayKind.RoadFog
        RoadCategoryWire.ICE -> NativeMapPointOverlayKind.RoadIce
        RoadCategoryWire.ANIMAL -> NativeMapPointOverlayKind.RoadAnimal
        RoadCategoryWire.OTHER -> NativeMapPointOverlayKind.RoadOther
    }

    fun cleanDraftComment(value: String): String = value.take(MAX_REPORT_COMMENT)

    fun cleanSpeedInput(value: String): String = value.take(MAX_SPEED_INPUT)

    private fun editRequest(
        request: NativeRoadEventEditRequestInput,
        eventId: String,
        formatter: UnitFormatter,
    ): NativeRoadEventEditRequestPresentation? {
        if (!HEX_64.matches(request.id) || request.eventId != eventId) return null
        if (!HEX_64.matches(request.requesterPubkey)) return null
        if (request.speedLimitKmh !in 5..300 || request.createdAtSeconds < 0) return null
        return NativeRoadEventEditRequestPresentation(
            id = request.id,
            requesterLabel = "${request.requesterPubkey.take(8)}…",
            speedLimitKmh = request.speedLimitKmh,
            speedLimit = formatter.toDisplaySpeed(request.speedLimitKmh.toDouble()).roundToInt(),
            speedUnit = formatter.speedUnit,
        )
    }

    private fun expired(input: NativeRoadEventInput, nowSeconds: Long): Boolean =
        nowSeconds >= safeClientExpiration(input.createdAtSeconds, input.category.ttlSeconds) ||
            (input.expiresAtSeconds != null && nowSeconds >= input.expiresAtSeconds)

    private fun safeClientExpiration(createdAtSeconds: Long, ttlSeconds: Int): Long =
        if (createdAtSeconds > Long.MAX_VALUE - ttlSeconds) {
            Long.MAX_VALUE
        } else {
            createdAtSeconds + ttlSeconds
        }

    private fun truncateReceivedComment(value: String): String =
        if (value.length > MAX_RECEIVED_COMMENT) {
            "${value.substring(0, MAX_RECEIVED_COMMENT)}…"
        } else {
            value
        }

    private fun clean(value: String?, maxLength: Int): String? {
        val result = value?.replace(CONTROLS, " ")?.trim()?.take(maxLength) ?: return null
        return result.takeIf(String::isNotEmpty)
    }

    private fun requirePoint(latitude: Double, longitude: Double) {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }

    private fun <T> immutable(values: List<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}

/** Revision-fenced state holder with no signer, relay, wallet, GPS or persistence adapter. */
class NativeRoadEventSession(
    private var imperial: Boolean = false,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeRoadEventSnapshot.hidden())
    private var revision = NativeRoadEventSnapshot.NO_REVISION

    val state: StateFlow<NativeRoadEventSnapshot> = _state.asStateFlow()

    fun showDetail(
        revision: Long,
        input: NativeRoadEventInput,
        nowSeconds: Long,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Road-event revision must be non-negative" }
        if (revision <= this.revision) return false
        val detail = NativeRoadEventPresenter.detail(input, nowSeconds, imperial)
        this.revision = revision
        _state.value = NativeRoadEventSnapshot(
            revision,
            NativeRoadEventSurface.Detail,
            detail,
            null,
        )
        true
    }

    fun showComposer(
        revision: Long,
        latitude: Double,
        longitude: Double,
        privacyAcknowledged: Boolean,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Road-event revision must be non-negative" }
        if (revision <= this.revision) return false
        val draft = NativeRoadEventPresenter.newDraft(latitude, longitude, imperial)
        this.revision = revision
        _state.value = NativeRoadEventSnapshot(
            revision,
            if (privacyAcknowledged) {
                NativeRoadEventSurface.Composer
            } else {
                NativeRoadEventSurface.PrivacyNotice
            },
            null,
            draft,
        )
        true
    }

    fun acceptPrivacy(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision, NativeRoadEventSurface.PrivacyNotice)) return false
        _state.value = _state.value.copy(surface = NativeRoadEventSurface.Composer)
        true
    }

    fun selectCategory(revision: Long, category: RoadCategoryWire): Boolean = synchronized(lock) {
        val draft = editableDraft(revision) ?: return false
        if (draft.category == category) return false
        _state.value = _state.value.copy(
            draft = draft.copy(
                category = category,
                speedInput = if (category == RoadCategoryWire.SPEED_CAMERA) {
                    draft.speedInput
                } else {
                    ""
                },
            ),
        )
        true
    }

    fun updateComment(revision: Long, value: String): Boolean = synchronized(lock) {
        val draft = editableDraft(revision) ?: return false
        val clean = NativeRoadEventPresenter.cleanDraftComment(value)
        if (draft.comment == clean) return false
        _state.value = _state.value.copy(draft = draft.copy(comment = clean))
        true
    }

    fun updateSpeed(revision: Long, value: String): Boolean = synchronized(lock) {
        val draft = editableDraft(revision) ?: return false
        if (draft.category != RoadCategoryWire.SPEED_CAMERA) return false
        val clean = NativeRoadEventPresenter.cleanSpeedInput(value)
        if (draft.speedInput == clean) return false
        _state.value = _state.value.copy(draft = draft.copy(speedInput = clean))
        true
    }

    fun beginSubmission(revision: Long, nowSeconds: Long): NativeRoadEventSubmission? =
        synchronized(lock) {
            val draft = editableDraft(revision) ?: return null
            val submission = NativeRoadEventPresenter.submission(revision, draft, nowSeconds)
                ?: return null
            _state.value = _state.value.copy(draft = draft.copy(submitting = true))
            submission
        }

    fun submissionFailed(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision, NativeRoadEventSurface.Composer)) return false
        val draft = _state.value.draft ?: return false
        if (!draft.submitting) return false
        _state.value = _state.value.copy(draft = draft.copy(submitting = false))
        true
    }

    fun submissionAccepted(revision: Long): Boolean = hide(revision)

    fun updateUnits(imperial: Boolean): Boolean = synchronized(lock) {
        if (this.imperial == imperial) return false
        val draft = _state.value.draft
        if (draft?.submitting == true) return false
        this.imperial = imperial
        val detail = _state.value.detail
        if (draft != null) {
            _state.value = _state.value.copy(
                draft = draft.copy(
                    speedInput = "",
                    speedUnit = UnitFormatter(imperial).speedUnit,
                    imperial = imperial,
                ),
            )
        } else if (detail != null) {
            val formatter = UnitFormatter(imperial)
            _state.value = _state.value.copy(
                detail = detail.copy(
                    speedLimit = detail.speedLimitKmh?.let {
                        formatter.toDisplaySpeed(it.toDouble()).roundToInt()
                    },
                    speedUnit = formatter.speedUnit,
                    editRequests = Collections.unmodifiableList(
                        detail.editRequests.map { request ->
                            request.copy(
                                speedLimit = formatter.toDisplaySpeed(
                                    request.speedLimitKmh.toDouble(),
                                ).roundToInt(),
                                speedUnit = formatter.speedUnit,
                            )
                        },
                    ),
                ),
            )
        }
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.surface == NativeRoadEventSurface.Hidden) {
            return false
        }
        _state.value = NativeRoadEventSnapshot.hidden(revision)
        true
    }

    private fun active(value: Long, surface: NativeRoadEventSurface): Boolean =
        value == revision && _state.value.surface == surface

    private fun editableDraft(value: Long): NativeRoadEventDraft? {
        if (!active(value, NativeRoadEventSurface.Composer)) return null
        return _state.value.draft?.takeUnless { it.submitting }
    }
}
