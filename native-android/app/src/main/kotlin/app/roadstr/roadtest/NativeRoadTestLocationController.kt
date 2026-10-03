package app.roadstr.roadtest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.roadstr.feature.home.NativeShellGpsFix
import app.roadstr.feature.home.NativeShellGpsPhase
import app.roadstr.feature.home.NativeShellGpsSnapshot
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.service.location.AndroidLocationManagerSource
import app.roadstr.service.location.NativeLocationFix
import app.roadstr.service.location.NativeLocationService
import app.roadstr.service.location.NativeLocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground-only owner for the road-test GPS feed.
 *
 * Permission prompting stays in the Activity, while this class owns one AOSP
 * listener and publishes value-only snapshots to Compose. It never logs or
 * persists coordinates and stops callbacks whenever the host is not visible.
 */
internal class NativeRoadTestLocationController(
    context: Context,
    private val source: NativeLocationSource = AndroidLocationManagerSource(context),
    private val hasLocationPermission: () -> Boolean = {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    },
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(
        NativeShellGpsSnapshot(NativeShellGpsPhase.PermissionRequired),
    )
    private val service = NativeLocationService(
        source = source,
        onFix = ::publishFix,
        scope = scope,
    )
    private var hostStarted = false
    private var permissionDenied = false
    private var generation = 0L
    private var nextFixSequence = 0L

    val state: StateFlow<NativeShellGpsSnapshot> = mutableState.asStateFlow()

    fun onHostStart() {
        hostStarted = true
        val requestedGeneration = ++generation
        scope.launch { start(requestedGeneration) }
    }

    fun onHostStop() {
        hostStarted = false
        val stoppedGeneration = ++generation
        scope.launch {
            service.stop()
            if (stoppedGeneration != generation) return@launch
            val current = mutableState.value
            if (
                current.phase == NativeShellGpsPhase.Starting ||
                current.phase == NativeShellGpsPhase.WaitingForFix ||
                current.phase == NativeShellGpsPhase.Active
            ) {
                mutableState.value = current.copy(phase = NativeShellGpsPhase.Paused)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        permissionDenied = !granted
        if (!granted) {
            mutableState.value = NativeShellGpsSnapshot(NativeShellGpsPhase.PermissionDenied)
            return
        }
        if (hostStarted) retry()
    }

    fun retry() {
        if (!hostStarted) return
        val requestedGeneration = ++generation
        scope.launch { start(requestedGeneration) }
    }

    fun close() {
        hostStarted = false
        ++generation
        scope.launch {
            service.dispose()
            scope.cancel()
        }
    }

    private suspend fun start(requestedGeneration: Long) {
        if (!hostStarted || requestedGeneration != generation) return
        if (!hasLocationPermission()) {
            mutableState.value = NativeShellGpsSnapshot(
                if (permissionDenied) {
                    NativeShellGpsPhase.PermissionDenied
                } else {
                    NativeShellGpsPhase.PermissionRequired
                },
            )
            return
        }

        mutableState.value = mutableState.value.copy(phase = NativeShellGpsPhase.Starting)
        if (!source.isLocationEnabled()) {
            mutableState.value = NativeShellGpsSnapshot(NativeShellGpsPhase.ProviderDisabled)
            return
        }

        val seed = service.lastKnown()
        if (seed == null) {
            mutableState.value = NativeShellGpsSnapshot(NativeShellGpsPhase.WaitingForFix)
        } else {
            publishFix(seed)
        }
        val started = service.start()
        if (!hostStarted || requestedGeneration != generation) {
            service.stop()
            return
        }
        if (!started) {
            mutableState.value = NativeShellGpsSnapshot(
                if (source.isLocationEnabled()) {
                    NativeShellGpsPhase.Failed
                } else {
                    NativeShellGpsPhase.ProviderDisabled
                },
            )
        }
    }

    private fun publishFix(fix: NativeLocationFix) {
        if (!hostStarted) return
        val accuracy = fix.accuracyMeters.takeIf { it.isFinite() && it >= 0.0 }
            ?: UNKNOWN_ACCURACY_METERS
        mutableState.value = NativeShellGpsSnapshot(
            phase = NativeShellGpsPhase.Active,
            fix = NativeShellGpsFix(
                sequence = nextFixSequence++,
                point = NativeMapPoint(fix.latitude, fix.longitude),
                speedMetersPerSecond = fix.speedKilometresPerHour / 3.6,
                accuracyMeters = accuracy,
                headingDegrees = fix.headingDegrees,
                altitudeMeters = fix.altitudeMeters,
                receivedAtElapsedRealtimeMillis = elapsedRealtimeMillis(),
            ),
        )
    }

    private companion object {
        const val UNKNOWN_ACCURACY_METERS = 9_999.0
    }
}
