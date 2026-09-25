package app.roadstr.core.navigation

/** Stateful trend detector ported from lib/utils/off_route_detector.dart. */
class OffRouteDetector {
    private var closestMeters: Double? = null
    private var samplesAway: Int = 0

    fun reset() {
        closestMeters = null
        samplesAway = 0
    }

    fun sawDeviation(distanceMeters: Double, accuracyMeters: Double = 0.0): Boolean {
        if (!distanceMeters.isFinite()) return false
        if (distanceMeters > HARD_THRESHOLD_METERS) {
            reset()
            return true
        }
        val closest = closestMeters
        if (closest == null || distanceMeters < closest) {
            closestMeters = distanceMeters
            samplesAway = 0
            return false
        }
        if (distanceMeters - closest < AWAY_GROWTH_METERS) {
            samplesAway = 0
            return false
        }
        if (accuracyMeters.isFinite() && distanceMeters < accuracyMeters * ACCURACY_MARGIN) {
            return false
        }
        samplesAway++
        if (distanceMeters < NOISE_FLOOR_METERS || samplesAway < AWAY_SAMPLES) return false
        reset()
        return true
    }

    companion object {
        const val NOISE_FLOOR_METERS = 30.0
        const val HARD_THRESHOLD_METERS = 55.0
        const val AWAY_GROWTH_METERS = 15.0
        const val AWAY_SAMPLES = 4
        const val ACCURACY_MARGIN = 1.5
    }
}
