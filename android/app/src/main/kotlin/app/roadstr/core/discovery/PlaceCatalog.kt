package app.roadstr.core.discovery

/** A condition on one OSM tag: present, one of [values], or matching [regex]. */
data class TagMatch(
    val key: String,
    val values: Set<String>? = null,
    val regex: String? = null,
) {
    init {
        require(KEY.matches(key)) { "Invalid OSM tag key" }
        require(values == null || regex == null) { "A tag match is either a value set or a regex" }
        values?.forEach { value -> require(VALUE.matches(value)) { "Invalid OSM tag value" } }
        regex?.let { pattern -> require(REGEX.matches(pattern)) { "Invalid OSM tag regex" } }
    }

    private companion object {
        val KEY = Regex("^[a-z][a-z0-9_:]{0,40}$")
        val VALUE = Regex("^[A-Za-z0-9_:/;. -]{1,40}$")
        val REGEX = Regex("^[A-Za-z0-9_|^$()/;.-]{1,80}$")
    }
}

enum class CategoryGroup {
    EAT,
    DRINK,
    LODGING,
    HEALTH,
    SHOPPING,
    CULTURE,
    OUTDOOR,
    SERVICES,
    TRANSPORT,
    SPORT,
}

private fun tag(key: String, vararg values: String) = TagMatch(key, values.toSet())

/**
 * What a place category means in OpenStreetMap: a list of alternatives, each a
 * list of tags that must all hold. Data, not logic; the parser and the Overpass
 * builder both read this one list.
 */
enum class PlaceCategory(
    val emoji: String,
    val alternatives: List<List<TagMatch>>,
    val groups: Set<CategoryGroup> = emptySet(),
) {
    RESTAURANT("🍽️", listOf(listOf(tag("amenity", "restaurant"))), setOf(CategoryGroup.EAT)),
    FAST_FOOD("🍔", listOf(listOf(tag("amenity", "fast_food"))), setOf(CategoryGroup.EAT)),
    CAFE("☕", listOf(listOf(tag("amenity", "cafe"))), setOf(CategoryGroup.EAT, CategoryGroup.DRINK)),
    BAR("🍺", listOf(listOf(tag("amenity", "bar"))), setOf(CategoryGroup.DRINK)),
    PUB("🍻", listOf(listOf(tag("amenity", "pub"))), setOf(CategoryGroup.DRINK)),
    ICE_CREAM("🍦", listOf(listOf(tag("amenity", "ice_cream")), listOf(tag("shop", "ice_cream")))),
    BAKERY("🥖", listOf(listOf(tag("shop", "bakery", "pastry")))),
    SUPERMARKET("🛒", listOf(listOf(tag("shop", "supermarket"))), setOf(CategoryGroup.SHOPPING)),
    CONVENIENCE("🏪", listOf(listOf(tag("shop", "convenience"))), setOf(CategoryGroup.SHOPPING)),
    GREENGROCER("🥬", listOf(listOf(tag("shop", "greengrocer"))), setOf(CategoryGroup.SHOPPING)),
    BUTCHER("🥩", listOf(listOf(tag("shop", "butcher")))),
    PHARMACY("💊", listOf(listOf(tag("amenity", "pharmacy"))), setOf(CategoryGroup.HEALTH)),
    CLOTHES("👗", listOf(listOf(tag("shop", "clothes"))), setOf(CategoryGroup.SHOPPING)),
    SHOES("👟", listOf(listOf(tag("shop", "shoes"))), setOf(CategoryGroup.SHOPPING)),
    MUSICAL_INSTRUMENTS(
        "🎹",
        listOf(listOf(tag("shop", "musical_instrument"))),
        setOf(CategoryGroup.SHOPPING),
    ),
    ELECTRONICS(
        "📱",
        listOf(listOf(tag("shop", "electronics", "computer", "mobile_phone"))),
        setOf(CategoryGroup.SHOPPING),
    ),
    HARDWARE("🔧", listOf(listOf(tag("shop", "hardware", "doityourself")))),
    BOOKS("📚", listOf(listOf(tag("shop", "books"))), setOf(CategoryGroup.SHOPPING)),
    FLORIST("💐", listOf(listOf(tag("shop", "florist")))),
    BICYCLE_SHOP("🚲", listOf(listOf(tag("shop", "bicycle"))), setOf(CategoryGroup.SHOPPING)),
    MALL("🛍️", listOf(listOf(tag("shop", "mall"))), setOf(CategoryGroup.SHOPPING)),
    HAIRDRESSER("💇", listOf(listOf(tag("shop", "hairdresser")))),
    LAUNDRY("🧺", listOf(listOf(tag("shop", "laundry", "dry_cleaning")))),
    CAR_REPAIR("🔧", listOf(listOf(tag("shop", "car_repair")))),
    CAR_WASH("🚿", listOf(listOf(tag("amenity", "car_wash")))),
    BANK("🏦", listOf(listOf(tag("amenity", "bank"))), setOf(CategoryGroup.SERVICES)),
    ATM("🏧", listOf(listOf(tag("amenity", "atm"))), setOf(CategoryGroup.SERVICES)),
    POST_OFFICE("📮", listOf(listOf(tag("amenity", "post_office"))), setOf(CategoryGroup.SERVICES)),
    FUEL("⛽", listOf(listOf(tag("amenity", "fuel"))), setOf(CategoryGroup.TRANSPORT)),
    CHARGING_STATION("🔌", listOf(listOf(tag("amenity", "charging_station"))), setOf(CategoryGroup.TRANSPORT)),
    PARKING("🅿️", listOf(listOf(tag("amenity", "parking"))), setOf(CategoryGroup.TRANSPORT)),
    TRAIN_STATION("🚉", listOf(listOf(tag("railway", "station"))), setOf(CategoryGroup.TRANSPORT)),
    BUS_STATION("🚌", listOf(listOf(tag("amenity", "bus_station"))), setOf(CategoryGroup.TRANSPORT)),
    AIRPORT("✈️", listOf(listOf(tag("aeroway", "aerodrome"))), setOf(CategoryGroup.TRANSPORT)),
    TAXI("🚕", listOf(listOf(tag("amenity", "taxi"))), setOf(CategoryGroup.TRANSPORT)),
    HOSPITAL("🏥", listOf(listOf(tag("amenity", "hospital"))), setOf(CategoryGroup.HEALTH)),
    CLINIC("🩺", listOf(listOf(tag("amenity", "clinic", "doctors"))), setOf(CategoryGroup.HEALTH)),
    DENTIST("🦷", listOf(listOf(tag("amenity", "dentist"))), setOf(CategoryGroup.HEALTH)),
    VETERINARY("🐾", listOf(listOf(tag("amenity", "veterinary")))),
    HOTEL(
        "🏨",
        listOf(listOf(tag("tourism", "hotel", "hostel", "guest_house", "motel"))),
        setOf(CategoryGroup.LODGING),
    ),
    CAMPING("⛺", listOf(listOf(tag("tourism", "camp_site", "caravan_site"))), setOf(CategoryGroup.LODGING)),
    CINEMA("🎬", listOf(listOf(tag("amenity", "cinema"))), setOf(CategoryGroup.CULTURE)),
    THEATRE("🎭", listOf(listOf(tag("amenity", "theatre"))), setOf(CategoryGroup.CULTURE)),
    MUSEUM("🏛️", listOf(listOf(tag("tourism", "museum"))), setOf(CategoryGroup.CULTURE)),
    ART_GALLERY("🖼️", listOf(listOf(tag("tourism", "gallery"))), setOf(CategoryGroup.CULTURE)),
    ATTRACTION("⌘", listOf(listOf(tag("tourism", "attraction"))), setOf(CategoryGroup.CULTURE)),
    MONUMENT("▲", listOf(listOf(tag("historic", "monument", "memorial"))), setOf(CategoryGroup.CULTURE)),
    VIEWPOINT("◉", listOf(listOf(tag("tourism", "viewpoint"))), setOf(CategoryGroup.OUTDOOR)),
    PARK("🌳", listOf(listOf(tag("leisure", "park"))), setOf(CategoryGroup.OUTDOOR)),
    NATURE_RESERVE("🌿", listOf(listOf(tag("leisure", "nature_reserve"))), setOf(CategoryGroup.OUTDOOR)),
    SWIMMING_POOL("🏊", listOf(listOf(tag("leisure", "swimming_pool"))), setOf(CategoryGroup.SPORT)),
    GYM("🏋️", listOf(listOf(tag("leisure", "fitness_centre"))), setOf(CategoryGroup.SPORT)),
    SPORTS_CENTRE("⚽", listOf(listOf(tag("leisure", "sports_centre", "stadium"))), setOf(CategoryGroup.SPORT)),
    LIBRARY("📚", listOf(listOf(tag("amenity", "library")))),
    PLACE_OF_WORSHIP("⛪", listOf(listOf(tag("amenity", "place_of_worship")))),
    TOILETS("🚻", listOf(listOf(tag("amenity", "toilets")))),
    DRINKING_WATER("🚰", listOf(listOf(tag("amenity", "drinking_water")))),
    POLICE("👮", listOf(listOf(tag("amenity", "police")))),
    FIRE_STATION("🚒", listOf(listOf(tag("amenity", "fire_station")))),
    SCHOOL("🏫", listOf(listOf(tag("amenity", "school")))),
    UNIVERSITY("🎓", listOf(listOf(tag("amenity", "university")))),
    ;

    companion object {
        fun members(group: CategoryGroup): List<PlaceCategory> =
            entries.filter { group in it.groups }
    }
}

/** A property a place may have, with the tag values that confirm it. */
enum class PlaceAttribute(
    val key: String,
    val values: Set<String>,
    val impliedGroup: CategoryGroup? = null,
    val impliedCategory: PlaceCategory? = null,
) {
    VEGAN("diet:vegan", setOf("yes", "only"), impliedGroup = CategoryGroup.EAT),
    VEGETARIAN("diet:vegetarian", setOf("yes", "only"), impliedGroup = CategoryGroup.EAT),
    GLUTEN_FREE("diet:gluten_free", setOf("yes", "only"), impliedGroup = CategoryGroup.EAT),
    HALAL("diet:halal", setOf("yes", "only"), impliedGroup = CategoryGroup.EAT),
    KOSHER("diet:kosher", setOf("yes", "only"), impliedGroup = CategoryGroup.EAT),
    OUTDOOR_SEATING("outdoor_seating", setOf("yes")),
    WHEELCHAIR("wheelchair", setOf("yes", "designated")),
    WIFI("internet_access", setOf("wlan", "wifi", "yes")),
    TAKEAWAY("takeaway", setOf("yes", "only")),
    DELIVERY("delivery", setOf("yes", "only")),
    DRIVE_THROUGH("drive_through", setOf("yes")),
    LPG("fuel:lpg", setOf("yes"), impliedCategory = PlaceCategory.FUEL),
    CNG("fuel:cng", setOf("yes"), impliedCategory = PlaceCategory.FUEL),
    DIESEL("fuel:diesel", setOf("yes"), impliedCategory = PlaceCategory.FUEL),
    OPEN_24_7("opening_hours", setOf("24/7")),
    ;

    /** The tag that decides a place does *not* have this property. */
    val negativeValues: Set<String> = setOf("no")
}

/** A cuisine, matched against the semicolon-separated `cuisine` tag. */
enum class Cuisine(val values: Set<String>) {
    PIZZA(setOf("pizza")),
    ITALIAN(setOf("italian")),
    SICILIAN(setOf("sicilian")),
    STEAK_HOUSE(setOf("steak_house", "steak")),
    BURGER(setOf("burger", "hamburger")),
    SEAFOOD(setOf("seafood", "fish")),
    SUSHI(setOf("sushi", "japanese")),
    CHINESE(setOf("chinese")),
    INDIAN(setOf("indian")),
    KEBAB(setOf("kebab", "turkish")),
    MEXICAN(setOf("mexican")),
    THAI(setOf("thai")),
    VIETNAMESE(setOf("vietnamese")),
    GREEK(setOf("greek")),
    SPANISH(setOf("spanish", "tapas")),
    FRENCH(setOf("french")),
    GERMAN(setOf("german")),
    LEBANESE(setOf("lebanese")),
}

/** Reads OSM tags through the catalogue. */
object PlaceCatalog {
    /** The first category whose tags a place satisfies, or null for an unknown kind of place. */
    fun categorize(tags: Map<String, String>): PlaceCategory? =
        PlaceCategory.entries.firstOrNull { category ->
            category.alternatives.any { alternative -> alternative.all { matches(it, tags) } }
        }

    fun matches(match: TagMatch, tags: Map<String, String>): Boolean {
        val value = tags[match.key] ?: return false
        match.values?.let { return value in it }
        match.regex?.let { return Regex(it).containsMatchIn(value) }
        return true
    }
}
