package app.roadstr.core.geo

import app.roadstr.core.navigation.CountryRuralSpeedData
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityCountryIndexTest {
    @Test
    fun `nearest city gives country and division, far from any city gives nothing`() {
        val index = CityCountryIndex.fromTsv(
            sequenceOf(
                "A\tIT\t05\tEmilia-Romagna",
                "C\t1\tImola\tImola\t44.3598\t11.7134\tIT\t05\t0",
                "C\t2\tHouston\tHouston\t29.7633\t-95.3633\tUS\tTX\t0",
                "C\tbad\tNowhere\tNowhere\tx\ty\tZZ\t00\t0",
            ),
        )
        assertEquals(2, index.size)
        assertEquals(CityCountryIndex.Place("IT", "05"), index.nearest(44.42, 11.80))
        assertEquals(CityCountryIndex.Place("US", "TX"), index.nearest(29.9, -95.5))
        assertNull(index.nearest(-40.0, -140.0))
        assertNull(index.nearest(Double.NaN, 0.0))
    }

    @Test
    fun `the bundled catalogue places well known roads in the right country`() {
        val index = bundled()
        val expected = mapOf(
            "Imola" to Triple(44.3598, 11.7134, "IT"),
            "A1 near Firenze" to Triple(43.80, 11.25, "IT"),
            "Berlin" to Triple(52.52, 13.405, "DE"),
            "Paris" to Triple(48.8566, 2.3522, "FR"),
            "London" to Triple(51.5072, -0.1276, "GB"),
            "Tokyo" to Triple(35.6762, 139.6503, "JP"),
            "Nairobi" to Triple(-1.2921, 36.8219, "KE"),
            "Ushuaia road" to Triple(-54.8, -68.3, "AR"),
            "Sydney" to Triple(-33.8688, 151.2093, "AU"),
            "Houston" to Triple(29.76, -95.37, "US"),
        )
        for ((name, point) in expected) {
            assertEquals(name, point.third, index.nearest(point.first, point.second)?.countryCode)
        }
        assertEquals("TX", index.nearest(29.76, -95.37)?.admin1Code)
    }

    @Test
    fun `every country of the bundled catalogue gets a threshold, and the table is sane`() {
        val index = bundled()
        assertTrue(index.size > 30_000)
        for (limit in CountryRuralSpeedData.byCountry.values + CountryRuralSpeedData.byUsState.values) {
            assertTrue("$limit", limit in 20..140)
        }
        assertEquals(90, CountryRuralSpeedData.byCountry["IT"])
        assertEquals(90, CountryRuralSpeedData.WORLD_MEDIAN_KMH)
    }

    private fun bundled(): CityCountryIndex {
        val file = File("src/main/assets/offline_catalog/geonames-cities15000.tsv.gz")
        assertTrue("run from the module directory: ${file.absolutePath}", file.exists())
        return GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8)
            .useLines { CityCountryIndex.fromTsv(it) }
    }
}
