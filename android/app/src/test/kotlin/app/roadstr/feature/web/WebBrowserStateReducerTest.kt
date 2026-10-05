package app.roadstr.feature.web

import app.roadstr.core.web.ExternalAction
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebBrowserStateReducerTest {
    private val reducer = WebBrowserStateReducer
    private val place = BrowserPagePlace(45.0, 9.0, "Trattoria Verde", linked = true)

    private fun page() = reducer.opened(URI("https://www.example.org/menu"))
        .let { reducer.location(it, "https://www.example.org/menu", "www.example.org") }
        .let { reducer.title(it, "Menu") }
        .let { reducer.history(it, canGoBack = true, canGoForward = false) }
        .let { reducer.loading(it, loading = false, failed = false) }
        .let { reducer.pagePlace(it, place) }

    @Test
    fun `opening shows the address and the lock for https`() {
        val state = reducer.opened(URI("https://www.example.org/menu"))
        assertTrue(state.open)
        assertTrue(state.loading)
        assertTrue(state.secure)
        assertEquals("www.example.org", state.host)
        assertFalse(reducer.opened(URI("http://old.example.org/")).secure)
    }

    @Test
    fun `a new load clears what the last page said about its place`() {
        assertNotNull(page().pagePlace)
        assertNull(reducer.loading(page(), loading = true, failed = false).pagePlace)
        // Finishing a load keeps it.
        assertNotNull(reducer.loading(page(), loading = false, failed = false).pagePlace)
    }

    @Test
    fun `moving to another site drops the place, moving inside the site keeps it`() {
        assertNotNull(reducer.location(page(), "https://www.example.org/other", "www.example.org").pagePlace)
        assertNull(reducer.location(page(), "https://elsewhere.example.net/", "elsewhere.example.net").pagePlace)
        assertEquals("www.example.org", reducer.location(page(), "about:blank", "").host)
    }

    @Test
    fun `a failed load is marked and a reload clears the mark`() {
        val failed = reducer.loading(page(), loading = false, failed = true)
        assertTrue(failed.failed)
        val again = reducer.reloading(failed)
        assertFalse(again.failed)
        assertTrue(again.loading)
    }

    @Test
    fun `when the engine dies the app stays on the page with a note and nothing the page said`() {
        val asked = reducer.asking(page(), ExternalAction.Dial("+390451234567"))
        val gone = reducer.engineGone(asked)
        assertTrue("the browser stays open so a retry can be offered", gone.open)
        assertEquals("https://www.example.org/menu", gone.url)
        assertTrue(gone.failed)
        assertFalse(gone.loading)
        assertFalse(gone.canGoBack)
        assertNull(gone.pagePlace)
        assertNull(gone.pendingAction)
        // And a reload from there starts again.
        val retry = reducer.reloading(gone)
        assertFalse(retry.failed)
        assertTrue(retry.loading)
    }

    @Test
    fun `a question is asked once and answered once`() {
        val asked = reducer.asking(page(), ExternalAction.UseAsDestination(45.0, 9.0))
        assertEquals(ExternalAction.UseAsDestination(45.0, 9.0), asked.pendingAction)
        assertNull(reducer.answered(asked).pendingAction)
    }

    @Test
    fun `the state never prints the page`() {
        val text = page().toString()
        assertEquals("WebBrowserState(open=true)", text)
        assertEquals("BrowserPagePlace(linked=true)", place.toString())
    }
}
