package app.roadstr.feature.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.ui.theme.RoadstrTheme
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.core.ui.theme.RoadstrThemeTokens
import app.roadstr.feature.activity.NativeActivityInboxPanel
import app.roadstr.feature.activity.NativeActivityInboxSession
import app.roadstr.feature.map.NativeMapCameraSession
import app.roadstr.feature.map.NativeMapCursorSession
import app.roadstr.feature.map.NativeMapEngine
import app.roadstr.feature.map.NativeMapInteraction
import app.roadstr.feature.map.NativeMapLibreHost
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeMapPointOverlaySession
import app.roadstr.feature.map.NativeMapStyle
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.map.NativeTransitOverlaySession
import app.roadstr.feature.navigation.NativeActiveNavigationSession
import app.roadstr.feature.navigation.NativeNavigationArrivalBanner
import app.roadstr.feature.navigation.NativeNavigationHud
import app.roadstr.feature.navigation.NativeNavigationHudSession
import app.roadstr.feature.onboarding.NativeOnboardingFlow
import app.roadstr.feature.onboarding.NativeOnboardingSession
import app.roadstr.feature.place.NativePlaceDetailsPanel
import app.roadstr.feature.place.NativePlaceSession
import app.roadstr.feature.profile.NativeProfilePanel
import app.roadstr.feature.profile.NativeProfileSession
import app.roadstr.feature.report.NativeRoadEventPanels
import app.roadstr.feature.report.NativeRoadEventSession
import app.roadstr.feature.route.NativeRoutePlanningPanel
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningStatus
import app.roadstr.feature.saved.NativeSavedPlacesPanel
import app.roadstr.feature.saved.NativeSavedPlacesSession
import app.roadstr.feature.search.NativeSearchOverlay
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.search.NativeSearchUiStatus
import app.roadstr.feature.settings.NativeSettingsPanel
import app.roadstr.feature.settings.NativeSettingsSession
import app.roadstr.feature.settings.NativeSettingsUiAction
import app.roadstr.feature.settings.NativeSettingsStatus
import app.roadstr.feature.settings.NativeSettingsVoiceGender
import app.roadstr.feature.settings.NativeSettingsVoiceModelStatus
import app.roadstr.feature.transit.NativeTransitItinerariesPanel
import app.roadstr.feature.transit.NativeTransitJourneySession
import app.roadstr.feature.transit.NativeTransitTransportMode
import app.roadstr.feature.wikipedia.NativeWikipediaReader
import app.roadstr.feature.wikipedia.NativeWikipediaSession
import app.roadstr.feature.voice.NativeVoiceCatalog
import app.roadstr.feature.voice.NativeVoiceGateway
import app.roadstr.feature.voice.NativeVoiceGender
import app.roadstr.feature.voice.NativeVoiceRuntimeStatus
import java.util.Locale
import kotlinx.coroutines.delay

enum class NativeShellMode {
    Canary,
    RoadTest,
}

private const val ARRIVAL_BANNER_MILLIS = 6_000L

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
@Composable
fun NativeRoadstrShell(
    mode: NativeShellMode = NativeShellMode.Canary,
    gpsSnapshot: NativeShellGpsSnapshot = NativeShellGpsSnapshot.Disabled,
    journeyGateway: NativeShellJourneyGateway? = null,
    voiceGateway: NativeVoiceGateway? = null,
    onGpsAction: () -> Unit = {},
) {
    val themeId = if (isSystemInDarkTheme()) {
        RoadstrThemeId.DarkNostr
    } else {
        RoadstrThemeId.LightNostr
    }
    RoadstrTheme(themeId = themeId) {
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
        LaunchedEffect(transitJourneySession, palette.accentArgb) {
            transitJourneySession.updatePresentation(
                accentArgb = palette.accentArgb,
                imperial = false,
            )
        }
        val transitUiState by transitJourneySession.state.collectAsState()
        var transitMode by remember { mutableStateOf(NativeTransitTransportMode.Transit) }
        val cameraSession = remember { NativeMapCameraSession() }
        val cameraState by cameraSession.state.collectAsState()
        val cursorSession = remember { NativeMapCursorSession() }
        val cursorState by cursorSession.state.collectAsState()
        LaunchedEffect(gpsSnapshot.fix?.sequence) {
            val fix = gpsSnapshot.fix ?: return@LaunchedEffect
            cursorSession.submitPosition(fix.sequence, fix.point)
            cameraSession.submitFix(
                sequence = fix.sequence,
                point = fix.point,
                headingDegrees = fix.headingDegrees ?: 0.0,
                speedMetersPerSecond = fix.speedMetersPerSecond,
                receivedAtMillis = fix.receivedAtElapsedRealtimeMillis,
            )
        }
        val pointOverlaySession = remember { NativeMapPointOverlaySession() }
        val pointOverlayState by pointOverlaySession.state.collectAsState()
        val searchSession = remember { NativeSearchSession(initialImperial = false) }
        val searchState by searchSession.state.collectAsState()
        val journeyScope = rememberCoroutineScope()
        val journeyCoordinator = remember(
            journeyGateway,
            journeyScope,
            searchSession,
            routePlanningSession,
        ) {
            journeyGateway?.let { gateway ->
                NativeShellJourneyCoordinator(
                    gateway = gateway,
                    scope = journeyScope,
                    searchSession = searchSession,
                    routeSession = routePlanningSession,
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
        val settingsSession = remember { NativeSettingsSession() }
        val settingsState by settingsSession.state.collectAsState()
        val voiceRuntimeState = voiceGateway?.state?.collectAsState()?.value
        val voiceLanguage = settingsState.values.languageCode ?: Locale.getDefault().language
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
            settingsSession.show(
                revision = settingsState.revision + 1L,
                input = settingsState.values.copy(
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
            Unit
        }
        val placeSession = remember { NativePlaceSession() }
        val placeState by placeSession.state.collectAsState()
        val profileSession = remember { NativeProfileSession() }
        val profileState by profileSession.state.collectAsState()
        val savedPlacesSession = remember { NativeSavedPlacesSession() }
        val savedPlacesState by savedPlacesSession.state.collectAsState()
        val activityInboxSession = remember { NativeActivityInboxSession() }
        val activityInboxState by activityInboxSession.state.collectAsState()
        val roadEventSession = remember { NativeRoadEventSession() }
        val roadEventState by roadEventSession.state.collectAsState()
        val navigationHudSession = remember { NativeNavigationHudSession() }
        val navigationHudState by navigationHudSession.state.collectAsState()
        val activeNavigationSession = remember(navigationHudSession, routeSession) {
            NativeActiveNavigationSession(navigationHudSession, routeSession)
        }
        val activeNavigationState by activeNavigationSession.state.collectAsState()
        val navigationNowLabel = stringResource(R.string.native_nav_now)
        LaunchedEffect(gpsSnapshot.fix?.sequence, activeNavigationState.active) {
            if (!activeNavigationState.active) return@LaunchedEffect
            val fix = gpsSnapshot.fix ?: return@LaunchedEffect
            activeNavigationSession.submitFix(
                sequence = fix.sequence,
                point = fix.point,
                speedMetersPerSecond = fix.speedMetersPerSecond,
                altitudeMeters = fix.altitudeMeters,
                accuracyMeters = fix.accuracyMeters,
                headingDegrees = fix.headingDegrees,
            )
        }
        LaunchedEffect(activeNavigationState.rerouteRequest?.sequence) {
            val request = activeNavigationState.rerouteRequest ?: return@LaunchedEffect
            val accepted = journeyCoordinator?.reroute(
                request = request,
                onSuccess = { sequence, route ->
                    activeNavigationSession.completeReroute(sequence, route)
                    routePlanningSession.synchronizeNavigationRevision(
                        activeNavigationSession.state.value.revision,
                    )
                },
                onFailure = activeNavigationSession::failReroute,
            ) ?: false
            if (!accepted) activeNavigationSession.failReroute(request.sequence)
        }
        LaunchedEffect(activeNavigationState.voiceCue?.sequence) {
            val cue = activeNavigationState.voiceCue ?: return@LaunchedEffect
            voiceGateway?.announceManeuver(
                instruction = cue.instruction,
                distanceMeters = cue.distanceMeters,
                nowMillis = SystemClock.elapsedRealtime(),
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
                screenHeightPixels = NativeMapCameraSession.DEFAULT_SCREEN_HEIGHT_PIXELS,
            )
            cameraSession.recenter(SystemClock.elapsedRealtime())
            delay(ARRIVAL_BANNER_MILLIS)
            activeNavigationSession.dismissArrival(activeNavigationState.revision)
        }
        val onboardingSession = remember { NativeOnboardingSession() }
        val onboardingState by onboardingSession.state.collectAsState()
        val homeSession = remember { NativeHomeSession() }
        val homeState by homeSession.state.collectAsState()
        val wikipediaSession = remember { NativeWikipediaSession() }
        val wikipediaState by wikipediaSession.state.collectAsState()
        LaunchedEffect(
            searchState.status,
            routePlanningState.status,
            activeNavigationState.active,
        ) {
            homeSession.replace(
                revision = homeSession.state.value.revision + 1L,
                input = NativeHomeInput(
                    navigating = activeNavigationState.active,
                    searchVisible = searchState.status != NativeSearchUiStatus.Hidden,
                    plannerVisible = routePlanningState.status == NativeRoutePlanningStatus.Planner,
                    previewVisible = routePlanningState.status == NativeRoutePlanningStatus.Preview,
                    alternativesVisible = routePlanningState.status == NativeRoutePlanningStatus.Alternatives,
                    calculating = routePlanningState.status == NativeRoutePlanningStatus.Loading,
                    hasRoute = routePlanningState.status == NativeRoutePlanningStatus.Alternatives ||
                        routePlanningState.status == NativeRoutePlanningStatus.Preview,
                ),
            )
        }
        LaunchedEffect(cameraSession, cameraState.frameActive) {
            while (cameraSession.state.value.frameActive) {
                delay(NativeMapCameraSession.FOLLOW_FRAME_MILLIS)
                cameraSession.advanceFrame(SystemClock.elapsedRealtime())
            }
        }
        BackHandler(
            enabled = journeyCoordinator != null && (
                searchState.status != NativeSearchUiStatus.Hidden ||
                    routePlanningState.status != NativeRoutePlanningStatus.Hidden ||
                    activeNavigationState.active
                ),
        ) {
            when {
                searchState.status != NativeSearchUiStatus.Hidden -> {
                    journeyCoordinator?.dismissSearch()
                }
                routePlanningState.status != NativeRoutePlanningStatus.Hidden -> {
                    journeyCoordinator?.cancelRoute()
                }
                activeNavigationState.active -> {
                    if (activeNavigationSession.stop(activeNavigationState.revision)) {
                        journeyCoordinator?.cancelReroute()
                        voiceGateway?.stop()
                        cameraSession.configure(
                            headingUp = true,
                            navigating = false,
                            zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                            pitchDegrees = NativeMapCameraSession.FREE_DRIVE_PITCH,
                            screenHeightPixels = NativeMapCameraSession.DEFAULT_SCREEN_HEIGHT_PIXELS,
                        )
                        cameraSession.recenter(SystemClock.elapsedRealtime())
                    }
                }
            }
        }
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = shellDescription },
            containerColor = MaterialTheme.colorScheme.background,
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                NativeMapLibreHost(
                    dark = themeId.dark,
                    mapEngine = NativeMapEngine.MapLibre,
                    tileUrl = NativeMapStyle.DEFAULT_TILE_URL,
                    routeOverlay = routeState.snapshot,
                    transitOverlay = transitState,
                    cameraCommand = cameraState.command,
                    cursorSnapshot = cursorState,
                    pointOverlay = pointOverlayState,
                    onCameraGesture = cameraSession::onUserGesture,
                    onMapInteraction = { interaction ->
                        if (interaction is NativeMapInteraction.MapTap) {
                            if (
                                !routePlanningSession.selectAlternativeAt(
                                    revision = routePlanningState.revision,
                                    point = interaction.point,
                                )
                            ) {
                                routeSession.selectAlternativeAt(
                                    revision = routeSession.state.value.revision,
                                    tap = interaction.point,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (
                    mode == NativeShellMode.RoadTest &&
                    searchState.status == NativeSearchUiStatus.Hidden &&
                    routePlanningState.status == NativeRoutePlanningStatus.Hidden &&
                    !activeNavigationState.active
                ) {
                    NativeShellGpsPanel(
                        snapshot = gpsSnapshot,
                        onAction = {
                            if (gpsSnapshot.phase == NativeShellGpsPhase.Active) {
                                cameraSession.recenter(SystemClock.elapsedRealtime())
                            } else {
                                onGpsAction()
                            }
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .padding(16.dp)
                            .wrapContentSize(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.native_map_canary_status),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                NativeHomeChrome(
                    snapshot = homeState,
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
                            NativeHomeAction.Parking,
                            NativeHomeAction.Activity,
                            NativeHomeAction.Events,
                            NativeHomeAction.Notifications,
                            NativeHomeAction.Profile,
                            null,
                            -> Unit
                        }
                    },
                    onFavorite = { revision, id ->
                        homeSession.selectFavorite(revision, id)
                    },
                )
                NativeSearchOverlay(
                    snapshot = searchState,
                    onQueryChanged = { query ->
                        journeyCoordinator?.updateSearchQuery(query)
                    },
                    onSubmit = { query ->
                        journeyCoordinator?.submitSearch(query, gpsSearchPoint)
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
                    onSelectResult = { result ->
                        journeyCoordinator?.selectDestination(
                            result = result,
                            gpsPoint = gpsSearchPoint,
                            myLocationLabel = myLocationLabel,
                        )
                    },
                    onSelectFavorite = { favorite ->
                        journeyCoordinator?.selectDestination(
                            label = favorite.label,
                            point = favorite.position,
                            gpsPoint = gpsSearchPoint,
                            myLocationLabel = myLocationLabel,
                        )
                    },
                    onSelectHistory = { history ->
                        journeyCoordinator?.selectDestination(
                            label = history.fullLabel,
                            point = history.position,
                            gpsPoint = gpsSearchPoint,
                            myLocationLabel = myLocationLabel,
                        )
                    },
                    onClearHistory = {
                        searchSession.clearHistory(searchState.revision)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
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
                            NativeSettingsUiAction.ConfigureRoutingKey,
                            NativeSettingsUiAction.TestGraphHopper,
                            NativeSettingsUiAction.ConfigureNwc,
                            NativeSettingsUiAction.OpenSavedPlaces,
                            NativeSettingsUiAction.ExportFavorites,
                            NativeSettingsUiAction.ImportFavorites,
                            NativeSettingsUiAction.SyncPush,
                            NativeSettingsUiAction.SyncPull,
                            NativeSettingsUiAction.EditSyncPassphrase,
                            NativeSettingsUiAction.EditSyncRelay,
                            NativeSettingsUiAction.OpenMapsAttribution,
                            NativeSettingsUiAction.OpenSource,
                            NativeSettingsUiAction.SupportRoadstr,
                            -> Unit
                            NativeSettingsUiAction.DownloadVoiceModel -> voiceGateway?.downloadAssets()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
                        routePlanningSession.selectMode(routePlanningState.revision, mode)
                    },
                    onCalculate = {
                        journeyCoordinator?.calculateRoute(
                            snapshot = routePlanningState,
                            gpsPoint = gpsSearchPoint,
                            myLocationLabel = myLocationLabel,
                        )
                    },
                    onSelectAlternative = { index ->
                        routePlanningSession.selectAlternative(routePlanningState.revision, index)
                    },
                    onAvoidanceChanged = { enabled ->
                        routePlanningSession.setAvoidanceState(
                            routePlanningState.revision,
                            enabled,
                            loading = false,
                        )
                    },
                    onConfirm = {
                        routePlanningSession.confirmSelection(routePlanningState.revision)
                    },
                    onStart = {
                        routePlanningSession
                            .selectedNavigationRoute(routePlanningState.revision)
                            ?.let { route ->
                                val revision = routePlanningState.revision
                                val destination = journeyCoordinator
                                    ?.navigationDestination(revision)
                                    ?: return@let
                                if (
                                    activeNavigationSession.start(
                                        revision = revision,
                                        route = route,
                                        destination = NativeMapPoint(
                                            destination.latitude,
                                            destination.longitude,
                                        ),
                                        mode = routePlanningState.mode,
                                        nowLabel = navigationNowLabel,
                                    )
                                ) {
                                    if (routePlanningSession.beginNavigation(revision)) {
                                        voiceGateway?.setMuted(!settingsState.values.voiceEnabled)
                                        if (settingsState.values.voiceEnabled) {
                                            voiceGateway?.announceStart()
                                        }
                                        cameraSession.configure(
                                            headingUp = true,
                                            navigating = true,
                                            zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                                            pitchDegrees = NativeMapCameraSession.NAVIGATION_PITCH,
                                            screenHeightPixels = NativeMapCameraSession.DEFAULT_SCREEN_HEIGHT_PIXELS,
                                        )
                                        cameraSession.recenter(SystemClock.elapsedRealtime())
                                    } else {
                                        activeNavigationSession.stop(revision)
                                    }
                                }
                            }
                    },
                    onCancel = {
                        journeyCoordinator?.cancelRoute()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                    onNavigate = {},
                    onOpenWebsite = {},
                    onOpenArticle = {},
                    onSearchWeb = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeProfilePanel(
                    snapshot = profileState,
                    onClose = profileSession::hide,
                    onAmberLogin = {},
                    onNsecLogin = {},
                    onCopyNpub = {},
                    onVisibilityChanged = { revision, profilePublic ->
                        profileSession.updateVisibility(revision, profilePublic)
                    },
                    onReportSelected = {},
                    onLogout = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeSavedPlacesPanel(
                    snapshot = savedPlacesState,
                    onAdd = {},
                    onEdit = { _, _ -> },
                    onDelete = { _, _ -> },
                    onExport = {},
                    onImport = {},
                    onNavigateParking = {},
                    onRemoveParking = {},
                    onClose = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeActivityInboxPanel(
                    snapshot = activityInboxState,
                    onClose = activityInboxSession::hide,
                    onViewed = { revision ->
                        activityInboxSession.markAllRead(revision)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeRoadEventPanels(
                    snapshot = roadEventState,
                    onClose = roadEventSession::hide,
                    onAcceptPrivacy = roadEventSession::acceptPrivacy,
                    onSelectCategory = roadEventSession::selectCategory,
                    onCommentChanged = roadEventSession::updateComment,
                    onSpeedChanged = roadEventSession::updateSpeed,
                    onSubmit = {},
                    onOpenReporter = {},
                    onVote = { _, _ -> },
                    onEditSpeedLimit = { _, _ -> },
                    onZap = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeWikipediaReader(
                    snapshot = wikipediaState,
                    onClose = {
                        if (wikipediaState.revision >= 0) {
                            wikipediaSession.hide(wikipediaState.revision)
                        }
                    },
                    onOpenExternal = {},
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
                    snapshot = navigationHudState,
                    onStop = {
                        if (activeNavigationSession.stop(activeNavigationState.revision)) {
                            journeyCoordinator?.cancelReroute()
                            voiceGateway?.stop()
                            cameraSession.configure(
                                headingUp = true,
                                navigating = false,
                                zoom = NativeMapCameraSession.DEFAULT_ZOOM,
                                pitchDegrees = NativeMapCameraSession.FREE_DRIVE_PITCH,
                                screenHeightPixels = NativeMapCameraSession.DEFAULT_SCREEN_HEIGHT_PIXELS,
                            )
                            cameraSession.recenter(SystemClock.elapsedRealtime())
                        }
                    },
                    onToggleVoice = {
                        if (activeNavigationSession.toggleVoice(activeNavigationState.revision)) {
                            voiceGateway?.setMuted(activeNavigationSession.state.value.voiceMuted)
                        }
                    },
                    onOpenSettings = showSettings,
                )
                NativeNavigationArrivalBanner(
                    visible = activeNavigationState.arrived,
                    onDismiss = {
                        activeNavigationSession.dismissArrival(activeNavigationState.revision)
                    },
                )
                NativeOnboardingFlow(
                    snapshot = onboardingState,
                    onPageSelected = { revision, page ->
                        onboardingSession.selectPage(revision, page)
                    },
                    onAmberLogin = {},
                    onNsecLogin = {},
                    onProfileVisibilityChanged = { revision, value ->
                        onboardingSession.updateProfileVisibility(revision, value)
                    },
                    onRequestLocation = onGpsAction,
                    onDownloadVoice = { voiceGateway?.downloadAssets() },
                    onOpenDisclosure = { revision ->
                        onboardingSession.openDisclosure(revision)
                    },
                    onAcceptDisclosure = { revision ->
                        onboardingSession.acceptDisclosure(revision)
                    },
                )
            }
        }
    }
}
