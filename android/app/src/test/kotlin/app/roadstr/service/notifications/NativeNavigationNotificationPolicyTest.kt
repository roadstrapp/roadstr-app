package app.roadstr.service.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationNotificationPolicyTest {
    @Test
    fun `first instruction posts immediately with private ongoing defaults`() {
        val policy = NativeNavigationNotificationPolicy()

        val update = policy.nextUpdate("Turn right", "200 m", nowMillis = 0L)

        assertEquals("Turn right", update!!.title)
        assertEquals("200 m", update.body)
        assertEquals(42, update.id)
        assertEquals("roadstr_navigation", update.channelId)
        assertTrue(update.ongoing)
        assertTrue(update.onlyAlertOnce)
        assertFalse(update.autoCancel)
        assertEquals(NativeNotificationVisibility.Private, update.visibility)
    }

    @Test
    fun `same instruction throttles distance-only updates for three seconds`() {
        val policy = NativeNavigationNotificationPolicy()
        policy.nextUpdate("Turn right", "200 m", nowMillis = 1_000L)

        assertNull(policy.nextUpdate("Turn right", "190 m", nowMillis = 3_999L))
        assertEquals(
            "180 m",
            policy.nextUpdate("Turn right", "180 m", nowMillis = 4_000L)!!.body,
        )
        assertNull(policy.nextUpdate("Turn right", "180 m", nowMillis = 10_000L))
    }

    @Test
    fun `new instruction bypasses distance throttle and reset reopens first update`() {
        val policy = NativeNavigationNotificationPolicy()
        policy.nextUpdate("Turn right", "200 m", nowMillis = 1_000L)

        assertEquals(
            "Turn left",
            policy.nextUpdate("Turn left", "190 m", nowMillis = 1_100L)!!.title,
        )
        policy.reset()
        assertEquals(
            "Continue",
            policy.nextUpdate("Continue", "1 km", nowMillis = 1_200L)!!.title,
        )
    }
}
