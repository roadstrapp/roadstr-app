package app.roadstr.core.web

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNavigationPolicyTest {
    private fun decide(url: String) = WebNavigationPolicy.decide(url)

    private fun blocked(url: String, reason: BlockReason) =
        assertEquals(url, NavigationDecision.Block(reason), decide(url))

    @Test
    fun `https pages are allowed as they are`() {
        assertEquals(NavigationDecision.Allow(URI("https://example.org/menu?x=1#top")), decide("https://example.org/menu?x=1#top"))
        assertEquals(NavigationDecision.Allow(URI("https://example.org:8443/a")), decide("  HTTPS://example.org:8443/a  ".replace("HTTPS", "https")))
    }

    @Test
    fun `plain http is upgraded to https and never loaded as it is`() {
        assertEquals(NavigationDecision.Upgrade(URI("https://example.org/menu")), decide("http://example.org/menu"))
        assertEquals(NavigationDecision.Upgrade(URI("https://example.org/a?b=c#d")), decide("http://example.org:80/a?b=c#d"))
        assertEquals(NavigationDecision.Upgrade(URI("https://example.org:8080/a")), decide("http://example.org:8080/a"))
    }

    @Test
    fun `schemes that start something else are refused`() {
        for (url in listOf(
            "intent://scan/#Intent;scheme=zxing;end", "javascript:alert(1)", "data:text/html,<b>x</b>",
            "file:///etc/hosts", "content://com.example/x", "market://details?id=a", "sms:+390000000",
            "ftp://example.org/a", "blob:https://example.org/uuid", "view-source:https://example.org", "custom-app://open",
        )) {
            blocked(url, BlockReason.UNSAFE_SCHEME)
        }
    }

    @Test
    fun `an address with a password, spaces or no host is refused`() {
        blocked("https://user:pass@example.org/", BlockReason.CREDENTIALS)
        blocked("https://example.org/a b", BlockReason.MALFORMED)
        blocked("https://", BlockReason.MALFORMED)
        blocked("https:///path", BlockReason.MALFORMED)
        blocked("example.org", BlockReason.MALFORMED)
        blocked("", BlockReason.EMPTY)
        blocked("   ", BlockReason.EMPTY)
        blocked("https://example.org/\u0000", BlockReason.MALFORMED)
        blocked("https://example.org/" + "a".repeat(WebNavigationPolicy.MAX_URL_CHARS), BlockReason.TOO_LONG)
    }

    @Test
    fun `no page may reach the device or its network, from a result, a link or a redirect`() {
        for (url in listOf(
            "https://localhost/", "https://127.0.0.1:8080/", "https://192.168.1.1/", "https://10.0.0.5/", "https://172.20.1.1/",
            "https://169.254.169.254/latest", "https://[::1]/", "https://printer.local/", "https://nas/", "https://x.localhost/",
            "http://0.0.0.0/", "https://router.lan/", "https://100.64.0.1/",
        )) {
            blocked(url, BlockReason.LOCAL_HOST)
        }
        assertTrue(decide("https://93.184.216.34/") is NavigationDecision.Allow)
        assertTrue(decide("https://example.org/") is NavigationDecision.Allow)
    }

    @Test
    fun `phone links are confirmed and carry only the number`() {
        assertEquals(NavigationDecision.Confirm(ExternalAction.Dial("+39045123456")), decide("tel:+39045123456"))
        assertEquals(NavigationDecision.Confirm(ExternalAction.Dial("045-123456")), decide("tel:045-123456;phone-context=x"))
        blocked("tel:abc", BlockReason.MALFORMED)
        blocked("tel:12", BlockReason.MALFORMED)
        blocked("tel:*21*123#", BlockReason.MALFORMED)
        blocked("tel:" + "1".repeat(40), BlockReason.MALFORMED)
    }

    @Test
    fun `mail links keep the address and nothing a page could have written`() {
        assertEquals(NavigationDecision.Confirm(ExternalAction.Mail("info@example.org")), decide("mailto:info@example.org?subject=hi&bcc=x@y.zz&body=secret"))
        blocked("mailto:not an address", BlockReason.MALFORMED)
        blocked("mailto:a@b", BlockReason.MALFORMED)
        blocked("mailto:", BlockReason.MALFORMED)
    }

    @Test
    fun `geo links become a destination to confirm, with checked numbers`() {
        assertEquals(NavigationDecision.Confirm(ExternalAction.UseAsDestination(45.44, 10.99)), decide("geo:45.44,10.99?q=Verde"))
        assertEquals(NavigationDecision.Confirm(ExternalAction.UseAsDestination(-33.9, 151.2)), decide("geo:-33.9,151.2,20;u=35"))
        blocked("geo:95,10", BlockReason.MALFORMED)
        blocked("geo:45,200", BlockReason.MALFORMED)
        blocked("geo:NaN,10", BlockReason.MALFORMED)
        blocked("geo:45", BlockReason.MALFORMED)
        assertEquals(
            NavigationDecision.Confirm(ExternalAction.UseAsDestination(45.44, 10.99)),
            decide("geo:0,0?q=45.44,10.99(Verde)&z=1"),
        )
        blocked("geo:0,0?q=Via+Roma+5,Verona", BlockReason.MALFORMED)
        blocked("geo:0,0", BlockReason.MALFORMED)
    }

    @Test
    fun `a redirect loop ends after ten hops`() {
        val guard = RedirectGuard()
        repeat(WebNavigationPolicy.MAX_REDIRECTS) { assertTrue(guard.allowNext()) }
        assertFalse(guard.allowNext())
        guard.reset()
        assertTrue(guard.allowNext())
    }

    @Test
    fun `the scheme is read without regard to case`() {
        assertTrue(decide("HTTPS://EXAMPLE.ORG/") is NavigationDecision.Allow)
        assertTrue(decide("JavaScript:alert(1)") is NavigationDecision.Block)
        assertFalse(WebNavigationPolicy.isLocalHost("example.org"))
    }
}
