package app.roadstr.feature.wikipedia

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWikipediaPresentationTest {
    @Test
    fun `policy admits standard and mobile Wikipedia articles`() {
        val admitted = listOf(
            "https://en.wikipedia.org/wiki/Rome",
            "https://it.m.wikipedia.org/wiki/Roma",
            "HTTPS://pt-br.wikipedia.org/wiki/S%C3%A3o_Paulo?oldid=1#Storia",
            "https://a-b.wikipedia.org/wiki/X",
        )

        admitted.forEach { value ->
            assertEquals(value, NativeWikipediaUriPolicy.parseAllowedArticle(value)?.toString())
        }
    }

    @Test
    fun `policy rejects transport authority and path escapes`() {
        val rejected = listOf(
            "http://en.wikipedia.org/wiki/Rome",
            "https://user@en.wikipedia.org/wiki/Rome",
            "https://en.wikipedia.org:443/wiki/Rome",
            "https://wikipedia.org/wiki/Rome",
            "https://en.wikipedia.org.evil.test/wiki/Rome",
            "https://en.wikipedia.org/w/index.php?title=Rome",
            "https://en.wikipedia.org/wiki/",
            "https://en.wikipedia.org/wiki",
            "not a URI",
        )

        rejected.forEach { assertNull(it, NativeWikipediaUriPolicy.parseAllowedArticle(it)) }
    }

    @Test
    fun `language label matches the Flutter bounds exactly`() {
        assertTrue(
            NativeWikipediaUriPolicy.isAllowedArticle(
                URI("https://${"a".repeat(24)}.wikipedia.org/wiki/X"),
            ),
        )
        assertFalse(
            NativeWikipediaUriPolicy.isAllowedArticle(
                URI("https://${"a".repeat(25)}.wikipedia.org/wiki/X"),
            ),
        )
        assertTrue(
            NativeWikipediaUriPolicy.isAllowedArticle(
                URI("https://EN.wikipedia.org/wiki/X"),
            ),
        )
        assertNull(NativeWikipediaUriPolicy.parseAllowedArticle("https://é.wikipedia.org/wiki/X"))
    }

    @Test
    fun `navigation is main-frame only and remains inside the article allowlist`() {
        val article = "https://en.wikipedia.org/wiki/Rome"

        assertTrue(NativeWikipediaUriPolicy.allowsNavigation(article, isMainFrame = true))
        assertFalse(NativeWikipediaUriPolicy.allowsNavigation(article, isMainFrame = false))
        assertFalse(
            NativeWikipediaUriPolicy.allowsNavigation(
                "https://commons.wikimedia.org/wiki/File:X.jpg",
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun `open validates input and projects bounded clean title`() {
        val session = NativeWikipediaSession()

        assertFalse(session.open(1, "https://example.org/wiki/X", "X"))
        assertFalse(session.open(1, "https://en.wikipedia.org/wiki/X", "\u0000\n"))
        assertTrue(
            session.open(
                1,
                "https://en.wikipedia.org/wiki/X",
                "  A\u0000B${"z".repeat(250)}  ",
            ),
        )

        val state = session.state.value
        assertEquals(NativeWikipediaStatus.Loading, state.status)
        assertEquals(200, state.title.length)
        assertTrue(state.title.startsWith("A B"))
        assertEquals(0, state.progress)
    }

    @Test
    fun `revisions reject duplicate stale and negative opens`() {
        val session = NativeWikipediaSession()
        assertTrue(session.open(4, "https://en.wikipedia.org/wiki/A", "A"))
        assertFalse(session.open(4, "https://en.wikipedia.org/wiki/B", "B"))
        assertFalse(session.open(3, "https://en.wikipedia.org/wiki/B", "B"))
        runCatching { session.open(-1, "https://en.wikipedia.org/wiki/B", "B") }
            .onSuccess { throw AssertionError("negative revision accepted") }
    }

    @Test
    fun `progress is clamped idempotent and revision fenced`() {
        val session = openSession(7)

        assertTrue(session.progress(7, 150))
        assertEquals(100, session.state.value.progress)
        assertFalse(session.progress(7, 101))
        assertFalse(session.progress(6, 20))
        assertTrue(session.progress(7, -5))
        assertEquals(0, session.state.value.progress)
    }

    @Test
    fun `page lifecycle accepts only allowed current destinations`() {
        val session = openSession(8)

        assertFalse(session.pageStarted(8, "https://example.org/wiki/B"))
        assertTrue(session.pageStarted(8, "https://it.wikipedia.org/wiki/Roma"))
        assertTrue(
            session.pageFinished(8, "https://it.wikipedia.org/wiki/Roma", canGoBack = true),
        )

        val state = session.state.value
        assertEquals(NativeWikipediaStatus.Ready, state.status)
        assertEquals(100, state.progress)
        assertTrue(state.canGoBack)
        assertEquals("it.wikipedia.org", state.currentUri?.host)
    }

    @Test
    fun `failure retries only the active failed revision`() {
        val session = openSession(9)

        assertFalse(session.retry(9))
        assertFalse(session.fail(8))
        assertTrue(session.fail(9))
        assertEquals(NativeWikipediaStatus.Failed, session.state.value.status)
        assertFalse(
            session.pageFinished(9, "https://en.wikipedia.org/wiki/A", canGoBack = false),
        )
        assertEquals(NativeWikipediaStatus.Failed, session.state.value.status)
        assertTrue(session.retry(9))
        assertEquals(NativeWikipediaStatus.Loading, session.state.value.status)
        assertEquals(0, session.state.value.progress)
        assertFalse(session.retry(9))
    }

    @Test
    fun `hide removes article state and invalidates late callbacks`() {
        val session = openSession(10)

        assertTrue(session.hide(10))
        assertEquals(NativeWikipediaStatus.Hidden, session.state.value.status)
        assertNull(session.state.value.originalUri)
        assertNull(session.state.value.currentUri)
        assertFalse(session.progress(10, 50))
        assertFalse(session.pageStarted(10, "https://en.wikipedia.org/wiki/B"))
        assertFalse(session.fail(10))
    }

    private fun openSession(revision: Long) = NativeWikipediaSession().also {
        assertTrue(it.open(revision, "https://en.wikipedia.org/wiki/A", "Article"))
    }
}
