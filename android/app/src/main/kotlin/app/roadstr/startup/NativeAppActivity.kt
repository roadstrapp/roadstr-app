package app.roadstr.startup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roadstr.core.ui.theme.RoadstrTheme
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.home.NativeShellMode
import app.roadstr.feature.onboarding.NativeMigrationReadiness
import app.roadstr.feature.onboarding.NativeOnboardingFlow
import app.roadstr.feature.onboarding.NativeOnboardingInput
import app.roadstr.feature.onboarding.NativeOnboardingPresenter
import app.roadstr.roadtest.NativeLiveStoreNames
import app.roadstr.roadtest.NativeNavigationRuntime
import app.roadstr.roadtest.NativeRoadTestActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The launcher of the Kotlin app. It is the road-test shell with its own set of store names, and
 * with a gate in front of the map: until the old app's data has been brought over (or found to be
 * absent) the person sees a progress screen, and if that fails, a plain explanation instead of a
 * silently empty profile.
 */
class NativeAppActivity : NativeRoadTestActivity() {
    override val storeNames: NativeLiveStoreNames = NativeLiveStoreNames(NativeLiveStoreNames.LIVE)

    override val shellMode: NativeShellMode = NativeShellMode.App

    // One runtime for the whole process: a trip goes on with the screen off and across a rotation.
    override fun obtainRuntime(): NativeNavigationRuntime =
        NativeGuidanceRuntimeHolder.obtain(applicationContext, storeNames)

    override fun releaseRuntime(runtime: NativeNavigationRuntime) = NativeGuidanceRuntimeHolder.release(runtime)

    private val startup by lazy(LazyThreadSafetyMode.NONE) {
        NativeStartupMigration.create(applicationContext, storeNames, Executors.newSingleThreadExecutor())
    }

    // Android 13+ shows the trip's notification only if the person allowed notifications. Asked once, when
    // the first trip starts; a refusal leaves the trip running, only without the line in the shade.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var notificationsAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            NativeGuidanceRuntimeHolder.obtain(applicationContext, storeNames).host.session.state
                .map { it.active }.distinctUntilChanged().filter { it }.collect { askForNotifications() }
        }
    }

    private fun askForNotifications() {
        if (notificationsAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationsAsked = true
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Composable
    override fun StartupGate(content: @Composable () -> Unit) {
        val readiness by startup.readiness.collectAsStateWithLifecycle()
        LaunchedEffect(startup) { startup.start() }
        if (readiness == NativeMigrationReadiness.Ready) {
            content()
            return
        }
        RoadstrTheme(if (isSystemInDarkTheme()) RoadstrThemeId.DarkNostr else RoadstrThemeId.LightNostr) {
            StartupMessage(readiness)
        }
    }

    /** The two startup screens the onboarding flow already owns: "loading" and "protected data unavailable". */
    @Composable
    private fun StartupMessage(readiness: NativeMigrationReadiness) {
        val snapshot = NativeOnboardingPresenter.present(
            revision = 0L,
            input = NativeOnboardingInput(
                protectedStorageAvailable = true,
                migrationReadiness = readiness,
                privacyDisclosureV2 = null,
            ),
        )
        NativeOnboardingFlow(
            snapshot = snapshot,
            onPageSelected = { _, _ -> Unit },
            onAmberLogin = {},
            onNsecLogin = {},
            onProfileVisibilityChanged = { _, _ -> Unit },
            onRequestLocation = {},
            onDownloadVoice = {},
            onOpenDisclosure = {},
            onAcceptDisclosure = {},
            onRetryMigration = startup::retry,
            onSkipMigration = startup::skip,
        )
    }
}
