package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrIngressRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A suggestion, from another driver, to change the speed limit of a report. */
data class NativeEditRequest(
    val id: String,
    val eventId: String,
    val requesterPubkey: String,
    val speedLimitKmh: Int,
    val comment: String,
    val createdAt: Long,
)

/**
 * Reads of one account's own history: its reports with the votes and speed
 * updates on them, and the edit requests waiting on a report. A port of the
 * Flutter fetchUserEvents and fetchEditRequests.
 *
 * Relays are untrusted. Every message is routed by subscription and kind and
 * counted against a budget before its signature is checked, the author is
 * checked locally whatever the filter asked for, and a vote counts once per
 * voter and report.
 */
class NativeUserReportsService(
    connector: NativeRelayConnector,
    private val relays: List<String> = NativeRoadEventService.DEFAULT_RELAYS,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val fetcher: NativeRelayFetcher = NativeRelayFetcher(connector, timeoutMillis = QUERY_TIMEOUT_MILLIS),
) {
    /** Reports published by [pubkey] in the last year, newest first, merged across relays. */
    suspend fun userEvents(pubkey: String, limit: Int = DEFAULT_LIMIT): List<NativeRoadEvent> {
        if (!NativeNostrWire.isHex32(pubkey)) return emptyList()
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val perRelay = coroutineScope {
            relays.map { url -> async { userEventsFrom(url, pubkey, safeLimit) } }.awaitAll()
        }
        val merged = LinkedHashMap<String, NativeRoadEvent>()
        for (events in perRelay) {
            for (event in events) {
                val existing = merged[event.id]
                merged[event.id] = if (existing == null) {
                    event
                } else {
                    // The same report from two relays: keep the higher tally rather
                    // than summing, or a vote stored twice would inflate reputation.
                    existing.copy(
                        confirmations = maxOf(existing.confirmations, event.confirmations),
                        denials = maxOf(existing.denials, event.denials),
                        speedLimit = existing.speedLimit ?: event.speedLimit,
                    )
                }
            }
        }
        return merged.values.sortedByDescending(NativeRoadEvent::createdAt)
    }

    private suspend fun userEventsFrom(url: String, pubkey: String, limit: Int): List<NativeRoadEvent> {
        val now = nowSeconds()
        val reports = LinkedHashMap<String, NativeRoadEvent>()
        val updates = ArrayList<Map<String, Any?>>()
        fetcher.fetch(
            url = url,
            subscriptionId = NativeNostrWire.randomSubscriptionId(),
            filter = linkedMapOf(
                "kinds" to listOf(1315, 1317),
                "authors" to listOf(pubkey),
                // A year: this is the user's own reporting history.
                "since" to now - HISTORY_SECONDS,
                "limit" to limit,
            ),
            routes = mapOf(1315 to NostrIngressRoute.USER_REPORT, 1317 to NostrIngressRoute.USER_UPDATE),
            maxEvents = limit * 2,
            accept = { event ->
                // Asked for `authors`, but a relay may ignore its own filters.
                if (event["pubkey"] == pubkey && NativeNostrWire.verify(event)) {
                    when (NativeNostrWire.integral(event["kind"])) {
                        1315L -> NativeRoadEventCodec.parse(event, now)?.let { reports.putIfAbsent(it.id, it) }
                        1317L -> updates += event
                    }
                }
                false
            },
        )
        if (reports.isEmpty()) return emptyList()

        val tallied = tally(url, reports.keys.toList(), limit)
        return reports.values.map { event ->
            var report = event
            updates.filter { NativeNostrWire.tagValue(it, "e") == report.id }
                .sortedBy { NativeNostrWire.integral(it["created_at"]) ?: 0L }
                .forEach { update -> report = applyUpdate(report, update) }
            val (up, down) = tallied[report.id] ?: (0 to 0)
            report.copy(confirmations = up, denials = down)
        }
    }

    /** Only the author can change a report's limit, and only with a newer update. */
    private fun applyUpdate(event: NativeRoadEvent, update: Map<String, Any?>): NativeRoadEvent {
        val limit = NativeNostrWire.tagValue(update, "maxspeed")?.toIntOrNull()
        val at = NativeNostrWire.integral(update["created_at"]) ?: 0L
        if (update["pubkey"] != event.pubkey) return event
        if (limit == null || limit !in 5..300) return event
        if (at <= event.createdAt) return event
        return event.copy(speedLimit = limit)
    }

    private suspend fun tally(url: String, ids: List<String>, limit: Int): Map<String, Pair<Int, Int>> {
        val counted = HashSet<String>()
        val result = HashMap<String, Pair<Int, Int>>()
        ids.chunked(VOTE_BATCH).forEach { batch ->
            fetcher.fetch(
                url = url,
                subscriptionId = NativeNostrWire.randomSubscriptionId(),
                filter = linkedMapOf("kinds" to listOf(1316), "#e" to batch, "limit" to VOTES_PER_BATCH),
                kind = 1316,
                route = NostrIngressRoute.USER_VOTE,
                maxEvents = limit * 5,
                accept = { vote ->
                    if (NativeNostrWire.verify(vote)) countVote(vote, counted, result)
                    false
                },
            )
        }
        return result
    }

    private fun countVote(
        vote: Map<String, Any?>,
        counted: MutableSet<String>,
        into: MutableMap<String, Pair<Int, Int>>,
    ) {
        val tags = NativeNostrWire.tags(vote["tags"]) ?: return
        val targets = tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
        val statuses = tags.filter { it.size >= 2 && it[0] == "status" }.map { it[1] }
        // Exactly one report and one outcome: anything else is malformed.
        if (targets.size != 1 || statuses.size != 1) return
        if (!counted.add("${targets.single()}:${vote["pubkey"]}")) return
        val (up, down) = into[targets.single()] ?: (0 to 0)
        into[targets.single()] = when (statuses.single()) {
            STILL_THERE -> (up + 1) to down
            NO_LONGER_THERE -> up to (down + 1)
            else -> return
        }
    }

    /** Pending speed-limit suggestions for [eventId], merged across relays. */
    suspend fun editRequests(eventId: String): List<NativeEditRequest> {
        if (!NativeNostrWire.isHex32(eventId)) return emptyList()
        val perRelay = coroutineScope {
            relays.map { url ->
                async {
                    val out = ArrayList<NativeEditRequest>()
                    fetcher.fetch(
                        url = url,
                        subscriptionId = NativeNostrWire.randomSubscriptionId(),
                        filter = linkedMapOf("kinds" to listOf(1318), "#e" to listOf(eventId), "limit" to 100),
                        kind = 1318,
                        route = NostrIngressRoute.EDIT_REQUEST,
                        maxEvents = 100,
                        accept = { event ->
                            if (NativeNostrWire.verify(event)) parseEditRequest(event, eventId)?.let(out::add)
                            false
                        },
                    )
                    out
                }
            }.awaitAll()
        }
        val merged = LinkedHashMap<String, NativeEditRequest>()
        perRelay.flatten().forEach { merged[it.id] = it }
        return merged.values.toList()
    }

    private fun parseEditRequest(event: Map<String, Any?>, eventId: String): NativeEditRequest? {
        val target = NativeNostrWire.tagValue(event, "e")
        val limit = NativeNostrWire.tagValue(event, "maxspeed")?.toIntOrNull()
        if (target != eventId || limit == null || limit !in 5..300) return null
        val id = event["id"] as? String ?: return null
        val requester = event["pubkey"] as? String ?: return null
        val comment = (event["content"] as? String).orEmpty().take(MAX_COMMENT)
        return NativeEditRequest(
            id = id,
            eventId = target,
            requesterPubkey = requester,
            speedLimitKmh = limit,
            comment = comment,
            createdAt = NativeNostrWire.integral(event["created_at"]) ?: 0L,
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 500
        const val HISTORY_SECONDS = 365L * 86_400L
        const val VOTE_BATCH = 200
        const val VOTES_PER_BATCH = 500
        const val QUERY_TIMEOUT_MILLIS = 8_000L
        const val MAX_COMMENT = 500
        const val STILL_THERE = "still_there"
        const val NO_LONGER_THERE = "no_longer_there"
    }
}
