package app.roadstr.roadtest

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Installs the bundled page extension and hands what it says to [onMessage] as text.
 *
 * The extension reads a page's own schema.org markup and sends one message; it has no network
 * access of its own and the page cannot call it. What arrives here is still untrusted, so the
 * text is checked by `WebPageMessageSchema` before any of it is used. The extension is an extra:
 * if it cannot be installed, pages load exactly the same.
 */
internal class GeckoPagePlaceExtractor(private val onMessage: (String) -> Unit) {
    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            if (nativeApp == NATIVE_APP) onMessage(message.toString())
            return null
        }
    }

    fun install(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, ID).accept(
            { extension ->
                if (extension != null) {
                    extension.setMessageDelegate(delegate, NATIVE_APP)
                    // Pages run in private sessions, and a built-in extension is off there unless allowed.
                    controller.setAllowedInPrivateBrowsing(extension, true)
                }
            },
            { _ -> },
        )
    }

    private companion object {
        const val LOCATION = "resource://android/assets/web/place-extractor/"
        const val ID = "place-extractor@roadstr.app"
        const val NATIVE_APP = "roadstrExtractor"
    }
}
