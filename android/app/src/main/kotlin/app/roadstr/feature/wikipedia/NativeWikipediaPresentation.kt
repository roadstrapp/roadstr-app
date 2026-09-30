package app.roadstr.feature.wikipedia

import java.net.URI
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NativeWikipediaUriPolicy {
    private val languageLabel = Regex("^[a-z0-9-]{1,24}$")

    fun parseAllowedArticle(value: String): URI? = try {
        URI(value).takeIf(::isAllowedArticle)
    } catch (_: Exception) {
        null
    }

    fun isAllowedArticle(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val labels = host.split('.')
        val path = uri.rawPath ?: return false
        val standard = labels.size == 3 && labels[1] == "wikipedia" && labels[2] == "org"
        val mobile = labels.size == 4 && labels[1] == "m" &&
            labels[2] == "wikipedia" && labels[3] == "org"
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null && uri.port == -1 &&
            (standard || mobile) && languageLabel.matches(labels[0]) &&
            path.startsWith("/wiki/") && path.length > "/wiki/".length
    }

    fun allowsNavigation(url: String, isMainFrame: Boolean): Boolean =
        isMainFrame && parseAllowedArticle(url) != null
}

enum class NativeWikipediaStatus { Hidden, Loading, Ready, Failed }

data class NativeWikipediaSnapshot(
    val revision: Long,
    val status: NativeWikipediaStatus,
    val title: String,
    val originalUri: URI?,
    val currentUri: URI?,
    val progress: Int,
    val canGoBack: Boolean,
) {
    companion object {
        const val NO_REVISION = -1L
        fun hidden() = NativeWikipediaSnapshot(
            NO_REVISION,
            NativeWikipediaStatus.Hidden,
            "",
            null,
            null,
            0,
            false,
        )
    }
}

/** Revision-fenced reader state; owns no WebView, cookies, Intent or network client. */
class NativeWikipediaSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeWikipediaSnapshot.hidden())
    private var revision = NativeWikipediaSnapshot.NO_REVISION
    val state: StateFlow<NativeWikipediaSnapshot> = _state.asStateFlow()

    fun open(revision: Long, articleUrl: String, title: String): Boolean = synchronized(lock) {
        require(revision >= 0) { "Wikipedia revision must be non-negative" }
        if (revision <= this.revision) return false
        val uri = NativeWikipediaUriPolicy.parseAllowedArticle(articleUrl) ?: return false
        val safeTitle = title.replace(CONTROLS, " ").trim().take(200)
        if (safeTitle.isEmpty()) return false
        this.revision = revision
        _state.value = NativeWikipediaSnapshot(
            revision,
            NativeWikipediaStatus.Loading,
            safeTitle,
            uri,
            uri,
            0,
            false,
        )
        true
    }

    fun pageStarted(revision: Long, url: String): Boolean = synchronized(lock) {
        if (!active(revision)) return false
        val uri = NativeWikipediaUriPolicy.parseAllowedArticle(url) ?: return false
        _state.value = _state.value.copy(
            status = NativeWikipediaStatus.Loading,
            currentUri = uri,
            progress = 0,
        )
        true
    }

    fun progress(revision: Long, value: Int): Boolean = synchronized(lock) {
        if (!active(revision)) return false
        val bounded = value.coerceIn(0, 100)
        if (_state.value.progress == bounded) return false
        _state.value = _state.value.copy(progress = bounded)
        true
    }

    fun pageFinished(revision: Long, url: String, canGoBack: Boolean): Boolean =
        synchronized(lock) {
            if (!active(revision)) return false
            if (_state.value.status == NativeWikipediaStatus.Failed) return false
            val uri = NativeWikipediaUriPolicy.parseAllowedArticle(url) ?: return false
            _state.value = _state.value.copy(
                status = NativeWikipediaStatus.Ready,
                currentUri = uri,
                progress = 100,
                canGoBack = canGoBack,
            )
            true
        }

    fun fail(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision)) return false
        _state.value = _state.value.copy(status = NativeWikipediaStatus.Failed)
        true
    }

    fun retry(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision) || _state.value.status != NativeWikipediaStatus.Failed) return false
        _state.value = _state.value.copy(status = NativeWikipediaStatus.Loading, progress = 0)
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision)) return false
        _state.value = NativeWikipediaSnapshot.hidden().copy(revision = revision)
        true
    }

    private fun active(value: Long): Boolean =
        value == revision && _state.value.status != NativeWikipediaStatus.Hidden

    private companion object {
        val CONTROLS = Regex("[\\u0000-\\u001f]")
    }
}
