package app.roadstr.feature.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.roadstr.core.discovery.resolve.PagePlace
import app.roadstr.core.discovery.structured.StructuredPlace
import app.roadstr.core.discovery.structured.WebPageMessage
import app.roadstr.core.web.ExternalAction
import app.roadstr.core.web.NavigationDecision
import app.roadstr.core.web.WebNavigationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The place the page on screen is about, offered as "navigate here". */
data class BrowserPagePlace(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    /** True when it matched a place from the map; false when it comes from the page alone. */
    val linked: Boolean,
) {
    // A name and a position are where the user may be going.
    override fun toString(): String = "BrowserPagePlace(linked=)"
}

/** What the in-app browser shows about the page it has open. */
data class WebBrowserState(
    val open: Boolean = false,
    val url: String = "",
    val host: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val secure: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val failed: Boolean = false,
    /** A phone, mail or map link the page asked for; nothing happens until the user confirms it. */
    val pendingAction: ExternalAction? = null,
    val pagePlace: BrowserPagePlace? = null,
) {
    // The address of the page is what the user is reading.
    override fun toString(): String = "WebBrowserState(open=$open)"
}

/**
 * Where a web page opens. The default is the system browser, which draws nothing in the app;
 * the optional GeckoView build draws the page inside Roadstr, in a private session that keeps
 * nothing. Either way the address is checked by [WebNavigationPolicy] first.
 */
interface WebBrowserHost {
    /** True when pages are drawn inside the app by [Content]. */
    val inApp: Boolean

    val state: StateFlow<WebBrowserState>

    /** Opens [url]; false when the policy refused it. */
    fun open(url: String): Boolean

    fun close()

    fun goBack() = Unit

    fun goForward() = Unit

    fun reload() = Unit

    /** The user said yes to the phone, mail or map link the page asked for. */
    fun confirmAction() = Unit

    fun dismissAction() = Unit

    /** Asked what a page's own place data amounts to; it sees the page only through the extension. */
    var pageResolver: ((WebPageMessage, List<StructuredPlace>) -> PagePlace?)?
        get() = null
        set(_) = Unit

    /** The user tapped "navigate here" for the place the page is about; the app confirms it first. */
    fun navigateToPagePlace() = Unit

    /** Told when the user asks for a map link to become a destination. */
    var onDestination: ((latitude: Double, longitude: Double) -> Unit)?
        get() = null
        set(_) = Unit

    /** The page of the in-app browser; nothing for the system browser. */
    @Composable
    fun Content(modifier: Modifier) = Unit

    /** The app is in front again: an in-app browser resumes its page. */
    fun onHostStart() = Unit

    /** The app went to the background: an in-app browser stops its page. */
    fun onHostStop() = Unit

    /** The system is short of memory: an in-app browser gives its engine back. */
    fun onTrimMemory(level: Int) = Unit

    /** The screen is going away for good. */
    fun release() = Unit
}

/**
 * The default: the page opens in the user's own browser. A plain http address is handed over as
 * it is, because the browser has its own warning for it and refusing would leave no way to open
 * a site that has no https.
 */
class SystemBrowserHost(private val openExternal: (String) -> Unit) : WebBrowserHost {
    private val closed = MutableStateFlow(WebBrowserState())

    override val inApp: Boolean = false
    override val state: StateFlow<WebBrowserState> = closed.asStateFlow()

    override fun open(url: String): Boolean = when (WebNavigationPolicy.decide(url)) {
        is NavigationDecision.Allow, is NavigationDecision.Upgrade -> {
            openExternal(url.trim())
            true
        }
        is NavigationDecision.Confirm, is NavigationDecision.Block -> false
    }

    override fun close() = Unit
}
