package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeMapLifecycleTest {
    @Test
    fun `host contract pins audited renderer and Flutter camera baseline`() {
        assertEquals("13.5.2", NativeMapHostContract.MAPLIBRE_VERSION)
        assertEquals(42.5, NativeMapHostContract.INITIAL_LATITUDE, 0.0)
        assertEquals(12.5, NativeMapHostContract.INITIAL_LONGITUDE, 0.0)
        assertEquals(17.0, NativeMapHostContract.INITIAL_ZOOM, 0.0)
        assertEquals(40.0, NativeMapHostContract.INITIAL_TILT, 0.0)
        assertEquals(30, NativeMapHostContract.MAXIMUM_RENDER_FPS)
        assertEquals(false, NativeMapHostContract.USE_TEXTURE_RENDERER)
    }

    @Test
    fun `normal lifecycle reaches MapView in exact order`() {
        val target = RecordingTarget()
        val controller = NativeMapLifecycleController(target)

        listOf(
            NativeMapLifecycleEvent.Create,
            NativeMapLifecycleEvent.Start,
            NativeMapLifecycleEvent.Resume,
            NativeMapLifecycleEvent.Pause,
            NativeMapLifecycleEvent.Stop,
            NativeMapLifecycleEvent.Destroy,
        ).forEach(controller::handle)

        assertEquals(
            listOf("create", "start", "resume", "pause", "stop", "destroy"),
            target.calls,
        )
        assertEquals(NativeMapLifecycleState.Destroyed, controller.state)
    }

    @Test
    fun `dispose from resumed state unwinds once`() {
        val target = RecordingTarget()
        val controller = NativeMapLifecycleController(target)

        controller.handle(NativeMapLifecycleEvent.Resume)
        controller.handle(NativeMapLifecycleEvent.Destroy)
        controller.handle(NativeMapLifecycleEvent.Stop)
        controller.handle(NativeMapLifecycleEvent.Destroy)

        assertEquals(
            listOf("create", "start", "resume", "pause", "stop", "destroy"),
            target.calls,
        )
    }

    @Test
    fun `stopped renderer can restart before final destroy`() {
        val target = RecordingTarget()
        val controller = NativeMapLifecycleController(target)

        controller.handle(NativeMapLifecycleEvent.Start)
        controller.handle(NativeMapLifecycleEvent.Stop)
        controller.handle(NativeMapLifecycleEvent.Resume)
        controller.handle(NativeMapLifecycleEvent.Destroy)

        assertEquals(
            listOf("create", "start", "stop", "start", "resume", "pause", "stop", "destroy"),
            target.calls,
        )
    }

    @Test
    fun `low memory reaches only a live created renderer`() {
        val target = RecordingTarget()
        val controller = NativeMapLifecycleController(target)

        controller.handle(NativeMapLifecycleEvent.LowMemory)
        controller.handle(NativeMapLifecycleEvent.Create)
        controller.handle(NativeMapLifecycleEvent.LowMemory)
        controller.handle(NativeMapLifecycleEvent.Destroy)
        controller.handle(NativeMapLifecycleEvent.LowMemory)

        assertEquals(listOf("create", "lowMemory", "destroy"), target.calls)
    }

    @Test
    fun `destroy before creation does not initialize renderer`() {
        val target = RecordingTarget()
        val controller = NativeMapLifecycleController(target)

        controller.handle(NativeMapLifecycleEvent.Destroy)
        controller.handle(NativeMapLifecycleEvent.Create)

        assertEquals(emptyList<String>(), target.calls)
        assertEquals(NativeMapLifecycleState.Destroyed, controller.state)
    }

    private class RecordingTarget : NativeMapLifecycleTarget {
        val calls = mutableListOf<String>()

        override fun create() = record("create")
        override fun start() = record("start")
        override fun resume() = record("resume")
        override fun pause() = record("pause")
        override fun stop() = record("stop")
        override fun destroy() = record("destroy")
        override fun lowMemory() = record("lowMemory")

        private fun record(value: String) {
            calls += value
        }
    }
}
