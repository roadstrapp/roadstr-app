package app.roadstr.service.nostr

import app.roadstr.core.network.PublicAddressPolicy
import app.roadstr.core.protocol.lightning.LnurlProtocol
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** HTTPS GET of a JSON object from a server named by a Lightning address. */
fun interface NativeLnurlHttp {
    /** The decoded object, or null for any failure, refusal or oversized answer. */
    suspend fun getJson(url: String, timeoutMillis: Long): Map<String, Any?>?
}

/**
 * LNURL servers are named by strangers (a Lightning address in somebody's
 * profile), so the request is not allowed to reach into the phone's own
 * network: the name is resolved once, every address has to be public, and the
 * connection goes to exactly those addresses. Redirects, proxies and
 * plaintext are off, and the answer is capped at 1 MiB.
 */
class OkHttpLnurlHttp(
    private val client: OkHttpClient = defaultClient(),
) : NativeLnurlHttp {
    override suspend fun getJson(url: String, timeoutMillis: Long): Map<String, Any?>? {
        if (!LnurlProtocol.isSafeHttpsUrl(url)) return null
        val request = runCatching {
            Request.Builder()
                .url(url)
                .header("User-Agent", "Roadstr/1.0")
                .header("Accept", "application/json")
                .build()
        }.getOrNull() ?: return null
        val call = client.newBuilder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
        return runCatching {
            runInterruptible(Dispatchers.IO) { call.execute().use(::readObject) }
        }.getOrNull()
    }

    private fun readObject(response: Response): Map<String, Any?>? {
        if (response.code != 200) return null
        val body = response.body ?: return null
        if (body.contentLength() > MAX_BYTES) return null
        val source = body.source()
        source.request(MAX_BYTES + 1)
        if (source.buffer.size > MAX_BYTES) return null
        val parsed = BoundedJsonParser(source.buffer.readUtf8()).parse() as? Map<*, *> ?: return null
        return parsed.entries.associate { (key, value) -> key.toString() to value }
    }

    private object PublicOnlyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || !addresses.all { PublicAddressPolicy.isPublic(it.address) }) {
                throw UnknownHostException("not a public address")
            }
            return addresses
        }
    }

    companion object {
        private const val MAX_BYTES = 1024L * 1024L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dns(PublicOnlyDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(4, TimeUnit.SECONDS)
            .build()
    }
}
