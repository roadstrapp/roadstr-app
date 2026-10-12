package app.roadstr.core.network

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitOperationalProtocolTest {
    private val provider = TransitProtocol.TRANSITOUS_PROVIDER

    @Test
    fun `stop requests use documented MOTIS endpoints and scoped values`() {
        val request = TransitOperationalProtocol.searchStopsRequest(
            endpoint = "https://api.transitous.org/api/v6/plan",
            query = "Milano Centrale",
            location = TransitRequestPoint(45.484, 9.204),
            languageCode = "it",
        )

        assertEquals(
            "https://api.transitous.org/api/v1/geocode?" +
                "text=Milano%20Centrale&type=STOP&numResults=20&" +
                "place=45.484%2C9.204&language=it",
            request.uri,
        )
        assertEquals(TransitProtocol.USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun `stop search retains platforms and only removes duplicate provider ids`() {
        val result = TransitOperationalProtocol.parseStopSearch(
            """[
              {"type":"STOP","name":"Central","id":"station","lat":45.0,"lon":9.0,"tz":"Europe/Rome","modes":["RAIL","SUBWAY"],"country":"IT","importance":0.9,"areas":[{"name":"Milano","default":true}]},
              {"type":"STOP","name":"Central platform 1","id":"platform-1","lat":45.0001,"lon":9.0001,"modes":["RAIL"]},
              {"type":"STOP","name":"Duplicate","id":"station","lat":45.0,"lon":9.0},
              {"type":"PLACE","name":"Not a stop","id":"place","lat":45.0,"lon":9.0}
            ]""",
            provider,
        )

        assertEquals(listOf("station", "platform-1"), result.map { it.stop.id.value })
        assertTrue(result.first().modes.contains(TransitMode.Rail))
        assertEquals("Milano", result.first().locality)
        assertEquals("transitous", result.last().stop.id.providerId)
    }

    @Test
    fun `departure board distinguishes schedule realtime delay cancellation and platforms`() {
        val board = TransitOperationalProtocol.parseStopBoard(
            body = """{
              "place":{"name":"Central","stopId":"station","lat":45.0,"lon":9.0,"tz":"Europe/Rome"},
              "previousPageCursor":null,
              "nextPageCursor":"next",
              "stopTimes":[
                {"place":{"name":"Platform 1","stopId":"platform-1","parentId":"station","lat":45.0,"lon":9.0,"scheduledDeparture":"2026-10-11T10:00:00+02:00","departure":"2026-10-11T10:05:00+02:00","scheduledTrack":"1","track":"2","alerts":[{"headerText":"Diversion","severityLevel":"WARNING"}]},"mode":"RAIL","realTime":true,"headsign":"Airport","tripId":"trip-1","routeId":"route-1","routeShortName":"R1","routeLongName":"Airport train","agencyId":"agency","agencyName":"Rail","routeColor":"0066ad","routeTextColor":"ffffff","cancelled":false,"tripCancelled":false,"nextStops":[{"name":"Airport","stopId":"airport","lat":45.5,"lon":9.5}]},
                {"place":{"name":"Central","stopId":"station","lat":45.0,"lon":9.0,"scheduledDeparture":"2026-10-11T10:10:00+02:00"},"mode":"BUS","realTime":false,"headsign":"Museum","tripId":"trip-2","routeId":"route-2","displayName":"10","cancelled":true,"tripCancelled":true}
              ]
            }""",
            provider = provider,
            updatedAt = Instant.parse("2026-10-11T07:55:00Z"),
        )

        assertEquals(2, board.departures.size)
        assertEquals(TransitServiceState.Delayed, board.departures[0].state)
        assertEquals(300L, board.departures[0].delaySeconds)
        assertEquals("1", board.departures[0].scheduledPlatform)
        assertEquals("2", board.departures[0].expectedPlatform)
        assertEquals("station", board.departures[0].stop.parentId!!.value)
        assertEquals("Airport", board.departures[0].nextStops.single().name)
        assertEquals("Diversion", board.departures[0].alerts.single().header)
        assertEquals(TransitServiceState.Cancelled, board.departures[1].state)
        assertNull(board.departures[1].delaySeconds)
        assertEquals("next", board.nextPageCursor)
    }

    @Test
    fun `stop lines preserve provider ids and incomplete optional values`() {
        val lines = TransitOperationalProtocol.parseLines(
            """{"place":{"name":"Central","lat":45.0,"lon":9.0},"routes":[
              {"routeId":"route-1","routeShortName":"M1","routeLongName":"Red line","mode":"SUBWAY","agencyId":"agency","agencyName":"Metro","routeColor":"ff0000","routeTextColor":"ffffff"},
              {"routeId":"route-2","routeShortName":"","routeLongName":"Regional","mode":"REGIONAL_RAIL","agencyId":"agency","agencyName":"Rail"}
            ]}""",
            provider,
        )

        assertEquals(2, lines.size)
        assertEquals("transitous", lines.first().id.providerId)
        assertEquals(TransitMode.Subway, lines.first().mode)
        assertEquals(0xFFFF0000L, lines.first().colorArgb)
        assertNull(lines.last().shortName)
    }
}
