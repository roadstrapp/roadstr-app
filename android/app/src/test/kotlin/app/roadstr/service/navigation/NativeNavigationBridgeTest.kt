package app.roadstr.service.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationBridgeTest {
    @Test
    fun `bridge exposes only explicit foreground GPS commands`() {
        assertTrue(NativeNavigationBridge.isSupportedMethod("startForegroundGps"))
        assertTrue(NativeNavigationBridge.isSupportedMethod("stopForegroundGps"))
        assertFalse(NativeNavigationBridge.isSupportedMethod("requestPermission"))
        assertFalse(NativeNavigationBridge.isSupportedMethod("start"))
    }
}
