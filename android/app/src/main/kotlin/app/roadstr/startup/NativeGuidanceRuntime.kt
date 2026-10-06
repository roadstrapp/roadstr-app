package app.roadstr.startup

import android.content.Context
import androidx.core.content.ContextCompat
import app.roadstr.feature.navigation.NativeNavigationPlatform
import app.roadstr.roadtest.NativeLiveStoreNames
import app.roadstr.roadtest.NativeNavigationRuntime
import app.roadstr.service.navigation.NativeNavigationForegroundService
import app.roadstr.service.notifications.NativeNavigationNotificationCommand
import app.roadstr.service.notifications.NativeNavigationNotificationDispatcher

/**
 * What keeps a trip going with the screen off: the foreground service that holds the process (and shows
 * "GPS active"), and the notification that says what the next manoeuvre is. Both already existed for the
 * Flutter app; the Kotlin app asks for them when a trip starts and lets go when it ends.
 */
internal class NativeGuidancePlatform(context: Context) : NativeNavigationPlatform {
    private val appContext = context.applicationContext

    override fun guidanceStarted() {
        // Refused (a denied permission, a restricted start) means the trip simply runs while the screen is on.
        runCatching {
            ContextCompat.startForegroundService(appContext, NativeNavigationForegroundService.startIntent(appContext))
        }
    }

    override fun guidanceStopped() {
        runCatching { appContext.stopService(NativeNavigationForegroundService.stopIntent(appContext)) }
    }

    override fun showInstruction(instruction: String, distance: String) {
        NativeNavigationNotificationDispatcher.dispatch(
            NativeNavigationNotificationCommand.Update(instruction.take(MAX_TEXT), distance.take(MAX_TEXT)),
        )
    }

    override fun clearInstruction() {
        NativeNavigationNotificationDispatcher.dispatch(NativeNavigationNotificationCommand.Reset)
    }

    private companion object {
        const val MAX_TEXT = 200
    }
}

/**
 * The one runtime of the process. An Activity takes it at creation and gives it back when it is destroyed;
 * it is released only when no trip is running, so a rotation, a swiped-away screen or a locked phone does
 * not end a trip, and the next Activity (opened from the notification) finds it still driving.
 */
internal object NativeGuidanceRuntimeHolder {
    private val lock = Any()
    private var current: NativeNavigationRuntime? = null

    fun obtain(context: Context, names: NativeLiveStoreNames): NativeNavigationRuntime = synchronized(lock) {
        current ?: NativeNavigationRuntime(context, names, NativeGuidancePlatform(context)).also { current = it }
    }

    fun release(runtime: NativeNavigationRuntime) = synchronized(lock) {
        if (runtime.navigating) return@synchronized
        if (current === runtime) current = null
        runtime.release()
    }
}
