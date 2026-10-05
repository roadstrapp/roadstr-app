package app.roadstr.service.discovery

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostPacerAndCacheTest {
    @Test
    fun `calls are spaced by the minimum interval`() = runBlocking {
        var clock = 10_000L
        val waits = mutableListOf<Long>()
        val pacer = HostPacer(1_100, now = { clock }, pause = { waits += it; clock += it })

        pacer.paced { clock += 200 }
        pacer.paced { clock += 50 }
        clock += 5_000
        pacer.paced { }

        assertEquals(listOf(900L), waits)
    }

    @Test
    fun `the first call never waits`() = runBlocking {
        val waits = mutableListOf<Long>()
        HostPacer(1_100, now = { 0L }, pause = { waits += it }).paced { }
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun `a result is returned from the paced block`() = runBlocking {
        assertEquals(7, HostPacer(10, now = { 0L }, pause = { }).paced { 7 })
    }

    @Test
    fun `entries expire`() {
        var clock = 0L
        val cache = TtlCache<String, String>(maxEntries = 4, ttlMillis = 100, now = { clock })
        cache.put("a", "1")
        clock = 99
        assertEquals("1", cache.get("a"))
        clock = 100
        assertNull(cache.get("a"))
    }

    @Test
    fun `the oldest unused entry goes first when full`() {
        val cache = TtlCache<String, Int>(maxEntries = 2, ttlMillis = 1_000, now = { 0L })
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = TtlCache<String, Int>(2, 1_000, now = { 0L })
        cache.put("a", 1)
        cache.clear()
        assertNull(cache.get("a"))
    }
}
