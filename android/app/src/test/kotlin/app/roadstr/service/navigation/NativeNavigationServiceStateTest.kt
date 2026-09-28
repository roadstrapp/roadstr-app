package app.roadstr.service.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationServiceStateTest {
    @Test
    fun `foreground GPS state follows service lifecycle markers`() {
        NativeNavigationServiceState.markForegroundGpsStopped()
        try {
            assertFalse(NativeNavigationServiceState.isForegroundGpsRunning())

            NativeNavigationServiceState.markForegroundGpsStarted()
            assertTrue(NativeNavigationServiceState.isForegroundGpsRunning())

            NativeNavigationServiceState.markForegroundGpsStopped()
            assertFalse(NativeNavigationServiceState.isForegroundGpsRunning())
        } finally {
            NativeNavigationServiceState.markForegroundGpsStopped()
        }
    }
}
