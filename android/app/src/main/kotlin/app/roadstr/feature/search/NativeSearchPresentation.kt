package app.roadstr.feature.search

import app.roadstr.core.discovery.web.SearxngCapability
import app.roadstr.core.format.UnitFormatter
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.core.search.SearchRankingProtocol
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the web part of a search could not be shown. */
enum class NativeWebProblem {
    JsonDisabled,
    RateLimited,
    NotSearxng,
    NoEngineList,
    Unreachable,
    Rejected,
}

/** The problem a failed instance check amounts to, or null when the instance works. */
fun SearxngCapability.toWebProblem(): NativeWebProblem? = when (this) {
    SearxngCapability.JsonDisabled -> NativeWebProblem.JsonDisabled
    is SearxngCapability.RateLimited -> NativeWebProblem.RateLimited
    SearxngCapability.NotSearxng -> NativeWebProblem.NotSearxng
    SearxngCapability.NoEngineList -> NativeWebProblem.NoEngineList
    SearxngCapability.Unreachable -> NativeWebProblem.Unreachable
    is SearxngCapability.Compatible -> null
}

/** What a web result is known to be about, when it could be tied to a place. */
sealed interface NativeWebPlaceLink {
    data object None : NativeWebPlaceLink

    /** Confidently the place [placeId]: the link under the result opens it on the map. */
    data class Linked(val placeId: String, val name: String) : NativeWebPlaceLink

    /** Possibly [placeId]: offered as a question and never merged with the result. */
    data class Candidate(val placeId: String, val name: String) : NativeWebPlaceLink
}

data class NativeWebResultPresentation(
    val title: String,
    val host: String,
    val snippet: String,
    val url: String,
    val link: NativeWebPlaceLink = NativeWebPlaceLink.None,
)

/** The optional web part of the search list: offered, asked about, running, shown or failed. */
sealed interface NativeSearchWeb {
    data object Hidden : NativeSearchWeb

    /** A row offering to search the web for [text]. */
    data class Offer(val text: String) : NativeSearchWeb

    /**
     * The inline question before anything is sent: send [text] to [host]? [addsTown] says that
     * the name of the user's town goes along, which a "near me" search needs and the words do not show.
     */
    data class Consent(val text: String, val host: String, val addsTown: Boolean = false) : NativeSearchWeb

    data object Loading : NativeSearchWeb

    data class Results(val host: String, val rows: List<NativeWebResultPresentation>) : NativeSearchWeb

    data class Unavailable(val problem: NativeWebProblem) : NativeSearchWeb
}

/** One line about how complete a natural-language search is. */
enum class NativeSearchNotice {
    FewTagged,
    Widened,
    RouteUnsupported,
    AreaFallback,
    OpenHoursUnknown,
}

enum class NativeSearchUiStatus {
    Hidden,
    Browsing,
    Loading,
    Results,
    EmptyNearby,
}

/** Order and symbols mirror NearbyCategory in the Flutter search panel. */
enum class NativeSearchNearbyCategory(val emoji: String, val wireQuery: String) {
    Fuel("⛽", "fuel"),
    Restaurant("🍽️", "restaurant"),
    Supermarket("🛒", "supermarket"),
    Atm("🏧", "atm"),
    Pharmacy("💊", "pharmacy"),
    Hospital("🏥", "hospital"),
    Police("👮", "police"),
    PostOffice("📮", "post office"),
    Parking("🅿️", "parking"),
    Hotel("🏨", "hotel"),
    Charging("🔌", "ev charging"),
}

data class NativeSearchFavorite(
    val label: String,
    val address: String,
    val position: SearchResponsePoint,
)

data class NativeSearchResultPresentation(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val distanceLabel: String?,
    val position: SearchResponsePoint,
)

data class NativeSearchFavoritePresentation(
    val label: String,
    val address: String,
    val position: SearchResponsePoint,
)

data class NativeSearchHistoryPresentation(
    val title: String,
    val subtitle: String,
    val fullLabel: String,
    val position: SearchResponsePoint,
)

data class NativeSearchUiSnapshot(
    val revision: Long,
    val status: NativeSearchUiStatus,
    val query: String,
    val results: List<NativeSearchResultPresentation>,
    val favorites: List<NativeSearchFavoritePresentation>,
    val history: List<NativeSearchHistoryPresentation>,
    val selectedNearby: NativeSearchNearbyCategory?,
    val nearbyEnabled: Boolean,
    val notice: NativeSearchNotice? = null,
    val web: NativeSearchWeb = NativeSearchWeb.Hidden,
) {
    companion object {
        const val NO_SEARCH_REVISION = -1L

        fun hidden() = NativeSearchUiSnapshot(
            revision = NO_SEARCH_REVISION,
            status = NativeSearchUiStatus.Hidden,
            query = "",
            results = emptyList(),
            favorites = emptyList(),
            history = emptyList(),
            selectedNearby = null,
            nearbyEnabled = false,
        )
    }
}

/** Pure bounded projection of typed search values into rows shown by Compose. */
object NativeSearchPresenter {
    const val MAX_FAVORITES = 1_000
    const val MAX_NEARBY_RESULTS = 25
    const val MAX_FAVORITE_LABEL_CHARS = 200
    const val MAX_FAVORITE_ADDRESS_CHARS = 500

    fun results(
        values: List<SearchResult>,
        imperial: Boolean,
    ): List<NativeSearchResultPresentation> = values
        .take(MAX_NEARBY_RESULTS)
        .mapNotNull { value -> result(value, imperial) }

    fun favorites(
        values: List<NativeSearchFavorite>,
        query: String,
    ): List<NativeSearchFavoritePresentation> {
        val normalizedQuery = query.lowercase(Locale.ROOT)
        return values.asSequence()
            .take(MAX_FAVORITES)
            .mapNotNull(::favorite)
            .filter { value ->
                normalizedQuery.isEmpty() ||
                    value.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    value.address.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .toList()
    }

    fun history(values: List<SearchHistoryEntry>): List<NativeSearchHistoryPresentation> =
        values.take(SearchHistoryProtocol.MAX_STORED_ITEMS).mapNotNull { value ->
            val label = clean(value.label, SearchHistoryProtocol.MAX_LABEL_LENGTH)
                ?: return@mapNotNull null
            if (!validPoint(value.latitude, value.longitude)) return@mapNotNull null
            val comma = label.indexOf(',')
            NativeSearchHistoryPresentation(
                title = if (comma > 0) label.substring(0, comma).trim() else label,
                subtitle = if (comma > 0) label.substring(comma + 1).trim() else "",
                fullLabel = label,
                position = SearchResponsePoint(value.latitude, value.longitude),
            )
        }

    private fun result(value: SearchResult, imperial: Boolean): NativeSearchResultPresentation? {
        if (!validPoint(value.position.latitude, value.position.longitude)) return null
        val title = clean(value.shortName, 120) ?: clean(value.displayName, 300) ?: return null
        val category = clean(value.categoryLabel, 160).orEmpty()
        val display = clean(value.displayName, 300).orEmpty()
        val distance = value.distanceM
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { UnitFormatter(imperial).formatDistance(it) }
        return NativeSearchResultPresentation(
            title = title,
            subtitle = category.ifEmpty { display },
            emoji = value.emoji,
            distanceLabel = distance,
            position = value.position,
        )
    }

    private fun favorite(value: NativeSearchFavorite): NativeSearchFavoritePresentation? {
        val label = clean(value.label, MAX_FAVORITE_LABEL_CHARS) ?: return null
        val address = clean(value.address, MAX_FAVORITE_ADDRESS_CHARS, allowEmpty = true) ?: return null
        if (!validPoint(value.position.latitude, value.position.longitude)) return null
        return NativeSearchFavoritePresentation(label, address, value.position)
    }

    private fun clean(value: String, max: Int, allowEmpty: Boolean = false): String? {
        val clean = value.replace(CONTROL_CHARACTERS, " ").trim().take(max)
        return clean.takeIf { allowEmpty || it.isNotEmpty() }
    }

    private fun validPoint(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private val CONTROL_CHARACTERS = Regex("[\\u0000-\\u001f]")
}

/**
 * Revision-safe in-memory owner for the dormant native search overlay.
 *
 * It owns no coroutine, provider, location or persistence adapter. A future
 * screen ViewModel may feed typed partial/final results and persist callbacks.
 */
class NativeSearchSession(initialImperial: Boolean = false) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeSearchUiSnapshot.hidden())
    private var revision = NativeSearchUiSnapshot.NO_SEARCH_REVISION
    private var imperial = initialImperial
    private var rawResults = emptyList<SearchResult>()
    private var rawFavorites = emptyList<NativeSearchFavorite>()
    private var rawHistory = emptyList<SearchHistoryEntry>()

    val state: StateFlow<NativeSearchUiSnapshot> = _state.asStateFlow()

    fun show(
        revision: Long,
        favorites: List<NativeSearchFavorite> = emptyList(),
        history: List<SearchHistoryEntry> = emptyList(),
        nearbyEnabled: Boolean = false,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Search revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        rawResults = emptyList()
        rawFavorites = favorites.take(NativeSearchPresenter.MAX_FAVORITES)
        rawHistory = history.take(SearchHistoryProtocol.MAX_STORED_ITEMS)
        publish(
            status = NativeSearchUiStatus.Browsing,
            query = "",
            selectedNearby = null,
            nearbyEnabled = nearbyEnabled,
        )
        true
    }

    fun beginQuery(revision: Long, query: String): Boolean = synchronized(lock) {
        require(revision >= 0) { "Search revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        rawResults = emptyList()
        val normalized = query.trim().take(SearchRankingProtocol.MAX_QUERY_LENGTH)
        publish(
            status = if (normalized.isEmpty()) {
                NativeSearchUiStatus.Browsing
            } else {
                NativeSearchUiStatus.Loading
            },
            query = normalized,
            selectedNearby = null,
            nearbyEnabled = _state.value.nearbyEnabled,
        )
        true
    }

    fun updateQuery(revision: Long, query: String): Boolean = synchronized(lock) {
        require(revision >= 0) { "Search revision must be non-negative" }
        if (revision <= this.revision || _state.value.status == NativeSearchUiStatus.Hidden) {
            return false
        }
        this.revision = revision
        rawResults = emptyList()
        publish(
            status = NativeSearchUiStatus.Browsing,
            query = query.trimStart().take(SearchRankingProtocol.MAX_QUERY_LENGTH),
            selectedNearby = null,
            nearbyEnabled = _state.value.nearbyEnabled,
        )
        true
    }

    fun beginNearby(revision: Long, category: NativeSearchNearbyCategory): Boolean =
        synchronized(lock) {
            require(revision >= 0) { "Search revision must be non-negative" }
            if (revision <= this.revision || !_state.value.nearbyEnabled) return false
            this.revision = revision
            rawResults = emptyList()
            publish(
                status = NativeSearchUiStatus.Loading,
                query = "",
                selectedNearby = category,
                nearbyEnabled = true,
            )
            true
        }

    fun submitPartial(revision: Long, results: List<SearchResult>): Boolean = synchronized(lock) {
        if (!acceptsOutcome(revision)) return false
        rawResults = results.take(NativeSearchPresenter.MAX_NEARBY_RESULTS)
        _state.value = _state.value.copy(
            results = NativeSearchPresenter.results(rawResults, imperial),
        )
        true
    }

    fun submitResults(
        revision: Long,
        results: List<SearchResult>,
        notice: NativeSearchNotice? = null,
    ): Boolean = synchronized(lock) {
        if (!acceptsOutcome(revision)) return false
        rawResults = results.take(NativeSearchPresenter.MAX_NEARBY_RESULTS)
        val projected = NativeSearchPresenter.results(rawResults, imperial)
        val current = _state.value
        _state.value = current.copy(
            status = when {
                projected.isNotEmpty() -> NativeSearchUiStatus.Results
                current.selectedNearby != null -> NativeSearchUiStatus.EmptyNearby
                else -> NativeSearchUiStatus.Browsing
            },
            results = projected,
            notice = notice,
        )
        true
    }

    /** Sets the web part of the list; ignored if the search has moved on. */
    fun updateWeb(revision: Long, web: NativeSearchWeb): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeSearchUiStatus.Hidden) return false
        _state.value = _state.value.copy(web = web)
        true
    }

    fun clearHistory(revision: Long): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeSearchUiStatus.Hidden) {
            return false
        }
        if (rawHistory.isEmpty()) return false
        rawHistory = emptyList()
        _state.value = _state.value.copy(history = emptyList())
        true
    }

    fun replaceHistory(revision: Long, history: List<SearchHistoryEntry>): Boolean =
        synchronized(lock) {
            if (revision != this.revision || _state.value.status == NativeSearchUiStatus.Hidden) {
                return false
            }
            rawHistory = history.take(SearchHistoryProtocol.MAX_STORED_ITEMS)
            _state.value = _state.value.copy(
                history = NativeSearchPresenter.history(rawHistory),
            )
            true
        }

    fun updateUnits(imperial: Boolean): Boolean = synchronized(lock) {
        if (this.imperial == imperial) return false
        this.imperial = imperial
        _state.value = _state.value.copy(
            results = NativeSearchPresenter.results(rawResults, imperial),
        )
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Search revision must be non-negative" }
        if (revision < this.revision) return false
        this.revision = revision
        rawResults = emptyList()
        rawFavorites = emptyList()
        rawHistory = emptyList()
        _state.value = NativeSearchUiSnapshot.hidden().copy(revision = revision)
        true
    }

    private fun acceptsOutcome(revision: Long): Boolean =
        revision == this.revision && _state.value.status == NativeSearchUiStatus.Loading

    private fun publish(
        status: NativeSearchUiStatus,
        query: String,
        selectedNearby: NativeSearchNearbyCategory?,
        nearbyEnabled: Boolean,
    ) {
        _state.value = NativeSearchUiSnapshot(
            revision = revision,
            status = status,
            query = query,
            results = NativeSearchPresenter.results(rawResults, imperial),
            favorites = NativeSearchPresenter.favorites(rawFavorites, query),
            history = NativeSearchPresenter.history(rawHistory),
            selectedNearby = selectedNearby,
            nearbyEnabled = nearbyEnabled,
        )
    }
}
