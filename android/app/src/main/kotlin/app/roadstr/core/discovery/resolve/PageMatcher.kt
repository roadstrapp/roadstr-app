package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.structured.StructuredPlace
import app.roadstr.core.discovery.structured.WebPageMessage
import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint

/** The place a page the user is reading is about, as far as the evidence goes. */
data class PagePlace(
    val place: RoadstrPlace,
    val evidence: List<Evidence>,
    val confidence: Double,
    val provenance: Set<PlaceSource>,
    /** True when no place from the search matched and this one was built from what the page says. */
    val isNew: Boolean,
) {
    val matchClass: MatchClass get() = Confidence.classify(confidence)

    override fun toString(): String = "PagePlace(confidence=$confidence)"
}

/**
 * Ties a page the user opened to a place using what the page says about itself. The page's
 * coordinates count only when they fall inside the search area, so a page cannot move the user's
 * destination to the other side of the world. They are weak evidence alone (a candidate); with a
 * nearby known place of the same name, the same website or phone they become a link. A place built
 * only from a page is never given a map pin.
 */
object PageMatcher {
    private const val NEAR_KNOWN_METERS = 150.0
    private const val NAME_THRESHOLD = 0.85
    private const val DEFAULT_REACH_METERS = 30_000.0

    fun match(
        page: WebPageMessage,
        structured: List<StructuredPlace>,
        known: List<RoadstrPlace>,
        context: ResolveContext,
    ): PagePlace? {
        val claims = structured.filter { it.position != null } + listOfNotNull(fromMeta(page.meta))
        return claims.mapNotNull { claim -> score(page, claim, known, context) }
            .maxByOrNull { it.confidence }
    }

    private fun score(page: WebPageMessage, claim: StructuredPlace, known: List<RoadstrPlace>, context: ResolveContext): PagePlace? {
        val position = claim.position ?: return null
        val reach = if (context.radiusMeters > 0) context.radiusMeters else DEFAULT_REACH_METERS
        if (GeoMath.distanceMeters(context.center, position) > reach) return null
        val evidence = arrayListOf(Evidence(EvidenceKind.STRUCTURED_COORDS))
        val nearby = known.filter { GeoMath.distanceMeters(it.position, position) <= NEAR_KNOWN_METERS }
        val agreed = nearby.mapNotNull { place -> agreement(page, claim, place, position) }
            .maxByOrNull { Confidence.combine(it.second) }
        if (agreed == null) return fresh(claim, position, evidence, context)
        evidence += agreed.second
        if (agreed.first.category != null && agreed.first.category in context.categories) {
            evidence += Evidence(EvidenceKind.CATEGORY)
        }
        // The facts come from the map and from the page the user is reading.
        val provenance = LinkedHashSet(agreed.first.sources).apply { add(PlaceSource.WEBSITE) }
        return PagePlace(agreed.first, evidence, Confidence.combine(evidence), provenance, isNew = false)
    }

    /** Why a known place and the page's claim are the same place, or null when nothing says so. */
    private fun agreement(
        page: WebPageMessage,
        claim: StructuredPlace,
        place: RoadstrPlace,
        position: GeoPoint,
    ): Pair<RoadstrPlace, List<Evidence>>? {
        val evidence = ArrayList<Evidence>()
        val similarity = claim.name?.let { WebTitleNames.similarity(it, place.name) } ?: 0.0
        if (similarity >= NAME_THRESHOLD) {
            evidence += Evidence(EvidenceKind.NAME_LOCALITY)
            Confidence.proximity(GeoMath.distanceMeters(place.position, position))?.let(evidence::add)
        }
        val siteKey = place.website?.host?.let(HostMatching::siteKey)
        val pageHost = page.url.host
        val claimHost = claim.website?.host
        val siteAgrees = siteKey != null &&
            (siteKey == HostMatching.siteKey(pageHost) || siteKey == claimHost?.let(HostMatching::siteKey))
        if (siteAgrees) evidence += Evidence(EvidenceKind.WEBSITE_HOST)
        if (TextEvidence.phoneIn(place.phone, claim.phone.orEmpty())) evidence += Evidence(EvidenceKind.PHONE)
        return if (evidence.isEmpty()) null else place to evidence
    }

    private fun fresh(
        claim: StructuredPlace,
        position: GeoPoint,
        base: List<Evidence>,
        context: ResolveContext,
    ): PagePlace {
        val category = categoryOf(claim.types)
        val evidence = ArrayList(base)
        if (category != null && category in context.categories) evidence += Evidence(EvidenceKind.CATEGORY)
        val name = claim.name.orEmpty()
        val place = RoadstrPlace(
            id = RoadstrPlace.idFor(null, name, position),
            osm = null,
            name = name,
            category = category,
            position = position,
            address = listOfNotNull(claim.streetAddress, claim.postalCode, claim.locality).joinToString(", ").ifEmpty { null },
            distanceMeters = GeoMath.distanceMeters(context.center, position),
            openingHours = claim.openingHours.joinToString("; ").ifEmpty { null },
            phone = claim.phone,
            website = claim.website,
            cuisine = claim.cuisine.joinToString(", ").ifEmpty { null },
            tags = tags(claim),
            sources = setOf(PlaceSource.WEBSITE),
            confidence = Confidence.combine(evidence),
        )
        return PagePlace(place, evidence, place.confidence, setOf(PlaceSource.WEBSITE), isNew = true)
    }

    /** The tags the place card already knows how to show, from what the page said. */
    private fun tags(claim: StructuredPlace): Map<String, String> = buildMap {
        claim.name?.let { put("name", it) }
        claim.streetAddress?.let { put("addr:street", it) }
        claim.postalCode?.let { put("addr:postcode", it) }
        claim.locality?.let { put("addr:city", it) }
        claim.phone?.let { put("phone", it) }
        claim.website?.let { put("website", it.toString()) }
        if (claim.openingHours.isNotEmpty()) put("opening_hours", claim.openingHours.joinToString("; "))
        if (claim.cuisine.isNotEmpty()) put("cuisine", claim.cuisine.joinToString(";") { it.lowercase() })
    }

    /** A place described only by Open Graph meta tags: a title and a point. */
    private fun fromMeta(meta: Map<String, String>): StructuredPlace? {
        val latitude = (meta["og:latitude"] ?: meta["place:location:latitude"])?.replace(',', '.')?.toDoubleOrNull()
        val longitude = (meta["og:longitude"] ?: meta["place:location:longitude"])?.replace(',', '.')?.toDoubleOrNull()
        val valid = latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
        if (!valid) return null
        return StructuredPlace(
            name = meta["og:title"] ?: meta["og:site_name"],
            types = emptyList(),
            position = GeoPoint(latitude!!, longitude!!),
            streetAddress = meta["og:street-address"],
            postalCode = meta["og:postal-code"],
            locality = meta["og:locality"],
            region = null,
            country = meta["og:country-name"],
            phone = meta["og:phone_number"],
            website = null,
            menu = null,
            openingHours = emptyList(),
            cuisine = emptyList(),
        )
    }

    /** schema.org types that name a kind of place Roadstr knows. */
    fun categoryOf(types: List<String>): PlaceCategory? = types.firstNotNullOfOrNull(schemaCategories::get)

    private val schemaCategories = mapOf(
        "Restaurant" to PlaceCategory.RESTAURANT, "FastFoodRestaurant" to PlaceCategory.FAST_FOOD,
        "CafeOrCoffeeShop" to PlaceCategory.CAFE, "BarOrPub" to PlaceCategory.BAR,
        "Bakery" to PlaceCategory.BAKERY, "IceCreamShop" to PlaceCategory.ICE_CREAM,
        "Pharmacy" to PlaceCategory.PHARMACY, "GroceryStore" to PlaceCategory.SUPERMARKET,
        "ConvenienceStore" to PlaceCategory.CONVENIENCE, "GasStation" to PlaceCategory.FUEL,
        "ParkingFacility" to PlaceCategory.PARKING, "Hotel" to PlaceCategory.HOTEL,
        "Museum" to PlaceCategory.MUSEUM, "MovieTheater" to PlaceCategory.CINEMA,
        "Hospital" to PlaceCategory.HOSPITAL, "Dentist" to PlaceCategory.DENTIST,
        "Library" to PlaceCategory.LIBRARY, "Bank" to PlaceCategory.BANK, "ATM" to PlaceCategory.ATM,
        "PostOffice" to PlaceCategory.POST_OFFICE, "TrainStation" to PlaceCategory.TRAIN_STATION,
        "BusStation" to PlaceCategory.BUS_STATION, "AutoRepair" to PlaceCategory.CAR_REPAIR,
        "AutoWash" to PlaceCategory.CAR_WASH, "ShoppingCenter" to PlaceCategory.MALL,
        "ElectricVehicleChargingStation" to PlaceCategory.CHARGING_STATION,
    )
}
