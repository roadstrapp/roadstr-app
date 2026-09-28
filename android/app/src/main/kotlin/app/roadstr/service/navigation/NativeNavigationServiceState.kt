package app.roadstr.service.navigation

import java.util.concurrent.atomic.AtomicBoolean

/** Process-local foreground GPS state shared by the service and bridge. */
object NativeNavigationServiceState {
    private val foregroundGpsRunning = AtomicBoolean(false)

    fun isForegroundGpsRunning(): Boolean = foregroundGpsRunning.get()

    internal fun markForegroundGpsStarted() {
        foregroundGpsRunning.set(true)
    }

    internal fun markForegroundGpsStopped() {
        foregroundGpsRunning.set(false)
    }
}
