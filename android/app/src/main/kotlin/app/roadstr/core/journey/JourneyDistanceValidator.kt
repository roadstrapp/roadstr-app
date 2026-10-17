package app.roadstr.core.journey

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class JourneyLocationSample(
    val latitude: Double,
    val longitude: Double,
    val elapsedRealtimeMillis: Long,
    val accuracyMeters: Double,
    val reportedSpeedMetersPerSecond: Double,
)

enum class JourneyFixDecision {
    ANCHOR,
    ACCEPTED,
    DUPLICATE,
    OUT_OF_ORDER,
    POOR_ACCURACY,
    TELEPORT,
    STATIONARY,
    SIGNAL_GAP,
    INVALID,
}

data class JourneyDistanceUpdate(
    val decision: JourneyFixDecision,
    val validatedDeltaMeters: Long = 0L,
    val uncertainDeltaMeters: Long = 0L,
)

/**
 * Counts only defensible GPS displacement. Estimated/dead-reckoned map points
 * never enter this class. Every mode has its own physical limits.
 */
class JourneyDistanceValidator(private val mode: JourneyMode) {
    private var anchor: JourneyLocationSample? = null
    private var validatedRemainderMeters = 0.0
    private var uncertainRemainderMeters = 0.0

    fun resetAnchor() {
        anchor = null
        validatedRemainderMeters = 0.0
        uncertainRemainderMeters = 0.0
    }

    fun accept(sample: JourneyLocationSample): JourneyDistanceUpdate {
        if (!valid(sample)) return JourneyDistanceUpdate(JourneyFixDecision.INVALID)
        val previous = anchor
        if (previous == null) {
            if (sample.accuracyMeters > limits.maxAccuracyMeters) {
                return JourneyDistanceUpdate(JourneyFixDecision.POOR_ACCURACY)
            }
            anchor = sample
            return JourneyDistanceUpdate(JourneyFixDecision.ANCHOR)
        }
        val elapsedMillis = sample.elapsedRealtimeMillis - previous.elapsedRealtimeMillis
        if (elapsedMillis < 0L) return JourneyDistanceUpdate(JourneyFixDecision.OUT_OF_ORDER)
        if (elapsedMillis == 0L) return JourneyDistanceUpdate(JourneyFixDecision.DUPLICATE)
        val distance = GeoMath.distanceMeters(
            GeoPoint(previous.latitude, previous.longitude),
            GeoPoint(sample.latitude, sample.longitude),
        )
        if (distance < 0.01) {
            anchor = sample
            return JourneyDistanceUpdate(JourneyFixDecision.DUPLICATE)
        }
        if (sample.accuracyMeters > limits.maxAccuracyMeters) {
            return JourneyDistanceUpdate(JourneyFixDecision.POOR_ACCURACY)
        }
        if (elapsedMillis > MAX_CERTIFIED_GAP_MILLIS) {
            anchor = sample
            return JourneyDistanceUpdate(
                JourneyFixDecision.SIGNAL_GAP,
                uncertainDeltaMeters = takeWholeUncertain(distance.takeIf { plausibleGap(it, elapsedMillis) } ?: 0.0),
            )
        }
        val elapsedSeconds = elapsedMillis / 1_000.0
        val derivedSpeed = distance / elapsedSeconds
        val accuracyAllowance = min(MAX_SPEED_ALLOWANCE, (previous.accuracyMeters + sample.accuracyMeters) / elapsedSeconds)
        if (
            derivedSpeed > limits.maxSpeedMetersPerSecond + accuracyAllowance ||
            sample.reportedSpeedMetersPerSecond > limits.maxReportedSpeedMetersPerSecond
        ) {
            // Reset after a jump so one poisoned anchor cannot reject every later fix.
            anchor = sample
            return JourneyDistanceUpdate(JourneyFixDecision.TELEPORT)
        }
        val jitterFloor = max(
            limits.minimumMovementMeters,
            min(MAX_JITTER_FLOOR_METERS, (previous.accuracyMeters + sample.accuracyMeters) * ACCURACY_JITTER_FACTOR),
        )
        anchor = sample
        if (distance <= jitterFloor) return JourneyDistanceUpdate(JourneyFixDecision.STATIONARY)
        return JourneyDistanceUpdate(
            JourneyFixDecision.ACCEPTED,
            validatedDeltaMeters = takeWholeValidated(distance),
        )
    }

    private fun plausibleGap(distance: Double, elapsedMillis: Long): Boolean =
        distance / (elapsedMillis / 1_000.0) <= limits.maxSpeedMetersPerSecond

    private fun takeWholeValidated(distance: Double): Long {
        val total = validatedRemainderMeters + distance
        val whole = floor(total).toLong()
        validatedRemainderMeters = total - whole
        return whole
    }

    private fun takeWholeUncertain(distance: Double): Long {
        val total = uncertainRemainderMeters + distance
        val whole = floor(total).toLong()
        uncertainRemainderMeters = total - whole
        return whole
    }

    private fun valid(sample: JourneyLocationSample): Boolean =
        sample.latitude.isFinite() && sample.latitude in -90.0..90.0 &&
            sample.longitude.isFinite() && sample.longitude in -180.0..180.0 &&
            sample.elapsedRealtimeMillis >= 0L &&
            sample.accuracyMeters.isFinite() && sample.accuracyMeters >= 0.0 &&
            sample.reportedSpeedMetersPerSecond.isFinite() && sample.reportedSpeedMetersPerSecond >= 0.0

    private val limits: Limits = when (mode) {
        JourneyMode.CAR -> Limits(50.0, 75.0, 85.0, 4.0)
        JourneyMode.MOTORCYCLE -> Limits(50.0, 90.0, 100.0, 4.0)
        JourneyMode.BICYCLE -> Limits(35.0, 25.0, 30.0, 2.5)
        JourneyMode.WALKING -> Limits(30.0, 4.5, 6.0, 2.0)
        JourneyMode.TRANSIT -> Limits(65.0, 100.0, 115.0, 5.0)
        JourneyMode.OTHER -> Limits(50.0, 60.0, 70.0, 4.0)
    }

    private data class Limits(
        val maxAccuracyMeters: Double,
        val maxSpeedMetersPerSecond: Double,
        val maxReportedSpeedMetersPerSecond: Double,
        val minimumMovementMeters: Double,
    )

    companion object {
        const val MAX_CERTIFIED_GAP_MILLIS = 120_000L
        private const val ACCURACY_JITTER_FACTOR = 0.35
        private const val MAX_JITTER_FLOOR_METERS = 15.0
        private const val MAX_SPEED_ALLOWANCE = 8.0
    }
}

