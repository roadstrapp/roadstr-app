package app.roadstr.core.search

import java.net.URI
import java.util.Collections
import java.util.Locale

enum class OsmPlaceKind {
    Parking,
    ChargingStation,
    FuelStation,
    Lodging,
    FoodAndDrink,
    Other,
}

data class OsmEvConnector(
    val type: String,
    val count: Int?,
    val output: String?,
)

data class OsmPlaceDetails(
    val name: String?,
    val category: String,
    val kind: OsmPlaceKind,
    val description: String?,
    val address: String?,
    val openingHours: String?,
    val operatorName: String?,
    val cuisine: String?,
    val wheelchair: String?,
    val phone: String?,
    val email: String?,
    val website: URI?,
    val acceptsBitcoin: Boolean,
    val acceptsLightning: Boolean,
    val access: String?,
    val fee: String?,
    val charge: String?,
    val capacity: Int?,
    val maxStay: String?,
    val parkingType: String?,
    val evConnectors: List<OsmEvConnector>,
    val fuels: Set<String>,
    val stars: String?,
    val smoking: String?,
    val outdoorSeating: String?,
    val takeaway: String?,
)

/** Exact bounded public-information projection of Flutter's OsmPoiDetails. */
object OsmPlaceDetailsProtocol {
    private val categoryKeys = listOf(
        "amenity",
        "shop",
        "tourism",
        "historic",
        "leisure",
        "office",
        "craft",
        "healthcare",
        "railway",
        "aeroway",
        "natural",
    )
    private val wheelchairValues = setOf("yes", "no", "limited", "designated")
    private val accessValues = setOf("private", "customers", "permit", "no", "destination")
    private val parkingValues = setOf(
        "surface",
        "underground",
        "multi-storey",
        "street_side",
        "lane",
        "rooftop",
    )
    private val smokingValues = setOf(
        "yes",
        "no",
        "outside",
        "separated",
        "isolated",
        "dedicated",
    )
    private val lodgingValues = setOf("hotel", "motel", "hostel", "guest_house", "apartment")
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val positiveInteger = Regex("^\\d{1,6}$")
    private val connectorCount = Regex("^\\d{1,3}$")
    private val starsPattern = Regex("^[1-7](?:[sS+])?$")
    private val languagePattern = Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$")
    private val controlCharacters = Regex("[\\u0000-\\u001f]")

    fun parse(
        tags: Map<String, Any?>,
        languageCode: String = "en",
    ): OsmPlaceDetails? {
        fun text(key: String, max: Int): String? {
            val value = tags[key] as? String ?: return null
            val clean = value.replace(controlCharacters, " ").trim()
            if (clean.isEmpty()) return null
            return if (clean.length <= max) clean else clean.substring(0, max) + "…"
        }

        val categoryValue = categoryKeys.firstNotNullOfOrNull { text(it, 80) } ?: return null
        val safeLanguage = languageCode.takeIf(languagePattern::matches) ?: "en"
        val street = text("addr:street", 120)
        val house = text("addr:housenumber", 30)
        val postcode = text("addr:postcode", 20)
        val city = text("addr:city", 100) ?: text("addr:town", 100) ?: text("addr:village", 100)
        val address = buildList {
            if (street != null) add(street + if (house == null) "" else " $house")
            listOfNotNull(postcode, city).joinToString(" ").takeIf(String::isNotEmpty)?.let(::add)
        }.joinToString(", ").takeIf(String::isNotEmpty)

        val websiteText = text("contact:website", 500) ?: text("website", 500)
        val website = websiteText?.let(::safeWebsite)
        val wheelchair = text("wheelchair", 20)?.takeIf(wheelchairValues::contains)
        val rawEmail = text("contact:email", 254) ?: text("email", 254)
        val email = rawEmail?.takeIf(emailPattern::matches)

        fun knownValue(key: String, allowed: Set<String>): String? =
            text(key, 80)?.lowercase(Locale.ROOT)?.takeIf(allowed::contains)

        fun isYes(key: String): Boolean =
            text(key, 20)?.lowercase(Locale.ROOT) in setOf("yes", "only", "accepted")

        fun positiveInt(key: String, max: Int = 100_000): Int? {
            val value = text(key, 20) ?: return null
            if (!positiveInteger.matches(value)) return null
            return value.toIntOrNull()?.takeIf { it in 1..max }
        }

        val amenity = text("amenity", 80)?.lowercase(Locale.ROOT)
        val tourism = text("tourism", 80)?.lowercase(Locale.ROOT)
        val kind = when {
            amenity == "parking" || amenity == "parking_entrance" -> OsmPlaceKind.Parking
            amenity == "charging_station" -> OsmPlaceKind.ChargingStation
            amenity == "fuel" -> OsmPlaceKind.FuelStation
            amenity in setOf("restaurant", "cafe", "bar", "pub", "fast_food") ->
                OsmPlaceKind.FoodAndDrink
            tourism in lodgingValues -> OsmPlaceKind.Lodging
            else -> OsmPlaceKind.Other
        }

        val connectors = mutableListOf<OsmEvConnector>()
        if (kind == OsmPlaceKind.ChargingStation) {
            for (type in listOf("type2", "chademo", "type2_combo")) {
                val raw = text("socket:$type", 20)?.lowercase(Locale.ROOT) ?: continue
                if (raw == "no" || raw == "0") continue
                val parsed = raw.takeIf(connectorCount::matches)?.toIntOrNull()
                val count = parsed?.takeIf { it > 0 }
                if (count == null && raw != "yes") continue
                connectors += OsmEvConnector(
                    type = type,
                    count = count,
                    output = text("socket:$type:output", 60),
                )
            }
        }

        val fuels = linkedSetOf<String>()
        if (kind == OsmPlaceKind.FuelStation) {
            if (isYes("fuel:diesel")) fuels += "diesel"
            if (isYes("fuel:octane_95")) fuels += "octane_95"
        }
        val stars = if (kind == OsmPlaceKind.Lodging) {
            text("stars", 10)?.takeIf(starsPattern::matches)?.uppercase(Locale.ROOT)
        } else {
            null
        }
        val cuisine = text("cuisine", 120)
            ?.split(';')
            ?.map(::humanize)
            ?.filter(String::isNotEmpty)
            ?.joinToString(", ")

        return OsmPlaceDetails(
            name = text("name:$safeLanguage", 160) ?: text("name", 160),
            category = humanize(categoryValue),
            kind = kind,
            description = text("description:$safeLanguage", 500) ?: text("description", 500),
            address = address,
            openingHours = text("opening_hours", 300),
            operatorName = text("operator", 160) ?: text("brand", 160),
            cuisine = cuisine,
            wheelchair = wheelchair,
            phone = text("contact:phone", 80) ?: text("phone", 80),
            email = email,
            website = website,
            acceptsBitcoin = isYes("payment:bitcoin"),
            acceptsLightning = isYes("payment:lightning"),
            access = knownValue("access", accessValues),
            fee = if (kind == OsmPlaceKind.Parking || kind == OsmPlaceKind.ChargingStation) {
                text("fee", 80)
            } else {
                null
            },
            charge = if (kind == OsmPlaceKind.Parking || kind == OsmPlaceKind.ChargingStation) {
                text("charge", 120)
            } else {
                null
            },
            capacity = if (kind == OsmPlaceKind.Parking || kind == OsmPlaceKind.ChargingStation) {
                positiveInt("capacity")
            } else {
                null
            },
            maxStay = if (kind == OsmPlaceKind.Parking) text("maxstay", 80) else null,
            parkingType = if (kind == OsmPlaceKind.Parking) {
                knownValue("parking", parkingValues)
            } else {
                null
            },
            evConnectors = Collections.unmodifiableList(connectors.toList()),
            fuels = Collections.unmodifiableSet(fuels.toSet()),
            stars = stars,
            smoking = if (kind == OsmPlaceKind.FoodAndDrink) {
                knownValue("smoking", smokingValues)
            } else {
                null
            },
            outdoorSeating = if (kind == OsmPlaceKind.FoodAndDrink) {
                knownValue("outdoor_seating", setOf("yes", "no"))
            } else {
                null
            },
            takeaway = if (kind == OsmPlaceKind.FoodAndDrink) {
                knownValue("takeaway", setOf("yes", "no", "only"))
            } else {
                null
            },
        )
    }

    private fun safeWebsite(value: String): URI? = try {
        URI(value).takeIf { uri ->
            uri.scheme == "https" &&
                !uri.host.isNullOrEmpty() &&
                uri.port == -1 &&
                uri.userInfo == null
        }
    } catch (_: Exception) {
        null
    }

    private fun humanize(value: String): String {
        val spaced = value.replace('_', ' ').trim()
        return spaced.replaceFirstChar { character -> character.uppercase(Locale.ROOT) }
    }
}
