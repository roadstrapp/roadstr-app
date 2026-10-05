package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.OsmRef
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.structured.JsonLdPlaceParser
import app.roadstr.core.discovery.structured.WebPageMessage
import app.roadstr.core.geo.GeoPoint
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMatcherTest {
    private val center = GeoPoint(45.4384, 10.9916)
    private val context = ResolveContext(center, 5_000.0, "Verona", "it", setOf(PlaceCategory.RESTAURANT))

    private fun known(
        id: Long,
        name: String,
        site: String? = null,
        phone: String? = null,
        at: GeoPoint = GeoPoint(45.4385, 10.9917),
    ) = RoadstrPlace(
        id = "osm:n:$id", osm = OsmRef(OsmElementType.NODE, id), name = name, category = PlaceCategory.RESTAURANT,
        position = at, address = null, distanceMeters = null, openingHours = null, phone = phone,
        website = site?.let(::URI), cuisine = null, tags = mapOf("name" to name),
        sources = setOf(PlaceSource.OPEN_STREET_MAP),
    )

    private fun page(url: String = "https://www.trattoriaverde.it/menu", meta: Map<String, String> = emptyMap()) =
        WebPageMessage(URI(url), emptyList(), meta)

    private fun claim(json: String) = JsonLdPlaceParser.parse(listOf(json))

    private val verdeJson = """{"@type":"Restaurant","name":"Trattoria Verde","telephone":"+39 045 1234567",
        "url":"https://www.trattoriaverde.it","address":{"streetAddress":"Via Mazzini 12","postalCode":"37121","addressLocality":"Verona"},
        "openingHours":["Mo-Sa 12:00-15:00"],"servesCuisine":"Steakhouse","geo":{"latitude":45.4384,"longitude":10.9916}}"""

    @Test
    fun `a page that matches a known place by name, position and website is linked to it`() {
        val verde = known(1, "Trattoria Verde", site = "https://www.trattoriaverde.it")
        val match = PageMatcher.match(page(), claim(verdeJson), listOf(verde), context)!!
        assertEquals(MatchClass.LINKED, match.matchClass)
        assertEquals(verde, match.place)
        assertFalse(match.isNew)
        assertEquals(setOf(PlaceSource.OPEN_STREET_MAP, PlaceSource.WEBSITE), match.provenance)
        assertTrue(match.evidence.any { it.kind == EvidenceKind.WEBSITE_HOST })
    }

    @Test
    fun `name and position alone link when the place is right there`() {
        val verde = known(1, "Trattoria Verde")
        val match = PageMatcher.match(page("https://blog.example.org/verde"), claim(verdeJson), listOf(verde), context)!!
        assertEquals(MatchClass.LINKED, match.matchClass)
        assertEquals(verde, match.place)
    }

    @Test
    fun `a phone number that agrees links a place with another name`() {
        val renamed = known(2, "Osteria Verde", phone = "+39 045 1234567")
        val match = PageMatcher.match(page("https://blog.example.org/x"), claim(verdeJson), listOf(renamed), context)!!
        assertEquals(renamed, match.place)
        assertTrue(match.evidence.any { it.kind == EvidenceKind.PHONE })
        assertEquals(MatchClass.LINKED, match.matchClass)
    }

    @Test
    fun `a different business at the same spot is not merged`() {
        val other = known(3, "Farmacia Centrale")
        val match = PageMatcher.match(page("https://blog.example.org/x"), claim(verdeJson), listOf(other), context)!!
        assertTrue(match.isNew)
        assertEquals("Trattoria Verde", match.place.name)
        assertTrue(match.place.osm == null)
    }

    @Test
    fun `a place built only from the page is a candidate, never linked, and carries what the page said`() {
        val match = PageMatcher.match(page(), claim(verdeJson), emptyList(), context)!!
        assertTrue(match.isNew)
        assertEquals(MatchClass.CANDIDATE, match.matchClass)
        assertEquals(PlaceCategory.RESTAURANT, match.place.category)
        assertEquals("Via Mazzini 12, 37121, Verona", match.place.address)
        assertEquals("Mo-Sa 12:00-15:00", match.place.openingHours)
        assertEquals("steakhouse", match.place.tags["cuisine"])
        assertEquals(setOf(PlaceSource.WEBSITE), match.place.sources)
        assertTrue(match.place.id.startsWith("geo:"))
    }

    @Test
    fun `coordinates outside the search area are ignored`() {
        val far = """{"@type":"Restaurant","name":"Far Away","geo":{"latitude":48.85,"longitude":2.35}}"""
        assertNull(PageMatcher.match(page(), claim(far), emptyList(), context))
        val nearEnough = """{"@type":"Restaurant","name":"Close","geo":{"latitude":45.45,"longitude":10.99}}"""
        assertNotNull(PageMatcher.match(page(), claim(nearEnough), emptyList(), context))
    }

    @Test
    fun `without a radius a generous default applies`() {
        val unbounded = context.copy(radiusMeters = 0.0)
        val town = """{"@type":"Restaurant","name":"Town","geo":{"latitude":45.60,"longitude":11.1}}"""
        assertNotNull(PageMatcher.match(page(), claim(town), emptyList(), unbounded))
        val country = """{"@type":"Restaurant","name":"Country","geo":{"latitude":47.0,"longitude":12.0}}"""
        assertNull(PageMatcher.match(page(), claim(country), emptyList(), unbounded))
    }

    @Test
    fun `meta tags alone can name a point`() {
        val meta = mapOf("og:title" to "Bar Centrale", "og:latitude" to "45,4390", "og:longitude" to "10.9920")
        assertNull(PageMatcher.match(page("https://bar.example.org/"), emptyList(), emptyList(), context))
        val withMeta = PageMatcher.match(page("https://bar.example.org/", meta), emptyList(), emptyList(), context)!!
        assertEquals("Bar Centrale", withMeta.place.name)
        assertEquals(10.992, withMeta.place.position.longitude, 1e-9)
        assertEquals(MatchClass.CANDIDATE, withMeta.matchClass)
    }

    @Test
    fun `a page with no usable point gives nothing`() {
        assertNull(PageMatcher.match(page(), claim("""{"@type":"Restaurant","name":"No point"}"""), emptyList(), context))
        val zero = mapOf("og:latitude" to "0", "og:longitude" to "0")
        assertNull(PageMatcher.match(page(meta = zero), emptyList(), emptyList(), context))
    }

    @Test
    fun `a listing page links by name and position, and its host proves nothing`() {
        val verde = known(1, "Trattoria Verde", site = "https://www.trattoriaverde.it")
        val noSite = claim("""{"@type":"Restaurant","name":"Trattoria Verde","geo":{"latitude":45.4384,"longitude":10.9916}}""")
        val match = PageMatcher.match(page("https://www.tripadvisor.it/Restaurant_Review-1"), noSite, listOf(verde), context)!!
        assertEquals(verde, match.place)
        assertEquals(MatchClass.LINKED, match.matchClass)
        assertTrue(match.evidence.none { it.kind == EvidenceKind.WEBSITE_HOST })
    }

    @Test
    fun `the schema types map to categories`() {
        assertEquals(PlaceCategory.PHARMACY, PageMatcher.categoryOf(listOf("Thing", "Pharmacy")))
        assertEquals(PlaceCategory.FUEL, PageMatcher.categoryOf(listOf("GasStation")))
        assertNull(PageMatcher.categoryOf(listOf("LocalBusiness")))
    }

    @Test
    fun `values never print the place`() {
        val match = PageMatcher.match(page(), claim(verdeJson), emptyList(), context)!!
        assertFalse(match.toString().contains("Verde"))
    }
}
