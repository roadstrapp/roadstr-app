package app.roadstr.core.network

import java.util.Base64
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutingResponseProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/routing_responses_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native routing response normalization reproduces every Dart case`() {
        assertEquals(59, rows.size)
        for (fields in rows) {
            val actual = evaluate(fields)
            assertEquals("${fields[0]}/${fields[1]}", decode(fields[6]), actual)
        }
    }

    @Test
    fun `response boundaries and route cleanup remain explicit`() {
        assertEquals(20, RoutingResponseProtocol.MAX_ROUNDABOUT_ARMS)
        assertEquals(250_000, RoutingResponseProtocol.MAX_ROUTE_POINTS)
        assertEquals(60_000, RoutingResponseProtocol.MAX_ROUTE_STEPS)

        val location = RoutingResponsePoint(45.0, 9.0)
        val cleaned = RoutingResponseProtocol.validate(
            RoutingParsedRoute(
                polyline = listOf(location, RoutingResponsePoint(45.1, 9.1)),
                steps = listOf(
                    RoutingResponseStep(
                        instruction = "Continue",
                        direction = "continue",
                        distanceM = 100.0,
                        location = location,
                        roadName = "Old name",
                        roadRef = "A1",
                    ),
                    RoutingResponseStep(
                        instruction = "New name",
                        direction = "new name",
                        modifier = "straight",
                        distanceM = 50.0,
                        location = location,
                    ),
                    RoutingResponseStep(
                        instruction = "Roundabout",
                        direction = "roundabout",
                        distanceM = 100.0,
                        location = location,
                        exitNumber = 21,
                        roundaboutArmCount = 2,
                        exitLabel = "X".repeat(33),
                        roadName = "Removed with invalid decorations",
                        roadRef = "SS1",
                    ),
                ),
                totalDistanceM = 250.0,
                totalDurationS = 30.0,
            ),
        )
        assertEquals(2, cleaned.steps.size)
        assertEquals(150.0, cleaned.steps.first().distanceM, 0.0)
        assertEquals("Old name", cleaned.steps.first().roadName)
        assertEquals("A1", cleaned.steps.first().roadRef)
        assertNull(cleaned.steps.last().exitNumber)
        assertNull(cleaned.steps.last().roundaboutArmCount)
        assertNull(cleaned.steps.last().exitLabel)
        assertEquals("", cleaned.steps.last().roadName)
        assertEquals("", cleaned.steps.last().roadRef)

        assertThrows(RoutingResponseException::class.java) {
            RoutingResponseProtocol.validate(
                cleaned.copy(
                    speedLimits = listOf(
                        RoutingSpeedLimitEntry(50.0, 30),
                        RoutingSpeedLimitEntry(40.0, 50),
                    ),
                ),
            )
        }
    }

    private fun evaluate(fields: List<String>): String {
        val operation = fields[0]
        val language = fields[2]
        val fallback = RoutingResponsePoint(fields[3].toDouble(), fields[4].toDouble())
        val input = decode(fields[5])
        return try {
            when (operation) {
                "osrm" -> RoutingResponseProtocol.parseOsrmRoutes(input, language)
                    .joinToString("\n--route--\n", transform = ::canonical)

                "ors" -> canonical(RoutingResponseProtocol.parseOpenRouteService(input, fallback))
                "graphhopper" -> canonical(RoutingResponseProtocol.parseGraphHopper(input, fallback))
                "valhalla" -> canonical(RoutingResponseProtocol.parseValhalla(input))
                "retime" -> canonicalRetime(RoutingResponseProtocol.parseOsrmRetimeLegs(input))
                "exit" -> "exit=${RoutingResponseProtocol.parseExitNumber(input) ?: "-"}"
                "polyline" -> RoutingResponseProtocol.decodeValhallaPolyline(input)
                    .joinToString(";", transform = ::point)

                else -> error("Unknown routing fixture operation: $operation")
            }
        } catch (error: RoutingResponseException) {
            "error=${error.responseMessage}"
        } catch (error: Exception) {
            "error=$error"
        }
    }

    private fun canonical(response: ValhallaParsedResponse): String = listOf(
        canonical(response.route),
        "summary=${response.summary["has_highway"] ?: "-"},${response.summary["has_toll"] ?: "-"}",
    ).joinToString("\n")

    private fun canonicalRetime(legs: List<OsrmRetimeLeg>?): String = legs
        ?.joinToString(";") { leg ->
            "${leg.distanceM?.let(::fixed3) ?: "-"},${leg.durationS?.let(::fixed3) ?: "-"}"
        }
        ?: "null"

    private fun canonical(route: RoutingParsedRoute): String = listOf(
        "distance=${fixed3(route.totalDistanceM)}",
        "duration=${fixed3(route.totalDurationS)}",
        "avoidance=${route.avoidance.fixtureName}",
        "from_avoidance=${route.fromAvoidanceRouter}",
        "points=${route.polyline.joinToString(";", transform = ::point)}",
        "steps=${route.steps.joinToString(";", transform = ::step)}",
        "speeds=${route.speedLimits.joinToString(";") { entry ->
            "${fixed3(entry.distFromStartM)},${entry.speedKmh ?: "-"}"
        }}",
    ).joinToString("\n")

    private fun step(step: RoutingResponseStep): String = listOf(
        encode(step.instruction),
        encode(step.direction),
        encode(step.modifier),
        fixed3(step.distanceM),
        point(step.location),
        step.exitNumber ?: "-",
        step.roundaboutArmCount ?: "-",
        step.exitLabel?.let(::encode) ?: "-",
        encode(step.roadName),
        encode(step.roadRef),
    ).joinToString(",")

    private val RoutingRouteAvoidance.fixtureName: String
        get() = when (this) {
            RoutingRouteAvoidance.None -> "none"
            RoutingRouteAvoidance.HighwayAndTollFree -> "highwayAndTollFree"
            RoutingRouteAvoidance.MinimizedHighwaysAndTolls -> "minimizedHighwaysAndTolls"
            RoutingRouteAvoidance.OffRoadAvoided -> "offRoadAvoided"
        }

    private fun point(value: RoutingResponsePoint): String =
        "${fixed6(value.latitude)},${fixed6(value.longitude)}"

    private fun fixed3(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun fixed6(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private fun decode(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
