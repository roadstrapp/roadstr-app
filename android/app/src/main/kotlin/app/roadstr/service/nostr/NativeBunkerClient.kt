package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.Nip04Cipher
import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.Nip46
import app.roadstr.core.protocol.nostr.Nip46Response
import app.roadstr.core.protocol.nostr.NostrBunkerUri
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.NostrSchnorr
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** What the signer answered: [result] on success, [error] when it refused or failed, neither on silence. */
class NativeBunkerReply(val result: String?, val error: String?) {
    val ok: Boolean get() = result != null && error == null
}

/**
 * The client side of NIP-46 (Nostr Connect): asks a remote signer to do something over relays and waits for
 * its answer. The signer's key never reaches this phone; the key held here, [clientKeyHex], only identifies
 * this app to the signer and encrypts the conversation.
 *
 * One call obtains a logical connection to each relay, sends the request on all of them, and returns the
 * first valid answer. A persistent connector can keep the physical sockets warm between calls. Only events
 * signed by the signer, addressed to the client and carrying the id of the request are taken for an answer.
 */
class NativeBunkerClient(
    private val connector: NativeRelayConnector,
    private val clientKeyHex: String,
    private val remotePubkeyHex: String,
    private val relays: List<String>,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val newRequestId: () -> String = ::randomRequestId,
    /** The signer wants the person to approve in a browser first: open this address. */
    private val onAuthUrl: (String) -> Unit = {},
    private val diagnostics: (String) -> Unit = {},
) {
    val clientPubkeyHex: String by lazy(LazyThreadSafetyMode.NONE) { NostrSchnorr.publicKey(clientKeyHex) }

    suspend fun call(
        method: String,
        params: List<String>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): NativeBunkerReply {
        val requestId = newRequestId()
        val plain = Nip46.requestJson(requestId, method, params)
        val encrypted = Nip44V2.encrypt(clientKeyHex, remotePubkeyHex, plain)
        val draft = Nip46.requestDraft(clientPubkeyHex, remotePubkeyHex, encrypted, nowSeconds())
        val request = NostrSchnorr.signEvent(draft, clientKeyHex)
        val publishFrame = NostrRelayWire.encode(NostrRelayWire.publish(request))
        val subscription = NativeNostrWire.randomSubscriptionId()
        val filter = linkedMapOf<String, Any?>(
            "kinds" to listOf(Nip46.KIND),
            "authors" to listOf(remotePubkeyHex),
            "#p" to listOf(clientPubkeyHex),
            "since" to nowSeconds() - SINCE_SLACK_SECONDS,
        )
        val subscribeFrame = NostrRelayWire.encode(listOf("REQ", subscription, filter))
        val closeFrame = NostrRelayWire.encode(listOf("CLOSE", subscription))
        val sockets = ArrayList<NativeRelaySocket>()
        val targets = relays.take(NostrBunkerUri.MAX_RELAYS)

        val reply = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<NativeBunkerReply> { continuation ->
                val done = AtomicBoolean(false)
                val ended = java.util.concurrent.atomic.AtomicInteger(0)
                fun finish(value: NativeBunkerReply) {
                    if (done.compareAndSet(false, true) && continuation.isActive) continuation.resume(value)
                }
                for (url in targets) {
                    val host = url.substringAfter("://").substringBefore('/')
                    val socketRef = AtomicReference<NativeRelaySocket?>(null)
                    val opened = AtomicBoolean(false)
                    val sent = AtomicBoolean(false)
                    fun trySend() {
                        val socket = socketRef.get() ?: return
                        if (opened.get() && sent.compareAndSet(false, true)) {
                            socket.send(subscribeFrame)
                            socket.send(publishFrame)
                        }
                    }
                    var seen = 0
                    val events = object : NativeRelayEvents {
                        override fun onOpen() {
                            opened.set(true)
                            trySend()
                        }

                        override fun onMessage(text: String) {
                            if (done.get() || ++seen > MAX_EVENTS_PER_RELAY) return
                            val message = NostrRelayMessageDecoder.decode(text).message
                            if (message !is NostrRelayEventMessage || message.subscriptionId != subscription) return
                            val answer = answerOrNull(message.event, requestId) ?: return
                            answer.authUrl?.let { url ->
                                diagnostics("bunker $host: approval needed in a browser")
                                onAuthUrl(url)
                                return
                            }
                            finish(NativeBunkerReply(answer.result, answer.error))
                        }

                        override fun onEnded() {
                            if (ended.incrementAndGet() >= targets.size) {
                                finish(NativeBunkerReply(null, null))
                            }
                        }
                    }
                    val socket = try {
                        connector.connect(url, events)
                    } catch (error: Exception) {
                        diagnostics("bunker $host: could not connect (${error.javaClass.simpleName})")
                        events.onEnded()
                        continue
                    }
                    socketRef.set(socket)
                    synchronized(sockets) { sockets += socket }
                    trySend()
                }
                continuation.invokeOnCancellation {
                    synchronized(sockets) {
                        sockets.forEach { socket ->
                            socket.send(closeFrame)
                            socket.close()
                        }
                    }
                }
            }
        }
        synchronized(sockets) {
            sockets.forEach { socket ->
                socket.send(closeFrame)
                socket.close()
            }
        }
        return reply ?: NativeBunkerReply(null, null)
    }

    /** The decoded answer if [event] is a valid reply to [requestId] from the signer, else null. */
    private fun answerOrNull(event: Map<String, Any?>, requestId: String): Nip46Response? {
        if (!NativeNostrWire.verify(event)) return null
        if (NativeNostrWire.integral(event["kind"]) != Nip46.KIND.toLong()) return null
        if (event["pubkey"] != remotePubkeyHex) return null
        if (NativeNostrWire.tagValue(event, "p") != clientPubkeyHex) return null
        val content = event["content"] as? String ?: return null
        val plain = runCatching {
            if (content.contains("?iv=")) {
                Nip04Cipher.decrypt(clientKeyHex, remotePubkeyHex, content)
            } else {
                Nip44V2.decrypt(clientKeyHex, remotePubkeyHex, content)
            }
        }.getOrNull() ?: return null
        val response = Nip46.parseResponse(plain) ?: return null
        return response.takeIf { it.id == requestId }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
        const val CONNECT_TIMEOUT_MILLIS = 90_000L
        private const val SINCE_SLACK_SECONDS = 10L
        private const val MAX_EVENTS_PER_RELAY = 32

        fun randomRequestId(): String {
            val bytes = ByteArray(12).also(SecureRandom()::nextBytes)
            return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
    }
}

/** Listens for the signer-initiated response to a `nostrconnect://` offer. */
class NativeNostrConnectReceiver(
    private val connector: NativeRelayConnector,
    private val clientKeyHex: String,
    private val relays: List<String>,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
) {
    private val clientPubkeyHex = NostrSchnorr.publicKey(clientKeyHex)

    init {
        require(relays.any(NostrBunkerUri::isRelay)) { "At least one secure relay is required" }
    }

    suspend fun awaitSigner(
        secret: String,
        timeoutMillis: Long = NativeBunkerClient.CONNECT_TIMEOUT_MILLIS,
        onOfferReady: () -> Unit,
    ): String? {
        val subscription = NativeNostrWire.randomSubscriptionId()
        val filter = linkedMapOf<String, Any?>(
            "kinds" to listOf(Nip46.KIND),
            "#p" to listOf(clientPubkeyHex),
            "since" to nowSeconds() - SINCE_SLACK_SECONDS,
        )
        val subscribeFrame = NostrRelayWire.encode(listOf("REQ", subscription, filter))
        val closeFrame = NostrRelayWire.encode(listOf("CLOSE", subscription))
        val sockets = ArrayList<NativeRelaySocket>()
        val targets = relays.distinct().take(NostrBunkerUri.MAX_RELAYS)
        val remote = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<String?> { continuation ->
                val done = AtomicBoolean(false)
                val ended = java.util.concurrent.atomic.AtomicInteger(0)
                fun finish(value: String?) {
                    if (done.compareAndSet(false, true) && continuation.isActive) continuation.resume(value)
                }
                for (relay in targets) {
                    val socketRef = AtomicReference<NativeRelaySocket?>(null)
                    val opened = AtomicBoolean(false)
                    var seen = 0
                    val events = object : NativeRelayEvents {
                        override fun onOpen() {
                            opened.set(true)
                            socketRef.get()?.send(subscribeFrame)
                        }

                        override fun onMessage(text: String) {
                            if (done.get() || ++seen > MAX_EVENTS_PER_RELAY) return
                            val message = NostrRelayMessageDecoder.decode(text).message
                            if (message !is NostrRelayEventMessage || message.subscriptionId != subscription) return
                            val event = message.event
                            if (!NativeNostrWire.verify(event)) return
                            if (NativeNostrWire.integral(event["kind"]) != Nip46.KIND.toLong()) return
                            if (NativeNostrWire.tagValue(event, "p") != clientPubkeyHex) return
                            val signer = (event["pubkey"] as? String)?.takeIf(NativeNostrWire::isHex32) ?: return
                            val content = event["content"] as? String ?: return
                            val plain = runCatching {
                                if (content.contains("?iv=")) {
                                    Nip04Cipher.decrypt(clientKeyHex, signer, content)
                                } else {
                                    Nip44V2.decrypt(clientKeyHex, signer, content)
                                }
                            }.getOrNull() ?: return
                            val response = Nip46.parseResponse(plain) ?: return
                            if (response.error == null && response.result == secret) finish(signer)
                        }

                        override fun onEnded() {
                            if (ended.incrementAndGet() >= targets.size) finish(null)
                        }
                    }
                    val socket = try {
                        connector.connect(relay, events)
                    } catch (_: Exception) {
                        events.onEnded()
                        continue
                    }
                    socketRef.set(socket)
                    synchronized(sockets) { sockets += socket }
                    if (opened.get()) socket.send(subscribeFrame)
                }
                onOfferReady()
                continuation.invokeOnCancellation {
                    synchronized(sockets) {
                        sockets.forEach { socket ->
                            socket.send(closeFrame)
                            socket.close()
                        }
                    }
                }
            }
        }
        synchronized(sockets) {
            sockets.forEach { socket ->
                socket.send(closeFrame)
                socket.close()
            }
        }
        return remote
    }

    private companion object {
        const val SINCE_SLACK_SECONDS = 10L
        const val MAX_EVENTS_PER_RELAY = 64
    }
}
