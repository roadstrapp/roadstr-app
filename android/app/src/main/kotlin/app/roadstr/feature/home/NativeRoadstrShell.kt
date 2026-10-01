package app.roadstr.feature.home

import android.os.SystemClock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
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
import app.roadstr.feature.map.NativeMapPointOverlaySession
import app.roadstr.feature.map.NativeMapStyle
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.map.NativeTransitOverlaySession
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
import app.roadstr.feature.saved.NativeSavedPlacesPanel
import app.roadstr.feature.saved.NativeSavedPlacesSession
import app.roadstr.feature.search.NativeSearchOverlay
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.settings.NativeSettingsPanel
import app.roadstr.feature.settings.NativeSettingsSession
import app.roadstr.feature.settings.NativeSettingsUiAction
import app.roadstr.feature.transit.NativeTransitItinerariesPanel
import app.roadstr.feature.transit.NativeTransitJourneySession
import app.roadstr.feature.transit.NativeTransitTransportMode
import app.roadstr.feature.wikipedia.NativeWikipediaReader
import app.roadstr.feature.wikipedia.NativeWikipediaSession
import kotlinx.coroutines.delay

/**
 * Dormant native UI boundary used to prove Compose and MapLibre packaging.
 *
 * It owns no storage, location or migration state and is not the launcher. If
 * invoked explicitly from inside the app, only the admitted OSM raster source
 * may perform network I/O. Product screens replace this boundary incrementally
 * after their parity gates are green.
 */
@Composable
fun NativeRoadstrShell() {
    val themeId = if (isSystemInDarkTheme()) {
        RoadstrThemeId.DarkNostr
    } else {
        RoadstrThemeId.LightNostr
    }
    RoadstrTheme(themeId = themeId) {
        val shellDescription = stringResource(R.string.native_shell_description)
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
        val pointOverlaySession = remember { NativeMapPointOverlaySession() }
        val pointOverlayState by pointOverlaySession.state.collectAsState()
        val searchSession = remember { NativeSearchSession(initialImperial = false) }
        val searchState by searchSession.state.collectAsState()
        val settingsSession = remember { NativeSettingsSession() }
        val settingsState by settingsSession.state.collectAsState()
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
        val onboardingSession = remember { NativeOnboardingSession() }
        val onboardingState by onboardingSession.state.collectAsState()
        val wikipediaSession = remember { NativeWikipediaSession() }
        val wikipediaState by wikipediaSession.state.collectAsState()
        LaunchedEffect(cameraSession, cameraState.frameActive) {
            while (cameraSession.state.value.frameActive) {
                delay(NativeMapCameraSession.FOLLOW_FRAME_MILLIS)
                cameraSession.advanceFrame(SystemClock.elapsedRealtime())
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
                NativeSearchOverlay(
                    snapshot = searchState,
                    onQueryChanged = {},
                    onSubmit = {},
                    onClearQuery = {},
                    onNearby = {},
                    onSelectResult = {},
                    onSelectFavorite = {},
                    onSelectHistory = {},
                    onClearHistory = {},
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
                            NativeSettingsUiAction.DownloadVoiceModel,
                            NativeSettingsUiAction.OpenMapsAttribution,
                            NativeSettingsUiAction.OpenSource,
                            NativeSettingsUiAction.SupportRoadstr,
                            -> Unit
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeRoutePlanningPanel(
                    snapshot = routePlanningState,
                    onOriginChanged = { value ->
                        routePlanningSession.updateOrigin(routePlanningState.revision, value)
                    },
                    onUseMyLocation = {},
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
                    onCalculate = {},
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
                    onStart = {},
                    onCancel = {
                        if (routePlanningState.revision >= 0) {
                            routePlanningSession.hide(routePlanningState.revision)
                        }
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
                    onStop = {},
                    onToggleVoice = {},
                    onOpenSettings = {},
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
                    onRequestLocation = {},
                    onDownloadVoice = {},
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
