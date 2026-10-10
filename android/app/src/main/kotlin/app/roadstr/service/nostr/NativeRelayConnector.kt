package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
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
 * Keeps one physical WebSocket per relay and hands callers cheap logical
 * sockets. Closing a logical socket only removes its listener; the underlying
 * connection remains warm for the next NIP-46 request.
 *
 * Relay frames are broadcast to logical listeners. Each service already
 * filters by its random subscription id, so multiplexing avoids reconnect/TLS
 * work without weakening message validation.
 */
class NativePersistentRelayConnector(
    private val delegate: NativeRelayConnector,
) : NativeRelayConnector, AutoCloseable {
    private class SharedConnection {
        val listeners = LinkedHashSet<NativeRelayEvents>()
        var socket: NativeRelaySocket? = null
        var open = false
        var ended = false
    }

    private val lock = Any()
    private val connections = HashMap<String, SharedConnection>()

    override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
        var openImmediately = false
        val shared: SharedConnection
        var create = false
        synchronized(lock) {
            val existing = connections[url]?.takeUnless { it.ended }
            shared = existing ?: SharedConnection().also {
                connections[url] = it
                create = true
            }
            shared.listeners += events
            openImmediately = shared.open
        }
        if (create) {
            val physical = try {
                delegate.connect(url, object : NativeRelayEvents {
                    override fun onOpen() {
                        val listeners = synchronized(lock) {
                            if (shared.ended) return
                            shared.open = true
                            shared.listeners.toList()
                        }
                        listeners.forEach(NativeRelayEvents::onOpen)
                    }

                    override fun onMessage(text: String) {
                        val listeners = synchronized(lock) {
                            if (shared.ended) return
                            shared.listeners.toList()
                        }
                        listeners.forEach { it.onMessage(text) }
                    }

                    override fun onEnded() {
                        val listeners = synchronized(lock) {
                            if (shared.ended) return
                            shared.ended = true
                            shared.open = false
                            if (connections[url] === shared) connections.remove(url)
                            shared.listeners.toList().also { shared.listeners.clear() }
                        }
                        listeners.forEach(NativeRelayEvents::onEnded)
                    }
                })
            } catch (error: Exception) {
                synchronized(lock) {
                    shared.ended = true
                    shared.listeners.remove(events)
                    if (connections[url] === shared) connections.remove(url)
                }
                throw error
            }
            synchronized(lock) {
                if (shared.ended) physical.close() else shared.socket = physical
            }
        }
        if (openImmediately) events.onOpen()
        return object : NativeRelaySocket {
            override fun send(text: String): Boolean {
                val socket = synchronized(lock) {
                    if (shared.ended || events !in shared.listeners) null else shared.socket
                }
                return socket?.send(text) == true
            }

            override fun close() {
                synchronized(lock) { shared.listeners.remove(events) }
            }
        }
    }

    override fun close() {
        val sockets = synchronized(lock) {
            connections.values.onEach {
                it.ended = true
                it.open = false
                it.listeners.clear()
            }.mapNotNull(SharedConnection::socket).also { connections.clear() }
        }
        sockets.forEach(NativeRelaySocket::close)
    }
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

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.length > NostrRelayMessageDecoder.MAX_FRAME_UTF16_CODE_UNITS) {
                        // Reject at the transport boundary: keeping a hostile
                        // socket open would let it allocate and deliver
                        // oversized frames forever even though the decoder
                        // correctly ignores each one.
                        webSocket.cancel()
                        end()
                        return
                    }
                    events.onMessage(text)
                }

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
