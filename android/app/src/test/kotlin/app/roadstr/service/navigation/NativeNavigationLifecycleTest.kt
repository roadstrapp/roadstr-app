package app.roadstr.service.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationLifecycleTest {
    @Test
    fun `foreground GPS request starts once and exposes foreground requirement after start`() {
        val lifecycle = NativeNavigationLifecycle()

        assertEquals(
            listOf(NativeNavigationEffect.StartGps),
            lifecycle.requestGps().effects,
        )
        assertTrue(lifecycle.onGpsStarted().state.foregroundLocationRequired)
        assertTrue(lifecycle.state.gpsRequested)
        assertTrue(lifecycle.requestGps().effects.isEmpty())
    }

    @Test
    fun `navigation keeps GPS and wakelock alive while app is paused`() {
        val lifecycle = NativeNavigationLifecycle(keepScreenOn = true)
        lifecycle.startNavigation()
        lifecycle.onGpsStarted()

        val paused = lifecycle.onAppPaused(nowMillis = 1_000L)

        assertTrue(paused.state.navigationActive)
        assertTrue(paused.state.gpsActive)
        assertTrue(paused.state.foregroundLocationRequired)
        assertTrue(paused.state.wakelockRequired)
        assertEquals(null, paused.state.idleStopAtMillis)
        assertFalse(paused.effects.contains(NativeNavigationEffect.StopGps))
    }

    @Test
    fun `non-navigation pause stops GPS only after grace period`() {
        val lifecycle = NativeNavigationLifecycle(backgroundGraceMillis = 30_000L)
        lifecycle.requestGps()
        lifecycle.onGpsStarted()

        val paused = lifecycle.onAppPaused(nowMillis = 10_000L)
        assertEquals(40_000L, paused.state.idleStopAtMillis)
        assertTrue(paused.state.gpsActive)

        assertTrue(lifecycle.onGracePeriodElapsed(39_999L).effects.isEmpty())
        val stopped = lifecycle.onGracePeriodElapsed(40_000L)
        assertEquals(listOf(NativeNavigationEffect.StopGps), stopped.effects)
        assertFalse(stopped.state.gpsRequested)
        assertFalse(stopped.state.gpsActive)
    }

    @Test
    fun `resume cancels pending stop and restarts only when GPS was stopped`() {
        val lifecycle = NativeNavigationLifecycle()
        lifecycle.requestGps()
        lifecycle.onGpsStarted()
        val paused = lifecycle.onAppPaused(0L)
        val resumed = lifecycle.onAppResumed()

        assertTrue(resumed.state.appVisible)
        assertEquals(null, resumed.state.idleStopAtMillis)
        assertTrue(resumed.effects.isEmpty())
        assertTrue(lifecycle.onGracePeriodElapsed(30_000L, paused.state.lifecycleGeneration).effects.isEmpty())

        lifecycle.onGpsStopped()
        assertEquals(
            listOf(NativeNavigationEffect.StartGps),
            lifecycle.onAppResumed().effects,
        )
    }

    @Test
    fun `stale grace callback from an older lifecycle generation is ignored`() {
        val lifecycle = NativeNavigationLifecycle()
        lifecycle.requestGps()
        lifecycle.onGpsStarted()
        val paused = lifecycle.onAppPaused(0L)
        lifecycle.onAppResumed()
        lifecycle.onAppPaused(10_000L)

        val ignored = lifecycle.onGracePeriodElapsed(40_000L, paused.state.lifecycleGeneration)

        assertTrue(ignored.effects.isEmpty())
        assertTrue(lifecycle.state.gpsActive)
    }

    @Test
    fun `detach releases GPS and wakelock immediately`() {
        val lifecycle = NativeNavigationLifecycle()
        lifecycle.startNavigation()
        lifecycle.onGpsStarted()

        val detached = lifecycle.onDetached()

        assertEquals(
            listOf(
                NativeNavigationEffect.StopGps,
                NativeNavigationEffect.DisableWakelock,
            ),
            detached.effects,
        )
        assertFalse(detached.state.navigationActive)
        assertFalse(detached.state.gpsActive)
        assertFalse(detached.state.wakelockRequired)
    }

    @Test
    fun `screen policy is foreground-only unless navigation keeps it awake`() {
        val lifecycle = NativeNavigationLifecycle(keepScreenOn = false, keepScreenOnAlways = true)
        assertTrue(lifecycle.requestGps().effects.contains(NativeNavigationEffect.EnableWakelock))

        lifecycle.onAppPaused(0L)
        assertFalse(lifecycle.state.wakelockRequired)

        lifecycle.startNavigation()
        assertFalse(lifecycle.state.wakelockRequired)
        val updated = lifecycle.updateScreenPolicy(keepScreenOn = true, keepScreenOnAlways = false)
        assertTrue(updated.state.wakelockRequired)
    }
}
