package app.roadstr.feature.navigation

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.feature.home.NativeShellGpsFix
import app.roadstr.feature.home.NativeShellGpsSnapshot
import app.roadstr.feature.home.NativeShellHeadingTracker
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.voice.NativeVoiceGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the host reads from the person's settings, at the moment it needs it. */
data class NativeNavigationSettings(
    val imperialUnits: Boolean,
    val avoidUnpavedRoads: Boolean,
    val languageCode: String,
)

/**
 * What only the platform can do while the screen is off: keep the process alive and the GPS running,
 * and show where the trip is. The host calls it; it never calls back.
 */
interface NativeNavigationPlatform {
    /** A trip started: keep the process and the GPS alive until [guidanceStopped]. */
    fun guidanceStarted()

    /** The trip ended (stopped, arrived, or the host closed). */
    fun guidanceStopped()

    fun showInstruction(instruction: String, distance: String)

    fun clearInstruction()
}

object NoNativeNavigationPlatform : NativeNavigationPlatform {
    override fun guidanceStarted() = Unit

    override fun guidanceStopped() = Unit

    override fun showInstruction(instruction: String, distance: String) = Unit

    override fun clearInstruction() = Unit
}

/**
 * Drives a trip without a screen.
 *
 * The route, the guidance and the active-trip sessions used to be created inside the Compose shell, and
 * their effects (a GPS fix moves the trip along, a voice cue speaks, an off-route request asks for a new
 * route) ran only while the screen was drawn, so a trip stopped as soon as the phone was locked. Here they
 * live in one owner that has no view, collects the GPS feed itself and can outlive the Activity, with the
 * shell only reading the sessions. Everything it needs from outside arrives through the constructor.
 */
class NativeNavigationHost(
    accentArgb: Long,
    private val gps: StateFlow<NativeShellGpsSnapshot>,
    private val voice: NativeVoiceGateway?,
    private val reroute: suspend (NativeNavigationRerouteRequest, NativeNavigationSettings) -> RoutingParsedRoute?,
    private val speedLimit: suspend (SearchResponsePoint) -> Int?,
    private val settings: () -> NativeNavigationSettings,
    private val platform: NativeNavigationPlatform,
    private val scope: CoroutineScope,
    private val elapsedRealtimeMillis: () -> Long,
    /** Waits; the tests replace it so a backoff or a grace period does not take real seconds. */
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : AutoCloseable {
    val routeSession = NativeRouteOverlaySession(accentArgb)
    val hudSession = NativeNavigationHudSession()
    val session = NativeActiveNavigationSession(hudSession, routeSession)

    private val heading = NativeShellHeadingTracker()
    private val backoff = NativeRerouteBackoff()
    private var rerouteJob: Job? = null
    private var speedLimitJob: Job? = null
    private var stopJob: Job? = null
    private var guidanceOn = false
    private var wasActive = false
    private val collectors = mutableListOf<Job>()

    /** Whether a trip is under way; the Activity and the service use it to decide what may be released. */
    val navigating: Boolean get() = session.state.value.active

    init {
        collectors += scope.launch { gps.collect { snapshot -> snapshot.fix?.let(::onFix) } }
        collectors += scope.launch {
            session.state.map { it.active }.distinctUntilChanged().collect(::onActiveChanged)
        }
        collectors += scope.launch {
            session.state.map { it.rerouteRequest }.filterNotNull().distinctUntilChangedBy { it.sequence }
                .collect(::onRerouteRequest)
        }
        collectors += scope.launch {
            session.state.map { it.voiceCue }.filterNotNull().distinctUntilChangedBy { it.sequence }
                .collect(::onVoiceCue)
        }
        collectors += scope.launch {
            session.state.map { it.arrived }.distinctUntilChanged().collect { arrived ->
                if (arrived) voice?.announceArrival()
            }
        }
        collectors += scope.launch {
            hudSession.state.map { hud -> hud.current?.let { it.instruction to it.distanceLabel } }
                .distinctUntilChanged().collect(::onInstruction)
        }
    }

    /** One owner per fix, in the same order the Flutter screen used: the trip sees the previous heading. */
    private fun onFix(fix: NativeShellGpsFix) {
        val navigating = session.state.value.active
        if (navigating) {
            submit(fix)
            refreshSpeedLimit(fix)
        }
        heading.update(
            point = fix.point,
            speedMetersPerSecond = fix.speedMetersPerSecond,
            accuracyMeters = fix.accuracyMeters,
            providerHeadingDegrees = fix.headingDegrees,
            navigating = navigating,
            routeLocalBearingAt = if (navigating) session::routeLocalBearingAt else null,
        )
    }

    private fun submit(fix: NativeShellGpsFix) {
        session.submitFix(
            sequence = fix.sequence,
            point = fix.point,
            speedMetersPerSecond = fix.speedMetersPerSecond,
            altitudeMeters = fix.altitudeMeters,
            accuracyMeters = fix.accuracyMeters,
            headingDegrees = heading.headingDegrees,
        )
    }

    private fun refreshSpeedLimit(fix: NativeShellGpsFix) {
        if (speedLimitJob?.isActive == true) return
        val point = SearchResponsePoint(fix.point.latitude, fix.point.longitude)
        speedLimitJob = scope.launch {
            val limit = runCatching { speedLimit(point) }.getOrNull()
            session.updateExternalSpeedLimit(session.state.value.revision, limit)
        }
    }

    private fun onActiveChanged(active: Boolean) {
        val before = wasActive
        wasActive = active
        if (active) {
            stopJob?.cancel()
            heading.resetReversal()
            backoff.reset()
            if (!guidanceOn) {
                guidanceOn = true
                platform.guidanceStarted()
            }
            // The guidance is published before the trip says it is active, so the first instruction
            // reached the filter too early and is not sent again: send it now.
            hudSession.state.value.current?.let { platform.showInstruction(it.instruction, it.distanceLabel) }
            gps.value.fix?.let(::submit)
            return
        }
        if (!before) return
        speedLimitJob?.cancel()
        speedLimitJob = null
        rerouteJob?.cancel()
        rerouteJob = null
        platform.clearInstruction()
        releaseGuidance(afterArrival = session.state.value.arrived)
    }

    /** After an arrival the phrase is still being spoken, so the process is held a little longer. */
    private fun releaseGuidance(afterArrival: Boolean) {
        stopJob?.cancel()
        stopJob = scope.launch {
            if (afterArrival) wait(ARRIVAL_GRACE_MILLIS)
            if (!session.state.value.active && guidanceOn) {
                guidanceOn = false
                platform.guidanceStopped()
            }
        }
    }

    private fun onRerouteRequest(request: NativeNavigationRerouteRequest) {
        if (rerouteJob?.isActive == true) {
            session.failReroute(request.sequence)
            return
        }
        rerouteJob = scope.launch {
            val route = try {
                reroute(request, settings())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (route == null) {
                // The request stays pending through the wait, so the session raises no new one until it is released.
                wait(backoff.nextDelayMillis())
                session.failReroute(request.sequence)
            } else {
                backoff.reset()
                session.completeReroute(request.sequence, route)
            }
        }
    }

    private fun onVoiceCue(cue: NativeNavigationVoiceCue) {
        voice?.announceManeuver(
            instruction = cue.instruction,
            distanceMeters = cue.distanceMeters,
            nowMillis = elapsedRealtimeMillis(),
            imperial = settings().imperialUnits,
        )
    }

    private fun onInstruction(step: Pair<String, String>?) {
        if (step == null || !session.state.value.active) return
        platform.showInstruction(step.first, step.second)
    }

    override fun close() {
        collectors.forEach(Job::cancel)
        collectors.clear()
        speedLimitJob?.cancel()
        rerouteJob?.cancel()
        stopJob?.cancel()
        if (guidanceOn) {
            guidanceOn = false
            platform.clearInstruction()
            platform.guidanceStopped()
        }
    }

    private companion object {
        const val ARRIVAL_GRACE_MILLIS = 12_000L
    }
}
