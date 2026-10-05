package app.roadstr.roadtest

import androidx.activity.ComponentActivity
import app.roadstr.feature.web.SystemBrowserHost
import app.roadstr.feature.web.WebBrowserHost

/** The default build: web pages open in the user's own browser. */
internal object WebBrowserHostFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(activity: ComponentActivity, openExternal: (String) -> Unit): WebBrowserHost =
        SystemBrowserHost(openExternal)
}
