package app.roadstr.core.search

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOrchestrationProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/search_orchestration_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native orchestration matches every out-of-order Dart transcript`() {
        var executed = 0
        for (fields in rows) {
            val near = fields[2].takeUnless { it == "-" }?.let {
                SearchResponsePoint(it.toDouble(), fields[3].toDouble())
            }
            val plan = requireNotNull(
                SearchRankingProtocol.executionPlan(
                    query = decode(fields[4]),
                    settled = fields[1] == "1",
                    hasNear = near != null,
                ),
            )
            val state = SearchOrchestrationProtocol(plan, near)
            val events = BoundedJsonParser(decode(fields[5])).parse() as List<*>
            val transcript = events.mapIndexed { index, raw ->
                val event = raw as Map<*, *>
                val batch = when (event["batch"]) {
                    "initial" -> SearchProviderBatch.INITIAL
                    "retry" -> SearchProviderBatch.RETRY
                    else -> error("Unknown batch ${event["batch"]}")
                }
                val provider = when (event["provider"]) {
                    "nominatim" -> SearchProviderKind.NOMINATIM
                    "photon" -> SearchProviderKind.PHOTON
                    "poi" -> SearchProviderKind.POI
                    else -> error("Unknown provider ${event["provider"]}")
                }
                val results = (event["results"] as List<*>)
                    .map { value -> result(value as Map<*, *>) }
                val outcome = state.accept(batch, provider, results)
                canonicalDecision(index, outcome, state.isCompleted)
            }.joinToString("\n")

            assertEquals(fields[0], decode(fields[6]), transcript)
            executed++
        }
        assertEquals(32, executed)
    }

    @Test
    fun `enabled providers follow phase and location exactly`() {
        val typeahead = SearchOrchestrationProtocol(
            requireNotNull(
                SearchRankingProtocol.executionPlan(
                    "museum",
                    settled = false,
                    hasNear = false,
                ),
            ),
            null,
        )
        assertEquals(setOf(SearchProviderKind.PHOTON), typeahead.expectedInitialProviders)
        assertFalse(typeahead.isCompleted)

        val settledNear = SearchOrchestrationProtocol(
            requireNotNull(
                SearchRankingProtocol.executionPlan(
                    "museum",
                    settled = true,
                    hasNear = true,
                ),
            ),
            SearchResponsePoint(45.0, 9.0),
        )
        assertEquals(
            setOf(
                SearchProviderKind.NOMINATIM,
                SearchProviderKind.PHOTON,
                SearchProviderKind.POI,
            ),
            settledNear.expectedInitialProviders,
        )
    }

    @Test
    fun `expected-provider view is immutable`() {
        val state = SearchOrchestrationProtocol(
            requireNotNull(
                SearchRankingProtocol.executionPlan(
                    "museum",
                    settled = true,
                    hasNear = true,
                ),
            ),
            SearchResponsePoint(45.0, 9.0),
        )
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (state.expectedInitialProviders as MutableSet<SearchProviderKind>).clear()
        }
        assertTrue(SearchProviderKind.PHOTON in state.expectedInitialProviders)
    }

    private fun result(value: Map<*, *>): SearchResult = SearchResult(
        displayName = value["display"] as String,
        shortName = value["short"] as String,
        position = SearchResponsePoint(
            latitude = (value["lat"] as Number).toDouble(),
            longitude = (value["lon"] as Number).toDouble(),
        ),
        city = value["city"] as? String,
        brand = value["brand"] as? String,
    )

    private fun canonicalDecision(
        index: Int,
        decision: SearchOrchestrationDecision,
        completed: Boolean,
    ): String = "$index:" +
        "partial=${ids(decision.partialResults)};" +
        "retry=${decision.retryQuery ?: "-"};" +
        "final=${ids(decision.finalResults)};" +
        "done=${if (completed) 1 else 0}"

    private fun ids(values: List<SearchResult>?): String =
        values?.joinToString(",") { result -> result.shortName } ?: "-"

    private fun decode(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
