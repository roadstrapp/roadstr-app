package app.roadstr.core.protocol.lightning

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class LnurlPayInfo(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadata: String,
    val nostrPubkey: String?,
    val allowsNostr: Boolean,
)

data class LnurlInvoiceRequest(
    val url: String,
    val description: String,
)

/** Deterministic LNURL-pay boundary mirrored by the shipped Dart client. */
object LnurlProtocol {
    const val MAX_ADDRESS_LENGTH = 254
    const val MAX_DECODED_LNURL_BYTES = 2048
    const val MAX_METADATA_LENGTH = 65_536
    const val MAX_METADATA_ENTRIES = 100

    fun resolveMetadataUrl(input: String): String? {
        return try {
            if (input.lowercase().startsWith("lnurl1")) {
                decodeLnurl(input)
            } else {
                val address = input.trim()
                if (address.length > MAX_ADDRESS_LENGTH) return null
                val parts = address.split('@')
                if (parts.size != 2) return null
                val user = parts[0].trim()
                val domain = parts[1].trim()
                val bracketedIpv6 = domain.startsWith('[') && domain.endsWith(']')
                if (
                    user.isEmpty() ||
                    domain.isEmpty() ||
                    '/' in user ||
                    (':' in domain && !bracketedIpv6)
                ) {
                    return null
                }
                val candidate = "https://${domain.lowercase()}/.well-known/lnurlp/${encodePathSegment(user)}"
                canonicalHttpsUrl(candidate)?.takeIf(::isSafeHttpsUrl)
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun parsePayInfo(data: Map<String, Any?>): LnurlPayInfo? {
        return try {
            if (data["status"] == "ERROR") return null
            val callback = (data["callback"] as? String)?.let(::canonicalHttpsUrl) ?: return null
            val min = (data["minSendable"] as? Number)?.dartLongValue() ?: return null
            val max = (data["maxSendable"] as? Number)?.dartLongValue() ?: return null
            val metadata = data["metadata"] as? String ?: return null
            val allowsNostr = data["allowsNostr"] == true
            val rawNostrPubkey = data["nostrPubkey"]
            if (rawNostrPubkey != null && rawNostrPubkey !is String) return null
            val nostrPubkey = rawNostrPubkey as? String
            if (
                !isSafeHttpsUrl(callback) ||
                metadata.length > MAX_METADATA_LENGTH ||
                !isValidMetadata(metadata) ||
                (allowsNostr && (nostrPubkey == null || !isHex32(nostrPubkey))) ||
                min <= 0L ||
                max < min
            ) {
                return null
            }
            LnurlPayInfo(
                callback = callback,
                minSendable = min,
                maxSendable = max,
                metadata = metadata,
                nostrPubkey = if (allowsNostr) nostrPubkey!!.lowercase() else null,
                allowsNostr = allowsNostr,
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    fun buildInvoiceRequest(
        payInfo: LnurlPayInfo,
        amountMillisatoshi: Long,
        zapRequest: Map<String, Any?>?,
    ): LnurlInvoiceRequest? {
        return try {
            if (amountMillisatoshi !in payInfo.minSendable..payInfo.maxSendable) return null
            val callback = canonicalHttpsUrl(payInfo.callback) ?: return null
            if (!isSafeHttpsUrl(callback)) return null
            val uri = URI(callback)
            val query = parseQuery(uri.rawQuery)
            query["amount"] = amountMillisatoshi.toString()
            if (zapRequest != null && payInfo.allowsNostr) {
                query["nostr"] = NostrJson.encode(zapRequest)
            }
            val description = query["nostr"] ?: payInfo.metadata
            LnurlInvoiceRequest(
                url = replaceQuery(uri, query),
                description = description,
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    fun validateInvoiceResponse(
        data: Map<String, Any?>,
        request: LnurlInvoiceRequest,
        amountMillisatoshi: Long,
        nowUnixSeconds: Long,
    ): String? {
        return try {
            if (data["status"] == "ERROR") return null
            val invoice = data["pr"] as? String ?: return null
            val decoded = Bolt11Invoice.parseOrNull(invoice) ?: return null
            invoice.takeIf {
                decoded.amountMillisatoshi == amountMillisatoshi &&
                    !decoded.isExpiredAt(nowUnixSeconds) &&
                    decoded.descriptionMatches(request.description)
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun isSafeHttpsUrl(raw: String): Boolean {
        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (scheme != "https" || host.isNullOrEmpty() || !uri.userInfo.isNullOrEmpty()) {
                return false
            }
            if (host == "localhost" || host.endsWith(".localhost")) return false
            val match = IPV4.matchEntire(host)
            if (match != null) {
                val octets = match.groupValues.drop(1).map(String::toInt)
                if (
                    octets.any { it > 255 } ||
                    octets[0] == 10 ||
                    octets[0] == 127 ||
                    (octets[0] == 169 && octets[1] == 254) ||
                    (octets[0] == 172 && octets[1] in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168)
                ) {
                    return false
                }
            }
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun isValidMetadata(raw: String): Boolean = try {
        val decoded = BoundedJsonParser(raw).parse()
        decoded is List<*> &&
            decoded.isNotEmpty() &&
            decoded.size <= MAX_METADATA_ENTRIES &&
            decoded.all { item ->
                item is List<*> &&
                    item.size == 2 &&
                    item[0] is String &&
                    item[1] is String
            }
    } catch (_: RuntimeException) {
        false
    }

    private fun decodeLnurl(encoded: String): String? {
        val decoded = Bech32.decodeOrNull(encoded.lowercase()) ?: return null
        if (decoded.humanReadablePart != "lnurl") return null
        val byteValues = Bolt11Invoice.convertFiveBitWords(decoded.data) ?: return null
        if (byteValues.size > MAX_DECODED_LNURL_BYTES) return null
        val bytes = ByteArray(byteValues.size) { byteValues[it].toByte() }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        return canonicalHttpsUrl(text)?.takeIf(::isSafeHttpsUrl)
    }

    private fun canonicalHttpsUrl(raw: String): String? {
        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val authority = buildString {
                if (!uri.userInfo.isNullOrEmpty()) append(uri.rawUserInfo).append('@')
                if (':' in host && !host.startsWith('[')) append('[').append(host).append(']') else append(host)
                if (uri.port >= 0) append(':').append(uri.port)
            }
            buildString {
                append(scheme).append("://").append(authority)
                append(normalizeEscapes(uri.rawPath.orEmpty()))
                if (uri.rawQuery != null) append('?').append(normalizeEscapes(uri.rawQuery))
                if (uri.rawFragment != null) append('#').append(normalizeEscapes(uri.rawFragment))
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun parseQuery(raw: String?): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        if (raw.isNullOrEmpty()) return result
        for (part in raw.split('&')) {
            val separator = part.indexOf('=')
            val key = decodeQueryComponent(if (separator < 0) part else part.substring(0, separator))
            val value = decodeQueryComponent(if (separator < 0) "" else part.substring(separator + 1))
            result[key] = value
        }
        return result
    }

    private fun replaceQuery(uri: URI, query: Map<String, String>): String = buildString {
        val base = canonicalHttpsUrl(uri.toString()) ?: error("Invalid callback")
        val fragmentAt = base.indexOf('#')
        val fragment = if (fragmentAt >= 0) base.substring(fragmentAt) else ""
        val withoutFragment = if (fragmentAt >= 0) base.substring(0, fragmentAt) else base
        append(withoutFragment.substringBefore('?'))
        append('?')
        append(
            query.entries.joinToString("&") { (key, value) ->
                val encodedKey = encodeQueryComponent(key)
                if (value.isEmpty()) encodedKey else "$encodedKey=${encodeQueryComponent(value)}"
            },
        )
        append(fragment)
    }

    private fun decodeQueryComponent(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun encodePathSegment(value: String): String =
        percentEncode(value, PATH_SEGMENT_SAFE, spaceAsPlus = false)

    private fun encodeQueryComponent(value: String): String =
        percentEncode(value, QUERY_SAFE, spaceAsPlus = true)

    private fun percentEncode(value: String, safe: Set<Int>, spaceAsPlus: Boolean): String = buildString {
        for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
            val unsigned = byte.toInt() and 0xff
            when {
                unsigned == 0x20 && spaceAsPlus -> append('+')
                unsigned in safe -> append(unsigned.toChar())
                else -> append('%').append(HEX[unsigned ushr 4]).append(HEX[unsigned and 0x0f])
            }
        }
    }

    private fun normalizeEscapes(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (
                character == '%' &&
                index + 2 < value.length &&
                value[index + 1].isHexDigit() &&
                value[index + 2].isHexDigit()
            ) {
                result.append('%')
                result.append(value[index + 1].uppercaseChar())
                result.append(value[index + 2].uppercaseChar())
                index += 3
            } else {
                result.append(character)
                index++
            }
        }
        return result.toString()
    }

    private fun Number.dartLongValue(): Long? = when (this) {
        is Double -> takeIf(Double::isFinite)?.toLong()
        is Float -> takeIf(Float::isFinite)?.toLong()
        else -> toLong()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun isHex32(value: String): Boolean = HEX_32.matches(value)

    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val HEX_32 = Regex("^[0-9a-fA-F]{64}$")
    private const val HEX = "0123456789ABCDEF"
    private val QUERY_SAFE = asciiSet("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
    private val PATH_SEGMENT_SAFE = asciiSet(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,:=@",
    )

    private fun asciiSet(value: String): Set<Int> = value.mapTo(hashSetOf()) { it.code }
}
