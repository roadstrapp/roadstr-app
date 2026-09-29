package app.roadstr.feature.navigation

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseStep
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationHudPresentationTest {
    private val now = LocalDateTime.of(2024, 1, 1, 10, 0)

    @Test
    fun `modifier grammar preserves every ordinary turn family`() {
        val expected = linkedMapOf(
            "" to NativeManeuverKind.Straight,
            "slight left" to NativeManeuverKind.SlightLeft,
            "left" to NativeManeuverKind.Left,
            "sharp left" to NativeManeuverKind.SharpLeft,
            "slight right" to NativeManeuverKind.SlightRight,
            "right" to NativeManeuverKind.Right,
            "sharp right" to NativeManeuverKind.SharpRight,
            "uturn left" to NativeManeuverKind.UTurnLeft,
            "uturn right" to NativeManeuverKind.UTurnRight,
            "uturn" to NativeManeuverKind.UTurnLeft,
        )

        for ((modifier, kind) in expected) {
            assertEquals(kind, NativeNavigationHudPresenter.maneuver(step(modifier = modifier)).kind)
        }
    }

    @Test
    fun `direction grammar distinguishes forks merges ramps exits and terminals`() {
        val expected = mapOf(
            step(direction = "fork", modifier = "left") to NativeManeuverKind.ForkLeft,
            step(direction = "use lane", modifier = "right") to NativeManeuverKind.ForkRight,
            step(direction = "merge", modifier = "left") to NativeManeuverKind.MergeLeft,
            step(direction = "merge", modifier = "right") to NativeManeuverKind.MergeRight,
            step(direction = "on ramp", modifier = "left") to NativeManeuverKind.RampLeft,
            step(direction = "on ramp", modifier = "right") to NativeManeuverKind.RampRight,
            step(direction = "off ramp", modifier = "left") to NativeManeuverKind.ExitLeft,
            step(direction = "off ramp", modifier = "right") to NativeManeuverKind.ExitRight,
            step(direction = "arrive") to NativeManeuverKind.Arrive,
            step(direction = "depart") to NativeManeuverKind.Depart,
            step(direction = "ferry") to NativeManeuverKind.Ferry,
        )

        for ((value, kind) in expected) {
            assertEquals(kind, NativeNavigationHudPresenter.maneuver(value).kind)
        }
    }

    @Test
    fun `roundabout keeps exit ordinal independent from arm count and bounds both`() {
        val visual = NativeNavigationHudPresenter.maneuver(
            step(direction = "rotary", exit = 30, arms = 2),
        )

        assertEquals(NativeManeuverKind.Roundabout, visual.kind)
        assertEquals(20, visual.roundaboutExit)
        assertEquals(3, visual.roundaboutArmCount)
        assertNull(NativeNavigationHudPresenter.maneuver(step()).roundaboutExit)
    }

    @Test
    fun `next instruction lowercases sentence starts but preserves acronyms`() {
        assertEquals("continue on Via Roma", NativeNavigationHudPresenter.uncapitalised("Continue on Via Roma"))
        assertEquals("SS16 exit", NativeNavigationHudPresenter.uncapitalised("SS16 exit"))
        assertEquals("A", NativeNavigationHudPresenter.uncapitalised("A"))
    }

    @Test
    fun `projection uses live maneuver distance and marks long straight roads`() {
        val route = route(
            listOf(
                step(
                    instruction = "Continue on A4",
                    direction = "continue",
                    distance = 3_400.0,
                    road = "A4",
                ),
                step(instruction = "Turn left", modifier = "left", distance = 150.0),
            ),
        )

        val snapshot = NativeNavigationHudPresenter.project(
            4,
            input(route, distanceToManeuverM = 1_250.0),
            nowLabel = "Now",
        )

        assertEquals("1.3 km", snapshot.current?.distanceLabel)
        assertTrue(requireNotNull(snapshot.current).prominentDistance)
        assertEquals("A4", snapshot.current?.roadName)
        assertEquals("turn left", snapshot.next?.instruction)
        assertEquals("3.4 km", snapshot.next?.distanceLabel)
    }

    @Test
    fun `arrival side replaces provider text and arrival is never previewed`() {
        val arrivalOnly = NativeNavigationHudPresenter.project(
            1,
            input(route(listOf(step(direction = "arrive", modifier = "right")))),
            "Now",
        )
        assertEquals(NativeArrivalSide.Right, arrivalOnly.current?.arrivalSide)

        val beforeArrival = NativeNavigationHudPresenter.project(
            2,
            input(route(listOf(step(), step(direction = "arrive", modifier = "left")))),
            "Now",
        )
        assertNull(beforeArrival.next)
    }

    @Test
    fun `metric and imperial speed altitude and limits follow Flutter units`() {
        val metric = NativeNavigationHudPresenter.project(
            1,
            input(
                route(listOf(step())),
                speedKmh = 51.0,
                speedLimitKmh = 50,
                altitudeM = 123.6,
                showAltitude = true,
                speedometerStyle = NativeSpeedometerStyle.Sport,
            ),
            "Now",
        )
        assertEquals(51, metric.speed)
        assertEquals("km/h", metric.speedUnit)
        assertEquals(50, metric.speedLimit)
        assertEquals("124 m", metric.altitudeLabel)
        assertTrue(metric.overSpeedLimit)
        assertEquals(NativeSpeedometerStyle.Sport, metric.speedometerStyle)

        val imperial = NativeNavigationHudPresenter.project(
            2,
            input(
                route(listOf(step())),
                speedKmh = 100.0,
                speedLimitKmh = 100,
                altitudeM = 100.0,
                showAltitude = true,
                imperial = true,
            ),
            "Now",
        )
        assertEquals(62, imperial.speed)
        assertEquals("mph", imperial.speedUnit)
        assertEquals(62, imperial.speedLimit)
        assertEquals("328 ft", imperial.altitudeLabel)
        assertFalse(imperial.overSpeedLimit)
    }

    @Test
    fun `live summary drives duration distance and ETA with route fallbacks`() {
        val route = route(listOf(step()), distance = 8_000.0, duration = 3_661.0)
        val live = NativeNavigationHudPresenter.project(
            1,
            input(route, remainingDistanceM = 1_500.0, remainingSeconds = 3_630.0),
            "Now",
        )
        assertEquals("1.5 km", live.remainingDistanceLabel)
        assertEquals(61, live.durationMinutes)
        assertEquals(1, live.durationHours)
        assertEquals(1, live.durationMinuteRemainder)
        assertEquals("11:00", live.etaLabel)

        val fallback = NativeNavigationHudPresenter.project(2, input(route), "Now")
        assertEquals("8.0 km", fallback.remainingDistanceLabel)
        assertEquals(61, fallback.durationMinutes)
        assertEquals("11:01", fallback.etaLabel)
    }

    @Test
    fun `invalid telemetry route and step inputs fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeNavigationHudPresenter.project(
                1,
                input(route(listOf(step())), speedKmh = Double.NaN),
                "Now",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeNavigationHudPresenter.project(
                1,
                input(route(emptyList()), stepIndex = 0),
                "Now",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeNavigationHudPresenter.project(
                1,
                input(route(listOf(step())), speedLimitKmh = 0),
                "Now",
            )
        }
    }

    @Test
    fun `speedometer persisted values retain five styles and safe fallback`() {
        assertEquals(
            NativeSpeedometerStyle.entries,
            NativeSpeedometerStyle.entries.map { NativeSpeedometerStyle.fromStorage(it.storageValue) },
        )
        assertEquals(NativeSpeedometerStyle.Classic, NativeSpeedometerStyle.fromStorage("unknown"))
        assertEquals(NativeSpeedometerStyle.Classic, NativeSpeedometerStyle.fromStorage(null))
    }

    @Test
    fun `session show and update fence revisions and backwards step movement`() {
        val session = NativeNavigationHudSession()
        val route = route(listOf(step(), step(instruction = "Second")))

        assertTrue(session.show(3, input(route), "Now"))
        assertFalse(session.show(3, input(route), "Now"))
        assertFalse(session.update(2, input(route, stepIndex = 1), "Now"))
        assertTrue(session.update(3, input(route, stepIndex = 1, speedKmh = 40.0), "Now"))
        assertEquals(1, session.state.value.current?.index)
        assertEquals(40, session.state.value.speed)
        assertFalse(session.update(3, input(route, stepIndex = 0), "Now"))
    }

    @Test
    fun `hide invalidates updates and retains a monotonic revision fence`() {
        val session = NativeNavigationHudSession()
        val value = input(route(listOf(step())))
        assertTrue(session.show(1, value, "Now"))
        assertTrue(session.hide(1))
        assertEquals(NativeNavigationHudStatus.Hidden, session.state.value.status)
        assertFalse(session.update(1, value, "Now"))
        assertFalse(session.hide(0))
        assertTrue(session.hide(2))
        assertFalse(session.show(2, value, "Now"))
        assertTrue(session.show(3, value, "Now"))
    }

    private fun input(
        route: RoutingParsedRoute,
        stepIndex: Int = 0,
        distanceToManeuverM: Double = 0.0,
        remainingDistanceM: Double = 0.0,
        remainingSeconds: Double = 0.0,
        speedKmh: Double = 0.0,
        speedLimitKmh: Int? = null,
        altitudeM: Double? = null,
        showAltitude: Boolean = false,
        speedometerStyle: NativeSpeedometerStyle = NativeSpeedometerStyle.Classic,
        imperial: Boolean = false,
    ) = NativeNavigationHudInput(
        route = route,
        stepIndex = stepIndex,
        distanceToManeuverM = distanceToManeuverM,
        remainingDistanceM = remainingDistanceM,
        remainingSeconds = remainingSeconds,
        speedKmh = speedKmh,
        speedLimitKmh = speedLimitKmh,
        altitudeM = altitudeM,
        showAltitude = showAltitude,
        speedometerStyle = speedometerStyle,
        imperial = imperial,
        now = now,
    )

    private fun route(
        steps: List<RoutingResponseStep>,
        distance: Double = 10_000.0,
        duration: Double = 3_600.0,
    ) = RoutingParsedRoute(
        polyline = emptyList(),
        steps = steps,
        totalDistanceM = distance,
        totalDurationS = duration,
    )

    private fun step(
        instruction: String = "Continue straight",
        direction: String = "turn",
        modifier: String = "",
        distance: Double = 500.0,
        exit: Int? = null,
        arms: Int? = null,
        road: String = "",
    ) = RoutingResponseStep(
        instruction = instruction,
        direction = direction,
        modifier = modifier,
        distanceM = distance,
        location = RoutingResponsePoint(45.0, 7.0),
        exitNumber = exit,
        roundaboutArmCount = arms,
        roadName = road,
    )
}
