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

    /**
     * The stretch of a route still ahead, as a polyline of at most [MAX_CORRIDOR_VERTICES] points and
     * a buffer on each side. The [center] is the point half way along it.
     */
    data class Corridor(val points: List<GeoPoint>, val bufferMeters: Int) : SearchArea {
        init {
            require(points.size in 2..MAX_CORRIDOR_VERTICES) { "A corridor has 2 to $MAX_CORRIDOR_VERTICES points" }
            require(bufferMeters in MIN_BUFFER_METERS..MAX_BUFFER_METERS) { "Corridor buffer out of range" }
        }

        val lengthMeters: Double get() = RouteCorridor.lengthMeters(points)

        override val center: GeoPoint get() = RouteCorridor.pointAlong(points, lengthMeters / 2)

        // The route is where the user is going.
        override fun toString(): String = "Corridor(points=${points.size})"
    }

    companion object {
        const val MAX_CORRIDOR_VERTICES = 24
        const val MIN_BUFFER_METERS = 100
        const val MAX_BUFFER_METERS = 5_000
        const val MAX_RADIUS_METERS = 50_000
        const val MAX_RELATION_ID = 3_599_999_999L
    }
}
