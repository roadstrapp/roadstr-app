package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutingAvoidanceProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/routing_avoidance_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native avoidance and retiming match all Dart vectors`() {
        assertEquals(36, rows.size)
        for (fields in rows) {
            val input = BoundedJsonParser(decode(fields[2])).parse() as Map<*, *>
            val actual = when (fields[0]) {
                "orchestration" -> evaluateOrchestration(input)
                "retime" -> evaluateRetime(input)
                else -> error("Unknown operation ${fields[0]}")
            }
            assertEquals(fields[1], decode(fields[3]), actual)
        }
    }

    @Test
    fun `retiming copy preserves route payload and only replaces timing provenance`() {
        val step = RoutingResponseStep(
            instruction = "Continue",
            direction = "continue",
            distanceM = 3_000.0,
            location = RoutingResponsePoint(0.0, 0.0),
        )
        val route = route(
            points = line(1_000.0, 1_000.0, 1_000.0),
            distance = 3_000.0,
            duration = 300.0,
        ).copy(
            steps = listOf(step),
            speedLimits = listOf(RoutingSpeedLimitEntry(0.0, 50)),
        )
        val result = RoutingRetimePolicy.apply(
            route,
            requireNotNull(RoutingRetimePolicy.buildPlan(route)),
            listOf(
                OsrmRetimeLeg(1_000.0, 40.0),
                OsrmRetimeLeg(1_000.0, 50.0),
                OsrmRetimeLeg(1_000.0, 60.0),
            ),
        )

        assertEquals(150.0, result.totalDurationS, 1e-6)
        assertEquals(true, result.fromAvoidanceRouter)
        assertSame(route.polyline, result.polyline)
        assertSame(route.steps, result.steps)
        assertSame(route.speedLimits, result.speedLimits)
        assertEquals(route.totalDistanceM, result.totalDistanceM, 0.0)
        assertEquals(route.avoidance, result.avoidance)
    }

    @Test
    fun `success without a route is rejected at the protocol boundary`() {
        val state = RoutingAvoidanceProtocol(RoutingAvoidanceMode.OFF_ROAD)
        assertThrows(IllegalStateException::class.java) {
            state.accept(
                RoutingAvoidanceAttempt.TRACKS,
                RoutingAvoidanceAttemptOutcome.SUCCESS,
            )
        }
    }

    private fun evaluateOrchestration(input: Map<*, *>): String {
        val mode = when (input["mode"]) {
            "highwaysAndTolls" -> RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS
            "offRoad" -> RoutingAvoidanceMode.OFF_ROAD
            else -> error("Unknown mode ${input["mode"]}")
        }
        val state = RoutingAvoidanceProtocol(mode)
        val transcript = mutableListOf("initial=${state.initialAttempt.fixtureName}")
        (input["events"] as List<*>).forEachIndexed { index, raw ->
            val event = raw as Map<*, *>
            val routeId = (event["route"] as? Number)?.toDouble()
            val decision = state.accept(
                attempt(event["attempt"] as String),
                outcome(event["outcome"] as String),
                routeId?.let { route(emptyList(), it, 300.0) },
            )
            transcript += "$index:" +
                "next=${decision.nextAttempt?.fixtureName ?: "-"};" +
                "final=${decision.finalRoute?.totalDistanceM?.toLong() ?: "-"};" +
                "propagate=${if (decision.propagateFailure) 1 else 0};" +
                "done=${if (state.isCompleted) 1 else 0}"
        }
        return transcript.joinToString("\n")
    }

    private fun evaluateRetime(input: Map<*, *>): String {
        val points = (input["points"] as List<*>).map { raw ->
            val pair = raw as List<*>
            RoutingResponsePoint(
                latitude = (pair[0] as Number).toDouble(),
                longitude = (pair[1] as Number).toDouble(),
            )
        }
        val route = route(
            points = points,
            distance = (input["distance"] as Number).toDouble(),
            duration = (input["duration"] as Number).toDouble(),
            avoidance = avoidance(input["avoidance"] as String),
            fromAvoidanceRouter = input["fromAvoidance"] as Boolean,
        )
        val legs = (input["legs"] as? List<*>)?.map { raw ->
            val leg = raw as Map<*, *>
            OsrmRetimeLeg(
                distanceM = (leg["distance"] as? Number)?.toDouble(),
                durationS = (leg["duration"] as? Number)?.toDouble(),
            )
        }
        val plan = RoutingRetimePolicy.buildPlan(route)
        val result = if (plan == null) route else RoutingRetimePolicy.apply(route, plan, legs)
        val canonicalPlan = if (plan == null) {
            "plan=-"
        } else {
            "plan=samples=${plan.sampleIndices.size};" +
                "indices=${plan.sampleIndices.joinToString(",")};" +
                "shape=${plan.shapeLengthMeters.fixed(3)};" +
                "crossings=${plan.seaCrossings.joinToString(",")};" +
                "waypoints=${plan.waypoints}"
        }
        return "$canonicalPlan\n" +
            "result=duration=${result.totalDurationS.fixed(3)};" +
            "distance=${result.totalDistanceM.fixed(3)};" +
            "avoidance=${result.avoidance.fixtureName};" +
            "retimed=${if (result.fromAvoidanceRouter) 1 else 0}"
    }

    private fun route(
        points: List<RoutingResponsePoint>,
        distance: Double,
        duration: Double,
        avoidance: RoutingRouteAvoidance = RoutingRouteAvoidance.HighwayAndTollFree,
        fromAvoidanceRouter: Boolean = false,
    ) = RoutingParsedRoute(
        polyline = points,
        steps = emptyList(),
        totalDistanceM = distance,
        totalDurationS = duration,
        avoidance = avoidance,
        fromAvoidanceRouter = fromAvoidanceRouter,
    )

    private fun line(vararg segmentLengthsMeters: Double): List<RoutingResponsePoint> {
        val result = mutableListOf(RoutingResponsePoint(0.0, 0.0))
        var longitude = 0.0
        for (meters in segmentLengthsMeters) {
            longitude += meters / 111_320.0
            result += RoutingResponsePoint(0.0, longitude)
        }
        return result
    }

    private fun attempt(value: String): RoutingAvoidanceAttempt = when (value) {
        "hard" -> RoutingAvoidanceAttempt.HARD
        "soft" -> RoutingAvoidanceAttempt.SOFT
        "tracks" -> RoutingAvoidanceAttempt.TRACKS
        else -> error("Unknown attempt $value")
    }

    private fun outcome(value: String): RoutingAvoidanceAttemptOutcome = when (value) {
        "success" -> RoutingAvoidanceAttemptOutcome.SUCCESS
        "routingFailure" -> RoutingAvoidanceAttemptOutcome.ROUTING_FAILURE
        else -> error("Unknown outcome $value")
    }

    private fun avoidance(value: String): RoutingRouteAvoidance = when (value) {
        "none" -> RoutingRouteAvoidance.None
        "highwayAndTollFree" -> RoutingRouteAvoidance.HighwayAndTollFree
        "minimizedHighwaysAndTolls" -> RoutingRouteAvoidance.MinimizedHighwaysAndTolls
        "offRoadAvoided" -> RoutingRouteAvoidance.OffRoadAvoided
        else -> error("Unknown avoidance $value")
    }

    private val RoutingAvoidanceAttempt.fixtureName: String
        get() = when (this) {
            RoutingAvoidanceAttempt.HARD -> "hard"
            RoutingAvoidanceAttempt.SOFT -> "soft"
            RoutingAvoidanceAttempt.TRACKS -> "tracks"
        }

    private val RoutingRouteAvoidance.fixtureName: String
        get() = when (this) {
            RoutingRouteAvoidance.None -> "none"
            RoutingRouteAvoidance.HighwayAndTollFree -> "highwayAndTollFree"
            RoutingRouteAvoidance.MinimizedHighwaysAndTolls -> "minimizedHighwaysAndTolls"
            RoutingRouteAvoidance.OffRoadAvoided -> "offRoadAvoided"
        }

    private fun Double.fixed(digits: Int): String =
        String.format(Locale.ROOT, "%.${digits}f", this)

    private fun decode(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
