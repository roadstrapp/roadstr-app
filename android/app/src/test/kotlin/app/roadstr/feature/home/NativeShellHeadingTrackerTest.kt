package app.roadstr.feature.home

import app.roadstr.core.navigation.HeadingFilter
import app.roadstr.feature.map.NativeMapPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeShellHeadingTrackerTest {
    private var now = 0L
    private val tracker = NativeShellHeadingTracker(HeadingFilter { now })

    private fun fix(
        latitude: Double,
        speedMetersPerSecond: Double,
        providerHeading: Double?,
    ): Double {
        now += 1_000L
        return tracker.update(
            point = NativeMapPoint(latitude, 12.0),
            speedMetersPerSecond = speedMetersPerSecond,
            accuracyMeters = 5.0,
            providerHeadingDegrees = providerHeading,
            navigating = false,
        )
    }

    @Test
    fun `a stop without a provider bearing keeps the last travel heading`() {
        // Driving due south at ~50 km/h, 14 m per fix.
        var latitude = 45.0
        repeat(4) {
            latitude -= 0.000125
            fix(latitude, speedMetersPerSecond = 14.0, providerHeading = 180.0)
        }
        assertEquals(180.0, tracker.headingDegrees, 1.0)

        // Red light: Android stops reporting a bearing. The map must not
        // turn north, which is what the raw `heading ?: 0.0` did.
        repeat(5) { fix(latitude, speedMetersPerSecond = 0.0, providerHeading = null) }
        assertEquals(180.0, tracker.headingDegrees, 1.0)
    }

    @Test
    fun `jitter at walking pace does not swing the heading`() {
        var latitude = 45.0
        repeat(4) {
            latitude += 0.000125
            fix(latitude, speedMetersPerSecond = 14.0, providerHeading = 0.5)
        }
        val settled = tracker.headingDegrees

        // Below the travel-heading speed the noisy provider course is ignored
        // in favour of the held bearing unless it is a usable value.
        fix(latitude + 0.000001, speedMetersPerSecond = 0.5, providerHeading = null)
        assertEquals(settled, tracker.headingDegrees, 0.0)
    }
}
