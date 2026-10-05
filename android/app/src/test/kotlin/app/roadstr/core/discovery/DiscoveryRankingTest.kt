package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.time.OpenState
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryRankingTest {
    private val device = GeoPoint(45.0, 9.0)
    private val noon = LocalDateTime.of(2026, 10, 5, 12, 0)
    private val night = LocalDateTime.of(2026, 10, 5, 3, 0)

    private fun place(
        id: String,
        dLat: Double = 0.0,
        hours: String? = null,
        name: String = "Place $id",
        tags: Map<String, String> = emptyMap(),
    ) = RoadstrPlace(
        id = id, osm = null, name = name, category = PlaceCategory.RESTAURANT,
        position = GeoPoint(device.latitude + dLat, device.longitude), address = null,
        distanceMeters = null, openingHours = hours, phone = null, website = null, cuisine = null,
        tags = tags, sources = setOf(PlaceSource.OPEN_STREET_MAP),
    )

    private fun query(
        openNow: Boolean = false,
        attributes: Set<PlaceAttribute> = emptySet(),
        terms: List<String> = emptyList(),
    ) = NaturalPlaceQuery(
        rawText = "x", locale = "en", intent = QueryIntent.FIND_PLACE,
        categories = listOf(PlaceCategory.RESTAURANT), attributes = attributes, cuisines = emptySet(),
        location = LocationConstraint.CurrentLocation, locationExplicit = false, openNow = openNow,
        residualTerms = terms, residualPlaceGuess = null, classicQuery = "x",
    )

    private fun rank(places: List<RoadstrPlace>, query: NaturalPlaceQuery, now: LocalDateTime = noon) =
        DiscoveryRanking.rank(places, query, anchor = device, device = device, now = now)

    @Test
    fun `along a route the place that costs the smaller detour comes first and shows how far ahead it is`() {
        // The road runs north from the device along the meridian; one place is far ahead on the road,
        // the other is close to the device but 3 km to the side.
        val road = (0..20).map { GeoPoint(45.0 + it * 0.01, 9.0) }
        val onRoad = place("ahead", dLat = 0.15)
        val aside = place("aside").copy(position = GeoPoint(45.0, 9.04))
        val ranked = DiscoveryRanking.rank(listOf(aside, onRoad), query(), anchor = road[10], device = device, now = noon, route = road)
        assertEquals(listOf("ahead", "aside"), ranked.map { it.place.id })
        assertEquals(16_700.0, ranked.first().place.distanceMeters!!, 100.0)
    }

    @Test
    fun `without a route nothing changes`() {
        val ranked = DiscoveryRanking.rank(listOf(place("far", 0.05), place("near", 0.001)), query(), device, device, noon, route = null)
        assertEquals(listOf("near", "far"), ranked.map { it.place.id })
    }

    @Test
    fun `nearer places come first`() {
        val ranked = rank(listOf(place("far", 0.05), place("near", 0.001), place("mid", 0.01)), query())
        assertEquals(listOf("near", "mid", "far"), ranked.map { it.place.id })
    }

    @Test
    fun `ties are broken the same way every time`() {
        val ranked = rank(listOf(place("b"), place("a"), place("c")), query())
        assertEquals(listOf("a", "b", "c"), ranked.map { it.place.id })
    }

    @Test
    fun `open now drops the closed ones and keeps the unknown`() {
        val places = listOf(
            place("open", 0.003, "Mo-Su 08:00-20:00"),
            place("closed", 0.001, "Mo-Su 08:00-10:00"),
            place("unknown", 0.002),
        )
        val ranked = rank(places, query(openNow = true))
        assertEquals(setOf("open", "unknown"), ranked.map { it.place.id }.toSet())
        assertEquals(OpenState.OPEN, ranked.first { it.place.id == "open" }.open)
        assertEquals(OpenState.UNKNOWN, ranked.first { it.place.id == "unknown" }.open)
    }

    @Test
    fun `without the open now ask a closed place is only ranked lower`() {
        val ranked = rank(listOf(place("closed", 0.0, "Mo-Su 08:00-10:00")), query())
        assertEquals(OpenState.CLOSED, ranked.single().open)
    }

    @Test
    fun `a place far from the device is never judged by this clock`() {
        val farAway = place("far", 3.0, "Mo-Su 08:00-10:00")
        val ranked = rank(listOf(farAway), query(openNow = true))
        assertEquals(OpenState.UNKNOWN, ranked.single().open)
    }

    @Test
    fun `an explicit no for a requested attribute excludes the place`() {
        val ranked = rank(
            listOf(
                place("yes", tags = mapOf("diet:vegan" to "yes")),
                place("no", tags = mapOf("diet:vegan" to "no")),
            ),
            query(attributes = setOf(PlaceAttribute.VEGAN)),
        )
        assertEquals(listOf("yes"), ranked.map { it.place.id })
    }

    @Test
    fun `leftover words raise a place that mentions them`() {
        val ranked = rank(
            listOf(place("a", name = "Steakhouse Manzo"), place("b", name = "Pizza Roma")),
            query(terms = listOf("manzo")),
        )
        assertEquals("a", ranked.first().place.id)
    }

    @Test
    fun `the list is capped`() {
        val many = (1..60).map { place("p$it", it * 0.0001) }
        assertEquals(25, rank(many, query()).size)
    }

    @Test
    fun `the device distance is reported even when the anchor is elsewhere`() {
        val ranked = DiscoveryRanking.rank(
            listOf(place("p", 0.01)), query(), anchor = GeoPoint(50.0, 9.0), device = device, now = noon,
        )
        assertTrue(ranked.single().place.distanceMeters!! in 1000.0..1200.0)
    }

    @Test
    fun `at night an evening only place is closed`() {
        val ranked = rank(listOf(place("p", 0.0, "Mo-Su 18:00-23:00")), query(), night)
        assertEquals(OpenState.CLOSED, ranked.single().open)
    }
}
