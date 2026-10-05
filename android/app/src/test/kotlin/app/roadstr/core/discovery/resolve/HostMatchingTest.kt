package app.roadstr.core.discovery.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMatchingTest {
    @Test
    fun `www and mobile prefixes and case do not matter`() {
        assertEquals("example.com", HostMatching.cleanHost("WWW.Example.COM."))
        assertEquals("example.com", HostMatching.cleanHost("m.example.com"))
        assertTrue(HostMatching.sameSite("www.example.com", "example.com"))
        assertTrue(HostMatching.sameSite("shop.example.com", "example.com"))
    }

    @Test
    fun `country code second level domains keep three labels`() {
        assertEquals("example.co.uk", HostMatching.registrableDomain("shop.example.co.uk"))
        assertEquals("example.com.au", HostMatching.registrableDomain("www.example.com.au"))
        assertFalse(HostMatching.sameSite("one.com.au", "two.com.au"))
        assertEquals("example.it", HostMatching.registrableDomain("menu.example.it"))
    }

    @Test
    fun `an address that is not a name has no site`() {
        assertNull(HostMatching.siteKey("192.168.1.20"))
        assertNull(HostMatching.siteKey("localhost"))
        assertNull(HostMatching.siteKey(""))
        assertNull(HostMatching.siteKey("not a host"))
    }

    @Test
    fun `listing sites never identify a business`() {
        for (host in listOf("www.tripadvisor.it", "tripadvisor.co.uk", "www.facebook.com", "m.facebook.com", "yelp.com", "www.thefork.it")) {
            assertTrue(host, HostMatching.isAggregator(host))
            assertNull(host, HostMatching.siteKey(host))
            assertFalse(host, HostMatching.sameSite(host, host))
        }
    }

    @Test
    fun `site builders identify a business by the whole host`() {
        assertFalse(HostMatching.sameSite("pizza.wixsite.com", "bar.wixsite.com"))
        assertTrue(HostMatching.sameSite("pizza.wixsite.com", "pizza.wixsite.com"))
        assertEquals("pizza.business.site", HostMatching.siteKey("pizza.business.site"))
    }

    @Test
    fun `ordinary business sites match by registrable domain`() {
        assertTrue(HostMatching.sameSite("www.trattoriaverde.it", "menu.trattoriaverde.it"))
        assertFalse(HostMatching.sameSite("trattoriaverde.it", "trattoriarossa.it"))
    }
}
