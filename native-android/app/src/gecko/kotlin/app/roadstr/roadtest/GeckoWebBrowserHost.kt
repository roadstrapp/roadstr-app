package app.roadstr.roadtest

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.roadstr.core.discovery.resolve.HostMatching
import app.roadstr.core.discovery.resolve.MatchClass
import app.roadstr.core.discovery.resolve.PagePlace
import app.roadstr.core.discovery.structured.JsonLdPlaceParser
import app.roadstr.core.discovery.structured.StructuredPlace
import app.roadstr.core.discovery.structured.WebPageMessage
import app.roadstr.core.discovery.structured.WebPageMessageSchema
import app.roadstr.core.web.ExternalAction
import app.roadstr.core.web.NavigationDecision
import app.roadstr.core.web.WebNavigationPolicy
import app.roadstr.feature.web.BrowserPagePlace
import app.roadstr.feature.web.WebBrowserHost
import app.roadstr.feature.web.WebBrowserState
import app.roadstr.feature.web.WebBrowserStateReducer
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * The optional in-app browser. One page at a time, in a private session that keeps nothing,
 * with the runtime created the first time a page is opened. The frame around the page, and all
 * the buttons, are the same Compose screen the shell draws for any in-app browser; this class
 * only owns the engine and turns what it reports into [WebBrowserState].
 */
internal class GeckoWebBrowserHost(
    private val activity: ComponentActivity,
    private val openExternal: (String) -> Unit,
) : WebBrowserHost, GeckoPageEvents {
    private val runtime = GeckoRuntimeHolder(activity, ::onPageMessage)
    private val mutable = MutableStateFlow(WebBrowserState())
    private var session: GeckoSession? = null
    private var view: GeckoView? = null

    override val inApp: Boolean = true
    override val state: StateFlow<WebBrowserState> = mutable.asStateFlow()
    override var onDestination: ((latitude: Double, longitude: Double) -> Unit)? = null
    override var pageResolver: ((WebPageMessage, List<StructuredPlace>) -> PagePlace?)? = null

    override fun open(url: String): Boolean {
        val target = when (val decision = WebNavigationPolicy.decide(url)) {
            is NavigationDecision.Allow -> decision.url
            is NavigationDecision.Upgrade -> decision.https
            is NavigationDecision.Confirm, is NavigationDecision.Block -> return false
        }
        mutable.value = WebBrowserStateReducer.opened(target)
        load(target.toString())
        return true
    }

    /** Starts a fresh private session on [url]; the engine is created here the first time. */
    private fun load(url: String) {
        endSession()
        val fresh = newSession()
        session = fresh
        fresh.open(runtime.get())
        fresh.loadUri(url)
    }

    override fun close() {
        endSession()
        mutable.value = WebBrowserState()
        // Nothing the page stored outlives the page.
        runtime.clearAllData()
    }

    override fun goBack() {
        session?.goBack()
    }

    override fun goForward() {
        session?.goForward()
    }

    override fun reload() {
        val current = mutable.value
        if (!current.open) return
        mutable.value = WebBrowserStateReducer.reloading(current)
        // After the engine died there is no session: reloading starts a new one on the same address.
        val live = session
        if (live != null) live.reload() else load(current.url)
    }

    override fun navigateToPagePlace() {
        val place = mutable.value.pagePlace ?: return
        mutable.value = WebBrowserStateReducer.asking(
            mutable.value,
            ExternalAction.UseAsDestination(place.latitude, place.longitude),
        )
    }

    override fun confirmAction() {
        val action = mutable.value.pendingAction ?: return
        mutable.value = WebBrowserStateReducer.answered(mutable.value)
        when (action) {
            is ExternalAction.Dial -> start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null)))
            is ExternalAction.Mail -> start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", action.address, null)))
            is ExternalAction.UseAsDestination -> onDestination?.invoke(action.latitude, action.longitude)
        }
    }

    override fun dismissAction() {
        mutable.value = WebBrowserStateReducer.answered(mutable.value)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val current = session ?: return
        AndroidView(
            factory = { context -> GeckoView(context).also { it.setSession(current); view = it } },
            update = { if (it.session !== current) it.setSession(current) },
            onRelease = { it.releaseSession() },
            modifier = modifier,
        )
    }

    override fun onHostStart() {
        session?.setActive(true)
    }

    override fun onHostStop() {
        session?.setActive(false)
    }

    override fun onTrimMemory(level: Int) {
        if (level >= LOW_MEMORY_LEVEL && session == null) runtime.clearCaches()
    }

    override fun release() = close()

    // --- what the engine reports ---------------------------------------------------------

    override fun onLocation(url: String) {
        val host = try {
            URI(url).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
        mutable.value = WebBrowserStateReducer.location(mutable.value, url, host)
    }

    /**
     * What the page extension reported. Only the page on screen can speak for itself: a message
     * about another site is dropped, whatever it says, and so is anything the schema refuses.
     */
    private fun onPageMessage(raw: String) {
        val current = mutable.value
        if (!current.open) return
        val page = WebPageMessageSchema.parse(raw) ?: return
        if (HostMatching.registrableDomain(page.url.host) != HostMatching.registrableDomain(current.host)) return
        val resolved = pageResolver?.invoke(page, JsonLdPlaceParser.parse(page.jsonLd)) ?: return
        val name = resolved.place.name.ifEmpty { current.title.ifEmpty { current.host } }
        mutable.value = WebBrowserStateReducer.pagePlace(
            current,
            BrowserPagePlace(
                latitude = resolved.place.position.latitude,
                longitude = resolved.place.position.longitude,
                name = name,
                linked = resolved.matchClass == MatchClass.LINKED,
            ),
        )
    }

    override fun onHistory(canGoBack: Boolean, canGoForward: Boolean) {
        mutable.value = WebBrowserStateReducer.history(mutable.value, canGoBack, canGoForward)
    }

    override fun onTitle(title: String) {
        mutable.value = WebBrowserStateReducer.title(mutable.value, title)
    }

    override fun onLoading(loading: Boolean, failed: Boolean) {
        mutable.value = WebBrowserStateReducer.loading(mutable.value, loading, failed)
    }

    override fun onSecurity(secure: Boolean) {
        mutable.value = WebBrowserStateReducer.security(mutable.value, secure)
    }

    override fun onExternalAction(action: ExternalAction) {
        mutable.value = WebBrowserStateReducer.asking(mutable.value, action)
    }

    override fun onNewWindow(url: String) {
        when (val decision = WebNavigationPolicy.decide(url)) {
            is NavigationDecision.Allow -> session?.loadUri(decision.url.toString())
            is NavigationDecision.Upgrade -> session?.loadUri(decision.https.toString())
            is NavigationDecision.Confirm -> onExternalAction(decision.action)
            is NavigationDecision.Block -> Unit
        }
    }

    override fun onCloseRequested() = close()

    /** The engine crashed or was killed: the page is gone, the app is not, and a retry is offered. */
    override fun onEngineGone() {
        endSession()
        mutable.value = WebBrowserStateReducer.engineGone(mutable.value)
    }

    // --- internals -----------------------------------------------------------------------

    private fun newSession(): GeckoSession {
        val events = this as GeckoPageEvents
        val progress = GeckoProgressDelegate(events)
        return GeckoSession(GeckoPrivacyProfile.sessionSettings()).also {
            it.navigationDelegate = GeckoNavigationDelegate(events)
            it.progressDelegate = progress
            it.contentDelegate = progress
            it.permissionDelegate = GeckoPermissionDelegate()
            it.promptDelegate = GeckoPromptDelegate()
        }
    }

    private fun endSession() {
        val old = session ?: return
        session = null
        view?.releaseSession()
        view = null
        old.setActive(false)
        old.close()
    }

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // No app for it: the user simply stays on the page.
        }
    }

    private companion object {
        // The system's "running low" level (10); its named constant is deprecated since API 34.
        const val LOW_MEMORY_LEVEL = 10
    }
}
