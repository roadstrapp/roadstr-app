package app.roadstr.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.feature.map.NativeMapPoint
import kotlin.math.roundToInt

enum class NativeShellGpsPhase {
    Disabled,
    PermissionRequired,
    PermissionDenied,
    ProviderDisabled,
    Starting,
    WaitingForFix,
    Active,
    Paused,
    Failed,
}

data class NativeShellGpsFix(
    val sequence: Long,
    val point: NativeMapPoint,
    val speedMetersPerSecond: Double,
    val accuracyMeters: Double,
    val headingDegrees: Double?,
    val altitudeMeters: Double,
    val receivedAtElapsedRealtimeMillis: Long,
) {
    init {
        require(sequence >= 0) { "GPS fix sequence must be non-negative" }
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0)
        require(accuracyMeters.isFinite() && accuracyMeters >= 0.0)
        require(headingDegrees == null || headingDegrees.isFinite())
        require(altitudeMeters.isFinite())
        require(receivedAtElapsedRealtimeMillis >= 0)
    }
}

data class NativeShellGpsSnapshot(
    val phase: NativeShellGpsPhase,
    val fix: NativeShellGpsFix? = null,
) {
    init {
        require(phase != NativeShellGpsPhase.Active || fix != null) {
            "An active GPS snapshot requires a fix"
        }
    }

    companion object {
        val Disabled = NativeShellGpsSnapshot(NativeShellGpsPhase.Disabled)
    }
}

/** Road-test-only status surface. It deliberately never renders coordinates. */
@Composable
fun NativeShellGpsPanel(
    snapshot: NativeShellGpsSnapshot,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when (snapshot.phase) {
        NativeShellGpsPhase.Disabled -> stringResource(R.string.native_road_test_status)
        NativeShellGpsPhase.PermissionRequired -> stringResource(R.string.native_gps_permission_required)
        NativeShellGpsPhase.PermissionDenied -> stringResource(R.string.native_gps_permission_denied)
        NativeShellGpsPhase.ProviderDisabled -> stringResource(R.string.native_gps_provider_disabled)
        NativeShellGpsPhase.Starting -> stringResource(R.string.native_gps_starting)
        NativeShellGpsPhase.WaitingForFix -> stringResource(R.string.native_gps_waiting)
        NativeShellGpsPhase.Active -> {
            val fix = requireNotNull(snapshot.fix)
            stringResource(
                R.string.native_gps_active,
                fix.accuracyMeters.roundToInt(),
                (fix.speedMetersPerSecond * 3.6).roundToInt(),
            )
        }
        NativeShellGpsPhase.Paused -> stringResource(R.string.native_gps_paused)
        NativeShellGpsPhase.Failed -> stringResource(R.string.native_gps_failed)
    }
    val action = when (snapshot.phase) {
        NativeShellGpsPhase.PermissionRequired,
        NativeShellGpsPhase.PermissionDenied,
        -> R.string.native_gps_allow
        NativeShellGpsPhase.ProviderDisabled -> R.string.native_gps_open_settings
        NativeShellGpsPhase.Active -> R.string.native_gps_recenter
        NativeShellGpsPhase.Paused,
        NativeShellGpsPhase.Failed,
        -> R.string.native_gps_retry
        NativeShellGpsPhase.Disabled,
        NativeShellGpsPhase.Starting,
        NativeShellGpsPhase.WaitingForFix,
        -> null
    }

    Surface(
        modifier = modifier.wrapContentSize(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.native_road_test_app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = status, style = MaterialTheme.typography.labelLarge)
            if (action != null) {
                TextButton(onClick = onAction) {
                    Text(stringResource(action))
                }
            }
        }
    }
}
