package app.roadstr.feature.search

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.core.search.SearchRankingProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSearchPresentationTest {
    @Test
    fun `nearby catalogue preserves Flutter order and symbols`() {
        assertEquals(
            listOf(
                NativeSearchNearbyCategory.Fuel,
                NativeSearchNearbyCategory.Restaurant,
                NativeSearchNearbyCategory.Supermarket,
                NativeSearchNearbyCategory.Atm,
                NativeSearchNearbyCategory.Pharmacy,
                NativeSearchNearbyCategory.Hospital,
                NativeSearchNearbyCategory.Police,
                NativeSearchNearbyCategory.PostOffice,
                NativeSearchNearbyCategory.Parking,
                NativeSearchNearbyCategory.Hotel,
                NativeSearchNearbyCategory.Charging,
            ),
            NativeSearchNearbyCategory.entries,
        )
        assertEquals("⛽", NativeSearchNearbyCategory.Fuel.emoji)
        assertEquals("🔌", NativeSearchNearbyCategory.Charging.emoji)
    }

    @Test
    fun `result projection preserves category fallback distances and work bounds`() {
        val values = List(30) { index ->
            result(
                title = "Place $index",
                display = "Place $index, Berlin, Germany",
                distance = if (index == 0) 327.0 else null,
            )
        } + result(point = SearchResponsePoint(91.0, 0.0))

        val projected = NativeSearchPresenter.results(values, imperial = false)

        assertEquals(NativeSearchPresenter.MAX_NEARBY_RESULTS, projected.size)
        assertEquals("Place 0", projected.first().title)
        assertEquals("Restaurant", projected.first().subtitle)
        assertEquals("327 m", projected.first().distanceLabel)
        assertEquals("🍽️", projected.first().emoji)
        assertEquals(
            "0.2 mi",
            NativeSearchPresenter.results(listOf(values.first()), imperial = true)
                .single()
                .distanceLabel,
        )
    }

    @Test
    fun `result projection rejects invalid points and bounds hostile text`() {
        val invalid = result(point = SearchResponsePoint(Double.NaN, 12.0))
        val hostile = result(
            title = "  Cafe\u0000${"x".repeat(200)}  ",
            display = "${"y".repeat(400)}, City",
            distance = -1.0,
        )

        val projected = NativeSearchPresenter.results(listOf(invalid, hostile), imperial = false)

        assertEquals(1, projected.size)
        assertEquals(120, projected.single().title.length)
        assertFalse(projected.single().title.contains('\u0000'))
        assertNull(projected.single().distanceLabel)
    }

    @Test
    fun `favorites match labels and addresses with Flutter bounds`() {
        val favorites = listOf(
            favorite("Home", "Via Roma"),
            favorite("Office", "Alexanderplatz"),
            favorite("", "Invalid"),
            favorite("Bad", "Point", SearchResponsePoint(100.0, 0.0)),
        )

        assertEquals(
            listOf("Home"),
            NativeSearchPresenter.favorites(favorites, "roma").map { it.label },
        )
        assertEquals(
            listOf("Office"),
            NativeSearchPresenter.favorites(favorites, "OFF").map { it.label },
        )
        assertEquals(2, NativeSearchPresenter.favorites(favorites, "").size)
    }

    @Test
    fun `history keeps five validated rows and splits the first comma`() {
        val history = listOf(
            SearchHistoryEntry("Via Roma 1, Torino, Italia", 45.0, 7.0),
            SearchHistoryEntry("Berlin", 52.5, 13.4),
            SearchHistoryEntry("Bad", 95.0, 0.0),
            SearchHistoryEntry("Third", 1.0, 1.0),
            SearchHistoryEntry("Fourth", 2.0, 2.0),
            SearchHistoryEntry("Fifth", 3.0, 3.0),
            SearchHistoryEntry("Sixth", 4.0, 4.0),
        )

        val rows = NativeSearchPresenter.history(history)

        assertEquals(4, rows.size)
        assertEquals("Via Roma 1", rows.first().title)
        assertEquals("Torino, Italia", rows.first().subtitle)
        assertEquals("", rows[1].subtitle)
    }

    @Test
    fun `show is revision safe and bounds favorites and history`() {
        val session = NativeSearchSession()
        val favorites = List(NativeSearchPresenter.MAX_FAVORITES + 5) { favorite("Place $it") }
        val history = List(SearchHistoryProtocol.MAX_STORED_ITEMS + 2) {
            SearchHistoryEntry("History $it", 40.0, 12.0)
        }

        assertTrue(session.show(4, favorites, history, nearbyEnabled = true))
        assertFalse(session.show(4))
        assertEquals(NativeSearchUiStatus.Browsing, session.state.value.status)
        assertEquals(NativeSearchPresenter.MAX_FAVORITES, session.state.value.favorites.size)
        assertEquals(SearchHistoryProtocol.MAX_STORED_ITEMS, session.state.value.history.size)
        assertTrue(session.state.value.nearbyEnabled)
    }

    @Test
    fun `query generation trims bounds and fences stale work`() {
        val session = NativeSearchSession()
        assertTrue(session.show(1, listOf(favorite("Home", "Via Roma"))))

        assertTrue(session.beginQuery(2, "  ${"x".repeat(250)}  "))
        assertEquals(SearchRankingProtocol.MAX_QUERY_LENGTH, session.state.value.query.length)
        assertEquals(NativeSearchUiStatus.Loading, session.state.value.status)
        assertFalse(session.beginQuery(2, "late"))
        assertFalse(session.submitResults(1, listOf(result())))

        assertTrue(session.beginQuery(3, "  "))
        assertEquals(NativeSearchUiStatus.Browsing, session.state.value.status)
        assertEquals("", session.state.value.query)
    }

    @Test
    fun `query editing stays idle and fences an obsolete provider generation`() {
        val session = NativeSearchSession()
        assertTrue(session.show(1, nearbyEnabled = true))
        assertTrue(session.beginQuery(2, "old query"))

        assertTrue(session.updateQuery(3, "  new query"))
        assertEquals(NativeSearchUiStatus.Browsing, session.state.value.status)
        assertEquals("new query", session.state.value.query)
        assertTrue(session.state.value.nearbyEnabled)
        assertFalse(session.submitResults(2, listOf(result(title = "Late"))))
    }

    @Test
    fun `nearby requires a fix and accepts one terminal result set`() {
        val session = NativeSearchSession()
        assertTrue(session.show(1, nearbyEnabled = false))
        assertFalse(session.beginNearby(2, NativeSearchNearbyCategory.Fuel))
        assertTrue(session.show(3, nearbyEnabled = true))
        assertTrue(session.beginNearby(4, NativeSearchNearbyCategory.Fuel))

        assertTrue(session.submitPartial(4, listOf(result(title = "Partial"))))
        assertEquals(NativeSearchUiStatus.Loading, session.state.value.status)
        assertEquals("Partial", session.state.value.results.single().title)
        assertTrue(session.submitResults(4, listOf(result(title = "Final"))))
        assertEquals(NativeSearchUiStatus.Results, session.state.value.status)
        assertEquals("Final", session.state.value.results.single().title)
        assertFalse(session.submitResults(4, emptyList()))
    }

    @Test
    fun `empty nearby is announced while an empty typed search stays silent`() {
        val session = NativeSearchSession()
        assertTrue(session.show(1, nearbyEnabled = true))
        assertTrue(session.beginNearby(2, NativeSearchNearbyCategory.Parking))
        assertTrue(session.submitResults(2, emptyList()))
        assertEquals(NativeSearchUiStatus.EmptyNearby, session.state.value.status)

        assertTrue(session.beginQuery(3, "nothing"))
        assertTrue(session.submitResults(3, emptyList()))
        assertEquals(NativeSearchUiStatus.Browsing, session.state.value.status)
        assertEquals("nothing", session.state.value.query)
    }

    @Test
    fun `history clear unit reprojection and hide retain revision fences`() {
        val session = NativeSearchSession()
        assertTrue(
            session.show(
                1,
                history = listOf(SearchHistoryEntry("Place", 40.0, 12.0)),
            ),
        )
        assertTrue(session.beginQuery(2, "place"))
        assertTrue(session.submitResults(2, listOf(result(distance = 327.0))))
        assertEquals("327 m", session.state.value.results.single().distanceLabel)
        assertTrue(session.updateUnits(imperial = true))
        assertEquals("0.2 mi", session.state.value.results.single().distanceLabel)
        assertTrue(session.clearHistory(2))
        assertTrue(session.state.value.history.isEmpty())
        assertFalse(session.clearHistory(1))

        assertTrue(session.hide(2))
        assertEquals(NativeSearchUiStatus.Hidden, session.state.value.status)
        assertFalse(session.submitResults(2, listOf(result())))
        assertFalse(session.hide(1))
    }

    private fun favorite(
        label: String,
        address: String = "Address",
        point: SearchResponsePoint = SearchResponsePoint(45.0, 7.0),
    ) = NativeSearchFavorite(label, address, point)

    private fun result(
        title: String = "Cafe",
        display: String = "$title, Berlin, Germany",
        distance: Double? = null,
        point: SearchResponsePoint = SearchResponsePoint(52.5, 13.4),
    ) = SearchResult(
        displayName = display,
        shortName = title,
        position = point,
        featureClass = "amenity",
        type = "restaurant",
        distanceM = distance,
    )
}
