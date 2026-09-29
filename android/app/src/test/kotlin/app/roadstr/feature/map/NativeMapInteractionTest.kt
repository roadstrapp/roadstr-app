package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapInteractionTest {
    @Test
    fun `tap actions distinguish road events from ordinary map coordinates`() {
        val point = pointAtMeters(0.0)
        val marker = marker("event", point, NativeMapPointOverlayKind.RoadHazard)

        assertEquals(
            NativeMapInteraction.RoadEventTap("event", 7),
            NativeMapInteractionPolicy.tap(point, marker, markerRevision = 7),
        )
        assertEquals(
            NativeMapInteraction.MapTap(point),
            NativeMapInteractionPolicy.tap(point, marker = null, markerRevision = -1),
        )
        assertEquals(
            NativeMapInteraction.MapLongPress(point),
            NativeMapInteractionPolicy.longPress(point),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeMapInteractionPolicy.tap(
                point,
                marker("parking", point, NativeMapPointOverlayKind.Parking),
                markerRevision = 7,
            )
        }
    }

    @Test
    fun `road event hit target matches the Flutter marker rectangle`() {
        val event = marker("event", pointAtMeters(0.0), NativeMapPointOverlayKind.RoadPolice)
        val snapshot = snapshot(event)
        val center = NativeMapScreenPoint(100.0, 200.0)

        assertEquals(
            event,
            hit(snapshot, zoom = 11.0, tap = NativeMapScreenPoint(118.0, 218.0)) { center },
        )
        assertNull(
            hit(snapshot, zoom = 11.0, tap = NativeMapScreenPoint(118.01, 218.0)) { center },
        )
    }

    @Test
    fun `hidden and static markers never intercept map taps`() {
        val road = marker("road", pointAtMeters(0.0), NativeMapPointOverlayKind.RoadHazard)
        val parking = marker("parking", pointAtMeters(1.0), NativeMapPointOverlayKind.Parking)
        val tap = NativeMapScreenPoint(20.0, 20.0)

        assertNull(hit(snapshot(road), zoom = 10.99, tap = tap) { tap })
        assertNull(hit(snapshot(parking), zoom = 17.0, tap = tap) { tap })
    }

    @Test
    fun `last painted overlapping road event wins the tap`() {
        val first = marker("first", pointAtMeters(0.0), NativeMapPointOverlayKind.RoadPolice)
        val second = marker("second", pointAtMeters(1.0), NativeMapPointOverlayKind.RoadAnimal)
        val center = NativeMapScreenPoint(50.0, 50.0)

        assertEquals(
            second,
            hit(snapshot(first, second), zoom = 17.0, tap = center) { center },
        )
    }

    @Test
    fun `hit testing rejects invalid display inputs and skips invalid projections`() {
        val event = marker("event", pointAtMeters(0.0), NativeMapPointOverlayKind.RoadHazard)
        val snapshot = snapshot(event)
        val tap = NativeMapScreenPoint(0.0, 0.0)

        assertThrows(IllegalArgumentException::class.java) {
            hit(snapshot, zoom = 17.0, density = 0.0, tap = tap) { tap }
        }
        assertThrows(IllegalArgumentException::class.java) {
            hit(snapshot, zoom = 17.0, tap = NativeMapScreenPoint(Double.NaN, 0.0)) { tap }
        }
        assertNull(
            hit(snapshot, zoom = 17.0, tap = tap) {
                NativeMapScreenPoint(Double.POSITIVE_INFINITY, 0.0)
            },
        )
    }

    @Test
    fun `nearest alternative keeps Flutter first-wins vertex ordering`() {
        val first = listOf(pointAtMeters(0.0), pointAtMeters(100.0))
        val second = listOf(pointAtMeters(200.0), pointAtMeters(300.0))

        assertEquals(
            1,
            NativeMapInteractionPolicy.nearestAlternative(pointAtMeters(210.0), listOf(first, second)),
        )
        assertEquals(
            0,
            NativeMapInteractionPolicy.nearestAlternative(
                pointAtMeters(0.0),
                listOf(first, listOf(pointAtMeters(0.0))),
            ),
        )
    }

    @Test
    fun `alternative tap uses strict rounded 60 metre gate`() {
        val alternative = listOf(pointAtMeters(0.0))

        assertEquals(
            0,
            NativeMapInteractionPolicy.nearestAlternative(
                pointAtMeters(59.4),
                listOf(alternative),
            ),
        )
        assertEquals(
            -1,
            NativeMapInteractionPolicy.nearestAlternative(
                pointAtMeters(59.6),
                listOf(alternative),
            ),
        )
    }

    @Test
    fun `route session applies only current nearby alternative taps`() {
        val session = NativeRouteOverlaySession(0xFF8B5CF6)
        val near = listOf(pointAtMeters(0.0), pointAtMeters(100.0))
        val far = listOf(pointAtMeters(1_000.0), pointAtMeters(1_100.0))
        assertTrue(
            session.submitAlternatives(
                revision = 5,
                candidates = listOf(candidate(near), candidate(far)),
                selectedIndex = 1,
            ),
        )

        assertFalse(session.selectAlternativeAt(4, pointAtMeters(10.0)))
        assertFalse(session.selectAlternativeAt(5, pointAtMeters(500.0)))
        assertTrue(session.selectAlternativeAt(5, pointAtMeters(10.0)))
        assertEquals(0, session.state.value.selectedAlternativeIndex)
        assertFalse(session.selectAlternativeAt(5, pointAtMeters(10.0)))
    }

    private fun hit(
        snapshot: NativeMapPointOverlaySnapshot,
        zoom: Double,
        density: Double = 1.0,
        tap: NativeMapScreenPoint,
        project: (NativeMapPoint) -> NativeMapScreenPoint?,
    ): NativeMapPointOverlayMarker? = NativeMapInteractionPolicy.hitRoadEvent(
        snapshot = snapshot,
        zoom = zoom,
        density = density,
        tap = tap,
        project = project,
    )

    private fun snapshot(vararg markers: NativeMapPointOverlayMarker) =
        NativeMapPointOverlaySnapshot(
            revision = 1,
            markers = NativeMapPointOverlayPolicy.normalize(markers.toList()),
        )

    private fun marker(
        id: String,
        point: NativeMapPoint,
        kind: NativeMapPointOverlayKind,
    ) = NativeMapPointOverlayMarker(id, point, kind)

    private fun candidate(points: List<NativeMapPoint>) =
        NativeRouteCandidate(points, List(points.size) { false })

    private fun pointAtMeters(meters: Double): NativeMapPoint = NativeMapPoint(
        latitude = 0.0,
        longitude = Math.toDegrees(meters / 6_378_137.0),
    )
}
