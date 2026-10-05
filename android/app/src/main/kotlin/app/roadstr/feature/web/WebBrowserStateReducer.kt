package app.roadstr.feature.web

import app.roadstr.core.web.ExternalAction
import java.net.URI

/**
 * How [WebBrowserState] changes when the engine reports something, apart from any engine, so the
 * rules that matter can be tested without one: a new load starts from nothing, a page never keeps
 * what the previous one said about itself, and an engine that dies leaves the app standing with a
 * page it can offer to load again.
 */
object WebBrowserStateReducer {
    fun opened(target: URI): WebBrowserState = WebBrowserState(
        open = true,
        url = target.toString(),
        host = target.host.orEmpty(),
        loading = true,
        secure = target.scheme.equals("https", ignoreCase = true),
    )

    /** The page moved to [url]; what the old site said about its place is dropped when the site changes. */
    fun location(state: WebBrowserState, url: String, host: String): WebBrowserState {
        val moved = host.isNotEmpty() && host != state.host
        return state.copy(
            url = url,
            host = host.ifEmpty { state.host },
            pagePlace = if (moved) null else state.pagePlace,
        )
    }

    fun history(state: WebBrowserState, canGoBack: Boolean, canGoForward: Boolean): WebBrowserState =
        state.copy(canGoBack = canGoBack, canGoForward = canGoForward)

    fun title(state: WebBrowserState, title: String): WebBrowserState = state.copy(title = title)

    /** A load starting clears what the last page said about its place; one ending can mark a failure. */
    fun loading(state: WebBrowserState, loading: Boolean, failed: Boolean): WebBrowserState = state.copy(
        loading = loading,
        failed = failed,
        pagePlace = if (loading) null else state.pagePlace,
    )

    fun security(state: WebBrowserState, secure: Boolean): WebBrowserState = state.copy(secure = secure)

    fun asking(state: WebBrowserState, action: ExternalAction): WebBrowserState = state.copy(pendingAction = action)

    fun answered(state: WebBrowserState): WebBrowserState = state.copy(pendingAction = null)

    fun pagePlace(state: WebBrowserState, place: BrowserPagePlace): WebBrowserState = state.copy(pagePlace = place)

    /** A reload clears the failure note until the engine says again. */
    fun reloading(state: WebBrowserState): WebBrowserState = state.copy(failed = false, loading = true)

    /**
     * The engine crashed or was killed. The page is gone and the app is not: the browser stays open
     * on the same address with a failure note, nothing the page said survives, and a reload loads it again.
     */
    fun engineGone(state: WebBrowserState): WebBrowserState = state.copy(
        loading = false,
        failed = true,
        canGoBack = false,
        canGoForward = false,
        pendingAction = null,
        pagePlace = null,
    )
}
