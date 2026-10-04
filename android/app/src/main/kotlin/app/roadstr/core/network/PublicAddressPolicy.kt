package app.roadstr.core.network

/**
 * Whether a resolved IP address may be contacted on behalf of a remote,
 * attacker-supplied URL (a Lightning address, an LNURL, an NWC relay).
 *
 * Checking the host name is not enough: a public name can resolve to
 * 169.254.169.254, to a private range, or to an IPv6 address that wraps one of
 * those. A network adapter must resolve first, then require every returned
 * address to pass this check, and connect to the address it checked.
 *
 * Mirrors ZapService._isPublicAddress in the shipped Flutter client, case for
 * case, so both clients refuse exactly the same destinations.
 */
object PublicAddressPolicy {
    /** [address] is the raw 4-byte IPv4 or 16-byte IPv6 form. */
    fun isPublic(address: ByteArray): Boolean = when (address.size) {
        4 -> isPublicV4(address, 0)
        16 -> isPublicV6(address)
        else -> false
    }

    private fun isPublicV6(b: ByteArray): Boolean {
        // An IPv6 address can carry an IPv4 one: ::ffff:127.0.0.1 and
        // ::127.0.0.1 both reach loopback, and 64:ff9b::/96 does the same
        // through NAT64. Judge those by the embedded IPv4 address.
        val firstEightZero = (0 until 8).all { b[it] == ZERO }
        val firstTenZero = firstEightZero && b[8] == ZERO && b[9] == ZERO
        val v4Mapped = firstTenZero && b[10] == FF && b[11] == FF
        val v4Translated = firstEightZero && b[8] == FF && b[9] == FF &&
            b[10] == ZERO && b[11] == ZERO
        val v4Compatible = (0 until 12).all { b[it] == ZERO }
        val nat64 = b[0] == ZERO && b[1] == 0x64.toByte() && b[2] == FF && b[3] == 0x9b.toByte() &&
            (4 until 12).all { b[it] == ZERO }
        if (v4Mapped || v4Translated || v4Compatible || nat64) return isPublicV4(b, 12)

        // 6to4 carries its IPv4 gateway immediately after 2002::/16. A local
        // or link-local embedded gateway is not a public destination.
        val sixToFour = b[0] == 0x20.toByte() && b[1] == 0x02.toByte()
        if (sixToFour && !isPublicV4(b, 2)) return false

        val unspecifiedOrLoopback = (0 until 15).all { b[it] == ZERO } &&
            (b[15] == ZERO || b[15] == 1.toByte())
        val linkLocal = b[0] == 0xfe.toByte() && (b[1].toInt() and 0xc0) == 0x80
        val siteLocal = b[0] == 0xfe.toByte() && (b[1].toInt() and 0xc0) == 0xc0
        val uniqueLocal = (b[0].toInt() and 0xfe) == 0xfc
        val multicast = b[0] == FF
        return !(unspecifiedOrLoopback || linkLocal || siteLocal || uniqueLocal || multicast)
    }

    private fun isPublicV4(b: ByteArray, offset: Int): Boolean {
        val first = b[offset].toInt() and 0xff
        val second = b[offset + 1].toInt() and 0xff
        return !(
            first == 0 ||
                first == 10 ||
                first == 127 ||
                (first == 100 && second in 64..127) ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 192 && second == 0 && b[offset + 2].toInt() == 0) ||
                (first == 192 && second == 0 && b[offset + 2].toInt() == 2) ||
                (first == 192 && second == 88 && b[offset + 2].toInt() == 99) ||
                (first == 198 && (second == 18 || second == 19)) ||
                (first == 198 && second == 51 && b[offset + 2].toInt() == 100) ||
                (first == 203 && second == 0 && b[offset + 2].toInt() == 113) ||
                first >= 224
            )
    }

    private const val ZERO: Byte = 0
    private val FF: Byte = 0xff.toByte()
}
