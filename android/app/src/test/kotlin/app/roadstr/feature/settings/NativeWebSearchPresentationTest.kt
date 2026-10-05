package app.roadstr.feature.settings

import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.EndpointRejection
import app.roadstr.core.discovery.web.SearxngCapability
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.feature.search.NativeWebProblem
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeWebSearchPresentationTest {
    private val start = WebDiscoverySettings()

    @Test
    fun `the defaults keep web search off with no instance`() {
        assertEquals(WebDiscoveryMode.OFF, start.mode)
        assertEquals("", start.endpointText)
        assertFalse(NativeWebSearchEditor.isActive(start))
    }

    @Test
    fun `a good https address is kept and shown only as its host`() {
        val edit = NativeWebSearchEditor.setEndpoint(start, "  https://search.example.org/search  ")
        assertNull(edit.rejection)
        assertEquals("https://search.example.org/search", edit.settings.endpointText)
        assertEquals("search.example.org", NativeWebSearchEditor.hostOf(edit.settings))
    }

    @Test
    fun `an address that can never work is refused and the old one stays`() {
        val saved = NativeWebSearchEditor.setEndpoint(start, "https://one.example.org").settings
        val cases = mapOf(
            "not a url" to EndpointRejection.INVALID,
            "http://public.example.org" to EndpointRejection.NOT_HTTPS,
            "https://user:pass@one.example.org" to EndpointRejection.CREDENTIALS,
            "https://one.example.org/?q=a" to EndpointRejection.QUERY_OR_FRAGMENT,
            "https://" + "a".repeat(250) + ".org" to EndpointRejection.TOO_LONG,
        )
        for ((text, reason) in cases) {
            val edit = NativeWebSearchEditor.setEndpoint(saved, text)
            assertEquals(text, reason, edit.rejection)
            assertEquals(saved, edit.settings)
        }
    }

    @Test
    fun `an empty box clears the address without a complaint`() {
        val saved = NativeWebSearchEditor.setEndpoint(start, "https://one.example.org").settings
        val edit = NativeWebSearchEditor.setEndpoint(saved, "   ")
        assertNull(edit.rejection)
        assertEquals("", edit.settings.endpointText)
    }

    @Test
    fun `plain http on a local network waits for the own instance box`() {
        val typed = NativeWebSearchEditor.setEndpoint(start, "http://192.168.1.20:8080")
        assertEquals(EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED, typed.rejection)
        assertEquals("http://192.168.1.20:8080", typed.settings.endpointText)
        assertNull(NativeWebSearchEditor.hostOf(typed.settings))

        val confirmed = NativeWebSearchEditor.setOwnInstance(typed.settings, true)
        assertNull(NativeWebSearchEditor.rejectionOf(confirmed))
        assertEquals("192.168.1.20", NativeWebSearchEditor.hostOf(confirmed))

        val withdrawn = NativeWebSearchEditor.setOwnInstance(confirmed, false)
        assertEquals(EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED, NativeWebSearchEditor.rejectionOf(withdrawn))
    }

    @Test
    fun `web search is active only when switched on and pointed at a usable instance`() {
        val withAddress = NativeWebSearchEditor.setEndpoint(start, "https://one.example.org").settings
        assertFalse(NativeWebSearchEditor.isActive(withAddress))
        assertTrue(NativeWebSearchEditor.isActive(NativeWebSearchEditor.setMode(withAddress, WebDiscoveryMode.ASK)))
        assertFalse(NativeWebSearchEditor.isActive(NativeWebSearchEditor.setMode(start, WebDiscoveryMode.ON)))
    }

    @Test
    fun `the settings never print the address`() {
        val saved = NativeWebSearchEditor.setEndpoint(start, "https://private.example.org").settings
        assertFalse(saved.toString().contains("private"))
    }

    @Test
    fun `a connection test is turned into one line for the screen`() {
        val host = "one.example.org"
        assertEquals(NativeWebSearchStatus.NotConfigured, ConnectionTest.NotConfigured.toStatus())
        assertEquals(
            NativeWebSearchStatus.Rejected(EndpointRejection.INVALID),
            ConnectionTest.Rejected(EndpointRejection.INVALID).toStatus(),
        )
        assertEquals(
            NativeWebSearchStatus.Works,
            ConnectionTest.Tested(SearxngCapability.Compatible(null, null), host).toStatus(),
        )
        val failures = mapOf(
            SearxngCapability.JsonDisabled to NativeWebProblem.JsonDisabled,
            SearxngCapability.NotSearxng to NativeWebProblem.NotSearxng,
            SearxngCapability.NoEngineList to NativeWebProblem.NoEngineList,
            SearxngCapability.Unreachable to NativeWebProblem.Unreachable,
            SearxngCapability.RateLimited(5) to NativeWebProblem.RateLimited,
        )
        for ((capability, problem) in failures) {
            assertEquals(NativeWebSearchStatus.Failed(problem), ConnectionTest.Tested(capability, host).toStatus())
        }
    }

    @Test
    fun `a test that throws reads as unreachable, a cancelled one is not swallowed`() = runBlocking {
        val thrown = runWebConnectionTest { throw IOException("down") }
        assertEquals(NativeWebSearchStatus.Failed(NativeWebProblem.Unreachable), thrown)
        try {
            runWebConnectionTest { throw CancellationException("stop") }
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
