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

    /** `receivedAt` of the last frame MeshCore Open's local API delivered, for its replay. */
    fun localApiCursor(): String? = null

    fun setLocalApiCursor(receivedAt: String) = Unit
}

/**
 * The pieces of the MeshCore support wired together: the radio client, the bridge and the queue of
 * radio events waiting for a relay. Nothing runs until [client] is started, and nothing is started
 * unless the user turned a radio feature on.
 *
 * The radio is reached over this app's BLE link or, when [localApiIdentity] is given and the user
 * chooses it, through MeshCore Open's local API on the same phone.
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
    /** The key this app pairs with MeshCore Open; null leaves only the BLE link. */
    private val localApiIdentity: LocalApiIdentity? = null,
    /** Keeps the local API's replay cursor. */
    cursorStorage: NativeMeshStorage? = null,
    clientVersion: String = "",
    /** Which link to use until the settings screen says otherwise. BLE unless the user chose. */
    startWithLocalApi: Boolean = false,
) {
    @Volatile
    private var configProvider: () -> MeshBridgeConfig = { MeshBridgeConfig() }

    @Volatile
    private var positionProvider: () -> GeoPoint? = { null }

    val republish = MeshRepublishQueue(republishStorage, nowSeconds)

    val client: SwitchableMeshRadio = SwitchableMeshRadio(
        scope = scope,
        ble = MeshCoreClient(
            link = link,
            scheduler = scheduler,
            channelName = channelName,
            onDatagram = { datagram -> bridge.onDatagram(datagram) },
        ),
        localApi = localApiIdentity?.let { identity ->
            LocalApiMeshClient(
                scope = scope,
                channelName = channelName,
                identity = identity,
                readCursor = { cursorStorage?.localApiCursor() },
                writeCursor = { cursorStorage?.setLocalApiCursor(it) },
                clientVersion = clientVersion.take(MAX_CLIENT_VERSION),
                onDatagram = { datagram -> bridge.onDatagram(datagram) },
            )
        },
        initialLocal = startWithLocalApi,
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

    /**
     * What MeshCore Open shows when it asks to allow this app, so the user can compare the two.
     * Reads the key store: call it off the main thread.
     */
    fun localApiFingerprint(): String? =
        localApiIdentity?.let { identity -> runCatching { LocalApiIdentity.fingerprint(identity.publicKey()) }.getOrNull() }

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
        const val MAX_CLIENT_VERSION = 32
    }
}
