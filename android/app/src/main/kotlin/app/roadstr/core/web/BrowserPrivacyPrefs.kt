package app.roadstr.core.web

/**
 * The engine preferences of the optional in-app browser, written to the YAML file GeckoView
 * reads at start. They switch off everything a reader of a restaurant page does not need and
 * that would talk to a third party or reach for a device capability: sign-in keys, location,
 * notifications and push, camera and microphone, WebRTC (it can reveal the local address),
 * telemetry and studies, Safe Browsing lookups, captive-portal and connectivity checks, link
 * prefetching and pings, and plain-http loading.
 *
 * The names are Firefox preferences, checked against the shipped engine only by running it:
 * a name the engine does not know is ignored, so each line is verified by the traffic capture
 * and the settings dump the owner makes with the variant build (see docs/world-discovery).
 */
object BrowserPrivacyPrefs {
    val prefs: Map<String, Any> = linkedMapOf(
        // Device capabilities a page must never reach.
        "security.webauth.webauthn" to false,
        "geo.enabled" to false,
        "dom.webnotifications.enabled" to false,
        "dom.push.enabled" to false,
        "media.navigator.enabled" to false,
        "media.peerconnection.enabled" to false,
        "dom.gamepad.enabled" to false,
        "dom.vr.enabled" to false,
        "dom.vr.webxr.enabled" to false,
        "media.autoplay.default" to 5,
        // Nothing may be sent to Mozilla or anyone else on the page's behalf.
        "datareporting.healthreport.uploadEnabled" to false,
        "datareporting.policy.dataSubmissionEnabled" to false,
        "toolkit.telemetry.enabled" to false,
        "toolkit.telemetry.unified" to false,
        "app.shield.optoutstudies.enabled" to false,
        "app.normandy.enabled" to false,
        "browser.safebrowsing.malware.enabled" to false,
        "browser.safebrowsing.phishing.enabled" to false,
        "browser.safebrowsing.downloads.enabled" to false,
        "network.captive-portal-service.enabled" to false,
        "network.connectivity-service.enabled" to false,
        // No guessing ahead of the reader.
        "network.prefetch-next" to false,
        "network.dns.disablePrefetch" to true,
        "network.predictor.enabled" to false,
        "network.http.speculative-parallel-limit" to 0,
        "browser.send_pings" to false,
        "beacon.enabled" to false,
        // Less to give away when a page is loaded.
        "network.http.referer.XOriginPolicy" to 2,
        "network.http.referer.trimmingPolicy" to 2,
        "dom.security.https_only_mode" to true,
    )

    /** The file's text: a comment, `prefs:` and one `name: value` line per preference. */
    fun yaml(): String = buildString {
        append("# Roadstr in-app browser preferences. Written by the app at start; edits are overwritten.\n")
        append("prefs:\n")
        for ((name, value) in prefs) {
            append("  ").append(name).append(": ")
            append(if (value is String) "\"$value\"" else value.toString())
            append('\n')
        }
    }
}
