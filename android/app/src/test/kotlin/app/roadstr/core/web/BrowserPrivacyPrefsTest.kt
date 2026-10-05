package app.roadstr.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPrivacyPrefsTest {
    private val lines = BrowserPrivacyPrefs.yaml().lines().filter { it.isNotEmpty() }

    @Test
    fun `the file is a comment, a prefs key and one indented line per preference`() {
        assertTrue(lines[0].startsWith("#"))
        assertEquals("prefs:", lines[1])
        assertEquals(BrowserPrivacyPrefs.prefs.size, lines.size - 2)
        assertTrue(lines.drop(2).all { Regex("^  [A-Za-z0-9._-]+: (true|false|\\d+)$").matches(it) })
    }

    @Test
    fun `no tab and no duplicate preference`() {
        assertFalse(BrowserPrivacyPrefs.yaml().contains('\t'))
        val names = lines.drop(2).map { it.trim().substringBefore(':') }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `the preferences that matter most are set the safe way`() {
        val prefs = BrowserPrivacyPrefs.prefs
        for (off in listOf(
            "security.webauth.webauthn", "geo.enabled", "dom.webnotifications.enabled", "dom.push.enabled",
            "media.navigator.enabled", "media.peerconnection.enabled", "toolkit.telemetry.enabled",
            "datareporting.healthreport.uploadEnabled", "app.shield.optoutstudies.enabled",
            "browser.safebrowsing.malware.enabled", "browser.safebrowsing.phishing.enabled",
            "network.captive-portal-service.enabled", "network.prefetch-next", "network.predictor.enabled",
        )) {
            assertEquals(off, false, prefs[off])
        }
        assertEquals(true, prefs["dom.security.https_only_mode"])
        assertEquals(0, prefs["network.http.speculative-parallel-limit"])
    }

    @Test
    fun `the output is the same every time`() {
        assertEquals(BrowserPrivacyPrefs.yaml(), BrowserPrivacyPrefs.yaml())
    }
}
