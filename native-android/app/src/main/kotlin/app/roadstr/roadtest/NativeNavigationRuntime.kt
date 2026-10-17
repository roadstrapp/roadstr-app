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
import app.roadstr.feature.savedtransit.NativeActiveTransitJourney
import app.roadstr.feature.savedtransit.NativeActiveTransitStore
import app.roadstr.feature.transit.NativeTransitNavigationSession
import app.roadstr.feature.transit.NativeTransitNavigationStage
import app.roadstr.feature.transit.NativeTransitVoiceCueKind
import app.roadstr.feature.voice.NativeVoiceCatalog
import app.roadstr.feature.voice.NativeVoiceGender
import app.roadstr.feature.settings.NativeSettingsVoiceGender
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.core.journey.JourneyController
import app.roadstr.core.journey.JourneyLocationSample
import app.roadstr.core.journey.JourneyMode
import app.roadstr.core.journey.JourneyRecordCodec
import app.roadstr.core.journey.JourneyStore
import app.roadstr.feature.route.NativeRouteTransportMode
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    private val innerPlatform: NativeNavigationPlatform = NoNativeNavigationPlatform,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visible = false
    private var roadGuidanceActive = false
    private var transitGuidanceActive = false
    private var platformGuidanceActive = false

    val location = NativeRoadTestLocationController(appContext)
    val voice: NativeRoadTestVoiceGateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestVoiceGateway(appContext)
    }

    private val ui = NativeRoadTestUiPreferences(appContext, names)
    private val journeyPreferences = NativeRoadTestProtectedPreferences(
        context = appContext,
        preferencesName = names.prefs("journey"),
        keyAlias = names.alias("journey"),
        maxPlaintextBytes = 3 * 1024 * 1024,
    )
    private val journeyStore = object : JourneyStore {
        override fun load() = JourneyRecordCodec.decode(journeyPreferences.read("record"))

        override fun save(record: app.roadstr.core.journey.JourneyRecord) {
            check(journeyPreferences.write("record", JourneyRecordCodec.encode(record)))
        }

        override fun clear() {
            journeyPreferences.remove("record")
        }
    }
    private val journeyDeviceId: String = journeyPreferences.read("device_id")
        ?.takeIf { it.matches(Regex("^[A-Za-z0-9._:-]{8,96}$")) }
        ?: UUID.randomUUID().toString().also { id ->
            check(journeyPreferences.write("device_id", id))
        }

    val journey = JourneyController(
        initialRecord = runCatching(journeyStore::load).getOrDefault(app.roadstr.core.journey.JourneyRecord()),
        deviceId = journeyDeviceId,
        enabled = ui.load().journeyEnabled,
        persist = journeyStore::save,
    )
    private val routingKey = NativeRoadTestProtectedPreferences(
        context = appContext,
        preferencesName = names.prefs("routing"),
        keyAlias = names.alias("routing"),
    )
    private val activeTransitPreferences = NativeRoadTestProtectedPreferences(
        context = appContext,
        preferencesName = names.prefs("active_transit"),
        keyAlias = names.alias("active-transit"),
        maxPlaintextBytes = app.roadstr.feature.savedtransit.NativeSavedTransitProtocol.MAX_STORED_BYTES * 2,
    )
    private val activeTransitStore = NativeActiveTransitStore(
        readEncryptedValue = { activeTransitPreferences.read("journey") },
        writeEncryptedValue = { activeTransitPreferences.write("journey", it) },
        removeEncryptedValue = { activeTransitPreferences.remove("journey") },
    )

    // A gateway of its own, only for what a trip asks for while nobody is looking: a new route and the
    // speed limit. Search and the rest stay with the Activity's gateway.
    private val gateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestJourneyGateway(appContext, routingConfiguration = ::routingConfiguration, names = names)
    }

    private val roadPlatform = object : NativeNavigationPlatform {
        override fun guidanceStarted() {
            roadGuidanceActive = true
            syncPlatformGuidance()
        }

        override fun guidanceStopped() {
            roadGuidanceActive = false
            syncPlatformGuidance()
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
        platform = roadPlatform,
        scope = scope,
        elapsedRealtimeMillis = SystemClock::elapsedRealtime,
    )

    val transitSession = NativeTransitNavigationSession()

    val navigating: Boolean get() = host.navigating || transitSession.state.value.active

    init {
        runCatching { activeTransitStore.load() }.getOrNull()?.let { active ->
            if (transitSession.restore(active.checkpoint, active.itinerary) &&
                transitSession.state.value.stage == NativeTransitNavigationStage.Suspended
            ) {
                transitSession.resume()
            }
        }
        scope.launch {
            location.state.collect { snapshot ->
                snapshot.fix?.let { fix ->
                    journey.submit(
                        JourneyLocationSample(
                            latitude = fix.point.latitude,
                            longitude = fix.point.longitude,
                            elapsedRealtimeMillis = fix.receivedAtElapsedRealtimeMillis,
                            accuracyMeters = fix.accuracyMeters,
                            reportedSpeedMetersPerSecond = fix.speedMetersPerSecond,
                        ),
                    )
                }
                snapshot.fix?.point?.let { point ->
                    transitSession.submitLocation(
                        TransitResponsePoint(point.latitude, point.longitude),
                        java.time.Instant.now(),
                    )
                }
            }
        }
        scope.launch {
            host.session.state.map { it.active to it.mode }.distinctUntilChanged().collect { (active, mode) ->
                if (active) {
                    journey.start(mode.toJourneyMode())
                } else if (journey.state.value.activeSession?.mode != JourneyMode.TRANSIT) {
                    journey.complete()
                }
            }
        }
        scope.launch {
            transitSession.state.map { it.active }.distinctUntilChanged().collect { active ->
                transitGuidanceActive = active
                syncPlatformGuidance()
                if (active) {
                    journey.start(JourneyMode.TRANSIT)
                } else if (journey.state.value.activeSession?.mode == JourneyMode.TRANSIT) {
                    journey.complete()
                }
            }
        }
        scope.launch {
            transitSession.state.distinctUntilChangedBy {
                listOf(it.active, it.stage, it.line, it.nextStop, it.alightingStop, it.platform)
            }.collect { snapshot ->
                if (!snapshot.active) return@collect
                val title = snapshot.line?.takeIf(String::isNotBlank)
                    ?: appContext.getString(app.roadstr.R.string.native_transport_mode_transit)
                val detail = listOfNotNull(snapshot.nextStop, snapshot.alightingStop, snapshot.platform)
                    .distinct().joinToString(" · ").ifBlank {
                        transitStageText(snapshot.stage)
                    }
                innerPlatform.showInstruction(title.take(200), detail.take(200))
            }
        }
        scope.launch(Dispatchers.IO) {
            transitSession.state.collect { snapshot ->
                val persisted = if (snapshot.active) {
                    val checkpoint = transitSession.checkpoint()
                    val itinerary = transitSession.itinerarySnapshot()
                    if (checkpoint != null && itinerary != null) NativeActiveTransitJourney(itinerary, checkpoint) else null
                } else null
                activeTransitStore.save(persisted)
            }
        }
        scope.launch {
            transitSession.state.map { it.voiceCue }.filterNotNull()
                .distinctUntilChangedBy { it.sequence }.collect { cue ->
                    val stored = ui.load()
                    voice.setMuted(!stored.voiceEnabled)
                    if (!stored.voiceEnabled) return@collect
                    val language = (stored.languageCode ?: Locale.getDefault().language).trim().ifBlank { "en" }
                    voice.configure(
                        languageCode = language,
                        gender = when (stored.voiceGender) {
                            NativeSettingsVoiceGender.Male -> NativeVoiceGender.Male
                            NativeSettingsVoiceGender.Female -> NativeVoiceGender.Female
                        },
                        speed = NativeVoiceCatalog.speedForStage(stored.voiceSpeedStage),
                        volume = stored.voiceVolume,
                    )
                    when (cue.kind) {
                        NativeTransitVoiceCueKind.Arrived -> voice.announceArrival()
                        else -> voice.announceManeuver(
                            instruction = transitVoiceInstruction(cue.kind, cue.line, cue.stopName),
                            distanceMeters = 0,
                            nowMillis = SystemClock.elapsedRealtime(),
                            imperial = settings().imperialUnits,
                        )
                    }
                }
        }
        scope.launch {
            while (true) {
                delay(30_000L)
                if (transitSession.state.value.active) transitSession.tick(java.time.Instant.now())
            }
        }
    }

    fun onActivityStart() {
        visible = true
        location.onHostStart()
    }

    fun onActivityStop() {
        visible = false
        if (!navigating) voice.stop()
        location.onHostStop()
    }

    fun setJourneyEnabled(enabled: Boolean) {
        journey.setEnabled(enabled)
        if (!enabled || journey.state.value.activeSession != null) return
        val road = host.session.state.value
        when {
            road.active -> journey.start(road.mode.toJourneyMode())
            transitSession.state.value.active -> journey.start(JourneyMode.TRANSIT)
        }
    }

    /** Ends everything; the caller decides whether a trip may still be running. */
    fun release() {
        host.close()
        location.close()
        voice.close()
        scope.cancel()
    }

    private fun syncPlatformGuidance() {
        val active = roadGuidanceActive || transitGuidanceActive
        if (active == platformGuidanceActive) return
        platformGuidanceActive = active
        location.holdForNavigation(active)
        if (active) {
            innerPlatform.guidanceStarted()
        } else {
            innerPlatform.clearInstruction()
            innerPlatform.guidanceStopped()
            if (!visible) voice.stop()
        }
    }

    private fun transitVoiceInstruction(
        kind: NativeTransitVoiceCueKind,
        line: String?,
        stopName: String?,
    ): String = when (kind) {
        NativeTransitVoiceCueKind.ApproachStop -> appContext.getString(app.roadstr.R.string.native_transit_voice_approach_stop)
        NativeTransitVoiceCueKind.PrepareToBoard -> appContext.getString(app.roadstr.R.string.native_transit_voice_prepare_board)
        NativeTransitVoiceCueKind.BoardLine -> appContext.getString(app.roadstr.R.string.native_transit_voice_take_line, line.orEmpty())
        NativeTransitVoiceCueKind.PrepareToAlight -> appContext.getString(app.roadstr.R.string.native_transit_voice_alight, stopName.orEmpty())
        NativeTransitVoiceCueKind.PrepareConnection -> appContext.getString(app.roadstr.R.string.native_transit_voice_connection)
        NativeTransitVoiceCueKind.ContinueOnFoot -> appContext.getString(app.roadstr.R.string.native_transit_voice_continue_walk)
        NativeTransitVoiceCueKind.Arrived -> appContext.getString(app.roadstr.R.string.native_transit_voice_arrived)
    }

    private fun transitStageText(stage: NativeTransitNavigationStage): String = appContext.getString(
        when (stage) {
            NativeTransitNavigationStage.ApproachStop -> app.roadstr.R.string.native_transit_nav_approach_stop
            NativeTransitNavigationStage.WaitingToBoard -> app.roadstr.R.string.native_transit_nav_waiting_to_board
            NativeTransitNavigationStage.OnBoard -> app.roadstr.R.string.native_transit_nav_on_board
            NativeTransitNavigationStage.ApproachingAlightingStop -> app.roadstr.R.string.native_transit_nav_approaching_alight
            NativeTransitNavigationStage.TransferWalk -> app.roadstr.R.string.native_transit_nav_transfer_walk
            NativeTransitNavigationStage.WaitingForConnection -> app.roadstr.R.string.native_transit_nav_waiting_connection
            NativeTransitNavigationStage.FinalWalk -> app.roadstr.R.string.native_transit_nav_final_walk
            NativeTransitNavigationStage.Arrived -> app.roadstr.R.string.native_transit_nav_arrived
            NativeTransitNavigationStage.Suspended -> app.roadstr.R.string.native_transit_nav_suspended
            NativeTransitNavigationStage.DataUnavailable -> app.roadstr.R.string.native_transit_nav_data_unavailable
            NativeTransitNavigationStage.MissedTrip -> app.roadstr.R.string.native_transit_nav_missed
            NativeTransitNavigationStage.Ended -> app.roadstr.R.string.native_transit_nav_end
        },
    )

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

private fun NativeRouteTransportMode?.toJourneyMode(): JourneyMode = when (this) {
    NativeRouteTransportMode.Driving -> JourneyMode.CAR
    NativeRouteTransportMode.Cycling -> JourneyMode.BICYCLE
    NativeRouteTransportMode.Walking -> JourneyMode.WALKING
    NativeRouteTransportMode.Transit -> JourneyMode.TRANSIT
    null -> JourneyMode.OTHER
}
