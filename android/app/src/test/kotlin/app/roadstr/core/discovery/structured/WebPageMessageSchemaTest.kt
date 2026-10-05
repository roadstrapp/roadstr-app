package app.roadstr.core.discovery.structured

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPageMessageSchemaTest {
    private fun parse(raw: String) = WebPageMessageSchema.parse(raw)

    @Test
    fun `a well formed message is read`() {
        val message = parse(
            """{"v":1,"url":"https://www.example.org/menu","jsonLd":["{\"@type\":\"Place\"}"],
                "og":{"og:title":"Trattoria Verde","og:latitude":"45.43","something:else":"ignored"}}""",
        )!!
        assertEquals("www.example.org", message.url.host)
        assertEquals(1, message.jsonLd.size)
        assertEquals(mapOf("og:title" to "Trattoria Verde", "og:latitude" to "45.43"), message.meta)
    }

    @Test
    fun `anything that does not fit is dropped whole`() {
        for (raw in listOf(
            "", "not json", "[]", "null", """{"url":"https://example.org"}""",
            """{"v":2,"url":"https://example.org"}""", """{"v":1}""", """{"v":1,"url":"http://example.org"}""",
            """{"v":1,"url":"https://user:pw@example.org"}""", """{"v":1,"url":"javascript:alert(1)"}""",
            """{"v":"1","url":"https://example.org"}""",
        )) {
            assertNull(raw, parse(raw))
        }
    }

    @Test
    fun `a message over 64 KiB is refused`() {
        val big = """{"v":1,"url":"https://example.org","jsonLd":["${"a".repeat(WebPageMessageSchema.MAX_BYTES)}"]}"""
        assertNull(parse(big))
    }

    @Test
    fun `blocks that are not text are dropped, the rest kept and capped`() {
        val many = (1..12).joinToString(",") { "\"b$it\"" }
        val message = parse("""{"v":1,"url":"https://example.org","jsonLd":[1,{"a":1},"ok",$many]}""")!!
        assertEquals(JsonLdPlaceParser.MAX_BLOCKS, message.jsonLd.size)
        assertEquals("ok", message.jsonLd.first())
    }

    @Test
    fun `only the known meta tags are kept and their values are clamped`() {
        val message = parse("""{"v":1,"url":"https://example.org","og":{"og:title":"${"t".repeat(400)}","og:image":"https://x","onerror":"x"}}""")!!
        assertEquals(setOf("og:title"), message.meta.keys)
        assertTrue(message.meta.getValue("og:title").length <= WebPageMessageSchema.MAX_META_VALUE_CHARS)
        assertTrue(parse("""{"v":1,"url":"https://example.org","og":[1,2]}""")!!.meta.isEmpty())
    }

    @Test
    fun `values never print the page`() {
        assertEquals("WebPageMessage(blocks=0)", parse("""{"v":1,"url":"https://example.org"}""")!!.toString())
    }
}
