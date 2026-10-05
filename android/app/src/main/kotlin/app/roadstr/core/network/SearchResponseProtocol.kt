package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class SearchResponsePoint(
    val latitude: Double,
    val longitude: Double,
)

data class SearchResult(
    val displayName: String,
    val shortName: String,
    val position: SearchResponsePoint,
    val featureClass: String? = null,
    val type: String? = null,
    val city: String? = null,
    val openingHours: String? = null,
    val distanceM: Double? = null,
    val brand: String? = null,
    /** Native-only: a discovery result brings its own symbol and its localised category line. */
    val emojiOverride: String? = null,
    val categoryLabelOverride: String? = null,
) {
    val emoji: String
        get() = emojiOverride ?: categoryEmoji(featureClass, type)

    val categoryLabel: String
        get() {
            categoryLabelOverride?.let { return it }
            if (featureClass == "highway") {
                return displayName.split(',')
                    .drop(2)
                    .map(String::trimDart)
                    .filter(String::isNotEmpty)
                    .take(2)
                    .joinToString(", ")
            }
            categoryLabel(featureClass, type)?.let { return it }
            val parts = displayName.split(',')
            return if (parts.size > 1) parts[1].trimDart() else ""
        }

    companion object {
        const val MAX_REMOTE_TEXT_CHARS = 300

        fun clampRemoteText(value: Any?, max: Int = MAX_REMOTE_TEXT_CHARS): String? {
            val clean = (value as? String)?.trimDart() ?: return null
            if (clean.isEmpty()) return null
            return if (clean.length <= max) clean else clean.substring(0, max)
        }

        private fun categoryEmoji(featureClass: String?, type: String?): String =
            when (featureClass) {
                "highway" -> "🛣️"
                "place" -> when (type) {
                    "city", "town" -> "🏙️"
                    "village", "hamlet" -> "🏘️"
                    "suburb", "neighbourhood" -> "🏡"
                    else -> "📍"
                }

                "amenity" -> when (type) {
                    "restaurant", "fast_food", "food_court" -> "🍽️"
                    "cafe", "coffee_shop" -> "☕"
                    "bar", "pub", "nightclub" -> "🍺"
                    "hospital", "clinic", "doctors" -> "🏥"
                    "pharmacy" -> "💊"
                    "school", "kindergarten" -> "🏫"
                    "university", "college" -> "🎓"
                    "bank", "atm" -> "🏦"
                    "fuel", "charging_station" -> "⛽"
                    "parking" -> "🅿️"
                    "police" -> "👮"
                    "post_office" -> "📮"
                    "library" -> "📚"
                    "theatre", "cinema" -> "🎭"
                    "place_of_worship" -> "⛪"
                    "marketplace" -> "🛒"
                    "townhall" -> "🏛️"
                    else -> "📍"
                }

                "tourism" -> when (type) {
                    "museum" -> "🏛️"
                    "hotel", "hostel", "motel", "guest_house" -> "🏨"
                    "attraction", "monument", "viewpoint" -> "🗺️"
                    "artwork", "gallery" -> "🎨"
                    "camp_site" -> "⛺"
                    "theme_park", "zoo" -> "🎡"
                    else -> "🗺️"
                }

                "shop" -> when (type) {
                    "supermarket", "convenience" -> "🛒"
                    "bakery" -> "🥖"
                    "clothes", "fashion" -> "👗"
                    "electronics" -> "📱"
                    "books" -> "📚"
                    "florist" -> "💐"
                    else -> "🛍️"
                }

                "office" -> when (type) {
                    "government", "administrative" -> "🏛️"
                    else -> "🏢"
                }

                "building" -> when (type) {
                    "public", "government" -> "🏛️"
                    "hospital" -> "🏥"
                    "school", "university" -> "🎓"
                    else -> "🏗️"
                }

                "natural" -> when (type) {
                    "beach" -> "🏖️"
                    "water", "lake" -> "💧"
                    "peak", "hill" -> "⛰️"
                    "wood", "forest" -> "🌲"
                    else -> "🌿"
                }

                "leisure" -> when (type) {
                    "park", "garden" -> "🌳"
                    "sports_centre", "stadium" -> "🏟️"
                    "swimming_pool" -> "🏊"
                    else -> "🎭"
                }

                "historic" -> "🏛️"
                "railway" -> "🚉"
                "aeroway" -> "✈️"
                "waterway" -> "🌊"
                "landuse" -> "🗺️"
                else -> "📍"
            }

        private fun categoryLabel(featureClass: String?, type: String?): String? =
            when (featureClass) {
                "highway" -> "Road"
                "place" -> when (type) {
                    "city" -> "City"
                    "town" -> "Town"
                    "village" -> "Village"
                    else -> "Place"
                }

                "amenity" -> when (type) {
                    "restaurant" -> "Restaurant"
                    "fast_food" -> "Fast food"
                    "cafe" -> "Café"
                    "bar", "pub" -> "Bar / Pub"
                    "hospital" -> "Hospital"
                    "pharmacy" -> "Pharmacy"
                    "school" -> "School"
                    "university" -> "University"
                    "bank" -> "Bank"
                    "atm" -> "ATM"
                    "fuel" -> "Petrol station"
                    "parking" -> "Parking"
                    "police" -> "Police"
                    "post_office" -> "Post office"
                    "library" -> "Library"
                    "theatre" -> "Theatre"
                    "cinema" -> "Cinema"
                    "place_of_worship" -> "Place of worship"
                    "townhall" -> "Town hall"
                    else -> "Service"
                }

                "tourism" -> when (type) {
                    "museum" -> "Museum"
                    "hotel", "hostel", "motel" -> "Hotel"
                    "attraction", "monument" -> "Attraction / Monument"
                    "artwork" -> "Artwork"
                    "gallery" -> "Gallery"
                    else -> "Tourism"
                }

                "shop" -> "Shop"
                "office" -> "Office"
                "historic" -> "Historic site"
                "leisure" -> "Leisure"
                "natural" -> "Natural area"
                "railway" -> "Railway / Station"
                "aeroway" -> "Airport"
                else -> null
            }
    }
}

data class NominatimReverseDetail(
    val display: String,
    val wikiQuery: String?,
    val openingHours: String?,
    val label: String,
    /**
     * The name of an actual place at the point (a shop, a monument, a park), as
     * opposed to the neighbourhood [wikiQuery] falls back to. Native-only: the
     * Flutter parity fields above are untouched.
     */
    val poiName: String? = null,
)

/** Exact socket-free normalization of Roadstr's search-provider responses. */
object SearchResponseProtocol {
    fun parseNominatimSearch(body: String): List<SearchResult> = try {
        val values = BoundedJsonParser(body).parse() as List<*>
        values.mapNotNull { value ->
            try {
                parseNominatimResult(value as Map<*, *>)
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun parseNominatimReverse(body: String): NominatimReverseDetail? = try {
        val data = parseObject(body)
        val display = nullableString(data["display_name"]) ?: ""
        val address = nullableMap(data["address"]) ?: emptyMap<Any?, Any?>()
        val extraTags = nullableMap(data["extratags"]) ?: emptyMap<Any?, Any?>()
        val openingHours = nullableString(extraTags["opening_hours"])?.trimDart()

        fun isNumber(value: String?): Boolean =
            value == null || Regex("^\\d+$").matches(value.trimDart())

        val poiName = listOf(
            nullableString(data["name"]),
            nullableString(address["tourism"]),
            nullableString(address["amenity"]),
            nullableString(address["historic"]),
            nullableString(address["leisure"]),
            nullableString(address["suburb"]),
            nullableString(address["quarter"]),
            nullableString(address["neighbourhood"]),
        ).firstOrNull { value -> value != null && value.isNotEmpty() && !isNumber(value) }
        val placeName = listOf(
            nullableString(data["name"]),
            nullableString(address["tourism"]),
            nullableString(address["amenity"]),
            nullableString(address["historic"]),
            nullableString(address["leisure"]),
        ).firstOrNull { value -> value != null && value.isNotEmpty() && !isNumber(value) }
        val city = listOf(
            nullableString(address["city"]),
            nullableString(address["town"]),
            nullableString(address["village"]),
            nullableString(address["municipality"]),
            nullableString(address["county"]),
        ).firstOrNull { value -> value != null && value.isNotEmpty() && !isNumber(value) }
        val wikiQuery = if (poiName != null) {
            if (city != null && city != poiName) "$poiName $city" else poiName
        } else {
            city
        }
        NominatimReverseDetail(
            display = display,
            wikiQuery = wikiQuery,
            openingHours = openingHours?.takeIf(String::isNotEmpty),
            label = shortLabelFrom(
                display = display,
                address = address,
                name = nullableString(data["name"]),
            ),
            poiName = placeName,
        )
    } catch (_: Exception) {
        null
    }

    fun parsePhoton(body: String): List<SearchResult> = try {
        val data = parseObject(body)
        val features = when (val value = data["features"]) {
            null -> emptyList<Any?>()
            else -> value as List<*>
        }
        features.mapNotNull { value ->
            try {
                parsePhotonFeature(value as Map<*, *>)
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun parseOverpassElements(body: String): List<Map<String, Any?>> {
        val data = parseObject(body)
        val rawElements = when (val value = data["elements"]) {
            null -> return emptyList()
            else -> value as List<*>
        }
        return rawElements.mapNotNull { value ->
            val raw = value as? Map<*, *> ?: return@mapNotNull null
            linkedMapOf<String, Any?>().also { element ->
                for ((key, item) in raw) {
                    if (key is String) element[key] = item
                }
            }
        }
    }

    fun overpassElementToResult(
        element: Map<String, Any?>,
        center: SearchResponsePoint,
        fallbackName: String? = null,
    ): SearchResult? {
        val tags = nullableMap(element["tags"])
        var name = nullableString(tags?.get("name"))
        if (name == null || name.isEmpty()) {
            name = nullableString(tags?.get("brand"))?.trimDart()
        }
        if (name == null || name.isEmpty()) name = fallbackName
        if (name == null || name.isEmpty()) return null

        var latitude = nullableNumber(element["lat"])?.toDouble()
        var longitude = nullableNumber(element["lon"])?.toDouble()
        if (latitude == null || longitude == null) {
            val elementCenter = nullableMap(element["center"])
            latitude = nullableNumber(elementCenter?.get("lat"))?.toDouble()
            longitude = nullableNumber(elementCenter?.get("lon"))?.toDouble()
        }
        if (
            latitude == null ||
            longitude == null ||
            !latitude.isFinite() ||
            !longitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return null
        }

        val featureClass = when {
            tags?.get("shop") != null -> "shop"
            tags?.get("amenity") != null -> "amenity"
            tags?.get("tourism") != null -> "tourism"
            else -> null
        }
        val type = SearchResult.clampRemoteText(
            tags?.get("shop") ?: tags?.get("amenity") ?: tags?.get("tourism"),
            80,
        )
        val safeName = SearchResult.clampRemoteText(name, 120) ?: return null
        val position = SearchResponsePoint(latitude, longitude)
        return SearchResult(
            displayName = safeName,
            shortName = safeName,
            position = position,
            featureClass = featureClass,
            type = type,
            openingHours = SearchResult.clampRemoteText(tags?.get("opening_hours"), 300),
            distanceM = roundedVincentyDistance(center, position),
        )
    }

    fun shortLabelFrom(
        display: String,
        address: Map<*, *>,
        name: String? = null,
    ): String {
        fun text(value: Any?): String? {
            val clean = (value as? String)
                ?.replace(Regex("[\\u0000-\\u001f]"), " ")
                ?.trimDart()
                ?: return null
            if (clean.isEmpty()) return null
            return if (clean.length <= 80) clean else clean.substring(0, 80) + "…"
        }

        val city = text(address["city"])
            ?: text(address["town"])
            ?: text(address["village"])
            ?: text(address["hamlet"])
            ?: text(address["municipality"])
        val road = text(address["road"]) ?: text(address["pedestrian"])
        val houseNumber = text(address["house_number"])
        val poi = text(name)
            ?: text(address["amenity"])
            ?: text(address["shop"])
            ?: text(address["tourism"])
            ?: text(address["historic"])
            ?: text(address["leisure"])
        if (poi != null && !Regex("^\\d+$").matches(poi)) {
            return if (city != null && city != poi) "$poi, $city" else poi
        }
        if (road != null) {
            val street = if (houseNumber != null) "$road $houseNumber" else road
            return if (city != null) "$street, $city" else street
        }
        val parts = display.split(',')
            .mapNotNull(::text)
            .filterNot { Regex("^\\d+$").matches(it) }
        if (parts.isEmpty()) return city ?: display.split(',').first().trimDart()
        return if (parts.size > 1 && city != null && parts.first() != city) {
            "${parts.first()}, $city"
        } else {
            parts.first()
        }
    }

    private fun parseNominatimResult(json: Map<*, *>): SearchResult {
        val latitude = (json["lat"] as String).toDoubleOrNull() ?: Double.NaN
        val longitude = (json["lon"] as String).toDoubleOrNull() ?: Double.NaN
        if (
            !latitude.isFinite() ||
            !longitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            error("Nominatim: invalid coordinates")
        }
        val display = SearchResult.clampRemoteText(json["display_name"])
            ?: error("Nominatim: no name")
        val featureClass = nullableString(json["class"])
        val address = nullableMap(json["address"]) ?: emptyMap<Any?, Any?>()
        val road = SearchResult.clampRemoteText(address["road"], 120)
        val houseNumber = SearchResult.clampRemoteText(address["house_number"], 24)
        val city = SearchResult.clampRemoteText(
            address["city"]
                ?: address["town"]
                ?: address["village"]
                ?: address["hamlet"]
                ?: address["municipality"],
            120,
        )
        val shortName = when {
            road != null && houseNumber != null -> buildString {
                append(road)
                append(' ')
                append(houseNumber)
                if (city != null) append(", $city")
            }

            road != null && featureClass == "highway" ->
                if (city != null) "$road, $city" else road

            else -> display.split(',').first().trimDart()
        }
        val extraTags = nullableMap(json["extratags"]) ?: emptyMap<Any?, Any?>()
        return SearchResult(
            displayName = display,
            shortName = shortName,
            position = SearchResponsePoint(latitude, longitude),
            featureClass = SearchResult.clampRemoteText(featureClass, 80),
            type = SearchResult.clampRemoteText(json["type"], 80),
            city = city,
            brand = SearchResult.clampRemoteText(extraTags["brand"], 160),
        )
    }

    private fun parsePhotonFeature(feature: Map<*, *>): SearchResult? {
        val geometry = nullableMap(feature["geometry"])
        val coordinates = when (val value = geometry?.get("coordinates")) {
            null -> return null
            else -> value as List<*>
        }
        if (coordinates.size < 2) return null
        val longitude = (coordinates[0] as Number).toDouble()
        val latitude = (coordinates[1] as Number).toDouble()
        if (
            !latitude.isFinite() ||
            !longitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return null
        }
        val properties = nullableMap(feature["properties"]) ?: emptyMap<Any?, Any?>()
        fun text(key: String): String? {
            val value = properties[key] as? String ?: return null
            val clean = value.replace(Regex("[\\u0000-\\u001f]"), " ").trimDart()
            if (clean.isEmpty()) return null
            return if (clean.length <= 160) clean else clean.substring(0, 160)
        }

        val name = text("name")
        val street = text("street")
        val houseNumber = text("housenumber")
        val city = text("city") ?: text("town") ?: text("village") ?: text("county")
        val state = text("state")
        val country = text("country")
        val shortName = when {
            street != null -> buildString {
                append(street)
                if (houseNumber != null) append(" $houseNumber")
                if (city != null) append(", $city")
            }

            name != null -> if (city != null && city != name) "$name, $city" else name
            city != null -> city
            else -> return null
        }
        val displayParts = linkedSetOf<String>()
        if (name != null && name != street) displayParts += name
        if (street != null) displayParts += if (houseNumber != null) "$street $houseNumber" else street
        if (city != null) displayParts += city
        if (state != null) displayParts += state
        if (country != null) displayParts += country
        val display = displayParts.joinToString(", ")
        return SearchResult(
            displayName = display.ifEmpty { shortName },
            shortName = shortName,
            position = SearchResponsePoint(latitude, longitude),
            featureClass = text("osm_key"),
            type = text("osm_value"),
            city = city,
        )
    }

    private fun parseObject(body: String): Map<String, Any?> {
        val decoded = BoundedJsonParser(body).parse() as Map<*, *>
        return linkedMapOf<String, Any?>().also { result ->
            for ((key, value) in decoded) {
                if (key !is String) error("Malformed search response")
                result[key] = value
            }
        }
    }

    private fun nullableString(value: Any?): String? = when (value) {
        null -> null
        else -> value as String
    }

    private fun nullableMap(value: Any?): Map<*, *>? = when (value) {
        null -> null
        else -> value as Map<*, *>
    }

    private fun nullableNumber(value: Any?): Number? = when (value) {
        null -> null
        else -> value as Number
    }

    /** Mirrors latlong2's default rounded WGS-84 Vincenty distance. */
    private fun roundedVincentyDistance(
        first: SearchResponsePoint,
        second: SearchResponsePoint,
    ): Double {
        val equatorRadius = 6_378_137.0
        val polarRadius = 6_356_752.314245
        val flattening = 1 / 298.257223563
        val latitude1 = Math.toRadians(first.latitude)
        val latitude2 = Math.toRadians(second.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val u1 = atan((1 - flattening) * tan(latitude1))
        val u2 = atan((1 - flattening) * tan(latitude2))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)
        var lambda = longitudeDelta
        var iterations = 200
        var sinSigma: Double
        var cosSigma: Double
        var sigma: Double
        var sinAlpha: Double
        var cosSqAlpha: Double
        var cos2SigmaM: Double
        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) *
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda),
            )
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            if (cos2SigmaM.isNaN()) cos2SigmaM = 0.0
            val c = flattening / 16 * cosSqAlpha * (4 + flattening * (4 - 3 * cosSqAlpha))
            val previousLambda = lambda
            lambda = longitudeDelta +
                (1 - c) * flattening * sinAlpha *
                (sigma + c * sinSigma *
                    (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - previousLambda) <= 1e-12) break
        } while (--iterations > 0)
        if (iterations == 0) error("Distance calculation failed to converge")
        val uSquared = cosSqAlpha *
            (equatorRadius * equatorRadius - polarRadius * polarRadius) /
            (polarRadius * polarRadius)
        val a = 1 + uSquared / 16_384 *
            (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)))
        val b = uSquared / 1024 *
            (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)))
        val deltaSigma = b * sinSigma *
            (
                cos2SigmaM + b / 4 *
                    (
                        cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                            b / 6 * cos2SigmaM *
                            (-3 + 4 * sinSigma * sinSigma) *
                            (-3 + 4 * cos2SigmaM * cos2SigmaM)
                    )
            )
        return floor(polarRadius * a * (sigma - deltaSigma) + 0.5)
    }
}

private fun String.trimDart(): String {
    var start = 0
    var end = length
    while (start < end && this[start].isDartWhitespace()) start++
    while (end > start && this[end - 1].isDartWhitespace()) end--
    return substring(start, end)
}

private fun Char.isDartWhitespace(): Boolean =
    this in '\u0009'..'\u000d' ||
        this == '\u0020' ||
        this == '\u0085' ||
        this == '\u00a0' ||
        this == '\u1680' ||
        this in '\u2000'..'\u200a' ||
        this == '\u2028' ||
        this == '\u2029' ||
        this == '\u202f' ||
        this == '\u205f' ||
        this == '\u3000' ||
        this == '\ufeff'
