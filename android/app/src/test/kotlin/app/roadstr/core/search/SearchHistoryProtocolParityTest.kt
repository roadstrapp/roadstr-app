package app.roadstr.core.search

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchHistoryProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/search_history_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native history policy matches every Dart transcript`() {
        var executed = 0
        for (fields in rows) {
            val payload = BoundedJsonParser(decode(fields[2])).parse()
            val actual = when (fields[0]) {
                "decode" -> canonicalEntries(SearchHistoryProtocol.decodeStored(payload))
                "prepend" -> {
                    val value = payload as Map<*, *>
                    val item = entry(requireNotNull(value["item"]) as Map<*, *>)
                    val current = (requireNotNull(value["current"]) as List<*>)
                        .map { currentValue -> entry(currentValue as Map<*, *>) }
                    canonicalEntries(SearchHistoryProtocol.prepend(item, current))
                }

                "encode" -> {
                    val entries = (payload as List<*>)
                        .map { value -> entry(value as Map<*, *>) }
                    canonicalStorage(SearchHistoryProtocol.encodeStored(entries))
                }

                else -> error("Unknown fixture operation ${fields[0]}")
            }
            assertEquals("${fields[0]}/${fields[1]}", decode(fields[3]), actual)
            executed++
        }
        assertEquals(31, executed)
    }

    @Test
    fun `history constants preserve the Flutter storage contract`() {
        assertEquals("searchHistory", SearchHistoryProtocol.STORAGE_KEY)
        assertEquals(300, SearchHistoryProtocol.MAX_LABEL_LENGTH)
        assertEquals(100, SearchHistoryProtocol.MAX_LOADED_ITEMS)
        assertEquals(5, SearchHistoryProtocol.MAX_STORED_ITEMS)
        assertEquals(0.0001, SearchHistoryProtocol.DUPLICATE_COORDINATE_DELTA, 0.0)
    }

    @Test
    fun `returned collections cannot be mutated through Java views`() {
        val decoded = SearchHistoryProtocol.decodeStored(
            listOf("{\"label\":\"Casa\",\"lat\":45,\"lon\":9}"),
        )
        val prepended = SearchHistoryProtocol.prepend(
            SearchHistoryEntry("Nuova", 44.0, 12.0),
            decoded,
        )
        val encoded = SearchHistoryProtocol.encodeStored(prepended)

        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (decoded as MutableList<SearchHistoryEntry>).clear()
        }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (prepended as MutableList<SearchHistoryEntry>).clear()
        }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (encoded as MutableList<String>).clear()
        }
    }

    @Test
    fun `encoder refuses non-finite coordinates`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchHistoryProtocol.encodeEntry(
                SearchHistoryEntry("Invalid", Double.NaN, 9.0),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchHistoryProtocol.encodeEntry(
                SearchHistoryEntry("Invalid", 45.0, Double.POSITIVE_INFINITY),
            )
        }
    }

    private fun entry(value: Map<*, *>): SearchHistoryEntry = SearchHistoryEntry(
        label = requireNotNull(value["label"]) as String,
        latitude = (requireNotNull(value["lat"]) as Number).toDouble(),
        longitude = (requireNotNull(value["lon"]) as Number).toDouble(),
    )

    private fun canonicalEntries(entries: List<SearchHistoryEntry>): String =
        canonicalStorage(entries.map(SearchHistoryProtocol::encodeEntry))

    private fun canonicalStorage(values: List<String>): String =
        "count=${values.size}\n${values.joinToString("\n")}"

    private fun decode(value: String): String {
        if (value == "-") return ""
        return String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }
}
