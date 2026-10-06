package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import org.json.JSONArray

/** Where a person searched is private: the history is one encrypted value under a Keystore key. */
internal class NativeRoadTestSearchHistoryStore(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) {
    private val preferences = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("search_history"),
        keyAlias = names.alias("search-history"),
    )

    /** An unreadable history raises, so a failed read is never mistaken for an empty one. */
    fun read(): List<SearchHistoryEntry> {
        val stored = preferences.read(KEY) ?: return emptyList()
        val array = JSONArray(stored)
        val encoded = buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index, null)?.let(::add)
            }
        }
        return SearchHistoryProtocol.decodeStored(encoded).take(SearchHistoryProtocol.MAX_STORED_ITEMS)
    }

    fun write(entries: List<SearchHistoryEntry>): Boolean {
        if (entries.isEmpty()) return preferences.remove(KEY)
        val array = JSONArray()
        SearchHistoryProtocol.encodeStored(entries).forEach(array::put)
        return preferences.write(KEY, array.toString())
    }

    fun clear(): Boolean = preferences.remove(KEY)

    private companion object {
        const val KEY = "entries"
    }
}
