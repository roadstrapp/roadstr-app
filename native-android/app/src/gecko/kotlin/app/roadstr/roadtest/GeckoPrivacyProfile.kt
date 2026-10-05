package app.roadstr.roadtest

import java.io.File
import app.roadstr.core.web.BrowserPrivacyPrefs
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * What the in-app browser is configured to do about privacy. Every choice here is the
 * most private one that still lets a restaurant page load; the engine preferences it cannot
 * express through settings are in [BrowserPrivacyPrefs] and written to a file at start.
 *
 * Nothing survives the session: it runs in private mode, its storage is cleared when it
 * closes, there is no history store, no autofill, no sign-in manager, no Safe Browsing lookup
 * (it sends URL hash prefixes to a third party), no DNS-over-HTTPS provider and no crash
 * uploader.
 */
internal object GeckoPrivacyProfile {
    fun runtimeSettings(preferencesFile: File): GeckoRuntimeSettings =
        GeckoRuntimeSettings.Builder()
            .configFilePath(preferencesFile.absolutePath)
            .contentBlocking(contentBlocking())
            .globalPrivacyControlEnabled(true)
            .remoteDebuggingEnabled(false)
            .aboutConfigEnabled(false)
            .loginAutofillEnabled(false)
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            .trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_DISABLED)
            .lowMemoryDetection(true)
            .translationsOfferPopup(false)
            .crashPullNeverShowAgain(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .build()

    fun sessionSettings(): GeckoSessionSettings =
        GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            .useTrackingProtection(true)
            .allowJavascript(true)
            .suspendMediaWhenInactive(true)
            .build()

    /** Writes the engine preferences where the runtime will read them. */
    fun writePreferences(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(BrowserPrivacyPrefs.yaml())
    }

    private fun contentBlocking(): ContentBlocking.Settings =
        ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
            .cookiePurging(true)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .build()
}
