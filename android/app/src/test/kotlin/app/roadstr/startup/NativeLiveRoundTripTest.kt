package app.roadstr.startup

import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import writes what the mapper produced and then reads it back from the live store, which encodes
 * on write and decodes on read. If that round trip changed a legitimate value, the read-back would fail
 * and a person with perfectly good data would be refused. These cases make sure it is a fixed point.
 */
class NativeLiveRoundTripTest {
    @Test
    fun `favourites decoded from the old box are unchanged by the live store's encoding`() {
        val legacy = legacyFavorites(
            favorite("Casa", "Via Roma 1, Verona", 45.4384, 10.9916),
            favorite("Caffè ☕ dell'angolo", "", 45.6495, 13.7768),
            favorite("L".repeat(300), "A".repeat(700), -33.8688, 151.2093),
            favorite("Polo", "", 89.99999, -179.99999),
            favorite("Riga\nnuova", "Tab\tindirizzo", 0.0, 0.0),
        )

        val first = NativeSavedPlacesProtocol.decodeStoredFavorites(legacy)

        assertTrue(first.isNotEmpty())
        assertEquals(first, NativeSavedPlacesProtocol.decodeStoredFavorites(NativeSavedPlacesProtocol.encodeStoredFavorites(first)))
    }

    @Test
    fun `a thousand favourites and a damaged one are a fixed point too`() {
        val many = (1..1_100).map { favorite("Posto $it", "Via $it", 40.0 + it / 1000.0, 10.0 + it / 1000.0) }
        val legacy = legacyFavorites(*many.toTypedArray(), "{not json")

        val first = NativeSavedPlacesProtocol.decodeStoredFavorites(legacy)

        assertEquals(NativeSavedPlacesProtocol.MAX_STORED_ITEMS, first.size)
        assertEquals(first, NativeSavedPlacesProtocol.decodeStoredFavorites(NativeSavedPlacesProtocol.encodeStoredFavorites(first)))
    }

    @Test
    fun `the parking spot keeps its point and its time`() {
        for (spot in listOf(
            NativeParkingPosition(NativeMapPoint(45.4401, 10.9902), 1_700_000_000_000L),
            NativeParkingPosition(NativeMapPoint(-12.0464, -77.0428), null),
        )) {
            val decoded = NativeSavedPlacesProtocol.decodeParking(NativeSavedPlacesProtocol.encodeParking(spot))
            assertEquals(spot, decoded)
            assertEquals(decoded, NativeSavedPlacesProtocol.decodeParking(NativeSavedPlacesProtocol.encodeParking(decoded!!)))
        }
    }

    @Test
    fun `search history is a fixed point of the live store's encoding`() {
        val entries = listOf(
            SearchHistoryEntry("Trieste, Friuli-Venezia Giulia", 45.6495, 13.7768),
            SearchHistoryEntry("Café «Gräfin» ☕", 48.2082, 16.3738),
            SearchHistoryEntry("X".repeat(400), -34.6037, -58.3816),
        )
        val stored = NostrJson.encode(SearchHistoryProtocol.encodeStored(entries))

        val first = SearchHistoryProtocol.decodeStored(app.roadstr.core.protocol.nostr.BoundedJsonParser(stored).parse())
        val second = SearchHistoryProtocol.decodeStored(SearchHistoryProtocol.encodeStored(first))

        assertTrue(first.isNotEmpty())
        assertEquals(first, second)
    }

    private fun favorite(label: String, address: String, latitude: Double, longitude: Double): String =
        NativeSavedPlacesProtocol.encodeStoredFavorites(listOf(NativeSavedPlace(label, address, NativeMapPoint(latitude, longitude))))
            .removePrefix("[").removeSuffix("]")

    /** The old box holds a list of JSON strings; each argument is one already-quoted element or a raw damaged one. */
    private fun legacyFavorites(vararg elements: String): String =
        elements.joinToString(prefix = "[", postfix = "]", separator = ",") { element ->
            if (element.startsWith("\"")) element else "\"" + element.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
}
