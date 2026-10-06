package app.roadstr.roadtest

import android.content.Context
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol

/** Keystore-backed local persistence for privacy-sensitive saved addresses. */
class NativeRoadTestFavoritesStore(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) {
    private val preferences = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("favorites"),
        keyAlias = names.alias("favorites"),
    )

    fun load(): List<NativeSavedPlace> = runCatching {
        NativeSavedPlacesProtocol.decodeStoredFavorites(preferences.read(KEY))
    }.getOrDefault(emptyList())

    fun save(values: List<NativeSavedPlace>): Boolean = preferences.write(
        KEY,
        NativeSavedPlacesProtocol.encodeStoredFavorites(values),
    )

    private companion object {
        const val KEY = "entries"
    }
}
