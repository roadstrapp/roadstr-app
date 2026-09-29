package app.roadstr.feature.place

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.search.OsmPlaceDetails
import app.roadstr.core.time.OpenState
import app.roadstr.core.time.OpeningHours
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativePlaceUiStatus {
    Hidden,
    Loading,
    Ready,
}

enum class NativePlaceOpeningState {
    Unknown,
    Open,
    Closed,
}

data class NativePlaceArticle(
    val title: String,
    val extract: String,
    val imageUrl: URI?,
    val pageUrl: URI?,
)

data class NativePlaceOpeningPresentation(
    val state: NativePlaceOpeningState,
    val raw: String,
    val changeLabel: String?,
)

data class NativePlaceUiSnapshot(
    val revision: Long,
    val status: NativePlaceUiStatus,
    val point: SearchResponsePoint?,
    val title: String?,
    val address: String?,
    val article: NativePlaceArticle?,
    val details: OsmPlaceDetails?,
    val wikiQuery: String?,
    val opening: NativePlaceOpeningPresentation?,
) {
    companion object {
        const val NO_PLACE_REVISION = -1L

        fun hidden() = NativePlaceUiSnapshot(
            revision = NO_PLACE_REVISION,
            status = NativePlaceUiStatus.Hidden,
            point = null,
            title = null,
            address = null,
            article = null,
            details = null,
            wikiQuery = null,
            opening = null,
        )
    }
}

data class NativePlaceArticleInput(
    val title: String,
    val extract: String,
    val imageUrl: String? = null,
    val pageUrl: String? = null,
)

/** Pure bounded projection for the place sheet and its opening-hours badge. */
object NativePlacePresenter {
    fun article(input: NativePlaceArticleInput?): NativePlaceArticle? {
        input ?: return null
        val title = clean(input.title, 200) ?: return null
        val extract = clean(input.extract, 1_000).orEmpty()
        return NativePlaceArticle(
            title = title,
            extract = extract,
            imageUrl = safeHttps(input.imageUrl, wikipediaOnly = false),
            pageUrl = safeHttps(input.pageUrl, wikipediaOnly = true),
        )
    }

    fun title(
        point: SearchResponsePoint,
        address: String?,
        article: NativePlaceArticle?,
        details: OsmPlaceDetails?,
    ): String = article?.title
        ?: details?.name
        ?: address
        ?: "%.5f, %.5f".format(Locale.ROOT, point.latitude, point.longitude)

    fun opening(
        raw: String?,
        now: LocalDateTime,
        locale: Locale,
    ): NativePlaceOpeningPresentation? {
        val value = clean(raw, 300) ?: return null
        val status = OpeningHours.evaluate(value, now)
        val state = when (status.state) {
            OpenState.OPEN -> NativePlaceOpeningState.Open
            OpenState.CLOSED -> NativePlaceOpeningState.Closed
            OpenState.UNKNOWN -> NativePlaceOpeningState.Unknown
        }
        val change = status.nextChange
        val changeLabel = change?.let { valueAtChange ->
            val time = valueAtChange.format(DateTimeFormatter.ofPattern("HH:mm", locale))
            if (state == NativePlaceOpeningState.Closed && valueAtChange.toLocalDate() != now.toLocalDate()) {
                val weekday = valueAtChange.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                "$weekday $time"
            } else {
                time
            }
        }
        return NativePlaceOpeningPresentation(state, value, changeLabel)
    }

    fun cleanAddress(value: String?): String? = clean(value, 500)

    fun cleanWikiQuery(value: String?): String? = clean(value, 200)

    private fun safeHttps(value: String?, wikipediaOnly: Boolean): URI? = try {
        val uri = value?.let(::URI) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        uri.takeIf {
            it.scheme == "https" &&
                it.port == -1 &&
                it.userInfo == null &&
                (!wikipediaOnly || host == "wikipedia.org" || host.endsWith(".wikipedia.org"))
        }
    } catch (_: Exception) {
        null
    }

    private fun clean(value: String?, max: Int): String? {
        val clean = value?.replace(CONTROL_CHARACTERS, " ")?.trim()?.take(max) ?: return null
        return clean.takeIf(String::isNotEmpty)
    }

    private val CONTROL_CHARACTERS = Regex("[\\u0000-\\u001f]")
}

/** Revision-safe in-memory owner; it intentionally owns no provider or intent launcher. */
class NativePlaceSession(
    private var now: LocalDateTime = LocalDateTime.now(),
    private var locale: Locale = Locale.getDefault(),
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativePlaceUiSnapshot.hidden())
    private var revision = NativePlaceUiSnapshot.NO_PLACE_REVISION
    private var rawOpeningHours: String? = null

    val state: StateFlow<NativePlaceUiSnapshot> = _state.asStateFlow()

    fun begin(
        revision: Long,
        point: SearchResponsePoint,
        address: String? = null,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Place revision must be non-negative" }
        requireValidPoint(point)
        if (revision <= this.revision) return false
        this.revision = revision
        rawOpeningHours = null
        val safeAddress = NativePlacePresenter.cleanAddress(address)
        _state.value = NativePlaceUiSnapshot(
            revision = revision,
            status = NativePlaceUiStatus.Loading,
            point = point,
            title = safeAddress,
            address = safeAddress,
            article = null,
            details = null,
            wikiQuery = null,
            opening = null,
        )
        true
    }

    fun submit(
        revision: Long,
        details: OsmPlaceDetails? = null,
        article: NativePlaceArticleInput? = null,
        address: String? = _state.value.address,
        wikiQuery: String? = null,
        openingHours: String? = details?.openingHours,
    ): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status != NativePlaceUiStatus.Loading) {
            return false
        }
        val point = requireNotNull(_state.value.point)
        val safeAddress = NativePlacePresenter.cleanAddress(address)
        val safeArticle = NativePlacePresenter.article(article)
        rawOpeningHours = openingHours
        _state.value = NativePlaceUiSnapshot(
            revision = revision,
            status = NativePlaceUiStatus.Ready,
            point = point,
            title = NativePlacePresenter.title(point, safeAddress, safeArticle, details),
            address = safeAddress,
            article = safeArticle,
            details = details,
            wikiQuery = NativePlacePresenter.cleanWikiQuery(wikiQuery),
            opening = NativePlacePresenter.opening(openingHours, now, locale),
        )
        true
    }

    fun updateClock(now: LocalDateTime, locale: Locale): Boolean = synchronized(lock) {
        if (this.now == now && this.locale == locale) return false
        this.now = now
        this.locale = locale
        if (_state.value.status == NativePlaceUiStatus.Ready) {
            _state.value = _state.value.copy(
                opening = NativePlacePresenter.opening(rawOpeningHours, now, locale),
            )
        }
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Place revision must be non-negative" }
        if (revision < this.revision) return false
        this.revision = revision
        rawOpeningHours = null
        _state.value = NativePlaceUiSnapshot.hidden().copy(revision = revision)
        true
    }

    private fun requireValidPoint(point: SearchResponsePoint) {
        require(
            point.latitude.isFinite() &&
                point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 &&
                point.longitude in -180.0..180.0,
        ) { "Place point must be valid WGS84" }
    }
}
