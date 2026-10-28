package app.roadstr.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.roadstr.R
import app.roadstr.core.geo.GeoPoint
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.settings.NativeSettingsBooleanKey
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsMeshProblem
import app.roadstr.feature.settings.NativeSettingsMeshState
import app.roadstr.feature.settings.NativeSettingsMeshStatus
import app.roadstr.feature.settings.NativeSettingsSession
import app.roadstr.service.meshcore.MeshBridgeConfig
import app.roadstr.service.meshcore.MeshCoreLinkFailure
import app.roadstr.service.meshcore.MeshCoreProblem
import app.roadstr.service.meshcore.MeshCoreSavedDevice
import app.roadstr.service.meshcore.MeshCoreScanResult
import app.roadstr.service.meshcore.MeshCoreState
import app.roadstr.service.meshcore.MeshRuntime
import app.roadstr.service.meshcore.NativeMeshStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the activity provides for the optional MeshCore support; null means the build or device has none. */
class NativeShellMesh(
    val runtime: MeshRuntime,
    val storage: NativeMeshStorage,
    /** Whether Bluetooth LE exists on this device at all. */
    val supported: Boolean,
    val permissionGranted: () -> Boolean,
    /** Asks for the Bluetooth runtime permission; the callback gets the outcome. */
    val requestPermission: ((Boolean) -> Unit) -> Unit,
    val startScan: (onFound: (MeshCoreScanResult) -> Unit, onEnd: (MeshCoreLinkFailure?) -> Unit) -> Unit,
    val stopScan: () -> Unit,
)

private val CONSENT_GATED = setOf(
    NativeSettingsBooleanKey.MeshSend,
    NativeSettingsBooleanKey.MeshBridgeToNostr,
    NativeSettingsBooleanKey.MeshBridgeToRadio,
)

/**
 * Ties the MeshCore support to the screen: starts and stops the radio with the lifecycle, feeds the
 * bridge the user's choices, reports the radio's state to the settings screen, and owns the two
 * dialogs (choosing a radio, accepting what transmitting means).
 */
class NativeShellMeshHost internal constructor(
    private val mesh: NativeShellMesh,
    private val applyBoolean: (NativeSettingsBooleanKey, Boolean) -> Unit,
    private val values: () -> NativeSettingsInput,
    private val toast: (Int) -> Unit,
    private val launch: (String) -> Boolean,
) {
    internal var scanVisible by mutableStateOf(false)
    internal var scanning by mutableStateOf(false)
    internal val found = mutableStateListOf<MeshCoreScanResult>()
    internal var pendingConsent by mutableStateOf<Pair<NativeSettingsBooleanKey, Boolean>?>(null)
    internal var saved by mutableStateOf(mesh.storage.device())

    /** Bumped when the waiting queue changes without the radio or the bridge saying so. */
    internal var queueRevision by mutableIntStateOf(0)

    /** The settings switch was flipped: radio transmission asks for consent first, once. */
    fun toggle(key: NativeSettingsBooleanKey, value: Boolean) {
        val needsConsent = value && key in CONSENT_GATED && !mesh.storage.transmitConsent()
        if (needsConsent) {
            pendingConsent = key to value
            return
        }
        apply(key, value)
    }

    internal fun acceptConsent() {
        val (key, value) = pendingConsent ?: return
        pendingConsent = null
        mesh.storage.setTransmitConsent(true)
        apply(key, value)
    }

    private fun apply(key: NativeSettingsBooleanKey, value: Boolean) {
        applyBoolean(key, value)
        // A radio feature on, the BLE link chosen and no radio picked yet: go straight to choosing one.
        val turnedOn = value || key == NativeSettingsBooleanKey.MeshUseLocalApi
        val v = values()
        val needsRadio = (v.meshReceiveEnabled || v.meshSendEnabled) && !v.meshUseLocalApi
        if (turnedOn && needsRadio && saved == null && mesh.supported) pickDevice()
    }

    /** Brings MeshCore Open forward, where its prompt, API switch and radio are. */
    fun openLocalApiApp() {
        if (!launch(LOCAL_API_PACKAGE)) toast(R.string.native_mesh_local_missing)
    }

    fun pickDevice() {
        if (mesh.permissionGranted()) return openScan()
        mesh.requestPermission { granted -> if (granted) openScan() else toast(R.string.native_mesh_permission_denied) }
    }

    internal fun openScan() {
        scanVisible = true
        search()
    }

    internal fun search() {
        found.clear()
        scanning = true
        mesh.startScan(
            { result -> if (found.none { it.address == result.address }) found += result },
            { failure ->
                scanning = false
                if (failure == MeshCoreLinkFailure.PermissionMissing) toast(R.string.native_mesh_permission_denied)
            },
        )
    }

    internal fun closeScan() {
        mesh.stopScan()
        scanning = false
        scanVisible = false
    }

    internal fun choose(result: MeshCoreScanResult) {
        closeScan()
        val device = MeshCoreSavedDevice(result.name, result.address)
        mesh.storage.saveDevice(device)
        saved = device
        mesh.runtime.client.stop()
        mesh.runtime.client.start()
    }

    fun forgetDevice() {
        mesh.runtime.client.stop()
        mesh.storage.saveDevice(null)
        saved = null
    }

    fun configureChannel() {
        mesh.runtime.client.configureChannel()
    }

    fun clearQueue() {
        mesh.runtime.republish.clear()
        queueRevision++
    }
}

/** Creates the host, or null when this build has no MeshCore support. */
@Composable
fun rememberNativeShellMeshHost(
    mesh: NativeShellMesh?,
    settingsSession: NativeSettingsSession,
    nostrHost: NativeShellNostrHost?,
    currentPoint: () -> NativeMapPoint?,
): NativeShellMeshHost? {
    if (mesh == null) return null
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val host = remember(mesh) {
        NativeShellMeshHost(
            mesh = mesh,
            applyBoolean = { key, value ->
                settingsSession.updateBoolean(settingsSession.state.value.revision, key, value)
            },
            values = { settingsSession.state.value.values },
            toast = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
            launch = { packageName ->
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                intent != null && runCatching { context.startActivity(intent) }.isSuccess
            },
        )
    }
    val values = settingsSession.state.value.values
    val radioWanted = values.meshReceiveEnabled || values.meshSendEnabled
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) resumed = true
            if (event == Lifecycle.Event.ON_STOP) resumed = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The radio link lives as long as the screen is in view and a radio feature is on. MeshCore Open
    // asks the user in its own window, so a link waiting for that answer outlives this screen: closing
    // it would withdraw the prompt the user just switched over to answer.
    val useLocalApi = values.meshUseLocalApi && mesh.runtime.client.localApiAvailable
    val link by mesh.runtime.client.state.collectAsState()
    val awaitingApproval = useLocalApi && link is MeshCoreState.Handshaking
    LaunchedEffect(mesh, radioWanted, resumed, host.saved, useLocalApi, awaitingApproval) {
        mesh.runtime.client.setMode(useLocalApi)
        val linkReady = useLocalApi || host.saved != null
        val inUse = resumed || awaitingApproval
        if (radioWanted && inUse && linkReady) mesh.runtime.client.start() else mesh.runtime.client.stop()
    }
    SideEffectConfig(mesh, settingsSession, currentPoint)
    BindOwnEvents(mesh, nostrHost)
    ReportStatus(mesh, host, settingsSession)
    KeepRepublishing(mesh, radioWanted && resumed)
    return host
}

@Composable
private fun SideEffectConfig(mesh: NativeShellMesh, settingsSession: NativeSettingsSession, currentPoint: () -> NativeMapPoint?) {
    val latestPoint by rememberUpdatedState(currentPoint)
    SideEffect {
        mesh.runtime.configure(
            config = {
                val v = settingsSession.state.value.values
                val consent = mesh.storage.transmitConsent()
                MeshBridgeConfig(
                    receive = v.meshReceiveEnabled,
                    send = v.meshSendEnabled && consent,
                    meshToNostr = v.meshBridgeToNostr && v.meshReceiveEnabled && consent,
                    nostrToMesh = v.meshBridgeToRadio && v.meshSendEnabled && consent,
                )
            },
            position = { latestPoint()?.let { GeoPoint(it.latitude, it.longitude) } },
        )
    }
}

@Composable
private fun BindOwnEvents(mesh: NativeShellMesh, nostrHost: NativeShellNostrHost?) {
    val reports = nostrHost?.reports ?: return
    DisposableEffect(reports, mesh) {
        reports.onCompatibleEvent = mesh.runtime.bridge::sendOwn
        onDispose { reports.onCompatibleEvent = null }
    }
}

@Composable
private fun ReportStatus(mesh: NativeShellMesh, host: NativeShellMeshHost, settingsSession: NativeSettingsSession) {
    val state by mesh.runtime.client.state.collectAsState()
    val stats by mesh.runtime.bridge.stats.collectAsState()
    LaunchedEffect(state) { if (state is MeshCoreState.Ready) mesh.runtime.bridge.onRadioReady() }
    // The session only takes a status while the panel is open, and opening it reloads the values: run
    // again when it opens or when its status differs from the one last written.
    val session by settingsSession.state.collectAsState()
    val useLocalApi = session.values.meshUseLocalApi
    LaunchedEffect(
        state, host.saved, host.scanning, host.queueRevision, stats.queuedForRelays, stats.publishedToRelays,
        useLocalApi, session.status, session.revision, session.values.mesh,
    ) {
        // The queue is stored encrypted and the key sits in the key store: read both off the main thread.
        val waiting = withContext(Dispatchers.IO) { mesh.runtime.republish.size() }
        val fingerprint = if (mesh.runtime.client.useLocalApi) withContext(Dispatchers.IO) { mesh.runtime.localApiFingerprint() } else null
        val status = statusOf(mesh, host, state, waiting, fingerprint)
        val current = settingsSession.state.value
        if (current.values.mesh != status) {
            settingsSession.refresh(current.revision, current.values.copy(mesh = status))
        }
    }
}

private fun statusOf(
    mesh: NativeShellMesh,
    host: NativeShellMeshHost,
    state: MeshCoreState,
    waiting: Int,
    fingerprint: String?,
): NativeSettingsMeshStatus {
    val device = host.saved
    val ready = state as? MeshCoreState.Ready
    val known = ready?.device ?: (state as? MeshCoreState.ChannelMissing)?.device
    val localApi = mesh.runtime.client.useLocalApi
    return NativeSettingsMeshStatus(
        supported = mesh.supported,
        localApi = localApi,
        localApiAvailable = mesh.runtime.client.localApiAvailable,
        localApiFingerprint = fingerprint,
        deviceName = if (localApi) known?.name else device?.name,
        state = when {
            !localApi && device == null -> NativeSettingsMeshState.NotPaired
            state is MeshCoreState.Off -> NativeSettingsMeshState.Idle
            // The local API signs at once and then waits for the user's answer in MeshCore Open.
            localApi && state is MeshCoreState.Handshaking -> NativeSettingsMeshState.AwaitingApproval
            state is MeshCoreState.Connecting || state is MeshCoreState.Handshaking -> NativeSettingsMeshState.Connecting
            state is MeshCoreState.ChannelMissing -> NativeSettingsMeshState.ChannelMissing
            state is MeshCoreState.Ready -> NativeSettingsMeshState.Ready
            else -> NativeSettingsMeshState.Problem
        },
        problem = (state as? MeshCoreState.Failed)?.problem?.let(::problemOf),
        firmware = known?.version,
        batteryMillivolts = ready?.batteryMillivolts,
        waitingForRelays = waiting,
        scanning = host.scanning,
    )
}

private fun problemOf(problem: MeshCoreProblem): NativeSettingsMeshProblem = when (problem) {
    MeshCoreProblem.BluetoothOff -> NativeSettingsMeshProblem.BluetoothOff
    MeshCoreProblem.PermissionMissing -> NativeSettingsMeshProblem.PermissionMissing
    MeshCoreProblem.DeviceNotFound -> NativeSettingsMeshProblem.DeviceNotFound
    MeshCoreProblem.ConnectionLost -> NativeSettingsMeshProblem.ConnectionLost
    MeshCoreProblem.Timeout -> NativeSettingsMeshProblem.Timeout
    MeshCoreProblem.ServiceMissing -> NativeSettingsMeshProblem.ServiceMissing
    MeshCoreProblem.IncompatibleFirmware -> NativeSettingsMeshProblem.IncompatibleFirmware
    MeshCoreProblem.ProtocolError -> NativeSettingsMeshProblem.ProtocolError
    MeshCoreProblem.LocalApiUnreachable -> NativeSettingsMeshProblem.LocalApiUnreachable
    MeshCoreProblem.NotApproved -> NativeSettingsMeshProblem.NotApproved
    MeshCoreProblem.LocalApiNoRadio -> NativeSettingsMeshProblem.LocalApiNoRadio
    MeshCoreProblem.LocalApiChannelMissing -> NativeSettingsMeshProblem.LocalApiChannelMissing
    MeshCoreProblem.LocalApiRefused -> NativeSettingsMeshProblem.LocalApiRefused
}

/** Retries publishing what the radio heard while there was no internet, for as long as there is something waiting. */
@Composable
private fun KeepRepublishing(mesh: NativeShellMesh, active: Boolean) {
    LaunchedEffect(mesh, active) {
        while (active) {
            mesh.runtime.bridge.flushRepublish()
            withContext(Dispatchers.IO) { mesh.runtime.persistLedger() }
            delay(REPUBLISH_RETRY_MILLIS)
        }
    }
}

private const val REPUBLISH_RETRY_MILLIS = 2 * 60_000L

/** The two MeshCore dialogs. */
@Composable
fun NativeShellMeshDialogs(host: NativeShellMeshHost?) {
    host ?: return
    if (host.scanVisible) ScanDialog(host)
    if (host.pendingConsent != null) ConsentDialog(host)
}

@Composable
private fun ScanDialog(host: NativeShellMeshHost) {
    AlertDialog(
        onDismissRequest = host::closeScan,
        title = { Text(stringResource(R.string.native_mesh_scan_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (host.scanning) Text(stringResource(R.string.native_mesh_scan_searching))
                if (!host.scanning && host.found.isEmpty()) Text(stringResource(R.string.native_mesh_scan_none))
                Spacer(modifier = Modifier.height(8.dp))
                host.found.forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp)
                            .clickable(role = Role.Button) { host.choose(result) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(result.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${result.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (!host.scanning) {
                TextButton(onClick = host::search) { Text(stringResource(R.string.native_mesh_scan_again)) }
            }
        },
        dismissButton = {
            TextButton(onClick = host::closeScan) { Text(stringResource(R.string.native_mesh_cancel)) }
        },
    )
}

@Composable
private fun ConsentDialog(host: NativeShellMeshHost) {
    AlertDialog(
        onDismissRequest = { host.pendingConsent = null },
        title = { Text(stringResource(R.string.native_mesh_consent_title)) },
        text = {
            Text(
                stringResource(R.string.native_mesh_consent_body),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = host::acceptConsent, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text(stringResource(R.string.native_mesh_consent_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = { host.pendingConsent = null }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text(stringResource(R.string.native_mesh_cancel))
            }
        },
    )
}

/** The switches that turn the radio on or let it transmit: the ones the host manages. */
fun NativeSettingsBooleanKey.isMeshRadioFeature(): Boolean =
    this == NativeSettingsBooleanKey.MeshReceive || this == NativeSettingsBooleanKey.MeshUseLocalApi || this in CONSENT_GATED

/** MeshCore Open, whose local API the radio can be shared through. */
private const val LOCAL_API_PACKAGE = "com.meshcore.meshcore_open"
