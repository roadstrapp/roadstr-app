package app.roadstr.service.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationBridgeTest {
    @Test
    fun `bridge exposes only explicit foreground GPS commands and state`() {
        assertTrue(NativeNavigationBridge.FIXES_CHANNEL.endsWith("native_navigation_fixes"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("startForegroundGps"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("stopForegroundGps"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("isForegroundGpsRunning"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("updateNavigationNotification"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("resetNavigationNotification"))
        assertFalse(NativeNavigationBridge.isSupportedMethod("requestPermission"))
        assertFalse(NativeNavigationBridge.isSupportedMethod("start"))
    }

    @Test
    fun `notification update parser accepts only bounded text maps`() {
        assertEquals(
            NativeNavigationNotificationUpdate("Turn right", "200 m"),
            NativeNavigationBridge.parseNotificationUpdate(
                mapOf("instruction" to "Turn right", "distance" to "200 m"),
            ),
        )
        assertNull(
            NativeNavigationBridge.parseNotificationUpdate(
                mapOf("instruction" to "", "distance" to "200 m"),
            ),
        )
        assertNull(
            NativeNavigationBridge.parseNotificationUpdate(
                mapOf("instruction" to "Turn right", "distance" to "x".repeat(65)),
            ),
        )
        assertNull(NativeNavigationBridge.parseNotificationUpdate("Turn right"))
    }
}
