package app.roadstr.core.protocol.interop

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompactCoordinatesTest {
    private fun lat(text: String) = CompactCoordinates.parseLatitude(text)

    @Test
    fun `formatting is exact, signed and locale free`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            assertEquals("48.8566140", CompactCoordinates.format(488_566_140))
            assertEquals("-0.0000005", CompactCoordinates.format(-5))
            assertEquals("0.0000000", CompactCoordinates.format(0))
            assertEquals("-180.0000000", CompactCoordinates.format(-1_800_000_000))
            assertEquals("2.3522219", CompactCoordinates.format(23_522_219))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `only the canonical text of a value parses`() {
        assertEquals(CompactCoordinates.Parsed.Value(488_566_140), lat("48.8566140"))
        assertEquals(CompactCoordinates.Parsed.Value(-5), lat("-0.0000005"))
        assertEquals(CompactCoordinates.Parsed.Value(900_000_000), lat("90.0000000"))
        for (text in listOf("-0.0000000", "048.8566140", "48.856614", "48.85661400", "48,8566140", " 48.8566140", "+48.8566140", "4.8e1", "", ".5000000")) {
            assertEquals(text, CompactCoordinates.Parsed.Malformed, lat(text))
        }
        assertEquals(CompactCoordinates.Parsed.OutOfRange, lat("90.0000001"))
        assertEquals(CompactCoordinates.Parsed.OutOfRange, CompactCoordinates.parseLongitude("180.0000001"))
        assertEquals(CompactCoordinates.Parsed.Value(1_800_000_000), CompactCoordinates.parseLongitude("180.0000000"))
    }

    @Test
    fun `quantising a GPS reading rounds instead of truncating`() {
        // 35.1976312 * 1e7 is 351976311.99999994 in IEEE-754: a truncating encoder is one unit short.
        assertEquals(351_976_312, CompactCoordinates.latitudeE7(35.1976312))
        assertEquals(-715_429_715, CompactCoordinates.longitudeE7(-71.5429715))
        assertNull(CompactCoordinates.latitudeE7(90.0000001))
        assertNull(CompactCoordinates.latitudeE7(Double.NaN))
        assertNull(CompactCoordinates.longitudeE7(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `every int32 in a sweep survives format then parse`() {
        var raw = -900_000_000
        while (raw <= 900_000_000) {
            assertEquals(CompactCoordinates.Parsed.Value(raw), lat(CompactCoordinates.format(raw)))
            raw += 7_654_321
        }
    }
}
