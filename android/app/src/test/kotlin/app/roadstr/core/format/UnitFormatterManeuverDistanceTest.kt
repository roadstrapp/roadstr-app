package app.roadstr.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitFormatterManeuverDistanceTest {
    private val metric = UnitFormatter(imperial = false)

    @Test
    fun `countdown reads in round numbers, coarser the farther the exit`() {
        val cases = mapOf(
            14_400.0 to "14 km",
            9_760.0 to "10 km",
            3_330.0 to "3.5 km",
            2_000.0 to "2 km",
            1_349.0 to "1.3 km",
            1_000.0 to "1 km",
            995.0 to "1 km",
            771.0 to "800 m",
            349.0 to "350 m",
            274.0 to "250 m",
            128.0 to "150 m",
            87.0 to "90 m",
            53.0 to "50 m",
        )
        for ((metres, label) in cases) {
            assertEquals("$metres m", label, metric.formatManeuverDistance(metres))
        }
    }

    @Test
    fun `countdown steps are never finer than the tier allows`() {
        // 900 m down to 100 m, every metre: only multiples of 100 above 500 m, of 50 below.
        for (metres in 100..999) {
            val value = metric.formatManeuverDistance(metres.toDouble()).removeSuffix(" m")
            if (value.endsWith("km")) continue
            val step = if (metres >= 500 || value.toInt() >= 500) 100 else 50
            assertEquals("$metres -> $value", 0, value.toInt() % step)
        }
    }

    @Test
    fun `under fifty metres it says now`() {
        assertEquals("Ora", metric.formatManeuverDistance(49.0, "Ora"))
        assertEquals("0 m", metric.formatManeuverDistance(10.0))
    }

    @Test
    fun `imperial countdown uses round miles and feet`() {
        val imperial = UnitFormatter(imperial = true)
        assertEquals("12 mi", imperial.formatManeuverDistance(19_300.0))
        assertEquals("3.5 mi", imperial.formatManeuverDistance(5_600.0))
        assertEquals("1.2 mi", imperial.formatManeuverDistance(1_950.0))
        assertEquals("600 ft", imperial.formatManeuverDistance(185.0))
        assertEquals("200 ft", imperial.formatManeuverDistance(60.0))
        assertEquals("0 ft", imperial.formatManeuverDistance(46.5))
    }
}
