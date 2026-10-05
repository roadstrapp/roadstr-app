package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassDiscoveryQueryTest {
    private val center = GeoPoint(45.0, 9.0)

    @Test
    fun `a vegan restaurant search around a point`() {
        val query = OverpassDiscoveryQuery.build(
            categories = listOf(PlaceCategory.RESTAURANT),
            attributes = setOf(PlaceAttribute.VEGAN),
            cuisines = emptySet(),
            area = SearchArea.Circle(center, 5_000),
        )
        assertEquals(
            "[out:json][timeout:12];(nwr[\"amenity\"=\"restaurant\"][\"diet:vegan\"~\"^(only|yes)$\"]" +
                "(around:5000,45.000000,9.000000););out tags center 80;",
            query,
        )
    }

    @Test
    fun `a route corridor is one polyline with a buffer`() {
        val corridor = SearchArea.Corridor(
            listOf(GeoPoint(52.5163, 13.3777), GeoPoint(52.517, 13.3889), GeoPoint(52.5219, 13.4132)),
            bufferMeters = 300,
        )
        val query = OverpassDiscoveryQuery.build(
            listOf(PlaceCategory.RESTAURANT), emptySet(), emptySet(), corridor, limit = 3,
        )
        // The shape checked on a live mirror on 2026-10-05.
        assertEquals(
            "[out:json][timeout:12];(nwr[\"amenity\"=\"restaurant\"]" +
                "(around:300,52.516300,13.377700,52.517000,13.388900,52.521900,13.413200););out tags center 3;",
            query,
        )
    }

    @Test
    fun `a corridor needs two to twenty four points and a sane buffer`() {
        val a = GeoPoint(45.0, 9.0)
        val b = GeoPoint(45.1, 9.1)
        for (bad in listOf({ SearchArea.Corridor(listOf(a), 500) }, { SearchArea.Corridor(List(25) { a }, 500) },
            { SearchArea.Corridor(listOf(a, b), 50) }, { SearchArea.Corridor(listOf(a, b), 9_000) })) {
            assertTrue(runCatching { bad() }.isFailure)
        }
        assertTrue(runCatching { SearchArea.Corridor(listOf(a, b), 500) }.isSuccess)
    }

    @Test
    fun `an eat group becomes one statement per category`() {
        val query = OverpassDiscoveryQuery.build(
            PlaceCategory.members(CategoryGroup.EAT), setOf(PlaceAttribute.GLUTEN_FREE), emptySet(),
            SearchArea.Circle(center, 2_000),
        )
        assertEquals(3, Regex("nwr\\[").findAll(query).count())
        assertTrue(query.contains("\"diet:gluten_free\""))
    }

    @Test
    fun `an administrative area is addressed by its relation`() {
        val area = SearchArea.AdminArea(
            relationId = 41_485,
            center = center,
            fallback = SearchArea.Circle(center, 8_000),
        )
        val query = OverpassDiscoveryQuery.build(listOf(PlaceCategory.CINEMA), emptySet(), emptySet(), area)
        assertEquals(
            "[out:json][timeout:12];area(3600041485)->.a;(nwr[\"amenity\"=\"cinema\"](area.a););out tags center 80;",
            query,
        )
    }

    @Test
    fun `a box uses the south west north east order`() {
        val query = OverpassDiscoveryQuery.build(
            listOf(PlaceCategory.BANK), emptySet(), emptySet(),
            SearchArea.Box(44.9, 8.9, 45.1, 9.1),
        )
        assertTrue(query.contains("(44.900000,8.900000,45.100000,9.100000)"))
    }

    @Test
    fun `cuisines match the semicolon separated tag`() {
        val query = OverpassDiscoveryQuery.build(
            listOf(PlaceCategory.RESTAURANT), emptySet(), setOf(Cuisine.PIZZA),
            SearchArea.Circle(center, 3_000),
        )
        assertTrue(query.contains("[\"cuisine\"~\"(^|;)(pizza)(;|$)\"]"))
    }

    @Test
    fun `a multi valued category matches any of its values`() {
        val query = OverpassDiscoveryQuery.build(
            listOf(PlaceCategory.HOTEL), emptySet(), emptySet(), SearchArea.Circle(center, 3_000),
        )
        assertTrue(query.contains("[\"tourism\"~\"^(guest_house|hostel|hotel|motel)$\"]"))
    }

    @Test
    fun `a boolean attribute uses an exact value`() {
        val query = OverpassDiscoveryQuery.build(
            listOf(PlaceCategory.RESTAURANT), setOf(PlaceAttribute.OPEN_24_7), emptySet(),
            SearchArea.Circle(center, 3_000),
        )
        assertTrue(query.contains("[\"opening_hours\"=\"24/7\"]"))
    }

    @Test
    fun `every category and attribute produces a query made only of safe characters`() {
        val safe = Regex("^[\\[\\]A-Za-z0-9_\":=~^()|;.,/$> \\-]+$")
        for (category in PlaceCategory.entries) {
            val query = OverpassDiscoveryQuery.build(
                listOf(category), PlaceAttribute.entries.toSet(), Cuisine.entries.toSet(),
                SearchArea.Circle(center, 1_000),
            )
            assertTrue(category.name, safe.matches(query))
        }
    }

    @Test
    fun `invalid input is refused`() {
        assertFalse(runCatching { OverpassDiscoveryQuery.build(emptyList(), emptySet(), emptySet(), SearchArea.Circle(center, 1_000)) }.isSuccess)
        assertFalse(runCatching { SearchArea.Circle(center, 50) }.isSuccess)
        assertFalse(runCatching { SearchArea.Box(46.0, 9.0, 45.0, 10.0) }.isSuccess)
        assertFalse(runCatching { SearchArea.AdminArea(0, center, SearchArea.Circle(center, 1_000)) }.isSuccess)
        assertFalse(runCatching { SearchArea.Circle(GeoPoint(Double.NaN, 9.0), 1_000).let { OverpassDiscoveryQuery.build(listOf(PlaceCategory.BANK), emptySet(), emptySet(), it) } }.isSuccess)
    }
}
