package app.roadstr.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCatalogTest {
    @Test
    fun `every category has at least one well formed alternative`() {
        for (category in PlaceCategory.entries) {
            assertTrue(category.name, category.alternatives.isNotEmpty())
            assertTrue(category.name, category.alternatives.all { it.isNotEmpty() })
            assertTrue(category.name, category.emoji.isNotEmpty())
        }
    }

    @Test
    fun `every nearby chip category of the classic search exists here`() {
        val chips = setOf(
            PlaceCategory.FUEL,
            PlaceCategory.RESTAURANT,
            PlaceCategory.SUPERMARKET,
            PlaceCategory.ATM,
            PlaceCategory.PHARMACY,
            PlaceCategory.HOSPITAL,
            PlaceCategory.POLICE,
            PlaceCategory.POST_OFFICE,
            PlaceCategory.PARKING,
            PlaceCategory.HOTEL,
            PlaceCategory.CHARGING_STATION,
        )
        assertTrue(PlaceCategory.entries.containsAll(chips))
    }

    @Test
    fun `groups list their members`() {
        assertEquals(
            listOf(PlaceCategory.RESTAURANT, PlaceCategory.FAST_FOOD, PlaceCategory.CAFE),
            PlaceCategory.members(CategoryGroup.EAT),
        )
        assertTrue(PlaceCategory.BAR in PlaceCategory.members(CategoryGroup.DRINK))
        assertTrue(PlaceCategory.HOTEL in PlaceCategory.members(CategoryGroup.LODGING))
    }

    @Test
    fun `tag matches reject anything that could break out of a query`() {
        assertFalse(runCatching { TagMatch("amenity\"", setOf("x")) }.isSuccess)
        assertFalse(runCatching { TagMatch("amenity", setOf("a\"];out;")) }.isSuccess)
        assertFalse(runCatching { TagMatch("amenity", regex = "a\";") }.isSuccess)
        assertFalse(runCatching { TagMatch("amenity", setOf("a"), regex = "b") }.isSuccess)
        assertTrue(runCatching { TagMatch("cuisine", regex = "(^|;)pizza(;|$)") }.isSuccess)
    }

    @Test
    fun `attributes name the tag that confirms them`() {
        assertEquals("diet:vegan", PlaceAttribute.VEGAN.key)
        assertTrue("only" in PlaceAttribute.VEGAN.values)
        assertEquals(PlaceCategory.FUEL, PlaceAttribute.LPG.impliedCategory)
        assertEquals(CategoryGroup.EAT, PlaceAttribute.GLUTEN_FREE.impliedGroup)
    }
}
