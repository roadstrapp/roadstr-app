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
            assertEquals("48.856614", CompactCoordinates.format(48_856_614))
            assertEquals("-0.000005", CompactCoordinates.format(-5))
            assertEquals("0.000000", CompactCoordinates.format(0))
            assertEquals("-180.000000", CompactCoordinates.format(-180_000_000))
            assertEquals("2.352222", CompactCoordinates.format(2_352_222))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `only the canonical text of a value parses`() {
        assertEquals(CompactCoordinates.Parsed.Value(48_856_614), lat("48.856614"))
        assertEquals(CompactCoordinates.Parsed.Value(-5), lat("-0.000005"))
        assertEquals(CompactCoordinates.Parsed.Value(90_000_000), lat("90.000000"))
        for (text in listOf("-0.000000", "048.856614", "48.85661", "48.8566140", "48,856614", " 48.856614", "+48.856614", "4.8e1", "", ".500000")) {
            assertEquals(text, CompactCoordinates.Parsed.Malformed, lat(text))
        }
        assertEquals(CompactCoordinates.Parsed.OutOfRange, lat("90.000001"))
        assertEquals(CompactCoordinates.Parsed.OutOfRange, CompactCoordinates.parseLongitude("180.000001"))
        assertEquals(CompactCoordinates.Parsed.Value(180_000_000), CompactCoordinates.parseLongitude("180.000000"))
    }

    @Test
    fun `quantising a GPS reading rounds instead of truncating`() {
        // 35.1976312 * 1e6 is 35197631.199999996 in IEEE-754: truncating would land a unit short.
        assertEquals(35_197_631, CompactCoordinates.latitudeE6(35.1976312))
        assertEquals(-71_542_971, CompactCoordinates.longitudeE6(-71.5429715))
        assertNull(CompactCoordinates.latitudeE6(90.000001))
        assertNull(CompactCoordinates.latitudeE6(Double.NaN))
        assertNull(CompactCoordinates.longitudeE6(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `every int32 in a sweep survives format then parse`() {
        var raw = -90_000_000
        while (raw <= 90_000_000) {
            assertEquals(CompactCoordinates.Parsed.Value(raw), lat(CompactCoordinates.format(raw)))
            raw += 7_654_321
        }
    }
}
