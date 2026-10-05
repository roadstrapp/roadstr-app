package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NominatimPlaceSearchTest {
    private val near = GeoPoint(45.44, 10.99)

    @Test
    fun `the request carries a name and a town, a viewbox bias and the project user agent`() {
        val request = NominatimPlaceSearch.request("Osteria Blu", "Verona", near, "it")!!
        assertTrue(request.uri.startsWith("https://nominatim.openstreetmap.org/search?q=Osteria%20Blu%20Verona&"))
        assertTrue(request.uri.contains("limit=3"))
        assertTrue(request.uri.contains("extratags=1"))
        assertTrue(request.uri.contains("accept-language=it"))
        assertTrue(request.uri.contains("viewbox="))
        assertTrue(request.headers.getValue("User-Agent").contains("github.com/roadstrapp"))
    }

    @Test
    fun `no name means no request, and a missing town is simply left out`() {
        assertNull(NominatimPlaceSearch.request("  ", "Verona", near, "it"))
        assertTrue(NominatimPlaceSearch.request("Osteria Blu", null, near, "it")!!.uri.contains("q=Osteria%20Blu&"))
    }

    @Test
    fun `an overlong name is cut and special characters are encoded`() {
        val request = NominatimPlaceSearch.request("A&B=C ".repeat(60), null, near, "xx!")!!
        assertFalse(request.uri.contains("A&B"))
        assertTrue(request.uri.contains("accept-language=en"))
        val q = request.uri.substringAfter("?q=").substringBefore("&format")
        assertTrue(q.length <= 400)
    }

    private val body = """
        [{"osm_type":"node","osm_id":4242,"lat":"45.4401","lon":"10.9902","category":"amenity","type":"restaurant",
          "name":"Osteria Blu","display_name":"Osteria Blu, Via Roma 5, Verona",
          "address":{"road":"Via Roma","house_number":"5","postcode":"37121","city":"Verona"},
          "extratags":{"website":"https://www.osteriablu.it","phone":"+39 045 111222","opening_hours":"Mo-Su 12:00-23:00","fixme":"x"}}]
    """.trimIndent()

    @Test
    fun `an answer becomes a place with an osm id, tags from the whitelist and two sources`() {
        val place = NominatimPlaceSearch.parse(body, near, "it").single()
        assertEquals("Osteria Blu", place.name)
        assertEquals(OsmElementType.NODE, place.osm!!.type)
        assertEquals(4242L, place.osm!!.id)
        assertEquals("osm:n:4242", place.id)
        assertEquals(PlaceCategory.RESTAURANT, place.category)
        assertEquals("www.osteriablu.it", place.website!!.host)
        assertEquals("+39 045 111222", place.phone)
        assertEquals("Via Roma", place.tags["addr:street"])
        assertEquals("5", place.tags["addr:housenumber"])
        assertFalse(place.tags.containsKey("fixme"))
        assertEquals(setOf(PlaceSource.GEOCODER, PlaceSource.OPEN_STREET_MAP), place.sources)
        assertTrue(place.distanceMeters!! < 200.0)
    }

    @Test
    fun `rows without a name, an osm id or a position are dropped`() {
        val rows = """
            [{"osm_type":"node","osm_id":1,"lat":"45.4","lon":"10.9","category":"amenity","type":"restaurant"},
             {"osm_type":"node","lat":"45.4","lon":"10.9","name":"A"},
             {"osm_type":"node","osm_id":2,"lat":"95","lon":"10.9","name":"B"},
             {"osm_type":"node","osm_id":3,"lat":"45.4","lon":"10.9","name":"C","category":"amenity","type":"cafe"}]
        """.trimIndent()
        assertEquals(listOf("C"), NominatimPlaceSearch.parse(rows, null, "en").map { it.name })
    }

    @Test
    fun `an insecure website is dropped and damaged answers give nothing`() {
        val insecure = body.replace("https://www.osteriablu.it", "http://www.osteriablu.it")
        assertNull(NominatimPlaceSearch.parse(insecure, near, "it").single().website)
        assertTrue(NominatimPlaceSearch.parse("not json", near, "it").isEmpty())
        assertTrue(NominatimPlaceSearch.parse("{}", near, "it").isEmpty())
        assertTrue(NominatimPlaceSearch.parse("[1,2,3]", near, "it").isEmpty())
    }

    @Test
    fun `at most three rows are read`() {
        val row = """{"osm_type":"node","osm_id":%d,"lat":"45.4","lon":"10.9","name":"N%d","category":"amenity","type":"cafe"}"""
        val rows = (1..6).joinToString(",", "[", "]") { row.format(it, it) }
        assertEquals(3, NominatimPlaceSearch.parse(rows, null, "en").size)
    }
}
