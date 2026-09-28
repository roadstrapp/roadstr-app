package app.roadstr.service.navigation

import app.roadstr.service.location.NativeLocationFix
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeNavigationFixDispatcherTest {
    @Test
    fun `fix encoding preserves the complete normalized payload`() {
        val fix = fix(headingDegrees = null)

        assertEquals(
            linkedMapOf<String, Any?>(
                "latitude" to 45.0,
                "longitude" to 9.0,
                "speedKmh" to 36.0,
                "accuracy" to 4.5,
                "heading" to null,
                "altitude" to 120.0,
                "timestampMillis" to 1_700_000_000_000L,
            ),
            NativeNavigationFixEvent.encode(fix),
        )
    }

    @Test
    fun `dispatcher isolates listeners and cancellation`() {
        val first = mutableListOf<NativeLocationFix>()
        val second = mutableListOf<NativeLocationFix>()
        val failing = NativeNavigationFixDispatcher.subscribe { error("detached engine") }
        val firstSubscription = NativeNavigationFixDispatcher.subscribe(first::add)
        val secondSubscription = NativeNavigationFixDispatcher.subscribe(second::add)

        try {
            NativeNavigationFixDispatcher.publish(fix(latitude = 45.1))
            firstSubscription.cancel()
            NativeNavigationFixDispatcher.publish(fix(latitude = 45.2))

            assertEquals(listOf(45.1), first.map(NativeLocationFix::latitude))
            assertEquals(listOf(45.1, 45.2), second.map(NativeLocationFix::latitude))
        } finally {
            failing.cancel()
            firstSubscription.cancel()
            secondSubscription.cancel()
        }
    }

    private fun fix(
        latitude: Double = 45.0,
        headingDegrees: Double? = 90.0,
    ) = NativeLocationFix(
        latitude = latitude,
        longitude = 9.0,
        speedKilometresPerHour = 36.0,
        accuracyMeters = 4.5,
        headingDegrees = headingDegrees,
        altitudeMeters = 120.0,
        timestampMillis = 1_700_000_000_000L,
    )
}
