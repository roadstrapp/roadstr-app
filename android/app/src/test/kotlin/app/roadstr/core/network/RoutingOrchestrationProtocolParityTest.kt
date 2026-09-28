package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingOrchestrationProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/routing_orchestration_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native reroute orchestration matches every Dart transcript`() {
        assertEquals(28, rows.size)
        for (fields in rows) {
            val state = RoutingOrchestrationProtocol(
                provider = provider(fields[1]),
                speedKilometresPerHour = number(fields[2]),
                originBearingDegrees = fields[3].takeUnless { it == "-" }?.let(::number),
                straightLineDistanceMeters = number(fields[4]),
            )
            val transcript = mutableListOf("initial=${attemptName(state.initialAttempt)}")
            val events = BoundedJsonParser(decode(fields[5])).parse() as List<*>
            events.forEachIndexed { index, raw ->
                val event = raw as Map<*, *>
                val attempt = when (event["attempt"]) {
                    "constrained" -> RoutingRouteAttempt.CONSTRAINED
                    "unconstrained" -> RoutingRouteAttempt.UNCONSTRAINED
                    else -> error("Unknown attempt ${event["attempt"]}")
                }
                val outcome = when (event["outcome"]) {
                    "success" -> RoutingAttemptOutcome.SUCCESS
                    "routingFailure" -> RoutingAttemptOutcome.ROUTING_FAILURE
                    else -> error("Unknown outcome ${event["outcome"]}")
                }
                val routes = (event["distances"] as List<*>).map { value ->
                    route((value as Number).toDouble())
                }
                val decision = state.accept(attempt, outcome, routes)
                transcript += canonicalDecision(index, decision, state.isCompleted)
            }
            assertEquals(fields[0], decode(fields[6]), transcript.joinToString("\n"))
        }
    }

    @Test
    fun `only moving OSRM with a supplied bearing starts constrained`() {
        fun initial(
            provider: RoutingProvider,
            speed: Double,
            bearing: Double?,
        ) = RoutingOrchestrationProtocol(provider, speed, bearing, 1_000.0).initialAttempt

        assertEquals(
            RoutingRouteAttempt.CONSTRAINED,
            initial(RoutingProvider.OSRM, 3.01, 90.0),
        )
        assertEquals(
            RoutingRouteAttempt.UNCONSTRAINED,
            initial(RoutingProvider.OSRM, 3.0, 90.0),
        )
        assertEquals(
            RoutingRouteAttempt.UNCONSTRAINED,
            initial(RoutingProvider.OSRM, 30.0, null),
        )
        assertEquals(
            RoutingRouteAttempt.UNCONSTRAINED,
            initial(RoutingProvider.OPEN_ROUTE, 30.0, 90.0),
        )
        assertFalse(RoutingOrchestrationProtocol.isImplausibleReroute(13_000.0, 1_000.0))
        assertTrue(RoutingOrchestrationProtocol.isImplausibleReroute(13_000.01, 1_000.0))
    }

    @Test
    fun `terminal route view is immutable and late outcomes are ignored`() {
        val state = RoutingOrchestrationProtocol(
            RoutingProvider.OSRM,
            30.0,
            90.0,
            1_000.0,
        )
        val accepted = state.accept(
            RoutingRouteAttempt.CONSTRAINED,
            RoutingAttemptOutcome.SUCCESS,
            listOf(route(2_000.0)),
        )
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (accepted.finalRoutes as MutableList<RoutingParsedRoute>).clear()
        }
        val late = state.accept(
            RoutingRouteAttempt.UNCONSTRAINED,
            RoutingAttemptOutcome.SUCCESS,
            listOf(route(999.0)),
        )
        assertEquals(RoutingOrchestrationDecision(), late)
        assertTrue(state.isCompleted)
    }

    private fun canonicalDecision(
        index: Int,
        decision: RoutingOrchestrationDecision,
        completed: Boolean,
    ): String = "$index:" +
        "next=${decision.nextAttempt?.let(::attemptName) ?: "-"};" +
        "final=${distances(decision.finalRoutes)};" +
        "propagate=${if (decision.propagateFailure) 1 else 0};" +
        "done=${if (completed) 1 else 0}"

    private fun distances(routes: List<RoutingParsedRoute>?): String =
        routes?.joinToString(",") { route -> route.totalDistanceM.toLong().toString() } ?: "-"

    private fun route(distance: Double) = RoutingParsedRoute(
        polyline = emptyList(),
        steps = emptyList(),
        totalDistanceM = distance,
        totalDurationS = 60.0,
    )

    private fun provider(value: String): RoutingProvider = when (value) {
        "osrm" -> RoutingProvider.OSRM
        "openRoute" -> RoutingProvider.OPEN_ROUTE
        "graphHopper" -> RoutingProvider.GRAPH_HOPPER
        else -> error("Unknown provider $value")
    }

    private fun attemptName(value: RoutingRouteAttempt): String = when (value) {
        RoutingRouteAttempt.CONSTRAINED -> "constrained"
        RoutingRouteAttempt.UNCONSTRAINED -> "unconstrained"
    }

    private fun number(value: String): Double = when (value) {
        "NaN" -> Double.NaN
        "Infinity" -> Double.POSITIVE_INFINITY
        "-Infinity" -> Double.NEGATIVE_INFINITY
        else -> value.toDouble()
    }

    private fun decode(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
