package app.roadstr.feature.savedroute

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseStep
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.core.network.RoutingSpeedLimitEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSavedRouteTest {
    @Test
    fun `complete route survives serialization with maneuver metadata`() {
        val saved = route("coastal-drive", "Coastal drive")

        val decoded = NativeSavedRouteProtocol.decode(NativeSavedRouteProtocol.encode(listOf(saved))).single()

        assertEquals(saved, decoded)
        assertEquals("A12", decoded.steps.single().roadRef)
        assertEquals("Harbour Road", decoded.steps.single().roadName)
        assertEquals(3, decoded.steps.single().exitNumber)
        assertEquals(listOf(90, null), decoded.speedLimits.map { it.speedKmh })
        assertEquals(saved.parsedRoute(), decoded.parsedRoute())
    }

    @Test
    fun `precision six geometry round trips negative and positive coordinates`() {
        val points = listOf(
            RoutingResponsePoint(-33.8688204, 151.2092955),
            RoutingResponsePoint(-33.8690014, 151.2100025),
            RoutingResponsePoint(35.6761919, 139.6503106),
        )

        val encoded = NativeSavedRouteProtocol.encodePolyline6(points)
        val decoded = NativeSavedRouteProtocol.decodePolyline6(encoded)!!

        assertEquals(-33.868820, decoded[0].latitude, 0.0000001)
        assertEquals(151.209296, decoded[0].longitude, 0.0000001)
        assertEquals(35.676192, decoded[2].latitude, 0.0000001)
        assertEquals(139.650311, decoded[2].longitude, 0.0000001)
    }

    @Test
    fun `version one migrates engine and offline defaults`() {
        val current = NativeSavedRouteProtocol.encode(listOf(route("legacy", "Legacy")))
        val legacy = current
            .replace("\"schema\":2", "\"schema\":1")
            .replace(Regex(",\"engine\":\"[^\"]+\""), "")
            .replace(",\"offlineRecalculable\":false", "")

        val decoded = NativeSavedRouteProtocol.decode(legacy).single()

        assertEquals("legacy", decoded.id)
        assertEquals(decoded.providerId, decoded.engineId)
        assertFalse(decoded.offlineRecalculable)
    }

    @Test
    fun `corruption unsupported versions and oversized values fail closed`() {
        assertTrue(NativeSavedRouteProtocol.decode("not-json").isEmpty())
        assertTrue(NativeSavedRouteProtocol.decode("{\"schema\":99,\"routes\":[]}").isEmpty())
        assertTrue(
            NativeSavedRouteProtocol.decode("x".repeat(NativeSavedRouteProtocol.MAX_STORED_BYTES + 1)).isEmpty(),
        )
        val damaged = NativeSavedRouteProtocol.encode(listOf(route("bad", "Bad")))
            .replace("\"distance\":10000.0", "\"distance\":-1")
        assertTrue(NativeSavedRouteProtocol.decode(damaged).isEmpty())
    }

    @Test
    fun `upsert keeps stable id newest first and enforces the route limit`() {
        var routes = emptyList<NativeSavedRoute>()
        repeat(NativeSavedRouteProtocol.MAX_ROUTES + 5) { index ->
            routes = NativeSavedRouteProtocol.upsert(
                routes,
                route("route-$index", "Route $index", modified = index.toLong() + 10),
            )
        }
        val replacement = route("route-10", "Renamed", modified = 100)
        routes = NativeSavedRouteProtocol.upsert(routes, replacement)

        assertEquals(NativeSavedRouteProtocol.MAX_ROUTES, routes.size)
        assertEquals(replacement, routes.first())
        assertEquals(1, routes.count { it.id == "route-10" })
    }

    @Test
    fun `ordered intermediate stops are retained`() {
        val saved = route("ordered", "Ordered").copy(
            stops = listOf(
                stop("Start", 48.8566, 2.3522),
                stop("First", 50.8503, 4.3517),
                stop("Second", 52.3676, 4.9041),
                stop("Finish", 52.52, 13.405),
            ),
        )

        val decoded = NativeSavedRouteProtocol.decode(NativeSavedRouteProtocol.encode(listOf(saved))).single()

        assertEquals(listOf("Start", "First", "Second", "Finish"), decoded.stops.map { it.label })
    }

    @Test
    fun `route marked for recalculation cannot navigate from stale geometry`() {
        val pending = NativeSavedRouteProtocol.markForRecalculation(route("pending", "Pending"), 20)

        assertEquals(NativeSavedRouteState.NeedsRecalculation, pending.state)
        assertNull(pending.parsedRoute())
        assertNull(pending.geometryPolyline6)
        assertTrue(pending.steps.isEmpty())
    }

    @Test
    fun `store performs save reopen update and delete through encrypted boundary`() {
        var ciphertext: String? = null
        val store = NativeSavedRoutesStore(
            readEncryptedValue = { ciphertext?.removePrefix("sealed:") },
            writeEncryptedValue = { plaintext -> ciphertext = "sealed:$plaintext"; true },
            removeEncryptedValue = { ciphertext = null; true },
        )
        val first = route("trip", "Weekend")

        assertEquals(listOf(first), store.upsert(emptyList(), first))
        assertTrue(ciphertext!!.startsWith("sealed:"))
        assertEquals(listOf(first), store.load())
        val renamed = NativeSavedRouteProtocol.rename(first, "Long weekend", 30)
        assertEquals(listOf(renamed), store.upsert(store.load(), renamed))
        assertTrue(store.remove(store.load(), renamed.id)!!.isEmpty())
        assertNull(ciphertext)
    }

    @Test
    fun `store leaves caller state unchanged when encrypted write fails`() {
        val initial = listOf(route("kept", "Kept"))
        val store = NativeSavedRoutesStore(
            readEncryptedValue = { null },
            writeEncryptedValue = { false },
            removeEncryptedValue = { false },
        )

        assertNull(store.upsert(initial, route("new", "New")))
        assertEquals(listOf("kept"), initial.map { it.id })
    }

    private fun route(id: String, name: String, modified: Long = 10): NativeSavedRoute =
        NativeSavedRouteProtocol.create(
            id = id,
            name = name,
            createdAtEpochMillis = minOf(10, modified),
            nowEpochMillis = modified,
            stops = listOf(
                stop("Start", 48.8566, 2.3522),
                stop("Finish", 52.52, 13.405),
            ),
            preferences = NativeSavedRoutePreferences(
                profile = "driving",
                avoidance = RoutingRouteAvoidance.HighwayAndTollFree,
                avoidUnpavedRoads = true,
            ),
            providerId = "osrm",
            engineId = "osrm",
            route = RoutingParsedRoute(
                polyline = listOf(
                    RoutingResponsePoint(48.8566, 2.3522),
                    RoutingResponsePoint(50.8503, 4.3517),
                    RoutingResponsePoint(52.52, 13.405),
                ),
                steps = listOf(
                    RoutingResponseStep(
                        instruction = "Take the third exit",
                        direction = "roundabout",
                        modifier = "right",
                        distanceM = 320.5,
                        location = RoutingResponsePoint(50.8503, 4.3517),
                        exitNumber = 3,
                        roundaboutArmCount = 5,
                        exitLabel = "Harbour",
                        roadName = "Harbour Road",
                        roadRef = "A12",
                    ),
                ),
                totalDistanceM = 10_000.0,
                totalDurationS = 900.0,
                speedLimits = listOf(
                    RoutingSpeedLimitEntry(0.0, 90),
                    RoutingSpeedLimitEntry(5_000.0, null),
                ),
                avoidance = RoutingRouteAvoidance.HighwayAndTollFree,
                fromAvoidanceRouter = true,
            ),
        )

    private fun stop(label: String, latitude: Double, longitude: Double) =
        NativeSavedRouteStop(label, RoutingResponsePoint(latitude, longitude))
}
