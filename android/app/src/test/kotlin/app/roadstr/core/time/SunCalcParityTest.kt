package app.roadstr.core.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunCalcParityTest {
    @Test
    fun `equinox times are plausible and ordered for Rome`() {
        val times = SunCalc.sunTimes(41.9028, 12.4964, LocalDate.of(2024, 3, 20))
        val rise = requireNotNull(times.rise)
        val set = requireNotNull(times.set)
        assertTrue(rise < set)
        assertEquals("2024-03-20", rise.toString().substring(0, 10))
        assertEquals("2024-03-20", set.toString().substring(0, 10))
        assertTrue(rise.toString().substring(11, 13).toInt() in 5..6)
        assertTrue(set.toString().substring(11, 13).toInt() in 17..18)
    }

    @Test
    fun `polar day and night expose no crossing`() {
        val summer = SunCalc.sunTimes(78.2232, 15.6469, LocalDate.of(2024, 6, 21))
        val winter = SunCalc.sunTimes(78.2232, 15.6469, LocalDate.of(2024, 12, 21))
        assertNull(summer.rise)
        assertNull(summer.set)
        assertNull(winter.rise)
        assertNull(winter.set)
    }
}
