package app.roadstr.service.meshcore

import app.roadstr.core.protocol.meshcore.MeshCoreCompanionProtocol as P
import app.roadstr.service.nostr.ManualScheduler
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshCoreClientTest {
    private val radio = FakeMeshCoreRadio()
    private val scheduler = ManualScheduler()
    private val datagrams = mutableListOf<MeshCoreDatagram>()
    private var channelName = "#roadstr"

    private fun client() = MeshCoreClient(
        link = radio,
        scheduler = scheduler,
        channelName = { channelName },
        random = Random(1),
        onDatagram = { datagrams += it },
    )

    private fun MeshCoreClient.ready(): MeshCoreState.Ready = state.value as MeshCoreState.Ready

    private fun send(client: MeshCoreClient, payload: ByteArray = byteArrayOf(1, 2, 3)): MeshCoreSendResult {
        var result: MeshCoreSendResult? = null
        client.send(payload) { result = it }
        scheduler.advance(0)
        return checkNotNull(result) { "no result yet" }
    }

    @Test
    fun `it handshakes, finds the roadstr channel and becomes ready`() {
        radio.configureRoadstrChannel(2)
        val client = client()
        client.start()

        val ready = client.ready()
        assertEquals(2, ready.channelIndex)
        assertEquals("Test node", ready.device.name)
        assertEquals("v1.17.1", ready.device.version)
        assertEquals("Heltec V3", ready.device.model)
        assertEquals(13, ready.device.firmwareCode)
        // The exact commands, in order, as the companion protocol documents them.
        assertEquals(listOf(P.CMD_APP_START, P.CMD_DEVICE_QUERY, P.CMD_GET_CHANNEL, P.CMD_GET_CHANNEL, P.CMD_GET_CHANNEL),
            radio.written.take(5).map { it[0].toInt() and 0xFF })
        assertArrayEquals(byteArrayOf(0x16, 0x03), radio.written[1])
        assertArrayEquals(byteArrayOf(0x1F, 0x00), radio.written[2])
        assertArrayEquals(byteArrayOf(0x1F, 0x02), radio.written[4])
    }

    @Test
    fun `without the channel it waits for the user and writes it only on request`() {
        radio.configureRoadstrChannel(1, "#other")
        val client = client()
        client.start()
        assertTrue(client.state.value is MeshCoreState.ChannelMissing)
        assertEquals("nothing is written to the radio on its own", 0, radio.written.count { (it[0].toInt() and 0xFF) == P.CMD_SET_CHANNEL })

        assertTrue(client.configureChannel())
        // Slot 0 is the public channel and slot 1 is taken: the first empty slot is 2.
        assertEquals("#roadstr", radio.slots[2].name)
        assertArrayEquals(P.hashtagChannelSecret("#roadstr"), radio.slots[2].secret)
        assertEquals(2, client.ready().channelIndex)
        assertEquals("#other", radio.slots[1].name)
    }

    @Test
    fun `configureChannel refuses when the radio is not waiting for it`() {
        val client = client()
        assertFalse(client.configureChannel())
        radio.configureRoadstrChannel()
        client.start()
        assertFalse(client.configureChannel())
    }

    @Test
    fun `a datagram is sent as a flood on the roadstr channel with the development data type`() {
        radio.configureRoadstrChannel(3)
        val client = client()
        client.start()

        assertEquals(MeshCoreSendResult.Sent, send(client, byteArrayOf(0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())))
        // 3E, channel, FF (flood), data_type little endian, payload: the documented layout.
        assertArrayEquals(
            byteArrayOf(0x3E, 0x03, 0xFF.toByte(), 0x52, 0xFF.toByte(), 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte()),
            radio.sentDatagrams.single(),
        )
    }

    @Test
    fun `an oversize payload is refused before it reaches the radio`() {
        radio.configureRoadstrChannel()
        val client = client()
        client.start()
        assertEquals(MeshCoreSendResult.TooLarge, send(client, ByteArray(P.MAX_CHANNEL_DATA_PAYLOAD + 1)))
        assertEquals(MeshCoreSendResult.TooLarge, send(client, ByteArray(0)))
        assertTrue(radio.sentDatagrams.isEmpty())
        assertEquals(MeshCoreSendResult.Sent, send(client, ByteArray(P.MAX_CHANNEL_DATA_PAYLOAD)))
    }

    @Test
    fun `sending without a ready radio does nothing`() {
        val client = client()
        assertEquals(MeshCoreSendResult.NotReady, send(client))
        radio.configureRoadstrChannel(1, "#other")
        client.start()
        assertEquals(MeshCoreSendResult.NotReady, send(client))
        assertTrue(radio.sentDatagrams.isEmpty())
    }

    @Test
    fun `firmware that does not know channel datagrams is reported as incompatible and not retried`() {
        radio.configureRoadstrChannel()
        radio.sendMode = FakeMeshCoreRadio.SendMode.UnsupportedCommand
        val client = client()
        client.start()

        assertEquals(MeshCoreSendResult.Incompatible, send(client))
        val failed = client.state.value as MeshCoreState.Failed
        assertEquals(MeshCoreProblem.IncompatibleFirmware, failed.problem)
        assertEquals(null, failed.retryInMillis)
        val opens = radio.openCount
        scheduler.advance(10 * 60_000)
        assertEquals("no reconnect storm against a radio that cannot do it", opens, radio.openCount)
    }

    @Test
    fun `a full radio queue is retried a few times and then reported as busy`() {
        radio.configureRoadstrChannel()
        radio.sendMode = FakeMeshCoreRadio.SendMode.TableFull
        radio.tableFullTimes = 1
        val client = client()
        client.start()

        var result: MeshCoreSendResult? = null
        client.send(byteArrayOf(9)) { result = it }
        assertEquals("not yet: it waits before retrying", null, result)
        scheduler.advance(MeshCoreClient.RETRY_FULL_QUEUE_MILLIS)
        assertEquals(MeshCoreSendResult.Sent, result)

        radio.tableFullTimes = 10
        result = null
        client.send(byteArrayOf(9)) { result = it }
        scheduler.advance(MeshCoreClient.RETRY_FULL_QUEUE_MILLIS * 3)
        assertEquals(MeshCoreSendResult.Busy, result)
    }

    @Test
    fun `received datagrams are drained, text messages are skipped, other apps' data is ignored`() {
        radio.configureRoadstrChannel(1)
        val client = client()
        client.start()

        radio.receive(byteArrayOf(1, 2, 3), snrQuarterDb = 22, pathLength = 2)
        radio.receiveText()
        radio.receive(byteArrayOf(9), dataType = 0xFF53)       // another application
        radio.receive(byteArrayOf(8), channel = 5)              // another channel
        radio.receive(byteArrayOf(4, 5, 6), pathLength = 0xFF)  // direct

        assertEquals(2, datagrams.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), datagrams[0].payload)
        assertEquals(5.5, datagrams[0].snrDb, 0.0)
        assertTrue(datagrams[0].flooded)
        assertArrayEquals(byteArrayOf(4, 5, 6), datagrams[1].payload)
        assertFalse(datagrams[1].flooded)
        // The queue is read to its end each time, and the client stays usable afterwards.
        assertEquals(MeshCoreSendResult.Sent, send(client))
    }

    @Test
    fun `datagrams that arrived while the app was away are read when it comes back`() {
        radio.configureRoadstrChannel(1)
        radio.receive(byteArrayOf(7, 7)) // pushed to nobody: it sits in the radio's queue
        val client = client()
        client.start()
        assertEquals(1, datagrams.size)
        assertArrayEquals(byteArrayOf(7, 7), datagrams.single().payload)
    }

    @Test
    fun `garbage and inconsistent frames are ignored`() {
        radio.configureRoadstrChannel(1)
        val client = client()
        client.start()
        radio.deliverUnsolicited(ByteArray(0))
        radio.deliverUnsolicited(byteArrayOf(0x1B, 0, 0))                            // far too short
        radio.deliverUnsolicited(byteArrayOf(0x1B, 0, 0, 0, 1, 0, 0x52, 0xFF.toByte(), 50, 1, 2)) // length lies
        radio.deliverUnsolicited(byteArrayOf(0x55, 1, 2, 3))                          // unknown type
        assertTrue(datagrams.isEmpty())
        assertNotNull(client.ready())
    }

    @Test
    fun `battery is read at once and then every few minutes`() {
        radio.configureRoadstrChannel(1)
        val client = client()
        client.start()
        assertEquals(4_012, client.ready().batteryMillivolts)
        radio.batteryMillivolts = 3_800
        scheduler.advance(MeshCoreClient.BATTERY_POLL_MILLIS)
        assertEquals(3_800, client.ready().batteryMillivolts)
    }

    @Test
    fun `a link that drops mid send reports the send as lost and reconnects with backoff`() {
        radio.configureRoadstrChannel(1)
        radio.sendMode = FakeMeshCoreRadio.SendMode.Silent
        val client = client()
        client.start()

        var result: MeshCoreSendResult? = null
        client.send(byteArrayOf(1)) { result = it }
        radio.dropLink()
        assertEquals(MeshCoreSendResult.Lost, result)
        val failed = client.state.value as MeshCoreState.Failed
        assertEquals(MeshCoreProblem.ConnectionLost, failed.problem)
        val delay = requireNotNull(failed.retryInMillis)
        assertTrue(delay in 900..1_600)

        radio.sendMode = FakeMeshCoreRadio.SendMode.Accept
        scheduler.advance(delay)
        assertNotNull(client.ready())
        assertEquals(MeshCoreSendResult.Sent, send(client))
    }

    @Test
    fun `repeated failures back off and a success resets the delay`() {
        radio.openFailure = MeshCoreLinkFailure.DeviceNotFound
        val client = client()
        client.start()
        val delays = mutableListOf<Long>()
        repeat(4) {
            val failed = client.state.value as MeshCoreState.Failed
            assertEquals(MeshCoreProblem.DeviceNotFound, failed.problem)
            delays += failed.retryInMillis!!
            scheduler.advance(failed.retryInMillis!!)
        }
        assertTrue("delays grow: $delays", delays[3] > delays[0])
        assertTrue(delays.all { it <= MeshCoreClient.MAX_RECONNECT_MILLIS * 5 / 4 })

        radio.openFailure = null
        radio.configureRoadstrChannel(1)
        scheduler.advance((client.state.value as MeshCoreState.Failed).retryInMillis!!)
        assertNotNull(client.ready())
        radio.dropLink()
        val again = (client.state.value as MeshCoreState.Failed).retryInMillis!!
        assertTrue("a good connection restarts the backoff", again < delays[3])
    }

    @Test
    fun `bluetooth off or a missing permission wait for the user instead of retrying`() {
        for (failure in listOf(MeshCoreLinkFailure.BluetoothOff, MeshCoreLinkFailure.PermissionMissing)) {
            val local = FakeMeshCoreRadio().also { it.openFailure = failure }
            val client = MeshCoreClient(local, scheduler, { "#roadstr" }, onDatagram = {})
            client.start()
            val failed = client.state.value as MeshCoreState.Failed
            assertEquals(null, failed.retryInMillis)
            scheduler.advance(5 * 60_000)
            assertEquals(1, local.openCount)
        }
    }

    @Test
    fun `a radio that stops answering times out and is reconnected`() {
        radio.answerNothingTo = setOf(P.CMD_DEVICE_QUERY)
        val client = client()
        client.start()
        assertTrue(client.state.value is MeshCoreState.Handshaking)
        scheduler.advance(MeshCoreClient.COMMAND_TIMEOUT_MILLIS)
        assertEquals(MeshCoreProblem.Timeout, (client.state.value as MeshCoreState.Failed).problem)
        assertTrue(radio.closeCount >= 1)
    }

    @Test
    fun `a write the radio refuses fails the link instead of waiting for a timeout`() {
        radio.refuseWrites = true
        val client = client()
        client.start()
        assertEquals(MeshCoreProblem.ConnectionLost, (client.state.value as MeshCoreState.Failed).problem)
    }

    @Test
    fun `stop disconnects, cancels the timers and ignores late frames`() {
        radio.configureRoadstrChannel(1)
        val client = client()
        client.start()
        client.stop()
        assertEquals(MeshCoreState.Off, client.state.value)
        assertTrue(radio.closeCount >= 1)
        assertEquals(0, scheduler.pendingCount)
        radio.receive(byteArrayOf(1))
        assertTrue(datagrams.isEmpty())
        assertEquals(MeshCoreSendResult.NotReady, send(client))
    }

    @Test
    fun `a callback may call straight back into the client`() {
        radio.configureRoadstrChannel(1)
        var echoed: MeshCoreSendResult? = null
        lateinit var client: MeshCoreClient
        client = MeshCoreClient(radio, scheduler, { "#roadstr" }, random = Random(1), onDatagram = { client.send(it.payload) { r -> echoed = r } })
        client.start()
        radio.receive(byteArrayOf(5))
        assertEquals(MeshCoreSendResult.Sent, echoed)
    }
}
