package app.roadstr.service.meshcore

import app.roadstr.core.protocol.meshcore.MeshCoreCompanionProtocol as Protocol
import app.roadstr.service.nostr.NativeScheduledTask
import app.roadstr.service.nostr.NativeScheduler
import java.util.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The byte pipe to one radio. The only Android-specific part of the MeshCore support, so the
 * protocol logic above it runs, and is tested, without Bluetooth.
 */
interface MeshCoreLink {
    /** Starts connecting; the outcome arrives through [listener], from any thread. */
    fun open(listener: Listener)

    /** Writes one companion frame. False when it could not be handed to the radio. */
    fun write(frame: ByteArray): Boolean

    fun close()

    interface Listener {
        /** Characteristics found and notifications on: commands may be sent. */
        fun onConnected()

        fun onFrame(frame: ByteArray)

        /** [failure] is null for a link the caller closed itself. */
        fun onDisconnected(failure: MeshCoreLinkFailure?)
    }
}

enum class MeshCoreLinkFailure {
    BluetoothOff,
    PermissionMissing,
    DeviceNotFound,
    ConnectionFailed,
    ServiceMissing,
}

/** What the user is told, and what decides whether the client keeps trying by itself. */
enum class MeshCoreProblem(val retries: Boolean) {
    BluetoothOff(false),
    PermissionMissing(false),
    DeviceNotFound(true),
    ConnectionLost(true),
    Timeout(true),
    ServiceMissing(false),

    /** The radio answered "unknown command" to a channel datagram: its firmware predates them. */
    IncompatibleFirmware(false),
    ProtocolError(true),

    /** MeshCore Open's local API is not answering: the app is not running or its API is off. */
    LocalApiUnreachable(true),

    /** MeshCore Open refused this app (closed with 4003): not approved yet, denied or revoked. */
    NotApproved(true),

    /** MeshCore Open runs, but is not connected to a radio, so it has no channels to offer. */
    LocalApiNoRadio(true),

    /** MeshCore Open has no channel with the shared name: the user adds it there. */
    LocalApiChannelMissing(true),

    /** MeshCore Open refused the subscription for another reason. */
    LocalApiRefused(true),
}

data class MeshCoreDevice(
    val name: String,
    val publicKeyPrefix: String,
    val firmwareCode: Int,
    val version: String?,
    val model: String?,
)

sealed interface MeshCoreState {
    /** Not running. */
    data object Off : MeshCoreState

    data object Connecting : MeshCoreState

    data object Handshaking : MeshCoreState

    /** The radio works, but the shared Roadstr channel is not on it yet. */
    data class ChannelMissing(val device: MeshCoreDevice) : MeshCoreState

    data class Ready(
        val device: MeshCoreDevice,
        val channelIndex: Int,
        val batteryMillivolts: Int?,
    ) : MeshCoreState

    data class Failed(val problem: MeshCoreProblem, val retryInMillis: Long?) : MeshCoreState
}

enum class MeshCoreSendResult {
    /** The radio accepted the datagram for transmission. This is not a delivery receipt. */
    Sent,

    /** No usable radio link right now; nothing was queued. */
    NotReady,

    /** The radio's send queue stayed full. */
    Busy,
    TooLarge,

    /** Firmware too old for channel datagrams. */
    Incompatible,

    /** The link dropped before the radio answered: it may or may not have been sent. */
    Lost,
}

class MeshCoreDatagram(
    val payload: ByteArray,
    val snrDb: Double,
    val flooded: Boolean,
)

/**
 * Talks the companion protocol to one radio over a [MeshCoreLink]: handshake, finding the Roadstr
 * channel, one command in flight at a time with a timeout, reconnecting with backoff, draining the
 * radio's queue of received datagrams, and sending ours.
 *
 * Threading: the link calls in from its own threads. Everything runs under one lock, and listener
 * callbacks are invoked after releasing it, so a callback may call back into the client.
 */
class MeshCoreClient(
    private val link: MeshCoreLink,
    private val scheduler: NativeScheduler,
    private val channelName: () -> String,
    private val dataType: Int = Protocol.ROADSTR_DEVELOPMENT_DATA_TYPE,
    private val random: Random = Random(),
    private val onDatagram: (MeshCoreDatagram) -> Unit,
) : MeshRadioClient {
    private class Command(
        val frame: ByteArray,
        /** Whether a received frame is this command's answer. */
        val answers: (Protocol.Inbound) -> Boolean,
        /** Runs under the client's lock with the answer, or null when the radio went away first. */
        val onReply: (Protocol.Inbound?) -> Unit,
    )

    private val lock = Any()
    private val queue = ArrayDeque<Command>()
    private var inFlight: Command? = null
    private var timeoutTask: NativeScheduledTask? = null
    private var reconnectTask: NativeScheduledTask? = null
    private var batteryTask: NativeScheduledTask? = null
    private var generation = 0
    private var wanted = false
    private var linkReady = false
    private var failures = 0
    private var device: MeshCoreDevice? = null
    private var channelIndex: Int? = null
    private var draining = false
    private var drainAgain = false
    private var drainSteps = 0
    private val deferred = ArrayList<() -> Unit>()
    private val _state = MutableStateFlow<MeshCoreState>(MeshCoreState.Off)

    override val state: StateFlow<MeshCoreState> = _state.asStateFlow()

    override val ready: Boolean get() = _state.value is MeshCoreState.Ready

    // ── lifecycle ───────────────────────────────────────────────────────────────────────────────

    /** Connects (or keeps trying to). Safe to call again. */
    override fun start() = locked {
        if (wanted && _state.value !is MeshCoreState.Failed) return@locked
        wanted = true
        connectLocked()
    }

    /** Disconnects and stops retrying. Anything waiting for the radio is told it was lost. */
    override fun stop() = locked {
        wanted = false
        teardownLocked()
        link.close()
        _state.value = MeshCoreState.Off
    }

    private fun connectLocked() {
        reconnectTask?.cancel()
        reconnectTask = null
        val attempt = ++generation
        linkReady = false
        device = null
        channelIndex = null
        _state.value = MeshCoreState.Connecting
        link.open(
            object : MeshCoreLink.Listener {
                override fun onConnected() = locked { if (attempt == generation) handshakeLocked() }

                override fun onFrame(frame: ByteArray) = locked { if (attempt == generation) frameLocked(frame) }

                override fun onDisconnected(failure: MeshCoreLinkFailure?) = locked {
                    if (attempt == generation && wanted) failLocked(problemOf(failure))
                }
            },
        )
    }

    private fun problemOf(failure: MeshCoreLinkFailure?): MeshCoreProblem = when (failure) {
        MeshCoreLinkFailure.BluetoothOff -> MeshCoreProblem.BluetoothOff
        MeshCoreLinkFailure.PermissionMissing -> MeshCoreProblem.PermissionMissing
        MeshCoreLinkFailure.DeviceNotFound -> MeshCoreProblem.DeviceNotFound
        MeshCoreLinkFailure.ServiceMissing -> MeshCoreProblem.ServiceMissing
        MeshCoreLinkFailure.ConnectionFailed, null -> MeshCoreProblem.ConnectionLost
    }

    /** Drops the link and either schedules another attempt or waits for the user, by [problem]. */
    private fun failLocked(problem: MeshCoreProblem) {
        teardownLocked()
        generation++ // anything still arriving from the old link is ignored
        link.close()
        if (!wanted || !problem.retries) {
            _state.value = MeshCoreState.Failed(problem, null)
            if (!problem.retries) wanted = false
            return
        }
        failures++
        val base = minOf(BASE_RECONNECT_MILLIS shl minOf(failures - 1, MAX_BACKOFF_STEPS), MAX_RECONNECT_MILLIS)
        val delay = (base * (0.75 + random.nextDouble() * 0.5)).toLong()
        _state.value = MeshCoreState.Failed(problem, delay)
        reconnectTask = scheduler.schedule(delay) { locked { if (wanted) connectLocked() } }
    }

    /** Cancels every timer and tells everything still waiting for the radio that it is gone. */
    private fun teardownLocked() {
        timeoutTask?.cancel()
        batteryTask?.cancel()
        reconnectTask?.cancel()
        timeoutTask = null
        batteryTask = null
        reconnectTask = null
        val abandoned = ArrayList<Command>()
        inFlight?.let { abandoned += it }
        abandoned += queue
        inFlight = null
        queue.clear()
        draining = false
        drainAgain = false
        linkReady = false
        for (command in abandoned) command.onReply(null)
    }

    // ── handshake ───────────────────────────────────────────────────────────────────────────────

    private fun handshakeLocked() {
        linkReady = true
        _state.value = MeshCoreState.Handshaking
        enqueueLocked(Protocol.appStart(), { it is Protocol.Inbound.SelfInfo }) { reply ->
            val info = reply as? Protocol.Inbound.SelfInfo ?: return@enqueueLocked handshakeFailed(reply)
            queryDeviceLocked(info)
        }
    }

    private fun queryDeviceLocked(info: Protocol.Inbound.SelfInfo) {
        enqueueLocked(Protocol.deviceQuery(), { it is Protocol.Inbound.DeviceInfo }) { reply ->
            val details = reply as? Protocol.Inbound.DeviceInfo ?: return@enqueueLocked handshakeFailed(reply)
            device = MeshCoreDevice(info.name, info.publicKeyPrefix, details.firmwareCode, details.version, details.model)
            val slots = (details.maxChannels ?: Protocol.CHANNEL_SLOTS).coerceIn(1, Protocol.CHANNEL_SLOTS)
            findChannelLocked(0, slots)
        }
    }

    private fun findChannelLocked(slot: Int, slots: Int) {
        if (slot >= slots) {
            _state.value = MeshCoreState.ChannelMissing(device ?: return)
            return
        }
        val wantedName = channelName()
        val answers: (Protocol.Inbound) -> Boolean = {
            (it is Protocol.Inbound.ChannelInfo && it.index == slot) || it is Protocol.Inbound.Error
        }
        enqueueLocked(Protocol.getChannel(slot), answers) { reply ->
            val info = reply as? Protocol.Inbound.ChannelInfo
            when {
                reply == null -> handshakeFailed(null)
                info == null -> findChannelLocked(slot + 1, slots)
                info.name == wantedName -> readyLocked(slot)
                else -> findChannelLocked(slot + 1, slots)
            }
        }
    }

    private fun handshakeFailed(reply: Protocol.Inbound?) {
        if (reply == null) return // the link dropped or timed out: that path reports it
        failLocked(MeshCoreProblem.ProtocolError)
    }

    private fun readyLocked(slot: Int) {
        val found = device ?: return
        channelIndex = slot
        failures = 0
        _state.value = MeshCoreState.Ready(found, slot, batteryMillivolts = null)
        pollBatteryLocked()
        drainLocked() // datagrams that arrived while the app was away
    }

    /**
     * Writes the Roadstr channel into the first empty slot of the radio. Only on the user's say:
     * it changes the radio's configuration. Returns false when the radio has no free slot or is not
     * waiting for it.
     */
    override fun configureChannel(): Boolean = locked {
        val missing = _state.value as? MeshCoreState.ChannelMissing ?: return@locked false
        val name = channelName()
        val secret = runCatching { Protocol.hashtagChannelSecret(name) }.getOrNull() ?: return@locked false
        findEmptySlotLocked(1, missing.device, name, secret)
        true
    }

    private fun findEmptySlotLocked(slot: Int, found: MeshCoreDevice, name: String, secret: ByteArray) {
        if (slot >= Protocol.CHANNEL_SLOTS) {
            failLocked(MeshCoreProblem.ProtocolError)
            return
        }
        val answers: (Protocol.Inbound) -> Boolean = { it is Protocol.Inbound.ChannelInfo || it is Protocol.Inbound.Error }
        enqueueLocked(Protocol.getChannel(slot), answers) { reply ->
            val info = reply as? Protocol.Inbound.ChannelInfo
            val empty = info != null && info.name.isEmpty() && info.secret.all { it == 0.toByte() }
            when {
                reply == null -> Unit
                info == null -> findEmptySlotLocked(slot + 1, found, name, secret)
                !empty -> findEmptySlotLocked(slot + 1, found, name, secret)
                else -> writeChannelLocked(slot, name, secret)
            }
        }
    }

    private fun writeChannelLocked(slot: Int, name: String, secret: ByteArray) {
        val answers: (Protocol.Inbound) -> Boolean = { it is Protocol.Inbound.Ok || it is Protocol.Inbound.Error }
        enqueueLocked(Protocol.setChannel(slot, name, secret), answers) { reply ->
            when (reply) {
                null -> Unit
                is Protocol.Inbound.Ok -> readyLocked(slot)
                else -> failLocked(MeshCoreProblem.ProtocolError)
            }
        }
    }

    // ── commands in flight ─────────────────────────────────────────────────────────────────────

    private fun enqueueLocked(frame: ByteArray, answers: (Protocol.Inbound) -> Boolean, onReply: (Protocol.Inbound?) -> Unit) {
        if (queue.size >= MAX_QUEUED_COMMANDS) {
            onReply(null)
            return
        }
        queue.addLast(Command(frame, answers, onReply))
        pumpLocked()
    }

    private fun pumpLocked() {
        if (inFlight != null || !linkReady) return
        val next = queue.removeFirstOrNull() ?: return
        inFlight = next
        // Armed before the write: an answer may come back before write() returns, and completing
        // the command is what cancels this timer.
        val attempt = generation
        timeoutTask = scheduler.schedule(COMMAND_TIMEOUT_MILLIS) {
            locked { if (attempt == generation && inFlight === next) failLocked(MeshCoreProblem.Timeout) }
        }
        if (!link.write(next.frame)) {
            timeoutTask?.cancel()
            timeoutTask = null
            inFlight = null
            next.onReply(null)
            failLocked(MeshCoreProblem.ConnectionLost)
        }
    }

    private fun completeLocked(reply: Protocol.Inbound) {
        val command = inFlight ?: return
        timeoutTask?.cancel()
        timeoutTask = null
        inFlight = null
        command.onReply(reply)
        pumpLocked()
    }

    // ── incoming frames ─────────────────────────────────────────────────────────────────────────

    private fun frameLocked(frame: ByteArray) {
        val message = Protocol.parse(frame) ?: return
        if (message is Protocol.Inbound.MessagesWaiting) {
            drainLocked()
            return
        }
        val command = inFlight
        if (command != null && command.answers(message)) {
            completeLocked(message)
            return
        }
        // A datagram can also arrive unasked; handle it so none is lost.
        if (message is Protocol.Inbound.ChannelData) deliverLocked(message)
    }

    private fun drainLocked() {
        if (channelIndex == null) return
        if (draining) {
            drainAgain = true
            return
        }
        draining = true
        drainSteps = 0
        nextMessageLocked()
    }

    private fun nextMessageLocked() {
        val answers: (Protocol.Inbound) -> Boolean = {
            it is Protocol.Inbound.ChannelData || it is Protocol.Inbound.NoMoreMessages || it is Protocol.Inbound.Other
        }
        enqueueLocked(Protocol.syncNextMessage(), answers) { reply ->
            if (reply is Protocol.Inbound.ChannelData) deliverLocked(reply)
            val more = reply != null && reply !is Protocol.Inbound.NoMoreMessages && ++drainSteps < MAX_DRAIN_STEPS
            when {
                more -> nextMessageLocked()
                drainAgain && reply != null -> {
                    drainAgain = false
                    drainSteps = 0
                    nextMessageLocked()
                }
                else -> draining = false
            }
        }
    }

    private fun deliverLocked(data: Protocol.Inbound.ChannelData) {
        if (data.dataType != dataType || data.channelIndex != channelIndex) return
        if (data.payload.isEmpty() || data.payload.size > Protocol.MAX_CHANNEL_DATA_PAYLOAD) return
        val datagram = MeshCoreDatagram(data.payload, data.snrDb, data.flooded)
        deferred += { onDatagram(datagram) }
    }

    // ── sending ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Offers [payload] to the radio as a flood datagram on the Roadstr channel. [onResult] is
     * called once, from whatever thread the answer arrives on.
     */
    override fun send(payload: ByteArray, onResult: (MeshCoreSendResult) -> Unit) {
        locked {
            val slot = channelIndex
            val ready = _state.value is MeshCoreState.Ready
            when {
                !ready || slot == null -> deferred += { onResult(MeshCoreSendResult.NotReady) }
                payload.isEmpty() || payload.size > Protocol.MAX_CHANNEL_DATA_PAYLOAD ->
                    deferred += { onResult(MeshCoreSendResult.TooLarge) }
                else -> sendLocked(Protocol.sendChannelData(slot, dataType, payload), attemptsLeft = SEND_ATTEMPTS, onResult)
            }
        }
    }

    private fun sendLocked(frame: ByteArray, attemptsLeft: Int, onResult: (MeshCoreSendResult) -> Unit) {
        val answers: (Protocol.Inbound) -> Boolean = { it is Protocol.Inbound.Ok || it is Protocol.Inbound.Error }
        enqueueLocked(frame, answers) { reply ->
            when {
                reply == null -> deferred += { onResult(MeshCoreSendResult.Lost) }
                reply is Protocol.Inbound.Ok -> deferred += { onResult(MeshCoreSendResult.Sent) }
                else -> sendRefusedLocked(frame, reply as Protocol.Inbound.Error, attemptsLeft, onResult)
            }
        }
    }

    private fun sendRefusedLocked(
        frame: ByteArray,
        error: Protocol.Inbound.Error,
        attemptsLeft: Int,
        onResult: (MeshCoreSendResult) -> Unit,
    ) {
        when (error.code) {
            Protocol.ERR_UNSUPPORTED_COMMAND -> {
                deferred += { onResult(MeshCoreSendResult.Incompatible) }
                failLocked(MeshCoreProblem.IncompatibleFirmware)
            }
            Protocol.ERR_TABLE_FULL -> retrySendLocked(frame, attemptsLeft - 1, onResult)
            Protocol.ERR_ILLEGAL_ARGUMENT -> deferred += { onResult(MeshCoreSendResult.TooLarge) }
            else -> deferred += { onResult(MeshCoreSendResult.NotReady) }
        }
    }

    private fun retrySendLocked(frame: ByteArray, attemptsLeft: Int, onResult: (MeshCoreSendResult) -> Unit) {
        if (attemptsLeft <= 0) {
            deferred += { onResult(MeshCoreSendResult.Busy) }
            return
        }
        val attempt = generation
        scheduler.schedule(RETRY_FULL_QUEUE_MILLIS) {
            locked { if (attempt == generation) sendLocked(frame, attemptsLeft, onResult) }
        }
    }

    // ── battery ─────────────────────────────────────────────────────────────────────────────────

    private fun pollBatteryLocked() {
        val answers: (Protocol.Inbound) -> Boolean = { it is Protocol.Inbound.Battery || it is Protocol.Inbound.Error }
        enqueueLocked(Protocol.getBattery(), answers) { reply ->
            val ready = _state.value as? MeshCoreState.Ready
            if (reply is Protocol.Inbound.Battery && ready != null) {
                _state.value = ready.copy(batteryMillivolts = reply.millivolts)
            }
        }
        val attempt = generation
        batteryTask?.cancel()
        batteryTask = scheduler.schedule(BATTERY_POLL_MILLIS) {
            locked { if (attempt == generation && _state.value is MeshCoreState.Ready) pollBatteryLocked() }
        }
    }

    // ── locking ─────────────────────────────────────────────────────────────────────────────────

    private var depth = 0

    /** Runs [block] under the lock; effects meant for the outside world run once the lock is free. */
    private inline fun <T> locked(block: () -> T): T {
        var run: List<() -> Unit> = emptyList()
        val result: T
        synchronized(lock) {
            depth++
            try {
                result = block()
            } finally {
                depth--
            }
            if (depth == 0 && deferred.isNotEmpty()) {
                run = ArrayList(deferred)
                deferred.clear()
            }
        }
        for (action in run) action()
        return result
    }

    companion object {
        const val COMMAND_TIMEOUT_MILLIS = 5_000L
        const val RETRY_FULL_QUEUE_MILLIS = 2_000L
        const val BATTERY_POLL_MILLIS = 5 * 60_000L
        const val BASE_RECONNECT_MILLIS = 1_250L
        const val MAX_RECONNECT_MILLIS = 60_000L
        private const val MAX_BACKOFF_STEPS = 6
        private const val MAX_QUEUED_COMMANDS = 16
        private const val MAX_DRAIN_STEPS = 64
        private const val SEND_ATTEMPTS = 3
    }
}
