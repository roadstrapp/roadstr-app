package app.roadstr.service.meshcore

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.interop.BridgeRateLimiter
import app.roadstr.core.protocol.interop.CompactConfirmation
import app.roadstr.core.protocol.interop.CompactCoordinates
import app.roadstr.core.protocol.interop.CompactError
import app.roadstr.core.protocol.interop.CompactOutcome
import app.roadstr.core.protocol.interop.CompactReport
import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.ExternalRoadReportAdapter
import app.roadstr.core.protocol.interop.InteropLifetime
import app.roadstr.core.protocol.interop.RoadEventTransport
import app.roadstr.core.protocol.interop.tryAcquireAll
import app.roadstr.service.nostr.NativeInteropListener
import app.roadstr.service.nostr.NativeNostrWire
import app.roadstr.service.nostr.NativeRoadEvent
import app.roadstr.service.nostr.NativeRoadEventService
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user switched on. All off by default; each direction is its own decision. */
data class MeshBridgeConfig(
    /** Read road reports from the radio and show them. */
    val receive: Boolean = false,
    /** Put this device's own reports and votes on the radio. */
    val send: Boolean = false,
    /** Republish what is heard on the radio to the Nostr relays, with the original author. */
    val meshToNostr: Boolean = false,
    /** Put reports seen on the relays onto the radio (needs [send]). */
    val nostrToMesh: Boolean = false,
)

/** Everything the bridge refuses to do on its own, so none of it is invisible. */
data class MeshBridgeLimits(
    /** Frames taken from the radio per minute; the rest are dropped unread. */
    val ingestPerMinute: Int = 60,
    /** Other people's events put on the radio, per hour and in total. */
    val forwardPerHour: Int = 20,
    val forwardPerAuthorPerHour: Int = 4,
    /** Minimum gap between two transmissions of other people's events. */
    val forwardCooldownSeconds: Long = 20,
    /** This device's own reports and votes: a loose guard against a runaway loop, not a quota. */
    val ownPerMinute: Int = 6,
    /** Only events this close to the driver are put on the radio: it is a local medium. */
    val forwardRadiusMeters: Double = 40_000.0,
    /** Only events this close are republished to relays from the radio. */
    val republishRadiusMeters: Double = 250_000.0,
)

data class MeshBridgeStats(
    val received: Int = 0,
    val shown: Int = 0,
    val malformed: Int = 0,
    val invalidSignature: Int = 0,
    val rateLimited: Int = 0,
    val stale: Int = 0,
    /** Votes heard before their report, held for a while in case it follows. */
    val waitingForReport: Int = 0,
    val queuedForRelays: Int = 0,
    val publishedToRelays: Int = 0,
    val sentToRadio: Int = 0,
    val notRepresentable: Int = 0,
    /** Frames the radio refused as too large. The encoder keeps to 163 bytes, so this stays 0. */
    val tooLarge: Int = 0,
)

/** The radio, as the bridge needs it. */
interface MeshRadioPort {
    val ready: Boolean

    fun send(payload: ByteArray, onResult: (MeshCoreSendResult) -> Unit)
}

/**
 * Moves road reports between a MeshCore radio and the app, and between the radio and the Nostr
 * relays, without ever acting as an author.
 *
 * What it guarantees, each covered by RoadMeshBridgeTest:
 *  - an event is always re-checked: a frame is rebuilt into a Nostr event and its signature
 *    verified before anything uses it, and nothing is forwarded that did not verify;
 *  - the author, ID and signature never change: the bridge does not sign, ever;
 *  - nothing is sent anywhere that already has it (the ledger), so relay -> radio -> relay cannot
 *    cycle, and every outgoing direction has a rate limit, a cooldown and an area;
 *  - everything is off until the user turns it on, and a frame that is not exactly the format is
 *    dropped, never repaired.
 *
 * Not covered by any test here: a real radio. See docs/interoperability/MESHCORE_NOSTR.md.
 */
class RoadMeshBridge(
    private val radio: MeshRadioPort,
    private val roads: NativeRoadEventService,
    private val republish: MeshRepublishQueue,
    private val config: () -> MeshBridgeConfig,
    private val position: () -> GeoPoint?,
    private val nowSeconds: () -> Long,
    private val scope: CoroutineScope,
    private val limits: MeshBridgeLimits = MeshBridgeLimits(),
) : NativeInteropListener {
    private val lock = Any()
    private val ingest = BridgeRateLimiter(limits.ingestPerMinute, 60)
    private val forwards = BridgeRateLimiter(limits.forwardPerHour, 3_600)
    private val cooldown = BridgeRateLimiter(1, limits.forwardCooldownSeconds.coerceAtLeast(1))
    private val own = BridgeRateLimiter(limits.ownPerMinute, 60)
    private val perAuthor = object : LinkedHashMap<String, BridgeRateLimiter>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BridgeRateLimiter>) = size > MAX_AUTHORS
    }
    private val frames = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > MAX_FRAME_DIGESTS
    }

    private class Parked(val vote: CompactConfirmation, val parkedAt: Long)

    /** Votes heard before their report, by report ID. Bounded in total, per report and in time. */
    private val parked = LinkedHashMap<String, MutableList<Parked>>()
    private var parkedCount = 0
    private val ownBacklog = ArrayDeque<Map<String, Any?>>()
    private val transmitting = HashSet<String>()
    private val _stats = MutableStateFlow(MeshBridgeStats())

    val stats: StateFlow<MeshBridgeStats> = _stats.asStateFlow()

    // ── radio -> app (and relays) ───────────────────────────────────────────────────────────────

    /** Called by the client with every datagram of the Roadstr channel. */
    fun onDatagram(datagram: MeshCoreDatagram) {
        if (!config().receive) return
        val now = nowSeconds()
        bump { copy(received = received + 1) }
        val admitted = synchronized(lock) { ingest.tryAcquire(now) }
        if (!admitted) return bump { copy(rateLimited = rateLimited + 1) }
        val digest = digestOf(datagram.payload)
        val repeated = synchronized(lock) { frames.put(digest, true) != null }
        if (repeated) return
        when (val outcome = CompactRoadCodec.decode(datagram.payload)) {
            is CompactOutcome.Failed -> countFailure(outcome.error)
            is CompactOutcome.Ok -> when (val decoded = outcome.value) {
                is CompactReport -> acceptReport(decoded, now)
                is CompactConfirmation -> acceptVote(decoded, now)
            }
        }
    }

    private fun acceptReport(decoded: CompactReport, now: Long) {
        val report = (ExternalRoadReportAdapter.adaptReport(decoded.event) as? CompactOutcome.Ok)?.value
            ?: return bump { copy(malformed = malformed + 1) }
        // A replay of an old frame, or a clock that is wrong by more than a few minutes, is noise.
        val fresh = report.createdAt <= now + InteropLifetime.MAX_FUTURE_SKEW_SECONDS &&
            InteropLifetime.isVisible(report.createdAt, report.type, emptyList(), now, report.relayExpiration)
        if (!fresh) return bump { copy(stale = stale + 1) }
        val displayed = roads.acceptInteropEvent(decoded.event, RoadEventTransport.MESH, alreadyVerified = true)
        if (displayed) bump { copy(shown = shown + 1) }
        val where = GeoPoint(CompactCoordinates.degrees(report.latitudeE6), CompactCoordinates.degrees(report.longitudeE6))
        val shownUntil = minOf(report.createdAt + report.type.effectiveTtlSeconds, report.relayExpiration)
        republishIfAllowed(decoded.event, shownUntil, where)
    }

    private fun acceptVote(decoded: CompactConfirmation, now: Long) {
        val createdAt = NativeNostrWire.integral(decoded.event["created_at"]) ?: return
        val tooNew = createdAt > now + InteropLifetime.MAX_FUTURE_SKEW_SECONDS
        if (tooNew || now - createdAt > MAX_VOTE_AGE_SECONDS) return bump { copy(stale = stale + 1) }
        // The vote frame is self-contained and already verified; only its report may be missing.
        val report = roads.find(decoded.referencedEventId) ?: return park(decoded, now)
        applyVote(decoded, report, now)
    }

    private fun applyVote(decoded: CompactConfirmation, report: NativeRoadEvent, now: Long) {
        val createdAt = NativeNostrWire.integral(decoded.event["created_at"]) ?: return
        if (createdAt < report.createdAt) return bump { copy(stale = stale + 1) }
        val displayed = roads.acceptInteropEvent(decoded.event, RoadEventTransport.MESH, alreadyVerified = true)
        if (displayed) bump { copy(shown = shown + 1) }
        republishIfAllowed(decoded.event, now + CONFIRMATION_REPUBLISH_SECONDS, GeoPoint(report.latitude, report.longitude))
    }

    private fun countFailure(error: CompactError) {
        when (error) {
            CompactError.SIGNATURE_INVALID -> bump { copy(invalidSignature = invalidSignature + 1) }
            else -> bump { copy(malformed = malformed + 1) }
        }
    }

    // ── votes that arrived before their report ──────────────────────────────────────────────────

    private fun park(vote: CompactConfirmation, now: Long) {
        synchronized(lock) {
            expireParkedLocked(now)
            val waiting = parked.getOrPut(vote.referencedEventId) { ArrayList() }
            val id = vote.event["id"]
            if (waiting.any { it.vote.event["id"] == id } || waiting.size >= MAX_PARKED_PER_REPORT) return
            if (parkedCount >= MAX_PARKED) return
            waiting += Parked(vote, now)
            parkedCount++
        }
        bump { copy(waitingForReport = waitingForReport + 1) }
    }

    /** The report [reportId] is now known, from the radio or a relay: apply what waited for it. */
    private fun applyParked(reportId: String) {
        val now = nowSeconds()
        val waiting = synchronized(lock) {
            val taken = parked.remove(reportId).orEmpty()
            parkedCount -= taken.size
            taken.filter { now - it.parkedAt <= PARKED_SECONDS }
        }
        if (waiting.isEmpty()) return
        val report = roads.find(reportId) ?: return
        for (entry in waiting) applyVote(entry.vote, report, now)
    }

    private fun expireParkedLocked(now: Long) {
        val iterator = parked.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val kept = entry.value.filter { now - it.parkedAt <= PARKED_SECONDS }
            parkedCount -= entry.value.size - kept.size
            if (kept.isEmpty()) iterator.remove() else entry.setValue(kept.toMutableList())
        }
    }

    // ── radio -> relays ─────────────────────────────────────────────────────────────────────────

    private fun republishIfAllowed(event: Map<String, Any?>, expiresAt: Long, where: GeoPoint) {
        if (!config().meshToNostr) return
        val id = event["id"] as? String ?: return
        if (!roads.ledger.shouldForward(id, RoadEventTransport.RELAY)) return
        val here = position()
        if (here == null || GeoMath.distanceMeters(here, where) > limits.republishRadiusMeters) return
        if (!republish.enqueue(event, expiresAt)) return
        bump { copy(queuedForRelays = queuedForRelays + 1) }
        scope.launch { flushRepublish() }
    }

    /**
     * Tries to publish what is waiting. Safe to call at any time; does nothing offline, and nothing
     * once the user switched the bridge off (what is waiting then stays, until cleared or published
     * after it is switched on again).
     */
    suspend fun flushRepublish() {
        if (!config().meshToNostr) return
        val published = republish.flush(
            publish = { roads.publishPlain(it) },
            onPublished = { id -> roads.ledger.markForwarded(id, RoadEventTransport.RELAY) },
        )
        if (published > 0) bump { copy(publishedToRelays = publishedToRelays + published) }
    }

    // ── relays -> radio ─────────────────────────────────────────────────────────────────────────

    /** The service accepted an event: release votes that waited for it, and maybe put it on the radio. */
    override fun onInteropEvent(event: Map<String, Any?>, via: RoadEventTransport, newOnTransport: Boolean) {
        val id = event["id"] as? String ?: return
        val report = NativeNostrWire.integral(event["kind"]) == CompactRoadCodec.KIND_REPORT.toLong()
        // Off the relay thread and off the service's lock.
        if (report) scope.launch { applyParked(id) }
        if (!report || via != RoadEventTransport.RELAY || !newOnTransport) return
        scope.launch { considerForwardingToRadio(event) }
    }

    private fun considerForwardingToRadio(event: Map<String, Any?>) {
        val cfg = config()
        if (!cfg.nostrToMesh || !cfg.send || !radio.ready) return
        val id = event["id"] as? String ?: return
        if (!roads.ledger.shouldForward(id, RoadEventTransport.MESH)) return
        val prepared = prepare(event) ?: return
        val now = nowSeconds()
        val where = prepared.where ?: return
        if (!insideForwardArea(where) || !prepared.live(now)) return
        val allowed = synchronized(lock) { withinForwardQuota(prepared.author, now) }
        if (!allowed) return bump { copy(rateLimited = rateLimited + 1) }
        transmit(id, prepared.frame)
    }

    /** All three gates must pass for the same event, and none is spent unless all do. */
    private fun withinForwardQuota(author: String, now: Long): Boolean {
        val authorLimiter = perAuthor.getOrPut(author) { BridgeRateLimiter(limits.forwardPerAuthorPerHour, 3_600) }
        return tryAcquireAll(now, authorLimiter, forwards, cooldown)
    }

    // ── this device's own events ────────────────────────────────────────────────────────────────

    /**
     * A report or vote this device just signed. Sent at once if the radio is up; held (a few,
     * briefly) if it is not, because the driver asked for it to be transmitted.
     */
    fun sendOwn(event: Map<String, Any?>) {
        val id = event["id"] as? String ?: return
        // The app publishes its own events to the relays itself; a radio echo must not do it again.
        roads.ledger.markForwarded(id, RoadEventTransport.RELAY)
        if (!config().send) return
        if (!radio.ready) {
            synchronized(lock) {
                if (ownBacklog.size >= MAX_OWN_BACKLOG) ownBacklog.removeFirst()
                ownBacklog.addLast(event)
            }
            return
        }
        transmitOwn(event)
    }

    /** The radio just became ready: send what was held for it. */
    fun onRadioReady() {
        val held = synchronized(lock) { ArrayList(ownBacklog).also { ownBacklog.clear() } }
        for (event in held) transmitOwn(event)
    }

    private fun transmitOwn(event: Map<String, Any?>) {
        if (!config().send) return
        val id = event["id"] as? String ?: return
        val prepared = prepare(event) ?: return
        val now = nowSeconds()
        if (!prepared.live(now)) return
        // A vote is worth airtime only where its report can be heard too.
        val reportId = prepared.referencedReport
        if (reportId != null && !reportKnownOnRadio(reportId)) return
        if (!synchronized(lock) { own.tryAcquire(now) }) return bump { copy(rateLimited = rateLimited + 1) }
        transmit(id, prepared.frame)
    }

    private fun reportKnownOnRadio(reportId: String): Boolean =
        roads.ledger.seenOn(reportId, RoadEventTransport.MESH) || roads.ledger.wasForwarded(reportId, RoadEventTransport.MESH)

    // ── shared ──────────────────────────────────────────────────────────────────────────────────

    private class Prepared(
        val frame: ByteArray,
        val author: String,
        /** Where a report is; null for a vote, which carries no position. */
        val where: GeoPoint?,
        val referencedReport: String?,
        val endsAt: Long,
    ) {
        fun live(now: Long): Boolean = endsAt - now >= MIN_REMAINING_SECONDS
    }

    /** The frame for [event] if it can be encoded exactly, with what the policy checks need. */
    private fun prepare(event: Map<String, Any?>): Prepared? {
        val author = event["pubkey"] as? String ?: return null
        val kind = NativeNostrWire.integral(event["kind"])
        val prepared = if (kind == CompactRoadCodec.KIND_REPORT.toLong()) prepareReport(event, author) else prepareVote(event, author)
        if (prepared == null) bump { copy(notRepresentable = notRepresentable + 1) }
        return prepared
    }

    private fun prepareReport(event: Map<String, Any?>, author: String): Prepared? {
        val report = (ExternalRoadReportAdapter.adaptReport(event) as? CompactOutcome.Ok)?.value ?: return null
        val frame = (CompactRoadCodec.encodeReport(event) as? CompactOutcome.Ok)?.value ?: return null
        val where = GeoPoint(CompactCoordinates.degrees(report.latitudeE6), CompactCoordinates.degrees(report.longitudeE6))
        val endsAt = minOf(report.createdAt + report.type.effectiveTtlSeconds, report.relayExpiration)
        return Prepared(frame, author, where, null, endsAt)
    }

    private fun prepareVote(event: Map<String, Any?>, author: String): Prepared? {
        val frame = (CompactRoadCodec.encodeConfirmation(event) as? CompactOutcome.Ok)?.value ?: return null
        val vote = (CompactRoadCodec.decodeConfirmation(frame) as? CompactOutcome.Ok)?.value ?: return null
        val createdAt = NativeNostrWire.integral(event["created_at"]) ?: return null
        return Prepared(frame, author, null, vote.referencedEventId, createdAt + CONFIRMATION_REPUBLISH_SECONDS)
    }

    private fun insideForwardArea(where: GeoPoint): Boolean {
        val here = position() ?: return false
        return GeoMath.distanceMeters(here, where) <= limits.forwardRadiusMeters
    }

    private fun transmit(eventId: String, frame: ByteArray) {
        // The same event can reach here twice at once (as the device's own, and as seen on a relay).
        val first = synchronized(lock) { transmitting.add(eventId) }
        if (!first) return
        if (roads.ledger.wasForwarded(eventId, RoadEventTransport.MESH)) {
            synchronized(lock) { transmitting.remove(eventId) }
            return
        }
        radio.send(frame) { result ->
            synchronized(lock) { transmitting.remove(eventId) }
            when (result) {
                MeshCoreSendResult.Sent -> {
                    roads.ledger.markForwarded(eventId, RoadEventTransport.MESH)
                    roads.ledger.observe(eventId, RoadEventTransport.MESH)
                    bump { copy(sentToRadio = sentToRadio + 1) }
                }
                MeshCoreSendResult.TooLarge -> bump { copy(tooLarge = tooLarge + 1) }
                else -> Unit
            }
        }
    }

    private fun digestOf(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(payload).take(DIGEST_BYTES).joinToString("") { "%02x".format(it) }

    private inline fun bump(crossinline change: MeshBridgeStats.() -> MeshBridgeStats) {
        _stats.update { it.change() }
    }

    private companion object {
        const val MAX_AUTHORS = 256
        const val MAX_FRAME_DIGESTS = 512
        const val MAX_PARKED = 64
        const val MAX_PARKED_PER_REPORT = 8
        const val PARKED_SECONDS = 15 * 60L

        /** The radio is a live medium: a vote older than a day is a replay, not news. */
        const val MAX_VOTE_AGE_SECONDS = 24 * 3_600L
        const val MAX_OWN_BACKLOG = 10
        const val DIGEST_BYTES = 16
        const val CONFIRMATION_REPUBLISH_SECONDS = 6 * 3_600L
        const val MIN_REMAINING_SECONDS = 5 * 60L
    }
}
