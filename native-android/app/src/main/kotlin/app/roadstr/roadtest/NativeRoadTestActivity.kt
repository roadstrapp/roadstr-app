package app.roadstr.roadtest

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.roadstr.feature.home.NativeRoadstrShell
import app.roadstr.feature.home.NativeShellMode

/** Standalone Compose launcher for the side-by-side Kotlin road-test APK. */
class NativeRoadTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        setContent {
            NativeRoadstrShell(mode = NativeShellMode.RoadTest)
        }
    }
}
