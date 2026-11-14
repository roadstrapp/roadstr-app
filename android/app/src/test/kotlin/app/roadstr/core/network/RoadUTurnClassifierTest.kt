package app.roadstr.core.network

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.RoadUTurnClassifier.NearbyRoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadUTurnClassifierTest {
    @Test
    fun `osm tags that forbid turning round`() {
        for (highway in listOf("motorway", "motorway_link", "trunk", "trunk_link")) {
            assertTrue(highway, RoadUTurnClassifier.forbidsUTurnByOsmTags(mapOf("highway" to highway)))
        }
        assertTrue(RoadUTurnClassifier.forbidsUTurnByOsmTags(mapOf("highway" to "primary", "motorroad" to "yes")))
        assertTrue(RoadUTurnClassifier.forbidsUTurnByOsmTags(mapOf("highway" to "primary", "expressway" to "yes")))
        for (highway in listOf("primary", "secondary", "residential", "service", "unclassified")) {
            assertFalse(highway, RoadUTurnClassifier.forbidsUTurnByOsmTags(mapOf("highway" to highway)))
        }
    }

    @Test
    fun `the nearest road decides, a farther motorway does not`() {
        // On a service road 8 m from the motorway: not a motorway.
        assertEquals(
            false,
            RoadUTurnClassifier.decide(listOf(NearbyRoad(3.0, false), NearbyRoad(11.0, true))),
        )
        // On the motorway, with the opposite carriageway and a parallel street farther off.
        assertEquals(
            true,
            RoadUTurnClassifier.decide(
                listOf(NearbyRoad(2.0, true), NearbyRoad(22.0, true), NearbyRoad(30.0, false)),
            ),
        )
        // A ramp overlapping the carriageway ties with it.
        assertEquals(true, RoadUTurnClassifier.decide(listOf(NearbyRoad(2.0, false), NearbyRoad(4.0, true))))
    }

    @Test
    fun `nothing close enough is unknown, not safe`() {
        assertNull(RoadUTurnClassifier.decide(emptyList()))
        assertNull(RoadUTurnClassifier.decide(listOf(NearbyRoad(90.0, true))))
    }

    @Test
    fun `valhalla locate edges are read by class and use`() {
        val body = """[{"input_lat":44.0,"input_lon":11.0,"edges":[
            {"distance":2.5,"edge":{"classification":{"classification":"motorway","link":false},"use":"road"},
             "edge_info":{"speed_limit":130}},
            {"distance":21.0,"edge":{"classification":{"classification":"motorway","link":false},"use":"road"}},
            {"distance":35.0,"edge":{"classification":{"classification":"service_other"},"use":"service_road"}}]}]"""
        val roads = RoadUTurnClassifier.parseValhallaLocate(body)!!
        assertEquals(3, roads.size)
        assertEquals(true, RoadUTurnClassifier.decide(roads))

        val residential = """[{"edges":[
            {"distance":4.0,"edge":{"classification":{"classification":"residential"},"use":"road"}},
            {"distance":9.0,"edge":{"classification":{"classification":"motorway"},"use":"road"}}]}]"""
        assertEquals(false, RoadUTurnClassifier.decide(RoadUTurnClassifier.parseValhallaLocate(residential)!!))

        val ramp = """[{"edges":[{"distance":3.0,"edge":{"classification":{"classification":"primary"},"use":"ramp"}}]}]"""
        assertEquals(true, RoadUTurnClassifier.decide(RoadUTurnClassifier.parseValhallaLocate(ramp)!!))

        assertNull(RoadUTurnClassifier.parseValhallaLocate("not json"))
        assertNull(RoadUTurnClassifier.decide(RoadUTurnClassifier.parseValhallaLocate("""[{"edges":[]}]""")!!))
    }

    @Test
    fun `overpass ways are measured against the position`() {
        // The position sits on the first way (a motorway running east); a service road runs 12 m to the north.
        val body = """{"elements":[
            {"type":"way","tags":{"highway":"motorway","oneway":"yes"},
             "geometry":[{"lat":44.0,"lon":11.0},{"lat":44.0,"lon":11.01}]},
            {"type":"way","tags":{"highway":"service"},
             "geometry":[{"lat":44.0001,"lon":11.0},{"lat":44.0001,"lon":11.01}]}]}"""
        val roads = RoadUTurnClassifier.parseOverpassWays(body, GeoPoint(44.0, 11.005))!!
        assertEquals(2, roads.size)
        assertEquals(0.0, roads.minOf { it.distanceMeters }, 0.5)
        assertEquals(true, RoadUTurnClassifier.decide(roads))

        val onService = RoadUTurnClassifier.parseOverpassWays(body, GeoPoint(44.0001, 11.005))!!
        assertEquals(false, RoadUTurnClassifier.decide(onService))

        assertNull(RoadUTurnClassifier.parseOverpassWays("<html>", GeoPoint(44.0, 11.0)))
    }
}
