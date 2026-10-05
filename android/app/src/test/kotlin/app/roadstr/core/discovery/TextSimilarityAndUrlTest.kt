package app.roadstr.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSimilarityAndUrlTest {
    @Test
    fun `identical after folding scores one`() {
        assertEquals(1.0, TextSimilarity.score("Città", "CITTA"), 0.0)
        assertEquals(1.0, TextSimilarity.score("Москва", "москва"), 0.0)
    }

    @Test
    fun `a name inside another scores high, unrelated names low`() {
        assertEquals(0.9, TextSimilarity.score("Firenze", "Firenze Centro"), 0.0)
        assertTrue(TextSimilarity.score("Roma", "Milano") < 0.5)
        assertEquals(0.0, TextSimilarity.score("", "x"), 0.0)
    }

    @Test
    fun `one typo is still close`() {
        assertTrue(TextSimilarity.score("Trieste", "Triest") >= 0.85)
    }

    @Test
    fun `urls are percent encoded as utf8`() {
        assertEquals("San%20Giovanni", UrlEncoding.encode("San Giovanni"))
        assertEquals("%C3%A8%2F%3Fa%3D1", UrlEncoding.encode("è/?a=1"))
        assertEquals("a-b_c.d~e", UrlEncoding.encode("a-b_c.d~e"))
    }

    @Test
    fun `only plain https links are accepted`() {
        assertEquals("https://a.example/x", UrlEncoding.safeHttps("https://a.example/x").toString())
        assertNull(UrlEncoding.safeHttps("http://a.example"))
        assertNull(UrlEncoding.safeHttps("https://u:p@a.example"))
        assertNull(UrlEncoding.safeHttps("https://a.example:8443"))
        assertNull(UrlEncoding.safeHttps("javascript:alert(1)"))
        assertNull(UrlEncoding.safeHttps("https://" + "a".repeat(600)))
        assertNull(UrlEncoding.safeHttps("not a url"))
    }
}
