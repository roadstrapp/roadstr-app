package app.roadstr.service.hazards

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeZtlServiceTest {
    @Test
    fun `restricted ways and relation polygons are parsed and classified`() = runBlocking {
        val transport = FakeTransport(
            """{"elements":[
              {"type":"way","id":1,"tags":{"highway":"residential","name":"Via Roma"},
               "geometry":[{"lat":45.0000,"lon":10.0000},{"lat":45.0010,"lon":10.0000}]},
              {"type":"relation","id":2,"tags":{"boundary":"traffic_zone","name":"Centro"},
               "members":[{"role":"outer","geometry":[
                 {"lat":45.0100,"lon":10.0100},{"lat":45.0110,"lon":10.0100},
                 {"lat":45.0110,"lon":10.0110},{"lat":45.0100,"lon":10.0100}]}]}
            ]}""",
        )
        val subject = NativeZtlService(transport)
        val snapshot = subject.update(GeoPoint(45.0, 10.0))

        assertEquals(1, snapshot.restrictedWays.size)
        assertEquals("Via Roma", snapshot.restrictedWays.single().name)
        assertEquals(1, snapshot.zones.size)
        assertTrue(snapshot.isInside(GeoPoint(45.0005, 10.00002)))
        assertTrue(snapshot.isInside(GeoPoint(45.0107, 10.0104)))
        assertFalse(snapshot.isInside(GeoPoint(45.02, 10.02)))
        assertEquals(
            listOf(true, false),
            snapshot.classify(listOf(NativeMapPoint(45.0005, 10.00002), NativeMapPoint(45.02, 10.02))),
        )
        assertTrue(transport.query.contains("motor_vehicle"))
        assertTrue(transport.query.contains("traffic_zone"))
    }

    @Test
    fun `country labels use established acronyms only`() {
        assertEquals("ZTL", NativeZtlSnapshot.officialAcronym(GeoPoint(45.4, 10.9)))
        assertEquals("ZAC", NativeZtlSnapshot.officialAcronym(GeoPoint(38.72, -9.14)))
        assertEquals(null, NativeZtlSnapshot.officialAcronym(GeoPoint(52.52, 13.40)))
    }

    private class FakeTransport(private val body: String) : NativeSearchHttpTransport {
        var query = ""
        override suspend fun execute(
            request: SearchProviderRequest,
            limits: NativeHttpRequestLimits,
        ): NativeHttpResponse {
            query = URLDecoder.decode(request.body.orEmpty().removePrefix("data="), "UTF-8")
            return NativeHttpResponse(200, "", emptyMap(), body.toByteArray())
        }
    }
}
