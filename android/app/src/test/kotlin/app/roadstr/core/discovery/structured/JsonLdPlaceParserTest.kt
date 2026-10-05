package app.roadstr.core.discovery.structured

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonLdPlaceParserTest {
    private fun parse(vararg blocks: String) = JsonLdPlaceParser.parse(blocks.toList())

    private val restaurant = """
        {"@context":"https://schema.org","@type":"Restaurant","name":"Trattoria Verde",
         "url":"https://www.trattoriaverde.it","telephone":"+39 045 1234567",
         "servesCuisine":["Italian","Steakhouse"],"hasMenu":"https://www.trattoriaverde.it/menu",
         "openingHours":["Mo-Sa 12:00-15:00","Mo-Sa 19:00-23:00","Sun closed"],
         "address":{"@type":"PostalAddress","streetAddress":"Via Mazzini 12","postalCode":"37121",
                    "addressLocality":"Verona","addressRegion":"VR","addressCountry":"IT"},
         "geo":{"@type":"GeoCoordinates","latitude":45.4384,"longitude":10.9916}}
    """.trimIndent()

    @Test
    fun `a restaurant is read field by field`() {
        val place = parse(restaurant).single()
        assertEquals("Trattoria Verde", place.name)
        assertEquals(listOf("Restaurant"), place.types)
        assertEquals(45.4384, place.position!!.latitude, 1e-9)
        assertEquals(10.9916, place.position!!.longitude, 1e-9)
        assertEquals("Via Mazzini 12", place.streetAddress)
        assertEquals("37121", place.postalCode)
        assertEquals("Verona", place.locality)
        assertEquals("IT", place.country)
        assertEquals("+39 045 1234567", place.phone)
        assertEquals("www.trattoriaverde.it", place.website!!.host)
        assertEquals("/menu", place.menu!!.path)
        assertEquals(listOf("Italian", "Steakhouse"), place.cuisine)
        assertEquals(listOf("Mo-Sa 12:00-15:00", "Mo-Sa 19:00-23:00"), place.openingHours)
    }

    @Test
    fun `a graph, a list and type urls are all understood`() {
        val graph = """{"@context":"https://schema.org","@graph":[{"@type":"WebSite","name":"Site"},
            {"@type":["Thing","http://schema.org/CafeOrCoffeeShop"],"name":"Bar Centrale","geo":{"latitude":"45,44","longitude":"10.99"}}]}"""
        val place = parse(graph).single()
        assertEquals("Bar Centrale", place.name)
        assertEquals(listOf("CafeOrCoffeeShop"), place.types)
        assertEquals(45.44, place.position!!.latitude, 1e-9)
        assertEquals(2, parse("[$restaurant,$restaurant]").size)
    }

    @Test
    fun `things that are not places are ignored`() {
        assertTrue(parse("""{"@type":"Recipe","name":"Pizza"}""", """{"@type":"Person","name":"A"}""", """{"@type":"Article"}""").isEmpty())
    }

    @Test
    fun `coordinates are range checked and zero zero means unknown`() {
        assertNull(parse("""{"@type":"Place","name":"A","geo":{"latitude":95,"longitude":10}}""").single().position)
        assertNull(parse("""{"@type":"Place","name":"A","geo":{"latitude":45,"longitude":200}}""").single().position)
        assertNull(parse("""{"@type":"Place","name":"A","geo":{"latitude":0,"longitude":0}}""").single().position)
        assertNull(parse("""{"@type":"Place","name":"A","geo":{"latitude":"abc","longitude":10}}""").single().position)
        assertNull(parse("""{"@type":"Place","name":"A","geo":{"latitude":"NaN","longitude":10}}""").single().position)
    }

    @Test
    fun `a page that names and locates nothing says nothing`() {
        assertTrue(parse("""{"@type":"Restaurant","telephone":"+39 045 1234567"}""").isEmpty())
    }

    @Test
    fun `only https addresses are kept`() {
        val place = parse("""{"@type":"Store","name":"S","url":"http://shop.example.org","hasMenu":"javascript:alert(1)"}""").single()
        assertNull(place.website)
        assertNull(place.menu)
        assertNull(parse("""{"@type":"Store","name":"S","url":"https://user:pw@shop.example.org"}""").single().website)
        assertNull(parse("""{"@type":"Store","name":"S","url":"https://shop.example.org:8443/"}""").single().website)
    }

    @Test
    fun `phone numbers and opening hours must look like phone numbers and hours`() {
        val place = parse("""{"@type":"Store","name":"S","telephone":"call <b>now</b>","openingHours":["whenever","Mo-Fr 9:00-18:00","Mo 99:00-18:00 and more words"]}""").single()
        assertNull(place.phone)
        assertEquals(listOf("Mo-Fr 9:00-18:00"), place.openingHours)
        assertNull(parse("""{"@type":"Store","name":"S","telephone":"12"}""").single().phone)
    }

    @Test
    fun `text is cleaned and clamped`() {
        val long = "x".repeat(500)
        val place = parse("""{"@type":"Store","name":"A\u0001B\n$long"}""").single()
        assertTrue(place.name!!.length <= 160)
        assertTrue(place.name!!.none { it.code < 0x20 })
    }

    @Test
    fun `damaged, oversized and deeply nested blocks are skipped without stopping the rest`() {
        val deep = "[".repeat(100) + "]".repeat(100)
        val huge = """{"@type":"Place","name":"${"a".repeat(JsonLdPlaceParser.MAX_BLOCK_CHARS)}"}"""
        val places = parse("not json", "{", deep, huge, "", restaurant)
        assertEquals(listOf("Trattoria Verde"), places.map { it.name })
    }

    @Test
    fun `the number of blocks and places is capped`() {
        val many = (1..20).map { """{"@type":"Place","name":"P$it"}""" }
        assertEquals(JsonLdPlaceParser.MAX_PLACES, JsonLdPlaceParser.parse(many).size)
        val ninth = List(JsonLdPlaceParser.MAX_BLOCKS) { "{}" } + restaurant
        assertTrue(JsonLdPlaceParser.parse(ninth).isEmpty())
        val graph = """{"@graph":[${(1..50).joinToString(",") { """{"@type":"Place","name":"G$it"}""" }}]}"""
        assertEquals(JsonLdPlaceParser.MAX_PLACES, parse(graph).size)
    }

    @Test
    fun `a hostile graph cannot run the parser out of time`() {
        val nested = (1..7).fold("""{"@type":"Place","name":"core"}""") { inner, _ -> """{"@graph":[$inner,$inner]}""" }
        val places = parse(nested)
        assertTrue(places.size <= JsonLdPlaceParser.MAX_PLACES)
    }

    @Test
    fun `values never print what the page said`() {
        assertEquals("StructuredPlace(types=[Restaurant])", parse(restaurant).single().toString())
    }
}
