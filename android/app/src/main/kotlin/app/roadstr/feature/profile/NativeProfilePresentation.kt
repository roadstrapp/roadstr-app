package app.roadstr.feature.profile

import app.roadstr.core.protocol.nostr.NostrNip19
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.report.NativeRoadEventAge
import app.roadstr.feature.report.NativeRoadEventPresenter
import java.net.URI
import java.util.Collections
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeProfileStatus {
    Hidden,
    Loading,
    LoggedOut,
    Ready,
}

enum class NativeProfileIdentityFlavor(val wireValue: String) {
    Amber("amber"),
    Bunker("bunker");

    companion object {
        fun fromWire(value: String?): NativeProfileIdentityFlavor? = entries.firstOrNull {
            it.wireValue == value?.trim()?.lowercase()
        }
    }
}

enum class NativeProfileReputationLevel { Low, Medium, High }

data class NativeProfileReportInput(
    val id: String,
    val category: RoadCategoryWire,
    val createdAtSeconds: Long,
    val address: String? = null,
    val comment: String = "",
    val confirmations: Int = 0,
    val denials: Int = 0,
    val zapMsat: Long = 0,
)

data class NativeProfileReportPresentation(
    val id: String,
    val category: RoadCategoryWire,
    val createdAtSeconds: Long,
    val age: NativeRoadEventAge,
    val address: String?,
    val comment: String?,
    val confirmations: Int,
    val denials: Int,
    val reliabilityPercent: Int?,
    val zapSats: Long,
)

data class NativeProfileInput(
    val pubkeyHex: String,
    val ownProfile: Boolean,
    val profilePublic: Boolean,
    val flavor: NativeProfileIdentityFlavor? = null,
    val displayName: String? = null,
    val name: String? = null,
    val pictureUrl: String? = null,
    val reports: List<NativeProfileReportInput> = emptyList(),
    val reportsLoading: Boolean = false,
    val balanceMsat: Long? = null,
)

data class NativeProfileSnapshot(
    val revision: Long,
    val status: NativeProfileStatus,
    val ownProfile: Boolean,
    val profilePublic: Boolean,
    val waitingAmber: Boolean,
    val showPublicProfile: Boolean,
    val npub: String?,
    val displayName: String?,
    val pictureUrl: String?,
    val flavor: NativeProfileIdentityFlavor?,
    val reputationPercent: Int?,
    val reputationLevel: NativeProfileReputationLevel?,
    val balanceSats: Long?,
    val reportsLoading: Boolean,
    val reports: List<NativeProfileReportPresentation>,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden(revision: Long = NO_REVISION) = NativeProfileSnapshot(
            revision = revision,
            status = NativeProfileStatus.Hidden,
            ownProfile = true,
            profilePublic = false,
            waitingAmber = false,
            showPublicProfile = false,
            npub = null,
            displayName = null,
            pictureUrl = null,
            flavor = null,
            reputationPercent = null,
            reputationLevel = null,
            balanceSats = null,
            reportsLoading = false,
            reports = emptyList(),
        )
    }
}

/** Pure, bounded projection of Flutter's profile, report and reputation state. */
object NativeProfilePresenter {
    const val MAX_PROFILE_LABEL_CHARS = 200
    const val MAX_PICTURE_URL_CHARS = 2_048
    const val MAX_ADDRESS_CHARS = 1_000
    const val MAX_COMMENT_CHARS = 500
    const val MAX_REPORTS = 100
    const val MAX_FETCHED_REPORTS = 500
    const val MAX_COUNTER = 1_000_000_000
    const val MAX_BALANCE_MSAT = 2_100_000_000_000_000_000L
    private const val MAX_FUTURE_SKEW_SECONDS = 300L
    private val HEX_64 = Regex("^[0-9a-f]{64}$")
    private val CONTROLS = Regex("[\\u0000-\\u001f]")

    fun present(
        revision: Long,
        input: NativeProfileInput,
        nowSeconds: Long,
    ): NativeProfileSnapshot {
        require(revision >= 0) { "Profile revision must be non-negative" }
        require(nowSeconds >= 0) { "Current time must be non-negative" }
        require(HEX_64.matches(input.pubkeyHex)) { "Profile pubkey must be lowercase hex" }
        require(input.reports.size <= MAX_FETCHED_REPORTS) { "Too many profile reports" }
        require(input.balanceMsat == null || input.balanceMsat in 0..MAX_BALANCE_MSAT) {
            "Invalid profile balance"
        }
        if (input.ownProfile) {
            require(input.flavor != null) { "Own profile requires an identity flavor" }
        } else {
            require(input.flavor == null) { "Remote profile must not expose a local identity flavor" }
        }

        val showPublicProfile = input.ownProfile || input.profilePublic
        if (!showPublicProfile) {
            return NativeProfileSnapshot.hidden(revision).copy(
                status = NativeProfileStatus.Ready,
                ownProfile = false,
                profilePublic = false,
            )
        }

        require(input.reports.map(NativeProfileReportInput::id).toSet().size == input.reports.size) {
            "Duplicate profile report id"
        }
        val projectedReports = input.reports.map { report(it, nowSeconds) }
        val reports = projectedReports
            .sortedByDescending(NativeProfileReportPresentation::createdAtSeconds)
            .take(MAX_REPORTS)
        val confirmations = projectedReports.sumOf { it.confirmations.toLong() }
        val denials = projectedReports.sumOf { it.denials.toLong() }
        val total = confirmations + denials
        val reputationPercent = if (total > 0) {
            dartRound(confirmations.toDouble() * 100.0 / total.toDouble())
        } else {
            null
        }
        val label = clean(input.displayName, MAX_PROFILE_LABEL_CHARS)
            ?: clean(input.name, MAX_PROFILE_LABEL_CHARS)

        return NativeProfileSnapshot(
            revision = revision,
            status = NativeProfileStatus.Ready,
            ownProfile = input.ownProfile,
            profilePublic = input.profilePublic,
            waitingAmber = false,
            showPublicProfile = true,
            npub = NostrNip19.encodePublicKey(input.pubkeyHex),
            displayName = label,
            pictureUrl = safePictureUrl(input.pictureUrl),
            flavor = input.flavor,
            reputationPercent = reputationPercent,
            reputationLevel = if (total > 0) {
                reputationLevel(confirmations.toDouble() / total.toDouble())
            } else {
                null
            },
            balanceSats = input.balanceMsat?.div(1_000),
            reportsLoading = input.reportsLoading,
            reports = immutable(reports),
        )
    }

    fun reputationLevel(score: Double): NativeProfileReputationLevel {
        require(score.isFinite() && score in 0.0..1.0) {
            "Reputation score must be between zero and one"
        }
        return when {
            score >= 0.67 -> NativeProfileReputationLevel.High
            score >= 0.34 -> NativeProfileReputationLevel.Medium
            else -> NativeProfileReputationLevel.Low
        }
    }

    private fun report(
        input: NativeProfileReportInput,
        nowSeconds: Long,
    ): NativeProfileReportPresentation {
        require(HEX_64.matches(input.id)) { "Profile report id must be lowercase hex" }
        require(input.createdAtSeconds >= 0) { "Profile report timestamp must be non-negative" }
        require(input.createdAtSeconds <= nowSeconds + MAX_FUTURE_SKEW_SECONDS) {
            "Profile report timestamp is too far in the future"
        }
        require(input.confirmations in 0..MAX_COUNTER) { "Invalid confirmation count" }
        require(input.denials in 0..MAX_COUNTER) { "Invalid denial count" }
        require(input.zapMsat in 0..MAX_BALANCE_MSAT) { "Invalid report zap total" }
        val total = input.confirmations.toLong() + input.denials.toLong()
        return NativeProfileReportPresentation(
            id = input.id,
            category = input.category,
            createdAtSeconds = input.createdAtSeconds,
            age = NativeRoadEventPresenter.age(input.createdAtSeconds, nowSeconds),
            address = clean(input.address, MAX_ADDRESS_CHARS),
            comment = clean(input.comment, MAX_COMMENT_CHARS),
            confirmations = input.confirmations,
            denials = input.denials,
            reliabilityPercent = if (total > 0) {
                dartRound(input.confirmations.toDouble() * 100.0 / total.toDouble())
            } else {
                null
            },
            zapSats = input.zapMsat / 1_000,
        )
    }

    private fun safePictureUrl(value: String?): String? {
        val normalized = clean(value, MAX_PICTURE_URL_CHARS) ?: return null
        return try {
            URI(normalized).takeIf { uri ->
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }?.toASCIIString()
        } catch (_: Exception) {
            null
        }
    }

    private fun clean(value: String?, limit: Int): String? = value
        ?.replace(CONTROLS, " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(limit)

    private fun dartRound(value: Double): Int = floor(value + 0.5).toInt()

    private fun <T> immutable(values: List<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}

/**
 * Revision-safe in-memory coordinator for the dormant native profile panel.
 *
 * It deliberately retains no nsec, signer, relay, secure-storage, clipboard,
 * reverse-geocoder, image-loader or wallet owner.
 */
class NativeProfileSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeProfileSnapshot.hidden())
    private var revision = NativeProfileSnapshot.NO_REVISION

    val state: StateFlow<NativeProfileSnapshot> = _state.asStateFlow()

    fun begin(revision: Long, ownProfile: Boolean): Boolean = synchronized(lock) {
        require(revision >= 0) { "Profile revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        _state.value = NativeProfileSnapshot.hidden(revision).copy(
            status = NativeProfileStatus.Loading,
            ownProfile = ownProfile,
        )
        true
    }

    fun showLoggedOut(revision: Long, waitingAmber: Boolean = false): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || !current.ownProfile) return false
        if (current.status !in setOf(NativeProfileStatus.Loading, NativeProfileStatus.LoggedOut)) {
            return false
        }
        _state.value = NativeProfileSnapshot.hidden(revision).copy(
            status = NativeProfileStatus.LoggedOut,
            ownProfile = true,
            waitingAmber = waitingAmber,
        )
        true
    }

    fun setWaitingAmber(revision: Long, waiting: Boolean): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeProfileStatus.LoggedOut) return false
        if (current.waitingAmber == waiting) return false
        _state.value = current.copy(waitingAmber = waiting)
        true
    }

    fun showProfile(
        revision: Long,
        input: NativeProfileInput,
        nowSeconds: Long,
    ): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeProfileStatus.Hidden) return false
        require(input.ownProfile == _state.value.ownProfile) { "Profile ownership changed mid-request" }
        _state.value = NativeProfilePresenter.present(revision, input, nowSeconds)
        true
    }

    fun updateVisibility(
        revision: Long,
        profilePublic: Boolean,
    ): Boolean = synchronized(lock) {
        val current = _state.value
        if (
            revision != this.revision ||
            current.status != NativeProfileStatus.Ready ||
            !current.ownProfile ||
            current.profilePublic == profilePublic
        ) {
            return false
        }
        _state.value = current.copy(profilePublic = profilePublic)
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeProfileStatus.Hidden) return false
        _state.value = NativeProfileSnapshot.hidden(revision)
        true
    }
}
