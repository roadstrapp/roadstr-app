package app.roadstr.feature.map

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Shared distance oracle matching latlong2's rounded WGS-84 Vincenty result. */
internal object NativeMapDistance {
    fun roundedVincentyMeters(first: NativeMapPoint, second: NativeMapPoint): Double {
        val flattening = 1 / 298.257223563
        val semiMajor = 6_378_137.0
        val semiMinor = 6_356_752.314245
        val latitude1 = Math.toRadians(first.latitude)
        val latitude2 = Math.toRadians(second.latitude)
        val longitudeDifference = Math.toRadians(second.longitude - first.longitude)
        val reduced1 = atan((1 - flattening) * tan(latitude1))
        val reduced2 = atan((1 - flattening) * tan(latitude2))
        val sine1 = sin(reduced1)
        val cosine1 = cos(reduced1)
        val sine2 = sin(reduced2)
        val cosine2 = cos(reduced2)
        var lambda = longitudeDifference
        var sineSigma: Double
        var cosineSigma: Double
        var sigma: Double
        var cosineSquaredAlpha: Double
        var cosineDoubleSigma: Double
        var iterations = 200
        do {
            val previous = lambda
            val sineLambda = sin(lambda)
            val cosineLambda = cos(lambda)
            sineSigma = sqrt(
                (cosine2 * sineLambda) * (cosine2 * sineLambda) +
                    (cosine1 * sine2 - sine1 * cosine2 * cosineLambda) *
                    (cosine1 * sine2 - sine1 * cosine2 * cosineLambda),
            )
            if (sineSigma == 0.0) return 0.0
            cosineSigma = sine1 * sine2 + cosine1 * cosine2 * cosineLambda
            sigma = atan2(sineSigma, cosineSigma)
            val sineAlpha = cosine1 * cosine2 * sineLambda / sineSigma
            cosineSquaredAlpha = 1 - sineAlpha * sineAlpha
            cosineDoubleSigma = cosineSigma - 2 * sine1 * sine2 / cosineSquaredAlpha
            if (cosineDoubleSigma.isNaN()) cosineDoubleSigma = 0.0
            val coefficient = flattening / 16 * cosineSquaredAlpha *
                (4 + flattening * (4 - 3 * cosineSquaredAlpha))
            lambda = longitudeDifference + (1 - coefficient) * flattening * sineAlpha *
                (sigma + coefficient * sineSigma *
                    (cosineDoubleSigma + coefficient * cosineSigma *
                        (-1 + 2 * cosineDoubleSigma * cosineDoubleSigma)))
            if (abs(lambda - previous) <= 1e-12) break
        } while (--iterations > 0)
        if (iterations == 0) error("Distance calculation failed to converge")
        val uSquared = cosineSquaredAlpha *
            (semiMajor * semiMajor - semiMinor * semiMinor) / (semiMinor * semiMinor)
        val a = 1 + uSquared / 16_384 *
            (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)))
        val b = uSquared / 1024 *
            (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)))
        val deltaSigma = b * sineSigma *
            (cosineDoubleSigma + b / 4 *
                (cosineSigma * (-1 + 2 * cosineDoubleSigma * cosineDoubleSigma) -
                    b / 6 * cosineDoubleSigma *
                    (-3 + 4 * sineSigma * sineSigma) *
                    (-3 + 4 * cosineDoubleSigma * cosineDoubleSigma)))
        return floor(semiMinor * a * (sigma - deltaSigma) + 0.5)
    }
}
