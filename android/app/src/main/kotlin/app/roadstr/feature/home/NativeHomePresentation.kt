package app.roadstr.feature.home

import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeHomeChromeStatus {
    Hidden,
    Idle,
}

enum class NativeHomeAction {
    Navigate,
    Locate,
    Parking,
    Activity,
    Events,
    Notifications,
    Profile,
    Menu,
}

data class NativeHomeFavorite(
    val id: String,
    val label: String,
)

/** Value-only projection of the states that replace Flutter's idle home chrome. */
data class NativeHomeInput(
    val navigating: Boolean = false,
    val searchVisible: Boolean = false,
    val placeVisible: Boolean = false,
    val plannerVisible: Boolean = false,
    val previewVisible: Boolean = false,
    val alternativesVisible: Boolean = false,
    val transitVisible: Boolean = false,
    val calculating: Boolean = false,
    val hasRoute: Boolean = false,
    val unreadActivityCount: Int = 0,
    val favorites: List<NativeHomeFavorite> = emptyList(),
)

data class NativeHomeSnapshot(
    val revision: Long,
    val status: NativeHomeChromeStatus,
    val expanded: Boolean,
    val unreadActivityCount: Int,
    val favorites: List<NativeHomeFavorite>,
) {
    val visible: Boolean
        get() = status == NativeHomeChromeStatus.Idle

    val unreadActivityLabel: String?
        get() = when {
            unreadActivityCount <= 0 -> null
            unreadActivityCount > 99 -> "99+"
            else -> unreadActivityCount.toString()
        }

    companion object {
        const val NO_REVISION = -1L

        fun hidden(revision: Long = NO_REVISION) = NativeHomeSnapshot(
            revision = revision,
            status = NativeHomeChromeStatus.Hidden,
            expanded = false,
            unreadActivityCount = 0,
            favorites = emptyList(),
        )
    }
}

/** Pure Flutter-parity policy for the map-first dashboard and bottom bar. */
object NativeHomePresenter {
    const val MAX_VISIBLE_FAVORITES = 5
    const val MAX_FAVORITE_ID_CHARS = 128
    const val MAX_FAVORITE_LABEL_CHARS = 200
    const val MAX_UNREAD_COUNT = 100

    private val CONTROLS = Regex("[\\u0000-\\u001f\\u007f]")

    fun present(
        revision: Long,
        input: NativeHomeInput,
        expanded: Boolean = false,
    ): NativeHomeSnapshot {
        require(revision >= 0) { "Home revision must be non-negative" }
        val idle = !input.navigating &&
            !input.searchVisible &&
            !input.placeVisible &&
            !input.plannerVisible &&
            !input.previewVisible &&
            !input.alternativesVisible &&
            !input.transitVisible &&
            !input.calculating &&
            !input.hasRoute
        if (!idle) return NativeHomeSnapshot.hidden(revision)

        return NativeHomeSnapshot(
            revision = revision,
            status = NativeHomeChromeStatus.Idle,
            expanded = expanded,
            unreadActivityCount = input.unreadActivityCount.coerceIn(0, MAX_UNREAD_COUNT),
            favorites = favorites(input.favorites),
        )
    }

    private fun favorites(values: Iterable<NativeHomeFavorite>): List<NativeHomeFavorite> {
        val ids = LinkedHashSet<String>()
        val result = ArrayList<NativeHomeFavorite>(MAX_VISIBLE_FAVORITES)
        for (value in values) {
            val id = clean(value.id, MAX_FAVORITE_ID_CHARS)
            val label = clean(value.label, MAX_FAVORITE_LABEL_CHARS)
            if (id == null || label == null || !ids.add(id)) continue
            result += NativeHomeFavorite(id, label)
            if (result.size == MAX_VISIBLE_FAVORITES) break
        }
        return Collections.unmodifiableList(result)
    }

    private fun clean(value: String, limit: Int): String? = value
        .replace(CONTROLS, " ")
        .trim()
        .takeIf(String::isNotEmpty)
        ?.take(limit)
}

/**
 * Revision-fenced owner for the dormant home chrome.
 *
 * Actions are returned to an external owner and never open storage, location,
 * navigation, profile, notification or settings integrations themselves.
 */
class NativeHomeSession(
    initialInput: NativeHomeInput = NativeHomeInput(),
) {
    private val lock = Any()
    private var revision = 0L
    private val _state = MutableStateFlow(NativeHomePresenter.present(revision, initialInput))

    val state: StateFlow<NativeHomeSnapshot> = _state.asStateFlow()

    fun replace(revision: Long, input: NativeHomeInput): Boolean = synchronized(lock) {
        require(revision >= 0) { "Home revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        _state.value = NativeHomePresenter.present(revision, input)
        true
    }

    fun toggleExpanded(revision: Long): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeHomeChromeStatus.Idle) {
            return false
        }
        _state.value = current.copy(expanded = !current.expanded)
        true
    }

    fun collapse(revision: Long): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || !current.visible || !current.expanded) return false
        _state.value = current.copy(expanded = false)
        true
    }

    fun action(revision: Long, action: NativeHomeAction): NativeHomeAction? = synchronized(lock) {
        if (revision != this.revision || !_state.value.visible) null else action
    }

    fun selectFavorite(revision: Long, id: String): NativeHomeFavorite? = synchronized(lock) {
        if (revision != this.revision || !_state.value.visible) return null
        _state.value.favorites.firstOrNull { it.id == id }
    }
}
