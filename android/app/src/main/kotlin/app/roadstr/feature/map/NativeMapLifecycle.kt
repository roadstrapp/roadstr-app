package app.roadstr.feature.map

/** MapView lifecycle calls kept behind a host-testable boundary. */
internal interface NativeMapLifecycleTarget {
    fun create()
    fun start()
    fun resume()
    fun pause()
    fun stop()
    fun destroy()
    fun lowMemory()
}

internal enum class NativeMapLifecycleEvent {
    Create,
    Start,
    Resume,
    Pause,
    Stop,
    Destroy,
    LowMemory,
}

internal enum class NativeMapLifecycleState {
    New,
    Created,
    Started,
    Resumed,
    Destroyed,
}

/**
 * Idempotent ordering adapter for MapLibre's imperative MapView lifecycle.
 *
 * Compose disposal can race the Activity's final callbacks. Collapsing both
 * paths here prevents duplicate native destroy calls and always unwinds a live
 * renderer in resume -> pause -> stop -> destroy order.
 */
internal class NativeMapLifecycleController(
    private val target: NativeMapLifecycleTarget,
) {
    var state: NativeMapLifecycleState = NativeMapLifecycleState.New
        private set

    fun handle(event: NativeMapLifecycleEvent) {
        when (event) {
            NativeMapLifecycleEvent.Create -> create()
            NativeMapLifecycleEvent.Start -> start()
            NativeMapLifecycleEvent.Resume -> resume()
            NativeMapLifecycleEvent.Pause -> pause()
            NativeMapLifecycleEvent.Stop -> stop()
            NativeMapLifecycleEvent.Destroy -> destroy()
            NativeMapLifecycleEvent.LowMemory -> lowMemory()
        }
    }

    private fun create() {
        if (state != NativeMapLifecycleState.New) return
        target.create()
        state = NativeMapLifecycleState.Created
    }

    private fun start() {
        if (state == NativeMapLifecycleState.New) create()
        if (state != NativeMapLifecycleState.Created) return
        target.start()
        state = NativeMapLifecycleState.Started
    }

    private fun resume() {
        if (state == NativeMapLifecycleState.New || state == NativeMapLifecycleState.Created) {
            start()
        }
        if (state != NativeMapLifecycleState.Started) return
        target.resume()
        state = NativeMapLifecycleState.Resumed
    }

    private fun pause() {
        if (state != NativeMapLifecycleState.Resumed) return
        target.pause()
        state = NativeMapLifecycleState.Started
    }

    private fun stop() {
        if (state == NativeMapLifecycleState.Resumed) pause()
        if (state != NativeMapLifecycleState.Started) return
        target.stop()
        state = NativeMapLifecycleState.Created
    }

    private fun destroy() {
        if (state == NativeMapLifecycleState.Destroyed) return
        if (state == NativeMapLifecycleState.New) {
            state = NativeMapLifecycleState.Destroyed
            return
        }
        if (state == NativeMapLifecycleState.Resumed || state == NativeMapLifecycleState.Started) {
            stop()
        }
        if (state == NativeMapLifecycleState.Created) target.destroy()
        state = NativeMapLifecycleState.Destroyed
    }

    private fun lowMemory() {
        if (state != NativeMapLifecycleState.New && state != NativeMapLifecycleState.Destroyed) {
            target.lowMemory()
        }
    }
}
