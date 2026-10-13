package app.roadstr.roadtest

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStopReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGenericSearchMergeTest {
    @Test
    fun `generic search keeps POIs and addresses ahead of operational stops`() {
        val places = (1..30).map { place(it) }
        val stops = (1..30).map { stop(it) }

        val merged = mergeGenericSearchResults(places, stops)

        assertEquals(60, merged.size)
        assertTrue(merged.take(20).all { it.transitStop == null })
        assertTrue(merged.drop(20).take(5).all { it.transitStop != null })
    }

    @Test
    fun `generic search fills available slots when only a few places match`() {
        val merged = mergeGenericSearchResults(listOf(place(1)), (1..30).map { stop(it) })

        assertEquals(25, merged.take(25).size)
        assertEquals(1, merged.take(25).count { it.transitStop == null })
    }

    private fun place(index: Int) = SearchResult(
        displayName = "Place $index, City",
        shortName = "Place $index",
        position = SearchResponsePoint(45.0 + index / 1_000.0, 9.0),
    )

    private fun stop(index: Int): SearchResult {
        val position = TransitResponsePoint(46.0 + index / 1_000.0, 10.0)
        return SearchResult(
            displayName = "Stop $index",
            shortName = "Stop $index",
            position = SearchResponsePoint(position.latitude, position.longitude),
            transitStop = TransitStopReference(
                id = TransitScopedId("test", "stop-$index"),
                name = "Stop $index",
                position = position,
            ),
        )
    }
}
