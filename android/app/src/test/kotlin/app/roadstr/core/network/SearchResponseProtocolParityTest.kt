package app.roadstr.core.network

import java.util.Base64
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResponseProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/search_responses_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native search response normalization reproduces every Dart case`() {
        assertEquals(34, rows.size)
        for (fields in rows) {
            assertEquals(
                "${fields[0]}/${fields[1]}",
                decode(fields[6]),
                evaluate(fields),
            )
        }
    }

    @Test
    fun `remote text and labels retain their defensive bounds`() {
        assertEquals(300, SearchResult.MAX_REMOTE_TEXT_CHARS)
        assertEquals(300, SearchResult.clampRemoteText("X".repeat(500))?.length)
        assertNull(SearchResult.clampRemoteText("   "))
        assertNull(SearchResult.clampRemoteText(42))
        assertEquals(
            "Via Roma 12, Milano",
            SearchResponseProtocol.shortLabelFrom(
                display = "12, Via Roma, Milano",
                address = mapOf(
                    "road" to "Via Roma",
                    "house_number" to "12",
                    "city" to "Milano",
                ),
            ),
        )
    }

    private fun evaluate(fields: List<String>): String {
        val operation = fields[0]
        val center = SearchResponsePoint(fields[2].toDouble(), fields[3].toDouble())
        val fallback = fields[4].takeUnless { it == "-" }?.let(::decode)
        val input = decode(fields[5])
        return try {
            when (operation) {
                "nominatim" -> canonicalResults(SearchResponseProtocol.parseNominatimSearch(input))
                "reverse" -> canonicalReverse(SearchResponseProtocol.parseNominatimReverse(input))
                "photon" -> canonicalResults(SearchResponseProtocol.parsePhoton(input))
                "overpass" -> canonicalResults(
                    SearchResponseProtocol.parseOverpassElements(input).mapNotNull { element ->
                        SearchResponseProtocol.overpassElementToResult(
                            element = element,
                            center = center,
                            fallbackName = fallback,
                        )
                    },
                )

                "overpass_elements" -> canonicalElements(
                    SearchResponseProtocol.parseOverpassElements(input),
                )

                else -> error("Unknown search response fixture operation: $operation")
            }
        } catch (_: Exception) {
            "error"
        }
    }

    private fun canonicalResults(results: List<SearchResult>): String = listOf(
        "count=${results.size}",
        "items=${results.joinToString(";", transform = ::canonicalResult)}",
    ).joinToString("\n")

    private fun canonicalResult(result: SearchResult): String = listOf(
        encode(result.displayName),
        encode(result.shortName),
        fixed6(result.position.latitude),
        fixed6(result.position.longitude),
        optional(result.featureClass),
        optional(result.type),
        optional(result.city),
        optional(result.openingHours),
        result.distanceM?.let(::fixed3) ?: "-",
        optional(result.brand),
        encode(result.emoji),
        encode(result.categoryLabel),
    ).joinToString(",")

    private fun canonicalReverse(detail: NominatimReverseDetail?): String = detail?.let {
        listOf(
            "display=${encode(it.display)}",
            "wiki=${optional(it.wikiQuery)}",
            "opening=${optional(it.openingHours)}",
            "label=${encode(it.label)}",
        ).joinToString("\n")
    } ?: "null"

    private fun canonicalElements(elements: List<Map<String, Any?>>): String = listOf(
        "count=${elements.size}",
        "items=${elements.joinToString(";") { element ->
            val type = element["type"]
            val id = element["id"]
            val keys = element.keys.sorted()
            "${encode(type as? String ?: "")},${if (id is Number) id else "-"}," +
                keys.joinToString(".", transform = ::encode)
        }}",
    ).joinToString("\n")

    private fun optional(value: String?): String = value?.let(::encode) ?: "-"

    private fun fixed3(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun fixed6(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private fun decode(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}

class SearchResponsePlaceNameTest {
    @Test
    fun `a business keeps its own name before its address`() {
        val body = """[{"lat":"44.3591","lon":"11.7128","display_name":"Chaimandir, 35, Via Felice Orsini, Imola, Italia",
            "class":"amenity","type":"restaurant","name":"Chaimandir",
            "address":{"amenity":"Chaimandir","house_number":"35","road":"Via Felice Orsini","town":"Imola"}}]"""
        assertEquals(
            "Chaimandir, Via Felice Orsini 35, Imola",
            SearchResponseProtocol.parseNominatimSearch(body).single().shortName,
        )
    }

    @Test
    fun `a plain address and a street stay what they were`() {
        val body = """[{"lat":"44.36","lon":"11.71","display_name":"35, Via Felice Orsini, Imola, Italia",
            "class":"place","type":"house","address":{"house_number":"35","road":"Via Felice Orsini","town":"Imola"}},
            {"lat":"44.36","lon":"11.71","display_name":"Via Felice Orsini, Imola, Italia",
            "class":"highway","type":"residential","name":"Via Felice Orsini",
            "address":{"road":"Via Felice Orsini","town":"Imola"}}]"""
        val results = SearchResponseProtocol.parseNominatimSearch(body)
        assertEquals("Via Felice Orsini 35, Imola", results[0].shortName)
        assertEquals("Via Felice Orsini, Imola", results[1].shortName)
    }

    @Test
    fun `stations, motorway service areas and stops keep their names`() {
        val body = """[
            {"lat":"44.5","lon":"11.3","display_name":"Bologna Centrale, Piazza delle Medaglie d'Oro, Bologna",
             "class":"railway","type":"station","name":"Bologna Centrale",
             "address":{"railway":"Bologna Centrale","road":"Piazza delle Medaglie d'Oro","city":"Bologna"}},
            {"lat":"44.4","lon":"11.6","display_name":"Area di servizio Sillaro Ovest, A14, Imola",
             "class":"highway","type":"services","name":"Area di servizio Sillaro Ovest",
             "address":{"road":"Autostrada Adriatica","town":"Imola"}},
            {"lat":"44.36","lon":"11.71","display_name":"Imola Stazione, Viale Andrea Costa, Imola",
             "class":"highway","type":"bus_stop","name":"Imola Stazione",
             "address":{"road":"Viale Andrea Costa","town":"Imola"}}]"""
        assertEquals(
            listOf(
                "Bologna Centrale, Piazza delle Medaglie d'Oro, Bologna",
                "Area di servizio Sillaro Ovest, Autostrada Adriatica, Imola",
                "Imola Stazione, Viale Andrea Costa, Imola",
            ),
            SearchResponseProtocol.parseNominatimSearch(body).map { it.shortName },
        )
    }

    @Test
    fun `an unnamed shop on a street is still its address`() {
        val body = """[{"lat":"44.36","lon":"11.71","display_name":"Via Emilia, Imola, Italia",
            "class":"shop","type":"bakery","name":"",
            "address":{"road":"Via Emilia","town":"Imola"}}]"""
        assertEquals("Via Emilia", SearchResponseProtocol.parseNominatimSearch(body).single().shortName)
    }

    @Test
    fun `photon keeps a place name ahead of its street`() {
        val body = """{"features":[{"geometry":{"coordinates":[11.7128,44.3591]},
            "properties":{"name":"Chaimandir","street":"Via Felice Orsini","housenumber":"35",
            "city":"Imola","osm_key":"amenity","osm_value":"restaurant"}}]}"""
        assertEquals(
            "Chaimandir, Via Felice Orsini 35, Imola",
            SearchResponseProtocol.parsePhoton(body).single().shortName,
        )
    }
}
