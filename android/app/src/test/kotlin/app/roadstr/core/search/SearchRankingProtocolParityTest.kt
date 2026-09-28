package app.roadstr.core.search

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.util.Base64
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/search_ranking_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native search planning ranking and merging reproduce every Dart case`() {
        assertEquals(53, rows.size)
        for (fields in rows) {
            assertEquals(
                "${fields[0]}/${fields[1]}",
                decode(fields[8]),
                evaluate(fields),
            )
        }
    }

    @Test
    fun `policy limits and phase guarantees remain explicit`() {
        assertEquals(30.0, SearchRankingProtocol.DUPLICATE_RADIUS_METERS, 0.0)
        assertEquals(10, SearchRankingProtocol.MAX_RESULTS)
        assertEquals(200, SearchRankingProtocol.MAX_QUERY_LENGTH)
        assertEquals(0.66, SearchRankingProtocol.MATCH_THRESHOLD, 0.0)
        assertNull(
            SearchRankingProtocol.executionPlan(
                query = " \u00a0 ",
                settled = true,
                hasNear = true,
            ),
        )
        val typeAhead = requireNotNull(
            SearchRankingProtocol.executionPlan(
                query = "museum",
                settled = false,
                hasNear = true,
            ),
        )
        assertFalse(typeAhead.useNominatim)
        assertTrue(typeAhead.usePhoton)
        assertTrue(typeAhead.usePoi)
        assertFalse(typeAhead.allowRelaxedRetry)
    }

    private fun evaluate(fields: List<String>): String {
        val operation = fields[0]
        val settled = fields[2] == "1"
        val near = fields[3].takeUnless { it == "-" }?.let { latitude ->
            SearchResponsePoint(latitude.toDouble(), fields[4].toDouble())
        }
        val query = decode(fields[5])
        val primary = parseResults(decode(fields[6]))
        val secondary = parseResults(decode(fields[7]))
        return try {
            when (operation) {
                "plan" -> canonicalPlan(
                    SearchRankingProtocol.executionPlan(
                        query = query,
                        settled = settled,
                        hasNear = near != null,
                    ),
                )

                "relax" -> optional(SearchRankingProtocol.relaxQuery(query))
                "match" -> fixed9(SearchRankingProtocol.matchScore(query, primary.single()))
                "dedupe" -> canonicalResults(
                    SearchRankingProtocol.dedupeByProximity(primary),
                )

                "rank" -> canonicalResults(
                    SearchRankingProtocol.rankResults(query, primary, near),
                )

                "rank_geo" -> canonicalResults(
                    SearchRankingProtocol.rankGeocoders(query, primary, secondary, near),
                )

                "merge_poi" -> canonicalResults(
                    SearchRankingProtocol.mergePoiFirst(primary, secondary),
                )

                "retry" -> optional(
                    SearchRankingProtocol.relaxedRetryQuery(
                        plan = requireNotNull(
                            SearchRankingProtocol.executionPlan(
                                query = query,
                                settled = settled,
                                hasNear = near != null,
                            ),
                        ),
                        geocoded = primary,
                        poi = secondary,
                    ),
                )

                else -> error("Unknown search ranking fixture operation: $operation")
            }
        } catch (_: Exception) {
            "error"
        }
    }

    private fun parseResults(json: String): List<SearchResult> {
        val values = BoundedJsonParser(json).parse() as List<*>
        return values.map { raw ->
            val value = raw as Map<*, *>
            SearchResult(
                displayName = value["display"] as String,
                shortName = value["short"] as String,
                position = SearchResponsePoint(
                    latitude = (value["lat"] as Number).toDouble(),
                    longitude = (value["lon"] as Number).toDouble(),
                ),
                featureClass = value["class"] as String?,
                type = value["type"] as String?,
                city = value["city"] as String?,
                openingHours = value["opening"] as String?,
                distanceM = (value["distance"] as Number?)?.toDouble(),
                brand = value["brand"] as String?,
            )
        }
    }

    private fun canonicalPlan(plan: SearchExecutionPlan?): String = plan?.let {
        listOf(
            "query=${encode(it.query)}",
            "nominatim=${if (it.useNominatim) 1 else 0}",
            "photon=${if (it.usePhoton) 1 else 0}",
            "poi=${if (it.usePoi) 1 else 0}",
            "relax=${if (it.allowRelaxedRetry) 1 else 0}",
        ).joinToString("\n")
    } ?: "null"

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
    ).joinToString(",")

    private fun optional(value: String?): String = value?.let(::encode) ?: "-"

    private fun fixed3(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun fixed6(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private fun fixed9(value: Double): String = String.format(Locale.ROOT, "%.9f", value)

    private fun decode(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
