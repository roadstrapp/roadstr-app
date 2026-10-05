package app.roadstr.core.discovery.web

import app.roadstr.core.discovery.AreaGeocoding
import app.roadstr.core.discovery.NaturalQueryParser
import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebQueryBuilderTest {
    private val parser = NaturalQueryParser()
    private val device = GeoPoint(45.123456, 9.654321)
    private val destination = GeoPoint(46.5, 10.5)

    private fun context(text: String, locale: String = "en") = WebSearchContext(
        parser.interpret(text, locale), device, destination, locale,
    )

    @Test
    fun `near me gets the town, not the position`() {
        val context = context("vegan steak house near me")
        assertEquals(device, WebQueryBuilder.localityPoint(context))
        assertEquals("vegan steak house Testville", WebQueryBuilder.build(context.parsed, "Testville"))
    }

    @Test
    fun `a typed place already says where, so nothing is added`() {
        val context = context("bistecche di manzo a Trieste", "it")
        assertNull(WebQueryBuilder.localityPoint(context))
        assertEquals("bistecche di manzo a Trieste", WebQueryBuilder.build(context.parsed, null))
    }

    @Test
    fun `near the destination uses the destination's town`() {
        val context = context("parking near my destination")
        assertEquals(destination, WebQueryBuilder.localityPoint(context))
    }

    @Test
    fun `along a route no town is added`() {
        assertNull(WebQueryBuilder.localityPoint(context("petrol station along the route")))
    }

    @Test
    fun `a name search is not told a town`() {
        assertNull(WebQueryBuilder.localityPoint(context("Esselunga")))
    }

    @Test
    fun `the near me words are removed from the text`() {
        val context = context("vegano nei dintorni", "it")
        assertEquals("vegano Testville", WebQueryBuilder.build(context.parsed, "Testville"))
    }

    @Test
    fun `the query never contains a coordinate and is capped`() {
        val context = context("pizza near me")
        val text = WebQueryBuilder.build(context.parsed, "Testville")
        assertFalse(text.contains("45.1"))
        assertFalse(text.contains("9.65"))
        val long = parser.interpret("pizza ".repeat(100), "en")
        assertTrue(WebQueryBuilder.build(long, "Testville").length <= WebQueryBuilder.MAX_CHARS)
    }

    @Test
    fun `the context does not print its contents`() {
        assertEquals("WebSearchContext", context("pizza").toString())
    }

    @Test
    fun `the locality request is rounded to about a kilometre`() {
        val request = AreaGeocoding.localityRequest(device, "it")
        assertTrue(request.uri.contains("lat=45.12&lon=9.65"))
        assertTrue(request.uri.contains("zoom=10"))
        assertTrue(request.uri.contains("accept-language=it"))
        assertFalse(request.uri.contains("45.123"))
        assertEquals("45.12,9.65", AreaGeocoding.localityCell(device))
        assertEquals("-33.87,151.21", AreaGeocoding.localityCell(GeoPoint(-33.8688, 151.2093)))
    }

    @Test
    fun `the town is read from the address`() {
        assertEquals("Testville", AreaGeocoding.parseLocality("""{"address":{"town":"Testville","county":"X"}}"""))
        assertEquals("Metro", AreaGeocoding.parseLocality("""{"address":{"city":"Metro"}}"""))
        assertEquals("Hamlet", AreaGeocoding.parseLocality("""{"address":{"hamlet":"Hamlet"}}"""))
        assertNull(AreaGeocoding.parseLocality("""{"address":{"road":"Via Uno"}}"""))
        assertNull(AreaGeocoding.parseLocality("nope"))
        assertNull(AreaGeocoding.parseLocality("{}"))
    }
}
