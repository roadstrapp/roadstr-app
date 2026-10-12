package app.roadstr.service.meshcore

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.interop.BridgeRateLimiter
import app.roadstr.core.protocol.interop.CompactConfirmation
import app.roadstr.core.protocol.interop.CompactCoordinates
import app.roadstr.core.protocol.interop.CompactError
import app.roadstr.core.protocol.interop.CompactFrameKind
import app.roadstr.core.protocol.interop.CompactOutcome
import app.roadstr.core.protocol.interop.CompactReport
import app.roadstr.core.protocol.interop.CompactReportLocation
import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.CompactTolerance
import app.roadstr.core.protocol.interop.ExternalRoadReport
import app.roadstr.core.protocol.interop.ExternalRoadReportAdapter
import app.roadstr.core.protocol.interop.InteropLifetime
import app.roadstr.core.protocol.interop.RoadEventTransport
import app.roadstr.core.protocol.interop.tryAcquireAll
import app.roadstr.service.nostr.NativeInteropListener
import app.roadstr.service.nostr.NativeNostrWire
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
    /** Put this device's own compatible reports and votes on the radio. */
    val send: Boolean = false,
    /** Republish what is heard on the radio to the Nostr relays, with the original author. */
    val meshToNostr: Boolean = false,
    /** Put compatible reports seen on the relays onto the radio (needs [send]). */
    val nostrToMesh: Boolean = false,
)

/** Everything the bridge refuses to do on its own, so none of it is invisible. */
data class MeshBridgeLimits(
    /** Frames taken from the radio per minute; the rest are dropped unread. */
    val ingestPerMinute: Int = 60,
    /** Extra signature checks for off-by-one coordinates (the reference encoder's bug) per minute. */
    val recoveriesPerMinute: Int = 10,
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
    val recoveredFromTruncation: Int = 0,
    val rateLimited: Int = 0,
    val stale: Int = 0,
    val waitingForReport: Int = 0,
    val queuedForRelays: Int = 0,
    val publishedToRelays: Int = 0,
    val sentToRadio: Int = 0,
    val notRepresentable: Int = 0,
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
 * What it guarantees, each backed by a test:
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
    private val recoveries = BridgeRateLimiter(limits.recoveriesPerMinute, 60)
    private val forwards = BridgeRateLimiter(limits.forwardPerHour, 3_600)
    private val cooldown = BridgeRateLimiter(1, limits.forwardCooldownSeconds.coerceAtLeast(1))
    private val own = BridgeRateLimiter(limits.ownPerMinute, 60)
    private val perAuthor = object : LinkedHashMap<String, BridgeRateLimiter>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BridgeRateLimiter>) = size > MAX_AUTHORS
    }
    private val frames = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > MAX_FRAME_DIGESTS
    }
    private val locations = object : LinkedHashMap<String, CompactReportLocation>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CompactReportLocation>) = size > MAX_LOCATIONS
    }
    private class Parked(val payload: ByteArray, val digest: String, val parkedAt: Long)

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
        when (CompactRoadCodec.kindOf(datagram.payload)) {
            CompactFrameKind.REPORT -> ingestReport(datagram.payload, now)
            CompactFrameKind.CONFIRMATION -> ingestConfirmation(datagram.payload, digest, now)
            null -> bump { copy(malformed = malformed + 1) }
        }
    }

    private fun ingestReport(payload: ByteArray, now: Long) {
        var outcome = CompactRoadCodec.decodeReport(payload)
        val badSignature = (outcome as? CompactOutcome.Failed)?.error == CompactError.SIGNATURE_INVALID
        if (badSignature && synchronized(lock) { recoveries.tryAcquire(now) }) {
            outcome = CompactRoadCodec.decodeReport(payload, CompactTolerance.RECOVER_REFERENCE_TRUNCATION)
        }
        when (outcome) {
            is CompactOutcome.Failed -> countFailure(outcome.error)
            is CompactOutcome.Ok -> acceptReport(outcome.value, now)
        }
    }

    private fun acceptReport(decoded: CompactReport, now: Long) {
        if (decoded.recoveredFromTruncation) bump { copy(recoveredFromTruncation = recoveredFromTruncation + 1) }
        val report = (ExternalRoadReportAdapter.adaptReport(decoded.event) as? CompactOutcome.Ok)?.value
            ?: return bump { copy(malformed = malformed + 1) }
        // A replay of an old frame, or a clock that is wrong by more than a few minutes, is noise.
        val fresh = report.createdAt <= now + InteropLifetime.MAX_FUTURE_SKEW_SECONDS &&
            InteropLifetime.isVisible(report.createdAt, report.type, emptyList(), now)
        if (!fresh) return bump { copy(stale = stale + 1) }
        remember(report)
        val displayed = roads.acceptInteropEvent(decoded.event, RoadEventTransport.MESH, alreadyVerified = true)
        if (displayed) bump { copy(shown = shown + 1) }
        retryParked(report.eventId, now)
        republishIfAllowed(decoded.event, report.createdAt + report.type.effectiveTtlSeconds, report)
    }

    private fun ingestConfirmation(payload: ByteArray, digest: String, now: Long) {
        val referenced = CompactRoadCodec.referencedEventId(payload) ?: return
        val location = synchronized(lock) { locations[referenced] }
        if (location == null) {
            // The frame has no coordinates: it can only be rebuilt once its report is known.
            synchronized(lock) { frames.remove(digest) }
            return park(referenced, payload, digest, now)
        }
        when (val outcome = CompactRoadCodec.decodeConfirmation(payload, location)) {
            is CompactOutcome.Failed -> countFailure(outcome.error)
            is CompactOutcome.Ok -> acceptConfirmation(outcome.value, location, now)
        }
    }

    private fun acceptConfirmation(decoded: CompactConfirmation, location: CompactReportLocation, now: Long) {
        val createdAt = (decoded.event["created_at"] as? Long) ?: return
        if (createdAt > now + InteropLifetime.MAX_FUTURE_SKEW_SECONDS) return bump { copy(stale = stale + 1) }
        val displayed = roads.acceptInteropEvent(decoded.event, RoadEventTransport.MESH, alreadyVerified = true)
        if (displayed) bump { copy(shown = shown + 1) }
        val point = GeoPoint(
            CompactCoordinates.degrees(location.latitudeE7),
            CompactCoordinates.degrees(location.longitudeE7),
        )
        republishIfAllowed(decoded.event, now + CONFIRMATION_REPUBLISH_SECONDS, point)
    }

    private fun countFailure(error: CompactError) {
        when (error) {
            CompactError.SIGNATURE_INVALID, CompactError.REFERENCE_MISMATCH ->
                bump { copy(invalidSignature = invalidSignature + 1) }
            else -> bump { copy(malformed = malformed + 1) }
        }
    }

    // ── confirmations that arrived before their report ──────────────────────────────────────────

    private fun park(referenced: String, payload: ByteArray, digest: String, now: Long) {
        synchronized(lock) {
            expireParkedLocked(now)
            val waiting = parked.getOrPut(referenced) { ArrayList() }
            if (waiting.any { it.digest == digest } || waiting.size >= MAX_PARKED_PER_REPORT) return
            if (parkedCount >= MAX_PARKED) return
            waiting += Parked(payload, digest, now)
            parkedCount++
        }
        bump { copy(waitingForReport = waitingForReport + 1) }
    }

    private fun retryParked(reportId: String, now: Long) {
        val waiting = synchronized(lock) {
            val taken = parked.remove(reportId).orEmpty()
            parkedCount -= taken.size
            taken.filter { now - it.parkedAt <= PARKED_SECONDS }
        }
        for (frame in waiting) ingestConfirmation(frame.payload, frame.digest, now)
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

    private fun republishIfAllowed(event: Map<String, Any?>, expiresAt: Long, where: ExternalRoadReport) =
        republishIfAllowed(
            event, expiresAt,
            GeoPoint(CompactCoordinates.degrees(where.latitudeE7), CompactCoordinates.degrees(where.longitudeE7)),
        )

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

    /** The relay path reported an event: remember where it is, and maybe put it on the radio. */
    override fun onInteropEvent(event: Map<String, Any?>, via: RoadEventTransport, newOnTransport: Boolean) {
        ExternalRoadReportAdapter.adaptReport(event).let { adapted ->
            (adapted as? CompactOutcome.Ok)?.value?.let { remember(it) }
        }
        if (via != RoadEventTransport.RELAY || !newOnTransport) return
        // Off the relay thread and off the service's lock.
        scope.launch { considerForwardingToRadio(event) }
    }

    private fun considerForwardingToRadio(event: Map<String, Any?>) {
        val cfg = config()
        if (!cfg.nostrToMesh || !cfg.send || !radio.ready) return
        val id = event["id"] as? String ?: return
        if (!roads.ledger.shouldForward(id, RoadEventTransport.MESH)) return
        val prepared = prepare(event) ?: return
        val now = nowSeconds()
        if (!insideForwardArea(prepared.where) || !prepared.live(now)) return
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
     * A compatible report or vote this device just signed. Sent at once if the radio is up; held
     * (a few, briefly) if it is not, because the driver asked for it to be transmitted.
     */
    fun sendOwn(event: Map<String, Any?>) {
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
        val id = event["id"] as? String ?: return
        val prepared = prepare(event) ?: return
        val now = nowSeconds()
        if (!prepared.live(now)) return
        if (!synchronized(lock) { own.tryAcquire(now) }) return bump { copy(rateLimited = rateLimited + 1) }
        // A vote only makes sense on the radio if the radio can find the report it is about.
        val reportId = (prepared.referencedReport)
        if (reportId != null && !reportKnownOnRadio(reportId)) return
        transmit(id, prepared.frame)
    }

    private fun reportKnownOnRadio(reportId: String): Boolean =
        roads.ledger.seenOn(reportId, RoadEventTransport.MESH) || roads.ledger.wasForwarded(reportId, RoadEventTransport.MESH)

    // ── shared ──────────────────────────────────────────────────────────────────────────────────

    private class Prepared(
        val frame: ByteArray,
        val author: String,
        val where: GeoPoint,
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
        val where = GeoPoint(CompactCoordinates.degrees(report.latitudeE7), CompactCoordinates.degrees(report.longitudeE7))
        return Prepared(frame, author, where, null, report.createdAt + report.type.effectiveTtlSeconds)
    }

    private fun prepareVote(event: Map<String, Any?>, author: String): Prepared? {
        val vote = (ExternalRoadReportAdapter.adaptConfirmation(event) as? CompactOutcome.Ok)?.value ?: return null
        val location = synchronized(lock) { locations[vote.referencedEventId] } ?: return null
        val frame = (CompactRoadCodec.encodeConfirmation(event, location) as? CompactOutcome.Ok)?.value ?: return null
        val where = GeoPoint(CompactCoordinates.degrees(location.latitudeE7), CompactCoordinates.degrees(location.longitudeE7))
        return Prepared(frame, author, where, vote.referencedEventId, vote.createdAt + CONFIRMATION_REPUBLISH_SECONDS)
    }

    private fun insideForwardArea(where: GeoPoint): Boolean {
        val here = position() ?: return false
        return GeoMath.distanceMeters(here, where) <= limits.forwardRadiusMeters
    }

    private fun transmit(eventId: String, frame: ByteArray) {
        // The same event can reach here twice at once (as the device's own, and as seen on a relay).
        val first = synchronized(lock) { transmitting.add(eventId) }
        if (!first || roads.ledger.wasForwarded(eventId, RoadEventTransport.MESH)) {
            synchronized(lock) { if (!first) return else transmitting.remove(eventId) }
            return
        }
        radio.send(frame) { result ->
            synchronized(lock) { transmitting.remove(eventId) }
            if (result == MeshCoreSendResult.Sent) {
                roads.ledger.markForwarded(eventId, RoadEventTransport.MESH)
                roads.ledger.observe(eventId, RoadEventTransport.MESH)
                bump { copy(sentToRadio = sentToRadio + 1) }
            }
        }
    }

    private fun remember(report: ExternalRoadReport) {
        synchronized(lock) {
            locations[report.eventId] = CompactReportLocation(report.eventId, report.latitudeE7, report.longitudeE7)
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
        const val MAX_LOCATIONS = 1_024
        const val MAX_PARKED = 64
        const val MAX_PARKED_PER_REPORT = 8
        const val PARKED_SECONDS = 15 * 60L
        const val MAX_OWN_BACKLOG = 10
        const val DIGEST_BYTES = 16
        const val CONFIRMATION_REPUBLISH_SECONDS = 6 * 3_600L
        const val MIN_REMAINING_SECONDS = 5 * 60L
    }
}
