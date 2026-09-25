package app.roadstr.core.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class CameraFollowState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val rotationDegrees: Double,
)

data class CameraCenter(val latitude: Double, val longitude: Double)

/** Rendering-engine-independent follow camera policy. */
object CameraFollowEasing {
    const val TAU_MILLIS = 350.0
    const val MAX_TURN_DEGREES_PER_SECOND = 90.0

    fun step(from: CameraFollowState, target: CameraFollowState, deltaMillis: Int): CameraFollowState? {
        val interpolation = 1 - exp(-deltaMillis / TAU_MILLIS)
        val rotationDelta = signedRotationDelta(target.rotationDegrees, from.rotationDegrees)
        val maximumStep = MAX_TURN_DEGREES_PER_SECOND * deltaMillis / 1000.0
        val rotationStep = (rotationDelta * interpolation).coerceIn(-maximumStep, maximumStep)
        val result = CameraFollowState(
            latitude = from.latitude + (target.latitude - from.latitude) * interpolation,
            longitude = from.longitude + (target.longitude - from.longitude) * interpolation,
            zoom = (from.zoom + (target.zoom - from.zoom) * interpolation).coerceIn(1.0, 22.0),
            rotationDegrees = from.rotationDegrees + rotationStep,
        )
        if (
            !result.latitude.isFinite() || result.latitude !in -90.0..90.0 ||
            !result.longitude.isFinite() || result.longitude !in -180.0..180.0 ||
            !result.zoom.isFinite() || !result.rotationDegrees.isFinite()
        ) {
            return null
        }
        return result
    }

    fun hasCaughtUp(current: CameraFollowState, target: CameraFollowState): Boolean {
        if (abs(signedRotationDelta(target.rotationDegrees, current.rotationDegrees)) >= 0.05) return false
        if (abs(target.zoom - current.zoom) >= 0.005) return false
        val latitudeMeters = (target.latitude - current.latitude) * 111_320
        val longitudeMeters = (target.longitude - current.longitude) *
            111_320 * cos(current.latitude * PI / 180)
        return latitudeMeters * latitudeMeters + longitudeMeters * longitudeMeters < 0.25
    }

    fun navigationCameraCenter(
        latitude: Double,
        longitude: Double,
        headingDegrees: Double,
        zoom: Double,
        screenHeightPixels: Double = 800.0,
        pitchDegrees: Double = 0.0,
    ): CameraCenter {
        val metresPerPixel = 40_075_016.0 / (256.0 * 2.0.pow(zoom))
        val pitchCompensation = 1.0 / max(cos(pitchDegrees * PI / 180), 0.5)
        return shiftByMeters(
            latitude,
            longitude,
            headingDegrees,
            0.18 * screenHeightPixels * metresPerPixel * pitchCompensation,
        )
    }

    fun shiftByMeters(
        latitude: Double,
        longitude: Double,
        headingDegrees: Double,
        shiftMeters: Double,
    ): CameraCenter {
        val radians = headingDegrees * PI / 180
        val deltaLatitude = cos(radians) * shiftMeters / 111_320.0
        val deltaLongitude = sin(radians) * shiftMeters / 111_320.0 /
            max(cos(latitude * PI / 180), 0.001)
        return CameraCenter(
            latitude = (latitude + deltaLatitude).coerceIn(-89.9, 89.9),
            longitude = longitude + deltaLongitude,
        )
    }

    internal fun signedRotationDelta(target: Double, from: Double): Double {
        var delta = (target - from) % 360.0
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        return delta
    }
}

class CameraFrameGate(
    private val minimumChangeDp: Double = 0.6,
    private val maximumGapMillis: Int = 150,
    private val edgeRadiusDp: Double = 400.0,
) {
    private var sent: CameraFollowState? = null
    private var sentAtMillis: Int = 0

    fun shouldSend(next: CameraFollowState, nowMillis: Int): Boolean {
        val previous = sent ?: return true
        val change = changeDp(previous, next)
        if (change >= minimumChangeDp) return true
        return change > NEGLIGIBLE_DP && nowMillis - sentAtMillis >= maximumGapMillis
    }

    fun hasVisibleChange(next: CameraFollowState): Boolean =
        sent?.let { changeDp(it, next) > NEGLIGIBLE_DP } ?: true

    fun markSent(state: CameraFollowState, nowMillis: Int) {
        sent = state
        sentAtMillis = nowMillis
    }

    fun reset() {
        sent = null
    }

    fun changeDp(first: CameraFollowState, second: CameraFollowState): Double {
        val latitudeMeters = (second.latitude - first.latitude) * 111_320
        val longitudeMeters = (second.longitude - first.longitude) *
            111_320 * cos(first.latitude * PI / 180)
        val meters = sqrt(latitudeMeters * latitudeMeters + longitudeMeters * longitudeMeters)
        val metersPerDp = 78_271.51696 * abs(cos(second.latitude * PI / 180)) /
            2.0.pow(second.zoom)
        val travel = if (metersPerDp > 0) meters / metersPerDp else 0.0
        val turn = abs(
            CameraFollowEasing.signedRotationDelta(
                second.rotationDegrees,
                first.rotationDegrees,
            ),
        ) * PI / 180 * edgeRadiusDp
        val zoom = (2.0.pow(abs(second.zoom - first.zoom)) - 1) * edgeRadiusDp
        return travel + turn + zoom
    }

    private companion object {
        const val NEGLIGIBLE_DP = 0.05
    }
}
