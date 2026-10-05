package app.roadstr.feature.home

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.report.NativeRoadEventEditRequestInput
import app.roadstr.feature.report.NativeRoadEventInput
import app.roadstr.feature.report.NativeRoadEventPresenter
import app.roadstr.feature.report.NativeRoadEventSession
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.service.nostr.NativeFavoritesCrypto
import app.roadstr.service.nostr.NativeFavoritesPull
import app.roadstr.service.nostr.NativeNostrWire
import app.roadstr.service.nostr.NativeReportOutcome
import app.roadstr.service.nostr.NativeRoadEvent
import app.roadstr.service.nostr.NativeRoadEventCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the user is told after a background or one-off Nostr action. */
enum class NativeShellMessage {
    SyncSuccess,
    SyncFailed,
    SyncNotFound,
    SyncNothingToPublish,
    ExportSuccess,
    ExportFailed,
    ImportSuccess,
    ImportFailed,
    ReportPublished,
    ReportQueued,
    ReportFailed,
    SigningFailed,
    VoteFailed,
    LoginRequired,
    VoteLoginRequired,
    VisibilityFailed,
    SpeedUpdateSent,
    EditRequestSent,
    SpeedUpdateFailed,
    ZapSent,
}

/**
 * Favourites sync, favourites files and the privacy choice, driven from the
 * settings screen. Plain logic with injected callbacks so it is testable
 * without Compose.
 */
class NativeShellFavoritesController(
    private val nostr: NativeShellNostr,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val message: (NativeShellMessage, Int) -> Unit,
    private val setBusy: (Boolean) -> Unit,
    private val favorites: () -> List<NativeSavedPlace>,
    /** Merges incoming places into the stored list and persists. Called off no thread in particular. */
    private val mergeFavorites: (List<NativeSavedPlace>) -> Unit,
    private val promptPassword: suspend () -> String?,
) {
    @Volatile
    private var busy = false

    fun push() {
        if (busy || nostr.signer.pubkeyHex == null) return
        // The snapshot is authoritative: publishing an empty list from a device
        // that simply has not pulled yet would wipe the one stored on the relays.
        if (favorites().isEmpty()) {
            message(NativeShellMessage.SyncNothingToPublish, 0)
            return
        }
        busy = true
        setBusy(true)
        scope.launch {
            val ok = try {
                nostr.favoritesSync.push(maps(favorites()))
            } finally {
                busy = false
                setBusy(false)
            }
            if (ok) nostr.syncSecrets.markSynced(nowMillis())
            message(if (ok) NativeShellMessage.SyncSuccess else NativeShellMessage.SyncFailed, 0)
        }
    }

    /**
     * Silent push after a local change, when the user turned auto-sync on. An
     * empty list is pushed like any other: the snapshot is authoritative, so
     * deleting the last favourite must replace the stored one rather than leave
     * it for the next reinstall to restore.
     */
    fun autoPush() {
        if (nostr.signer.pubkeyHex == null) return
        scope.launch {
            if (nostr.favoritesSync.push(maps(favorites()))) nostr.syncSecrets.markSynced(nowMillis())
        }
    }

    fun pull() {
        if (busy || nostr.signer.pubkeyHex == null) return
        busy = true
        setBusy(true)
        scope.launch {
            var found = true
            val merged = try {
                var result = nostr.favoritesSync.pull()
                found = result != NativeFavoritesPull.NotFound
                if (result == NativeFavoritesPull.Locked) {
                    // Sealed with a passphrase this device does not have (new
                    // device, or changed elsewhere): ask once and retry.
                    val entered = promptPassword()
                    if (!entered.isNullOrEmpty()) {
                        result = nostr.favoritesSync.pull(passphraseOverride = entered)
                        // Remember the one that worked so later syncs are seamless.
                        if (result is NativeFavoritesPull.Ok) nostr.syncSecrets.setPassphrase(entered)
                    }
                }
                (result as? NativeFavoritesPull.Ok)?.favorites?.mapNotNull(::placeFromMap)
            } finally {
                busy = false
                setBusy(false)
            }
            if (merged == null) {
                message(if (found) NativeShellMessage.SyncFailed else NativeShellMessage.SyncNotFound, 0)
                return@launch
            }
            mergeFavorites(merged)
            nostr.syncSecrets.markSynced(nowMillis())
            message(NativeShellMessage.SyncSuccess, 0)
        }
    }

    /** Best-effort restore at launch; never removes a local place, only adds or updates. */
    fun autoPull() {
        if (nostr.signer.pubkeyHex == null) return
        scope.launch {
            val result = nostr.favoritesSync.pull() as? NativeFavoritesPull.Ok ?: return@launch
            val incoming = result.favorites.mapNotNull(::placeFromMap)
            if (incoming.isNotEmpty()) mergeFavorites(incoming)
        }
    }

    /** [password] null or empty exports in clear; the file holds home and work, so the caller defaults to encrypting. */
    fun export(password: String?) {
        val list = favorites()
        if (list.isEmpty()) return
        scope.launch {
            val json = runCatching {
                val plaintext = NativeSavedPlacesProtocol.encodeImportPlaintext(list)
                val envelope: Map<String, Any?> = if (!password.isNullOrEmpty()) {
                    val sealed = withContext(Dispatchers.Default) { NativeFavoritesCrypto.encrypt(plaintext, password) }
                    linkedMapOf<String, Any?>("v" to 1, "encrypted" to true).also { it.putAll(sealed) }
                } else {
                    linkedMapOf("v" to 1, "encrypted" to false, "data" to plaintext)
                }
                NostrJson.encode(envelope)
            }.getOrNull()
            if (json == null) {
                message(NativeShellMessage.ExportFailed, 0)
                return@launch
            }
            nostr.files.export("roadstr_favorites.json", json)
        }
    }

    fun import(fileText: String) {
        scope.launch {
            val imported = runCatching {
                val envelope = NativeSavedPlacesProtocol.decodeImportEnvelope(fileText) ?: return@runCatching null
                val plaintext = if (envelope.encrypted) {
                    val password = promptPassword()
                    if (password.isNullOrEmpty()) return@launch
                    val fields = envelope.encryptedFields ?: return@runCatching null
                    withContext(Dispatchers.Default) { NativeFavoritesCrypto.decrypt(fields, password) }
                } else {
                    envelope.plaintext
                } ?: return@runCatching null
                NativeSavedPlacesProtocol.decodeImportPlaintext(plaintext)
            }.getOrNull()
            if (imported == null) {
                message(NativeShellMessage.ImportFailed, 0)
                return@launch
            }
            mergeFavorites(imported)
            message(NativeShellMessage.ImportSuccess, imported.size)
        }
    }

    private fun maps(places: List<NativeSavedPlace>): List<Map<String, Any?>> = places.map {
        linkedMapOf(
            "label" to it.label,
            "address" to it.address,
            "lat" to it.point.latitude,
            "lon" to it.point.longitude,
        )
    }

    private fun placeFromMap(map: Map<String, Any?>): NativeSavedPlace? {
        val label = map["label"] as? String ?: return null
        val latitude = (map["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (map["lon"] as? Number)?.toDouble() ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return NativeSavedPlacesProtocol.normalizeFavorite(
            NativeSavedPlace(label, map["address"] as? String ?: "", NativeMapPoint(latitude, longitude)),
        )
    }
}

/** Reports, votes and speed-limit corrections on community road events. */
class NativeShellReportController(
    private val nostr: NativeShellNostr,
    private val scope: CoroutineScope,
    private val session: NativeRoadEventSession,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val message: (NativeShellMessage, Int) -> Unit,
) {
    /** Opens the report composer at [point], after the one-time privacy notice. */
    fun openComposer(point: NativeMapPoint) {
        if (nostr.signer.pubkeyHex == null) {
            message(NativeShellMessage.LoginRequired, 0)
            return
        }
        session.showComposer(
            revision = session.state.value.revision.coerceAtLeast(0L) + 1L,
            latitude = point.latitude,
            longitude = point.longitude,
            privacyAcknowledged = nostr.reportPrivacyAcknowledged(),
        )
    }

    fun acceptPrivacy(revision: Long) {
        if (session.acceptPrivacy(revision)) nostr.acknowledgeReportPrivacy()
    }

    fun submit(revision: Long) {
        val submission = session.beginSubmission(revision, nowSeconds()) ?: return
        scope.launch {
            val pubkey = nostr.signer.pubkeyHex
            if (pubkey == null) {
                session.submissionFailed(revision)
                message(NativeShellMessage.LoginRequired, 0)
                return@launch
            }
            val now = nowSeconds()
            val draft = RoadstrNostrEvents.report(
                pubkey = pubkey,
                createdAt = now,
                latitude = submission.latitude,
                longitude = submission.longitude,
                category = submission.category.wireKey,
                expiresAt = submission.expiresAtSeconds,
                content = submission.comment,
                speedLimit = submission.speedLimitKmh,
            )
            val signed = nostr.signer.sign(draft)
            val event = signed?.let { NativeRoadEventCodec.parse(it, now) }
            if (signed == null || event == null) {
                session.submissionFailed(revision)
                message(NativeShellMessage.SigningFailed, 0)
                return@launch
            }
            when (nostr.roadEvents.publishReport(signed, event)) {
                is NativeReportOutcome.Published -> {
                    session.submissionAccepted(revision)
                    message(NativeShellMessage.ReportPublished, 0)
                }

                is NativeReportOutcome.Queued -> {
                    session.submissionAccepted(revision)
                    message(NativeShellMessage.ReportQueued, 0)
                }

                NativeReportOutcome.Failed -> {
                    session.submissionFailed(revision)
                    message(NativeShellMessage.ReportFailed, 0)
                }
            }
        }
    }

    fun vote(eventId: String, stillThere: Boolean) {
        scope.launch {
            val pubkey = nostr.signer.pubkeyHex
            if (pubkey == null) {
                message(NativeShellMessage.VoteLoginRequired, 0)
                return@launch
            }
            val signed = nostr.signer.sign(RoadstrNostrEvents.vote(pubkey, nowSeconds(), eventId, stillThere))
            if (signed == null || !nostr.roadEvents.publishVote(signed)) {
                message(NativeShellMessage.VoteFailed, 0)
            }
        }
    }

    /**
     * The owner corrects a camera's limit directly; anyone else files a request
     * that only the owner's client can turn into an update.
     */
    fun editSpeedLimit(event: NativeRoadEvent, speedKmh: Int) {
        if (speedKmh !in 5..300) return
        scope.launch {
            val pubkey = nostr.signer.pubkeyHex
            if (pubkey == null) {
                message(NativeShellMessage.LoginRequired, 0)
                return@launch
            }
            val now = nowSeconds()
            val owner = pubkey == event.pubkey
            val draft = if (owner) {
                RoadstrNostrEvents.update(pubkey, now, event.id, speedKmh, event.latitude, event.longitude, event.comment)
            } else {
                RoadstrNostrEvents.editRequest(pubkey, event.pubkey, now, event.id, speedKmh, event.latitude, event.longitude)
            }
            val signed = nostr.signer.sign(draft)
            val ok = signed != null && if (owner) {
                nostr.roadEvents.publishUpdate(signed)
            } else {
                nostr.roadEvents.publishPlain(signed)
            }
            message(
                when {
                    !ok -> NativeShellMessage.SpeedUpdateFailed
                    owner -> NativeShellMessage.SpeedUpdateSent
                    else -> NativeShellMessage.EditRequestSent
                },
                0,
            )
        }
    }

    /** The report on screen, so a zap or an edit acts on exactly what the driver sees. */
    @Volatile
    var currentEvent: NativeRoadEvent? = null
        private set

    /** Re-renders the open report with one more fact; null when no report is open. */
    private var updateDetail: ((EventView.() -> Unit) -> Unit)? = null

    /** What the panel knows about a report beyond the report itself. */
    class EventView(
        var public: Boolean = false,
        var label: String? = null,
        var editRequests: List<NativeRoadEventEditRequestInput> = emptyList(),
        var zapSats: Long = 0,
    )

    /**
     * Shows [event], then fills in what needs the network: the reporter's name
     * if they chose to be public, the zaps it earned, and for its owner the
     * speed-limit suggestions waiting on it.
     */
    fun showEvent(event: NativeRoadEvent) {
        val viewer = nostr.signer.pubkeyHex
        val view = EventView()
        var shown = session.state.value.revision.coerceAtLeast(0L) + 1L
        if (!session.showDetail(shown, input(event, viewer, view), nowSeconds())) return
        currentEvent = event
        val update: (EventView.() -> Unit) -> Unit = { change ->
            val current = session.state.value
            // Only while the driver is still looking at this very report.
            if (current.revision == shown && current.detail?.id == event.id) {
                view.change()
                if (session.showDetail(shown + 1, input(event, viewer, view), nowSeconds())) shown += 1
            }
        }
        updateDetail = update

        scope.launch {
            if (nostr.visibility.fetch(event.pubkey) != true) return@launch
            val label = nostr.profileLookup(event.pubkey)?.let { it.displayName ?: it.name }
            update { public = true; this.label = label }
        }
        nostr.zaps?.let { zaps ->
            scope.launch {
                val sats = (zaps.zapTotalMsat(event.id, event.pubkey) / MSAT_PER_SAT)
                    .coerceIn(0L, NativeRoadEventPresenter.MAX_COUNTER.toLong())
                if (sats > 0) update { zapSats = sats }
            }
        }
        if (viewer != null && viewer == event.pubkey) {
            nostr.userReports?.let { reports ->
                scope.launch {
                    val requests = reports.editRequests(event.id)
                        .take(NativeRoadEventPresenter.MAX_EDIT_REQUESTS)
                        .map {
                            NativeRoadEventEditRequestInput(
                                id = it.id,
                                eventId = it.eventId,
                                requesterPubkey = it.requesterPubkey,
                                speedLimitKmh = it.speedLimitKmh,
                                comment = it.comment,
                                createdAtSeconds = it.createdAt,
                            )
                        }
                    if (requests.isNotEmpty()) update { editRequests = requests }
                }
            }
        }
    }

    /** A zap of [sats] was confirmed: show it on the report without asking the relays again. */
    fun zapPaid(sats: Int) {
        updateDetail?.invoke { zapSats = (zapSats + sats).coerceAtMost(NativeRoadEventPresenter.MAX_COUNTER.toLong()) }
    }

    /** The owner accepts a suggested limit: it becomes a signed update of their report. */
    fun acceptEditRequest(event: NativeRoadEvent, requestId: String) {
        val request = session.state.value.detail?.editRequests?.firstOrNull { it.id == requestId } ?: return
        editSpeedLimit(event, request.speedLimitKmh)
    }

    private fun input(event: NativeRoadEvent, viewer: String?, view: EventView) = NativeRoadEventInput(
        id = event.id,
        pubkey = event.pubkey,
        category = event.category,
        latitude = event.latitude,
        longitude = event.longitude,
        comment = event.comment,
        createdAtSeconds = event.createdAt,
        expiresAtSeconds = event.expiresAt,
        speedLimitKmh = event.speedLimit,
        confirmations = event.confirmations,
        denials = event.denials,
        zapSats = view.zapSats,
        viewerPubkey = viewer,
        reporterPublic = view.public,
        reporterLabel = view.label,
        editRequests = view.editRequests,
    )

    private companion object {
        const val MSAT_PER_SAT = 1_000L
    }
}
