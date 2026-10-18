package app.roadstr.core.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyRecordCodecTest {
    private fun session(
        id: String = "session-0001",
        meters: Long = 1_000L,
        status: JourneySessionStatus = JourneySessionStatus.COMPLETED,
    ) = JourneySession(
        id = id,
        deviceId = "device-0001",
        mode = JourneyMode.CAR,
        startedAtEpochMillis = 1_000L,
        endedAtEpochMillis = if (status == JourneySessionStatus.ACTIVE || status == JourneySessionStatus.PAUSED) null else 2_000L,
        validatedMeters = meters,
        uncertainMeters = 12L,
        status = status,
    )

    @Test
    fun `record round trips without coordinates`() {
        val record = JourneyRecord(
            sessions = listOf(session()),
            unlocks = listOf(JourneyAchievementUnlock(AchievementCatalog.achievements.first().id, 2_000L)),
        )
        assertEquals(record, JourneyRecordCodec.decode(JourneyRecordCodec.encode(record)))
    }

    @Test
    fun `controller is opt in pauses and completes`() {
        var saved = JourneyRecord()
        var now = 10_000L
        val controller = JourneyController(saved, "device-0001", false, { saved = it }, { now }, { "session-0001" })
        assertNull(controller.start(JourneyMode.CAR))
        assertTrue(controller.setEnabled(true))
        assertNotNull(controller.start(JourneyMode.CAR))
        controller.submit(JourneyLocationSample(45.0, 9.0, 1_000L, 5.0, 0.0))
        assertTrue(controller.pause())
        assertEquals(JourneyFixDecision.INVALID, controller.submit(JourneyLocationSample(45.01, 9.0, 2_000L, 5.0, 0.0)).decision)
        assertTrue(controller.resume())
        controller.submit(JourneyLocationSample(45.0, 9.0, 3_000L, 5.0, 0.0))
        controller.submit(JourneyLocationSample(45.01, 9.0, 103_000L, 5.0, 11.0))
        now = 120_000L
        val ended = controller.complete()
        assertEquals(JourneySessionStatus.COMPLETED, ended?.status)
        assertTrue(saved.sessions.single().validatedMeters > 1_000L)
        assertTrue(saved.unlocks.isNotEmpty())
    }

    @Test
    fun `active checkpoint recovers as interrupted after process death`() {
        val active = JourneyRecord(listOf(session(status = JourneySessionStatus.ACTIVE)))
        var saved: JourneyRecord? = null
        val controller = JourneyController(active, "device-0001", true, { saved = it }, { 5_000L })
        assertNull(controller.state.value.activeSession)
        assertEquals(JourneySessionStatus.INTERRUPTED, controller.state.value.record.sessions.single().status)
        assertEquals(JourneySessionStatus.INTERRUPTED, saved?.sessions?.single()?.status)
    }

    @Test
    fun `multi device merge deduplicates session ids`() {
        val first = session(meters = 1_000L)
        val controller = JourneyController(JourneyRecord(listOf(first)), "device-0001", true, {}, { 9_000L })
        val richer = first.copy(validatedMeters = 1_500L)
        val second = session("session-0002", 2_000L).copy(deviceId = "device-0002")
        assertTrue(controller.merge(JourneyRecord(listOf(richer, second))))
        assertEquals(3_500L, controller.state.value.statistics.totalMeters)
        assertEquals(2, controller.state.value.record.sessions.size)
        assertFalse(controller.merge(controller.state.value.record))
    }
}

