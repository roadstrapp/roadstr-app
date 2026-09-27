package app.roadstr.core.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchProviderProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/search_provider_requests_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native request composition reproduces every Dart case`() {
        assertEquals(39, rows.size)
        for (fields in rows) {
            val request = when (fields[0]) {
                "nominatim_search" -> SearchProviderProtocol.nominatimSearch(
                    query = decode(fields[2]),
                    latitude = fields[3].optionalDouble(),
                    longitude = fields[4].optionalDouble(),
                )

                "nominatim_reverse" -> SearchProviderProtocol.nominatimReverse(
                    latitude = fields[3].toDouble(),
                    longitude = fields[4].toDouble(),
                )

                "photon_search" -> SearchProviderProtocol.photonSearch(
                    query = decode(fields[2]),
                    latitude = fields[3].optionalDouble(),
                    longitude = fields[4].optionalDouble(),
                    languageCode = decode(fields[5]),
                    limit = fields[6].toInt(),
                )

                "overpass" -> SearchProviderProtocol.overpass(
                    mirror = decode(fields[5]),
                    query = decode(fields[2]),
                )

                else -> error("Unknown provider fixture operation: ${fields[0]}")
            }
            assertEquals("${fields[0]}/${fields[1]}", decode(fields[7]), canonical(request))
        }
    }

    @Test
    fun `provider constants and incomplete coordinate pairs stay closed`() {
        assertEquals(200, SearchProviderProtocol.photonMaxQueryLength)
        assertEquals(setOf("en", "de", "fr"), SearchProviderProtocol.photonSupportedLanguages)
        assertEquals(2, SearchProviderProtocol.overpassMirrors.size)
        assertFalse(SearchProviderProtocol.overpassMirrors.any { it.contains("osm.ch") })
        assertThrows(IllegalArgumentException::class.java) {
            SearchProviderProtocol.nominatimSearch("x", latitude = 45.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchProviderProtocol.photonSearch("x", longitude = 7.0)
        }
    }

    private fun canonical(request: SearchProviderRequest?): String {
        if (request == null) return "null"
        val headers = request.headers.entries
            .sortedBy { it.key }
            .joinToString(",") { "${encode(it.key)}:${encode(it.value)}" }
        return listOf(
            "method=${request.method.fixtureName}",
            "uri=${request.uri}",
            "headers=$headers",
            "body=${request.body?.let(::encode) ?: "-"}",
        ).joinToString("\n")
    }

    private fun String.optionalDouble(): Double? = takeUnless { it == "-" }?.toDouble()

    private fun decode(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
