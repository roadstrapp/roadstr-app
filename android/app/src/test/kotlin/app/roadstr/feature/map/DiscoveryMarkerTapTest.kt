package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryMarkerTapTest {
    private val point = NativeMapPoint(45.0, 9.0)
    private val pin = NativeMapPointOverlayMarker("discovery:osm:n:1", point, NativeMapPointOverlayKind.DiscoveryResult)

    @Test
    fun `a tap on a discovery pin opens a place, not a road report`() {
        val interaction = NativeMapInteractionPolicy.tap(point, pin, markerRevision = 4)
        assertEquals(NativeMapInteraction.PlaceMarkerTap("discovery:osm:n:1", 4), interaction)
    }

    @Test
    fun `a tap on a road event still opens the report`() {
        val event = NativeMapPointOverlayMarker("event:1", point, NativeMapPointOverlayKind.RoadPolice)
        assertTrue(NativeMapInteractionPolicy.tap(point, event, 1) is NativeMapInteraction.RoadEventTap)
    }

    @Test
    fun `other markers do not take taps`() {
        val light = NativeMapPointOverlayMarker("l", point, NativeMapPointOverlayKind.TrafficLight)
        assertFalse(runCatching { NativeMapInteractionPolicy.tap(point, light, 1) }.isSuccess)
    }

    @Test
    fun `the hit test finds a discovery pin under the finger`() {
        val snapshot = NativeMapPointOverlaySnapshot(1, listOf(pin))
        val hit = NativeMapInteractionPolicy.hitRoadEvent(
            snapshot, zoom = 14.0, density = 2.0, tap = NativeMapScreenPoint(100.0, 100.0),
        ) { NativeMapScreenPoint(110.0, 95.0) }
        assertNotNull(hit)
        assertEquals(pin.id, hit!!.id)
        val miss = NativeMapInteractionPolicy.hitRoadEvent(
            snapshot, zoom = 14.0, density = 2.0, tap = NativeMapScreenPoint(500.0, 500.0),
        ) { NativeMapScreenPoint(110.0, 95.0) }
        assertNull(miss)
    }
}
