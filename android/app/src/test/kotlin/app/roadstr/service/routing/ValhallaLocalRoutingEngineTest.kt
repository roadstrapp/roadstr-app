package app.roadstr.service.routing

import app.roadstr.core.network.RoutingRequestPoint
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ValhallaLocalRoutingEngineTest {
    @Test
    fun `raw response is normalized through shared protocol and session is reused`() = runBlocking {
        val factory = FakeFactory(SUCCESS)
        val engine = ValhallaLocalRoutingEngine(factory)
        val request = request()

        val first = engine.route(request)
        val second = engine.route(request)

        assertEquals(1, factory.opens)
        assertSame(factory.session, factory.lastOpened)
        assertEquals(1_000.0, (first as RoutingEngineOutcome.Success).routes.single().totalDistanceM, 0.0)
        assertEquals(1_000.0, (second as RoutingEngineOutcome.Success).routes.single().totalDistanceM, 0.0)
        engine.close()
        assertEquals(1, factory.session.closes)
    }

    @Test
    fun `dataset version replacement closes old session`() = runBlocking {
        val factory = FakeFactory(SUCCESS)
        val engine = ValhallaLocalRoutingEngine(factory)
        engine.route(request(version = 1))
        val first = factory.lastOpened

        engine.route(request(version = 2))

        assertEquals(2, factory.opens)
        assertEquals(1, first?.closes)
    }

    @Test
    fun `error 171 is area not downloaded`() = runBlocking {
        val engine = ValhallaLocalRoutingEngine(FakeFactory("""{"error_code":171,"error":"missing"}"""))

        assertEquals(
            RoutingEngineOutcome.Unavailable(RoutingEngineUnavailableReason.AreaNotDownloaded),
            engine.route(request()),
        )
    }

    @Test
    fun `bad response and broken dataset are distinct`() = runBlocking {
        val malformed = ValhallaLocalRoutingEngine(FakeFactory("{}"))
        val broken = ValhallaLocalRoutingEngine(LocalValhallaSessionFactory { throw IOException("bad") })

        assertEquals(
            RoutingEngineOutcome.Failed(RoutingEngineFailureKind.InvalidResponse),
            malformed.route(request()),
        )
        assertEquals(
            RoutingEngineOutcome.Unavailable(RoutingEngineUnavailableReason.DatasetInvalid),
            broken.route(request()),
        )
    }

    @Test
    fun `request contains via points language and avoidance without coordinates in errors`() = runBlocking {
        val factory = FakeFactory(SUCCESS)
        val engine = ValhallaLocalRoutingEngine(factory)

        engine.route(
            request().copy(
                via = listOf(RoutingRequestPoint(10.5, 20.5)),
                languageCode = "it",
                avoidance = app.roadstr.core.network.RoutingRouteAvoidance.HighwayAndTollFree,
            ),
        )

        val raw = factory.session.requests.single()
        assert(raw.contains("\"lat\":10.5"))
        assert(raw.contains("\"language\":\"it-IT\""))
        assert(raw.contains("\"exclude_highways\":true"))
    }

    private fun request(version: Int = 1) = RoutingEngineRequest(
        origin = RoutingRequestPoint(1.0, 2.0),
        destination = RoutingRequestPoint(3.0, 4.0),
        localDataset = LocalRoutingDataset("region-example", version, "/private/tiles.tar", "build-1"),
    )

    private class FakeFactory(private val response: String) : LocalValhallaSessionFactory {
        var opens = 0
        var lastOpened: FakeSession? = null
        val session: FakeSession get() = requireNotNull(lastOpened)

        override fun open(dataset: LocalRoutingDataset): LocalValhallaSession {
            opens++
            return FakeSession(response).also { lastOpened = it }
        }
    }

    private class FakeSession(private val response: String) : LocalValhallaSession {
        val requests = mutableListOf<String>()
        var closes = 0
        override fun routeRaw(requestJson: String): String {
            requests += requestJson
            return response
        }
        override fun close() { closes++ }
    }

    private companion object {
        const val SUCCESS = """{"trip":{"status":0,"summary":{"length":1.0,"time":60},"legs":[{"shape":"????","maneuvers":[{"type":1,"instruction":"Depart","length":1.0,"begin_shape_index":0}]}]}}"""
    }
}
