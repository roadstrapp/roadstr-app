package app.roadstr.core.protocol.meshcore

import app.roadstr.core.protocol.meshcore.MeshCoreCompanionProtocol as P
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshCoreCompanionProtocolTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `commands match the examples of the companion protocol document`() {
        // "Example (hex): 01 00 00 00 00 00 00 00 6d 63 63 6c 69" -- app name "mccli".
        assertArrayEquals(bytes(0x01, 0, 0, 0, 0, 0, 0, 0, 0x6d, 0x63, 0x63, 0x6c, 0x69), P.appStart("mccli"))
        assertArrayEquals(bytes(0x16, 0x03), P.deviceQuery())
        assertArrayEquals(bytes(0x1F, 0x01), P.getChannel(1))
        assertArrayEquals(bytes(0x0A), P.syncNextMessage())
        assertArrayEquals(bytes(0x14), P.getBattery())
        // "Example (flood, DATA_TYPE_DEV, payload A1 B2 C3, channel 1): 3E 01 FF FF FF A1 B2 C3"
        assertArrayEquals(
            bytes(0x3E, 0x01, 0xFF, 0xFF, 0xFF, 0xA1, 0xB2, 0xC3),
            P.sendChannelData(1, P.DATA_TYPE_DEV, bytes(0xA1, 0xB2, 0xC3)),
        )
    }

    @Test
    fun `set channel is 50 bytes with a null padded name and a 16 byte secret`() {
        val secret = ByteArray(16) { it.toByte() }
        val frame = P.setChannel(1, "YourChannelName", secret)
        assertEquals(50, frame.size)
        assertEquals(0x20, frame[0].toInt())
        assertEquals(1, frame[1].toInt())
        assertEquals("YourChannelName", String(frame, 2, 15))
        assertTrue(frame.copyOfRange(17, 34).all { it == 0.toByte() })
        assertArrayEquals(secret, frame.copyOfRange(34, 50))
    }

    @Test
    fun `invalid arguments are rejected before they are sent`() {
        assertThrows(IllegalArgumentException::class.java) { P.setChannel(0, "x", ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { P.setChannel(1, "x", ByteArray(32)) }
        assertThrows(IllegalArgumentException::class.java) { P.setChannel(1, "x".repeat(33), ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { P.getChannel(8) }
        assertThrows(IllegalArgumentException::class.java) { P.sendChannelData(1, 0, ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { P.sendChannelData(1, 0xFFFF, ByteArray(164)) }
        assertThrows(IllegalArgumentException::class.java) { P.sendChannelData(8, 0xFFFF, ByteArray(1)) }
    }

    @Test
    fun `the compact frames fit the datagram payload limit with room to spare`() {
        assertTrue(110 <= P.MAX_CHANNEL_DATA_PAYLOAD && 134 <= P.MAX_CHANNEL_DATA_PAYLOAD)
        assertEquals(163, P.MAX_CHANNEL_DATA_PAYLOAD)
    }

    @Test
    fun `a received datagram parses as the document's pseudocode does`() {
        // [1B, snr x4, r, r, channel, path_len, data_type LE, length, payload]
        val frame = bytes(0x1B, 0xF4, 0, 0, 0x01, 0x03, 0x52, 0xFF, 0x03, 0xAA, 0xBB, 0xCC)
        val parsed = P.parse(frame) as P.Inbound.ChannelData
        assertEquals(-3.0, parsed.snrDb, 0.0) // 0xF4 = -12 -> -3 dB
        assertEquals(1, parsed.channelIndex)
        assertEquals(3, parsed.pathLength)
        assertTrue(parsed.flooded)
        assertEquals(0xFF52, parsed.dataType)
        assertArrayEquals(bytes(0xAA, 0xBB, 0xCC), parsed.payload)
        assertEquals(false, (P.parse(bytes(0x1B, 0, 0, 0, 0, 0xFF, 0x52, 0xFF, 0)) as P.Inbound.ChannelData).flooded)
    }

    @Test
    fun `malformed responses are rejected instead of read out of bounds`() {
        assertNull(P.parse(ByteArray(0)))
        assertNull(P.parse(bytes(0x1B, 0, 0, 0, 1, 0, 0x52, 0xFF)))            // header cut short
        assertNull(P.parse(bytes(0x1B, 0, 0, 0, 1, 0, 0x52, 0xFF, 9, 1, 2)))   // length beyond the frame
        assertNull(P.parse(bytes(0x12, 1, 0x41)))                                // channel info cut short
        assertNull(P.parse(ByteArray(50).also { it[0] = 0x12; it[1] = 9 }))      // slot out of range
        assertNull(P.parse(bytes(0x0C, 1)))                                      // battery cut short
        assertNull(P.parse(bytes(0x05, 0, 0, 0)))                                // self info cut short
    }

    @Test
    fun `device info degrades gracefully on old and short frames`() {
        val old = P.parse(bytes(0x0D, 2)) as P.Inbound.DeviceInfo
        assertEquals(2, old.firmwareCode)
        assertNull(old.version)
        val v3Short = P.parse(ByteArray(40).also { it[0] = 0x0D; it[1] = 3 }) as P.Inbound.DeviceInfo
        assertNull(v3Short.model)
        val full = ByteArray(82).also {
            it[0] = 0x0D; it[1] = 13; it[3] = 8
            "Heltec V3".toByteArray().copyInto(it, 20)
            "v1.17.1".toByteArray().copyInto(it, 60)
        }
        val parsed = P.parse(full) as P.Inbound.DeviceInfo
        assertEquals(8, parsed.maxChannels)
        assertEquals("Heltec V3", parsed.model)
        assertEquals("v1.17.1", parsed.version)
    }

    @Test
    fun `text from a radio is cleaned of control characters`() {
        val frame = ByteArray(60)
        frame[0] = 0x12
        frame[1] = 1
        byteArrayOf('#'.code.toByte(), 7, 'a'.code.toByte(), 27, 'b'.code.toByte()).copyInto(frame, 2)
        val info = P.parse(frame) as P.Inbound.ChannelInfo
        assertEquals("#ab", info.name)
    }

    @Test
    fun `hashtag channel secrets follow the documented derivation`() {
        // companion_protocol.md: "#test" has the key 9cd8fcf22a47333b591d96a2b848b73f.
        assertEquals("9cd8fcf22a47333b591d96a2b848b73f", P.hashtagChannelSecret("#test").joinToString("") { "%02x".format(it) })
        assertEquals("b7f4317db7b03aeb563b2c4bdc476d6a", P.hashtagChannelSecret("#roadstr").joinToString("") { "%02x".format(it) })
        assertThrows(IllegalArgumentException::class.java) { P.hashtagChannelSecret("roadstr") }
        assertThrows(IllegalArgumentException::class.java) { P.hashtagChannelSecret("#") }
    }

    @Test
    fun `the development data type is inside the range that needs no registration`() {
        assertTrue(P.ROADSTR_DEVELOPMENT_DATA_TYPE in P.FIRST_DEVELOPMENT_DATA_TYPE..0xFFFE)
    }
}
