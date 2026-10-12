package app.roadstr.core.protocol.interop

/** A medium an event can travel on. The same signed event is the same event on every one of them. */
enum class RoadEventTransport(val bit: Int) {
    RELAY(1),
    MESH(2),
}

data class RoadEventObservation(
    /** Never seen on any transport before. */
    val firstSighting: Boolean,
    /** Not seen on this particular transport before. */
    val newOnThisTransport: Boolean,
    val alsoSeenOn: Set<RoadEventTransport>,
)

/**
 * Bounded memory of which signed events were seen, and sent, on which transport.
 *
 * Identity is the Nostr event ID, so an event is one event whether it came from a relay, from the
 * radio, or both. The ledger is also what makes a bridge safe: an event is only offered to a
 * transport that has not seen it and that we have not already fed, which is what stops
 * relay -> radio -> relay -> radio from turning into a storm.
 *
 * Least-recently-used entries are dropped past [capacity]; forgetting an event can only cost one
 * redundant forward, never a loop, because forwards are also rate limited.
 */
class RoadEventLedger(private val capacity: Int = DEFAULT_CAPACITY) {
    private class Entry(var seen: Int, var forwarded: Int)

    init {
        require(capacity in 1..MAX_CAPACITY) { "ledger capacity must be 1..$MAX_CAPACITY" }
    }

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > capacity
    }

    @Synchronized
    fun observe(eventId: String, transport: RoadEventTransport): RoadEventObservation {
        val existing = entries[eventId]
        val others = existing?.seen?.let(::transportsOf).orEmpty()
        val entry = existing ?: Entry(0, 0).also { entries[eventId] = it }
        val already = entry.seen and transport.bit != 0
        entry.seen = entry.seen or transport.bit
        return RoadEventObservation(
            firstSighting = existing == null,
            newOnThisTransport = !already,
            alsoSeenOn = others - transport,
        )
    }

    @Synchronized
    fun seenOn(eventId: String, transport: RoadEventTransport): Boolean =
        (entries[eventId]?.seen ?: 0) and transport.bit != 0

    /** Records that we handed the event to [transport] ourselves. */
    @Synchronized
    fun markForwarded(eventId: String, transport: RoadEventTransport) {
        val entry = entries.getOrPut(eventId) { Entry(0, 0) }
        entry.forwarded = entry.forwarded or transport.bit
    }

    @Synchronized
    fun wasForwarded(eventId: String, transport: RoadEventTransport): Boolean =
        (entries[eventId]?.forwarded ?: 0) and transport.bit != 0

    /** Worth offering to [to]: it has not been seen there and we did not already put it there. */
    @Synchronized
    fun shouldForward(eventId: String, to: RoadEventTransport): Boolean =
        !seenOn(eventId, to) && !wasForwarded(eventId, to)

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clear() = entries.clear()

    /** `id:seen:forwarded` lines, oldest first, for persisting between launches. */
    @Synchronized
    fun snapshot(): List<String> = entries.map { (id, entry) -> "$id:${entry.seen}:${entry.forwarded}" }

    /**
     * The part worth keeping across a restart: what has touched the radio, or was put on the relays
     * by a bridge. What a relay delivered can be learned again, because relays resend their recent
     * events at every connection; what the radio carried cannot, and forgetting it would put the
     * same events on the air again at every launch. At most [maxLines], newest kept.
     */
    @Synchronized
    fun snapshotRadioInvolved(maxLines: Int): List<String> = entries
        .filter { (_, entry) ->
            val radio = RoadEventTransport.MESH.bit
            (entry.seen or entry.forwarded) and radio != 0 || entry.forwarded and RoadEventTransport.RELAY.bit != 0
        }
        .map { (id, entry) -> "$id:${entry.seen}:${entry.forwarded}" }
        .takeLast(maxLines)

    /** Loads a [snapshot]; lines that are not exactly a 64-hex ID and two masks are ignored. */
    @Synchronized
    fun restore(lines: List<String>) {
        for (line in lines.takeLast(capacity)) {
            val parts = line.split(':')
            val seen = parts.getOrNull(1)?.toIntOrNull()
            val forwarded = parts.getOrNull(2)?.toIntOrNull()
            val valid = parts.size == 3 && parts[0].matches(HEX_64) && seen in 0..ALL_BITS && forwarded in 0..ALL_BITS
            if (valid) entries[parts[0]] = Entry(seen!!, forwarded!!)
        }
    }

    private fun transportsOf(mask: Int): Set<RoadEventTransport> =
        RoadEventTransport.entries.filter { mask and it.bit != 0 }.toSet()

    companion object {
        const val DEFAULT_CAPACITY = 2_048
        const val MAX_CAPACITY = 16_384
        private const val ALL_BITS = 3
        private val HEX_64 = Regex("^[0-9a-f]{64}\$")
    }
}

/**
 * Spends one unit of every limiter, or none: a refusal by one of them must not eat the quota of
 * the others.
 */
fun tryAcquireAll(nowSeconds: Long, vararg limiters: BridgeRateLimiter): Boolean {
    synchronized(LOCK) {
        if (!limiters.all { it.canAcquire(nowSeconds) }) return false
        limiters.forEach { it.tryAcquire(nowSeconds) }
        return true
    }
}

private val LOCK = Any()

/**
 * Sliding-window cap on how many events a bridge may emit, so a burst on one side can never become
 * a flood on the other (an LoRa channel is shared airtime: a hundred packets is minutes of it).
 */
class BridgeRateLimiter(
    private val maxEvents: Int,
    private val windowSeconds: Long,
) {
    private val stamps = ArrayDeque<Long>()

    init {
        require(maxEvents >= 0 && windowSeconds > 0) { "invalid rate limit" }
    }

    @Synchronized
    fun tryAcquire(nowSeconds: Long): Boolean {
        if (!canAcquire(nowSeconds)) return false
        stamps.addLast(nowSeconds)
        return true
    }

    /** Whether [tryAcquire] would succeed now, without spending anything. */
    @Synchronized
    fun canAcquire(nowSeconds: Long): Boolean {
        while (stamps.isNotEmpty() && nowSeconds - stamps.first() >= windowSeconds) stamps.removeFirst()
        return stamps.size < maxEvents
    }

    @Synchronized
    fun reset() = stamps.clear()
}
