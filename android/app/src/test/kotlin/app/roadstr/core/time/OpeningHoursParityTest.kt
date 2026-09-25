package app.roadstr.core.time

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningHoursParityTest {
    private fun monday(hour: Int, minute: Int) = LocalDateTime.of(2024, 1, 1, hour, minute)
    private fun saturday(hour: Int, minute: Int) = LocalDateTime.of(2024, 1, 6, hour, minute)
    private fun sunday(hour: Int, minute: Int) = LocalDateTime.of(2024, 1, 7, hour, minute)

    @Test
    fun `always-open forms have no next transition`() {
        for (raw in listOf("24/7", "00:00-24:00", "Mo-Su 00:00-24:00")) {
            val status = OpeningHours.evaluate(raw, sunday(23, 59))
            assertEquals(OpenState.OPEN, status.state)
            assertNull(status.nextChange)
        }
    }

    @Test
    fun `weekday range reports close and next open`() {
        val open = OpeningHours.evaluate("Mo-Fr 08:00-18:00", monday(10, 0))
        assertEquals(OpenState.OPEN, open.state)
        assertEquals(monday(18, 0), open.nextChange)

        val before = OpeningHours.evaluate("Mo-Fr 08:00-18:00", monday(7, 0))
        assertEquals(OpenState.CLOSED, before.state)
        assertEquals(monday(8, 0), before.nextChange)

        val weekend = OpeningHours.evaluate("Mo-Fr 08:00-18:00", saturday(10, 0))
        assertEquals(OpenState.CLOSED, weekend.state)
        assertEquals(LocalDateTime.of(2024, 1, 8, 8, 0), weekend.nextChange)
    }

    @Test
    fun `lists split shifts overrides and overlaps match Dart`() {
        val split = "Mo-Fr 08:35-12:55, 14:10-16:15; Sa-Su off"
        assertEquals(OpenState.CLOSED, OpeningHours.evaluate(split, monday(13, 0)).state)
        assertEquals(OpenState.OPEN, OpeningHours.evaluate(split, monday(9, 0)).state)
        assertEquals(OpenState.OPEN, OpeningHours.evaluate(split, monday(15, 0)).state)

        val list = "Mo,We,Fr 09:00-17:00"
        assertEquals(OpenState.OPEN, OpeningHours.evaluate(list, monday(10, 0)).state)
        assertEquals(
            OpenState.CLOSED,
            OpeningHours.evaluate(list, LocalDateTime.of(2024, 1, 2, 10, 0)).state,
        )

        val overlap = OpeningHours.evaluate("Mo 08:00-12:00,10:00-18:00", monday(11, 0))
        assertEquals(OpenState.OPEN, overlap.state)
        assertEquals(monday(18, 0), overlap.nextChange)
    }

    @Test
    fun `overnight ranges continue into the following day`() {
        val raw = "Mo-Sa 16:30-02:00, Su 15:00-01:00"
        assertEquals(OpenState.OPEN, OpeningHours.evaluate(raw, monday(23, 0)).state)
        assertEquals(
            OpenState.OPEN,
            OpeningHours.evaluate(raw, LocalDateTime.of(2024, 1, 2, 1, 0)).state,
        )
        assertEquals(
            OpenState.CLOSED,
            OpeningHours.evaluate(raw, LocalDateTime.of(2024, 1, 2, 3, 0)).state,
        )
    }

    @Test
    fun `unsupported or unsafe syntax remains unknown`() {
        val unsafe = listOf(
            "Nov-Mar: 07:00-18:00; Apr-Oct: 07:00-19:00",
            "Mo-Fr 08:00-18:00 \"by appointment\"",
            "Mo-Su sunrise-sunset",
            "",
            "Mo-Fr 08:00-18:00 unknown",
            "Mo-Fr 08:00-24:30",
            "Mo-Fr 24:00-01:00",
            "Mo-Fr 08:20-19:05, Sa 08:20-12:35; Su,PH off",
        )
        for (raw in unsafe) {
            assertEquals(raw, OpenState.UNKNOWN, OpeningHours.evaluate(raw, monday(10, 0)).state)
        }
    }
}
