package app.roadstr.core.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoutingRequestProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/routing_requests_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native routing request composition reproduces every Dart case`() {
        assertEquals(89, rows.size)
        for (fields in rows) {
            val actual = evaluate(fields)
            assertEquals("${fields[0]}/${fields[1]}", decode(fields[13]), actual)
        }
    }

    @Test
    fun `routing constants keep privacy and provider boundaries explicit`() {
        assertEquals(4, RoutingRequestProtocol.maxIntermediateWaypoints)
        assertEquals(45, RoutingRequestProtocol.rerouteBearingToleranceDegrees)
        assertEquals(
            "https://valhalla1.openstreetmap.de/route",
            RoutingRequestProtocol.valhallaEndpoint,
        )
        assertFalse(RoutingRequestProtocol.graphHopperPublicEndpoint.startsWith("http://"))
    }

    private fun evaluate(fields: List<String>): String {
        val origin = point(fields[2], fields[3])
        val destination = point(fields[4], fields[5])
        val arg1 = decodeOptional(fields[6])
        val arg2 = decodeOptional(fields[7])
        val arg3 = decodeOptional(fields[8])
        val arg4 = decodeOptional(fields[9])
        val number = fields[10].takeUnless { it == "-" }?.toDouble()
        val flag = fields[11].toBooleanStrict()
        val via = decode(fields[12]).takeIf(String::isNotEmpty)?.split(';')?.map { encoded ->
            val parts = encoded.split(',')
            RoutingRequestPoint(parts[0].toDouble(), parts[1].toDouble())
        }.orEmpty()

        return when (fields[0]) {
            "ors_language" -> "value=${RoutingRequestProtocol.openRouteServiceLanguage(arg1!!)}"
            "valhalla_language" -> "value=${RoutingRequestProtocol.valhallaLanguage(arg1!!)}"
            "gh_endpoint" -> "value=${RoutingRequestProtocol.graphHopperEndpoint(arg1)}"
            "ors" -> canonical(
                RoutingRequestProtocol.openRouteService(
                    origin = origin!!,
                    destination = destination!!,
                    apiKey = arg1!!,
                    languageCode = arg2!!,
                    vehicle = arg3!!,
                ),
            )

            "gh_route" -> canonical(
                RoutingRequestProtocol.graphHopperRoute(
                    origin = origin!!,
                    destination = destination!!,
                    server = RoutingRequestProtocol.graphHopperEndpoint(arg1),
                    languageCode = arg2!!,
                    vehicle = arg3!!,
                    apiKey = arg4,
                ),
            )

            "gh_probe" -> canonical(
                RoutingRequestProtocol.graphHopperProbe(
                    server = RoutingRequestProtocol.graphHopperEndpoint(arg1),
                    apiKey = arg4,
                ),
            )

            "osrm" -> canonical(
                RoutingRequestProtocol.osrmRoute(
                    origin = origin!!,
                    destination = destination!!,
                    vehicle = arg1!!,
                    endpoint = arg2,
                    via = via,
                    requestAlternatives = flag,
                    originBearingDegrees = number,
                ),
            )

            "valhalla" -> canonical(
                RoutingRequestProtocol.valhalla(
                    origin = origin!!,
                    destination = destination!!,
                    languageCode = arg1!!,
                    endpoint = arg2,
                    costingPolicy = arg3!!.toValhallaPolicy(),
                ),
            )

            "retime" -> canonical(
                RoutingRequestProtocol.osrmRetime(
                    waypoints = arg1!!,
                    endpoint = arg2,
                ),
            )

            else -> error("Unknown routing fixture operation: ${fields[0]}")
        }
    }

    private fun String.toValhallaPolicy(): ValhallaCostingPolicy = when (this) {
        "hardHighwayAndTollExclusion" -> ValhallaCostingPolicy.HardHighwayAndTollExclusion
        "softHighwayAndTollAvoidance" -> ValhallaCostingPolicy.SoftHighwayAndTollAvoidance
        "avoidTracks" -> ValhallaCostingPolicy.AvoidTracks
        else -> error("Unknown Valhalla policy: $this")
    }

    private fun point(latitude: String, longitude: String): RoutingRequestPoint? =
        if (latitude == "-") null else RoutingRequestPoint(latitude.toDouble(), longitude.toDouble())

    private fun canonical(request: RoutingProviderRequest): String {
        val headers = request.headers.entries
            .sortedBy { it.key }
            .joinToString(",") { "${encode(it.key)}:${encode(it.value)}" }
        return listOf(
            "method=${request.method.fixtureName}",
            "uri=${request.uri}",
            "headers=$headers",
            "body=${request.body?.let(::encode) ?: "-"}",
        ).joinToString("\n")
    }

    private fun decodeOptional(encoded: String): String? =
        encoded.takeUnless { it == "-" }?.let(::decode)

    private fun decode(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
