package app.roadstr.core.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyDistanceValidatorTest {
    private fun sample(
        latitude: Double = 45.0,
        longitude: Double = 9.0,
        time: Long,
        accuracy: Double = 5.0,
        speed: Double = 10.0,
    ) = JourneyLocationSample(latitude, longitude, time, accuracy, speed)

    @Test
    fun `valid movement counts actual displacement`() {
        val validator = JourneyDistanceValidator(JourneyMode.CAR)
        assertEquals(JourneyFixDecision.ANCHOR, validator.accept(sample(time = 1_000L)).decision)
        val update = validator.accept(sample(latitude = 45.001, time = 11_000L))
        assertEquals(JourneyFixDecision.ACCEPTED, update.decision)
        assertTrue(update.validatedDeltaMeters in 105L..117L)
    }

    @Test
    fun `duplicate out of order noise and teleport do not count`() {
        val validator = JourneyDistanceValidator(JourneyMode.WALKING)
        validator.accept(sample(time = 10_000L, speed = 0.0))
        assertEquals(JourneyFixDecision.DUPLICATE, validator.accept(sample(time = 11_000L, speed = 0.0)).decision)
        assertEquals(JourneyFixDecision.OUT_OF_ORDER, validator.accept(sample(latitude = 45.0001, time = 9_000L, speed = 0.0)).decision)
        assertEquals(JourneyFixDecision.STATIONARY, validator.accept(sample(latitude = 45.00001, time = 12_000L, speed = 0.0)).decision)
        assertEquals(JourneyFixDecision.TELEPORT, validator.accept(sample(latitude = 46.0, time = 13_000L, speed = 0.0)).decision)
    }

    @Test
    fun `poor accuracy and long gaps are not certified`() {
        val validator = JourneyDistanceValidator(JourneyMode.BICYCLE)
        assertEquals(JourneyFixDecision.POOR_ACCURACY, validator.accept(sample(time = 0L, accuracy = 80.0)).decision)
        validator.accept(sample(time = 1_000L, speed = 0.0))
        val gap = validator.accept(sample(latitude = 45.001, time = 122_000L, speed = 0.0))
        assertEquals(JourneyFixDecision.SIGNAL_GAP, gap.decision)
        assertEquals(0L, gap.validatedDeltaMeters)
        assertTrue(gap.uncertainDeltaMeters > 0L)
    }

    @Test
    fun `mode limits are not shared`() {
        val walking = JourneyDistanceValidator(JourneyMode.WALKING)
        val car = JourneyDistanceValidator(JourneyMode.CAR)
        walking.accept(sample(time = 0L, speed = 0.0))
        car.accept(sample(time = 0L, speed = 0.0))
        val fast = sample(latitude = 45.001, time = 2_000L, speed = 55.0)
        assertEquals(JourneyFixDecision.TELEPORT, walking.accept(fast).decision)
        assertEquals(JourneyFixDecision.ACCEPTED, car.accept(fast).decision)
    }
}

