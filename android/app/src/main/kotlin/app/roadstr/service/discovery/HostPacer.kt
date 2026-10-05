package app.roadstr.service.discovery

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps calls to one host at least [minIntervalMillis] apart. Calls run one at a
 * time; a call that arrives early waits. The clock and the wait are injectable so
 * the spacing can be tested without sleeping.
 */
class HostPacer(
    private val minIntervalMillis: Long,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastStart: Long? = null

    suspend fun <T> paced(block: suspend () -> T): T = mutex.withLock {
        val last = lastStart
        if (last != null) {
            val wait = last + minIntervalMillis - now()
            if (wait > 0) pause(wait)
        }
        lastStart = now()
        block()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
