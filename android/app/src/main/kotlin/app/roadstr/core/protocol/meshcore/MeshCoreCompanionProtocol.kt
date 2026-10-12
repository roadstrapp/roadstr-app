package app.roadstr.core.protocol.meshcore

import java.security.MessageDigest

/**
 * The MeshCore companion protocol (BLE), the part Roadstr needs: pairing with a radio, finding a
 * channel, sending binary channel datagrams and receiving them.
 *
 * Source: docs/companion_protocol.md of meshcore-dev/MeshCore, companion firmware v1.12.0+, checked
 * against examples/companion_radio/MyMesh.cpp of firmware 1.17. Frames come from a radio over BLE
 * and carry whatever the air delivered: every parser here bounds-checks and returns null rather
 * than throwing on a short or inconsistent frame.
 */
object MeshCoreCompanionProtocol {
    // BLE (Nordic UART service), as documented.
    const val SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    const val RX_CHARACTERISTIC_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E" // app -> firmware
    const val TX_CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // firmware -> app

    // Commands.
    const val CMD_APP_START = 0x01
    const val CMD_SYNC_NEXT_MESSAGE = 0x0A
    const val CMD_GET_BATTERY = 0x14
    const val CMD_DEVICE_QUERY = 0x16
    const val CMD_GET_CHANNEL = 0x1F
    const val CMD_SET_CHANNEL = 0x20
    const val CMD_SEND_CHANNEL_DATA = 0x3E

    // Responses and pushes.
    const val PACKET_OK = 0x00
    const val PACKET_ERROR = 0x01
    const val PACKET_SELF_INFO = 0x05
    const val PACKET_NO_MORE_MESSAGES = 0x0A
    const val PACKET_BATTERY = 0x0C
    const val PACKET_DEVICE_INFO = 0x0D
    const val PACKET_CHANNEL_INFO = 0x12
    const val PACKET_CHANNEL_DATA_RECEIVED = 0x1B
    const val PACKET_MESSAGES_WAITING = 0x83

    // Errors (PACKET_ERROR byte 1).
    const val ERR_UNSUPPORTED_COMMAND = 1
    const val ERR_NOT_FOUND = 2
    const val ERR_TABLE_FULL = 3
    const val ERR_BAD_STATE = 4
    const val ERR_FILE_IO = 5
    const val ERR_ILLEGAL_ARGUMENT = 6

    /** App protocol version announced in DEVICE_QUERY; 3 is the one that adds the V3/SNR frames. */
    const val APP_PROTOCOL_VERSION = 3

    const val CHANNEL_SLOTS = 8
    const val CHANNEL_NAME_BYTES = 32
    const val CHANNEL_SECRET_BYTES = 16

    /** MAX_FRAME_SIZE (172) - 9: what firmware accepts as a channel-data payload. */
    const val MAX_CHANNEL_DATA_PAYLOAD = 163
    const val FLOOD = 0xFF

    /** data_type values: 0xFF00-0xFFFF are for development and need no registration. */
    const val DATA_TYPE_RESERVED = 0x0000
    const val DATA_TYPE_DEV = 0xFFFF
    const val FIRST_DEVELOPMENT_DATA_TYPE = 0xFF00

    /**
     * What Roadstr uses while there is no registered application ID: a value inside the
     * development range (0x52 is "R"). A stable ID is requested from MeshCore by a pull request to
     * docs/number_allocations.md once a working application can be demonstrated; until then this
     * stays a development value and is not an official assignment.
     */
    const val ROADSTR_DEVELOPMENT_DATA_TYPE = 0xFF52

    // ── commands ────────────────────────────────────────────────────────────────────────────────

    fun appStart(appName: String = "Roadstr"): ByteArray {
        val name = appName.toByteArray(Charsets.UTF_8).take(32).toByteArray()
        return byteArrayOf(CMD_APP_START.toByte()) + ByteArray(7) + name
    }

    fun deviceQuery(): ByteArray = byteArrayOf(CMD_DEVICE_QUERY.toByte(), APP_PROTOCOL_VERSION.toByte())

    fun getChannel(index: Int): ByteArray {
        require(index in 0 until CHANNEL_SLOTS) { "channel slot out of range" }
        return byteArrayOf(CMD_GET_CHANNEL.toByte(), index.toByte())
    }

    fun setChannel(index: Int, name: String, secret: ByteArray): ByteArray {
        require(index in 1 until CHANNEL_SLOTS) { "slot 0 is the public channel" }
        require(secret.size == CHANNEL_SECRET_BYTES) { "the secret is 16 bytes (the 32-byte form is unsupported)" }
        val encoded = name.toByteArray(Charsets.UTF_8)
        require(encoded.size <= CHANNEL_NAME_BYTES) { "channel name longer than 32 bytes" }
        val padded = encoded.copyOf(CHANNEL_NAME_BYTES)
        return byteArrayOf(CMD_SET_CHANNEL.toByte(), index.toByte()) + padded + secret
    }

    /** Flood (no path) datagram on a channel. [payload] is opaque to the firmware. */
    fun sendChannelData(channelIndex: Int, dataType: Int, payload: ByteArray): ByteArray {
        require(channelIndex in 0 until CHANNEL_SLOTS) { "channel slot out of range" }
        require(dataType in 1..0xFFFF) { "data_type 0 is reserved" }
        require(payload.size <= MAX_CHANNEL_DATA_PAYLOAD) { "payload larger than the firmware accepts" }
        return byteArrayOf(
            CMD_SEND_CHANNEL_DATA.toByte(),
            channelIndex.toByte(),
            FLOOD.toByte(),
            (dataType and 0xFF).toByte(),
            (dataType ushr 8).toByte(),
        ) + payload
    }

    fun syncNextMessage(): ByteArray = byteArrayOf(CMD_SYNC_NEXT_MESSAGE.toByte())

    fun getBattery(): ByteArray = byteArrayOf(CMD_GET_BATTERY.toByte())

    // ── responses ───────────────────────────────────────────────────────────────────────────────

    sealed interface Inbound {
        data class Ok(val value: Long?) : Inbound

        data class Error(val code: Int?) : Inbound

        data class SelfInfo(val publicKeyPrefix: String, val name: String) : Inbound

        data class DeviceInfo(
            val firmwareCode: Int,
            val maxChannels: Int?,
            val model: String?,
            val version: String?,
        ) : Inbound

        data class ChannelInfo(val index: Int, val name: String, val secret: ByteArray) : Inbound {
            override fun equals(other: Any?): Boolean =
                other is ChannelInfo && other.index == index && other.name == name && other.secret.contentEquals(secret)

            override fun hashCode(): Int = 31 * (31 * index + name.hashCode()) + secret.contentHashCode()
        }

        data class ChannelData(
            val snrDb: Double,
            val channelIndex: Int,
            /** 0xFF: it arrived over a direct route. Anything else: it was flooded, and this is its encoded path length. */
            val pathLength: Int,
            val dataType: Int,
            val payload: ByteArray,
        ) : Inbound {
            val flooded: Boolean get() = pathLength != FLOOD

            override fun equals(other: Any?): Boolean = other is ChannelData && other.snrDb == snrDb &&
                other.channelIndex == channelIndex && other.pathLength == pathLength &&
                other.dataType == dataType && other.payload.contentEquals(payload)

            override fun hashCode(): Int = 31 * (31 * channelIndex + dataType) + payload.contentHashCode()
        }

        data object NoMoreMessages : Inbound

        data class Battery(val millivolts: Int) : Inbound

        data object MessagesWaiting : Inbound

        /** A frame type Roadstr has no use for (adverts, text messages, logs...). */
        data class Other(val type: Int) : Inbound
    }

    /** Null for a frame that is empty or inconsistent with its own type. */
    fun parse(frame: ByteArray): Inbound? {
        if (frame.isEmpty()) return null
        return when (val type = frame[0].toInt() and 0xFF) {
            PACKET_OK -> Inbound.Ok(if (frame.size >= 5) uint32le(frame, 1) else null)
            PACKET_ERROR -> Inbound.Error(frame.getOrNull(1)?.let { it.toInt() and 0xFF })
            PACKET_SELF_INFO -> parseSelfInfo(frame)
            PACKET_DEVICE_INFO -> parseDeviceInfo(frame)
            PACKET_CHANNEL_INFO -> parseChannelInfo(frame)
            PACKET_CHANNEL_DATA_RECEIVED -> parseChannelData(frame)
            PACKET_NO_MORE_MESSAGES -> Inbound.NoMoreMessages
            PACKET_BATTERY -> if (frame.size >= 3) Inbound.Battery(uint16le(frame, 1)) else null
            PACKET_MESSAGES_WAITING -> Inbound.MessagesWaiting
            else -> Inbound.Other(type)
        }
    }

    private fun parseSelfInfo(frame: ByteArray): Inbound.SelfInfo? {
        // type, adv type, tx power, max tx power, public key (32): the name, if any, starts at 58.
        if (frame.size < 36) return null
        val key = frame.copyOfRange(4, 36).joinToString("") { "%02x".format(it) }
        val name = if (frame.size > 58) text(frame, 58, frame.size) else ""
        return Inbound.SelfInfo(publicKeyPrefix = key.take(12), name = name)
    }

    private fun parseDeviceInfo(frame: ByteArray): Inbound.DeviceInfo? {
        if (frame.size < 2) return null
        val firmware = frame[1].toInt() and 0xFF
        if (firmware < 3 || frame.size < 80) return Inbound.DeviceInfo(firmware, null, null, null)
        return Inbound.DeviceInfo(
            firmwareCode = firmware,
            maxChannels = frame[3].toInt() and 0xFF,
            model = text(frame, 20, 60).ifBlank { null },
            version = text(frame, 60, 80).ifBlank { null },
        )
    }

    private fun parseChannelInfo(frame: ByteArray): Inbound.ChannelInfo? {
        if (frame.size < 50) return null
        val index = frame[1].toInt() and 0xFF
        if (index >= CHANNEL_SLOTS) return null
        return Inbound.ChannelInfo(index, text(frame, 2, 34), frame.copyOfRange(34, 50))
    }

    private fun parseChannelData(frame: ByteArray): Inbound.ChannelData? {
        if (frame.size < 9) return null
        val length = frame[8].toInt() and 0xFF
        if (9 + length > frame.size) return null
        val snrByte = frame[1].toInt() // signed, scaled x4
        return Inbound.ChannelData(
            snrDb = snrByte / 4.0,
            channelIndex = frame[4].toInt() and 0xFF,
            pathLength = frame[5].toInt() and 0xFF,
            dataType = uint16le(frame, 6),
            payload = frame.copyOfRange(9, 9 + length),
        )
    }

    // ── channels ────────────────────────────────────────────────────────────────────────────────

    /**
     * The key MeshCore derives for a hashtag channel: the first 16 bytes of SHA-256 of the name
     * including its '#'. Anyone who knows or guesses the name derives the same key, so such a
     * channel is shared and public, not private. Verified: "#test" gives 9cd8fcf22a47333b591d96a2b848b73f.
     */
    fun hashtagChannelSecret(name: String): ByteArray {
        require(name.startsWith("#") && name.length in 2..CHANNEL_NAME_BYTES) { "not a hashtag channel name" }
        return MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8)).copyOf(CHANNEL_SECRET_BYTES)
    }

    // ── bytes ───────────────────────────────────────────────────────────────────────────────────

    private fun uint16le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun uint32le(bytes: ByteArray, offset: Int): Long =
        uint16le(bytes, offset).toLong() or (uint16le(bytes, offset + 2).toLong() shl 16)

    private fun text(bytes: ByteArray, from: Int, to: Int): String {
        var end = from
        while (end < to && bytes[end].toInt() != 0) end++
        return String(bytes, from, end - from, Charsets.UTF_8).filter { it.code >= 0x20 }.trim()
    }
}
