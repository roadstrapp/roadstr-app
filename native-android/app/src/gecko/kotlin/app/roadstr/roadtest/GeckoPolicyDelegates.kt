package app.roadstr.roadtest

import app.roadstr.core.web.ExternalAction
import app.roadstr.core.web.NavigationDecision
import app.roadstr.core.web.RedirectGuard
import app.roadstr.core.web.WebNavigationPolicy
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** What the delegates tell the host; the host owns the state, the delegates own no UI. */
internal interface GeckoPageEvents {
    fun onLocation(url: String)

    fun onHistory(canGoBack: Boolean, canGoForward: Boolean)

    fun onTitle(title: String)

    fun onLoading(loading: Boolean, failed: Boolean)

    fun onSecurity(secure: Boolean)

    fun onExternalAction(action: ExternalAction)

    /** The page asked for a new window; the host may load it in the same one if the policy allows. */
    fun onNewWindow(url: String)

    fun onCloseRequested()

    fun onEngineGone()
}

/**
 * Every navigation passes through [WebNavigationPolicy]: web addresses load, plain http is
 * retried as https, phone, mail and map links are confirmed by the user and never started by
 * the page, everything else is refused, and a redirect chain ends after ten hops.
 */
internal class GeckoNavigationDelegate(private val events: GeckoPageEvents) : GeckoSession.NavigationDelegate {
    private val redirects = RedirectGuard()

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? {
        if (request.uri == BLANK) return GeckoResult.allow()
        if (request.isRedirect) {
            if (!redirects.allowNext()) return GeckoResult.deny()
        } else {
            redirects.reset()
        }
        return when (val decision = WebNavigationPolicy.decide(request.uri)) {
            is NavigationDecision.Allow -> GeckoResult.allow()
            is NavigationDecision.Upgrade -> {
                session.loadUri(decision.https.toString())
                GeckoResult.deny()
            }
            is NavigationDecision.Confirm -> {
                events.onExternalAction(decision.action)
                GeckoResult.deny()
            }
            is NavigationDecision.Block -> GeckoResult.deny()
        }
    }

    /** A window the page tried to open; never a new session, at most the same page in place. */
    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        events.onNewWindow(uri)
        return null
    }

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
    ) {
        if (url != null && url != BLANK) events.onLocation(url)
    }

    private var canGoBack = false
    private var canGoForward = false

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        this.canGoBack = canGoBack
        events.onHistory(this.canGoBack, canGoForward)
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        this.canGoForward = canGoForward
        events.onHistory(canGoBack, this.canGoForward)
    }

    private companion object {
        const val BLANK = "about:blank"
    }
}

/** Progress, security and title of the page, reported to the host. */
internal class GeckoProgressDelegate(private val events: GeckoPageEvents) :
    GeckoSession.ProgressDelegate,
    GeckoSession.ContentDelegate {
    override fun onPageStart(session: GeckoSession, url: String) = events.onLoading(loading = true, failed = false)

    override fun onPageStop(session: GeckoSession, success: Boolean) = events.onLoading(loading = false, failed = !success)

    override fun onSecurityChange(
        session: GeckoSession,
        securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
    ) = events.onSecurity(securityInfo.isSecure)

    override fun onTitleChange(session: GeckoSession, title: String?) = events.onTitle(title.orEmpty())

    override fun onCloseRequest(session: GeckoSession) = events.onCloseRequested()

    override fun onCrash(session: GeckoSession) = events.onEngineGone()

    override fun onKill(session: GeckoSession) = events.onEngineGone()
}

/** A page gets no device capability and no Android permission: the answer is always no. */
internal class GeckoPermissionDelegate : GeckoSession.PermissionDelegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) = callback.reject()

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int> = GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) = callback.reject()
}

/** Pop-ups, redirects through a prompt, file pickers and sign-in questions are refused or dismissed. */
internal class GeckoPromptDelegate : GeckoSession.PromptDelegate {
    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    override fun onRedirectPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RedirectPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> = GeckoResult.fromValue(prompt.dismiss())

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> = GeckoResult.fromValue(prompt.dismiss())

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> = GeckoResult.fromValue(prompt.dismiss())
}
