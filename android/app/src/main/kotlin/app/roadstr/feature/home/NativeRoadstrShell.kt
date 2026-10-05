package app.roadstr.feature.home

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.roadstr.R
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.feature.saved.NativeSavedPlacesStatus
import app.roadstr.feature.settings.NativeSettingsBooleanKey
import app.roadstr.core.protocol.nostr.NostrNip19
import app.roadstr.service.hazards.NativeOsmHazard
import app.roadstr.service.hazards.NativeOsmHazardKind
import app.roadstr.service.hazards.NativeOsmHazardService
import app.roadstr.core.ui.theme.RoadstrTheme
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.core.ui.theme.RoadstrThemeTokens
import app.roadstr.feature.activity.NativeActivityInboxPanel
import app.roadstr.feature.activity.NativeActivityInboxSession
import app.roadstr.feature.map.NativeMapCameraSession
import app.roadstr.feature.map.NativeMapCursorSession
import app.roadstr.feature.map.NativeMapCursorStyle
import app.roadstr.feature.map.NativeMapEngine
import app.roadstr.feature.map.NativeMapInteraction
import app.roadstr.feature.map.NativeMapLibreHost
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeCompassSensor
import app.roadstr.feature.map.NativeMapFreeDriveControls
import app.roadstr.feature.map.NativeMapNavigationControls
import app.roadstr.feature.map.NativeMapPointOverlayKind
import app.roadstr.feature.map.NativeMapPointOverlayMarker
import app.roadstr.feature.map.NativeMapPointOverlaySession
import app.roadstr.feature.map.NativeMapSearchButton
import app.roadstr.feature.map.NativeMapAltitudeBadge
import app.roadstr.feature.map.NativeMapStyle
import app.roadstr.feature.map.NativeTileUrlPolicy
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.map.NativeTransitOverlaySession
import app.roadstr.feature.navigation.NativeActiveNavigationSession
import app.roadstr.feature.navigation.NativeNavigationArrivalBanner
import app.roadstr.feature.navigation.NativeNavigationHud
import app.roadstr.feature.navigation.NativeNavigationHudSession
import app.roadstr.feature.navigation.NativeRerouteBackoff
import app.roadstr.feature.onboarding.NativeOnboardingFlow
import app.roadstr.feature.onboarding.NativeOnboardingInput
import app.roadstr.feature.onboarding.NativeOnboardingLocationStatus
import app.roadstr.feature.onboarding.NativeOnboardingSession
import app.roadstr.feature.onboarding.NativeOnboardingVoiceStatus
import app.roadstr.feature.onboarding.NativeMigrationReadiness
import app.roadstr.feature.place.NativePlaceDetailsPanel
import app.roadstr.feature.place.NativePlaceSession
import app.roadstr.feature.profile.NativeProfilePanel
import app.roadstr.feature.profile.NativeProfileSession
import app.roadstr.feature.profile.NativeIdentityGateway
import app.roadstr.feature.profile.NativeIdentitySnapshot
import app.roadstr.feature.profile.NativeProfileMetadata
import app.roadstr.feature.report.NativeRoadEventPanels
import app.roadstr.feature.report.NativeRoadEventSession
import app.roadstr.feature.route.NativeRoutePlanningPanel
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningStatus
import app.roadstr.feature.saved.NativeSavedPlacesPanel
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.feature.discovery.DiscoveryPresentation
import app.roadstr.feature.discovery.NativeDiscoverySnapshot
import app.roadstr.feature.history.NativeRouteHistoryEntry
import app.roadstr.feature.history.NativeRouteHistoryPanel
import app.roadstr.feature.history.NativeRouteHistoryProtocol
import app.roadstr.feature.saved.NativeMapContextMenu
import app.roadstr.feature.saved.NativeParkingPanel
import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.feature.saved.NativeSavedPlacesSession
import app.roadstr.feature.search.NativeSearchFavorite
import app.roadstr.feature.search.NativeSearchPresenter
import app.roadstr.feature.search.NativeSearchOverlay
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.search.NativeSearchUiStatus
import app.roadstr.feature.settings.NativeSettingsPanel
import app.roadstr.feature.web.NativeWebBrowserScreen
import app.roadstr.feature.web.WebBrowserHost
import app.roadstr.feature.web.WebBrowserState
import app.roadstr.feature.settings.NativeWebSearchEditor
import app.roadstr.feature.settings.NativeWebSearchPanel
import app.roadstr.feature.settings.NativeWebSearchStatus
import app.roadstr.feature.settings.runWebConnectionTest
import app.roadstr.feature.settings.NativeSettingsSession
import app.roadstr.feature.settings.NativeSettingsUiAction
import app.roadstr.feature.settings.NativeSettingsStatus
import app.roadstr.feature.settings.NativeSettingsCursorColor
import app.roadstr.feature.settings.NativeSettingsCursorStyle
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsVoiceGender
import app.roadstr.feature.settings.NativeSettingsVoiceModelStatus
import app.roadstr.feature.transit.NativeTransitItinerariesPanel
import app.roadstr.feature.transit.NativeTransitJourneySession
import app.roadstr.feature.transit.NativeTransitTransportMode
import app.roadstr.feature.wikipedia.NativeWikipediaReader
import app.roadstr.feature.wikipedia.NativeWikipediaSession
import app.roadstr.feature.voice.NativeVoiceCatalog
import app.roadstr.service.nostr.NativeRoadEvent
import app.roadstr.feature.voice.NativeVoiceGateway
import app.roadstr.feature.voice.NativeVoiceGender
import app.roadstr.feature.voice.NativeVoiceRuntimeStatus
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class NativeShellMode {
    Canary,
    RoadTest,
}

private const val ARRIVAL_BANNER_MILLIS = 6_000L
private const val HAZARD_POLL_MILLIS = 4_000L

private fun hazardMarker(hazard: NativeOsmHazard): NativeMapPointOverlayMarker =
    NativeMapPointOverlayMarker(
        id = when (hazard.kind) {
            NativeOsmHazardKind.TrafficLight -> "osm-light-${hazard.id}"
            NativeOsmHazardKind.Crosswalk -> "osm-crossing-${hazard.id}"
            NativeOsmHazardKind.SpeedBump -> "osm-bump-${hazard.id}"
        },
        point = NativeMapPoint(hazard.latitude, hazard.longitude),
        kind = when (hazard.kind) {
            NativeOsmHazardKind.TrafficLight -> NativeMapPointOverlayKind.TrafficLight
            NativeOsmHazardKind.Crosswalk -> NativeMapPointOverlayKind.Crosswalk
            NativeOsmHazardKind.SpeedBump -> NativeMapPointOverlayKind.SpeedBump
        },
    )

private fun effectiveThemeId(
    selected: RoadstrThemeId,
    autoDarkEnabled: Boolean,
    systemDark: Boolean,
): RoadstrThemeId {
    val dark = if (autoDarkEnabled) systemDark else selected.dark
    val bitcoin = selected == RoadstrThemeId.LightBitcoin || selected == RoadstrThemeId.DarkBitcoin
    return when {
        bitcoin && dark -> RoadstrThemeId.DarkBitcoin
        bitcoin -> RoadstrThemeId.LightBitcoin
        dark -> RoadstrThemeId.DarkNostr
        else -> RoadstrThemeId.LightNostr
    }
}

private fun cursorStyle(value: NativeSettingsCursorStyle): NativeMapCursorStyle = when (value) {
    NativeSettingsCursorStyle.Arrow -> NativeMapCursorStyle.Arrow
    NativeSettingsCursorStyle.Formula1 -> NativeMapCursorStyle.Formula1
    NativeSettingsCursorStyle.Suv -> NativeMapCursorStyle.Suv
    NativeSettingsCursorStyle.Racing -> NativeMapCursorStyle.Racing
    NativeSettingsCursorStyle.Electric -> NativeMapCursorStyle.Electric
    NativeSettingsCursorStyle.City -> NativeMapCursorStyle.City
    NativeSettingsCursorStyle.Classic500 -> NativeMapCursorStyle.Classic500
}

private fun cursorColor(value: NativeSettingsCursorColor): Long = when (value) {
    NativeSettingsCursorColor.Violet -> 0xFF8B3DFF
    NativeSettingsCursorColor.Indigo -> 0xFF5856D6
    NativeSettingsCursorColor.Blue -> 0xFF0A84FF
    NativeSettingsCursorColor.Green -> 0xFF34C759
    NativeSettingsCursorColor.Yellow -> 0xFFFFCC00
    NativeSettingsCursorColor.Orange -> 0xFFFF9500
    NativeSettingsCursorColor.Red -> 0xFFFF3B30
}

private fun submitNavigationFix(
    activeNavigationSession: NativeActiveNavigationSession,
    fix: NativeShellGpsFix,
    headingDegrees: Double,
) {
    activeNavigationSession.submitFix(
        sequence = fix.sequence,
        point = fix.point,
        speedMetersPerSecond = fix.speedMetersPerSecond,
        altitudeMeters = fix.altitudeMeters,
        accuracyMeters = fix.accuracyMeters,
        headingDegrees = headingDegrees,
    )
}

/**
 * Dormant native UI boundary used to prove Compose and MapLibre packaging.
 *
 * It owns no storage, sensor or migration adapter. The production package does
 * not launch it; the separate road-test APK can inject a value-only GPS feed
 * and host it directly without Flutter. Live journey providers can only enter
 * through the explicit value-only gateway; the production canary injects none.
 * Product screens replace this boundary incrementally after their parity gates
 * are green.
 */
/** Panel heights closer than this share one camera fit. */
private const val ROUTE_FIT_INSET_BUCKET_PX = 32

@Composable
fun NativeRoadstrShell(
    mode: NativeShellMode = NativeShellMode.Canary,
    gpsSnapshot: NativeShellGpsSnapshot = NativeShellGpsSnapshot.Disabled,
    journeyGateway: NativeShellJourneyGateway? = null,
    voiceGateway: NativeVoiceGateway? = null,
    onGpsAction: () -> Unit = {},
    identityGateway: NativeIdentityGateway? = null,
    onAmberLogin: (Long) -> Unit = {},
    onOpenExternal: (String) -> Unit = {},
    onboardingCompleted: Boolean = false,
    onOnboardingCompleted: () -> Unit = {},
    initialParking: NativeParkingPosition? = null,
    onParkingChanged: (NativeParkingPosition?) -> Unit = {},
    initialRouteHistory: List<NativeRouteHistoryEntry> = emptyList(),
    onRouteHistoryChanged: (List<NativeRouteHistoryEntry>) -> Unit = {},
    initialWebSearch: WebDiscoverySettings = WebDiscoverySettings(),
    onWebSearchChanged: (WebDiscoverySettings) -> Unit = {},
    /** Where web pages open: the user's browser, or the optional in-app browser. */
    webBrowser: WebBrowserHost? = null,
    initialFavorites: List<NativeSavedPlace> = emptyList(),
    onFavoritesChanged: (List<NativeSavedPlace>) -> Unit = {},
    initialSettings: NativeSettingsInput = NativeSettingsInput(),
    onSettingsChanged: (NativeSettingsInput) -> Unit = {},
    onNwcChanged: (String) -> Boolean = { false },
    /** Stores the routing API key protected; an empty value removes it. False if it could not be stored. */
    onRoutingKeyChanged: (String) -> Boolean = { false },
    hazardService: NativeOsmHazardService? = null,
    nostr: NativeShellNostr? = null,
) {
    val settingsSession = remember(initialSettings) { NativeSettingsSession(initialSettings) }
    val settingsState by settingsSession.state.collectAsState()
    LaunchedEffect(settingsState.status, settingsState.values) {
        if (settingsState.status == NativeSettingsStatus.Ready) {
            onSettingsChanged(settingsState.values)
        }
    }
    val themeId = effectiveThemeId(
        selected = settingsState.values.themeId,
        autoDarkEnabled = settingsState.values.autoDarkEnabled,
        systemDark = isSystemInDarkTheme(),
    )
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, settingsState.values.languageCode) {
        val language = settingsState.values.languageCode
        if (language == null) {
            baseContext
        } else {
            val localizedConfiguration = Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            baseContext.createConfigurationContext(localizedConfiguration)
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
    RoadstrTheme(themeId = themeId) {
        val view = LocalView.current
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenHeightPixels = with(density) {
            configuration.screenHeightDp.dp.toPx().toDouble()
        }
        val routePanelBottomInsetPixels = with(density) { 390.dp.roundToPx() }
            .coerceAtMost((screenHeightPixels * 0.55).toInt())
        // The route sheet changes height (stops, alternatives, avoidance), so
        // the framing follows the measured panel instead of a fixed guess:
        // the route is centred in the map that is actually visible.
        var routePanelHeightPixels by remember { mutableIntStateOf(0) }
        val routeFitBottomInsetPixels = if (routePanelHeightPixels > 0) {
            (routePanelHeightPixels + with(density) { 20.dp.roundToPx() })
                .coerceAtMost((screenHeightPixels * 0.8).toInt())
        } else {
            routePanelBottomInsetPixels
        }
        val routeFitTopInsetPixels = WindowInsets.statusBars.getTop(density) +
            with(density) { 56.dp.roundToPx() }
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !themeId.dark
            controller.isAppearanceLightNavigationBars = !themeId.dark
        }
        val shellDescription = stringResource(
            when (mode) {
                NativeShellMode.Canary -> R.string.native_shell_description
                NativeShellMode.RoadTest -> R.string.native_road_test_description
            },
        )
        val palette = RoadstrThemeTokens.palette(themeId)
        val routeSession = remember { NativeRouteOverlaySession(themeId.accentArgb) }
        val routePlanningSession = remember(routeSession) {
            NativeRoutePlanningSession(routeSession)
        }
        val routePlanningState by routePlanningSession.state.collectAsState()
        LaunchedEffect(routeSession, themeId.accentArgb) {
            routeSession.updateAccent(themeId.accentArgb)
        }
        val routeState by routeSession.state.collectAsState()
        val transitSession = remember {
            NativeTransitOverlaySession(
                initialAccentArgb = palette.accentArgb,
                initialTextSecondaryArgb = palette.textSecondaryArgb,
            )
        }
        LaunchedEffect(transitSession, palette.accentArgb, palette.textSecondaryArgb) {
            transitSession.updateTheme(palette.accentArgb, palette.textSecondaryArgb)
        }
        val transitState by transitSession.state.collectAsState()
        val transitJourneySession = remember(transitSession) {
            NativeTransitJourneySession(
                overlaySession = transitSession,
                initialAccentArgb = palette.accentArgb,
            )
        }
        LaunchedEffect(transitJourneySession, palette.accentArgb, settingsState.values.imperialUnits) {
            transitJourneySession.updatePresentation(
                accentArgb = palette.accentArgb,
                imperial = settingsState.values.imperialUnits,
            )
        }
        val transitUiState by transitJourneySession.state.collectAsState()
        var transitMode by remember { mutableStateOf(NativeTransitTransportMode.Transit) }
        var headingMode by remember { mutableStateOf(true) }
        val cameraSession = remember { NativeMapCameraSession() }
        val cameraState by cameraSession.state.collectAsState()
        val cursorSession = remember { NativeMapCursorSession() }
        val cursorState by cursorSession.state.collectAsState()
        val pointOverlaySession = remember { NativeMapPointOverlaySession() }
        val pointOverlayState by pointOverlaySession.state.collectAsState()
        var parkingPosition by remember { mutableStateOf(initialParking) }
        var favorites by remember(initialFavorites) { mutableStateOf(initialFavorites) }
        var trafficLights by remember { mutableStateOf<List<NativeOsmHazard>>(emptyList()) }
        var crossingHazards by remember { mutableStateOf<List<NativeOsmHazard>>(emptyList()) }
        val latestFix by rememberUpdatedState(gpsSnapshot.fix)
        // The service decides whether a refetch is due (moved far enough, or
        // data too old), so this only has to ask now and then. A plain effect
        // keyed on the fix would be cancelled by the next fix half a second
        // later, and no request would ever complete.
        LaunchedEffect(hazardService, settingsState.values.showTrafficLights) {
            val service = hazardService
            if (service == null || !settingsState.values.showTrafficLights) {
                trafficLights = emptyList()
                return@LaunchedEffect
            }
            while (true) {
                latestFix?.let { fix ->
                    trafficLights = service.trafficLights(
                        GeoPoint(fix.point.latitude, fix.point.longitude),
                    )
                }
                delay(HAZARD_POLL_MILLIS)
            }
        }
        LaunchedEffect(hazardService, settingsState.values.showCrosswalks) {
            val service = hazardService
            if (service == null || !settingsState.values.showCrosswalks) {
                crossingHazards = emptyList()
                return@LaunchedEffect
            }
            while (true) {
                latestFix?.let { fix ->
                    crossingHazards = service.crossingsAndBumps(
                        GeoPoint(fix.point.latitude, fix.point.longitude),
                    )
                }
                delay(HAZARD_POLL_MILLIS)
            }
        }
        val searchSession = remember { NativeSearchSession(initialImperial = settingsState.values.imperialUnits) }
        val searchState by searchSession.state.collectAsState()
        val journeyScope = rememberCoroutineScope()
        var speedLimitJob by remember { mutableStateOf<Job?>(null) }
        val identityState = identityGateway?.state?.collectAsState()?.value ?: NativeIdentitySnapshot()
        var profileMetadata by remember(identityState.pubkeyHex) {
            mutableStateOf<NativeProfileMetadata?>(null)
        }
        LaunchedEffect(identityState.pubkeyHex, identityGateway) {
            profileMetadata = identityState.pubkeyHex?.let { pubkey ->
                identityGateway?.fetchProfileMetadata(pubkey)
            }
        }
        var nsecDialogVisible by remember { mutableStateOf(false) }
        var nsecInput by remember { mutableStateOf("") }
        var nsecError by remember { mutableStateOf(false) }
        var routingKeyPrompt by remember { mutableStateOf<NativeShellPrompt?>(null) }
        var nwcDialogVisible by remember { mutableStateOf(false) }
        var nwcInput by remember { mutableStateOf("") }
        var nwcError by remember { mutableStateOf(false) }
        val sensitiveDialogVisible = nsecDialogVisible || nwcDialogVisible
        DisposableEffect(sensitiveDialogVisible, context) {
            val activity = context as? Activity
            val secureFlag = WindowManager.LayoutParams.FLAG_SECURE
            val alreadySecure = activity?.window?.attributes?.flags?.and(secureFlag) != 0
            if (sensitiveDialogVisible && alreadySecure == false) {
                activity.window.addFlags(secureFlag)
            }
            onDispose {
                if (sensitiveDialogVisible && alreadySecure == false) {
                    activity?.window?.clearFlags(secureFlag)
                }
            }
        }
        val journeyLanguage = settingsState.values.languageCode
            ?: configuration.locales[0].language
        // The places the latest natural-language search found, pinned on the map, and the
        // end of the current trip for "near my destination".
        var discovery by remember { mutableStateOf(NativeDiscoverySnapshot.Empty) }
        var tripDestination by remember { mutableStateOf<SearchResponsePoint?>(null) }
        // What the user chose about web results; the host keeps it (the address, encrypted).
        var webSettings by remember { mutableStateOf(initialWebSearch) }
        var webPanelVisible by remember { mutableStateOf(false) }
        var webStatus by remember { mutableStateOf<NativeWebSearchStatus>(NativeWebSearchStatus.Idle) }
        var webEditCount by remember { mutableIntStateOf(0) }
        val browserState = webBrowser?.state?.collectAsState()?.value ?: WebBrowserState()
        val updateWebSettings: (WebDiscoverySettings) -> Unit = { next ->
            webSettings = next
            // A result about the old address must not stay on screen next to the new one.
            webEditCount += 1
            webStatus = NativeWebSearchStatus.Idle
            onWebSearchChanged(next)
        }
        val journeyCoordinator = remember(
            journeyGateway,
            journeyScope,
            searchSession,
            routePlanningSession,
            journeyLanguage,
        ) {
            journeyGateway?.let { gateway ->
                NativeShellJourneyCoordinator(
                    gateway = gateway,
                    scope = journeyScope,
                    searchSession = searchSession,
                    routeSession = routePlanningSession,
                    languageCode = journeyLanguage,
                    // Saved places are listed in the search menu, as on the Flutter map.
                    favorites = {
                        favorites.map { place ->
                            NativeSearchFavorite(
                                label = place.label,
                                address = place.address,
                                position = SearchResponsePoint(place.point.latitude, place.point.longitude),
                            )
                        }
                    },
                    destination = { tripDestination },
                    onDiscovery = { revision, places ->
                        discovery = NativeDiscoverySnapshot(revision, places)
                    },
                    webSettings = { webSettings },
                    routeAhead = {
                        routeState.snapshot.activeRuns.flatMap { run ->
                            run.points.map { GeoPoint(it.latitude, it.longitude) }
                        }
                    },
                )
            }
        }
        DisposableEffect(journeyCoordinator) {
            onDispose { journeyCoordinator?.close() }
        }
        val myLocationLabel = stringResource(R.string.native_route_my_location)
        val gpsSearchPoint = gpsSnapshot.fix?.point?.let { point ->
            SearchResponsePoint(point.latitude, point.longitude)
        }
        // A map link the user confirmed inside a page becomes the destination, as a tap on the map would.
        DisposableEffect(webBrowser, journeyCoordinator, gpsSearchPoint, myLocationLabel) {
            webBrowser?.onDestination = { latitude, longitude ->
                webBrowser.close()
                journeyCoordinator?.selectDestination(
                    label = String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude),
                    point = SearchResponsePoint(latitude, longitude),
                    gpsPoint = gpsSearchPoint,
                    myLocationLabel = myLocationLabel,
                )
            }
            onDispose { webBrowser?.onDestination = null }
        }
        // What a page says about its own place is judged against the current search.
        DisposableEffect(webBrowser, journeyCoordinator) {
            webBrowser?.pageResolver = { page, structured -> journeyCoordinator?.pagePlace(page, structured) }
            onDispose { webBrowser?.pageResolver = null }
        }
        val voiceRuntimeState = voiceGateway?.state?.collectAsState()?.value
        val voiceLanguage = journeyLanguage
        LaunchedEffect(
            voiceGateway,
            voiceLanguage,
            settingsState.values.voiceGender,
            settingsState.values.voiceSpeedStage,
            settingsState.values.voiceVolume,
        ) {
            voiceGateway?.configure(
                languageCode = voiceLanguage,
                gender = when (settingsState.values.voiceGender) {
                    NativeSettingsVoiceGender.Female -> NativeVoiceGender.Female
                    NativeSettingsVoiceGender.Male -> NativeVoiceGender.Male
                },
                speed = NativeVoiceCatalog.speedForStage(settingsState.values.voiceSpeedStage),
                volume = settingsState.values.voiceVolume,
            )
        }
        LaunchedEffect(routePlanningState.status, settingsState.values.voiceEnabled, voiceGateway) {
            if (
                routePlanningState.status == NativeRoutePlanningStatus.Alternatives &&
                settingsState.values.voiceEnabled
            ) {
                voiceGateway?.prewarmStart()
            }
        }
        LaunchedEffect(voiceRuntimeState) {
            if (settingsState.status != NativeSettingsStatus.Ready) return@LaunchedEffect
            val voiceState = voiceRuntimeState ?: return@LaunchedEffect
            settingsSession.refresh(
                settingsState.revision,
                settingsState.values.copy(
                    voiceModelStatus = when (voiceState.status) {
                        NativeVoiceRuntimeStatus.MissingAssets,
                        NativeVoiceRuntimeStatus.Failed,
                        -> NativeSettingsVoiceModelStatus.NotDownloaded
                        NativeVoiceRuntimeStatus.Downloading -> NativeSettingsVoiceModelStatus.Downloading
                        NativeVoiceRuntimeStatus.Ready,
                        NativeVoiceRuntimeStatus.Speaking,
                        -> NativeSettingsVoiceModelStatus.Ready
                    },
                    voiceDownloadProgress = voiceState.downloadFraction,
                    voiceGenderChoiceAvailable = NativeVoiceCatalog.selection(
                        voiceLanguage,
                        NativeVoiceGender.Male,
                    )?.genderChoiceAvailable ?: false,
                ),
            )
        }
        val showSettings = {
            val voiceState = voiceRuntimeState
            val revision = settingsState.revision + 1L
            if (settingsSession.reopen(revision)) {
                settingsSession.refresh(
                    revision = revision,
                    input = settingsSession.state.value.values.copy(
                        favoritesCount = favorites.size,
                        syncIdentityAvailable = identityGateway?.let { identityState.loggedIn } == true,
                        voiceModelStatus = when (voiceState?.status) {
                            NativeVoiceRuntimeStatus.Downloading -> NativeSettingsVoiceModelStatus.Downloading
                            NativeVoiceRuntimeStatus.Ready,
                            NativeVoiceRuntimeStatus.Speaking,
                            -> NativeSettingsVoiceModelStatus.Ready
                            NativeVoiceRuntimeStatus.MissingAssets,
                            NativeVoiceRuntimeStatus.Failed,
                            null,
                            -> NativeSettingsVoiceModelStatus.NotDownloaded
                        },
                        voiceDownloadProgress = voiceState?.downloadFraction ?: 0.0,
                    ),
                )
            }
            Unit
        }
        val placeSession = remember { NativePlaceSession() }
        val placeState by placeSession.state.collectAsState()
        val showSearchPlace: (String, String?, SearchResponsePoint) -> Unit = { label, address, point ->
            val revision = placeSession.state.value.revision.coerceAtLeast(0L) + 1L
            if (placeSession.begin(revision, point, address = address ?: label)) {
                journeyCoordinator?.closeSearchForPlace()
                // First reveal the spatial relationship with the current fix,
                // then land smoothly on the selected POI above its place card.
                journeyScope.launch {
                    val destination = NativeMapPoint(point.latitude, point.longitude)
                    val origin = gpsSnapshot.fix?.point
                    if (origin != null) {
                        cameraSession.fitRoute(
                            points = listOf(origin, destination),
                            nowMillis = SystemClock.elapsedRealtime(),
                            bottomInsetPixels = routePanelBottomInsetPixels,
                        )
                        delay(460)
                    }
                    cameraSession.focus(destination, SystemClock.elapsedRealtime())
                }
                // Search already supplied the label/address. Enrich Wikipedia
                // by text so this flow does not disclose another exact point.
                journeyScope.launch {
                    val article = runCatching {
                        journeyGateway?.wikipediaArticle(label, voiceLanguage)
                    }.getOrNull()
                    placeSession.submit(
                        revision = revision,
                        article = article,
                        address = address ?: label,
                        wikiQuery = label,
                    )
                }
            }
        }
        // A point on the map, from a tap or from "What's here?": the address and
        // opening hours from the reverse lookup, then a short Wikipedia
        // description found by the place's name, so the exact spot is never sent
        // to Wikipedia.
        val inspectPlace: (NativeMapPoint) -> Unit = { mapPoint ->
            val revision = placeSession.state.value.revision.coerceAtLeast(0L) + 1L
            val point = SearchResponsePoint(mapPoint.latitude, mapPoint.longitude)
            if (placeSession.begin(revision, point)) {
                journeyScope.launch {
                    val detail = runCatching {
                        journeyGateway?.reverseGeocode(point = point, languageCode = voiceLanguage)
                    }.getOrNull()
                    // Only a real place gets a description: for a plain address the
                    // query is the neighbourhood, and its article would mislead.
                    val article = detail?.wikiQuery?.takeIf { detail.poiName != null }?.let { query ->
                        runCatching { journeyGateway?.wikipediaArticle(query, voiceLanguage) }.getOrNull()
                    }
                    placeSession.submit(
                        revision = revision,
                        article = article,
                        address = detail?.display,
                        wikiQuery = detail?.wikiQuery,
                        openingHours = detail?.openingHours,
                    )
                }
            }
        }
        // Results of a natural-language search open a sheet with the facts OpenStreetMap
        // already gave (hours, phone, website, cuisine), not a second lookup.
        var fittedDiscoveryRevision by remember { mutableStateOf(-1L) }
        val showDiscoveryPlace: (RoadstrPlace) -> Unit = { place ->
            fittedDiscoveryRevision = discovery.revision
            val revision = placeSession.state.value.revision.coerceAtLeast(0L) + 1L
            val point = SearchResponsePoint(place.position.latitude, place.position.longitude)
            val title = DiscoveryPresentation.title(place, voiceLanguage)
            if (placeSession.begin(revision, point, address = place.address ?: title)) {
                journeyCoordinator?.closeSearchForPlace()
                journeyScope.launch {
                    cameraSession.focus(NativeMapPoint(point.latitude, point.longitude), SystemClock.elapsedRealtime())
                }
                placeSession.submit(
                    revision = revision,
                    details = DiscoveryPresentation.details(place, voiceLanguage),
                    address = place.address ?: title,
                    openingHours = place.openingHours,
                )
            }
        }
        var parkingPanelVisible by remember { mutableStateOf(false) }
        // The routes the driver started, kept by the host encrypted on the device.
        var routeHistory by remember { mutableStateOf(initialRouteHistory) }
        var historyVisible by remember { mutableStateOf(false) }
        val updateRouteHistory: (List<NativeRouteHistoryEntry>) -> Unit = { next ->
            routeHistory = next
            onRouteHistoryChanged(next)
        }
        var contextMenuPoint by remember { mutableStateOf<NativeMapPoint?>(null) }
        val parkingLabel = stringResource(R.string.native_saved_parking_title)
        val navigateToParking: (NativeParkingPosition) -> Unit = { parking ->
            journeyCoordinator?.selectDestination(
                label = parkingLabel,
                point = SearchResponsePoint(parking.point.latitude, parking.point.longitude),
                gpsPoint = gpsSearchPoint,
                myLocationLabel = myLocationLabel,
            )
        }
        val saveParking: (NativeMapPoint) -> Unit = { point ->
            val next = NativeParkingPosition(point = point, savedAtEpochMillis = System.currentTimeMillis())
            parkingPosition = next
            onParkingChanged(next)
            Toast.makeText(context, R.string.native_parking_saved, Toast.LENGTH_SHORT).show()
        }
        val profileSession = remember { NativeProfileSession() }
        val profileState by profileSession.state.collectAsState()
        LaunchedEffect(profileState.revision, profileState.status, identityState, settingsState.values.profilePublic) {
            if (profileState.status == app.roadstr.feature.profile.NativeProfileStatus.Loading ||
                profileState.status == app.roadstr.feature.profile.NativeProfileStatus.LoggedOut
            ) {
                val pubkey = identityState.pubkeyHex
                val flavor = identityState.flavor
                if (identityState.loggedIn && pubkey != null && flavor != null) {
                    val metadata = profileMetadata ?: identityGateway?.fetchProfileMetadata(pubkey)
                    profileSession.showProfile(
                        revision = profileState.revision,
                        input = app.roadstr.feature.profile.NativeProfileInput(
                            pubkeyHex = pubkey,
                            ownProfile = true,
                            profilePublic = settingsState.values.profilePublic,
                            flavor = flavor,
                            displayName = metadata?.displayName,
                            name = metadata?.name,
                            pictureUrl = metadata?.pictureUrl,
                        ),
                        nowSeconds = System.currentTimeMillis() / 1_000L,
                    )
                } else {
                    profileSession.showLoggedOut(profileState.revision)
                }
            }
        }
        val savedPlacesSession = remember { NativeSavedPlacesSession() }
        val savedPlacesState by savedPlacesSession.state.collectAsState()
        var favoriteEditorVisible by remember { mutableStateOf(false) }
        var favoriteEditorIndex by remember { mutableStateOf<Int?>(null) }
        var favoriteEditorLabel by remember { mutableStateOf("") }
        var favoriteEditorAddress by remember { mutableStateOf("") }
        var favoriteEditorOriginalAddress by remember { mutableStateOf("") }
        var favoriteEditorError by remember { mutableStateOf(false) }
        var favoriteEditorBusy by remember { mutableStateOf(false) }
        val activityInboxSession = remember { NativeActivityInboxSession() }
        val activityInboxState by activityInboxSession.state.collectAsState()
        val roadEventSession = remember { NativeRoadEventSession() }
        val roadEventState by roadEventSession.state.collectAsState()
        val nostrHost = rememberNativeShellNostrHost(
            nostr = nostr,
            settingsSession = settingsSession,
            roadEventSession = roadEventSession,
            identityPubkey = identityState.pubkeyHex,
            favorites = favorites,
            onMergeFavorites = { incoming ->
                favorites = NativeSavedPlacesProtocol.mergeByLabel(favorites, incoming)
                onFavoritesChanged(favorites)
                if (savedPlacesSession.state.value.status != NativeSavedPlacesStatus.Hidden) {
                    savedPlacesSession.show(
                        savedPlacesSession.state.value.revision + 1L,
                        favorites,
                        parkingPosition,
                    )
                }
            },
            currentPoint = { latestFix?.point },
        )
        // The account's own reports and zap balance come from the relays after the
        // profile is up, so the screen opens at once and fills in.
        var myReports by remember { mutableStateOf<List<NativeRoadEvent>>(emptyList()) }
        var reportsLoadedFor by remember { mutableLongStateOf(-1L) }
        LaunchedEffect(profileState.revision, profileState.status, identityState.pubkeyHex) {
            val ownerPubkey = identityState.pubkeyHex ?: return@LaunchedEffect
            val bridge = nostrHost?.nostr ?: return@LaunchedEffect
            val reportsService = bridge.userReports ?: return@LaunchedEffect
            val snapshot = profileState
            if (snapshot.status != app.roadstr.feature.profile.NativeProfileStatus.Ready ||
                !snapshot.ownProfile ||
                reportsLoadedFor == snapshot.revision
            ) {
                return@LaunchedEffect
            }
            reportsLoadedFor = snapshot.revision
            val flavor = snapshot.flavor ?: return@LaunchedEffect
            fun input(
                reports: List<NativeRoadEvent>,
                loading: Boolean,
                balanceMsat: Long?,
            ) = app.roadstr.feature.profile.NativeProfileInput(
                pubkeyHex = ownerPubkey,
                ownProfile = true,
                profilePublic = snapshot.profilePublic,
                flavor = flavor,
                displayName = snapshot.displayName,
                pictureUrl = snapshot.pictureUrl,
                reports = reports.map { event ->
                    app.roadstr.feature.profile.NativeProfileReportInput(
                        id = event.id,
                        category = event.category,
                        createdAtSeconds = event.createdAt,
                        comment = event.comment,
                        confirmations = event.confirmations,
                        denials = event.denials,
                    )
                },
                reportsLoading = loading,
                balanceMsat = balanceMsat,
            )
            profileSession.showProfile(snapshot.revision, input(emptyList(), true, null), System.currentTimeMillis() / 1_000L)
            val (events, balance) = coroutineScope {
                val reports = async { reportsService.userEvents(ownerPubkey) }
                val zapped = async { bridge.zaps?.balanceMsat(ownerPubkey) }
                reports.await() to zapped.await()
            }
            myReports = events
            profileSession.showProfile(
                snapshot.revision,
                input(events, false, balance),
                System.currentTimeMillis() / 1_000L,
            )
        }
        // Every panel that prints a distance or a speed follows the units switch:
        // each session was created metric and only ever told otherwise here.
        LaunchedEffect(settingsState.values.imperialUnits) {
            val imperial = settingsState.values.imperialUnits
            routePlanningSession.updateUnits(imperial)
            searchSession.updateUnits(imperial)
            roadEventSession.updateUnits(imperial)
        }
        // One composition of everything drawn as a point: parking, OSM hazards
        // and community road reports replace each other in a single overlay.
        val roadMarkers = nostrHost?.markers.orEmpty()
        LaunchedEffect(parkingPosition, trafficLights, crossingHazards, roadMarkers, discovery) {
            pointOverlaySession.replace(
                revision = pointOverlayState.revision + 1L,
                markers = buildList {
                    parkingPosition?.let { add(NativeSavedPlacesProtocol.parkingMarker(it)) }
                    trafficLights.forEach { add(hazardMarker(it)) }
                    crossingHazards.forEach { add(hazardMarker(it)) }
                    addAll(roadMarkers)
                    addAll(DiscoveryPresentation.pins(discovery.places))
                },
            )
        }
        // Once the search list is closed, show every place it found.
        val searchClosed = searchState.status == NativeSearchUiStatus.Hidden
        LaunchedEffect(discovery.revision, searchClosed) {
            val places = discovery.places
            if (!searchClosed || places.isEmpty() || fittedDiscoveryRevision == discovery.revision) {
                return@LaunchedEffect
            }
            fittedDiscoveryRevision = discovery.revision
            val points = places.map { NativeMapPoint(it.place.position.latitude, it.place.position.longitude) } +
                listOfNotNull(gpsSnapshot.fix?.point)
            cameraSession.fitRoute(points, SystemClock.elapsedRealtime(), routePanelBottomInsetPixels)
        }
        val navigationHudSession = remember { NativeNavigationHudSession() }
        val navigationHudState by navigationHudSession.state.collectAsState()
        val activeNavigationSession = remember(navigationHudSession, routeSession) {
            NativeActiveNavigationSession(navigationHudSession, routeSession)
        }
        val activeNavigationState by activeNavigationSession.state.collectAsState()
        // Screen-wake and brightness-floor policy, same as the Flutter map screens.
        val wantsScreenOn = settingsState.values.keepScreenOnAlways ||
            (activeNavigationState.active && settingsState.values.keepScreenOn)
        val brightnessFloor = settingsState.values.minimumBrightness
        DisposableEffect(wantsScreenOn, brightnessFloor) {
            val window = (context as? Activity)?.window
            if (window != null) {
                if (wantsScreenOn) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                window.attributes = window.attributes.also {
                    it.screenBrightness = if (brightnessFloor > 0.0) {
                        brightnessFloor.toFloat()
                    } else {
                        android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
            onDispose {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window?.attributes = window?.attributes?.also {
                    it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
        // "Center on my position at launch" off: leave the map where it is until
        // the driver asks for the GPS button.
        LaunchedEffect(Unit) {
            if (!initialSettings.autoCenterOnLaunch) cameraSession.onUserGesture()
        }
        var exitNavigationDialogVisible by remember { mutableStateOf(false) }
        val stopNavigation: () -> Unit = {
            if (activeNavigationSession.stop(activeNavigationState.revision)) {
                journeyCoordinator?.cancelReroute()
                voiceGateway?.stop()
                cameraSession.configure(
                    headingUp = true,
                    navigating = false,
                    zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                    pitchDegrees = NativeMapCameraSession.FREE_DRIVE_PITCH,
                    screenHeightPixels = screenHeightPixels,
                )
                cameraSession.recenter(SystemClock.elapsedRealtime())
            }
            exitNavigationDialogVisible = false
        }
        LaunchedEffect(
            activeNavigationSession,
            activeNavigationState.revision,
            settingsState.values.showAltitude,
            settingsState.values.imperialUnits,
            settingsState.values.speedometerStyle,
        ) {
            activeNavigationSession.updatePresentationSettings(
                revision = activeNavigationState.revision,
                showAltitude = settingsState.values.showAltitude,
                imperialUnits = settingsState.values.imperialUnits,
                speedometerStyle = settingsState.values.speedometerStyle,
            )
        }
        LaunchedEffect(
            cursorSession,
            settingsState.values.cursorStyle,
            settingsState.values.cursorColor,
        ) {
            cursorSession.updateStyle(cursorStyle(settingsState.values.cursorStyle))
            cursorSession.updateColor(cursorColor(settingsState.values.cursorColor))
        }
        val walkingCursor = activeNavigationState.mode == app.roadstr.feature.route.NativeRouteTransportMode.Walking ||
            (!activeNavigationState.active &&
                routePlanningState.status != NativeRoutePlanningStatus.Hidden &&
                routePlanningState.mode == app.roadstr.feature.route.NativeRouteTransportMode.Walking)
        LaunchedEffect(
            cursorSession,
            walkingCursor,
            gpsSnapshot.fix?.speedMetersPerSecond,
        ) {
            cursorSession.updateMotion(
                walkingMode = walkingCursor,
                speedMetersPerSecond = gpsSnapshot.fix?.speedMetersPerSecond ?: 0.0,
            )
        }
        val navigationNowLabel = stringResource(R.string.native_nav_now)
        val headingTracker = remember { NativeShellHeadingTracker() }
        // The vehicle sits just above the bottom panel, as on the Flutter map:
        // that panel's height, a 24dp gap and a tenth of the screen, measured on
        // the real window instead of guessed. (A camera padding of P puts the
        // target at (P + H) / 2, hence the 2x.)
        var navPanelHeightPixels by remember { mutableIntStateOf(0) }
        val windowHeightPixels = LocalWindowInfo.current.containerSize.height
        val navigationDensity = LocalDensity.current
        LaunchedEffect(navPanelHeightPixels, windowHeightPixels) {
            if (navPanelHeightPixels <= 0 || windowHeightPixels <= 0) return@LaunchedEffect
            val height = windowHeightPixels.toDouble()
            val gap = with(navigationDensity) { 24.dp.toPx() } + height * 0.10
            val target = height - navPanelHeightPixels - gap
            cameraSession.setNavigationTopPadding((2.0 * target - height).coerceIn(0.0, height * 0.7))
        }
        // At a standstill outside navigation the heading-up map turns with the
        // phone's compass, as on the Flutter screen; in motion it follows the
        // GPS course and the route instead.
        val compassWanted = !activeNavigationState.active && headingMode && cameraState.followEnabled
        val compassLifecycle = LocalLifecycleOwner.current
        DisposableEffect(compassWanted, compassLifecycle) {
            if (!compassWanted) {
                cameraSession.setCompassHeading(null)
                return@DisposableEffect onDispose {}
            }
            val sensor = NativeCompassSensor(context) { azimuth ->
                if (headingTracker.isMoving) {
                    cameraSession.setCompassHeading(null)
                } else {
                    headingTracker.adopt(azimuth)
                    cameraSession.setCompassHeading(azimuth)
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> sensor.start()
                    Lifecycle.Event.ON_STOP -> {
                        sensor.stop()
                        cameraSession.setCompassHeading(null)
                    }
                    else -> Unit
                }
            }
            compassLifecycle.lifecycle.addObserver(observer)
            if (compassLifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) sensor.start()
            onDispose {
                compassLifecycle.lifecycle.removeObserver(observer)
                sensor.stop()
                cameraSession.setCompassHeading(null)
            }
        }
        val rerouteBackoff = remember { NativeRerouteBackoff() }
        // One owner per fix, in the Flutter screen's order: route progress and
        // off-route checks see the previous heading, then the filter resolves
        // this fix's heading (nudged toward the route's own direction while
        // navigating) for the cursor and the camera.
        LaunchedEffect(gpsSnapshot.fix?.sequence) {
            val fix = gpsSnapshot.fix ?: return@LaunchedEffect
            val navigating = activeNavigationSession.state.value.active
            if (navigating) {
                submitNavigationFix(activeNavigationSession, fix, headingTracker.headingDegrees)
                if (speedLimitJob?.isActive != true && journeyGateway != null) {
                    val point = SearchResponsePoint(fix.point.latitude, fix.point.longitude)
                    speedLimitJob = journeyScope.launch {
                        val limit = runCatching { journeyGateway.speedLimit(point) }.getOrNull()
                        activeNavigationSession.updateExternalSpeedLimit(
                            activeNavigationSession.state.value.revision,
                            limit,
                        )
                    }
                }
            }
            val heading = headingTracker.update(
                point = fix.point,
                speedMetersPerSecond = fix.speedMetersPerSecond,
                accuracyMeters = fix.accuracyMeters,
                providerHeadingDegrees = fix.headingDegrees,
                navigating = navigating,
                routeLocalBearingAt = if (navigating) {
                    activeNavigationSession::routeLocalBearingAt
                } else {
                    null
                },
            )
            cameraSession.submitFix(
                sequence = fix.sequence,
                point = fix.point,
                headingDegrees = heading,
                // Dead reckoning runs only while really moving: a stopped phone
                // still reports a fraction of a metre per second, which would
                // otherwise creep the map and the cursor along the old heading.
                speedMetersPerSecond = if (headingTracker.isMoving) fix.speedMetersPerSecond else 0.0,
                receivedAtMillis = fix.receivedAtElapsedRealtimeMillis,
            )
            cursorSession.submitPosition(fix.sequence, fix.point)
        }
        LaunchedEffect(activeNavigationState.active) {
            if (!activeNavigationState.active) {
                speedLimitJob?.cancel()
                speedLimitJob = null
                tripDestination = null
            }
        }
        LaunchedEffect(activeNavigationState.active) {
            if (!activeNavigationState.active) return@LaunchedEffect
            headingTracker.resetReversal()
            rerouteBackoff.reset()
            val fix = gpsSnapshot.fix ?: return@LaunchedEffect
            submitNavigationFix(activeNavigationSession, fix, headingTracker.headingDegrees)
        }
        LaunchedEffect(activeNavigationState.rerouteRequest?.sequence) {
            val request = activeNavigationState.rerouteRequest ?: return@LaunchedEffect
            val accepted = journeyCoordinator?.reroute(
                request = request,
                avoidUnpavedRoads = settingsState.values.avoidUnpavedRoads,
                onSuccess = { sequence, route ->
                    rerouteBackoff.reset()
                    activeNavigationSession.completeReroute(sequence, route)
                    routePlanningSession.synchronizeNavigationRevision(
                        activeNavigationSession.state.value.revision,
                    )
                },
                onFailure = { sequence ->
                    // The request stays pending through the wait, so the
                    // session raises no new one until it is released.
                    val wait = rerouteBackoff.nextDelayMillis()
                    journeyScope.launch {
                        delay(wait)
                        activeNavigationSession.failReroute(sequence)
                    }
                },
            ) ?: false
            if (!accepted) activeNavigationSession.failReroute(request.sequence)
        }
        LaunchedEffect(activeNavigationState.voiceCue?.sequence) {
            val cue = activeNavigationState.voiceCue ?: return@LaunchedEffect
            voiceGateway?.announceManeuver(
                instruction = cue.instruction,
                distanceMeters = cue.distanceMeters,
                nowMillis = SystemClock.elapsedRealtime(),
                imperial = settingsState.values.imperialUnits,
            )
        }
        LaunchedEffect(activeNavigationState.arrived, activeNavigationState.revision) {
            if (!activeNavigationState.arrived) return@LaunchedEffect
            journeyCoordinator?.cancelReroute()
            voiceGateway?.announceArrival()
            cameraSession.configure(
                headingUp = true,
                navigating = false,
                zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                pitchDegrees = NativeMapCameraSession.FREE_DRIVE_PITCH,
                screenHeightPixels = screenHeightPixels,
            )
            cameraSession.recenter(SystemClock.elapsedRealtime())
            delay(ARRIVAL_BANNER_MILLIS)
            activeNavigationSession.dismissArrival(activeNavigationState.revision)
        }
        val onboardingSession = remember { NativeOnboardingSession() }
        val onboardingState by onboardingSession.state.collectAsState()
        LaunchedEffect(onboardingSession) {
            onboardingSession.begin(
                revision = 0L,
                input = NativeOnboardingInput(
                    protectedStorageAvailable = true,
                    migrationReadiness = NativeMigrationReadiness.Ready,
                    privacyDisclosureV2 = onboardingCompleted,
                    locationStatus = if (gpsSnapshot.phase == NativeShellGpsPhase.Active) {
                        NativeOnboardingLocationStatus.Granted
                    } else {
                        NativeOnboardingLocationStatus.Required
                    },
                    voiceStatus = when (voiceRuntimeState?.status) {
                        NativeVoiceRuntimeStatus.Downloading -> NativeOnboardingVoiceStatus.Downloading
                        NativeVoiceRuntimeStatus.Ready,
                        NativeVoiceRuntimeStatus.Speaking,
                        -> NativeOnboardingVoiceStatus.Ready
                        NativeVoiceRuntimeStatus.MissingAssets,
                        NativeVoiceRuntimeStatus.Failed,
                        null,
                        -> NativeOnboardingVoiceStatus.NotDownloaded
                    },
                    voiceProgress = voiceRuntimeState?.downloadFraction ?: 0.0,
                ),
            )
        }
        LaunchedEffect(gpsSnapshot.phase, voiceRuntimeState?.status, voiceRuntimeState?.downloadFraction) {
            val revision = onboardingSession.state.value.revision
            if (revision < 0L) return@LaunchedEffect
            if (onboardingSession.state.value.status != app.roadstr.feature.onboarding.NativeStartupGateStatus.Onboarding) {
                return@LaunchedEffect
            }
            onboardingSession.updateLocation(
                revision,
                if (gpsSnapshot.phase == NativeShellGpsPhase.Active) {
                    NativeOnboardingLocationStatus.Granted
                } else {
                    NativeOnboardingLocationStatus.Required
                },
            )
            val voiceStatus = when (voiceRuntimeState?.status) {
                NativeVoiceRuntimeStatus.Downloading -> NativeOnboardingVoiceStatus.Downloading
                NativeVoiceRuntimeStatus.Ready,
                NativeVoiceRuntimeStatus.Speaking,
                -> NativeOnboardingVoiceStatus.Ready
                else -> NativeOnboardingVoiceStatus.NotDownloaded
            }
            onboardingSession.updateVoice(
                revision,
                voiceStatus,
                voiceRuntimeState?.downloadFraction ?: 0.0,
            )
        }
        LaunchedEffect(identityState) {
            if (onboardingState.status == app.roadstr.feature.onboarding.NativeStartupGateStatus.Onboarding &&
                onboardingState.revision >= 0L
            ) {
                onboardingSession.updateIdentity(
                    revision = onboardingState.revision,
                    status = if (identityState.loggedIn) {
                        app.roadstr.feature.onboarding.NativeOnboardingIdentityStatus.Connected
                    } else {
                        app.roadstr.feature.onboarding.NativeOnboardingIdentityStatus.Disconnected
                    },
                    label = if (identityState.loggedIn) "Nostr" else null,
                )
            }
        }
        val homeSession = remember { NativeHomeSession() }
        val homeState by homeSession.state.collectAsState()
        val wikipediaSession = remember { NativeWikipediaSession() }
        val wikipediaState by wikipediaSession.state.collectAsState()
        LaunchedEffect(
            searchState.status,
            routePlanningState.status,
            placeState.status,
            activeNavigationState.active,
            nostrHost?.activityUnread,
        ) {
            homeSession.replace(
                revision = homeSession.state.value.revision + 1L,
                input = NativeHomeInput(
                    navigating = activeNavigationState.active,
                    searchVisible = searchState.status != NativeSearchUiStatus.Hidden,
                    placeVisible = placeState.status != app.roadstr.feature.place.NativePlaceUiStatus.Hidden,
                    plannerVisible = routePlanningState.status == NativeRoutePlanningStatus.Planner,
                    previewVisible = routePlanningState.status == NativeRoutePlanningStatus.Preview,
                    alternativesVisible = routePlanningState.status == NativeRoutePlanningStatus.Alternatives,
                    calculating = routePlanningState.status == NativeRoutePlanningStatus.Loading,
                    hasRoute = routePlanningState.status == NativeRoutePlanningStatus.Alternatives ||
                        routePlanningState.status == NativeRoutePlanningStatus.Preview,
                    unreadActivityCount = nostrHost?.activityUnread ?: 0,
                ),
            )
        }
        // A new map engine starts from its own default view: bring it back to
        // the driver instead of leaving it on the engine's initial position.
        val selectedMapEngine = settingsState.values.mapEngine
        var shownMapEngine by remember { mutableStateOf(selectedMapEngine) }
        LaunchedEffect(selectedMapEngine) {
            if (shownMapEngine == selectedMapEngine) return@LaunchedEffect
            shownMapEngine = selectedMapEngine
            gpsSnapshot.fix?.point?.let { point ->
                cameraSession.focus(point, SystemClock.elapsedRealtime())
            }
        }
        LaunchedEffect(cameraSession, cameraState.frameActive) {
            while (cameraSession.state.value.frameActive) {
                delay(NativeMapCameraSession.FOLLOW_FRAME_MILLIS)
                cameraSession.advanceFrame(SystemClock.elapsedRealtime())
            }
        }
        LaunchedEffect(
            routePlanningState.status,
            routeState.revision,
            routeState.selectedAlternativeIndex,
            routeState.snapshot.activeRuns.size,
            routeState.snapshot.completedPoints.size,
            routeFitBottomInsetPixels / ROUTE_FIT_INSET_BUCKET_PX,
        ) {
            if (activeNavigationState.active) return@LaunchedEffect
            if (
                routePlanningState.status != NativeRoutePlanningStatus.Alternatives &&
                routePlanningState.status != NativeRoutePlanningStatus.Preview
            ) {
                return@LaunchedEffect
            }
            val routePoints = buildList {
                // Frame the selected journey, not the union of every muted
                // alternative. A wide detour otherwise pushes the highlighted
                // route to one side of the screen (most visible on long trips).
                routeState.snapshot.activeRuns.forEach { addAll(it.points) }
                addAll(routeState.snapshot.completedPoints)
            }.distinct()
            if (routePoints.isNotEmpty()) {
                // Let the sheet finish laying out so one smooth fit replaces a
                // series of small corrections.
                delay(120)
                cameraSession.fitRoute(
                    points = routePoints,
                    nowMillis = SystemClock.elapsedRealtime(),
                    bottomInsetPixels = routeFitBottomInsetPixels,
                    topInsetPixels = routeFitTopInsetPixels,
                )
            }
        }
        BackHandler(
            enabled = (
                browserState.open ||
                    webPanelVisible ||
                    parkingPanelVisible ||
                    historyVisible ||
                    contextMenuPoint != null ||
                    settingsState.status != NativeSettingsStatus.Hidden ||
                    profileState.status != app.roadstr.feature.profile.NativeProfileStatus.Hidden ||
                    placeState.status != app.roadstr.feature.place.NativePlaceUiStatus.Hidden ||
                    savedPlacesState.status != app.roadstr.feature.saved.NativeSavedPlacesStatus.Hidden ||
                    activityInboxState.status != app.roadstr.feature.activity.NativeActivityInboxStatus.Hidden ||
                    roadEventState.surface != app.roadstr.feature.report.NativeRoadEventSurface.Hidden ||
                    wikipediaState.status != app.roadstr.feature.wikipedia.NativeWikipediaStatus.Hidden ||
                    transitUiState.status != app.roadstr.feature.transit.NativeTransitUiStatus.Hidden ||
                    searchState.status != NativeSearchUiStatus.Hidden ||
                    routePlanningState.status != NativeRoutePlanningStatus.Hidden ||
                    activeNavigationState.active
                ),
        ) {
            when {
                browserState.open -> webBrowser?.close()
                webPanelVisible -> webPanelVisible = false
                contextMenuPoint != null -> contextMenuPoint = null
                parkingPanelVisible -> parkingPanelVisible = false
                historyVisible -> historyVisible = false
                wikipediaState.status != app.roadstr.feature.wikipedia.NativeWikipediaStatus.Hidden -> {
                    wikipediaSession.hide(wikipediaState.revision)
                }
                roadEventState.surface != app.roadstr.feature.report.NativeRoadEventSurface.Hidden -> {
                    roadEventSession.hide(roadEventState.revision)
                }
                activityInboxState.status != app.roadstr.feature.activity.NativeActivityInboxStatus.Hidden -> {
                    activityInboxSession.hide(activityInboxState.revision)
                }
                savedPlacesState.status != app.roadstr.feature.saved.NativeSavedPlacesStatus.Hidden -> {
                    savedPlacesSession.hide(savedPlacesState.revision)
                }
                profileState.status != app.roadstr.feature.profile.NativeProfileStatus.Hidden -> {
                    profileSession.hide(profileState.revision)
                }
                placeState.status != app.roadstr.feature.place.NativePlaceUiStatus.Hidden -> {
                    placeSession.hide(placeState.revision)
                }
                transitUiState.status != app.roadstr.feature.transit.NativeTransitUiStatus.Hidden -> {
                    transitJourneySession.clear(transitUiState.revision)
                }
                settingsState.status != NativeSettingsStatus.Hidden -> {
                    settingsSession.hide(settingsState.revision)
                }
                searchState.status != NativeSearchUiStatus.Hidden -> {
                    journeyCoordinator?.dismissSearch()
                }
                routePlanningState.status != NativeRoutePlanningStatus.Hidden -> {
                    journeyCoordinator?.cancelRoute()
                }
                activeNavigationState.active -> {
                    exitNavigationDialogVisible = true
                }
            }
        }
        val startSelectedRoute: () -> Unit = {
            val revision = routePlanningSession.state.value.revision
            val mode = routePlanningSession.state.value.mode
            val destinationLabel = routePlanningSession.state.value.destinationLabel
            routePlanningSession.selectedNavigationRoute(revision)?.let { route ->
                val destination = journeyCoordinator?.navigationDestination(revision) ?: return@let
                if (
                    activeNavigationSession.start(
                        revision = revision,
                        route = route,
                        destination = NativeMapPoint(destination.latitude, destination.longitude),
                        mode = mode,
                        nowLabel = navigationNowLabel,
                    )
                ) {
                    if (routePlanningSession.beginNavigation(revision)) {
                        discovery = NativeDiscoverySnapshot.Empty
                        tripDestination = destination
                        val target = NativeMapPoint(destination.latitude, destination.longitude)
                        updateRouteHistory(
                            NativeRouteHistoryProtocol.record(
                                routeHistory,
                                NativeRouteHistoryEntry(
                                    label = destinationLabel
                                        ?.takeIf(String::isNotBlank)
                                        ?: String.format(Locale.ROOT, "%.5f, %.5f", target.latitude, target.longitude),
                                    point = target,
                                    startedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            ),
                        )
                        voiceGateway?.setMuted(!settingsState.values.voiceEnabled)
                        if (settingsState.values.voiceEnabled) voiceGateway?.announceStart()
                        cameraSession.configure(
                            headingUp = true,
                            navigating = true,
                            zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                            pitchDegrees = NativeMapCameraSession.NAVIGATION_PITCH,
                            screenHeightPixels = screenHeightPixels,
                        )
                        cameraSession.recenter(SystemClock.elapsedRealtime())
                    } else {
                        activeNavigationSession.stop(revision)
                    }
                }
            }
        }
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = shellDescription },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                NativeMapLibreHost(
                    // Theme darkness belongs to app chrome. Keep the map in its
                    // normal daytime style, matching the stable Flutter shell.
                    dark = settingsState.values.darkMapEnabled,
                    mapEngine = when (settingsState.values.mapEngine) {
                        app.roadstr.feature.settings.NativeSettingsMapEngine.MapLibre -> NativeMapEngine.MapLibre
                        app.roadstr.feature.settings.NativeSettingsMapEngine.Osm -> NativeMapEngine.LegacyRaster
                    },
                    tileUrl = remember(settingsState.values.mapTileUrl) {
                        // A custom source the policy refuses falls back to the default
                        // instead of leaving the map blank.
                        runCatching { NativeTileUrlPolicy.requireAccepted(settingsState.values.mapTileUrl) }
                            .getOrDefault(NativeMapStyle.DEFAULT_TILE_URL)
                    },
                    routeOverlay = routeState.snapshot,
                    transitOverlay = transitState,
                    cameraCommand = cameraState.command,
                    // The vehicle is drawn where the camera frame says it is.
                    cursorSnapshot = remember(cursorState, cameraState.displaySequence) {
                        val drawn = cameraState.displayPoint
                        if (drawn == null || cursorState.point == null) cursorState else cursorState.copy(point = drawn)
                    },
                    pointOverlay = pointOverlayState,
                    onCameraGesture = cameraSession::onUserGesture,
                    onMapInteraction = { interaction ->
                        when (interaction) {
                            is NativeMapInteraction.MapTap -> {
                                val selectedAlternative = routePlanningSession.selectAlternativeAt(
                                    revision = routePlanningState.revision,
                                    point = interaction.point,
                                ) || routeSession.selectAlternativeAt(
                                    revision = routeSession.state.value.revision,
                                    tap = interaction.point,
                                )
                                if (!selectedAlternative && !activeNavigationState.active) {
                                    inspectPlace(interaction.point)
                                }
                            }
                            is NativeMapInteraction.MapLongPress -> {
                                // A long press asks what to do there rather than
                                // guessing: park here, or find out what is there.
                                parkingPanelVisible = false
                                contextMenuPoint = interaction.point
                            }
                            is NativeMapInteraction.PlaceMarkerTap -> {
                                DiscoveryPresentation.placeForPin(interaction.markerId, discovery.places)
                                    ?.let(showDiscoveryPlace)
                            }
                            is NativeMapInteraction.RoadEventTap -> {
                                nostrHost?.eventForMarker(interaction.markerId)?.let { event ->
                                    nostrHost.reports.showEvent(event)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (
                    homeState.visible &&
                    searchState.status == NativeSearchUiStatus.Hidden &&
                    placeState.status == app.roadstr.feature.place.NativePlaceUiStatus.Hidden &&
                    routePlanningState.status == NativeRoutePlanningStatus.Hidden &&
                    !activeNavigationState.active
                ) {
                    NativeHomeChrome(
                        snapshot = homeState,
                        profilePictureUrl = if (identityState.loggedIn) profileMetadata?.pictureUrl else null,
                        onToggleExpanded = homeSession::toggleExpanded,
                        onAction = { revision, action ->
                            when (homeSession.action(revision, action)) {
                            NativeHomeAction.Navigate -> {
                                journeyCoordinator?.openSearch(gpsSearchPoint != null)
                            }
                            NativeHomeAction.Locate -> {
                                if (gpsSnapshot.phase == NativeShellGpsPhase.Active) {
                                    cameraSession.recenter(SystemClock.elapsedRealtime())
                                } else {
                                    onGpsAction()
                                }
                            }
                            NativeHomeAction.Menu -> showSettings()
                            NativeHomeAction.Parking -> {
                                // Only the parking spot: saved places have their own panel.
                                contextMenuPoint = null
                                parkingPanelVisible = true
                            }
                            NativeHomeAction.Activity -> {
                                contextMenuPoint = null
                                parkingPanelVisible = false
                                historyVisible = true
                            }
                            NativeHomeAction.Notifications -> {
                                val nextRevision = activityInboxState.revision.coerceAtLeast(0L) + 1L
                                val pubkey = identityState.pubkeyHex
                                if (pubkey == null) {
                                    activityInboxSession.showLoggedOut(nextRevision)
                                } else {
                                    activityInboxSession.show(nextRevision, pubkey, nostrHost?.inboxFor(pubkey))
                                }
                            }
                            // The road-event panel remains packaging-only until
                            // disclosure persistence, signer, relay and offline
                            // queue ownership enter together. Opening it with a
                            // live fix now would present a publish flow that can
                            // only discard the user's report.
                            NativeHomeAction.Events -> {
                                gpsSnapshot.fix?.point?.let { point -> nostrHost?.openReport(point) }
                            }
                            null -> Unit
                            NativeHomeAction.Profile -> {
                                val revision = profileState.revision.coerceAtLeast(0L) + 1L
                                profileSession.begin(revision, ownProfile = true)
                            }
                            }
                        },
                        onFavorite = { revision, id ->
                            homeSession.selectFavorite(revision, id)
                        },
                    )
                }
                if (!activeNavigationState.active &&
                    searchState.status == NativeSearchUiStatus.Hidden &&
                    placeState.status == app.roadstr.feature.place.NativePlaceUiStatus.Hidden &&
                    routePlanningState.status == NativeRoutePlanningStatus.Hidden
                ) {
                    NativeMapSearchButton(
                        onClick = { journeyCoordinator?.openSearch(gpsSearchPoint != null) },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    )
                    if (settingsState.values.showAltitude) {
                        gpsSnapshot.fix?.let { fix ->
                            NativeMapAltitudeBadge(
                                altitudeMeters = fix.altitudeMeters,
                                imperial = settingsState.values.imperialUnits,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = 76.dp, end = 12.dp),
                            )
                        }
                    }
                }
                if (
                    activeNavigationState.active &&
                    searchState.status == NativeSearchUiStatus.Hidden &&
                    placeState.status == app.roadstr.feature.place.NativePlaceUiStatus.Hidden &&
                    routePlanningState.status == NativeRoutePlanningStatus.Hidden &&
                    wikipediaState.status == app.roadstr.feature.wikipedia.NativeWikipediaStatus.Hidden
                ) {
                    NativeMapNavigationControls(
                        headingActive = headingMode,
                        bearingDegrees = (cameraState.command?.bearingDegrees ?: 0.0).toFloat(),
                        onToggleHeading = {
                            headingMode = !headingMode
                            cameraSession.configure(
                                headingUp = headingMode,
                                navigating = true,
                                zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                                pitchDegrees = NativeMapCameraSession.NAVIGATION_PITCH,
                                screenHeightPixels = screenHeightPixels,
                            )
                        },
                        onRecenter = { cameraSession.recenter(SystemClock.elapsedRealtime()) },
                        onReport = {
                            gpsSnapshot.fix?.point?.let { point -> nostrHost?.openReport(point) }
                        },
                        onAddWaypoint = { journeyCoordinator?.openSearch(gpsSearchPoint != null) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 12.dp, bottom = 154.dp),
                    )
                }
                if (
                    !activeNavigationState.active &&
                    !homeState.expanded &&
                    searchState.status == NativeSearchUiStatus.Hidden &&
                    placeState.status == app.roadstr.feature.place.NativePlaceUiStatus.Hidden &&
                    routePlanningState.status == NativeRoutePlanningStatus.Hidden &&
                    wikipediaState.status == app.roadstr.feature.wikipedia.NativeWikipediaStatus.Hidden &&
                    settingsState.status == NativeSettingsStatus.Hidden &&
                    profileState.status == app.roadstr.feature.profile.NativeProfileStatus.Hidden &&
                    savedPlacesState.status == NativeSavedPlacesStatus.Hidden &&
                    activityInboxState.status == app.roadstr.feature.activity.NativeActivityInboxStatus.Hidden &&
                    roadEventState.surface == app.roadstr.feature.report.NativeRoadEventSurface.Hidden
                ) {
                    NativeMapFreeDriveControls(
                        headingActive = headingMode,
                        bearingDegrees = (cameraState.command?.bearingDegrees ?: 0.0).toFloat(),
                        showRecenter = !cameraState.followEnabled,
                        onToggleHeading = {
                            headingMode = !headingMode
                            cameraSession.configure(
                                headingUp = headingMode,
                                navigating = false,
                                zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                                pitchDegrees = NativeMapCameraSession.FREE_DRIVE_PITCH,
                                screenHeightPixels = screenHeightPixels,
                            )
                        },
                        onRecenter = { cameraSession.recenter(SystemClock.elapsedRealtime()) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 12.dp, bottom = 190.dp),
                    )
                }
                val chooseFavorite: (String, String, SearchResponsePoint) -> Unit = { label, address, position ->
                    if (activeNavigationState.active) {
                        journeyCoordinator?.selectDestination(label, position, gpsSearchPoint, myLocationLabel)
                    } else {
                        showSearchPlace(label, address, position)
                    }
                }
                NativeSearchOverlay(
                    snapshot = searchState,
                    onQueryChanged = { query ->
                        journeyCoordinator?.updateSearchQuery(query)
                    },
                    onSubmit = { query ->
                        // A saved place that matches what was typed wins over a web
                        // search, as on the Flutter map.
                        val saved = if (query.isBlank()) {
                            null
                        } else {
                            NativeSearchPresenter.favorites(
                                favorites.map { place ->
                                    NativeSearchFavorite(
                                        place.label,
                                        place.address,
                                        SearchResponsePoint(place.point.latitude, place.point.longitude),
                                    )
                                },
                                query.trim(),
                            ).firstOrNull()
                        }
                        if (saved != null) {
                            chooseFavorite(saved.label, saved.address, saved.position)
                        } else {
                            journeyCoordinator?.submitSearch(query, gpsSearchPoint)
                        }
                    },
                    onClearQuery = {
                        journeyCoordinator?.updateSearchQuery("")
                    },
                    onDismiss = {
                        journeyCoordinator?.dismissSearch()
                    },
                    onNearby = { category ->
                        journeyCoordinator?.submitNearby(category, gpsSearchPoint)
                    },
                    onSearchWeb = { journeyCoordinator?.searchWeb() },
                    onConfirmWeb = { journeyCoordinator?.confirmWeb() },
                    onDeclineWeb = { journeyCoordinator?.declineWeb() },
                    onOpenWebResult = { url ->
                        // The host applies the navigation policy; an address it refuses is not
                        // handed to another browser behind its back.
                        if (webBrowser != null) webBrowser.open(url) else onOpenExternal(url)
                    },
                    onOpenWebPlace = { id -> journeyCoordinator?.webPlace(id)?.let(showDiscoveryPlace) },
                    onSelectResult = { result ->
                        val discovered = DiscoveryPresentation.placeAt(
                            result.position.latitude, result.position.longitude, discovery.places,
                        )
                        if (discovered != null && !activeNavigationState.active) {
                            showDiscoveryPlace(discovered)
                        } else if (activeNavigationState.active) {
                            cameraSession.focus(
                                point = NativeMapPoint(result.position.latitude, result.position.longitude),
                                nowMillis = SystemClock.elapsedRealtime(),
                            )
                            journeyCoordinator?.selectDestination(result, gpsSearchPoint, myLocationLabel)
                        } else {
                            showSearchPlace(result.title, result.subtitle, result.position)
                        }
                    },
                    onSelectFavorite = { favorite ->
                        chooseFavorite(favorite.label, favorite.address, favorite.position)
                    },
                    onSelectHistory = { history ->
                        if (activeNavigationState.active) {
                            journeyCoordinator?.selectDestination(
                                history.fullLabel, history.position, gpsSearchPoint, myLocationLabel,
                            )
                        } else {
                            showSearchPlace(history.title, history.subtitle, history.position)
                        }
                    },
                    onClearHistory = {
                        journeyCoordinator?.clearSearchHistory()
                            ?: searchSession.clearHistory(searchState.revision)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
                NativeSettingsPanel(
                    snapshot = settingsState,
                    onAction = { revision, action ->
                        when (action) {
                            NativeSettingsUiAction.Close -> settingsSession.hide(revision)
                            is NativeSettingsUiAction.BooleanChanged -> {
                                settingsSession.updateBoolean(revision, action.key, action.value)
                                if (action.key.storageKey == "voiceEnabled") {
                                    voiceGateway?.setMuted(!action.value)
                                }
                                if (action.key == NativeSettingsBooleanKey.ProfilePublic) {
                                    nostrHost?.visibilityChanged(action.value)
                                }
                            }
                            is NativeSettingsUiAction.ThemeChanged -> {
                                settingsSession.updateTheme(revision, action.value)
                            }
                            is NativeSettingsUiAction.LanguageChanged -> {
                                settingsSession.updateLanguage(revision, action.value)
                            }
                            is NativeSettingsUiAction.MapEngineChanged -> {
                                settingsSession.updateMapEngine(revision, action.value)
                            }
                            is NativeSettingsUiAction.BrightnessChanged -> {
                                settingsSession.updateBrightness(revision, action.value)
                            }
                            is NativeSettingsUiAction.TileUrlChanged -> {
                                settingsSession.updateMapTileUrl(revision, action.value)
                            }
                            is NativeSettingsUiAction.RoutingProviderChanged -> {
                                settingsSession.updateRoutingProvider(revision, action.value)
                            }
                            is NativeSettingsUiAction.GraphHopperServerChanged -> {
                                settingsSession.updateGraphHopperServer(revision, action.value)
                            }
                            is NativeSettingsUiAction.SpeedometerChanged -> {
                                settingsSession.updateSpeedometer(revision, action.value)
                            }
                            is NativeSettingsUiAction.CursorStyleChanged -> {
                                settingsSession.updateCursorStyle(revision, action.value)
                            }
                            is NativeSettingsUiAction.CursorColorChanged -> {
                                settingsSession.updateCursorColor(revision, action.value)
                            }
                            is NativeSettingsUiAction.SearchEngineChanged -> {
                                settingsSession.updateSearchEngine(revision, action.value)
                            }
                            is NativeSettingsUiAction.VoiceGenderChanged -> {
                                settingsSession.updateVoiceGender(revision, action.value)
                            }
                            is NativeSettingsUiAction.VoiceSpeedChanged -> {
                                settingsSession.updateVoiceSpeedStage(revision, action.stage)
                            }
                            is NativeSettingsUiAction.VoiceVolumeChanged -> {
                                settingsSession.updateVoiceVolume(revision, action.value)
                            }
                            NativeSettingsUiAction.OpenMapsAttribution ->
                                onOpenExternal("https://www.openstreetmap.org/copyright")
                            NativeSettingsUiAction.ExportFavorites ->
                                nostrHost?.exportFavorites() ?: run {
                                    val nextRevision = savedPlacesState.revision.coerceAtLeast(0L) + 1L
                                    savedPlacesSession.show(nextRevision, favorites, parkingPosition)
                                    settingsSession.hide(revision)
                                }
                            NativeSettingsUiAction.ImportFavorites ->
                                nostrHost?.requestImport() ?: run {
                                    val nextRevision = savedPlacesState.revision.coerceAtLeast(0L) + 1L
                                    savedPlacesSession.show(nextRevision, favorites, parkingPosition)
                                    settingsSession.hide(revision)
                                }
                            NativeSettingsUiAction.SyncPush -> nostrHost?.push()
                            NativeSettingsUiAction.SyncPull -> nostrHost?.pull()
                            NativeSettingsUiAction.EditSyncPassphrase -> nostrHost?.editSyncPassphrase()
                            NativeSettingsUiAction.EditSyncRelay -> nostrHost?.editSyncRelay()
                            NativeSettingsUiAction.ConfigureRoutingKey -> {
                                routingKeyPrompt = NativeShellPrompt(
                                    title = R.string.native_settings_api_key_hint,
                                    hint = R.string.native_settings_api_key_hint,
                                    secret = true,
                                    confirmLabel = R.string.native_settings_nwc_save,
                                    removeLabel = if (settingsState.values.routingApiKeyConfigured) {
                                        R.string.native_settings_nwc_remove
                                    } else {
                                        null
                                    },
                                    onResult = { result ->
                                        if (result != null && onRoutingKeyChanged(result)) {
                                            settingsSession.refresh(
                                                settingsSession.state.value.revision,
                                                settingsSession.state.value.values.copy(
                                                    routingApiKeyConfigured = result.isNotEmpty(),
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                            NativeSettingsUiAction.TestGraphHopper -> {
                                val server = settingsState.values.graphHopperServer.trim()
                                if (server.isEmpty()) {
                                    Toast.makeText(context, R.string.native_settings_gh_url_required, Toast.LENGTH_SHORT).show()
                                } else {
                                    journeyScope.launch {
                                        val reachable = journeyGateway?.probeRoutingServer(server, null) == true
                                        Toast.makeText(
                                            context,
                                            if (reachable) R.string.native_settings_gh_reachable else R.string.native_settings_gh_unreachable,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                            NativeSettingsUiAction.ConfigureNwc -> {
                                nwcInput = ""
                                nwcError = false
                                nwcDialogVisible = true
                            }
                            NativeSettingsUiAction.OpenSource ->
                                onOpenExternal("https://github.com/roadstrapp/roadstr-app")
                            NativeSettingsUiAction.SupportRoadstr ->
                                onOpenExternal("lightning:lwb89@blink.sv")
                            NativeSettingsUiAction.OpenSavedPlaces -> {
                                val nextRevision = savedPlacesState.revision.coerceAtLeast(0L) + 1L
                                savedPlacesSession.show(nextRevision, favorites, parkingPosition)
                                settingsSession.hide(revision)
                            }
                            NativeSettingsUiAction.OpenWebSearch -> webPanelVisible = true
                            NativeSettingsUiAction.DownloadVoiceModel -> voiceGateway?.downloadAssets()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    webSearchActive = NativeWebSearchEditor.isActive(webSettings),
                )
                if (webBrowser != null && webBrowser.inApp && browserState.open) {
                    NativeWebBrowserScreen(
                        state = browserState,
                        onBack = webBrowser::goBack,
                        onForward = webBrowser::goForward,
                        onReload = webBrowser::reload,
                        onClose = webBrowser::close,
                        onOpenExternal = onOpenExternal,
                        onNavigateHere = webBrowser::navigateToPagePlace,
                        onConfirmAction = webBrowser::confirmAction,
                        onDismissAction = webBrowser::dismissAction,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        content = { pageModifier -> webBrowser.Content(pageModifier) },
                    )
                }
                if (webPanelVisible) {
                    NativeWebSearchPanel(
                        settings = webSettings,
                        status = webStatus,
                        onClose = { webPanelVisible = false },
                        onModeChanged = { updateWebSettings(NativeWebSearchEditor.setMode(webSettings, it)) },
                        onEndpointSubmitted = { text ->
                            val edit = NativeWebSearchEditor.setEndpoint(webSettings, text)
                            if (edit.settings != webSettings) updateWebSettings(edit.settings)
                            edit.rejection
                        },
                        onOwnInstanceChanged = { updateWebSettings(NativeWebSearchEditor.setOwnInstance(webSettings, it)) },
                        onStrictChanged = { updateWebSettings(webSettings.copy(strictSources = it)) },
                        onSafeSearchChanged = { updateWebSettings(webSettings.copy(safeSearch = it)) },
                        onTest = {
                            val edits = webEditCount
                            webStatus = NativeWebSearchStatus.Testing
                            journeyScope.launch {
                                val result = runWebConnectionTest {
                                    journeyGateway?.testWebSearch() ?: ConnectionTest.NotConfigured
                                }
                                if (edits == webEditCount) webStatus = result
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                NativeRoutePlanningPanel(
                    snapshot = routePlanningState,
                    onOriginChanged = { value ->
                        routePlanningSession.updateOrigin(routePlanningState.revision, value)
                    },
                    onUseMyLocation = {
                        routePlanningSession.useMyLocation(
                            routePlanningState.revision,
                            myLocationLabel,
                        )
                    },
                    onStopChanged = { index, value ->
                        routePlanningSession.updateStop(routePlanningState.revision, index, value)
                    },
                    onAddStop = {
                        routePlanningSession.addStop(routePlanningState.revision)
                    },
                    onRemoveStop = { index ->
                        routePlanningSession.removeStop(routePlanningState.revision, index)
                    },
                    onMoveStop = { fromIndex, toIndex ->
                        routePlanningSession.reorderStop(
                            routePlanningState.revision,
                            fromIndex,
                            toIndex,
                        )
                    },
                    onModeChanged = { mode ->
                        val recalculate = routePlanningState.status == NativeRoutePlanningStatus.Alternatives
                        if (routePlanningSession.selectMode(routePlanningState.revision, mode) && recalculate) {
                            journeyCoordinator?.calculateRoute(
                                snapshot = routePlanningSession.state.value,
                                gpsPoint = gpsSearchPoint,
                                myLocationLabel = myLocationLabel,
                                avoidUnpavedRoads = settingsState.values.avoidUnpavedRoads,
                            )
                        }
                    },
                    onCalculate = {
                        journeyCoordinator?.calculateRoute(
                            snapshot = routePlanningState,
                            gpsPoint = gpsSearchPoint,
                            myLocationLabel = myLocationLabel,
                            avoidUnpavedRoads = settingsState.values.avoidUnpavedRoads,
                        )
                    },
                    onSelectAlternative = { index ->
                        routePlanningSession.selectAlternative(routePlanningState.revision, index)
                    },
                    onAvoidanceChanged = { enabled ->
                        if (routePlanningSession.setAvoidanceState(
                            routePlanningState.revision,
                            enabled,
                            loading = false,
                        )) {
                            journeyCoordinator?.calculateRoute(
                                snapshot = routePlanningSession.state.value,
                                gpsPoint = gpsSearchPoint,
                                myLocationLabel = myLocationLabel,
                                avoidUnpavedRoads = settingsState.values.avoidUnpavedRoads,
                            )
                        }
                    },
                    onConfirm = {
                        // Commit and start in the same tap. The Preview state is
                        // retained internally for the atomic hand-off only; it
                        // is never presented as a second confirmation window.
                        if (routePlanningSession.confirmSelection(routePlanningState.revision)) {
                            startSelectedRoute()
                        }
                    },
                    onStart = startSelectedRoute,
                    onCancel = {
                        journeyCoordinator?.cancelRoute()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { routePanelHeightPixels = it.height },
                )
                NativeTransitItinerariesPanel(
                    snapshot = transitUiState,
                    transportMode = transitMode,
                    onSelect = { index ->
                        transitJourneySession.select(transitUiState.revision, index)
                    },
                    onCancel = {
                        if (transitUiState.revision >= 0) {
                            transitJourneySession.clear(transitUiState.revision)
                        }
                    },
                    onRetry = null,
                    onModeChanged = { transitMode = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativePlaceDetailsPanel(
                    snapshot = placeState,
                    onCancel = {
                        if (placeState.revision >= 0) {
                            placeSession.hide(placeState.revision)
                        }
                    },
                    onNavigate = {
                        val point = placeState.point
                        if (point != null) {
                            cameraSession.focus(
                                point = NativeMapPoint(point.latitude, point.longitude),
                                nowMillis = SystemClock.elapsedRealtime(),
                            )
                            val accepted = journeyCoordinator?.selectDestinationAndCalculate(
                                label = placeState.title ?: "Dropped pin",
                                point = point,
                                gpsPoint = gpsSearchPoint,
                                myLocationLabel = myLocationLabel,
                                avoidUnpavedRoads = settingsState.values.avoidUnpavedRoads,
                            ) == true
                            if (accepted) placeSession.hide(placeState.revision)
                        }
                    },
                    onOpenWebsite = { uri -> onOpenExternal(uri.toString()) },
                    onOpenArticle = { article ->
                        val revision = wikipediaState.revision.coerceAtLeast(0L) + 1L
                        wikipediaSession.open(revision, article.toString(), placeState.title ?: "Wikipedia")
                    },
                    searchEngineName = settingsState.values.searchEngine.displayName,
                    onSearchWeb = { query ->
                        onOpenExternal(settingsState.values.searchEngine.searchUrl(query))
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeProfilePanel(
                    snapshot = profileState,
                    onClose = profileSession::hide,
                    onAmberLogin = { revision -> onAmberLogin(revision) },
                    onNsecLogin = {
                        nsecError = false
                        nsecInput = ""
                        nsecDialogVisible = true
                    },
                    onCopyNpub = {
                        identityState.pubkeyHex?.let { pubkey ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("npub", NostrNip19.encodePublicKey(pubkey)),
                            )
                        }
                    },
                    onVisibilityChanged = { revision, profilePublic ->
                        profileSession.updateVisibility(revision, profilePublic)
                        settingsSession.updateBoolean(
                            settingsSession.state.value.revision,
                            NativeSettingsBooleanKey.ProfilePublic,
                            profilePublic,
                        )
                        nostrHost?.visibilityChanged(profilePublic)
                    },
                    onReportSelected = { id ->
                        myReports.firstOrNull { it.id == id }?.let { event ->
                            profileSession.hide(profileState.revision)
                            cameraSession.focus(
                                NativeMapPoint(event.latitude, event.longitude),
                                SystemClock.elapsedRealtime(),
                            )
                            nostrHost?.reports?.showEvent(event)
                        }
                    },
                    onLogout = { revision ->
                        identityGateway?.logout()
                        profileSession.showLoggedOut(revision)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                if (historyVisible) {
                    NativeRouteHistoryPanel(
                        entries = routeHistory,
                        onSelect = { entry ->
                            // The same as tapping that place on the map: focus it and
                            // show the place sheet with "navigate here" and the preview.
                            historyVisible = false
                            journeyScope.launch {
                                cameraSession.focus(entry.point, SystemClock.elapsedRealtime())
                            }
                            inspectPlace(entry.point)
                        },
                        onRemove = { entry ->
                            updateRouteHistory(NativeRouteHistoryProtocol.remove(routeHistory, entry))
                        },
                        onClear = { updateRouteHistory(emptyList()) },
                        onClose = { historyVisible = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (parkingPanelVisible) {
                    NativeParkingPanel(
                        parking = parkingPosition,
                        canSaveHere = gpsSnapshot.fix != null,
                        onSaveHere = {
                            gpsSnapshot.fix?.point?.let(saveParking)
                            parkingPanelVisible = false
                        },
                        onNavigate = {
                            parkingPosition?.let(navigateToParking)
                            parkingPanelVisible = false
                        },
                        onRemove = {
                            parkingPosition = null
                            onParkingChanged(null)
                            Toast.makeText(context, R.string.native_parking_removed, Toast.LENGTH_SHORT).show()
                            parkingPanelVisible = false
                        },
                        onClose = { parkingPanelVisible = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                contextMenuPoint?.let { point ->
                    NativeMapContextMenu(
                        point = point,
                        onSaveParking = {
                            saveParking(point)
                            contextMenuPoint = null
                        },
                        onWhatsHere = {
                            contextMenuPoint = null
                            inspectPlace(point)
                        },
                        onClose = { contextMenuPoint = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                NativeSavedPlacesPanel(
                    snapshot = savedPlacesState,
                    onAdd = {
                        favoriteEditorIndex = null
                        favoriteEditorLabel = ""
                        favoriteEditorAddress = ""
                        favoriteEditorOriginalAddress = ""
                        favoriteEditorError = false
                        favoriteEditorVisible = true
                    },
                    onEdit = { index, place ->
                        favoriteEditorIndex = index
                        favoriteEditorLabel = place.label
                        favoriteEditorAddress = place.address
                        favoriteEditorOriginalAddress = place.address
                        favoriteEditorError = false
                        favoriteEditorVisible = true
                    },
                    onDelete = { index, _ ->
                        if (savedPlacesSession.delete(savedPlacesState.revision, index)) {
                            favorites = savedPlacesSession.state.value.favorites
                            onFavoritesChanged(favorites)
                            nostrHost?.favoritesChangedLocally()
                        }
                    },
                    onExport = { nostrHost?.exportFavorites() },
                    onImport = { nostrHost?.requestImport() },
                    onNavigateParking = navigateToParking,
                    onRemoveParking = {
                        parkingPosition = null
                        onParkingChanged(null)
                    },
                    onClose = { savedPlacesSession.hide(savedPlacesState.revision) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeActivityInboxPanel(
                    snapshot = activityInboxState,
                    onClose = activityInboxSession::hide,
                    onViewed = { revision ->
                        activityInboxSession.markAllRead(revision)?.let { write -> nostrHost?.inboxChanged(write) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeRoadEventPanels(
                    snapshot = roadEventState,
                    onClose = roadEventSession::hide,
                    onAcceptPrivacy = { revision ->
                        nostrHost?.reports?.acceptPrivacy(revision)
                            ?: roadEventSession.acceptPrivacy(revision)
                    },
                    onSelectCategory = roadEventSession::selectCategory,
                    onCommentChanged = roadEventSession::updateComment,
                    onSpeedChanged = roadEventSession::updateSpeed,
                    onSubmit = { revision -> nostrHost?.reports?.submit(revision) },
                    onOpenReporter = {
                        roadEventState.detail?.reporterNpub?.let { npub -> onOpenExternal("nostr:$npub") }
                    },
                    onVote = { _, stillThere ->
                        roadEventState.detail?.id?.let { id -> nostrHost?.reports?.vote(id, stillThere) }
                    },
                    onEditSpeedLimit = { _, requestId ->
                        val event = nostrHost?.reports?.currentEvent
                        if (event != null && event.id == roadEventState.detail?.id) {
                            if (requestId != null) {
                                nostrHost.reports.acceptEditRequest(event, requestId)
                            } else {
                                nostrHost.promptSpeedLimit(event, settingsState.values.imperialUnits)
                            }
                        }
                    },
                    onZap = {
                        val event = nostrHost?.reports?.currentEvent
                        if (event != null && event.id == roadEventState.detail?.id) nostrHost.zap.open(event)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeWikipediaReader(
                    snapshot = wikipediaState,
                    onClose = {
                        if (wikipediaState.revision >= 0) {
                            wikipediaSession.hide(wikipediaState.revision)
                        }
                    },
                    onOpenExternal = { uri -> onOpenExternal(uri.toString()) },
                    onProgress = { revision, progress ->
                        wikipediaSession.progress(revision, progress)
                    },
                    onPageStarted = { revision, url ->
                        wikipediaSession.pageStarted(revision, url)
                    },
                    onPageFinished = { revision, url, canGoBack ->
                        wikipediaSession.pageFinished(
                            revision,
                            url,
                            canGoBack,
                        )
                    },
                    onFailed = { revision ->
                        wikipediaSession.fail(revision)
                    },
                    onRetry = { revision ->
                        wikipediaSession.retry(revision)
                    },
                )
                NativeNavigationHud(
                    snapshot = if (
                        searchState.status == NativeSearchUiStatus.Hidden &&
                        placeState.status == app.roadstr.feature.place.NativePlaceUiStatus.Hidden &&
                        settingsState.status == NativeSettingsStatus.Hidden &&
                        routePlanningState.status == NativeRoutePlanningStatus.Hidden &&
                        wikipediaState.status == app.roadstr.feature.wikipedia.NativeWikipediaStatus.Hidden
                    ) {
                        navigationHudState
                    } else {
                        app.roadstr.feature.navigation.NativeNavigationHudSnapshot.hidden(
                            navigationHudState.revision,
                        )
                    },
                    onStop = { exitNavigationDialogVisible = true },
                    onToggleVoice = {
                        if (activeNavigationSession.toggleVoice(activeNavigationState.revision)) {
                            voiceGateway?.setMuted(activeNavigationSession.state.value.voiceMuted)
                        }
                    },
                    onOpenSettings = showSettings,
                    onBottomPanelHeight = { navPanelHeightPixels = it },
                )
                NativeNavigationArrivalBanner(
                    visible = activeNavigationState.arrived,
                    onDismiss = {
                        activeNavigationSession.dismissArrival(activeNavigationState.revision)
                    },
                )
                if (exitNavigationDialogVisible) {
                    AlertDialog(
                        onDismissRequest = { exitNavigationDialogVisible = false },
                        title = { androidx.compose.material3.Text(stringResource(R.string.native_nav_exit)) },
                        text = { androidx.compose.material3.Text(stringResource(R.string.native_nav_exit_body)) },
                        confirmButton = {
                            Button(onClick = stopNavigation) {
                                androidx.compose.material3.Text(stringResource(R.string.native_nav_exit_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { exitNavigationDialogVisible = false }) {
                                androidx.compose.material3.Text(stringResource(R.string.native_nav_continue))
                            }
                        },
                    )
                }
                if (favoriteEditorVisible) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!favoriteEditorBusy) favoriteEditorVisible = false
                        },
                        title = {
                            androidx.compose.material3.Text(
                                if (favoriteEditorIndex == null) {
                                    stringResource(R.string.native_saved_add)
                                } else {
                                    favoriteEditorLabel
                                },
                            )
                        },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                OutlinedTextField(
                                    value = favoriteEditorLabel,
                                    onValueChange = {
                                        favoriteEditorLabel = it.take(200)
                                        favoriteEditorError = false
                                    },
                                    label = { androidx.compose.material3.Text(stringResource(R.string.native_saved_label)) },
                                    singleLine = true,
                                    enabled = !favoriteEditorBusy,
                                )
                                OutlinedTextField(
                                    value = favoriteEditorAddress,
                                    onValueChange = {
                                        favoriteEditorAddress = it.take(500)
                                        favoriteEditorError = false
                                    },
                                    modifier = Modifier.padding(top = 10.dp),
                                    label = { androidx.compose.material3.Text(stringResource(R.string.native_place_address)) },
                                    singleLine = true,
                                    enabled = !favoriteEditorBusy,
                                )
                                if (favoriteEditorError) {
                                    androidx.compose.material3.Text(
                                        stringResource(R.string.native_saved_geocoding_error),
                                        modifier = Modifier.padding(top = 8.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = !favoriteEditorBusy &&
                                    favoriteEditorLabel.isNotBlank() &&
                                    favoriteEditorAddress.isNotBlank(),
                                onClick = {
                                    favoriteEditorBusy = true
                                    favoriteEditorError = false
                                    journeyScope.launch {
                                        val index = favoriteEditorIndex
                                        val existing = index?.let { favorites.getOrNull(it) }
                                        val point = if (
                                            existing != null &&
                                            favoriteEditorAddress.trim() == favoriteEditorOriginalAddress.trim()
                                        ) {
                                            SearchResponsePoint(
                                                existing.point.latitude,
                                                existing.point.longitude,
                                            )
                                        } else {
                                            runCatching {
                                                journeyGateway?.search(
                                                    query = favoriteEditorAddress.trim(),
                                                    near = gpsSearchPoint,
                                                    languageCode = voiceLanguage,
                                                    onPartial = {},
                                                )?.firstOrNull()?.position
                                            }.getOrNull()
                                        }
                                        if (point == null) {
                                            favoriteEditorBusy = false
                                            favoriteEditorError = true
                                        } else {
                                            val place = NativeSavedPlace(
                                                label = favoriteEditorLabel.trim(),
                                                address = favoriteEditorAddress.trim(),
                                                point = NativeMapPoint(point.latitude, point.longitude),
                                            )
                                            if (savedPlacesSession.upsert(savedPlacesState.revision, place, index)) {
                                                favorites = savedPlacesSession.state.value.favorites
                                                onFavoritesChanged(favorites)
                                                nostrHost?.favoritesChangedLocally()
                                                favoriteEditorVisible = false
                                            } else {
                                                favoriteEditorError = true
                                            }
                                            favoriteEditorBusy = false
                                        }
                                    }
                                },
                            ) {
                                androidx.compose.material3.Text(stringResource(R.string.native_settings_nwc_save))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !favoriteEditorBusy,
                                onClick = { favoriteEditorVisible = false },
                            ) {
                                androidx.compose.material3.Text(stringResource(R.string.native_route_cancel))
                            }
                        },
                    )
                }
                NativeOnboardingFlow(
                    snapshot = onboardingState,
                    onPageSelected = { revision, page ->
                        onboardingSession.selectPage(revision, page)
                    },
                    onAmberLogin = { onAmberLogin(onboardingState.revision) },
                    onNsecLogin = {
                        nsecError = false
                        nsecInput = ""
                        nsecDialogVisible = true
                    },
                    onProfileVisibilityChanged = { revision, value ->
                        onboardingSession.updateProfileVisibility(revision, value)
                    },
                    onRequestLocation = onGpsAction,
                    onDownloadVoice = { voiceGateway?.downloadAssets() },
                    onOpenDisclosure = { revision ->
                        onboardingSession.openDisclosure(revision)
                    },
                    onAcceptDisclosure = { revision ->
                        if (onboardingSession.acceptDisclosure(revision) != null) {
                            onOnboardingCompleted()
                        }
                    },
                )
                NativeShellNostrDialogs(nostrHost)
                routingKeyPrompt?.let { prompt ->
                    NativeShellPromptDialog(
                        prompt = prompt,
                        onConfirm = { value ->
                            routingKeyPrompt = null
                            prompt.onResult(value)
                        },
                        onDismiss = { routingKeyPrompt = null },
                    )
                }
                if (nsecDialogVisible) {
                    val dismissNsecDialog = {
                        nsecInput = ""
                        nsecError = false
                        nsecDialogVisible = false
                    }
                    AlertDialog(
                        onDismissRequest = dismissNsecDialog,
                        title = { androidx.compose.material3.Text("Accedi con nsec") },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                OutlinedTextField(
                                    value = nsecInput,
                                    onValueChange = { nsecInput = it.take(120); nsecError = false },
                                    label = { androidx.compose.material3.Text("nsec") },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                    ),
                                )
                                if (nsecError) {
                                    androidx.compose.material3.Text(
                                        "Chiave nsec non valida",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    journeyScope.launch {
                                        val accepted = identityGateway?.loginNsec(nsecInput) == true
                                        if (accepted) {
                                            nsecDialogVisible = false
                                            nsecInput = ""
                                        } else {
                                            nsecError = true
                                        }
                                    }
                                },
                                enabled = nsecInput.isNotBlank() && identityGateway != null,
                            ) { androidx.compose.material3.Text("Accedi") }
                        },
                        dismissButton = {
                            TextButton(onClick = dismissNsecDialog) {
                                androidx.compose.material3.Text("Annulla")
                            }
                        },
                    )
                }
                if (nwcDialogVisible) {
                    AlertDialog(
                        onDismissRequest = {
                            nwcInput = ""
                            nwcError = false
                            nwcDialogVisible = false
                        },
                        title = { androidx.compose.material3.Text(stringResource(R.string.native_settings_nwc)) },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                OutlinedTextField(
                                    value = nwcInput,
                                    onValueChange = { nwcInput = it.take(4_096); nwcError = false },
                                    label = { androidx.compose.material3.Text("nostr+walletconnect://…") },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                    ),
                                )
                                if (nwcError) {
                                    androidx.compose.material3.Text(
                                        stringResource(R.string.native_settings_nwc_invalid),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val accepted = onNwcChanged(nwcInput.trim())
                                    if (accepted) {
                                        settingsSession.refresh(
                                            settingsState.revision,
                                            settingsSession.state.value.values.copy(nwcConfigured = true),
                                        )
                                        nwcInput = ""
                                        nwcDialogVisible = false
                                    } else {
                                        nwcError = true
                                    }
                                },
                                enabled = nwcInput.isNotBlank(),
                            ) { androidx.compose.material3.Text(stringResource(R.string.native_settings_nwc_save)) }
                        },
                        dismissButton = {
                            if (settingsState.values.nwcConfigured) {
                                TextButton(onClick = {
                                    if (onNwcChanged("")) {
                                        settingsSession.refresh(
                                            settingsState.revision,
                                            settingsSession.state.value.values.copy(nwcConfigured = false),
                                        )
                                        nwcDialogVisible = false
                                    }
                                }) {
                                    androidx.compose.material3.Text(stringResource(R.string.native_settings_nwc_remove))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    }
}
