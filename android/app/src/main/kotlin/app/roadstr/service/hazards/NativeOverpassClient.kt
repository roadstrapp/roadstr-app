package app.roadstr.service.hazards

import app.roadstr.core.network.SearchProviderProtocol
import app.roadstr.core.network.SearchResponseProtocol
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.IOException
import java.time.Duration
import java.util.Locale

/** A mirror answered with something other than 200. */
class NativeOverpassException(val statusCode: Int) : IOException("Overpass HTTP $statusCode")

/**
 * Access to the public Overpass mirrors, one instance per service so one
 * service exhausting a mirror does not move the others off a working one.
 *
 * Port of the Flutter OverpassClient: same mirror order, same exponential
 * back-off, with a throttled mirror (429/503/504) starting the ramp four
 * times higher. The mirrors are free and volunteer-run; an app that retries at
 * a fixed rate no matter how loudly the server says no is a bad neighbour.
 */
class NativeOverpassClient(
    private val transport: NativeSearchHttpTransport,
    private val mirrors: List<String> = SearchProviderProtocol.overpassMirrors,
) {
    private var index = 0
    private var failureStreak = 0
    private var lastFailureWasThrottle = false

    init {
        require(mirrors.isNotEmpty()) { "At least one Overpass mirror is required" }
    }

    /** How many different mirrors one fetch may go through before it gives up. */
    val mirrorCount: Int get() = mirrors.size

    /**
     * Formats a latitude or longitude for an `around:` clause. Plain
     * `toString()` can print 5.0E-7, which Overpass rejects as a syntax error.
     */
    fun coord(value: Double): String = String.format(Locale.ROOT, "%.7f", value)

    @Synchronized
    fun noteSuccess() {
        failureStreak = 0
        lastFailureWasThrottle = false
    }

    @Synchronized
    fun noteFailure(error: Throwable?) {
        if (failureStreak < MAX_STREAK) failureStreak++
        val status = (error as? NativeOverpassException)?.statusCode ?: 0
        lastFailureWasThrottle = status == 429 || status == 503 || status == 504
    }

    @Synchronized
    fun failureBackoff(
        base: Duration = Duration.ofSeconds(15),
        max: Duration = Duration.ofMinutes(5),
    ): Duration {
        if (failureStreak == 0) return Duration.ZERO
        val start = if (lastFailureWasThrottle) base.multipliedBy(4) else base
        val grown = start.multipliedBy(1L shl (failureStreak - 1))
        return if (grown > max) max else grown
    }

    @Synchronized
    fun rotate() {
        index = (index + 1) % mirrors.size
    }

    @Synchronized
    private fun currentMirror(): String = mirrors[index % mirrors.size]

    /**
     * Runs [query] against the current mirror and returns its `elements`.
     * Throws on a transport failure or a non-200 status, so a caller that
     * caches "nothing here" can tell a real empty answer from a dead mirror.
     */
    suspend fun fetchElements(
        query: String,
        maxBytes: Long,
        timeoutMillis: Long,
    ): List<Map<String, Any?>> {
        val response = transport.execute(
            SearchProviderProtocol.overpass(currentMirror(), query),
            NativeHttpRequestLimits(timeoutMillis = timeoutMillis, maxResponseBytes = maxBytes),
        )
        if (response.statusCode != 200) throw NativeOverpassException(response.statusCode)
        return SearchResponseProtocol.parseOverpassElements(response.bodyUtf8)
    }

    private companion object {
        const val MAX_STREAK = 8
    }
}
