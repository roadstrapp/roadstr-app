package app.roadstr.feature.map

import kotlin.math.roundToInt

/** Closed native counterpart of Flutter's worldwide [TransitMode] catalogue. */
enum class NativeTransitMode(
    val wireName: String,
    val isTransit: Boolean,
) {
    Walk("WALK", false),
    Bike("BIKE", false),
    Car("CAR", false),
    Tram("TRAM", true),
    Subway("SUBWAY", true),
    Metro("METRO", true),
    Suburban("SUBURBAN", true),
    RegionalRail("REGIONAL_RAIL", true),
    RegionalFastRail("REGIONAL_FAST_RAIL", true),
    LongDistance("LONG_DISTANCE", true),
    HighspeedRail("HIGHSPEED_RAIL", true),
    NightRail("NIGHT_RAIL", true),
    Rail("RAIL", true),
    Bus("BUS", true),
    Coach("COACH", true),
    Ferry("FERRY", true),
    Airplane("AIRPLANE", true),
    Funicular("FUNICULAR", true),
    AerialLift("AERIAL_LIFT", true),
    OnDemand("ODM", true),
    Other("OTHER", true),
    ;

    companion object {
        /** Total parser: future provider modes retain a drawable fallback. */
        fun fromWire(value: String?): NativeTransitMode {
            val normalized = value?.trim()?.uppercase() ?: return Other
            return entries.firstOrNull { it.wireName == normalized } ?: Other
        }
    }
}

data class NativeTransitLeg(
    val mode: NativeTransitMode,
    val points: List<NativeMapPoint>,
    val routeColorArgb: Long? = null,
)

data class NativeTransitItinerary(val legs: List<NativeTransitLeg>)

/** Selected-itinerary value consumed by the private MapLibre renderer. */
data class NativeTransitOverlaySnapshot(
    val revision: Long,
    val selectedItineraryIndex: Int?,
    val itineraryCount: Int,
    val legs: List<NativeTransitLeg>,
    val accentArgb: Long,
    val textSecondaryArgb: Long,
) {
    companion object {
        fun empty(accentArgb: Long, textSecondaryArgb: Long) = NativeTransitOverlaySnapshot(
            revision = NativeTransitOverlaySession.NO_TRANSIT_REVISION,
            selectedItineraryIndex = null,
            itineraryCount = 0,
            legs = emptyList(),
            accentArgb = accentArgb,
            textSecondaryArgb = textSecondaryArgb,
        )
    }
}

data class NativeTransitOverlayPayload(
    val geoJson: String,
    val featureCount: Int,
    val pointCount: Int,
)

/** Pure bounded GeoJSON compiler for individually styled public-transport legs. */
object NativeTransitOverlayCompiler {
    const val MAX_ITINERARIES = 8
    const val MAX_LEGS_PER_ITINERARY = 128
    const val MAX_TRANSIT_POINTS = 250_000
    private const val EMPTY_FEATURE_COLLECTION =
        "{\"type\":\"FeatureCollection\",\"features\":[]}"

    fun compile(snapshot: NativeTransitOverlaySnapshot): NativeTransitOverlayPayload {
        require(snapshot.revision >= NativeTransitOverlaySession.NO_TRANSIT_REVISION) {
            "Transit revision is invalid"
        }
        require(snapshot.itineraryCount in 0..MAX_ITINERARIES) {
            "Transit itinerary count is outside the bounded range"
        }
        if (snapshot.itineraryCount == 0) {
            require(snapshot.selectedItineraryIndex == null && snapshot.legs.isEmpty()) {
                "Empty transit metadata must not expose selected geometry"
            }
        } else {
            require(snapshot.revision >= 0) { "Visible transit geometry requires a revision" }
            require(snapshot.selectedItineraryIndex in 0 until snapshot.itineraryCount) {
                "Selected transit itinerary is outside the alternatives"
            }
            require(snapshot.legs.isNotEmpty()) { "Selected transit itinerary must contain legs" }
        }
        require(snapshot.legs.size <= MAX_LEGS_PER_ITINERARY) {
            "Transit itinerary has too many legs"
        }
        requireArgb(snapshot.accentArgb, "Transit accent")
        requireArgb(snapshot.textSecondaryArgb, "Transit secondary text color")

        var pointCount = 0L
        snapshot.legs.forEach { leg ->
            leg.routeColorArgb?.let { requireArgb(it, "Transit route color") }
            pointCount += leg.points.size.toLong()
            require(pointCount <= MAX_TRANSIT_POINTS.toLong()) {
                "Transit overlay has too many points"
            }
            leg.points.forEach(::requireValidPoint)
        }

        val drawable = snapshot.legs.filter { it.points.size >= 2 }
        if (drawable.isEmpty()) {
            return NativeTransitOverlayPayload(EMPTY_FEATURE_COLLECTION, 0, pointCount.toInt())
        }
        val geoJson = buildString {
            append("{\"type\":\"FeatureCollection\",\"features\":[")
            drawable.forEachIndexed { index, leg ->
                if (index > 0) append(',')
                append("{\"type\":\"Feature\",\"properties\":{\"color\":\"")
                append(colorCss(colorFor(leg, snapshot), alphaMultiplier(leg)))
                append("\",\"transit\":")
                append(leg.mode.isTransit)
                append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
                appendCoordinates(leg.points)
                append("]}}")
            }
            append("]}")
        }
        return NativeTransitOverlayPayload(
            geoJson = geoJson,
            featureCount = drawable.size,
            pointCount = pointCount.toInt(),
        )
    }

    private fun colorFor(
        leg: NativeTransitLeg,
        snapshot: NativeTransitOverlaySnapshot,
    ): Long = if (leg.mode.isTransit) {
        leg.routeColorArgb ?: snapshot.accentArgb
    } else {
        snapshot.textSecondaryArgb
    }

    private fun alphaMultiplier(leg: NativeTransitLeg): Double =
        if (leg.mode.isTransit) 1.0 else NativeMapTransitRenderer.STREET_ALPHA

    private fun colorCss(argb: Long, alphaMultiplier: Double): String {
        val alpha = (((argb ushr 24) and 0xFF) / 255.0 * alphaMultiplier)
            .coerceIn(0.0, 1.0)
        return "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF}," +
            canonicalAlpha(alpha) + ")"
    }

    private fun canonicalAlpha(alpha: Double): String = when {
        alpha == 0.0 -> "0"
        alpha == 1.0 -> "1"
        else -> ((alpha * 1_000_000).roundToInt() / 1_000_000.0).toString()
    }

    private fun StringBuilder.appendCoordinates(points: List<NativeMapPoint>) {
        points.forEachIndexed { index, point ->
            if (index > 0) append(',')
            append('[')
            append(canonicalNumber(point.longitude))
            append(',')
            append(canonicalNumber(point.latitude))
            append(']')
        }
    }

    private fun canonicalNumber(value: Double): String = if (value == 0.0) "0" else value.toString()

    private fun requireValidPoint(point: NativeMapPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Transit latitude is outside the WGS84 range"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Transit longitude is outside the WGS84 range"
        }
    }

    internal fun requireArgb(value: Long, label: String) {
        require(value in 0L..0xFFFF_FFFFL) { "$label must be a 32-bit ARGB value" }
    }
}

/** Flutter MapLibre uses physical-pixel correction for transit strokes too. */
object NativeTransitLayerMetrics {
    const val TRANSIT_LOGICAL_WIDTH = 7.0
    const val STREET_LOGICAL_WIDTH = 4.0

    fun widthPixels(logicalWidth: Double, displayDensity: Float): Float =
        NativeRouteLayerMetrics.widthPixels(logicalWidth, displayDensity)
}
