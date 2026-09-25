package app.roadstr.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitFormatterParityTest {
    @Test
    fun `metric and imperial display values match Dart`() {
        val metric = UnitFormatter(imperial = false)
        assertEquals("0 m", metric.formatDistance(49.9))
        assertEquals("now", metric.formatDistance(20.0, nowLabel = "now"))
        assertEquals("999 m", metric.formatDistance(999.0))
        assertEquals("1.0 km", metric.formatDistance(1000.0))
        assertEquals("123 m", metric.formatAltitude(123.4))
        assertEquals("124 m", metric.formatAltitude(123.6))
        assertEquals("-2 m", metric.formatAltitude(-1.5))

        val imperial = UnitFormatter(imperial = true)
        assertEquals("0 ft", imperial.formatDistance(20.0))
        assertEquals("200 ft", imperial.formatDistance(60.0))
        assertEquals("1.0 mi", imperial.formatDistance(1609.0))
        assertEquals("328 ft", imperial.formatAltitude(100.0))
        assertEquals("mph", imperial.speedUnit)
        assertTrue(imperial.toDisplaySpeed(100.0) in 62.13..62.14)
    }

    @Test
    fun `spoken distance respects locale punctuation and spacing`() {
        val metric = UnitFormatter(imperial = false)
        assertEquals("in 300 meters", metric.ttsDistanceInline(300, "en"))
        assertEquals("tra 300 metri", metric.ttsDistanceInline(300, "it"))
        assertEquals("300メートル先で", metric.ttsDistanceInline(300, "ja"))
        assertEquals("在300米后", metric.ttsDistanceInline(300, "zh"))
        assertEquals("Tra 4,5 chilometri, ", metric.ttsDistancePrefix(4500, "it"))
        assertEquals("", metric.ttsDistanceInline(0, "en"))
        assertEquals(
            "300メートル先で出口です",
            metric.joinDistance("300メートル先で", "出口です", "ja"),
        )

        val imperial = UnitFormatter(imperial = true)
        assertEquals("in 1.0 miles", imperial.ttsDistanceInline(1609, "en"))
        assertEquals("miglia orarie", imperial.speedUnitForSpeech("it"))
    }
}
