package app.roadstr.roadtest

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.roadstr.core.web.ExternalAction
import app.roadstr.core.web.NavigationDecision
import app.roadstr.core.web.WebNavigationPolicy
import app.roadstr.feature.web.WebBrowserHost
import app.roadstr.feature.web.WebBrowserState
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
    private val runtime = GeckoRuntimeHolder(activity)
    private val mutable = MutableStateFlow(WebBrowserState())
    private var session: GeckoSession? = null
    private var view: GeckoView? = null

    override val inApp: Boolean = true
    override val state: StateFlow<WebBrowserState> = mutable.asStateFlow()
    override var onDestination: ((latitude: Double, longitude: Double) -> Unit)? = null

    override fun open(url: String): Boolean {
        val target = when (val decision = WebNavigationPolicy.decide(url)) {
            is NavigationDecision.Allow -> decision.url
            is NavigationDecision.Upgrade -> decision.https
            is NavigationDecision.Confirm, is NavigationDecision.Block -> return false
        }
        endSession()
        val fresh = newSession()
        session = fresh
        mutable.value = WebBrowserState(
            open = true,
            url = target.toString(),
            host = target.host.orEmpty(),
            loading = true,
            secure = target.scheme.equals("https", ignoreCase = true),
        )
        fresh.open(runtime.get())
        fresh.loadUri(target.toString())
        return true
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
        mutable.value = mutable.value.copy(failed = false)
        session?.reload()
    }

    override fun confirmAction() {
        val action = mutable.value.pendingAction ?: return
        mutable.value = mutable.value.copy(pendingAction = null)
        when (action) {
            is ExternalAction.Dial -> start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null)))
            is ExternalAction.Mail -> start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", action.address, null)))
            is ExternalAction.UseAsDestination -> onDestination?.invoke(action.latitude, action.longitude)
        }
    }

    override fun dismissAction() {
        mutable.value = mutable.value.copy(pendingAction = null)
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
        mutable.value = mutable.value.copy(url = url, host = host.ifEmpty { mutable.value.host })
    }

    override fun onHistory(canGoBack: Boolean, canGoForward: Boolean) {
        mutable.value = mutable.value.copy(canGoBack = canGoBack, canGoForward = canGoForward)
    }

    override fun onTitle(title: String) {
        mutable.value = mutable.value.copy(title = title)
    }

    override fun onLoading(loading: Boolean, failed: Boolean) {
        mutable.value = mutable.value.copy(loading = loading, failed = failed)
    }

    override fun onSecurity(secure: Boolean) {
        mutable.value = mutable.value.copy(secure = secure)
    }

    override fun onExternalAction(action: ExternalAction) {
        mutable.value = mutable.value.copy(pendingAction = action)
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
        mutable.value = mutable.value.copy(loading = false, failed = true, canGoBack = false, canGoForward = false)
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
