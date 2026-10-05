package app.roadstr.roadtest

import androidx.activity.ComponentActivity
import app.roadstr.feature.web.WebBrowserHost

/** The GeckoView build: web pages open inside the app, in a private session that keeps nothing. */
internal object WebBrowserHostFactory {
    fun create(activity: ComponentActivity, openExternal: (String) -> Unit): WebBrowserHost =
        GeckoWebBrowserHost(activity, openExternal)
}
