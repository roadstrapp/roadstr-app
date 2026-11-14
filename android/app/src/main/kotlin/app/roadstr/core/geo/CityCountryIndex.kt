package app.roadstr.core.geo

import kotlin.math.cos

/**
 * Which country (and first-level division) a position is in, taken from the nearest city of the
 * bundled GeoNames list (`offline_catalog/geonames-cities15000.tsv`: cities over 15 000 people).
 *
 * It needs no network and no polygons. Near a border the nearest big city can lie across it, so the
 * answer is a good guess, not a boundary: use it for things where a neighbour's value is an
 * acceptable miss, never for anything legal.
 */
class CityCountryIndex private constructor(
    private val latitudes: DoubleArray,
    private val longitudes: DoubleArray,
    private val countries: Array<String>,
    private val admin1Codes: Array<String>,
) {
    data class Place(val countryCode: String, val admin1Code: String)

    val size: Int get() = latitudes.size

    /** The nearest city's country, or null when no city lies within [maxMeters]. */
    fun nearest(latitude: Double, longitude: Double, maxMeters: Double = MAX_DISTANCE_METERS): Place? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        val cosLatitude = cos(Math.toRadians(latitude))
        var best = -1
        var bestSquared = Double.POSITIVE_INFINITY
        for (index in latitudes.indices) {
            val dy = (latitudes[index] - latitude) * GeoMath.metresPerDegree
            var dLongitude = kotlin.math.abs(longitudes[index] - longitude)
            if (dLongitude > 180.0) dLongitude = 360.0 - dLongitude
            val dx = dLongitude * GeoMath.metresPerDegree * cosLatitude
            val squared = dx * dx + dy * dy
            if (squared < bestSquared) {
                bestSquared = squared
                best = index
            }
        }
        if (best < 0 || bestSquared > maxMeters * maxMeters) return null
        return Place(countries[best], admin1Codes[best])
    }

    companion object {
        const val MAX_DISTANCE_METERS = 400_000.0

        /** Reads the `C` rows of the bundled catalogue: `C id name ascii lat lon country admin1 ...`. */
        fun fromTsv(lines: Sequence<String>): CityCountryIndex {
            val lat = ArrayList<Double>()
            val lon = ArrayList<Double>()
            val country = ArrayList<String>()
            val admin1 = ArrayList<String>()
            for (line in lines) {
                if (!line.startsWith("C\t")) continue
                val values = line.split('\t')
                if (values.size < 8) continue
                val latitude = values[4].toDoubleOrNull() ?: continue
                val longitude = values[5].toDoubleOrNull() ?: continue
                if (values[6].length != 2) continue
                lat += latitude
                lon += longitude
                country += values[6].uppercase()
                admin1 += values[7]
            }
            return CityCountryIndex(
                lat.toDoubleArray(),
                lon.toDoubleArray(),
                country.toTypedArray(),
                admin1.toTypedArray(),
            )
        }
    }
}
