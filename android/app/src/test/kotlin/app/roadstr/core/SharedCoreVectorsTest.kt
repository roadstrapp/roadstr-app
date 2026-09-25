package app.roadstr.core

import app.roadstr.core.geo.EncodedPolyline
import app.roadstr.core.protocol.lightning.Bolt11Invoice
import app.roadstr.core.search.FuzzyMatch
import app.roadstr.core.time.OpeningHours
import app.roadstr.core.time.SunCalc
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Executes the same committed vectors as test/native_core_parity_vectors_test.dart. */
class SharedCoreVectorsTest {
    @Test
    fun `Kotlin matches shared Dart oracle vectors`() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/parity/core_vectors.tsv"))
        val lines = resource.bufferedReader().use { it.readLines() }
        var executed = 0
        for (line in lines) {
            if (line.isBlank() || line.startsWith('#')) continue
            val fields = line.split('\t')
            val id = fields[1]
            when (fields[0]) {
                "sun" -> {
                    val result = SunCalc.sunTimes(
                        fields[2].toDouble(),
                        fields[3].toDouble(),
                        LocalDate.parse(fields[4]),
                    )
                    assertEquals(id, fields[5].toLong(), result.rise?.toEpochMilli())
                    assertEquals(id, fields[6].toLong(), result.set?.toEpochMilli())
                }

                "fuzzy" -> assertEquals(id, fields[3], FuzzyMatch.normalize(fields[2]))

                "opening" -> {
                    val result = OpeningHours.evaluate(fields[2], LocalDateTime.parse(fields[3]))
                    assertEquals(id, fields[4], result.state.name.lowercase())
                    val expectedChange = fields[5].takeUnless { it == "-" }?.let(LocalDateTime::parse)
                    assertEquals(id, expectedChange, result.nextChange)
                }

                "polyline" -> {
                    val points = EncodedPolyline.decode(fields[2], fields[3].toInt())
                    assertEquals(id, fields[4].toInt(), points.size)
                    assertEquals(id, fields[5].toDouble(), points.first().latitude, 0.0000001)
                    assertEquals(id, fields[6].toDouble(), points.first().longitude, 0.0000001)
                    assertEquals(id, fields[7].toDouble(), points.last().latitude, 0.0000001)
                    assertEquals(id, fields[8].toDouble(), points.last().longitude, 0.0000001)
                }

                "bolt11" -> {
                    val invoice = requireNotNull(Bolt11Invoice.parseOrNull(fields[2])) { id }
                    assertEquals(id, fields[3].toLong(), invoice.amountMillisatoshi)
                    assertEquals(id, fields[4].toLong(), invoice.createdAtUnixSeconds)
                    assertEquals(id, fields[5].toInt(), invoice.expirySeconds)
                    assertTrue(id, invoice.preimageMatches(fields[6]))
                    assertTrue(id, invoice.descriptionMatches(fields[7]))
                }

                else -> error("Unknown parity vector kind: ${fields[0]}")
            }
            executed++
        }
        assertTrue("fixture must not be empty", executed >= 10)
    }
}
