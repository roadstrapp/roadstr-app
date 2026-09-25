package app.roadstr.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteDetectorParityTest {
    @Test
    fun `hard threshold fires immediately and resets`() {
        val detector = OffRouteDetector()
        assertTrue(detector.sawDeviation(56.0))
        assertFalse(detector.sawDeviation(40.0))
    }

    @Test
    fun `growing gap must persist outside the noise floor`() {
        val detector = OffRouteDetector()
        assertFalse(detector.sawDeviation(10.0))
        repeat(3) { assertFalse(detector.sawDeviation(31.0)) }
        assertTrue(detector.sawDeviation(31.0))

        detector.reset()
        assertFalse(detector.sawDeviation(5.0))
        repeat(6) { assertFalse(detector.sawDeviation(25.0)) }
    }

    @Test
    fun `uncertain fix neither fires nor erases a good trend`() {
        val detector = OffRouteDetector()
        assertFalse(detector.sawDeviation(10.0))
        repeat(3) { assertFalse(detector.sawDeviation(35.0, accuracyMeters = 5.0)) }
        assertFalse(detector.sawDeviation(40.0, accuracyMeters = 40.0))
        assertTrue(detector.sawDeviation(35.0, accuracyMeters = 5.0))
        assertFalse(detector.sawDeviation(Double.NaN))
    }
}
