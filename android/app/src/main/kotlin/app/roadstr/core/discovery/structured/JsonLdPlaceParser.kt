package app.roadstr.core.discovery.structured

import app.roadstr.core.discovery.PlaceTagPolicy
import app.roadstr.core.discovery.UrlEncoding
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.net.URI

/** What a page says about a place in its own schema.org markup. A claim by the page, never a fact. */
data class StructuredPlace(
    val name: String?,
    val types: List<String>,
    val position: GeoPoint?,
    val streetAddress: String?,
    val postalCode: String?,
    val locality: String?,
    val region: String?,
    val country: String?,
    val phone: String?,
    val website: URI?,
    val menu: URI?,
    val openingHours: List<String>,
    val cuisine: List<String>,
) {
    // A name, an address and a position are what the user is looking at.
    override fun toString(): String = "StructuredPlace(types=$types)"
}

/**
 * Reads the `application/ld+json` blocks of a page into places. The page is untrusted input:
 * blocks, size, nesting and the number of places are capped, only `LocalBusiness` style types are
 * read, text is cleaned and clamped, coordinates are range-checked, addresses must be https, and
 * nothing the page contains is ever evaluated.
 */
object JsonLdPlaceParser {
    const val MAX_BLOCKS = 8
    const val MAX_BLOCK_CHARS = 65_536
    const val MAX_PLACES = 8
    const val MAX_NODES = 400
    const val MAX_DEPTH = 8
    private const val MAX_HOURS = 14
    private const val MAX_CUISINE = 5
    private const val MAX_PHONE_CHARS = 32
    private const val MAX_HOURS_CHARS = 80

    private val phoneChars = Regex("[+0-9 ()\\-./]+")
    private val hours = Regex("^[A-Za-z]{2}(?:[-,][A-Za-z]{2})*\\s+\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}$")

    /** schema.org types that describe a place a person can go to; everything else is ignored. */
    private val placeTypes = setOf(
        "LocalBusiness", "Place", "Store", "FoodEstablishment", "Restaurant", "CafeOrCoffeeShop",
        "BarOrPub", "Bakery", "FastFoodRestaurant", "IceCreamShop", "Winery", "Brewery", "Distillery",
        "LodgingBusiness", "Hotel", "Hostel", "Motel", "BedAndBreakfast", "Resort", "GroceryStore",
        "ConvenienceStore", "Pharmacy", "GasStation", "ParkingFacility", "ShoppingCenter",
        "HealthAndBeautyBusiness", "AutomotiveBusiness", "AutoRepair", "AutoWash", "CarRental",
        "Dentist", "Physician", "MedicalClinic", "Hospital", "EntertainmentBusiness", "MovieTheater",
        "TouristAttraction", "Museum", "LandmarksOrHistoricalBuildings", "Library", "Bank",
        "FinancialService", "ATM", "BankOrCreditUnion", "TravelAgency", "SportsActivityLocation",
        "GolfCourse", "SkiResort", "TouristInformationCenter", "TrainStation", "BusStation",
        "ElectricVehicleChargingStation", "EmergencyService", "PostOffice",
    )

    /** The places in [blocks], the raw text of each JSON-LD script; damaged blocks are skipped. */
    fun parse(blocks: List<String>): List<StructuredPlace> {
        val found = ArrayList<StructuredPlace>()
        for (block in blocks.take(MAX_BLOCKS)) {
            if (found.size >= MAX_PLACES) break
            val root = decode(block) ?: continue
            collect(root, depth = 0, budget = intArrayOf(MAX_NODES), into = found)
        }
        return found.take(MAX_PLACES)
    }

    private fun decode(block: String): Any? {
        if (block.length > MAX_BLOCK_CHARS) return null
        return try {
            BoundedJsonParser(block.trim()).parse()
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun collect(node: Any?, depth: Int, budget: IntArray, into: MutableList<StructuredPlace>) {
        if (depth > MAX_DEPTH || into.size >= MAX_PLACES || budget[0]-- <= 0) return
        when (node) {
            is List<*> -> node.forEach { collect(it, depth + 1, budget, into) }
            is Map<*, *> -> {
                place(node)?.let(into::add)
                (node["@graph"] as? List<*>)?.forEach { collect(it, depth + 1, budget, into) }
                node["mainEntity"]?.let { collect(it, depth + 1, budget, into) }
            }
        }
    }

    private fun place(map: Map<*, *>): StructuredPlace? {
        val types = typesOf(map["@type"])
        if (types.none { it in placeTypes }) return null
        val address = map["address"]
        val postal = address as? Map<*, *>
        val place = StructuredPlace(
            name = text(map["name"]),
            types = types.filter { it in placeTypes },
            position = position(map["geo"]),
            streetAddress = text(postal?.get("streetAddress") ?: (address as? String)),
            postalCode = text(postal?.get("postalCode")),
            locality = text(postal?.get("addressLocality")),
            region = text(postal?.get("addressRegion")),
            country = text(postal?.get("addressCountry").let { (it as? Map<*, *>)?.get("name") ?: it }),
            phone = phone(map["telephone"]),
            website = https(map["url"]),
            menu = https(menuOf(map["hasMenu"]) ?: map["menu"]),
            openingHours = openingHours(map["openingHours"]),
            cuisine = list(map["servesCuisine"]).mapNotNull(::text).take(MAX_CUISINE),
        )
        // A page that names nothing and locates nothing says nothing about a place.
        return place.takeIf { it.name != null || it.position != null }
    }

    private fun typesOf(value: Any?): List<String> =
        list(value).filterIsInstance<String>().map { it.substringAfterLast('/').substringAfter(':') }

    private fun list(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value.take(MAX_CUISINE * 4)
        else -> listOf(value)
    }

    private fun text(value: Any?): String? = (value as? String)?.let { PlaceTagPolicy.clamp(it) }

    private fun phone(value: Any?): String? {
        val raw = (value as? String)?.trim() ?: return null
        if (raw.length > MAX_PHONE_CHARS || !phoneChars.matches(raw)) return null
        return raw.takeIf { it.count(Char::isDigit) >= 3 }
    }

    private fun https(value: Any?): URI? = (value as? String)?.let { UrlEncoding.safeHttps(it) }

    private fun menuOf(value: Any?): Any? = when (value) {
        is Map<*, *> -> value["url"] ?: value["@id"]
        is List<*> -> value.firstNotNullOfOrNull { menuOf(it) }
        else -> value
    }

    private fun openingHours(value: Any?): List<String> = list(value)
        .filterIsInstance<String>()
        .map { it.trim() }
        .filter { it.length <= MAX_HOURS_CHARS && hours.matches(it) }
        .take(MAX_HOURS)

    private fun position(value: Any?): GeoPoint? {
        val geo = value as? Map<*, *> ?: return null
        val latitude = number(geo["latitude"]) ?: return null
        val longitude = number(geo["longitude"]) ?: return null
        val valid = latitude in -90.0..90.0 && longitude in -180.0..180.0
        // (0, 0) is what a page writes when it does not know.
        if (!valid || (latitude == 0.0 && longitude == 0.0)) return null
        return GeoPoint(latitude, longitude)
    }

    private fun number(value: Any?): Double? {
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return number?.takeIf { it.isFinite() }
    }
}
