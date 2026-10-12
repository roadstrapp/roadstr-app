package app.roadstr.service.meshcore

import app.roadstr.core.geo.GeoPoint
import app.roadstr.service.nostr.NativePendingReportStorage
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.NativeScheduler
import kotlinx.coroutines.CoroutineScope

/** The radio the user chose, kept between launches. */
data class MeshCoreSavedDevice(val name: String, val address: String)

/** What the MeshCore support remembers; the address of a radio is a device identifier, so it is stored protected. */
interface NativeMeshStorage {
    fun device(): MeshCoreSavedDevice?

    fun saveDevice(device: MeshCoreSavedDevice?)

    /** Name of the shared hashtag channel on the radio. */
    fun channelName(): String

    /** Whether the user has read and accepted what transmitting by radio exposes. */
    fun transmitConsent(): Boolean

    fun setTransmitConsent(accepted: Boolean)
}

/**
 * The pieces of the MeshCore support wired together: the radio client, the bridge and the queue of
 * radio events waiting for a relay. Nothing runs until [client] is started, and nothing is started
 * unless the user turned a radio feature on.
 */
class MeshRuntime(
    link: MeshCoreLink,
    scheduler: NativeScheduler,
    roads: NativeRoadEventService,
    republishStorage: NativePendingReportStorage,
    /** Where the radio-related part of the duplicate ledger is kept between launches. */
    private val ledgerStorage: NativePendingReportStorage,
    channelName: () -> String,
    nowSeconds: () -> Long,
    scope: CoroutineScope,
) {
    @Volatile
    private var configProvider: () -> MeshBridgeConfig = { MeshBridgeConfig() }

    @Volatile
    private var positionProvider: () -> GeoPoint? = { null }

    val republish = MeshRepublishQueue(republishStorage, nowSeconds)

    val client: MeshCoreClient = MeshCoreClient(
        link = link,
        scheduler = scheduler,
        channelName = channelName,
        onDatagram = { datagram -> bridge.onDatagram(datagram) },
    )

    val bridge: RoadMeshBridge = RoadMeshBridge(
        radio = client,
        roads = roads,
        republish = republish,
        config = { configProvider() },
        position = { positionProvider() },
        nowSeconds = nowSeconds,
        scope = scope,
    )

    private val ledger = roads.ledger

    init {
        roads.setInteropListener(bridge)
        runCatching { ledger.restore(ledgerStorage.read()) }
    }

    /** Saves what the radio has carried, so a restart does not put the same events on the air again. */
    fun persistLedger() {
        runCatching { ledgerStorage.write(ledger.snapshotRadioInvolved(MAX_PERSISTED_LEDGER)) }
    }

    /**
     * Where the user's choices and the driver's position come from. Set by the screen, because
     * both live there; until then everything is off and nothing is known.
     */
    fun configure(config: () -> MeshBridgeConfig, position: () -> GeoPoint?) {
        configProvider = config
        positionProvider = position
    }

    /** Drops the radio and the bridge's hook into the relay path. The queue stays on disk. */
    fun close() {
        client.stop()
        persistLedger()
    }

    private companion object {
        const val MAX_PERSISTED_LEDGER = 1_000
    }
}
