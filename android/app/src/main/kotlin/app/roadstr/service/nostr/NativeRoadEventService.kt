package app.roadstr.service.nostr

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.core.protocol.nostr.NostrIngressRule
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrRelayEoseMessage
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayIngress
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import java.security.SecureRandom
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where signed reports wait while no relay is reachable. One JSON string per row. */
interface NativePendingReportStorage {
    fun read(): List<String>

    fun write(rows: List<String>)
}

sealed interface NativeReportOutcome {
    /** At least one relay acknowledged the report. */
    data class Published(val event: NativeRoadEvent) : NativeReportOutcome

    /** No relay answered; the signed event is queued and will be retried. */
    data class Queued(val event: NativeRoadEvent) : NativeReportOutcome

    data object Failed : NativeReportOutcome
}

/**
 * Live community road reports over Nostr, with the privacy and abuse limits of
 * the Flutter NostrRelayService it replaces.
 *
 * One long-lived connection carries the geohash subscription, and only the
 * coarse level-4 cell (~40 x 20 km): that REQ is a live location beacon, so it
 * must not be finer than the data needs. relay.nostr.band stays out of the
 * pool on purpose, because it feeds a public search indexer. Publishing is
 * independent of that socket and goes to every relay in parallel, so a report
 * survives any single relay being down.
 *
 * Relays are untrusted. Every event is routed by subscription and kind before
 * the expensive signature check, then verified, and the caches that hold
 * relay data are all bounded.
 */
class NativeRoadEventService(
    private val connector: NativeRelayConnector,
    private val publisher: NativeRelayPublisher,
    private val scheduler: NativeScheduler,
    private val relays: List<String> = DEFAULT_RELAYS,
    private val pendingStorage: NativePendingReportStorage? = null,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val random: Random = SecureRandom(),
    private val newSubscriptionId: () -> String = NativeNostrWire::randomSubscriptionId,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxIngressEventsPerSubscription: Int = MAX_INGRESS_EVENTS_PER_SUBSCRIPTION,
) {
    private val lock = Any()
    private val cache = LinkedHashMap<String, NativeRoadEvent>()
    private val pendingUpdates = LinkedHashMap<String, Map<String, Any?>>()
    private val latestUpdateAt = LinkedHashMap<String, Long>()
    private val countedVotes = HashSet<String>()
    private val voteCountByEvent = HashMap<String, Int>()
    private val pendingIds = LinkedHashSet<String>()
    private val _events = MutableStateFlow<List<NativeRoadEvent>>(emptyList())

    private var socket: NativeRelaySocket? = null
    private var connected = false
    private var disposed = false
    private var generation = 0
    private var relayIndex = 0
    private var reconnectAttempt = 0
    private var failuresThisSweep = 0
    private var lastGeohashes: List<String> = emptyList()
    private var eventsSubId = ""
    private var confSubId = ""
    private var ingress: NostrRelayIngress? = null
    private var reconnectTask: NativeScheduledTask? = null
    private var confTask: NativeScheduledTask? = null
    private var cleanupTask: NativeScheduledTask? = null
    private var handshakeTask: NativeScheduledTask? = null
    private var flushing = false

    init {
        require(maxIngressEventsPerSubscription > 0) { "Ingress event budget must be positive" }
    }

    /** Non-expired reports currently known, refreshed on every change. */
    val events: StateFlow<List<NativeRoadEvent>> = _events.asStateFlow()

    // ── lifecycle ────────────────────────────────────────────────────────────

    /** Opens the area connection. Safe to call again: it reconnects. */
    fun connect() {
        synchronized(lock) {
            if (disposed) return
            val attempt = ++generation
            socket?.close()
            socket = null
            connected = false
            cleanupTask?.cancel()
            cleanupTask = scheduler.schedule(CLEANUP_INTERVAL_MILLIS, ::cleanup)
            handshakeTask?.cancel()
            val url = relays[relayIndex % relays.size]
            val opened = try {
                connector.connect(
                    url,
                    object : NativeRelayEvents {
                        override fun onOpen() = handleOpen(attempt)

                        override fun onMessage(text: String) = handleMessage(attempt, text)

                        override fun onEnded() = handleEnded(attempt)
                    },
                )
            } catch (_: Exception) {
                handleEnded(attempt)
                return
            }
            // The callbacks above may already have run and bumped the
            // generation; a socket belonging to a superseded attempt is closed.
            if (attempt != generation) {
                opened.close()
                return
            }
            socket = opened
            // The connection is not real until the handshake completes: a
            // refused upgrade must count as a failure, not reset the backoff.
            handshakeTask = scheduler.schedule(HANDSHAKE_TIMEOUT_MILLIS) {
                synchronized(lock) {
                    if (attempt == generation && !connected) {
                        socket?.close()
                        handleEnded(attempt)
                    }
                }
            }
        }
    }

    /**
     * Drops the area connection and its timers but keeps the cached reports and
     * the last area, so [connect] can resume. Called when the app leaves the
     * screen: the subscription tells a relay roughly where the driver is, and
     * there is no reason to keep sending that in the background.
     */
    fun disconnect() {
        synchronized(lock) {
            if (disposed) return
            generation++
            socket?.close()
            socket = null
            connected = false
            reconnectTask?.cancel()
            confTask?.cancel()
            cleanupTask?.cancel()
            handshakeTask?.cancel()
        }
    }

    /** Stops everything. The service cannot be reused afterwards. */
    fun close() {
        synchronized(lock) {
            disposed = true
            generation++
            socket?.close()
            socket = null
            connected = false
            reconnectTask?.cancel()
            confTask?.cancel()
            cleanupTask?.cancel()
            handshakeTask?.cancel()
        }
        scope.cancel()
    }

    private fun handleOpen(attempt: Int) = synchronized(lock) {
        if (disposed || attempt != generation) return@synchronized
        handshakeTask?.cancel()
        connected = true
        reconnectAttempt = 0
        failuresThisSweep = 0
        // Subscriptions belong to the connection that carried them: a fresh
        // socket has nothing to CLOSE.
        eventsSubId = ""
        confSubId = ""
        ingress = null
        if (lastGeohashes.isNotEmpty()) sendEventsRequest(lastGeohashes)
        // A report made with no signal at all is queued the same way one made
        // mid-drive is; both leave on the first successful connection.
        requestFlush()
    }

    private fun handleEnded(attempt: Int) = synchronized(lock) {
        if (attempt != generation) return@synchronized
        generation++
        scheduleReconnect()
    }

    /**
     * Exponential, capped and jittered. The counter advances only once the
     * whole pool has been tried and refused: one sick relay (damus answers 503
     * to most attempts) must not stretch the delay for the healthy ones
     * behind it, while a real outage must not be retried at a fixed rate.
     */
    private fun scheduleReconnect() {
        if (disposed) return
        connected = false
        socket?.close()
        socket = null
        reconnectTask?.cancel()
        relayIndex++
        if (++failuresThisSweep >= relays.size) {
            failuresThisSweep = 0
            if (reconnectAttempt < MAX_RECONNECT_ATTEMPT) reconnectAttempt++
        }
        val base = BASE_RECONNECT_MILLIS * (1L shl reconnectAttempt)
        val capped = minOf(base, MAX_RECONNECT_MILLIS)
        // ±25 %, so a fleet of clients does not return in the same second.
        val jittered = (capped * (0.75 + random.nextDouble() * 0.5)).toLong()
        reconnectTask = scheduler.schedule(jittered, ::connect)
    }

    // ── area subscription ────────────────────────────────────────────────────

    /**
     * Subscribes to reports in the level-4 geohash cell containing the point.
     * Only level 4: every report carries its own g4/g5/g6 tags, and a
     * same-g5 report shares our g4, so g4 alone loses nothing while telling the
     * relay where we are to city level instead of ~5 km. Do not make this
     * finer without re-reading that.
     */
    fun subscribeArea(latitude: Double, longitude: Double) {
        synchronized(lock) {
            if (disposed) return
            val g4 = RoadstrNostrEvents.geohash(latitude, longitude, 4)
            if (lastGeohashes.size == 1 && lastGeohashes[0] == g4) return
            lastGeohashes = listOf(g4)
            pendingIds.clear()
            // Keep nearby markers during the relay round trip, but drop areas
            // from earlier legs of a long journey so the cache cannot grow.
            val center = GeoPoint(latitude, longitude)
            cache.values.removeAll { event ->
                GeoMath.distanceMeters(center, GeoPoint(event.latitude, event.longitude)) > FAR_METERS
            }
            pruneDerivedState()
            publishState()
            if (connected) sendEventsRequest(lastGeohashes)
        }
    }

    private fun sendEventsRequest(geohashes: List<String>) {
        if (eventsSubId.isNotEmpty()) send(NostrRelayWire.close(eventsSubId))
        eventsSubId = newSubscriptionId()
        ingress = null
        send(NostrRelayWire.areaRequest(eventsSubId, geohashes, nowSeconds()))
    }

    private fun sendConfirmationRequest(ids: List<String>) {
        if (ids.isEmpty()) return
        if (confSubId.isNotEmpty()) send(NostrRelayWire.close(confSubId))
        confSubId = newSubscriptionId()
        ingress = null
        send(NostrRelayWire.confirmationRequest(confSubId, ids, nowSeconds()))
    }

    private fun send(frame: List<Any?>) {
        if (disposed) return
        runCatching { socket?.send(NostrRelayWire.encode(frame)) }
    }

    // ── incoming ─────────────────────────────────────────────────────────────

    private fun liveIngress(): NostrRelayIngress = ingress ?: NostrRelayIngress(
        listOf(
            NostrIngressRule(
                name = "events",
                subscriptionId = eventsSubId,
                routes = mapOf(
                    1315 to NostrIngressRoute.ROAD_EVENT,
                    1317 to NostrIngressRoute.ROAD_UPDATE,
                ),
                maxEvents = maxIngressEventsPerSubscription,
            ),
            NostrIngressRule(
                name = "confirmations",
                subscriptionId = confSubId,
                routes = mapOf(1316 to NostrIngressRoute.CONFIRMATION),
                maxEvents = maxIngressEventsPerSubscription,
            ),
        ),
    ).also { ingress = it }

    private fun handleMessage(attempt: Int, text: String) {
        synchronized(lock) {
            if (disposed || attempt != generation) return
            val message = NostrRelayMessageDecoder.decode(text).message ?: return
            when (message) {
                is NostrRelayEventMessage -> {
                    // Cheap routing first: a relay (or anyone publishing through
                    // one) flooding validly signed events for a subscription or
                    // kind we never asked for must not cost a signature check.
                    val decision = liveIngress().inspect(message.subscriptionId, message.event["kind"])
                    if (decision.limitReached) {
                        // A relay exhausting a subscription's CPU budget is
                        // treated as unhealthy. Rotate instead of accepting an
                        // unbounded signature-verification workload or leaving
                        // this area permanently starved.
                        handleEnded(attempt)
                        return
                    }
                    if (!decision.shouldVerify) return
                    if (!NativeNostrWire.verify(message.event)) return
                    when (decision.route) {
                        NostrIngressRoute.ROAD_EVENT -> handleRoadEvent(message.event)
                        NostrIngressRoute.ROAD_UPDATE -> handleRoadUpdate(message.event)
                        NostrIngressRoute.CONFIRMATION -> handleConfirmation(message.event)
                        else -> Unit
                    }
                }

                is NostrRelayEoseMessage -> {
                    if (message.subscriptionId == eventsSubId && eventsSubId.isNotEmpty()) {
                        // The delay lets in-flight EVENTs arrive before the
                        // confirmation filter is built from the ids seen.
                        confTask?.cancel()
                        confTask = scheduler.schedule(CONFIRMATION_DELAY_MILLIS) {
                            synchronized(lock) {
                                sendConfirmationRequest(pendingIds.toList())
                                pendingIds.clear()
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    private fun handleRoadEvent(json: Map<String, Any?>) {
        val event = NativeRoadEventCodec.parse(json, nowSeconds()) ?: return
        val hashes = NativeNostrWire.tags(json["tags"])
            ?.filter { it.size >= 2 && it[0] == "g" }
            ?.map { it[1] }
            ?.toSet() ?: return
        // The report must carry the geohashes of its own coordinates, or it
        // could be planted in a cell it does not belong to.
        for (precision in 4..6) {
            if (RoadstrNostrEvents.geohash(event.latitude, event.longitude, precision) !in hashes) return
        }
        cache[event.id] = event
        pendingUpdates.remove(event.id)?.let { applyUpdate(it, event.id) }
        if (cache.size > MAX_CACHED_EVENTS) {
            cache.values.minByOrNull { it.createdAt }?.let { cache.remove(it.id) }
            pruneDerivedState()
        }
        // Past the cap the report is still shown; only its tally may go unfetched.
        if (pendingIds.size < MAX_PENDING_IDS) pendingIds += event.id
        publishState()
    }

    private fun handleRoadUpdate(json: Map<String, Any?>) {
        val target = NativeNostrWire.tagValue(json, "e") ?: return
        if (cache[target] == null) {
            // Park it, but never let the parking lot grow without bound: when it
            // is full the oldest is dropped, so a flood of updates for ids that
            // never arrive costs a fixed amount of memory.
            if (pendingUpdates.size >= MAX_PENDING_UPDATES) {
                prunePendingUpdates()
                if (pendingUpdates.size >= MAX_PENDING_UPDATES) {
                    pendingUpdates.entries
                        .minByOrNull { (NativeNostrWire.integral(it.value["created_at"]) ?: 0L) }
                        ?.let { pendingUpdates.remove(it.key) }
                }
            }
            pendingUpdates[target] = json
            return
        }
        if (applyUpdate(json, target)) publishState()
    }

    /** Only the original reporter can change a report, and only forwards in time. */
    private fun applyUpdate(json: Map<String, Any?>, eventId: String): Boolean {
        val event = cache[eventId] ?: return false
        if (json["pubkey"] != event.pubkey) return false
        val createdAt = NativeNostrWire.integral(json["created_at"]) ?: 0L
        if (createdAt <= (latestUpdateAt[eventId] ?: event.createdAt)) return false
        val limit = NativeNostrWire.tagValue(json, "maxspeed")?.toIntOrNull() ?: return false
        if (limit !in 5..300) return false
        cache[eventId] = event.copy(speedLimit = limit)
        // A security mark, not a cache entry: kept after the event is evicted so
        // a replayed older update cannot be accepted as new.
        latestUpdateAt[eventId] = createdAt
        while (latestUpdateAt.size > MAX_UPDATE_MARKS) {
            latestUpdateAt.remove(latestUpdateAt.keys.first())
        }
        return true
    }

    private fun handleConfirmation(json: Map<String, Any?>) {
        val tags = NativeNostrWire.tags(json["tags"]) ?: return
        val targets = tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
        val statuses = tags.filter { it.size >= 2 && it[0] == "status" }.map { it[1] }
        if (targets.size != 1 || statuses.size != 1) return
        val targetId = targets.single()
        val status = statuses.single()
        if (status != STILL_THERE && status != NO_LONGER_THERE) return
        val event = cache[targetId] ?: return
        // Capped per event: minting throwaway identities is free, and a report
        // can stay cached for up to 30 days.
        if ((voteCountByEvent[targetId] ?: 0) >= MAX_VOTES_PER_EVENT) return
        // One vote per identity per event; re-fetching is idempotent.
        if (!countedVotes.add("$targetId:${json["pubkey"]}")) return
        voteCountByEvent[targetId] = (voteCountByEvent[targetId] ?: 0) + 1
        cache[targetId] = if (status == STILL_THERE) {
            event.copy(confirmations = event.confirmations + 1)
        } else {
            event.copy(denials = event.denials + 1)
        }
        publishState()
    }

    // ── publishing ───────────────────────────────────────────────────────────

    /**
     * Sends an already-signed kind-1315 [signed] and shows [event] on the map
     * at once. If no relay acknowledges it, the event is queued, not lost.
     */
    suspend fun publishReport(signed: Map<String, Any?>, event: NativeRoadEvent): NativeReportOutcome {
        val accepted = publisher.publish(signed)
        addLocal(event)
        if (accepted) return NativeReportOutcome.Published(event)
        val expiresAt = event.expiresAt ?: (event.createdAt + event.category.ttlSeconds)
        return if (queue(signed, expiresAt)) {
            NativeReportOutcome.Queued(event)
        } else {
            NativeReportOutcome.Failed
        }
    }

    /** Publishes a signed vote (kind 1316) and counts it locally on success. */
    suspend fun publishVote(signed: Map<String, Any?>): Boolean {
        if (!publisher.publish(signed)) return false
        synchronized(lock) { handleConfirmation(signed) }
        return true
    }

    /** Publishes a signed owner update (kind 1317) and applies it locally. */
    suspend fun publishUpdate(signed: Map<String, Any?>): Boolean {
        if (!publisher.publish(signed)) return false
        synchronized(lock) {
            NativeNostrWire.tagValue(signed, "e")?.let { if (applyUpdate(signed, it)) publishState() }
        }
        return true
    }

    /** Publishes any other signed event (edit request, visibility). */
    suspend fun publishPlain(signed: Map<String, Any?>): Boolean = publisher.publish(signed)

    private fun addLocal(event: NativeRoadEvent) = synchronized(lock) {
        cache[event.id] = event
        publishState()
    }

    // ── offline queue ────────────────────────────────────────────────────────

    private fun queue(signed: Map<String, Any?>, expiresAt: Long): Boolean {
        val storage = pendingStorage ?: return false
        return runCatching {
            val row = NostrJson.encode(linkedMapOf("event" to signed, "expiresAt" to expiresAt))
            val rows = (storage.read() + row).takeLast(MAX_QUEUED_REPORTS)
            storage.write(rows)
        }.isSuccess
    }

    /** Retries queued reports in the background, one pass at a time. */
    fun requestFlush() {
        if (pendingStorage == null) return
        scope.launch { flushPending() }
    }

    /** One retry pass over the queue; a pass already running makes this a no-op. */
    suspend fun flushPending() {
        val storage = pendingStorage ?: return
        synchronized(lock) {
            if (flushing || disposed) return
            flushing = true
        }
        try {
            flushQueue(storage)
        } finally {
            synchronized(lock) { flushing = false }
        }
    }

    private suspend fun flushQueue(storage: NativePendingReportStorage) {
        val snapshot = runCatching { storage.read() }.getOrDefault(emptyList())
        if (snapshot.isEmpty()) return
        val now = nowSeconds()
        val remaining = ArrayList<String>()
        for (row in snapshot) {
            val entry = parseRow(row) ?: continue
            val (event, expiresAt) = entry
            // A report published hours after the hazard was seen would mislead
            // whoever sees it: a stale one is dropped silently.
            if (expiresAt <= now) continue
            if (!NativeNostrWire.verify(event)) continue
            if (!publisher.publish(event)) remaining += row
        }
        // Reports queued while the relays were being awaited must survive.
        val known = snapshot.toHashSet()
        val current = runCatching { storage.read() }.getOrDefault(snapshot)
        runCatching { storage.write(remaining + current.filter { it !in known }) }
    }

    private fun parseRow(row: String): Pair<Map<String, Any?>, Long>? = runCatching {
        val map = BoundedJsonParser(row).parse() as? Map<*, *> ?: return null
        @Suppress("UNCHECKED_CAST")
        val event = (map["event"] as? Map<String, Any?>) ?: return null
        val expiresAt = NativeNostrWire.integral(map["expiresAt"]) ?: return null
        event to expiresAt
    }.getOrNull()

    // ── housekeeping ─────────────────────────────────────────────────────────

    private fun cleanup() {
        synchronized(lock) {
            if (disposed) return
            // Unconditional, so the parked-update TTL is a real deadline.
            prunePendingUpdates()
            val before = cache.size
            val now = nowSeconds()
            cache.values.removeAll { it.isExpired(now) }
            if (cache.size != before) {
                pruneDerivedState()
                publishState()
            }
            cleanupTask = scheduler.schedule(CLEANUP_INTERVAL_MILLIS, ::cleanup)
        }
    }

    private fun prunePendingUpdates() {
        val cutoff = nowSeconds() - PENDING_UPDATE_TTL_SECONDS
        pendingUpdates.values.removeAll { (NativeNostrWire.integral(it["created_at"]) ?: 0L) < cutoff }
    }

    private fun pruneDerivedState() {
        countedVotes.removeAll { vote ->
            val separator = vote.indexOf(':')
            separator <= 0 || !cache.containsKey(vote.substring(0, separator))
        }
        voteCountByEvent.keys.removeAll { !cache.containsKey(it) }
        prunePendingUpdates()
    }

    private fun publishState() {
        val now = nowSeconds()
        _events.value = cache.values.filter { !it.isExpired(now) }
    }

    companion object {
        /**
         * Verified end to end (publish a custom kind, read it straight back):
         * this rules out relays that answer OK and quietly drop what they do
         * not recognise. damus is last on purpose: it refuses a third of the
         * connection attempts. relay.nostr.band is deliberately NOT here: this
         * socket carries the geohash subscription, and nostr.band feeds a
         * public search indexer.
         */
        val DEFAULT_RELAYS: List<String> = listOf(
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.oxtr.dev",
            "wss://relay.damus.io",
        )

        private const val STILL_THERE = "still_there"
        private const val NO_LONGER_THERE = "no_longer_there"
        private const val MAX_CACHED_EVENTS = 1_000
        private const val MAX_PENDING_IDS = 500
        private const val MAX_PENDING_UPDATES = 200
        private const val MAX_UPDATE_MARKS = 2_000
        private const val MAX_VOTES_PER_EVENT = 200
        private const val MAX_QUEUED_REPORTS = 200
        private const val MAX_INGRESS_EVENTS_PER_SUBSCRIPTION = 4_096
        private const val PENDING_UPDATE_TTL_SECONDS = 15L * 60L
        private const val FAR_METERS = 100_000.0
        private const val CLEANUP_INTERVAL_MILLIS = 2L * 60L * 1000L
        private const val CONFIRMATION_DELAY_MILLIS = 300L
        private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000L
        private const val BASE_RECONNECT_MILLIS = 1_250L
        private const val MAX_RECONNECT_MILLIS = 60_000L
        private const val MAX_RECONNECT_ATTEMPT = 6
    }
}
