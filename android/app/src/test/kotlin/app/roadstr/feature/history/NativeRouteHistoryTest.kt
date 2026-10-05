package app.roadstr.feature.history

import app.roadstr.feature.map.NativeMapPoint
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class NativeRouteHistoryTest {
    private fun entry(label: String, lat: Double, lon: Double, at: Long) =
        NativeRouteHistoryEntry(label, NativeMapPoint(lat, lon), at)

    @Test
    fun newestRouteComesFirst() {
        val first = entry("Home", 45.0, 9.0, 1)
        val second = entry("Work", 45.1, 9.1, 2)

        val history = NativeRouteHistoryProtocol.record(
            NativeRouteHistoryProtocol.record(emptyList(), first),
            second,
        )

        assertEquals(listOf(second, first), history)
    }

    @Test
    fun goingToTheSamePlaceAgainMovesItUpInsteadOfRepeating() {
        val home = entry("Home", 45.0, 9.0, 1)
        val work = entry("Work", 45.1, 9.1, 2)
        // About 11 m from "Home": the same destination.
        val homeAgain = entry("Home, Via Roma 1", 45.0001, 9.0, 3)

        val history = listOf(work, home).fold(emptyList<NativeRouteHistoryEntry>()) { list, item ->
            NativeRouteHistoryProtocol.record(list, item)
        }.let { NativeRouteHistoryProtocol.record(it, homeAgain) }

        assertEquals(listOf(homeAgain, work), history)
    }

    @Test
    fun placesFurtherApartThanTheThresholdStaySeparate() {
        // About 111 m apart.
        val a = entry("A", 45.0, 9.0, 1)
        val b = entry("B", 45.001, 9.0, 2)

        val history = NativeRouteHistoryProtocol.record(
            NativeRouteHistoryProtocol.record(emptyList(), a),
            b,
        )

        assertEquals(2, history.size)
    }

    @Test
    fun keepsOnlyTheMostRecentFifty() {
        var history = emptyList<NativeRouteHistoryEntry>()
        repeat(60) { index ->
            history = NativeRouteHistoryProtocol.record(
                history,
                entry("Place $index", 40.0 + index * 0.01, 9.0, index.toLong()),
            )
        }

        assertEquals(NativeRouteHistoryProtocol.MAX_ENTRIES, history.size)
        assertEquals("Place 59", history.first().label)
        assertEquals("Place 10", history.last().label)
    }

    @Test
    fun removeDropsOnlyThatRow() {
        val a = entry("A", 45.0, 9.0, 1)
        val b = entry("B", 46.0, 9.0, 2)

        assertEquals(listOf(b), NativeRouteHistoryProtocol.remove(listOf(a, b), a))
        assertEquals(listOf(a, b), NativeRouteHistoryProtocol.remove(listOf(a, b), entry("C", 1.0, 1.0, 3)))
    }

    @Test
    fun roundTripsThroughItsTextForm() {
        val history = listOf(
            entry("Café \"Roma\" \\ Piazza", 45.4642, 9.19, 1_700_000_000_000),
            entry("Línea\nnueva", -33.8688, 151.2093, 1_700_000_100_000),
        )

        val decoded = NativeRouteHistoryProtocol.decode(NativeRouteHistoryProtocol.encode(history))

        assertEquals(
            history.map { it.copy(label = it.label.trim()) },
            decoded,
        )
    }

    @Test
    fun rejectsBadRowsAndKeepsTheGoodOnes() {
        val raw = """[
            {"label":"ok","lat":45.0,"lon":9.0,"ts":5},
            {"label":"","lat":45.0,"lon":9.0,"ts":5},
            {"label":"far","lat":95.0,"lon":9.0,"ts":5},
            {"label":"notime","lat":45.0,"lon":9.0},
            "junk",
            {"label":"neg","lat":45.0,"lon":9.0,"ts":-1}
        ]"""

        val decoded = NativeRouteHistoryProtocol.decode(raw)

        assertEquals(listOf("ok"), decoded.map { it.label })
    }

    @Test
    fun garbageOrOversizedInputDecodesAsEmpty() {
        assertTrue(NativeRouteHistoryProtocol.decode(null).isEmpty())
        assertTrue(NativeRouteHistoryProtocol.decode("not json").isEmpty())
        assertTrue(NativeRouteHistoryProtocol.decode("{}").isEmpty())
        val oversized = "[" + "{\"label\":\"x\",\"lat\":1,\"lon\":1,\"ts\":1},".repeat(5_000) + "]"
        assertTrue(NativeRouteHistoryProtocol.decode(oversized).isEmpty())
    }

    @Test
    fun blankOrOverlongLabelsAreNotRecorded() {
        assertTrue(NativeRouteHistoryProtocol.record(emptyList(), entry("   ", 45.0, 9.0, 1)).isEmpty())
        val long = "x".repeat(NativeRouteHistoryProtocol.MAX_LABEL_CHARS + 1)
        assertTrue(NativeRouteHistoryProtocol.record(emptyList(), entry(long, 45.0, 9.0, 1)).isEmpty())
    }
}
