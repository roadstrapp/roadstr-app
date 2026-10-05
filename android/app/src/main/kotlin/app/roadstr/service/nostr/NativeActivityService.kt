package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.feature.activity.NativeActivityCursorKind
import app.roadstr.feature.activity.NativeActivityCursorProtocol
import app.roadstr.feature.activity.NativeActivityNotification
import app.roadstr.feature.activity.NativeActivityNotificationType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Where the inbox and its two "seen up to here" cursors survive a restart. */
interface NativeActivityStore {
    /** Normalised inbox of [pubkey], as `NativeActivityInboxProtocol` writes it. */
    fun readInbox(pubkey: String): String?

    fun writeInbox(pubkey: String, normalized: String)

    fun readCursor(key: String): String?

    fun writeCursor(key: String, value: String)
}

/**
 * What happened to this account's own reports while the app was closed: zaps
 * received and confirmations or denials of its reports. A port of the
 * Flutter activity notifications, run as a poll rather than a standing
 * subscription: the inbox is deliberately silent (nothing may compete with
 * turn-by-turn guidance), so a check at launch and every few minutes while the
 * map is open is as good as a live feed and keeps no socket open.
 *
 * Both cursors start at "now" the first time, so years of old zaps do not all
 * arrive at once, and only move forward.
 */
class NativeActivityService(
    private val zaps: NativeZapService,
    private val reports: NativeUserReportsService,
    connector: NativeRelayConnector,
    private val store: NativeActivityStore,
    private val relays: List<String> = NativeRoadEventService.DEFAULT_RELAYS,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val fetcher: NativeRelayFetcher = NativeRelayFetcher(connector),
) {
    private var cachedFor: String? = null
    private var cachedReports: Map<String, NativeRoadEvent> = emptyMap()
    private var cachedUntil = 0L
    private var zapSigner: String? = null

    /**
     * What happened since the last poll, oldest first; never throws. The
     * newest notification of the previous poll can come back once more because
     * the cursor sits on its own second: the inbox drops repeats by id.
     */
    suspend fun poll(pubkey: String): List<NativeActivityNotification> = runCatching {
        if (!NativeNostrWire.isHex32(pubkey)) return emptyList()
        val own = ownReports(pubkey)
        zaps(pubkey) + votes(pubkey, own)
    }.getOrDefault(emptyList()).sortedBy(NativeActivityNotification::createdAtSeconds)

    private suspend fun ownReports(pubkey: String): Map<String, NativeRoadEvent> {
        val now = nowSeconds()
        if (cachedFor != pubkey || now >= cachedUntil) {
            cachedReports = reports.userEvents(pubkey, limit = OWN_REPORT_LIMIT).associateBy(NativeRoadEvent::id)
            cachedFor = pubkey
            cachedUntil = now + REPORT_CACHE_SECONDS
            zapSigner = null
        }
        return cachedReports
    }

    private suspend fun votes(pubkey: String, own: Map<String, NativeRoadEvent>): List<NativeActivityNotification> {
        if (own.isEmpty()) return emptyList()
        val key = NativeActivityCursorProtocol.storageKey(NativeActivityCursorKind.Confirmation, pubkey)
        val cursor = seed(key)
        val found = LinkedHashMap<String, NativeActivityNotification>()
        coroutineScope {
            relays.map { url ->
                async {
                    own.keys.toList().chunked(ID_BATCH).forEach { batch ->
                        fetcher.fetch(
                            url = url,
                            subscriptionId = NativeNostrWire.randomSubscriptionId(),
                            filter = linkedMapOf(
                                "kinds" to listOf(1316),
                                "#e" to batch,
                                "since" to cursor,
                                "limit" to PER_QUERY,
                            ),
                            kind = 1316,
                            route = NostrIngressRoute.OWN_CONFIRMATION,
                            maxEvents = PER_QUERY,
                            accept = { event ->
                                if (NativeNostrWire.verify(event)) {
                                    voteNotification(event, own)?.let { synchronized(found) { found.putIfAbsent(it.id, it) } }
                                }
                                false
                            },
                        )
                    }
                }
            }.awaitAll()
        }
        advance(NativeActivityCursorKind.Confirmation, pubkey, cursor, found.values)
        return found.values.toList()
    }

    private fun voteNotification(
        event: Map<String, Any?>,
        own: Map<String, NativeRoadEvent>,
    ): NativeActivityNotification? {
        val tags = NativeNostrWire.tags(event["tags"]) ?: return null
        val target = tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1) ?: return null
        val status = tags.firstOrNull { it.size >= 2 && it[0] == "status" }?.get(1) ?: return null
        val report = own[target] ?: return null
        // Confirming your own report is not "someone confirmed you".
        if (event["pubkey"] == report.pubkey) return null
        val type = when (status) {
            "still_there" -> NativeActivityNotificationType.Confirmed
            "no_longer_there" -> NativeActivityNotificationType.Denied
            else -> return null
        }
        val id = event["id"] as? String ?: return null
        return NativeActivityNotification(
            id = id,
            type = type,
            createdAtSeconds = NativeNostrWire.integral(event["created_at"]) ?: return null,
            category = report.category,
        )
    }

    private suspend fun zaps(pubkey: String): List<NativeActivityNotification> {
        val signer = zapSigner ?: zaps.zapSigner(pubkey)?.also { zapSigner = it } ?: return emptyList()
        val key = NativeActivityCursorProtocol.storageKey(NativeActivityCursorKind.Zap, pubkey)
        val cursor = seed(key)
        val found = LinkedHashMap<String, NativeActivityNotification>()
        coroutineScope {
            relays.map { url ->
                async {
                    fetcher.fetch(
                        url = url,
                        subscriptionId = NativeNostrWire.randomSubscriptionId(),
                        filter = linkedMapOf(
                            "kinds" to listOf(9735),
                            "#p" to listOf(pubkey),
                            "since" to cursor,
                            "limit" to PER_QUERY,
                        ),
                        kind = 9735,
                        route = NostrIngressRoute.ZAP_RECEIPT,
                        maxEvents = PER_QUERY,
                        accept = { event ->
                            val id = event["id"] as? String
                            val msat = zaps.verifiedReceiptAmount(event, null, pubkey, signer)
                            if (id != null && msat != null) {
                                val notification = NativeActivityNotification(
                                    id = id,
                                    type = NativeActivityNotificationType.Zap,
                                    createdAtSeconds = NativeNostrWire.integral(event["created_at"]) ?: 0L,
                                    amountSat = msat / 1000,
                                )
                                synchronized(found) { found.putIfAbsent(id, notification) }
                            }
                            false
                        },
                    )
                }
            }.awaitAll()
        }
        advance(NativeActivityCursorKind.Zap, pubkey, cursor, found.values)
        return found.values.toList()
    }

    private fun seed(key: String): Long {
        val stored = store.readCursor(key)
        val value = NativeActivityCursorProtocol.seed(stored, nowSeconds())
        if (stored == null) store.writeCursor(key, value.toString())
        return value
    }

    private fun advance(
        kind: NativeActivityCursorKind,
        pubkey: String,
        current: Long,
        found: Collection<NativeActivityNotification>,
    ) {
        val newest = found.maxOfOrNull(NativeActivityNotification::createdAtSeconds) ?: return
        NativeActivityCursorProtocol.advance(kind, pubkey, current, newest)
            ?.let { store.writeCursor(it.storageKey, it.value.toString()) }
    }

    private companion object {
        const val OWN_REPORT_LIMIT = 200
        const val REPORT_CACHE_SECONDS = 600L
        const val ID_BATCH = 100
        const val PER_QUERY = 100
    }
}
