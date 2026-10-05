package app.roadstr.core.discovery

import java.net.URI

/** Percent-encoding and URL checks shared by the discovery requests. */
object UrlEncoding {
    private const val HEX = "0123456789ABCDEF"

    /** RFC 3986 unreserved characters stay; everything else is %XX of its UTF-8 bytes. */
    fun encode(value: String): String = buildString {
        for (signed in value.toByteArray(Charsets.UTF_8)) {
            val byte = signed.toInt() and 0xff
            val unreserved = byte in 'a'.code..'z'.code || byte in 'A'.code..'Z'.code ||
                byte in '0'.code..'9'.code || byte.toChar() in "-._~"
            if (unreserved) {
                append(byte.toChar())
            } else {
                append('%').append(HEX[byte ushr 4]).append(HEX[byte and 0x0f])
            }
        }
    }

    /** An `https` URL with a host, no port and no credentials, or null. */
    fun safeHttps(value: String, maxChars: Int = 500): URI? {
        if (value.length > maxChars) return null
        val uri = try {
            URI(value.trim())
        } catch (_: Exception) {
            return null
        }
        val acceptable = uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrEmpty() && uri.port == -1 && uri.userInfo == null
        return uri.takeIf { acceptable }
    }
}
