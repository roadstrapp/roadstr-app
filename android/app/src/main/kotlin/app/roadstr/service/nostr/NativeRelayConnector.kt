package app.roadstr.service.nostr

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** One open (or opening) relay connection. */
interface NativeRelaySocket {
    /** Queues [text]; false when the socket is closed. */
    fun send(text: String): Boolean

    fun close()
}

/** Everything the services need to hear about a socket. */
interface NativeRelayEvents {
    fun onOpen()
    fun onMessage(text: String)

    /** The connection ended or never opened, for any reason. Called at most once. */
    fun onEnded()
}

fun interface NativeRelayConnector {
    /** Starts connecting to [url] and returns at once; results arrive on [events]. */
    fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket
}

/**
 * OkHttp WebSocket adapter. Plaintext `ws://` is refused outright: a relay
 * connection carries signed reports, the geohash cell the driver is in and
 * encrypted favourites, none of which belong on a clear channel.
 */
class OkHttpRelayConnector(
    private val client: OkHttpClient = defaultClient(),
) : NativeRelayConnector {
    override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
        require(url.startsWith("wss://")) { "Relays must use wss://" }
        val ended = java.util.concurrent.atomic.AtomicBoolean(false)
        fun end() {
            if (ended.compareAndSet(false, true)) events.onEnded()
        }
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = events.onOpen()

                override fun onMessage(webSocket: WebSocket, text: String) = events.onMessage(text)

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    end()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = end()

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = end()
            },
        )
        return object : NativeRelaySocket {
            override fun send(text: String): Boolean = socket.send(text)

            // Abort rather than negotiate a close: this also works for a socket
            // that never finished opening, and the peer is untrusted anyway.
            override fun close() {
                socket.cancel()
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // A relay legitimately stays quiet for minutes; the ping below is
            // what notices a dead connection.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
