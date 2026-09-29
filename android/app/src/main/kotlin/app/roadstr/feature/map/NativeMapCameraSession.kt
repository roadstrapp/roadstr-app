package app.roadstr.feature.map

import app.roadstr.core.map.CameraFollowEasing
import app.roadstr.core.map.CameraFollowState
import app.roadstr.core.map.CameraFrameGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
)

data class NativeMapCameraSessionState(
    val followEnabled: Boolean,
    val headingUp: Boolean,
    val navigating: Boolean,
    val frameActive: Boolean,
    val lastFixSequence: Long,
    val command: NativeMapCameraCommand?,
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
        frameActive = followEnabled
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
        }
        if (settled) frameActive = false
        publish()
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
        val bearing = if (headingUp) fix.headingDegrees else 0.0
        val center = if (navigating && headingUp) {
            measuredForwardShiftMeters?.let { shiftMeters ->
                CameraFollowEasing.shiftByMeters(
                    latitude = predicted.latitude,
                    longitude = predicted.longitude,
                    headingDegrees = bearing,
                    shiftMeters = shiftMeters,
                )
            } ?: CameraFollowEasing.navigationCameraCenter(
                latitude = predicted.latitude,
                longitude = predicted.longitude,
                headingDegrees = bearing,
                zoom = zoom,
                screenHeightPixels = screenHeightPixels,
                pitchDegrees = pitchDegrees,
            )
        } else {
            predicted
        }
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
        const val FOLLOW_FRAME_MILLIS = 33L
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
