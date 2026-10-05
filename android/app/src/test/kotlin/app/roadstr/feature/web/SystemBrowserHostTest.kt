package app.roadstr.feature.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBrowserHostTest {
    private val opened = mutableListOf<String>()
    private val host = SystemBrowserHost { opened += it }

    @Test
    fun `a web address goes to the user's browser`() {
        assertTrue(host.open(" https://example.org/menu "))
        assertEquals(listOf("https://example.org/menu"), opened)
    }

    @Test
    fun `plain http is handed over as it is, the browser has its own warning`() {
        assertTrue(host.open("http://example.org/old"))
        assertEquals(listOf("http://example.org/old"), opened)
    }

    @Test
    fun `an address the policy refuses is not opened anywhere`() {
        for (url in listOf("javascript:alert(1)", "intent://x#Intent;end", "https://localhost/", "https://192.168.0.2/", "tel:+39045123456", "", "file:///etc/hosts")) {
            assertFalse(url, host.open(url))
        }
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `it draws nothing in the app and never reports an open page`() {
        assertFalse(host.inApp)
        assertFalse(host.state.value.open)
        host.open("https://example.org/")
        assertFalse(host.state.value.open)
        host.close()
        assertEquals("WebBrowserState(open=false)", host.state.value.toString())
    }
}
