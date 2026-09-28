package app.roadstr.service.navigation

import app.roadstr.service.location.NativeLocationFix
import io.flutter.plugin.common.EventChannel

/** Stable, value-only encoding for the native navigation fix EventChannel. */
object NativeNavigationFixEvent {
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val SPEED_KMH = "speedKmh"
    const val ACCURACY = "accuracy"
    const val HEADING = "heading"
    const val ALTITUDE = "altitude"
    const val TIMESTAMP_MILLIS = "timestampMillis"

    fun encode(fix: NativeLocationFix): Map<String, Any?> = linkedMapOf(
        LATITUDE to fix.latitude,
        LONGITUDE to fix.longitude,
        SPEED_KMH to fix.speedKilometresPerHour,
        ACCURACY to fix.accuracyMeters,
        HEADING to fix.headingDegrees,
        ALTITUDE to fix.altitudeMeters,
        TIMESTAMP_MILLIS to fix.timestampMillis,
    )
}

fun interface NativeNavigationFixSubscription {
    fun cancel()
}

/**
 * Process-local fan-out from the foreground service to attached Flutter engines.
 *
 * Fixes are never persisted or logged. Listener failures are isolated so a
 * stale Activity cannot interrupt the LocationManager source or other engines.
 */
object NativeNavigationFixDispatcher {
    private class Entry(val listener: (NativeLocationFix) -> Unit)

    private val lock = Any()
    private val listeners = linkedSetOf<Entry>()

    internal fun subscribe(
        listener: (NativeLocationFix) -> Unit,
    ): NativeNavigationFixSubscription {
        val entry = Entry(listener)
        synchronized(lock) {
            listeners += entry
        }
        return NativeNavigationFixSubscription {
            synchronized(lock) {
                listeners -= entry
            }
        }
    }

    internal fun publish(fix: NativeLocationFix) {
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { entry ->
            try {
                entry.listener(fix)
            } catch (_: Exception) {
                // A detached Flutter engine must not interrupt GPS delivery.
            }
        }
    }
}

/** EventChannel adapter kept separate from the engine-independent dispatcher. */
class NativeNavigationFixStreamHandler : EventChannel.StreamHandler {
    private var subscription: NativeNavigationFixSubscription? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        subscription?.cancel()
        subscription = NativeNavigationFixDispatcher.subscribe { fix ->
            events.success(NativeNavigationFixEvent.encode(fix))
        }
    }

    override fun onCancel(arguments: Any?) {
        subscription?.cancel()
        subscription = null
    }
}
