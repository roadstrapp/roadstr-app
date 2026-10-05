package app.roadstr.service.discovery

/**
 * A small in-memory cache with a size cap and an expiry. Nothing here is ever
 * written to disk: the keys are typed places and queries.
 */
class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean =
            size > maxEntries
    }

    init {
        require(maxEntries > 0 && ttlMillis > 0) { "Cache bounds must be positive" }
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (now() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, now() + ttlMillis)
    }

    @Synchronized
    fun clear() = entries.clear()
}
