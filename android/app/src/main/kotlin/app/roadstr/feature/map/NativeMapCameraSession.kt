package app.roadstr.feature.map

import app.roadstr.core.map.CameraFollowEasing
import app.roadstr.core.map.CameraFollowState
import app.roadstr.core.map.CameraFrameGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DEFAULT_ROUTE_FIT_PADDING_PX = 96

enum class NativeMapCameraMotion {
    Move,
    Ease,
}

data class NativeMapCameraCommand(
    val sequence: Long,
    val center: NativeMapPoint,
    val zoom: Double,
    val bearingDegrees: Double,
    val pitchDegrees: Double,
    val motion: NativeMapCameraMotion,
    val durationMillis: Int,
    val bounds: NativeMapCameraBounds? = null,
    val paddingLeftPixels: Double = 0.0,
    val paddingTopPixels: Double = 0.0,
    val paddingRightPixels: Double = 0.0,
    val paddingBottomPixels: Double = 0.0,
)

data class NativeMapCameraBounds(
    val southWest: NativeMapPoint,
    val northEast: NativeMapPoint,
    val paddingLeftPixels: Int = DEFAULT_ROUTE_FIT_PADDING_PX,
    val paddingTopPixels: Int = DEFAULT_ROUTE_FIT_PADDING_PX,
    val paddingRightPixels: Int = DEFAULT_ROUTE_FIT_PADDING_PX,
    val paddingBottomPixels: Int = DEFAULT_ROUTE_FIT_PADDING_PX,
)

data class NativeMapCameraSessionState(
    val followEnabled: Boolean,
    val headingUp: Boolean,
    val navigating: Boolean,
    val frameActive: Boolean,
    val lastFixSequence: Long,
    val command: NativeMapCameraCommand?,
    /**
     * Where the vehicle is drawn: the last fix, advanced along its heading by
     * dead reckoning on every frame that moves the camera. Published together
     * with [command] so the cursor and the map move in the same step; a cursor
     * that waits for the next fix while the camera glides ahead creeps back
     * against the map and then jumps.
     */
    val displayPoint: NativeMapPoint? = null,
    /** Changes with every new [displayPoint], for consumers that need an ordering. */
    val displaySequence: Long = 0L,
)

/**
 * Value-only camera session between future native fixes and MapLibre.
 *
 * The session owns no sensors or permissions. Callers submit already resolved
 * fixes, drive [advanceFrame] at the Flutter-compatible cadence, and consume
 * immutable commands. User gestures stop follow before another frame can move
 * the map back underneath the gesture.
 */
class NativeMapCameraSession {
    private val lock = Any()
    private val frameGate = CameraFrameGate()
    private val _state = MutableStateFlow(initialState())

    private var followEnabled = true
    private var headingUp = true
    private var navigating = false
    private var zoom = DEFAULT_ZOOM
    private var pitchDegrees = FREE_DRIVE_PITCH
    private var screenHeightPixels = DEFAULT_SCREEN_HEIGHT_PIXELS
    private var measuredForwardShiftMeters: Double? = null
    private var fix: CameraFix? = null
    private var current: CameraFollowState? = null
    private var lastFrameMillis: Long? = null
    private var gateClockOriginMillis: Long? = null
    private var commandSequence = 0L
    private var command: NativeMapCameraCommand? = null
    private var frameActive = false
    private var forceNextCommand = false
    private var displayPoint: NativeMapPoint? = null
    private var displaySequence = 0L
    private var compassHeading: Double? = null
    private var navigationTopPaddingPixels: Double? = null

    val state: StateFlow<NativeMapCameraSessionState> = _state.asStateFlow()

    fun configure(
        headingUp: Boolean,
        navigating: Boolean,
        zoom: Double,
        pitchDegrees: Double,
        screenHeightPixels: Double,
        measuredForwardShiftMeters: Double? = null,
    ): Boolean = synchronized(lock) {
        require(zoom.isFinite() && zoom in MIN_ZOOM..MAX_ZOOM) {
            "Camera zoom is outside the supported range"
        }
        require(pitchDegrees.isFinite() && pitchDegrees in MIN_PITCH..MAX_PITCH) {
            "Camera pitch is outside the supported range"
        }
        require(screenHeightPixels.isFinite() && screenHeightPixels > 0.0) {
            "Camera screen height must be finite and positive"
        }
        require(
            measuredForwardShiftMeters == null ||
                measuredForwardShiftMeters.isFinite() && measuredForwardShiftMeters >= 0.0,
        ) { "Camera forward shift must be finite and non-negative" }
        val changed =
            this.headingUp != headingUp ||
                this.navigating != navigating ||
                this.zoom != zoom ||
                this.pitchDegrees != pitchDegrees ||
                this.screenHeightPixels != screenHeightPixels ||
                this.measuredForwardShiftMeters != measuredForwardShiftMeters
        if (!changed) return false
        this.headingUp = headingUp
        this.navigating = navigating
        this.zoom = zoom
        this.pitchDegrees = pitchDegrees
        this.screenHeightPixels = screenHeightPixels
        this.measuredForwardShiftMeters = measuredForwardShiftMeters
        frameActive = followEnabled && fix != null
        forceNextCommand = frameActive
        publish()
        true
    }

    fun submitFix(
        sequence: Long,
        point: NativeMapPoint,
        headingDegrees: Double,
        speedMetersPerSecond: Double,
        receivedAtMillis: Long,
    ): Boolean = synchronized(lock) {
        require(sequence >= 0) { "Camera fix sequence must be non-negative" }
        requireValidPoint(point)
        require(headingDegrees.isFinite()) { "Camera heading must be finite" }
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0) {
            "Camera speed must be finite and non-negative"
        }
        require(receivedAtMillis >= 0) { "Camera fix time must be non-negative" }
        if (sequence <= (fix?.sequence ?: NO_FIX_SEQUENCE)) return false
        fix = CameraFix(
            sequence = sequence,
            point = point,
            headingDegrees = normalizeBearing(headingDegrees),
            speedMetersPerSecond = speedMetersPerSecond,
            receivedAtMillis = receivedAtMillis,
        )
        // A real fix corrects the drawn position at once, whether or not the
        // camera is following.
        displayPoint = point
        displaySequence += 1
        frameActive = followEnabled
        publish()
        true
    }

    /**
     * Where the vehicle sits on screen while navigating heading-up, as the
     * camera's top padding: the camera target appears at the middle of what the
     * padding leaves, so a padding of P puts it at (P + H) / 2 from the top.
     * null falls back to a fixed fraction until the panel has been measured.
     */
    fun setNavigationTopPadding(pixels: Double?): Boolean = synchronized(lock) {
        val validated = pixels?.takeIf { it.isFinite() && it >= 0.0 }
        if (navigationTopPaddingPixels == validated) return false
        navigationTopPaddingPixels = validated
        if (followEnabled && fix != null && navigating) {
            forceNextCommand = true
            frameActive = true
        }
        publish()
        true
    }

    /**
     * While the vehicle stands still outside navigation the heading-up map
     * turns with the phone's compass; null hands the bearing back to the fix.
     */
    fun setCompassHeading(degrees: Double?): Boolean = synchronized(lock) {
        val normalized = degrees?.takeIf { it.isFinite() }?.let(::normalizeBearing)
        if (compassHeading == normalized) return false
        compassHeading = normalized
        if (followEnabled && fix != null) frameActive = true
        publish()
        true
    }

    /** Advances one software-eased follow frame. */
    fun advanceFrame(nowMillis: Long): Boolean = synchronized(lock) {
        require(nowMillis >= 0) { "Camera frame time must be non-negative" }
        val currentFix = fix
        if (!followEnabled || !frameActive || currentFix == null) return false
        val target = targetFor(currentFix, nowMillis)
        val previous = current
        val next = if (previous == null) {
            target
        } else {
            val deltaMillis = ((nowMillis - (lastFrameMillis ?: nowMillis)).coerceIn(1, 100)).toInt()
            CameraFollowEasing.step(previous, target, deltaMillis) ?: return false
        }
        current = next
        lastFrameMillis = nowMillis
        val settled = !navigating && CameraFollowEasing.hasCaughtUp(next, target)
        val gateNow = gateTime(nowMillis)
        val shouldSend = forceNextCommand || frameGate.shouldSend(next, gateNow) ||
            (settled && frameGate.hasVisibleChange(next))
        if (shouldSend) {
            frameGate.markSent(next, gateNow)
            command = next.toCommand(
                motion = NativeMapCameraMotion.Move,
                durationMillis = 0,
            )
            forceNextCommand = false
            // Same step as the camera: the vehicle advances only on a frame
            // that actually moved the map.
            deadReckoned(currentFix, nowMillis)?.let {
                displayPoint = it
                displaySequence += 1
            }
        }
        if (settled) frameActive = false
        // The easing state may advance more often than MapLibre needs a new
        // command. Avoid waking every StateFlow/Compose consumer for frames
        // intentionally rejected by CameraFrameGate.
        if (shouldSend || settled) publish()
        shouldSend
    }

    /** Explicit recenter: re-enables follow and emits one eased jump now. */
    fun recenter(nowMillis: Long): Boolean = synchronized(lock) {
        require(nowMillis >= 0) { "Camera frame time must be non-negative" }
        val currentFix = fix ?: return false
        followEnabled = true
        val target = targetFor(currentFix, nowMillis)
        current = target
        lastFrameMillis = nowMillis
        frameGate.reset()
        frameGate.markSent(target, gateTime(nowMillis))
        command = target.toCommand(
            motion = NativeMapCameraMotion.Ease,
            durationMillis = RECENTER_DURATION_MILLIS,
        )
        forceNextCommand = false
        frameActive = navigating
        publish()
        true
    }

    /**
     * Legacy cursor-centred snap retained for tests and non-HUD callers.
     * Turn-by-turn navigation must use [recenter], whose forward camera target
     * keeps the vehicle clear of the bottom navigation panel.
     */
    fun recenterOnCursor(nowMillis: Long): Boolean = synchronized(lock) {
        require(nowMillis >= 0) { "Camera frame time must be non-negative" }
        val currentFix = fix ?: return false
        followEnabled = true
        val target = CameraFollowState(
            latitude = currentFix.point.latitude,
            longitude = currentFix.point.longitude,
            zoom = zoom,
            rotationDegrees = if (headingUp) currentFix.headingDegrees else 0.0,
        )
        current = target
        lastFrameMillis = nowMillis
        frameGate.reset()
        frameGate.markSent(target, gateTime(nowMillis))
        command = target.toCommand(
            motion = NativeMapCameraMotion.Ease,
            durationMillis = RECENTER_DURATION_MILLIS,
        )
        forceNextCommand = false
        frameActive = navigating
        publish()
        true
    }

    /** Explicit destination focus used by the route planner's “Naviga qui”. */
    fun focus(point: NativeMapPoint, nowMillis: Long, zoom: Double = DESTINATION_ZOOM): Boolean =
        synchronized(lock) {
            require(nowMillis >= 0) { "Camera frame time must be non-negative" }
            requireValidPoint(point)
            require(zoom.isFinite() && zoom in MIN_ZOOM..MAX_ZOOM) {
                "Camera zoom is outside the supported range"
            }
            followEnabled = false
            frameActive = false
            current = null
            lastFrameMillis = null
            frameGate.reset()
            commandSequence += 1
            command = NativeMapCameraCommand(
                sequence = commandSequence,
                center = point,
                zoom = zoom,
                bearingDegrees = 0.0,
                pitchDegrees = FREE_DRIVE_PITCH,
                motion = NativeMapCameraMotion.Ease,
                durationMillis = DESTINATION_FOCUS_DURATION_MILLIS,
            )
            publish()
            true
        }

    /** Fits every visible route vertex after “Calcola percorso”. */
    fun fitRoute(
        points: List<NativeMapPoint>,
        nowMillis: Long,
        bottomInsetPixels: Int = ROUTE_FIT_PADDING_PX,
        topInsetPixels: Int = ROUTE_FIT_PADDING_PX,
    ): Boolean = synchronized(lock) {
        require(nowMillis >= 0) { "Camera frame time must be non-negative" }
        require(bottomInsetPixels >= 0 && topInsetPixels >= 0) {
            "Camera fit insets must be non-negative"
        }
        if (points.isEmpty()) return false
        points.forEach(::requireValidPoint)
        val south = points.minOf { it.latitude }
        val west = points.minOf { it.longitude }
        val north = points.maxOf { it.latitude }
        val east = points.maxOf { it.longitude }
        followEnabled = false
        frameActive = false
        current = null
        lastFrameMillis = null
        frameGate.reset()
        commandSequence += 1
        command = NativeMapCameraCommand(
            sequence = commandSequence,
            center = NativeMapPoint((south + north) / 2.0, (west + east) / 2.0),
            zoom = FIT_FALLBACK_ZOOM,
            bearingDegrees = 0.0,
            pitchDegrees = 0.0,
            motion = NativeMapCameraMotion.Ease,
            durationMillis = ROUTE_FIT_DURATION_MILLIS,
            bounds = NativeMapCameraBounds(
                southWest = NativeMapPoint(south, west),
                northEast = NativeMapPoint(north, east),
                paddingTopPixels = topInsetPixels,
                paddingBottomPixels = bottomInsetPixels,
            ),
        )
        publish()
        true
    }

    /** Called only for MapLibre's API_GESTURE reason. */
    fun onUserGesture(): Boolean = synchronized(lock) {
        if (!followEnabled) return false
        followEnabled = false
        frameActive = false
        forceNextCommand = false
        lastFrameMillis = null
        frameGate.reset()
        publish()
        true
    }

    /** The fix advanced along its heading, or null while it is not moving. */
    private fun deadReckoned(fix: CameraFix, nowMillis: Long): NativeMapPoint? {
        val elapsedMillis = (nowMillis - fix.receivedAtMillis).coerceIn(0, DEAD_RECKONING_CAP_MILLIS)
        if (fix.speedMetersPerSecond <= 0.0 || elapsedMillis <= 0L) return null
        val shifted = CameraFollowEasing.shiftByMeters(
            latitude = fix.point.latitude,
            longitude = fix.point.longitude,
            headingDegrees = fix.headingDegrees,
            shiftMeters = fix.speedMetersPerSecond * elapsedMillis / 1000.0,
        )
        return NativeMapPoint(shifted.latitude, shifted.longitude)
    }

    private fun targetFor(fix: CameraFix, nowMillis: Long): CameraFollowState {
        val elapsedMillis = (nowMillis - fix.receivedAtMillis).coerceIn(0, DEAD_RECKONING_CAP_MILLIS)
        val predicted = if (fix.speedMetersPerSecond > 0.0 && elapsedMillis > 0L) {
            CameraFollowEasing.shiftByMeters(
                latitude = fix.point.latitude,
                longitude = fix.point.longitude,
                headingDegrees = fix.headingDegrees,
                shiftMeters = fix.speedMetersPerSecond * elapsedMillis / 1000.0,
            )
        } else {
            app.roadstr.core.map.CameraCenter(fix.point.latitude, fix.point.longitude)
        }
        val bearing = if (headingUp) (compassHeading ?: fix.headingDegrees) else 0.0
        // MapLibre can place its target inside a padded viewport. Keep the
        // target on the real GPS point and let renderer padding move it below
        // centre, leaving road ahead visible without an error-prone geographic
        // estimate that could project the cursor completely off-screen.
        val center = predicted
        return CameraFollowState(
            latitude = center.latitude,
            longitude = center.longitude,
            zoom = zoom,
            rotationDegrees = bearing,
        )
    }

    private fun CameraFollowState.toCommand(
        motion: NativeMapCameraMotion,
        durationMillis: Int,
    ): NativeMapCameraCommand {
        commandSequence += 1
        return NativeMapCameraCommand(
            sequence = commandSequence,
            center = NativeMapPoint(latitude, longitude),
            zoom = zoom,
            bearingDegrees = normalizeBearing(rotationDegrees),
            pitchDegrees = pitchDegrees,
            motion = motion,
            durationMillis = durationMillis,
            // Only heading-up: a north-up map keeps the vehicle in the middle.
            paddingTopPixels = if (navigating && headingUp) {
                navigationTopPaddingPixels ?: (screenHeightPixels * NAVIGATION_TOP_PADDING_FRACTION)
            } else {
                0.0
            },
        )
    }

    private fun gateTime(nowMillis: Long): Int {
        val origin = gateClockOriginMillis ?: nowMillis.also { gateClockOriginMillis = it }
        return (nowMillis - origin).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun publish() {
        _state.value = NativeMapCameraSessionState(
            followEnabled = followEnabled,
            headingUp = headingUp,
            navigating = navigating,
            frameActive = frameActive,
            lastFixSequence = fix?.sequence ?: NO_FIX_SEQUENCE,
            command = command,
            displayPoint = displayPoint,
            displaySequence = displaySequence,
        )
    }

    private data class CameraFix(
        val sequence: Long,
        val point: NativeMapPoint,
        val headingDegrees: Double,
        val speedMetersPerSecond: Double,
        val receivedAtMillis: Long,
    )

    companion object {
        const val FOLLOW_FRAME_MILLIS = 50L
        const val DEAD_RECKONING_CAP_MILLIS = 3_000L
        const val RECENTER_DURATION_MILLIS = 300
        const val DEFAULT_ZOOM = 17.0
        const val FREE_DRIVE_PITCH = 40.0
        const val NAVIGATION_PITCH = 55.0
        const val DEFAULT_SCREEN_HEIGHT_PIXELS = 800.0
        private const val MIN_ZOOM = 1.0
        private const val MAX_ZOOM = 22.0
        private const val MIN_PITCH = 0.0
        private const val MAX_PITCH = 60.0
        private const val NO_FIX_SEQUENCE = -1L
        private const val DESTINATION_ZOOM = 16.5
        private const val FIT_FALLBACK_ZOOM = 12.0
        private const val DESTINATION_FOCUS_DURATION_MILLIS = 350
        private const val ROUTE_FIT_DURATION_MILLIS = 500
        // Places the GPS target at ~64% of the physical screen height. This
        // matches main's road-ahead framing while keeping the cursor clear of
        // the bottom navigation panel.
        // Fallback before the bottom panel is measured: a 45% top inset puts the
        // vehicle at 72.5% of the screen height, about where the Flutter map
        // keeps it on a phone of this shape.
        private const val NAVIGATION_TOP_PADDING_FRACTION = 0.45
        const val ROUTE_FIT_PADDING_PX = DEFAULT_ROUTE_FIT_PADDING_PX

        private fun initialState() = NativeMapCameraSessionState(
            followEnabled = true,
            headingUp = true,
            navigating = false,
            frameActive = false,
            lastFixSequence = NO_FIX_SEQUENCE,
            command = null,
        )

        private fun requireValidPoint(point: NativeMapPoint) {
            require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
                "Camera latitude is outside the WGS84 range"
            }
            require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
                "Camera longitude is outside the WGS84 range"
            }
        }

        private fun normalizeBearing(value: Double): Double =
            ((value % 360.0) + 360.0) % 360.0
    }
}
