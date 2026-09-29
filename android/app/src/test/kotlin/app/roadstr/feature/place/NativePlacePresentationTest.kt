package app.roadstr.feature.place

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.search.OsmPlaceDetailsProtocol
import java.net.URI
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlacePresentationTest {
    private val point = SearchResponsePoint(45.07031, 7.68686)
    private val monday = LocalDateTime.of(2024, 1, 1, 10, 0)

    @Test
    fun `article projection bounds text and admits only safe links`() {
        val article = requireNotNull(
            NativePlacePresenter.article(
                NativePlaceArticleInput(
                    title = "  Mole Antonelliana  ",
                    extract = "x".repeat(1_200),
                    imageUrl = "https://upload.wikimedia.org/image.jpg",
                    pageUrl = "https://it.wikipedia.org/wiki/Mole_Antonelliana",
                ),
            ),
        )

        assertEquals("Mole Antonelliana", article.title)
        assertEquals(1_000, article.extract.length)
        assertEquals("upload.wikimedia.org", article.imageUrl?.host)
        assertEquals("it.wikipedia.org", article.pageUrl?.host)
        assertNull(
            NativePlacePresenter.article(
                NativePlaceArticleInput("Title", "Extract", pageUrl = "https://example.test/wiki"),
            )?.pageUrl,
        )
        assertNull(
            NativePlacePresenter.article(
                NativePlaceArticleInput("Title", "Extract", imageUrl = "http://example.test/image"),
            )?.imageUrl,
        )
    }

    @Test
    fun `title priority is article details address and coordinates`() {
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(mapOf("tourism" to "attraction", "name" to "OSM name")),
        )
        val article = NativePlaceArticle("Article", "", null, null)

        assertEquals("Article", NativePlacePresenter.title(point, "Address", article, details))
        assertEquals("OSM name", NativePlacePresenter.title(point, "Address", null, details))
        assertEquals("Address", NativePlacePresenter.title(point, "Address", null, null))
        assertEquals("45.07031, 7.68686", NativePlacePresenter.title(point, null, null, null))
    }

    @Test
    fun `opening presentation reports state and localized next change`() {
        val open = requireNotNull(
            NativePlacePresenter.opening("Mo-Fr 08:00-18:00", monday, Locale.ENGLISH),
        )
        assertEquals(NativePlaceOpeningState.Open, open.state)
        assertEquals("18:00", open.changeLabel)

        val saturday = LocalDateTime.of(2024, 1, 6, 10, 0)
        val closed = requireNotNull(
            NativePlacePresenter.opening("Mo-Fr 08:00-18:00", saturday, Locale.ENGLISH),
        )
        assertEquals(NativePlaceOpeningState.Closed, closed.state)
        assertEquals("Mon 08:00", closed.changeLabel)

        val unknown = requireNotNull(NativePlacePresenter.opening("sunrise-sunset", monday, Locale.ENGLISH))
        assertEquals(NativePlaceOpeningState.Unknown, unknown.state)
        assertNull(unknown.changeLabel)
    }

    @Test
    fun `begin validates WGS84 bounds and fences revisions`() {
        val session = NativePlaceSession(monday, Locale.ENGLISH)

        assertTrue(session.begin(2, point, "  Via Roma 1  "))
        assertEquals(NativePlaceUiStatus.Loading, session.state.value.status)
        assertEquals("Via Roma 1", session.state.value.title)
        assertFalse(session.begin(2, point))
        assertFalse(session.begin(1, point))
        assertThrows(IllegalArgumentException::class.java) {
            session.begin(3, SearchResponsePoint(91.0, 0.0))
        }
    }

    @Test
    fun `submit accepts only current loading work and publishes bounded content`() {
        val session = NativePlaceSession(monday, Locale.ENGLISH)
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "amenity" to "restaurant",
                    "name" to "OSM name",
                    "opening_hours" to "Mo-Fr 08:00-18:00",
                ),
            ),
        )
        assertTrue(session.begin(4, point))
        assertFalse(session.submit(3, details))
        assertTrue(
            session.submit(
                revision = 4,
                details = details,
                article = NativePlaceArticleInput(
                    "Article title",
                    "Extract",
                    pageUrl = "https://en.wikipedia.org/wiki/Article",
                ),
                address = "Address",
                wikiQuery = "  Article title Torino  ",
            ),
        )

        val ready = session.state.value
        assertEquals(NativePlaceUiStatus.Ready, ready.status)
        assertEquals("Article title", ready.title)
        assertEquals("Article title Torino", ready.wikiQuery)
        assertEquals(URI("https://en.wikipedia.org/wiki/Article"), ready.article?.pageUrl)
        assertEquals(NativePlaceOpeningState.Open, ready.opening?.state)
        assertFalse(session.submit(4, details))
    }

    @Test
    fun `clock updates reproject opening state without changing revision`() {
        val session = NativePlaceSession(monday, Locale.ENGLISH)
        assertTrue(session.begin(1, point))
        assertTrue(session.submit(1, openingHours = "Mo-Fr 08:00-18:00"))
        assertEquals(NativePlaceOpeningState.Open, session.state.value.opening?.state)

        assertTrue(session.updateClock(monday.withHour(20), Locale.ENGLISH))
        assertEquals(1, session.state.value.revision)
        assertEquals(NativePlaceOpeningState.Closed, session.state.value.opening?.state)
        assertFalse(session.updateClock(monday.withHour(20), Locale.ENGLISH))
    }

    @Test
    fun `hide invalidates late callbacks and retains monotonic fence`() {
        val session = NativePlaceSession(monday, Locale.ENGLISH)
        assertTrue(session.begin(5, point))
        assertTrue(session.hide(5))
        assertEquals(NativePlaceUiStatus.Hidden, session.state.value.status)
        assertFalse(session.submit(5))
        assertFalse(session.hide(4))
        assertTrue(session.hide(6))
        assertFalse(session.begin(6, point))
        assertTrue(session.begin(7, point))
    }
}
