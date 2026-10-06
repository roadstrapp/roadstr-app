package app.roadstr.startup

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
import app.roadstr.roadtest.NativeRoadTestActivity
import java.util.concurrent.Executors

/**
 * The launcher of the Kotlin app. It is the road-test shell with its own set of store names, and
 * with a gate in front of the map: until the old app's data has been brought over (or found to be
 * absent) the person sees a progress screen, and if that fails, a plain explanation instead of a
 * silently empty profile.
 */
class NativeAppActivity : NativeRoadTestActivity() {
    override val storeNames: NativeLiveStoreNames = NativeLiveStoreNames(NativeLiveStoreNames.LIVE)

    override val shellMode: NativeShellMode = NativeShellMode.App

    private val startup by lazy(LazyThreadSafetyMode.NONE) {
        NativeStartupMigration.create(applicationContext, storeNames, Executors.newSingleThreadExecutor())
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
        )
    }
}
