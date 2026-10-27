package app.roadstr.service.meshcore

import kotlinx.coroutines.flow.StateFlow

/**
 * What the shell needs from a radio backend. Two implementations: [MeshCoreClient] over BLE and
 * [LocalApiMeshClient] over MeshCore Open's local API, so the app can share the radio with the
 * chat app instead of taking the BLE link over.
 */
interface MeshRadioClient : MeshRadioPort {
    val state: StateFlow<MeshCoreState>

    fun start()

    fun stop()

    /** The BLE client finds or creates the shared channel; the local API resolves it by name. */
    fun configureChannel(): Boolean
}
