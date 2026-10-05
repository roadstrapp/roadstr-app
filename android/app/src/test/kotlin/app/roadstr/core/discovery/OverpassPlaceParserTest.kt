package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassPlaceParserTest {
    private val origin = GeoPoint(45.0, 9.0)

    private val body = """
        {"elements":[
          {"type":"node","id":1,"lat":45.001,"lon":9.001,"tags":{
            "amenity":"restaurant","name":"Green Table","cuisine":"vegan;italian",
            "diet:vegan":"only","opening_hours":"Mo-Su 10:00-22:00","phone":"+39 000 000",
            "website":"https://green.example/menu","addr:street":"Via Uno","addr:housenumber":"3",
            "addr:city":"Testville","fixme":"drop me","note":"drop me too"}},
          {"type":"way","id":2,"center":{"lat":45.002,"lon":9.002},"tags":{
            "amenity":"parking","capacity":"40","fee":"yes"}},
          {"type":"node","id":3,"lat":45.003,"lon":9.003,"tags":{"shop":"bakery"}},
          {"type":"node","id":4,"lat":95.0,"lon":9.0,"tags":{"amenity":"cafe","name":"Nowhere"}},
          {"type":"node","id":5,"lat":45.004,"lon":9.004,"tags":{
            "amenity":"cafe","name":"Plain","website":"http://plain.example","name:it":"Semplice"}},
          {"type":"relation","id":-6,"lat":45.0,"lon":9.0,"tags":{"amenity":"cafe","name":"Bad id"}},
          {"type":"node","id":7,"lat":45.005,"lon":9.005}
        ]}
    """.trimIndent()

    private val places = OverpassPlaceParser.parse(body, origin, "en")

    @Test
    fun `named and normally unnamed places are kept, the rest dropped`() {
        assertEquals(listOf("osm:n:1", "osm:w:2", "osm:n:5"), places.map { it.id })
    }

    @Test
    fun `a restaurant keeps its useful facts`() {
        val place = places.first()
        assertEquals("Green Table", place.name)
        assertEquals(PlaceCategory.RESTAURANT, place.category)
        assertEquals("Mo-Su 10:00-22:00", place.openingHours)
        assertEquals("+39 000 000", place.phone)
        assertEquals("https://green.example/menu", place.website.toString())
        assertEquals("Via Uno 3, Testville", place.address)
        assertEquals("Vegan, Italian", place.cuisine)
        assertEquals(setOf(PlaceSource.OPEN_STREET_MAP), place.sources)
        assertTrue(place.distanceMeters!! in 100.0..200.0)
    }

    @Test
    fun `only whitelisted tags survive`() {
        val tags = places.first().tags
        assertTrue("diet:vegan" in tags)
        assertNull(tags["fixme"])
        assertNull(tags["note"])
    }

    @Test
    fun `a way is positioned by its centre and an unnamed parking keeps its category`() {
        val parking = places[1]
        assertEquals("", parking.name)
        assertEquals(PlaceCategory.PARKING, parking.category)
        assertEquals(45.002, parking.position.latitude, 0.0)
        assertEquals("40", parking.tags["capacity"])
    }

    @Test
    fun `an insecure website is dropped and a localised name wins`() {
        val cafe = OverpassPlaceParser.parse(body, origin, "it").last()
        assertNull(cafe.website)
        assertEquals("Semplice", cafe.name)
    }

    @Test
    fun `garbage does not throw`() {
        assertTrue(OverpassPlaceParser.parse("not json", origin, "en").isEmpty())
        assertTrue(OverpassPlaceParser.parse("{}", origin, "en").isEmpty())
        assertTrue(OverpassPlaceParser.parse("""{"elements":"x"}""", origin, "en").isEmpty())
    }

    @Test
    fun `a place never prints its position`() {
        assertEquals("RoadstrPlace(id=osm:n:1)", places.first().toString())
    }
}
