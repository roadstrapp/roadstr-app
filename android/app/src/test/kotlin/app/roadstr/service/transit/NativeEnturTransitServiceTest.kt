package app.roadstr.service.transit

import app.roadstr.core.network.EnturTransitProtocol
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeTransitHttpTransport
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEnturTransitServiceTest {
    private val query = NativeTransitQuery(
        from = TransitRequestPoint(59.9111, 10.7528),
        to = TransitRequestPoint(59.9290, 10.7148),
        departure = Instant.parse("2026-10-12T08:00:00Z"),
        languageCode = "nb",
    )

    @Test
    fun `request is an identified parameterized GraphQL POST`() = runBlocking {
        var captured: app.roadstr.service.network.NativeHttpRequest? = null
        val result = service(NativeTransitHttpTransport { request, _ ->
            captured = request
            response(fixture())
        }).plan(query)

        assertTrue(result is NativeTransitPlan)
        val request = requireNotNull(captured)
        assertEquals(NativeHttpMethod.Post, request.method)
        assertEquals(EnturTransitProtocol.CLIENT_NAME, request.headers["ET-Client-Name"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertNotNull(request.body)
        assertTrue(request.body!!.contains("\"variables\""))
        assertTrue(request.body!!.contains("$" + "from"))
        assertTrue(request.body!!.contains("59.9111"))
        assertFalse(request.body!!.contains("trip(from: {"))
    }

    @Test
    fun `Transmodel response maps scoped ids realtime platforms alerts and geometry`() = runBlocking {
        val plan = service(NativeTransitHttpTransport { _, _ -> response(fixture()) }).plan(query) as NativeTransitPlan
        val transitLeg = plan.itineraries.single().transitLegs.single()

        assertEquals("entur", plan.plan.provider.id)
        assertEquals("entur", transitLeg.tripId!!.providerId)
        assertEquals("RUT:ServiceJourney:31-1001", transitLeg.tripId!!.value)
        assertEquals("31", transitLeg.routeShortName)
        assertEquals("A", transitLeg.from!!.expectedPlatform)
        assertTrue(transitLeg.realTime)
        assertTrue(transitLeg.geometry.isNotEmpty())
        assertEquals("Minor delay", transitLeg.alerts.single().header)
        assertEquals("Nationaltheatret", transitLeg.intermediateStops.single().name)
    }

    @Test
    fun `partial GraphQL errors retain usable data but errors without data fail`() = runBlocking {
        val partial = fixture().replaceFirst("{", "{\"errors\":[{\"message\":\"partial\"}],")
        assertTrue(service(NativeTransitHttpTransport { _, _ -> response(partial) }).plan(query) is NativeTransitPlan)

        val failed = service(NativeTransitHttpTransport { _, _ ->
            response("{\"errors\":[{\"message\":\"invalid\"}],\"data\":null}")
        }).plan(query) as NativeTransitFailure
        assertFalse(failed.transient)
        assertFalse(failed.message.contains("invalid"))
    }

    @Test
    fun `timeout retries within the shared interactive budget`() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls++
                if (calls < 3) throw NativeHttpException(NativeHttpFailureKind.Timeout)
                response(fixture())
            },
            sleep = waits::add,
        ).plan(query)

        assertTrue(result is NativeTransitPlan)
        assertEquals(3, calls)
        assertEquals(listOf(400L, 800L), waits)
    }

    @Test
    fun `router uses Norway coverage and only transiently falls back`() = runBlocking {
        val enturTransient = service(NativeTransitHttpTransport { _, _ ->
            throw NativeHttpException(NativeHttpFailureKind.Transport)
        })
        var fallbackCalls = 0
        val fallback = object : NativeTransitProvider {
            override val descriptor = NativeTransitService.TRANSITOUS_DESCRIPTOR
            override suspend fun plan(query: NativeTransitQuery): NativeTransitResult {
                fallbackCalls++
                return NativeTransitUnavailable
            }
        }
        val router = NativeTransitProviderRouter(listOf(enturTransient, fallback))

        assertEquals(NativeTransitUnavailable, router.plan(query))
        assertEquals(1, fallbackCalls)

        val outsideNorway = query.copy(
            from = TransitRequestPoint(45.46, 9.19),
            to = TransitRequestPoint(45.48, 9.20),
        )
        assertEquals(NativeTransitUnavailable, router.plan(outsideNorway))
        assertEquals(2, fallbackCalls)
    }

    /** Opt-in, one identified and bounded request; excluded from normal test runs. */
    @Test
    fun `live Entur probe returns an Oslo journey`() = runBlocking {
        if (System.getenv("ROADSTR_ENTUR_LIVE_PROBE") != "1") return@runBlocking
        val result = NativeEnturTransitService().plan(query.copy(departure = Instant.now().plusSeconds(300)))
            as NativeTransitPlan
        assertEquals("entur", result.plan.provider.id)
        assertTrue(result.itineraries.isNotEmpty())
        assertTrue(result.itineraries.any { itinerary -> itinerary.transitLegs.isNotEmpty() })
    }

    private fun service(
        transport: NativeTransitHttpTransport,
        sleep: suspend (Long) -> Unit = {},
    ) = NativeEnturTransitService(
        transport = transport,
        endpoint = "https://example.test/graphql",
        sleep = sleep,
    )

    private fun response(body: String) = NativeHttpResponse(200, "test", emptyMap(), body.toByteArray())

    private fun fixture(): String {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            val candidate = File(current, "test/fixtures/entur_trip_oslo.json")
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile
        }
        error("Unable to locate entur_trip_oslo.json")
    }
}
