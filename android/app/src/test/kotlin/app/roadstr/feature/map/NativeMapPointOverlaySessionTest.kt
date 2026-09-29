package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapPointOverlaySessionTest {
    private fun marker(
        id: String,
        kind: NativeMapPointOverlayKind = NativeMapPointOverlayKind.RoadHazard,
        latitude: Double = 45.0,
        longitude: Double = 9.0,
    ) = NativeMapPointOverlayMarker(id, NativeMapPoint(latitude, longitude), kind)

    @Test
    fun `point overlays start empty and unfenced`() {
        assertEquals(NativeMapPointOverlaySnapshot.Empty, NativeMapPointOverlaySession().state.value)
    }

    @Test
    fun `newer bulk replacements publish while stale revisions are ignored`() {
        val session = NativeMapPointOverlaySession()
        val first = marker("first")

        assertTrue(session.replace(2, listOf(first)))
        assertFalse(session.replace(2, listOf(marker("same"))))
        assertFalse(session.replace(1, listOf(marker("older"))))
        assertEquals(listOf(first), session.state.value.markers)
    }

    @Test
    fun `clear hides all markers and fences late cache callbacks`() {
        val session = NativeMapPointOverlaySession()
        session.replace(1, listOf(marker("active")))

        assertTrue(session.clear(3))
        assertFalse(session.replace(2, listOf(marker("late"))))
        assertEquals(3L, session.state.value.revision)
        assertTrue(session.state.value.markers.isEmpty())
    }

    @Test
    fun `revision and WGS84 validation fail closed`() {
        val session = NativeMapPointOverlaySession()

        assertThrows(IllegalArgumentException::class.java) { session.clear(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            session.replace(1, listOf(marker("lat", latitude = 91.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.replace(1, listOf(marker("lon", longitude = Double.NaN)))
        }
    }

    @Test
    fun `ids are bounded non-blank and unique`() {
        val session = NativeMapPointOverlaySession()

        assertThrows(IllegalArgumentException::class.java) {
            session.replace(1, listOf(marker(" ")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.replace(1, listOf(marker("x".repeat(129))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.replace(1, listOf(marker("same"), marker("same")))
        }
    }

    @Test
    fun `marker count is bounded before publication`() {
        val tooMany = List(NativeMapPointOverlayPolicy.MAX_MARKERS + 1) { marker("marker-$it") }

        assertThrows(IllegalArgumentException::class.java) {
            NativeMapPointOverlaySession().replace(1, tooMany)
        }
    }

    @Test
    fun `normalization preserves source order inside Flutter layer order`() {
        val session = NativeMapPointOverlaySession()
        session.replace(
            1,
            listOf(
                marker("crossing", NativeMapPointOverlayKind.Crosswalk),
                marker("camera", NativeMapPointOverlayKind.OsmSpeedCamera),
                marker("event-a", NativeMapPointOverlayKind.RoadPolice),
                marker("parking", NativeMapPointOverlayKind.Parking),
                marker("event-b", NativeMapPointOverlayKind.RoadAnimal),
                marker("light", NativeMapPointOverlayKind.TrafficLight),
                marker("bump", NativeMapPointOverlayKind.SpeedBump),
            ),
        )

        assertEquals(
            listOf("event-a", "event-b", "camera", "parking", "light", "bump", "crossing"),
            session.state.value.markers.map { it.id },
        )
    }

    @Test
    fun `zoom gates match Flutter point overlay density policy`() {
        val snapshot = NativeMapPointOverlaySnapshot(
            revision = 1,
            markers = NativeMapPointOverlayPolicy.normalize(
                listOf(
                    marker("event", NativeMapPointOverlayKind.RoadPolice),
                    marker("camera", NativeMapPointOverlayKind.OsmSpeedCamera),
                    marker("light", NativeMapPointOverlayKind.TrafficLight),
                    marker("bump", NativeMapPointOverlayKind.SpeedBump),
                    marker("crossing", NativeMapPointOverlayKind.Crosswalk),
                ),
            ),
        )

        assertEquals(listOf("camera"), visibleIds(snapshot, 10.0))
        assertEquals(listOf("event", "camera"), visibleIds(snapshot, 11.0))
        assertEquals(listOf("event", "camera", "light", "bump"), visibleIds(snapshot, 15.0))
        assertEquals(
            listOf("event", "camera", "light", "bump", "crossing"),
            visibleIds(snapshot, 16.0),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeMapPointOverlayPolicy.visibleAtZoom(snapshot, Double.NaN)
        }
    }

    @Test
    fun `static marker geometry and accents match Flutter`() {
        assertKind(NativeMapPointOverlayKind.OsmSpeedCamera, "📷", 0xFF7C3AED, 30.0)
        assertKind(NativeMapPointOverlayKind.Parking, "P", 0xFF1E88E5, 38.0)
        assertKind(NativeMapPointOverlayKind.TrafficLight, "🚦", 0xFF70D69B, 24.0)
        assertKind(NativeMapPointOverlayKind.SpeedBump, "〰️", 0xFFFF8547, 22.0)
        assertKind(NativeMapPointOverlayKind.Crosswalk, "🚸", 0xFFFFB347, 20.0)
    }

    @Test
    fun `all fourteen road categories are emphasized Flutter pins`() {
        val roadKinds = NativeMapPointOverlayKind.entries.filter { it.name.startsWith("Road") }
        val expectedSymbolsAndAccents = listOf(
            "👮" to 0xFF2563EB,
            "🏛️" to 0xFF1E3A8A,
            "📷" to 0xFF7C3AED,
            "🚗" to 0xFFD97706,
            "💥" to 0xFFDC2626,
            "🚫" to 0xFF991B1B,
            "🚧" to 0xFFF59E0B,
            "⚠️" to 0xFFF59E0B,
            "🛣️" to 0xFF92400E,
            "🕳️" to 0xFF6B7280,
            "🌫️" to 0xFF9CA3AF,
            "🧊" to 0xFF60A5FA,
            "🦌" to 0xFF16A34A,
            "ℹ️" to 0xFF6B7280,
        )

        assertEquals(14, roadKinds.size)
        assertTrue(roadKinds.all { it.emphasized })
        assertTrue(roadKinds.all { it.sizeDp == 36.0 && it.minimumZoom == 11.0 })
        assertEquals(
            expectedSymbolsAndAccents,
            roadKinds.map { it.symbol to it.accentArgb },
        )
    }

    private fun visibleIds(snapshot: NativeMapPointOverlaySnapshot, zoom: Double): List<String> =
        NativeMapPointOverlayPolicy.visibleAtZoom(snapshot, zoom).map { it.id }

    private fun assertKind(
        kind: NativeMapPointOverlayKind,
        symbol: String,
        accent: Long,
        size: Double,
    ) {
        assertEquals(symbol, kind.symbol)
        assertEquals(accent, kind.accentArgb)
        assertEquals(size, kind.sizeDp, 0.0)
    }
}
