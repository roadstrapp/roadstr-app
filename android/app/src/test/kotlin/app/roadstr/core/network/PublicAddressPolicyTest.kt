package app.roadstr.core.network

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as the Flutter client's SSRF address filter test. */
class PublicAddressPolicyTest {
    private fun isPublic(literal: String): Boolean =
        PublicAddressPolicy.isPublic(InetAddress.getByName(literal).address)

    @Test
    fun `ordinary public addresses are accepted`() {
        assertTrue(isPublic("1.1.1.1"))
        assertTrue(isPublic("2606:4700::1111"))
    }

    @Test
    fun `private loopback and reserved IPv4 are refused`() {
        for (ip in listOf(
            "0.0.0.0", "10.0.0.1", "127.0.0.1", "100.64.0.1", "169.254.169.254",
            "172.16.0.1", "172.31.255.255", "192.168.1.1", "192.0.0.1", "192.0.2.1",
            "192.88.99.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1",
            "255.255.255.255",
        )) {
            assertFalse(ip, isPublic(ip))
        }
    }

    @Test
    fun `neighbouring public ranges are not caught by the private ones`() {
        for (ip in listOf("172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1", "198.17.0.1", "198.20.0.1")) {
            assertTrue(ip, isPublic(ip))
        }
    }

    @Test
    fun `IPv6 that carries a private IPv4 or is local is refused`() {
        for (ip in listOf(
            "::1", "::", "fe80::1", "fec0::1", "fd00::1", "fc00::1", "ff02::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254",
            "::ffff:0:127.0.0.1", "::127.0.0.1", "64:ff9b::127.0.0.1",
            "2002:7f00:0001::",
        )) {
            assertFalse(ip, isPublic(ip))
        }
    }

    @Test
    fun `a mapped public IPv4 is still accepted`() {
        assertTrue(isPublic("::ffff:1.1.1.1"))
    }

    @Test
    fun `an address of any other length is refused`() {
        assertFalse(PublicAddressPolicy.isPublic(ByteArray(0)))
        assertFalse(PublicAddressPolicy.isPublic(ByteArray(6)))
    }
}
