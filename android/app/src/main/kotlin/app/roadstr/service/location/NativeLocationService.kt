package app.roadstr.service.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Raw AOSP location values before Roadstr's safety and unit normalization. */
data class NativeRawLocation(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Double,
    val accuracyMeters: Double,
    val bearingDegrees: Double,
    val altitudeMeters: Double,
    val timestampMillis: Long,
    /** Android provider name; only used to choose between cached fixes. */
    val provider: String? = null,
)

/** Stable location value delivered to the native navigation owner. */
data class NativeLocationFix(
    val latitude: Double,
    val longitude: Double,
    val speedKilometresPerHour: Double,
    val accuracyMeters: Double,
    val headingDegrees: Double?,
    val altitudeMeters: Double,
    val timestampMillis: Long,
) {
    val isReliable: Boolean
        get() = accuracyMeters < RELIABLE_ACCURACY_METERS

    private companion object {
        const val RELIABLE_ACCURACY_METERS = 30.0
    }
}

/** Source boundary for LocationManager, injectable without Android runtime state. */
interface NativeLocationSource {
    suspend fun isLocationEnabled(): Boolean

    /** Starts callbacks and returns false when the source cannot be started. */
    suspend fun start(onLocation: (NativeRawLocation) -> Unit): Boolean

    suspend fun stop()

    suspend fun lastKnown(): NativeRawLocation?
}

fun interface NativeLocationClock {
    fun nowMillis(): Long
}

/** Pure normalization shared by stream and last-known fixes. */
object NativeLocationPolicy {
    /**
     * Whether [candidate] should replace [best] as the cached starting fix.
     *
     * Same rule as the vendored geolocator's LocationManagerClient, so the
     * native map seeds from exactly the position the Flutter map does: a fix
     * more than two minutes newer wins, one more than two minutes older loses,
     * and in between the more accurate wins, or the newer one unless it is
     * less accurate (or much less accurate and from another provider).
     */
    fun isBetterCachedFix(candidate: NativeRawLocation, best: NativeRawLocation?): Boolean {
        if (best == null) return true
        val timeDelta = candidate.timestampMillis - best.timestampMillis
        if (timeDelta > TWO_MINUTES_MILLIS) return true
        if (timeDelta < -TWO_MINUTES_MILLIS) return false
        val isNewer = timeDelta > 0
        // The reference truncates the difference to an int before comparing.
        val accuracyDelta = (candidate.accuracyMeters - best.accuracyMeters).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isSignificantlyLessAccurate = accuracyDelta > 200
        val sameProvider = candidate.provider != null && candidate.provider == best.provider
        if (accuracyDelta < 0) return true
        if (isNewer && !isLessAccurate) return true
        return isNewer && !isSignificantlyLessAccurate && sameProvider
    }

    private const val TWO_MINUTES_MILLIS = 2 * 60 * 1000L

    fun normalize(raw: NativeRawLocation, useReportedSpeed: Boolean = true): NativeLocationFix? {
        if (
            !raw.latitude.isFinite() ||
                !raw.longitude.isFinite() ||
                raw.latitude !in -90.0..90.0 ||
                raw.longitude !in -180.0..180.0
        ) {
            return null
        }

        val speedKilometresPerHour = if (!useReportedSpeed) {
            0.0
        } else {
            val metresPerSecond = raw.speedMetersPerSecond
            if (!metresPerSecond.isFinite() || metresPerSecond <= 0.0) {
                0.0
            } else {
                (metresPerSecond * 3.6).takeIf(Double::isFinite) ?: 0.0
            }
        }
        val accuracy = raw.accuracyMeters
            .takeIf { it.isFinite() && it >= 0.0 }
            ?: Double.POSITIVE_INFINITY
        val heading = raw.bearingDegrees
            .takeIf { it.isFinite() && it >= 0.0 }
        val altitude = raw.altitudeMeters.takeIf(Double::isFinite) ?: 0.0

        return NativeLocationFix(
            latitude = raw.latitude,
            longitude = raw.longitude,
            speedKilometresPerHour = speedKilometresPerHour,
            accuracyMeters = accuracy,
            headingDegrees = heading,
            altitudeMeters = altitude,
            timestampMillis = raw.timestampMillis,
        )
    }
}

/**
 * Cancellable, lifecycle-owned GPS coordinator.
 *
 * The Android LocationManager adapter is deliberately behind [NativeLocationSource].
 * This class owns the behavior that must remain identical across devices: one
 * active source, valid-fix normalization, last-known safety and a 45-second
 * dead-stream restart guarded by the source's enabled state.
 */
class NativeLocationService(
    private val source: NativeLocationSource,
    private val onFix: (NativeLocationFix) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val clock: NativeLocationClock = NativeLocationClock {
        System.currentTimeMillis()
    },
    private val watchdogIntervalMillis: Long = WATCHDOG_INTERVAL_MILLIS,
    private val staleAfterMillis: Long = STALE_AFTER_MILLIS,
) {
    private val lifecycleMutex = Mutex()
    private var watchdogJob: Job? = null
    private var lastCallbackAtMillis: Long? = null
    private var running = false
    private var disposed = false

    val isRunning: Boolean
        get() = running

    suspend fun start(): Boolean = lifecycleMutex.withLock {
        if (disposed) return@withLock false
        if (running) return@withLock true
        if (!source.isLocationEnabled()) return@withLock false

        running = true
        lastCallbackAtMillis = clock.nowMillis()
        val started = try {
            source.start(::onRawLocation)
        } catch (cancelled: CancellationException) {
            running = false
            lastCallbackAtMillis = null
            throw cancelled
        } catch (_: SecurityException) {
            false
        }
        if (!started) {
            running = false
            lastCallbackAtMillis = null
            return@withLock false
        }
        watchdogJob?.cancel()
        watchdogJob = scope.launch { watchdogLoop() }
        true
    }

    suspend fun stop() = lifecycleMutex.withLock {
        stopLocked()
    }

    suspend fun lastKnown(): NativeLocationFix? {
        if (disposed) return null
        return try {
            source.lastKnown()?.let { raw ->
                NativeLocationPolicy.normalize(raw, useReportedSpeed = false)
            }
        } catch (_: SecurityException) {
            null
        }
    }

    /** Stops delivery and makes future starts no-ops. The caller owns [scope]. */
    suspend fun dispose() = lifecycleMutex.withLock {
        if (disposed) return@withLock
        disposed = true
        stopLocked()
    }

    /** Deterministic hook for tests and lifecycle owners that have their own ticker. */
    internal suspend fun checkWatchdogNow(): Boolean = lifecycleMutex.withLock {
        restartIfStaleLocked()
    }

    private suspend fun watchdogLoop() {
        while (scope.isActive) {
            delay(watchdogIntervalMillis)
            try {
                checkWatchdogNow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A transient provider failure is retried on the next tick.
            }
        }
    }

    private suspend fun restartIfStaleLocked(): Boolean {
        if (!running || disposed) return false
        val lastCallback = lastCallbackAtMillis ?: return false
        if (clock.nowMillis() - lastCallback < staleAfterMillis) return false
        if (!source.isLocationEnabled()) return false

        stopLocked()
        return startLocked()
    }

    private suspend fun startLocked(): Boolean {
        if (disposed || running) return running
        if (!source.isLocationEnabled()) return false
        running = true
        lastCallbackAtMillis = clock.nowMillis()
        val started = try {
            source.start(::onRawLocation)
        } catch (cancelled: CancellationException) {
            running = false
            lastCallbackAtMillis = null
            throw cancelled
        } catch (_: SecurityException) {
            false
        }
        if (!started) {
            running = false
            lastCallbackAtMillis = null
            return false
        }
        watchdogJob = scope.launch { watchdogLoop() }
        return true
    }

    private suspend fun stopLocked() {
        val wasRunning = running
        running = false
        lastCallbackAtMillis = null
        watchdogJob?.cancel()
        watchdogJob = null
        if (wasRunning) source.stop()
    }

    private fun onRawLocation(raw: NativeRawLocation) {
        if (!running || disposed) return
        // Match the Dart watchdog: a delivered platform callback proves that
        // the stream is alive even when the payload itself is later rejected.
        lastCallbackAtMillis = clock.nowMillis()
        NativeLocationPolicy.normalize(raw)?.let { fix ->
            try {
                onFix(fix)
            } catch (_: Exception) {
                // A stale UI/navigation callback must not kill the source.
            }
        }
    }

    companion object {
        const val WATCHDOG_INTERVAL_MILLIS = 20_000L
        const val STALE_AFTER_MILLIS = 45_000L
    }
}
