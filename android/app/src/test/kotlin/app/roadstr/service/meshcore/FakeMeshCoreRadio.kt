package app.roadstr.service.meshcore

import app.roadstr.core.protocol.meshcore.MeshCoreCompanionProtocol as P

/**
 * The radio side of the companion protocol, written from docs/companion_protocol.md, so the client
 * is exercised against the documented frames rather than against its own idea of them.
 * It is NOT a real radio: nothing here proves behaviour on hardware.
 */
class FakeMeshCoreRadio(
    var maxChannels: Int = 8,
    var firmwareCode: Int = 13,
    var version: String = "v1.17.1",
    var batteryMillivolts: Int = 4_012,
) : MeshCoreLink {
    enum class SendMode { Accept, UnsupportedCommand, TableFull, Silent, IllegalArgument }

    class Slot(var name: String = "", var secret: ByteArray = ByteArray(16))

    val slots = MutableList(8) { Slot() }
    val written = mutableListOf<ByteArray>()
    val sentDatagrams = mutableListOf<ByteArray>()
    var sendMode = SendMode.Accept
    var tableFullTimes = 0
    var openFailure: MeshCoreLinkFailure? = null
    var openCount = 0
    var closeCount = 0
    var refuseWrites = false
    private var listener: MeshCoreLink.Listener? = null
    private val outbox = ArrayDeque<ByteArray>()
    var answerNothingTo: Set<Int> = emptySet()

    override fun open(listener: MeshCoreLink.Listener) {
        openCount++
        this.listener = listener
        val failure = openFailure
        if (failure != null) listener.onDisconnected(failure) else listener.onConnected()
    }

    override fun write(frame: ByteArray): Boolean {
        if (refuseWrites) return false
        written += frame
        val command = frame[0].toInt() and 0xFF
        if (command in answerNothingTo) return true
        answer(command, frame)
        return true
    }

    override fun close() {
        closeCount++
    }

    private fun emit(frame: ByteArray) = listener?.onFrame(frame)

    private fun answer(command: Int, frame: ByteArray) {
        when (command) {
            P.CMD_APP_START -> emit(selfInfo())
            P.CMD_DEVICE_QUERY -> emit(deviceInfo())
            P.CMD_GET_CHANNEL -> channelInfo(frame[1].toInt() and 0xFF)
            P.CMD_SET_CHANNEL -> setChannel(frame)
            P.CMD_SEND_CHANNEL_DATA -> sendData(frame)
            P.CMD_SYNC_NEXT_MESSAGE -> emit(outbox.removeFirstOrNull() ?: byteArrayOf(P.PACKET_NO_MORE_MESSAGES.toByte()))
            P.CMD_GET_BATTERY -> emit(byteArrayOf(P.PACKET_BATTERY.toByte(), (batteryMillivolts and 0xFF).toByte(), (batteryMillivolts ushr 8).toByte()) + ByteArray(8))
            else -> emit(byteArrayOf(P.PACKET_ERROR.toByte(), P.ERR_UNSUPPORTED_COMMAND.toByte()))
        }
    }

    private fun channelInfo(index: Int) {
        if (index >= maxChannels) {
            emit(byteArrayOf(P.PACKET_ERROR.toByte(), P.ERR_NOT_FOUND.toByte()))
            return
        }
        val slot = slots[index]
        emit(byteArrayOf(P.PACKET_CHANNEL_INFO.toByte(), index.toByte()) + slot.name.toByteArray().copyOf(32) + slot.secret)
    }

    private fun setChannel(frame: ByteArray) {
        val index = frame[1].toInt() and 0xFF
        val name = String(frame, 2, 32, Charsets.UTF_8).trimEnd('\u0000')
        slots[index].name = name
        slots[index].secret = frame.copyOfRange(34, 50)
        emit(byteArrayOf(P.PACKET_OK.toByte()))
    }

    private fun sendData(frame: ByteArray) {
        when (sendMode) {
            SendMode.Silent -> Unit
            SendMode.UnsupportedCommand -> emit(byteArrayOf(P.PACKET_ERROR.toByte(), P.ERR_UNSUPPORTED_COMMAND.toByte()))
            SendMode.IllegalArgument -> emit(byteArrayOf(P.PACKET_ERROR.toByte(), P.ERR_ILLEGAL_ARGUMENT.toByte()))
            SendMode.TableFull -> {
                if (tableFullTimes-- > 0) {
                    emit(byteArrayOf(P.PACKET_ERROR.toByte(), P.ERR_TABLE_FULL.toByte()))
                } else {
                    sentDatagrams += frame
                    emit(byteArrayOf(P.PACKET_OK.toByte()))
                }
            }
            SendMode.Accept -> {
                sentDatagrams += frame
                emit(byteArrayOf(P.PACKET_OK.toByte()))
            }
        }
    }

    // ── what the radio tells the app ───────────────────────────────────────────────────────────

    fun selfInfo(name: String = "Test node"): ByteArray {
        val frame = ByteArray(58)
        frame[0] = P.PACKET_SELF_INFO.toByte()
        for (i in 0 until 32) frame[4 + i] = (0xA0 + i % 16).toByte()
        return frame + name.toByteArray()
    }

    fun deviceInfo(): ByteArray {
        val frame = ByteArray(82)
        frame[0] = P.PACKET_DEVICE_INFO.toByte()
        frame[1] = firmwareCode.toByte()
        frame[2] = 50
        frame[3] = maxChannels.toByte()
        "Heltec V3".toByteArray().copyInto(frame, 20)
        version.toByteArray().copyInto(frame, 60)
        return frame
    }

    /** Queues a received datagram for the app and sends the "messages waiting" push. */
    fun receive(payload: ByteArray, dataType: Int = P.ROADSTR_DEVELOPMENT_DATA_TYPE, channel: Int = 1, snrQuarterDb: Int = 22, pathLength: Int = 2) {
        outbox += channelData(payload, dataType, channel, snrQuarterDb, pathLength)
        emit(byteArrayOf(P.PACKET_MESSAGES_WAITING.toByte()))
    }

    /** A text message in the radio's queue, which the app must read past without choking. */
    fun receiveText() {
        outbox += byteArrayOf(0x08, 1, 0, 0, 0, 0, 0, 0, 'h'.code.toByte(), 'i'.code.toByte())
        emit(byteArrayOf(P.PACKET_MESSAGES_WAITING.toByte()))
    }

    fun deliverUnsolicited(frame: ByteArray) = emit(frame)

    fun channelData(payload: ByteArray, dataType: Int, channel: Int, snrQuarterDb: Int, pathLength: Int): ByteArray =
        byteArrayOf(
            P.PACKET_CHANNEL_DATA_RECEIVED.toByte(), snrQuarterDb.toByte(), 0, 0, channel.toByte(), pathLength.toByte(),
            (dataType and 0xFF).toByte(), (dataType ushr 8).toByte(), payload.size.toByte(),
        ) + payload

    fun dropLink(failure: MeshCoreLinkFailure? = MeshCoreLinkFailure.ConnectionFailed) {
        listener?.onDisconnected(failure)
    }

    fun configureRoadstrChannel(index: Int = 1, name: String = "#roadstr") {
        slots[index].name = name
        slots[index].secret = P.hashtagChannelSecret(name)
    }

    fun lastWritten(command: Int): ByteArray? = written.lastOrNull { (it[0].toInt() and 0xFF) == command }
}
