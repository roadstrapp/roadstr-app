package app.roadstr.roadtest

import android.content.Context
import android.os.SystemClock
import app.roadstr.core.network.RoutingProviderConfigProtocol
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.feature.navigation.NativeNavigationHost
import app.roadstr.feature.navigation.NativeNavigationPlatform
import app.roadstr.feature.navigation.NativeNavigationRerouteRequest
import app.roadstr.feature.navigation.NativeNavigationSettings
import app.roadstr.feature.navigation.NoNativeNavigationPlatform
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Everything a trip needs in order to outlive the screen: the GPS feed, the voice, and the host that
 * moves the trip along. The road-test APK makes one per Activity and releases it with the Activity; the
 * real app keeps one for the whole process, so a locked phone, a rotation or a destroyed Activity does not
 * end a trip.
 *
 * The Activity only reports whether it is visible. While a trip lasts the GPS stays on and the voice keeps
 * speaking; when it ends, and nothing is visible, both are released.
 */
internal class NativeNavigationRuntime(
    context: Context,
    names: NativeLiveStoreNames,
    innerPlatform: NativeNavigationPlatform = NoNativeNavigationPlatform,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visible = false

    val location = NativeRoadTestLocationController(appContext)
    val voice: NativeRoadTestVoiceGateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestVoiceGateway(appContext)
    }

    private val ui = NativeRoadTestUiPreferences(appContext, names)
    private val routingKey = NativeRoadTestProtectedPreferences(
        context = appContext,
        preferencesName = names.prefs("routing"),
        keyAlias = names.alias("routing"),
    )

    // A gateway of its own, only for what a trip asks for while nobody is looking: a new route and the
    // speed limit. Search and the rest stay with the Activity's gateway.
    private val gateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestJourneyGateway(appContext, routingConfiguration = ::routingConfiguration, names = names)
    }

    private val platform = object : NativeNavigationPlatform {
        override fun guidanceStarted() {
            location.holdForNavigation(true)
            innerPlatform.guidanceStarted()
        }

        override fun guidanceStopped() {
            innerPlatform.guidanceStopped()
            location.holdForNavigation(false)
            if (!visible) voice.stop()
        }

        override fun showInstruction(instruction: String, distance: String) =
            innerPlatform.showInstruction(instruction, distance)

        override fun clearInstruction() = innerPlatform.clearInstruction()
    }

    val host = NativeNavigationHost(
        accentArgb = DEFAULT_ACCENT_ARGB,
        gps = location.state,
        voice = voice,
        reroute = ::reroute,
        speedLimit = { point -> gateway.speedLimit(point) },
        settings = ::settings,
        platform = platform,
        scope = scope,
        elapsedRealtimeMillis = SystemClock::elapsedRealtime,
    )

    val navigating: Boolean get() = host.navigating

    fun onActivityStart() {
        visible = true
        location.onHostStart()
    }

    fun onActivityStop() {
        visible = false
        if (!host.navigating) voice.stop()
        location.onHostStop()
    }

    /** Ends everything; the caller decides whether a trip may still be running. */
    fun release() {
        host.close()
        location.close()
        voice.close()
        scope.cancel()
    }

    private suspend fun reroute(
        request: NativeNavigationRerouteRequest,
        settings: NativeNavigationSettings,
    ): RoutingParsedRoute? = gateway.reroute(
        origin = SearchResponsePoint(request.origin.latitude, request.origin.longitude),
        destination = SearchResponsePoint(request.destination.latitude, request.destination.longitude),
        mode = request.mode,
        languageCode = settings.languageCode,
        speedKilometresPerHour = request.speedKilometresPerHour,
        headingDegrees = request.headingDegrees,
        straightLineDistanceMeters = request.straightLineDistanceMeters,
        avoidUnpavedRoads = settings.avoidUnpavedRoads,
    ).firstOrNull()

    private fun settings(): NativeNavigationSettings {
        val stored = ui.load()
        val language = (stored.languageCode ?: Locale.getDefault().language)
            .trim().lowercase(Locale.ROOT).take(MAX_LANGUAGE_CHARS).ifEmpty { "en" }
        return NativeNavigationSettings(
            imperialUnits = stored.imperialUnits,
            avoidUnpavedRoads = stored.avoidUnpavedRoads,
            languageCode = language,
        )
    }

    /** Provider, GraphHopper server and key as the person configured them. */
    private fun routingConfiguration(): RoutingProviderConfiguration {
        val stored = ui.load()
        // Read each time: a reroute is rare, and the key may have been changed in the settings meanwhile.
        return RoutingProviderConfigProtocol.resolve(
            providerKey = stored.routingProvider.storageValue,
            secureApiKey = runCatching { routingKey.read("api_key") }.getOrNull(),
            legacyApiKey = "",
            graphHopperServer = stored.graphHopperServer,
            deferCredentialReadForOsrm = false,
        )
    }

    private companion object {
        const val DEFAULT_ACCENT_ARGB = 0xFF71_58E2L
        const val MAX_LANGUAGE_CHARS = 8
    }
}
