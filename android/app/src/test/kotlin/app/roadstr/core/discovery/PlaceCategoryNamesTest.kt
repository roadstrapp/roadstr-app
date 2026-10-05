package app.roadstr.core.discovery

import app.roadstr.core.discovery.lexicon.LexiconRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCategoryNamesTest {
    @Test
    fun `names come from the language's own vocabulary`() {
        assertEquals("Farmacia", PlaceCategoryNames.of(PlaceCategory.PHARMACY, "it"))
        assertEquals("Apotheke", PlaceCategoryNames.of(PlaceCategory.PHARMACY, "de"))
        assertEquals("Pharmacie", PlaceCategoryNames.of(PlaceCategory.PHARMACY, "fr-CA"))
        assertEquals("Pharmacy", PlaceCategoryNames.of(PlaceCategory.PHARMACY, "tlh"))
    }

    @Test
    fun `a category a language lacks falls back to english`() {
        assertEquals("Fire station", PlaceCategoryNames.of(PlaceCategory.FIRE_STATION, "ga"))
    }

    @Test
    fun `every core category has a full word, not a stem, in every language`() {
        val core = listOf(
            PlaceCategory.RESTAURANT, PlaceCategory.CAFE, PlaceCategory.SUPERMARKET,
            PlaceCategory.PHARMACY, PlaceCategory.FUEL, PlaceCategory.CHARGING_STATION,
            PlaceCategory.PARKING, PlaceCategory.HOTEL, PlaceCategory.HOSPITAL, PlaceCategory.ATM,
            PlaceCategory.BANK, PlaceCategory.POST_OFFICE, PlaceCategory.POLICE,
            PlaceCategory.CINEMA, PlaceCategory.TRAIN_STATION,
        )
        for (language in LexiconRegistry.locales) {
            val own = LexiconRegistry.ownLexicon(language).entries
            for (category in core) {
                val first = own.firstOrNull { it.id == category.name && it.kind.key == "c" }
                assertTrue("$language $category has no full word", first != null && !first.prefix)
            }
        }
    }
}
