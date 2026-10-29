package app.roadstr.service.meshcore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchableMeshRadioTest {
    private class FakeLink(val name: String) : MeshRadioClient {
        override val state = MutableStateFlow<MeshCoreState>(MeshCoreState.Off)
        val sent = mutableListOf<ByteArray>()
        val waiting = mutableListOf<(MeshCoreSendResult) -> Unit>()
        var starts = 0
        var stops = 0

        override val ready: Boolean get() = state.value is MeshCoreState.Ready

        override fun start() {
            starts++
            state.value = MeshCoreState.Ready(MeshCoreDevice(name, "", 0, null, null), 0, null)
        }

        override fun stop() {
            stops++
            state.value = MeshCoreState.Off
            // A link that is stopped loses what it had in flight, and says so.
            waiting.toList().also { waiting.clear() }.forEach { it(MeshCoreSendResult.Lost) }
        }

        override fun configureChannel() = name == "ble"

        override fun send(payload: ByteArray, onResult: (MeshCoreSendResult) -> Unit) {
            sent += payload
            waiting += onResult
        }
    }

    private val ble = FakeLink("ble")
    private val local = FakeLink("local")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun `BLE is the default and the local API is used only when chosen`() {
        val radio = SwitchableMeshRadio(scope, ble, local, initialLocal = false)
        assertFalse(radio.useLocalApi)
        radio.start()
        assertEquals(1, ble.starts)
        assertEquals(0, local.starts)
        assertTrue(radio.configureChannel())
    }

    @Test
    fun `without a key store there is only BLE`() {
        val radio = SwitchableMeshRadio(scope, ble, null, initialLocal = true)
        assertFalse(radio.localApiAvailable)
        assertFalse(radio.useLocalApi)
        radio.setMode(true)
        assertFalse(radio.useLocalApi)
    }

    @Test
    fun `switching mid-send stops the old link, reports the lost send once, and sends on the new one`() {
        val radio = SwitchableMeshRadio(scope, ble, local, initialLocal = false)
        radio.start()
        val results = mutableListOf<MeshCoreSendResult>()
        radio.send(byteArrayOf(1)) { results += it }
        assertEquals(1, ble.sent.size)

        radio.setMode(true)
        assertEquals(listOf(MeshCoreSendResult.Lost), results)
        assertEquals(1, ble.stops)
        assertEquals(1, local.starts)
        assertTrue(radio.useLocalApi)
        assertTrue(radio.state.value is MeshCoreState.Ready)
        assertEquals("local", (radio.state.value as MeshCoreState.Ready).device.name)

        radio.send(byteArrayOf(2)) { results += it }
        assertEquals(1, ble.sent.size)
        assertEquals(1, local.sent.size)
        local.waiting.single()(MeshCoreSendResult.Sent)
        assertEquals(listOf(MeshCoreSendResult.Lost, MeshCoreSendResult.Sent), results)
    }

    @Test
    fun `the old link's state no longer reaches the screen after a switch`() {
        val radio = SwitchableMeshRadio(scope, ble, local, initialLocal = false)
        radio.setMode(true)
        ble.state.value = MeshCoreState.Failed(MeshCoreProblem.BluetoothOff, null)
        assertEquals(MeshCoreState.Off, radio.state.value)
    }

    @Test
    fun `switching while stopped starts nothing`() {
        val radio = SwitchableMeshRadio(scope, ble, local, initialLocal = false)
        radio.setMode(true)
        assertEquals(0, local.starts)
        assertEquals(0, ble.stops)
        radio.start()
        assertEquals(1, local.starts)
        radio.stop()
        assertEquals(1, local.stops)
    }
}
