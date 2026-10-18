package app.roadstr.core.discovery

/** Stable, UI-independent top-level catalogue for explicit area exploration. */
enum class ExploreCategory(
    val stableId: String,
    val categories: Set<PlaceCategory>,
) {
    ALL("all", PlaceCategory.entries.toSet()),
    FOOD_AND_DRINK(
        "food_drink",
        PlaceCategory.entries.filterTo(linkedSetOf()) {
            CategoryGroup.EAT in it.groups || CategoryGroup.DRINK in it.groups
        },
    ),
    ATTRACTIONS_AND_CULTURE("culture", PlaceCategory.members(CategoryGroup.CULTURE).toSet()),
    SHOPPING("shopping", PlaceCategory.members(CategoryGroup.SHOPPING).toSet()),
    NATURE_AND_OUTDOOR("outdoor", PlaceCategory.members(CategoryGroup.OUTDOOR).toSet()),
    SERVICES("services", PlaceCategory.members(CategoryGroup.SERVICES).toSet()),
    TRANSPORT("transport", PlaceCategory.members(CategoryGroup.TRANSPORT).toSet()),
    HEALTH("health", PlaceCategory.members(CategoryGroup.HEALTH).toSet()),
    SPORT_AND_LEISURE("sport", PlaceCategory.members(CategoryGroup.SPORT).toSet()),
    LODGING("lodging", PlaceCategory.members(CategoryGroup.LODGING).toSet()),
    ;

    init {
        require(stableId.matches(Regex("^[a-z_]{2,32}$")))
        require(categories.isNotEmpty())
    }

    companion object {
        fun fromStableId(value: String?): ExploreCategory? = entries.firstOrNull { it.stableId == value }
    }
}

/** Stable subcategory relationship; Compose reads it but never owns the taxonomy. */
object ExploreCatalog {
    const val VERSION = 1

    fun categories(parent: ExploreCategory): List<PlaceCategory> = parent.categories.sortedBy { it.name }
}
