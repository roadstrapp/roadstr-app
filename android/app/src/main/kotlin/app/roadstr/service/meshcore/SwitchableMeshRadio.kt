package app.roadstr.service.meshcore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One radio, two ways to reach it: the BLE companion link this app owns, or MeshCore Open's local
 * API on the same phone. The user picks; both carry the same frames, so the bridge and the codec
 * do not care which one is active. Without a [localApi] (no key store) only BLE exists.
 */
class SwitchableMeshRadio(
    private val scope: CoroutineScope,
    private val ble: MeshRadioClient,
    private val localApi: MeshRadioClient?,
    initialLocal: Boolean,
) : MeshRadioClient {
    private val lock = Any()
    private val _state = MutableStateFlow<MeshCoreState>(MeshCoreState.Off)
    private var active: MeshRadioClient = if (initialLocal && localApi != null) localApi else ble
    private var wanted = false
    private var stateJob: Job? = null

    override val state: StateFlow<MeshCoreState> = _state.asStateFlow()

    /** True while the active link is MeshCore Open's local API. */
    val useLocalApi: Boolean get() = synchronized(lock) { active === localApi }

    val localApiAvailable: Boolean get() = localApi != null

    init {
        synchronized(lock) { bindLocked() }
    }

    private fun bindLocked() {
        stateJob?.cancel()
        val link = active
        _state.value = link.state.value
        // Only the active link's state reaches the screen; a late update from the old one is ignored.
        stateJob = scope.launch { link.state.collect { if (current() === link) _state.value = it } }
    }

    private fun current(): MeshRadioClient = synchronized(lock) { active }

    override val ready: Boolean get() = current().ready

    override fun start() {
        val link = synchronized(lock) {
            wanted = true
            active
        }
        link.start()
    }

    override fun stop() {
        val link = synchronized(lock) {
            wanted = false
            active
        }
        link.stop()
    }

    override fun configureChannel(): Boolean = current().configureChannel()

    /** Goes to the link active now; a result from a link switched away from still reaches [onResult]. */
    override fun send(payload: ByteArray, onResult: (MeshCoreSendResult) -> Unit) = current().send(payload, onResult)

    /** Switches at once; the old link is stopped first so only one of them owns the radio. */
    fun setMode(local: Boolean) {
        val target = if (local) localApi ?: return else ble
        val (previous, restart) = synchronized(lock) {
            if (active === target) return
            val previous = active
            active = target
            bindLocked()
            previous to wanted
        }
        if (restart) previous.stop()
        if (restart) target.start()
    }
}
