package app.roadstr.core.geo

/** A latitude/longitude point in WGS84 degrees. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class SegmentProjection(
    val distanceMeters: Double,
    val t: Double,
)
