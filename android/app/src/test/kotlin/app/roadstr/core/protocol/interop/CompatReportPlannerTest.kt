package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatReportPlannerTest {
    @Test
    fun `every Roadstr category with an equivalent maps to the same wire name`() {
        for (category in RoadCategoryWire.entries) {
            val plan = CompatReportPlanner.plan(category, 38.7223, -9.1393, "")
            if (category == RoadCategoryWire.POLICE_STATION) {
                assertEquals(CompatPlan.Ineligible(CompatIneligible.NO_EQUIVALENT_CATEGORY), plan)
            } else {
                val eligible = plan as CompatPlan.Eligible
                assertEquals(category.wireKey, eligible.type.wireName)
            }
        }
    }

    @Test
    fun `coordinates are rounded to seven decimals, not truncated`() {
        val plan = CompatReportPlanner.plan(RoadCategoryWire.HAZARD, 35.1976312, -71.5429715, "") as CompatPlan.Eligible
        assertEquals(351_976_312, plan.latitudeE7)
        assertEquals(-715_429_715, plan.longitudeE7)
    }

    @Test
    fun `a comment goes to relays but keeps the report out of the radio frame`() {
        val withComment = CompatReportPlanner.plan(RoadCategoryWire.POLICE, 1.0, 2.0, "checking seatbelts") as CompatPlan.Eligible
        assertEquals("checking seatbelts", withComment.comment)
        assertFalse(withComment.frameable)
        assertTrue((CompatReportPlanner.plan(RoadCategoryWire.POLICE, 1.0, 2.0, "") as CompatPlan.Eligible).frameable)
    }

    @Test
    fun `impossible coordinates are refused`() {
        assertEquals(
            CompatPlan.Ineligible(CompatIneligible.COORDINATES_OUT_OF_RANGE),
            CompatReportPlanner.plan(RoadCategoryWire.POLICE, 91.0, 0.0, ""),
        )
        assertEquals(
            CompatPlan.Ineligible(CompatIneligible.COORDINATES_OUT_OF_RANGE),
            CompatReportPlanner.plan(RoadCategoryWire.POLICE, 0.0, Double.NaN, ""),
        )
    }

    @Test
    fun `a planned report signed with a one time key is a valid frame-ready event`() {
        val plan = CompatReportPlanner.plan(RoadCategoryWire.ACCIDENT, 45.4642035, 9.189996, "") as CompatPlan.Eligible
        val key = OneTimeKey.generate()
        val draft = CompactRoadCodec.reportDraft(key.publicKeyHex, 1_800_000_000, plan.latitudeE7, plan.longitudeE7, plan.type)
        val signed = NostrSchnorr.signEvent(draft, key.privateKeyHex)

        val frame = CompactRoadCodec.encodeReport(signed)
        assertTrue(frame is CompactOutcome.Ok)
        val back = CompactRoadCodec.decodeReport((frame as CompactOutcome.Ok).value)
        assertEquals(signed["id"], (back as CompactOutcome.Ok).value.event["id"])
    }

    @Test
    fun `one time keys are fresh, valid and unrelated`() {
        val random = SecureRandom()
        val keys = (1..20).map { OneTimeKey.generate(random) }
        assertEquals(20, keys.map { it.publicKeyHex }.toSet().size)
        for (key in keys) assertEquals(key.publicKeyHex, NostrSchnorr.publicKey(key.privateKeyHex))
        assertNotEquals(keys[0].privateKeyHex, keys[1].privateKeyHex)
    }

    @Test
    fun `publishing compatible reports is off until asked for`() {
        assertFalse(InteropPublishPolicy.OFF.enabled)
        assertFalse(InteropPublishPolicy.OFF.ephemeralKey)
    }

    @Test
    fun `a report with a comment is a valid event that the codec still refuses to encode`() {
        val plan = CompatReportPlanner.plan(RoadCategoryWire.POLICE, 48.856614, 2.3522219, "ciao") as CompatPlan.Eligible
        val key = OneTimeKey.generate()
        val signed = NostrSchnorr.signEvent(
            CompactRoadCodec.reportDraft(key.publicKeyHex, 1_800_000_000, plan.latitudeE7, plan.longitudeE7, plan.type, plan.comment),
            key.privateKeyHex,
        )
        assertEquals(CompactOutcome.Failed(CompactError.NON_EMPTY_CONTENT), CompactRoadCodec.encodeReport(signed))
        assertEquals(RoadEventLayout.COMPACT_CANONICAL, ExternalRoadReportAdapter.layoutOf(signed))
    }
}
