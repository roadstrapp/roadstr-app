package app.roadstr.feature.navigation

import kotlin.math.min

/**
 * Wait before an automatic reroute may follow a failed one.
 *
 * Off-route detection runs on every fix, about twice a second. Without a pause
 * a routing outage or a dead zone turned each failure into an immediate retry:
 * a request stream against the free public OSRM instance and a busy radio for
 * nothing. Mirrors the Flutter RerouteBackoff: 5, 10, 20, then 30 seconds.
 */
class NativeRerouteBackoff {
    private var failures = 0

    fun nextDelayMillis(): Long {
        val delay = DELAYS_MILLIS[min(failures, DELAYS_MILLIS.lastIndex)]
        failures++
        return delay
    }

    fun reset() {
        failures = 0
    }

    private companion object {
        val DELAYS_MILLIS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)
    }
}
