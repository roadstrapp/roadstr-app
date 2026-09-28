package app.roadstr.service.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationBridgeTest {
    @Test
    fun `bridge exposes only explicit foreground GPS commands and state`() {
        assertTrue(NativeNavigationBridge.FIXES_CHANNEL.endsWith("native_navigation_fixes"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("startForegroundGps"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("stopForegroundGps"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("isForegroundGpsRunning"))
        assertFalse(NativeNavigationBridge.isSupportedMethod("requestPermission"))
        assertFalse(NativeNavigationBridge.isSupportedMethod("start"))
    }
}
