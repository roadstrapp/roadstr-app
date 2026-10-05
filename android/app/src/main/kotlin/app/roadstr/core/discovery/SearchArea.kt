package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint

/** Where one discovery query looks. */
sealed interface SearchArea {
    val center: GeoPoint

    data class Circle(override val center: GeoPoint, val radiusMeters: Int) : SearchArea {
        init {
            require(radiusMeters in 100..MAX_RADIUS_METERS) { "Search radius out of range" }
        }
    }

    data class Box(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
    ) : SearchArea {
        override val center: GeoPoint = GeoPoint((south + north) / 2, (west + east) / 2)

        init {
            require(south < north && west < east) { "Empty search box" }
            require(south >= -90.0 && north <= 90.0 && west >= -180.0 && east <= 180.0) {
                "Search box outside WGS84"
            }
        }

        val diagonalMeters: Double
            get() = GeoMath.distanceMeters(GeoPoint(south, west), GeoPoint(north, east))
    }

    /** An administrative area Overpass can address by relation id; [fallback] is used if it is empty. */
    data class AdminArea(
        val relationId: Long,
        override val center: GeoPoint,
        val fallback: SearchArea,
    ) : SearchArea {
        init {
            require(relationId in 1..MAX_RELATION_ID) { "Relation id out of range" }
        }
    }

    companion object {
        const val MAX_RADIUS_METERS = 50_000
        const val MAX_RELATION_ID = 3_599_999_999L
    }
}
