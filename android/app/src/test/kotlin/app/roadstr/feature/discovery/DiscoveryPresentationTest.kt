package app.roadstr.feature.discovery

import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RankedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.time.OpenState
import app.roadstr.feature.map.NativeMapPointOverlayKind
import app.roadstr.feature.search.NativeSearchNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPresentationTest {
    private fun place(
        id: String = "osm:n:1",
        name: String = "Farmacia Centrale",
        category: PlaceCategory? = PlaceCategory.PHARMACY,
        address: String? = "Via Uno 3, Testville",
        tags: Map<String, String> = mapOf("amenity" to "pharmacy", "phone" to "+39 000"),
    ) = RankedPlace(
        RoadstrPlace(
            id = id, osm = null, name = name, category = category,
            position = GeoPoint(45.5, 9.5), address = address, distanceMeters = 250.0,
            openingHours = "Mo-Su 09:00-19:00", phone = "+39 000", website = null, cuisine = null,
            tags = tags, sources = setOf(PlaceSource.OPEN_STREET_MAP),
        ),
        score = 0.8,
        open = OpenState.OPEN,
    )

    @Test
    fun `a row shows the localised category and the address under the name`() {
        val row = DiscoveryPresentation.searchResult(place(), "it")
        assertEquals("Farmacia Centrale", row.shortName)
        assertEquals("Farmacia · Via Uno 3, Testville", row.categoryLabel)
        assertEquals("💊", row.emoji)
        assertEquals(250.0, row.distanceM!!, 0.0)
        assertEquals("Mo-Su 09:00-19:00", row.openingHours)
    }

    @Test
    fun `an unnamed place is titled by its category in the app language`() {
        val parking = place(name = "", category = PlaceCategory.PARKING, address = null)
        assertEquals("Parcheggio", DiscoveryPresentation.title(parking.place, "it"))
        assertEquals("Parking", DiscoveryPresentation.title(parking.place, "en"))
        assertEquals("Parcheggio", DiscoveryPresentation.searchResult(parking, "it").shortName)
    }

    @Test
    fun `a place of unknown kind still has a symbol`() {
        val other = place(category = null, address = null)
        assertEquals("📍", DiscoveryPresentation.searchResult(other, "en").emoji)
    }

    @Test
    fun `the most important notice wins`() {
        assertEquals(
            NativeSearchNotice.FewTagged,
            DiscoveryPresentation.notice(setOf(DiscoveryNotice.WIDENED, DiscoveryNotice.FEW_TAGGED)),
        )
        assertEquals(NativeSearchNotice.Widened, DiscoveryPresentation.notice(setOf(DiscoveryNotice.WIDENED)))
        assertEquals(
            NativeSearchNotice.OpenHoursUnknown,
            DiscoveryPresentation.notice(setOf(DiscoveryNotice.OPEN_HOURS_UNKNOWN)),
        )
        assertNull(DiscoveryPresentation.notice(emptySet()))
    }

    @Test
    fun `the route notice informs and gives way to the ones that change what to do`() {
        assertEquals(NativeSearchNotice.RouteAhead, DiscoveryPresentation.notice(setOf(DiscoveryNotice.ROUTE_AHEAD)))
        assertEquals(
            NativeSearchNotice.FewTagged,
            DiscoveryPresentation.notice(setOf(DiscoveryNotice.ROUTE_AHEAD, DiscoveryNotice.FEW_TAGGED)),
        )
        assertEquals(
            NativeSearchNotice.RouteUnsupported,
            DiscoveryPresentation.notice(setOf(DiscoveryNotice.ROUTE_UNSUPPORTED)),
        )
    }

    @Test
    fun `pins are discovery markers with ids that find the place again`() {
        val first = place("osm:n:1")
        val second = place("osm:w:2", name = "Other")
        val pins = DiscoveryPresentation.pins(listOf(first, second))
        assertEquals(listOf("discovery:osm:n:1", "discovery:osm:w:2"), pins.map { it.id })
        assertTrue(pins.all { it.kind == NativeMapPointOverlayKind.DiscoveryResult })
        assertEquals("Other", DiscoveryPresentation.placeForPin(pins[1].id, listOf(first, second))?.name)
        assertNull(DiscoveryPresentation.placeForPin("discovery:missing", listOf(first)))
    }

    @Test
    fun `a list row finds its place by position`() {
        val list = listOf(place())
        assertNotNull(DiscoveryPresentation.placeAt(45.5, 9.5, list))
        assertNull(DiscoveryPresentation.placeAt(45.6, 9.5, list))
    }

    @Test
    fun `the place sheet gets the details the map already holds`() {
        val details = DiscoveryPresentation.details(place().place, "it")
        assertEquals("+39 000", details?.phone)
    }
}
