package app.roadstr.core.discovery.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebDiscoverySettingsCodecTest {
    private val full = WebDiscoverySettings(
        mode = WebDiscoveryMode.ASK,
        endpointText = "https://search.example.org/base",
        ownInstanceConfirmed = true,
        strictSources = true,
        safeSearch = SafeSearch.STRICT,
    )

    @Test
    fun `settings survive a round trip`() {
        assertEquals(full, WebDiscoverySettingsCodec.decode(WebDiscoverySettingsCodec.encode(full)))
        val defaults = WebDiscoverySettings()
        assertEquals(defaults, WebDiscoverySettingsCodec.decode(WebDiscoverySettingsCodec.encode(defaults)))
    }

    @Test
    fun `a missing or damaged value means off with no instance`() {
        val defaults = WebDiscoverySettings()
        for (raw in listOf(null, "", "not json", "[]", "{\"mode\":", "null", "{\"mode\":\"on\"")) {
            assertEquals(raw, defaults, WebDiscoverySettingsCodec.decode(raw))
        }
    }

    @Test
    fun `an unknown mode or level falls back to the private choice`() {
        val decoded = WebDiscoverySettingsCodec.decode("{\"mode\":\"always\",\"safe\":9,\"own\":\"yes\"}")
        assertEquals(WebDiscoveryMode.OFF, decoded.mode)
        assertEquals(SafeSearch.MODERATE, decoded.safeSearch)
        assertFalse(decoded.ownInstanceConfirmed)
    }

    @Test
    fun `an oversized address or value is dropped, not trusted`() {
        val long = "https://" + "a".repeat(SearxngEndpointPolicy.MAX_CHARS) + ".org"
        val decoded = WebDiscoverySettingsCodec.decode("{\"mode\":\"on\",\"endpoint\":\"$long\"}")
        assertEquals("", decoded.endpointText)
        assertEquals(WebDiscoverySettings(), WebDiscoverySettingsCodec.decode("{\"mode\":\"on\"," + " ".repeat(3_000) + "}"))
    }

    @Test
    fun `quotes and control characters in an address cannot break the encoding`() {
        val odd = full.copy(endpointText = "https://x.example.org/a\"b\\c\u0001")
        val decoded = WebDiscoverySettingsCodec.decode(WebDiscoverySettingsCodec.encode(odd))
        assertEquals(odd.endpointText, decoded.endpointText)
    }
}
