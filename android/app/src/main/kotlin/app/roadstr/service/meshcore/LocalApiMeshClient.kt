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
 * (branch pr/local-api).
 *
 * One connection at a time, messages handled strictly in arrival order by one consumer. Pairing
 * proves this app's [identity] by signing `meshcore-open-pair-v1:<nonce>` once per connection; the
 * user approves the key in MeshCore Open the first time. After every approved `hello` the client
 * subscribes to `channel.data` of the shared channel again, asking for what was heard since the
 * last frame it processed (`receivedAt`, as the server stamped it).
 *
 * A refused subscription keeps the socket: MeshCore Open pushes a `device` message whenever the
 * radio comes or goes or its channels change, and the client subscribes again on it (or after
 * [notApprovedRetryMillis], for servers that never push one). A missing channel on a connected
 * radio is asked for once per connection with `channels.join`, which MeshCore Open confirms with
 * the user.
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
    private val sendDataType: Int = MeshCoreCompanionProtocol.ROADSTR_SEND_DATA_TYPE,
    private val receiveDataTypes: Set<Int> = MeshCoreCompanionProtocol.ROADSTR_RECEIVE_DATA_TYPES,
    private val http: OkHttpClient = defaultHttp(),
    private val retryBaseMillis: Long = 2_000,
    private val retryMaxMillis: Long = 60_000,
    private val notApprovedRetryMillis: Long = 60_000,
    /** MeshCore Open gives the user two minutes to answer a `channels.join` prompt. */
    private val joinTimeoutMillis: Long = 150_000,
    /** How long a "no" to adding the channel holds before it is asked again. */
    private val channelPromptPauseMillis: Long = 10 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onDatagram: (MeshCoreDatagram) -> Unit,
) : MeshRadioClient {
    private sealed interface Inbound {
        data object Opened : Inbound

        class Text(val text: String) : Inbound

        class Closed(val code: Int) : Inbound

        /** Something this connection scheduled, run by its consumer like any message. */
        class Timer(val action: () -> Unit) : Inbound
    }

    private class Session(val generation: Long, val socket: WebSocket)

    private val lock = Any()
    private val _state = MutableStateFlow<MeshCoreState>(MeshCoreState.Off)
    private var generation = 0L
    private var job: Job? = null
    private var session: Session? = null
    private var nextId = 0L
    private val pending = HashMap<Long, CompletableDeferred<Map<String, Any?>?>>()

    /** When the user last turned down adding the channel; kept across connections, not across launches. */
    private var channelDeniedAt: Long? = null

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

    /** The user changed something: a refused channel prompt may be shown again on the next connection. */
    override fun allowPromptsAgain() {
        synchronized(lock) { channelDeniedAt = null }
    }

    /** The channel is MeshCore Open's to configure; it is resolved by name on every request. */
    override fun configureChannel(): Boolean = false

    override fun send(payload: ByteArray, onResult: (MeshCoreSendResult) -> Unit) {
        if (!ready) return onResult(MeshCoreSendResult.NotReady)
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD) return onResult(MeshCoreSendResult.TooLarge)
        val params = linkedMapOf<String, Any?>(
            "channel" to channelName(),
            "dataType" to sendDataType,
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
        val conversation = Conversation(current, socket, inbox)
        try {
            for (message in inbox) {
                if (!isCurrent(current)) break
                when (message) {
                    Inbound.Opened -> socket.send(conversation.hello())
                    is Inbound.Text -> conversation.handle(message.text)
                    is Inbound.Timer -> message.action()
                    is Inbound.Closed ->
                        return Ended(conversation.authenticated, message.code == CLOSE_DENIED, conversation.refused)
                }
            }
            return Ended(conversation.authenticated, denied = false, refused = conversation.refused)
        } finally {
            conversation.cancelTimers()
            socket.cancel()
            synchronized(lock) { if (session?.socket === socket) session = null }
            failPending()
        }
    }

    /** One connection's handshake and traffic. Used only by the single consumer of its inbox. */
    private inner class Conversation(
        private val current: Long,
        private val socket: WebSocket,
        private val inbox: Channel<Inbound>,
    ) {
        private var signed = false
        private var subscribeId: Long? = null
        private var joinId: Long? = null
        private var subscribed = false

        /** `channels.join` is asked at most once per connection, never in a loop. */
        private var askedToJoin = false
        private var device: MeshCoreDevice? = null
        private var radioConnected = true
        private var retryToken = 0L
        private val timers = mutableListOf<Job>()
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
                "device" -> deviceChanged(message)
                "event" -> event(message)
            }
        }

        fun cancelTimers() {
            timers.forEach { it.cancel() }
            timers.clear()
        }

        /** Runs [action] on this connection's consumer after [millis], unless the connection ends first. */
        private fun after(millis: Long, action: () -> Unit) {
            timers.removeAll { !it.isActive }
            timers += scope.launch {
                delay(millis)
                inbox.trySend(Inbound.Timer(action))
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
            readDevice(message["device"] as? Map<String, Any?>)
            subscribe()
        }

        /** `hello.device` and `device.device` are the same object; `connected` means channels resolve. */
        private fun readDevice(info: Map<String, Any?>?) {
            radioConnected = info?.get("connected") != false
            val name = (info?.get("name") as? String).orEmpty().filter { it.code >= 0x20 }.take(MAX_NAME)
            device = MeshCoreDevice(
                name = name.ifEmpty { device?.name ?: "MeshCore Open" },
                publicKeyPrefix = "",
                firmwareCode = 0,
                version = null,
                model = null,
            )
        }

        /**
         * MeshCore Open says the radio came or went, or its channels changed; it sends this only on a
         * change after `hello`. Gone: say so and keep the socket, the next `device` message brings it
         * back. A subscription survives that on the server, so one already held is never asked for
         * again (that would deliver every frame twice). Without one, the radio coming back or the
         * channel appearing subscribes now instead of waiting for the slow retry.
         */
        private fun deviceChanged(message: Map<String, Any?>) {
            if (!authenticated) return
            @Suppress("UNCHECKED_CAST")
            val info = message["device"] as? Map<String, Any?> ?: return
            val wasConnected = radioConnected
            readDevice(info)
            // An answer on its way decides by itself; nothing to start or stop here meanwhile.
            if (subscribeId != null || joinId != null) return
            if (!radioConnected) return waitFor(MeshCoreProblem.LocalApiNoRadio)
            val known = device
            if (subscribed) {
                if (known != null) setState(current, MeshCoreState.Ready(known, channelIndex = 0, batteryMillivolts = null))
                return
            }
            val channels = (message["channels"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
            if (channels == null || channelName() in channels || !wasConnected) subscribe()
        }

        private fun subscribe() {
            if (subscribeId != null || joinId != null) return
            retryToken++
            val params = linkedMapOf<String, Any?>(
                "events" to listOf("channel.data"),
                "channel" to channelName(),
                "dataTypes" to receiveDataTypes.toList(),
            )
            runCatching { readCursor() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { params["since"] = it }
            val id = synchronized(lock) { ++nextId }
            subscribeId = id
            socket.send(NostrJson.encode(linkedMapOf("id" to id, "method" to "events.subscribe", "params" to params)))
        }

        /**
         * Nothing flows without the subscription: say why, keep the socket for the `device` message
         * that changes it, and subscribe again after a while for servers that never send one.
         */
        private fun waitFor(problem: MeshCoreProblem) {
            setState(current, MeshCoreState.Failed(problem, notApprovedRetryMillis))
            val token = ++retryToken
            after(notApprovedRetryMillis) { if (token == retryToken && !subscribed) subscribe() }
        }

        private fun answer(id: Long, message: Map<String, Any?>) {
            when (id) {
                subscribeId -> subscriptionAnswered(message)
                joinId -> joinAnswered(message)
                else -> synchronized(lock) { pending.remove(id) }?.complete(message)
            }
        }

        private fun subscriptionAnswered(message: Map<String, Any?>) {
            subscribeId = null
            val known = device
            if (message["ok"] == true && known != null) {
                subscribed = true
                setState(current, MeshCoreState.Ready(known, channelIndex = 0, batteryMillivolts = null))
                return
            }
            when {
                errorCode(message) != "unknown_channel" -> {
                    // Not something a radio or a channel fixes: let the user see it, and retry later.
                    refused = MeshCoreProblem.LocalApiRefused
                    setState(current, MeshCoreState.Failed(MeshCoreProblem.LocalApiRefused, notApprovedRetryMillis))
                    socket.close(NORMAL_CLOSURE, "subscription refused")
                }
                // Without a radio MeshCore Open knows no channels at all.
                !radioConnected -> waitFor(MeshCoreProblem.LocalApiNoRadio)
                mayAskToJoin() -> join()
                else -> waitFor(MeshCoreProblem.LocalApiChannelMissing)
            }
        }

        /** Once per connection, a valid hashtag name, and not within the pause after the user said no. */
        private fun mayAskToJoin(): Boolean {
            if (askedToJoin || !HASHTAG_CHANNEL.matches(channelName())) return false
            val deniedAt = synchronized(lock) { channelDeniedAt } ?: return true
            return clock() - deniedAt !in 0 until channelPromptPauseMillis
        }

        /**
         * Asks MeshCore Open to add the hashtag channel. It may ask the user first, which can take two
         * minutes; [MeshCoreState.AwaitingChannel] keeps the link up while its prompt is showing.
         */
        private fun join() {
            askedToJoin = true
            val id = synchronized(lock) { ++nextId }
            joinId = id
            setState(current, MeshCoreState.AwaitingChannel)
            val params = linkedMapOf<String, Any?>("name" to channelName())
            socket.send(NostrJson.encode(linkedMapOf("id" to id, "method" to "channels.join", "params" to params)))
            after(joinTimeoutMillis) { if (joinId == id) joinAnswered(null) }
        }

        private fun joinAnswered(message: Map<String, Any?>?) {
            joinId = null
            val code = message?.let(::errorCode)
            when {
                message?.get("ok") == true -> subscribe()
                code == "denied" -> {
                    // The user said no, or let the prompt run out: not again for a while.
                    synchronized(lock) { channelDeniedAt = clock() }
                    waitFor(MeshCoreProblem.LocalApiChannelMissing)
                }
                code == "not_connected" -> {
                    // The radio went before the channel could be added; it is asked again once it is back.
                    radioConnected = false
                    askedToJoin = false
                    waitFor(MeshCoreProblem.LocalApiNoRadio)
                }
                // Too old to know the method, no free slot, a #roadstr with another key, another prompt
                // open, no answer at all: the user adds the channel in MeshCore Open.
                else -> waitFor(MeshCoreProblem.LocalApiChannelMissing)
            }
        }

        private fun event(message: Map<String, Any?>) {
            @Suppress("UNCHECKED_CAST")
            val event = message["event"] as? Map<String, Any?> ?: return
            if (event["type"] != "channel.data") return
            if (event["channel"] != channelName()) return
            if ((event["dataType"] as? Number)?.toInt() !in receiveDataTypes) return
            val payload = runCatching { Base64.getDecoder().decode(event["payload"] as String) }.getOrNull() ?: return
            // Null for a direct path or our own send; a value outside a 6-bit count is not a hop count.
            val hops = (event["hops"] as? Number)?.toInt()?.takeIf { it in 0..MAX_HOPS }
            val snrDb = (event["snrDb"] as? Number)?.toDouble() ?: 0.0
            onDatagram(MeshCoreDatagram(payload, snrDb = snrDb, flooded = true, hops = hops))
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
        private const val MAX_HOPS = 63

        /** What `channels.join` accepts: MeshCore derives the key from the name, so none is sent. */
        private val HASHTAG_CHANNEL = Regex("^#[a-z0-9-]{1,31}$")

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
