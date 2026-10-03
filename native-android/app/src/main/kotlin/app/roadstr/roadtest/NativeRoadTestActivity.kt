package app.roadstr.roadtest

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roadstr.feature.home.NativeRoadstrShell
import app.roadstr.feature.home.NativeShellGpsPhase
import app.roadstr.feature.home.NativeShellMode

/** Standalone Compose launcher for the side-by-side Kotlin road-test APK. */
class NativeRoadTestActivity : ComponentActivity() {
    private lateinit var locationController: NativeRoadTestLocationController
    private val journeyGateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestJourneyGateway()
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        locationController.onPermissionResult(grants.values.any { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationController = NativeRoadTestLocationController(applicationContext)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        setContent {
            val gpsSnapshot by locationController.state.collectAsStateWithLifecycle()
            NativeRoadstrShell(
                mode = NativeShellMode.RoadTest,
                gpsSnapshot = gpsSnapshot,
                journeyGateway = journeyGateway,
                onGpsAction = ::handleGpsAction,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        locationController.onHostStart()
    }

    override fun onStop() {
        locationController.onHostStop()
        super.onStop()
    }

    override fun onDestroy() {
        locationController.close()
        super.onDestroy()
    }

    private fun handleGpsAction() {
        when (locationController.state.value.phase) {
            NativeShellGpsPhase.PermissionRequired,
            NativeShellGpsPhase.PermissionDenied,
            -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            NativeShellGpsPhase.ProviderDisabled -> {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            NativeShellGpsPhase.Paused,
            NativeShellGpsPhase.Failed,
            -> locationController.retry()
            NativeShellGpsPhase.Disabled,
            NativeShellGpsPhase.Starting,
            NativeShellGpsPhase.WaitingForFix,
            NativeShellGpsPhase.Active,
            -> Unit
        }
    }
}
