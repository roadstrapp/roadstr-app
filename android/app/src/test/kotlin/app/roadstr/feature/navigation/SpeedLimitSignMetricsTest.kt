package app.roadstr.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitSignMetricsTest {
    @Test
    fun `a number that fits keeps the full enlarged size`() {
        assertEquals(1f, SpeedLimitSignMetrics.fitScale(measuredWidth = 36f, availableWidth = 54f), 0f)
        assertEquals(1f, SpeedLimitSignMetrics.fitScale(measuredWidth = 54f, availableWidth = 54f), 0f)
    }

    @Test
    fun `a wider number is shrunk until it exactly fits and is never enlarged`() {
        val scale = SpeedLimitSignMetrics.fitScale(measuredWidth = 60f, availableWidth = 54f)
        assertEquals(0.9f, scale, 1e-6f)
        assertEquals(54f, 60f * scale, 1e-4f)
        assertTrue(SpeedLimitSignMetrics.fitScale(measuredWidth = 200f, availableWidth = 54f) < 1f)
    }

    @Test
    fun `an empty measurement leaves the size alone`() {
        assertEquals(1f, SpeedLimitSignMetrics.fitScale(measuredWidth = 0f, availableWidth = 54f), 0f)
    }

    @Test
    fun `the text area stays inside the white circle`() {
        val inner = SpeedLimitSignMetrics.SIZE.value - 2 * SpeedLimitSignMetrics.RING.value
        assertEquals(66f, inner, 0f)
        // Digits are about 0.71 em tall; at the enlarged 22 sp * 1.4 size half of that height from the
        // centre the circle's chord must still be wider than the text area.
        val halfDigitHeight = 22f * SpeedLimitSignMetrics.TEXT_SCALE * 0.71f / 2f
        val radius = inner / 2f
        val chord = 2f * kotlin.math.sqrt(radius * radius - halfDigitHeight * halfDigitHeight)
        assertTrue(chord > SpeedLimitSignMetrics.TEXT_MAX_WIDTH.value)
    }

    @Test
    fun `the number is forty percent larger`() {
        assertEquals(1.4f, SpeedLimitSignMetrics.TEXT_SCALE, 0f)
    }
}
