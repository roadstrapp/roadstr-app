package app.roadstr.service.meshcore

import app.roadstr.core.protocol.meshcore.MeshCoreCompanionProtocol
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The radio, through MeshCore Open's local API on this phone instead of this app's own BLE link,
 * so the chat app and Roadstr share one radio. Protocol: LOCAL_API.md in jooray/meshcore-open
 * (branch feat/local-api).
 *
 * One connection at a time, messages handled strictly in arrival order by one consumer. Pairing
 * proves this app's [identity] by signing `meshcore-open-pair-v1:<nonce>` once per connection; the
 * user approves the key in MeshCore Open the first time. After every approved `hello` the client
 * subscribes to `channel.data` of the shared channel again, asking for what was heard since the
 * last frame it processed (`receivedAt`, as the server stamped it).
 */
class LocalApiMeshClient(
    private val scope: CoroutineScope,
    private val channelName: () -> String,
    private val identity: LocalApiIdentity,
    /** The `receivedAt` of the last frame processed, kept between launches. */
    private val readCursor: () -> String?,
    private val writeCursor: (String) -> Unit,
    private val clientName: String = "Roadstr",
    private val clientVersion: String = "",
    private val url: String = DEFAULT_URL,
    private val dataType: Int = MeshCoreCompanionProtocol.ROADSTR_DEVELOPMENT_DATA_TYPE,
    private val http: OkHttpClient = defaultHttp(),
    private val retryBaseMillis: Long = 2_000,
    private val retryMaxMillis: Long = 60_000,
    private val notApprovedRetryMillis: Long = 60_000,
    private val onDatagram: (MeshCoreDatagram) -> Unit,
) : MeshRadioClient {
    private sealed interface Inbound {
        data object Opened : Inbound

        class Text(val text: String) : Inbound

        class Closed(val code: Int) : Inbound
    }

    private class Session(val generation: Long, val socket: WebSocket)

    private val lock = Any()
    private val _state = MutableStateFlow<MeshCoreState>(MeshCoreState.Off)
    private var generation = 0L
    private var job: Job? = null
    private var session: Session? = null
    private var nextId = 0L
    private val pending = HashMap<Long, CompletableDeferred<Map<String, Any?>?>>()

    override val state: StateFlow<MeshCoreState> = _state.asStateFlow()

    override val ready: Boolean get() = _state.value is MeshCoreState.Ready

    override fun start() {
        synchronized(lock) {
            if (job?.isActive == true) return
            val current = ++generation
            job = scope.launch { run(current) }
        }
    }

    /** Disconnects and stops retrying; whatever was waiting for an answer is told it was lost. */
    override fun stop() {
        val (stopped, socket) = synchronized(lock) {
            generation++
            val stopped = job
            job = null
            val socket = session?.socket
            session = null
            stopped to socket
        }
        socket?.close(NORMAL_CLOSURE, "stopping")
        stopped?.cancel()
        failPending()
        _state.value = MeshCoreState.Off
    }

    /** The channel is MeshCore Open's to configure; it is resolved by name on every request. */
    override fun configureChannel(): Boolean = false

    override fun send(payload: ByteArray, onResult: (MeshCoreSendResult) -> Unit) {
        if (!ready) return onResult(MeshCoreSendResult.NotReady)
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD) return onResult(MeshCoreSendResult.TooLarge)
        val params = linkedMapOf<String, Any?>(
            "channel" to channelName(),
            "dataType" to dataType,
            "payload" to Base64.getEncoder().encodeToString(payload),
        )
        scope.launch {
            val reply = request("channel.sendData", params)
            onResult(
                when {
                    reply == null -> MeshCoreSendResult.Lost
                    reply["ok"] == true -> MeshCoreSendResult.Sent
                    errorCode(reply) == "payload_too_large" -> MeshCoreSendResult.TooLarge
                    errorCode(reply) == "rate_limited" -> MeshCoreSendResult.Busy
                    else -> MeshCoreSendResult.NotReady
                },
            )
        }
    }

    // ── connection ──────────────────────────────────────────────────────────────────────────────

    private fun isCurrent(current: Long) = synchronized(lock) { generation == current }

    /** Connects, and reconnects with backoff, until [stop]. Cancellation ends it quietly. */
    private suspend fun run(current: Long) {
        var failures = 0
        while (isCurrent(current)) {
            val ended = try {
                connectOnce(current)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A bad key store, a malformed URL: the same as an unreachable server, retried.
                Ended(authenticated = false, denied = false, refused = null)
            }
            if (!isCurrent(current)) return
            // Only an approved hello proves the other side works; opening a socket proves nothing.
            failures = if (ended.authenticated) 0 else failures + 1
            // A refusal is the user's to fix in MeshCore Open; asking again at once only spins.
            val wait = if (ended.denied || ended.refused != null) {
                notApprovedRetryMillis
            } else {
                (retryBaseMillis shl (failures - 1).coerceIn(0, 5)).coerceAtMost(retryMaxMillis)
            }
            val problem = when {
                ended.refused != null -> ended.refused
                ended.denied -> MeshCoreProblem.NotApproved
                ended.authenticated -> MeshCoreProblem.ConnectionLost
                else -> MeshCoreProblem.LocalApiUnreachable
            }
            setState(current, MeshCoreState.Failed(problem, wait))
            delay(wait)
        }
    }

    /** How a connection ended; [refused] is why MeshCore Open turned the subscription down. */
    private class Ended(val authenticated: Boolean, val denied: Boolean, val refused: MeshCoreProblem? = null)

    private suspend fun connectOnce(current: Long): Ended {
        val inbox = Channel<Inbound>(INBOX_CAPACITY)
        val socket = http.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    inbox.trySend(Inbound.Opened)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // A consumer this far behind is not keeping up: drop the connection, not memory.
                    if (inbox.trySend(Inbound.Text(text)).isFailure) webSocket.cancel()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    inbox.trySend(Inbound.Closed(code))
                    webSocket.close(NORMAL_CLOSURE, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    inbox.trySend(Inbound.Closed(code))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    inbox.trySend(Inbound.Closed(ABNORMAL_CLOSURE))
                }
            },
        )
        val accepted = synchronized(lock) {
            if (generation != current) return@synchronized false
            session = Session(current, socket)
            true
        }
        if (!accepted) {
            socket.cancel()
            return Ended(authenticated = false, denied = false)
        }
        setState(current, MeshCoreState.Connecting)
        val conversation = Conversation(current, socket)
        try {
            for (message in inbox) {
                if (!isCurrent(current)) break
                when (message) {
                    Inbound.Opened -> socket.send(conversation.hello())
                    is Inbound.Text -> conversation.handle(message.text)
                    is Inbound.Closed ->
                        return Ended(conversation.authenticated, message.code == CLOSE_DENIED, conversation.refused)
                }
            }
            return Ended(conversation.authenticated, denied = false, refused = conversation.refused)
        } finally {
            socket.cancel()
            synchronized(lock) { if (session?.socket === socket) session = null }
            failPending()
        }
    }

    /** One connection's handshake and traffic. Used only by the single consumer of its inbox. */
    private inner class Conversation(private val current: Long, private val socket: WebSocket) {
        private var signed = false
        private var subscribeId: Long? = null
        private var device: MeshCoreDevice? = null
        private var radioConnected = true
        var authenticated = false
            private set
        var refused: MeshCoreProblem? = null
            private set

        fun hello(): String = NostrJson.encode(
            linkedMapOf(
                "type" to "hello",
                "client" to linkedMapOf(
                    "name" to clientName,
                    "version" to clientVersion,
                    "publicKey" to Base64.getEncoder().encodeToString(identity.publicKey()),
                ),
            ),
        )

        fun handle(text: String) {
            @Suppress("UNCHECKED_CAST")
            val message = runCatching { BoundedJsonParser(text).parse() as? Map<String, Any?> }.getOrNull() ?: return
            val id = (message["id"] as? Number)?.toLong()
            if (id != null) return answer(id, message)
            when (message["type"]) {
                "pairing.challenge" -> challenge(message)
                "hello" -> approved(message)
                "event" -> event(message)
            }
        }

        /**
         * Signs the nonce, at most once per connection. The signed text is built here; a `message`
         * the server suggests is never signed, so the key cannot be made to sign anything else.
         */
        private fun challenge(message: Map<String, Any?>) {
            if (signed || authenticated) return
            val nonce = (message["nonce"] as? String)?.takeIf { it.length in 1..MAX_NONCE } ?: return
            signed = true
            setState(current, MeshCoreState.Handshaking)
            val signature = identity.sign((PAIRING_PREFIX + nonce).toByteArray(Charsets.UTF_8))
            socket.send(
                NostrJson.encode(
                    linkedMapOf("type" to "pair", "signature" to Base64.getEncoder().encodeToString(signature)),
                ),
            )
        }

        private fun approved(message: Map<String, Any?>) {
            if (!signed || authenticated) return
            authenticated = true
            @Suppress("UNCHECKED_CAST")
            val info = message["device"] as? Map<String, Any?>
            radioConnected = info?.get("connected") != false
            val name = (info?.get("name") as? String).orEmpty().filter { it.code >= 0x20 }.take(MAX_NAME)
            device = MeshCoreDevice(
                name = name.ifEmpty { "MeshCore Open" },
                publicKeyPrefix = "",
                firmwareCode = 0,
                version = null,
                model = null,
            )
            val params = linkedMapOf<String, Any?>(
                "events" to listOf("channel.data"),
                "channel" to channelName(),
                "dataTypes" to listOf(dataType),
            )
            runCatching { readCursor() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { params["since"] = it }
            val id = synchronized(lock) { ++nextId }
            subscribeId = id
            socket.send(NostrJson.encode(linkedMapOf("id" to id, "method" to "events.subscribe", "params" to params)))
        }

        private fun answer(id: Long, message: Map<String, Any?>) {
            if (id == subscribeId) {
                subscribeId = null
                val known = device
                if (message["ok"] == true && known != null) {
                    setState(current, MeshCoreState.Ready(known, channelIndex = 0, batteryMillivolts = null))
                } else {
                    // Nothing flows without the subscription: say why, and let the user fix it there.
                    val problem = when {
                        errorCode(message) != "unknown_channel" -> MeshCoreProblem.LocalApiRefused
                        // Without a radio MeshCore Open knows no channels at all.
                        !radioConnected -> MeshCoreProblem.LocalApiNoRadio
                        else -> MeshCoreProblem.LocalApiChannelMissing
                    }
                    refused = problem
                    setState(current, MeshCoreState.Failed(problem, notApprovedRetryMillis))
                    socket.close(NORMAL_CLOSURE, "subscription refused")
                }
                return
            }
            synchronized(lock) { pending.remove(id) }?.complete(message)
        }

        private fun event(message: Map<String, Any?>) {
            @Suppress("UNCHECKED_CAST")
            val event = message["event"] as? Map<String, Any?> ?: return
            if (event["type"] != "channel.data") return
            if (event["channel"] != channelName()) return
            if ((event["dataType"] as? Number)?.toInt() != dataType) return
            val payload = runCatching { Base64.getDecoder().decode(event["payload"] as String) }.getOrNull() ?: return
            onDatagram(MeshCoreDatagram(payload, snrDb = (event["snrDb"] as? Number)?.toDouble() ?: 0.0, flooded = true))
            // `since` is inclusive: the last frame comes back once more and the bridge drops it.
            (event["receivedAt"] as? String)?.takeIf { it.length <= MAX_TIMESTAMP }?.let { runCatching { writeCursor(it) } }
        }
    }

    private suspend fun request(method: String, params: Map<String, Any?>): Map<String, Any?>? {
        val reply = CompletableDeferred<Map<String, Any?>?>()
        val (socket, id) = synchronized(lock) {
            val socket = session?.socket ?: return null
            val id = ++nextId
            pending[id] = reply
            socket to id
        }
        val sent = socket.send(NostrJson.encode(linkedMapOf("id" to id, "method" to method, "params" to params)))
        if (!sent) reply.complete(null)
        return try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { reply.await() }
        } finally {
            synchronized(lock) { pending.remove(id) }
        }
    }

    private fun failPending() {
        val waiting = synchronized(lock) { pending.values.toList().also { pending.clear() } }
        waiting.forEach { it.complete(null) }
    }

    /** Only the connection that is still current may change what the screen sees. */
    private fun setState(current: Long, state: MeshCoreState) {
        synchronized(lock) { if (generation == current) _state.value = state }
    }

    private fun errorCode(reply: Map<String, Any?>): String? = (reply["error"] as? Map<*, *>)?.get("code") as? String

    companion object {
        const val DEFAULT_URL = "ws://127.0.0.1:8787/api/v1"

        /** What MeshCore Open accepts in one `channel.sendData`: the BLE companion bound. */
        const val MAX_PAYLOAD = 163
        const val PAIRING_PREFIX = "meshcore-open-pair-v1:"

        /** MeshCore Open closes with this code when the user denied or revoked the app. */
        const val CLOSE_DENIED = 4003

        private const val NORMAL_CLOSURE = 1000
        private const val ABNORMAL_CLOSURE = 1006
        private const val INBOX_CAPACITY = 512
        private const val REQUEST_TIMEOUT_MILLIS = 15_000L
        private const val MAX_NONCE = 128
        private const val MAX_NAME = 64
        private const val MAX_TIMESTAMP = 64

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
