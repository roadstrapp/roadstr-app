package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryDeduplicatorTest {
    private fun place(
        id: String,
        name: String,
        longitude: Double,
        osm: OsmRef? = null,
        address: String? = null,
        source: PlaceSource = PlaceSource.OPEN_STREET_MAP,
    ) = RoadstrPlace(
        id = id,
        osm = osm,
        name = name,
        category = PlaceCategory.MUSICAL_INSTRUMENTS,
        position = GeoPoint(43.7696, longitude),
        address = address,
        distanceMeters = null,
        openingHours = null,
        phone = null,
        website = null,
        cuisine = null,
        tags = mapOf("shop" to "musical_instrument"),
        sources = setOf(source),
    )

    @Test
    fun `stable osm id merges provider copies and keeps their provenance`() {
        val osm = OsmRef(OsmElementType.NODE, 42)
        val merged = DiscoveryDeduplicator.merge(
            listOf(
                place("one", "Piano Firenze", 11.2558, osm, source = PlaceSource.OPEN_STREET_MAP),
                place("two", "Piano Firenze", 11.2558, osm, address = "Via Roma", source = PlaceSource.GEOCODER),
            ),
        ).single()
        assertEquals("Via Roma", merged.address)
        assertEquals(setOf(PlaceSource.OPEN_STREET_MAP, PlaceSource.GEOCODER), merged.sources)
    }

    @Test
    fun `same address does not merge distinct businesses`() {
        val values = DiscoveryDeduplicator.merge(
            listOf(
                place("one", "Piano Firenze", 11.2558, address = "Via Roma"),
                place("two", "Music House", 11.2558, address = "Via Roma"),
            ),
        )
        assertEquals(2, values.size)
    }
}
