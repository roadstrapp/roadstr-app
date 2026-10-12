package app.roadstr.service.meshcore

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.service.nostr.NativeNostrWire
import app.roadstr.service.nostr.NativePendingReportStorage

/**
 * Events heard on the radio that still have to reach the Nostr relays because there was no
 * connection when they arrived.
 *
 * Bounded (oldest dropped), persisted by the caller's storage, cleared on demand, and every entry
 * carries the time after which it is no longer worth publishing: a road report that surfaces hours
 * late would mislead more than it helps. Events are kept exactly as received, with their original
 * author, ID and signature; nothing is ever re-signed here.
 */
class MeshRepublishQueue(
    private val storage: NativePendingReportStorage?,
    private val nowSeconds: () -> Long,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val lock = Any()
    private var flushing = false

    init {
        require(capacity in 1..MAX_CAPACITY) { "queue capacity must be 1..$MAX_CAPACITY" }
    }

    /** Adds [event] unless it is already waiting. Returns false if nowhere to keep it. */
    fun enqueue(event: Map<String, Any?>, expiresAt: Long): Boolean = synchronized(lock) {
        val id = event["id"] as? String ?: return false
        val target = storage ?: return false
        if (expiresAt <= nowSeconds()) return false
        val rows = readRows()
        if (rows.any { idOf(it) == id }) return true
        val row = NostrJson.encode(linkedMapOf("event" to event, "expiresAt" to expiresAt))
        runCatching { target.write(shrink(rows + row)) }.isSuccess
    }

    fun size(): Int = synchronized(lock) { readRows().size }

    fun clear() = synchronized(lock) {
        runCatching { storage?.write(emptyList()) }
        Unit
    }

    /**
     * Offers the waiting events to [publish] one after another, dropping the stale and the
     * unverifiable. [onPublished] gets the ID of each one a relay accepted. One pass at a time.
     * Returns how many were published.
     */
    suspend fun flush(publish: suspend (Map<String, Any?>) -> Boolean, onPublished: (String) -> Unit): Int {
        val snapshot = synchronized(lock) {
            if (flushing || storage == null) return 0
            flushing = true
            readRows()
        }
        var published = 0
        try {
            val remaining = ArrayList<String>()
            for (row in snapshot) {
                val entry = parse(row) ?: continue
                if (entry.second <= nowSeconds() || !NativeNostrWire.verify(entry.first)) continue
                if (publish(entry.first)) {
                    published++
                    (entry.first["id"] as? String)?.let(onPublished)
                } else {
                    remaining += row
                }
            }
            synchronized(lock) {
                // Rows added while the pass was running must survive it.
                val added = readRows().filter { it !in snapshot.toHashSet() }
                runCatching { storage?.write(shrink(remaining + added)) }
            }
        } finally {
            synchronized(lock) { flushing = false }
        }
        return published
    }

    private fun readRows(): List<String> = runCatching { storage?.read() }.getOrNull().orEmpty()

    private fun shrink(rows: List<String>): List<String> = rows.takeLast(capacity)

    private fun idOf(row: String): String? = parse(row)?.first?.get("id") as? String

    private fun parse(row: String): Pair<Map<String, Any?>, Long>? = runCatching {
        val map = BoundedJsonParser(row).parse() as? Map<*, *> ?: return null
        @Suppress("UNCHECKED_CAST")
        val event = map["event"] as? Map<String, Any?> ?: return null
        val expiresAt = NativeNostrWire.integral(map["expiresAt"]) ?: return null
        event to expiresAt
    }.getOrNull()

    companion object {
        const val DEFAULT_CAPACITY = 100
        const val MAX_CAPACITY = 500
    }
}
