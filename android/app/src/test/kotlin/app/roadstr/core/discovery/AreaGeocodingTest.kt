package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaGeocodingTest {
    private val body = """
        [
          {"lat":"43.7696","lon":"11.2558","display_name":"Firenze, Toscana, Italia",
           "osm_type":"relation","osm_id":41485,"category":"boundary","type":"administrative",
           "importance":0.75,"boundingbox":["43.7266","43.8350","11.1544","11.3283"],
           "namedetails":{"name":"Firenze","name:en":"Florence","name:de":"Florenz"}},
          {"lat":"43.5","lon":"11.0","display_name":"Via Firenze, Altrove",
           "osm_type":"way","osm_id":99,"category":"highway","type":"residential","importance":0.2,
           "boundingbox":["43.49","43.51","10.99","11.01"],"namedetails":{"name":"Via Firenze"}},
          {"lat":"43.0","lon":"11.0","display_name":"Mario, Villaggio",
           "osm_type":"node","osm_id":55,"category":"place","type":"village","importance":0.1,
           "namedetails":{"name":"Mario"}}
        ]
    """.trimIndent()

    private val areas = AreaGeocoding.parse(body)

    @Test
    fun `the request is a polite single result search`() {
        val request = AreaGeocoding.request("San Giovanni", "it", GeoPoint(45.0, 9.0))!!
        assertTrue(request.uri.startsWith("https://nominatim.openstreetmap.org/search?q=San%20Giovanni&format=jsonv2"))
        assertTrue(request.uri.contains("limit=5"))
        assertTrue(request.uri.contains("namedetails=1"))
        assertTrue(request.uri.contains("accept-language=it"))
        assertTrue(request.uri.contains("viewbox="))
        assertTrue(request.headers.getValue("User-Agent").contains("roadstr"))
    }

    @Test
    fun `empty text and odd languages are handled`() {
        assertNull(AreaGeocoding.request("   ", "it", null))
        assertTrue(AreaGeocoding.request("Roma", "../x", null)!!.uri.contains("accept-language=en"))
        assertTrue(!AreaGeocoding.request("Roma", "it", null)!!.uri.contains("viewbox"))
    }

    @Test
    fun `the answer is read into areas`() {
        assertEquals(3, areas.size)
        val florence = areas.first()
        assertEquals(OsmRef(OsmElementType.RELATION, 41_485), florence.osm)
        assertEquals("boundary", florence.category)
        assertNotNull(florence.box)
        assertTrue("Florence" in florence.names)
    }

    @Test
    fun `a name typed in another language still finds the city`() {
        val chosen = AreaGeocoding.choose(areas, "Florence", strict = true)
        assertEquals(41_485L, chosen?.osm?.id)
        assertEquals(41_485L, AreaGeocoding.choose(areas, "Firenze", strict = false)?.osm?.id)
    }

    @Test
    fun `a street or a person is not taken for a city`() {
        assertNull(AreaGeocoding.choose(areas.drop(1).take(1), "Via Firenze", strict = false))
        assertNull(AreaGeocoding.choose(areas, "Marco", strict = true))
    }

    @Test
    fun `a typed word that only half matches is refused when strict`() {
        assertNull(AreaGeocoding.choose(areas, "Mari", strict = true))
        assertNotNull(AreaGeocoding.choose(areas, "Mario", strict = true))
    }

    @Test
    fun `a city becomes an administrative area with a box fallback`() {
        val area = AreaGeocoding.toSearchArea(areas.first())
        assertTrue(area is SearchArea.AdminArea)
        assertEquals(41_485L, (area as SearchArea.AdminArea).relationId)
        assertTrue(area.fallback is SearchArea.Box)
    }

    @Test
    fun `a village without a box becomes a circle`() {
        assertTrue(AreaGeocoding.toSearchArea(areas.last()) is SearchArea.Circle)
    }

    @Test
    fun `a huge region is searched as a circle, not by area`() {
        val region = areas.first().copy(box = SearchArea.Box(40.0, 8.0, 47.0, 14.0))
        assertTrue(AreaGeocoding.toSearchArea(region) is SearchArea.Circle)
    }

    @Test
    fun `garbage answers read as nothing`() {
        assertTrue(AreaGeocoding.parse("nope").isEmpty())
        assertTrue(AreaGeocoding.parse("""[{"lat":"x","lon":"y"}]""").isEmpty())
        assertTrue(AreaGeocoding.parse("""[{"lat":"91","lon":"0"}]""").isEmpty())
    }
}
