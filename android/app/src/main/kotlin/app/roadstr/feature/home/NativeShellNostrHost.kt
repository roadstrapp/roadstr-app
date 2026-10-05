package app.roadstr.feature.home

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.roadstr.R
import app.roadstr.core.protocol.nostr.CustomRelayPolicy
import app.roadstr.core.ui.RoadstrSwitch
import app.roadstr.feature.map.NativeMapPointOverlayKind
import app.roadstr.feature.map.NativeMapPointOverlayMarker
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.report.NativeRoadEventPresenter
import app.roadstr.feature.report.NativeRoadEventSession
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.settings.NativeSettingsSession
import app.roadstr.service.nostr.NativeRoadEvent
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** A one-field question: a passphrase, a relay address, an export password. */
class NativeShellPrompt(
    @StringRes val title: Int,
    @StringRes val description: Int? = null,
    @StringRes val hint: Int,
    val secret: Boolean,
    val initial: String = "",
    @StringRes val confirmLabel: Int,
    /** When set, a switch (on by default) decides whether the field is asked at all. */
    @StringRes val toggleLabel: Int? = null,
    /** When set, an extra action that answers with an empty string. */
    @StringRes val removeLabel: Int? = null,
    val validate: (String) -> Boolean = { true },
    @StringRes val invalidText: Int? = null,
    val numeric: Boolean = false,
    /** null = cancelled; "" = remove, or "no encryption" when the switch is off. */
    val onResult: (String?) -> Unit,
)

/** Marker id prefix for community reports on the overlay. */
private const val ROAD_MARKER_PREFIX = "road-"

/**
 * Wires the Nostr-backed features into the screen: live road reports and the
 * connection that carries them, reporting and voting, favourites sync, and the
 * privacy choice. It owns no widgets of its own except the small prompts.
 */
class NativeShellNostrHost internal constructor(
    val nostr: NativeShellNostr,
    val events: List<NativeRoadEvent>,
    val reports: NativeShellReportController,
    val favorites: NativeShellFavoritesController,
    val zap: NativeShellZapController,
    private val activity: NativeShellActivityController,
    /** Unread entries of the activity inbox, for the badge on the home bar. */
    val activityUnread: Int,
    private val promptState: androidx.compose.runtime.MutableState<NativeShellPrompt?>,
    private val toast: (NativeShellMessage, Int) -> Unit,
    private val publishVisibility: (Boolean) -> Unit,
    private val refreshSettings: () -> Unit,
    private val autoSyncEnabled: () -> Boolean,
) {
    val prompt: NativeShellPrompt? get() = promptState.value

    val markers: List<NativeMapPointOverlayMarker> = events.map { event ->
        NativeMapPointOverlayMarker(
            id = ROAD_MARKER_PREFIX + event.id,
            point = NativeMapPoint(event.latitude, event.longitude),
            kind = NativeRoadEventPresenter.markerKind(event.category),
        )
    }

    fun eventForMarker(markerId: String): NativeRoadEvent? =
        if (markerId.startsWith(ROAD_MARKER_PREFIX)) {
            events.firstOrNull { it.id == markerId.removePrefix(ROAD_MARKER_PREFIX) }
        } else {
            null
        }

    fun message(message: NativeShellMessage, count: Int = 0) = toast(message, count)

    /** The stored inbox the activity panel opens with. */
    fun inboxFor(pubkey: String): String? = activity.stored(pubkey)

    /** The inbox panel changed the inbox (marked all read): keep it and the badge in step. */
    fun inboxChanged(write: app.roadstr.feature.activity.NativeActivityInboxWrite) = activity.persist(write)

    /** The user flipped the pseudonymous/clear switch (settings or profile). */
    fun visibilityChanged(isPublic: Boolean) = publishVisibility(isPublic)

    fun openReport(point: NativeMapPoint) = reports.openComposer(point)

    fun dismissPrompt() {
        val current = promptState.value ?: return
        promptState.value = null
        current.onResult(null)
    }

    fun confirmPrompt(value: String) {
        val current = promptState.value ?: return
        promptState.value = null
        current.onResult(value)
    }

    /** A local favourite changed: push in the background if the user opted in. */
    fun favoritesChangedLocally() {
        if (autoSyncEnabled()) favorites.autoPush()
    }

    fun push() = favorites.push()

    fun pull() = favorites.pull()

    fun requestImport() = nostr.files.requestImport()

    fun exportFavorites() {
        promptState.value = NativeShellPrompt(
            title = R.string.native_nostr_export_title,
            description = R.string.native_nostr_export_desc,
            hint = R.string.native_nostr_password_hint,
            secret = true,
            confirmLabel = R.string.native_nostr_export_button,
            toggleLabel = R.string.native_nostr_export_encrypt,
            onResult = { result -> if (result != null) favorites.export(result.ifEmpty { null }) },
        )
    }

    /** Asks for a speed limit, in the units the driver sees, and files it for [event]. */
    fun promptSpeedLimit(event: NativeRoadEvent, imperial: Boolean) {
        val owner = nostr.signer.pubkeyHex == event.pubkey
        promptState.value = NativeShellPrompt(
            title = if (owner) R.string.native_nostr_edit_speed_title else R.string.native_nostr_request_speed_title,
            hint = R.string.native_nostr_speed_hint,
            secret = false,
            numeric = true,
            confirmLabel = R.string.native_nostr_ok,
            validate = { value -> value.toIntOrNull()?.let { it in 1..300 } == true },
            onResult = { result ->
                result?.toIntOrNull()?.let { entered ->
                    val kmh = if (imperial) kotlin.math.round(entered * 1.60934).toInt() else entered
                    reports.editSpeedLimit(event, kmh)
                }
            },
        )
    }

    fun editSyncPassphrase() {
        promptState.value = NativeShellPrompt(
            title = R.string.native_nostr_sync_passphrase_title,
            description = R.string.native_nostr_sync_passphrase_desc,
            hint = R.string.native_nostr_password_hint,
            secret = true,
            confirmLabel = R.string.native_nostr_ok,
            removeLabel = if (nostr.syncSecrets.passphraseConfigured()) R.string.native_settings_nwc_remove else null,
            onResult = { result ->
                if (result != null && nostr.syncSecrets.setPassphrase(result)) refreshSettings()
            },
        )
    }

    fun editSyncRelay() {
        val current = nostr.syncSecrets.customRelay()
        promptState.value = NativeShellPrompt(
            title = R.string.native_nostr_relay_title,
            description = R.string.native_nostr_relay_desc,
            hint = R.string.native_nostr_relay_hint,
            secret = false,
            initial = current.orEmpty(),
            confirmLabel = R.string.native_nostr_ok,
            removeLabel = if (current != null) R.string.native_settings_nwc_remove else null,
            validate = { CustomRelayPolicy.normalise(it) != null },
            invalidText = R.string.native_nostr_relay_invalid,
            onResult = { result ->
                if (result != null && nostr.syncSecrets.setCustomRelay(result)) refreshSettings()
            },
        )
    }
}

private fun messageText(message: NativeShellMessage): Int = when (message) {
    NativeShellMessage.SyncSuccess -> R.string.native_nostr_sync_success
    NativeShellMessage.SyncFailed -> R.string.native_nostr_sync_failed
    NativeShellMessage.SyncNotFound -> R.string.native_nostr_sync_not_found
    NativeShellMessage.SyncNothingToPublish -> R.string.native_nostr_sync_empty
    NativeShellMessage.ExportSuccess -> R.string.native_nostr_export_success
    NativeShellMessage.ExportFailed -> R.string.native_nostr_export_failed
    NativeShellMessage.ImportSuccess -> R.string.native_nostr_import_success
    NativeShellMessage.ImportFailed -> R.string.native_nostr_import_failed
    NativeShellMessage.ReportPublished -> R.string.native_nostr_report_published
    NativeShellMessage.ReportQueued -> R.string.native_nostr_report_queued
    NativeShellMessage.LoginRequired -> R.string.native_nostr_login_to_report
    NativeShellMessage.VoteLoginRequired -> R.string.native_nostr_login_to_vote
    NativeShellMessage.VisibilityFailed -> R.string.native_nostr_visibility_error
    NativeShellMessage.SpeedUpdateSent -> R.string.native_nostr_speed_saved
    NativeShellMessage.EditRequestSent -> R.string.native_nostr_edit_request_sent
    NativeShellMessage.ZapSent -> R.string.native_zap_sent
    NativeShellMessage.ReportFailed,
    NativeShellMessage.SigningFailed,
    NativeShellMessage.VoteFailed,
    NativeShellMessage.SpeedUpdateFailed,
    -> R.string.native_nostr_action_failed
}

/**
 * Creates the host, or returns null when the app was started without Nostr
 * support. Starts and stops the area connection with the screen: the
 * subscription tells a relay roughly where the driver is, so it is only open
 * while the app is in view.
 */
@Composable
fun rememberNativeShellNostrHost(
    nostr: NativeShellNostr?,
    settingsSession: NativeSettingsSession,
    roadEventSession: NativeRoadEventSession,
    identityPubkey: String?,
    favorites: List<NativeSavedPlace>,
    onMergeFavorites: (List<NativeSavedPlace>) -> Unit,
    currentPoint: () -> NativeMapPoint?,
): NativeShellNostrHost? {
    if (nostr == null) return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val promptState = remember { mutableStateOf<NativeShellPrompt?>(null) }
    val latestFavorites by rememberUpdatedState(favorites)
    val latestMerge by rememberUpdatedState(onMergeFavorites)
    val latestPoint by rememberUpdatedState(currentPoint)
    val latestContext by rememberUpdatedState(context)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val toast: (NativeShellMessage, Int) -> Unit = remember(nostr) {
        { message, count ->
            // Controllers call from whichever thread finished the work.
            mainHandler.post {
                val text = latestContext.getString(messageText(message), count)
                Toast.makeText(latestContext, text, Toast.LENGTH_LONG).show()
            }
        }
    }
    val refreshSettings: () -> Unit = remember(nostr) {
        {
            val values = settingsSession.state.value.values
            settingsSession.refresh(
                settingsSession.state.value.revision,
                values.copy(
                    syncPassphraseConfigured = nostr.syncSecrets.passphraseConfigured(),
                    customSyncRelay = nostr.syncSecrets.customRelay(),
                    lastSyncMillis = nostr.syncSecrets.lastSyncMillis(),
                ),
            )
        }
    }
    val favoritesController = remember(nostr) {
        NativeShellFavoritesController(
            nostr = nostr,
            scope = scope,
            nowMillis = System::currentTimeMillis,
            message = { message, count ->
                toast(message, count)
                if (message == NativeShellMessage.SyncSuccess) refreshSettings()
            },
            setBusy = { busy ->
                val values = settingsSession.state.value.values
                settingsSession.refresh(settingsSession.state.value.revision, values.copy(syncBusy = busy))
            },
            favorites = { latestFavorites },
            mergeFavorites = { incoming -> mainHandler.post { latestMerge(incoming) } },
            promptPassword = {
                suspendCancellableCoroutine { continuation ->
                    mainHandler.post {
                        promptState.value = NativeShellPrompt(
                            title = R.string.native_nostr_sync_passphrase_title,
                            description = R.string.native_nostr_import_password,
                            hint = R.string.native_nostr_password_hint,
                            secret = true,
                            confirmLabel = R.string.native_nostr_ok,
                            onResult = { value -> if (continuation.isActive) continuation.resume(value) },
                        )
                    }
                    continuation.invokeOnCancellation { mainHandler.post { promptState.value = null } }
                }
            },
        )
    }
    val reportController = remember(nostr, roadEventSession) {
        NativeShellReportController(
            nostr = nostr,
            scope = scope,
            session = roadEventSession,
            message = toast,
        )
    }
    val zapController = remember(nostr, reportController) {
        NativeShellZapController(
            nostr = nostr,
            scope = scope,
            onPaid = reportController::zapPaid,
            message = toast,
        )
    }
    var activityUnread by remember(nostr) { mutableIntStateOf(0) }
    val activityController = remember(nostr) {
        NativeShellActivityController(nostr) { unread -> activityUnread = unread }
    }

    // Open the area connection only while the app is on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(nostr, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> nostr.roadEvents.connect()
                Lifecycle.Event.ON_STOP -> nostr.roadEvents.disconnect()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            nostr.roadEvents.connect()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            nostr.roadEvents.disconnect()
        }
    }
    // The service only re-subscribes when the driver crosses into a new coarse
    // cell, so asking every few seconds costs nothing.
    LaunchedEffect(nostr) {
        while (true) {
            latestPoint()?.let { nostr.roadEvents.subscribeArea(it.latitude, it.longitude) }
            delay(AREA_POLL_MILLIS)
        }
    }
    LaunchedEffect(nostr) {
        nostr.files.imports.collect { text -> favoritesController.import(text) }
    }
    // The inbox is silent, so a check at launch and every few minutes while the
    // map is on screen is as good as a standing subscription, and keeps no
    // socket open.
    LaunchedEffect(nostr, identityPubkey, lifecycleOwner) {
        activityController.load(identityPubkey)
        if (identityPubkey == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                activityController.poll()
                delay(ACTIVITY_POLL_MILLIS)
            }
        }
    }
    // Restore at launch when the user opted in; never removes a local place.
    LaunchedEffect(nostr, identityPubkey) {
        if (identityPubkey != null && settingsSession.state.value.values.favoritesSyncAutoEnabled) {
            favoritesController.autoPull()
        }
    }

    val events by nostr.roadEvents.events.collectAsState()
    return remember(nostr, events, favoritesController, reportController, zapController, activityUnread) {
        NativeShellNostrHost(
            nostr = nostr,
            events = events,
            reports = reportController,
            favorites = favoritesController,
            zap = zapController,
            activity = activityController,
            activityUnread = activityUnread,
            promptState = promptState,
            toast = toast,
            publishVisibility = { isPublic ->
                if (nostr.signer.pubkeyHex != null) {
                    scope.launch {
                        if (!nostr.visibility.publish(isPublic)) toast(NativeShellMessage.VisibilityFailed, 0)
                    }
                }
            },
            refreshSettings = refreshSettings,
            autoSyncEnabled = { settingsSession.state.value.values.favoritesSyncAutoEnabled },
        )
    }
}

private const val AREA_POLL_MILLIS = 5_000L
private const val ACTIVITY_POLL_MILLIS = 180_000L

/** Renders the pending [NativeShellPrompt], if any. */
@Composable
fun NativeShellNostrDialogs(host: NativeShellNostrHost?) {
    host ?: return
    host.zap.state.value?.let { NativeShellZapDialog(host.zap, it) }
    val prompt = host.prompt ?: return
    NativeShellPromptDialog(prompt, onConfirm = host::confirmPrompt, onDismiss = host::dismissPrompt)
}

/** The one-field question dialog, usable by any part of the shell. */
@Composable
fun NativeShellPromptDialog(
    prompt: NativeShellPrompt,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(prompt) { mutableStateOf(prompt.initial) }
    var encrypt by remember(prompt) { mutableStateOf(true) }
    val asksForText = prompt.toggleLabel == null || encrypt
    val valid = if (asksForText) {
        if (prompt.toggleLabel != null) text.isNotEmpty() else prompt.validate(text) && text.isNotBlank()
    } else {
        true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(prompt.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                prompt.description?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                prompt.toggleLabel?.let { label ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), modifier = Modifier.weight(1f))
                        RoadstrSwitch(checked = encrypt, onCheckedChange = { encrypt = it })
                    }
                }
                if (asksForText) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(MAX_PROMPT_CHARS) },
                        label = { Text(stringResource(prompt.hint)) },
                        singleLine = true,
                        isError = prompt.invalidText != null && text.isNotEmpty() && !valid,
                        visualTransformation = if (prompt.secret) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = when {
                                prompt.secret -> KeyboardType.Password
                                prompt.numeric -> KeyboardType.Number
                                else -> KeyboardType.Uri
                            },
                        ),
                    )
                    if (prompt.invalidText != null && text.isNotEmpty() && !valid) {
                        Text(
                            stringResource(prompt.invalidText),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(if (asksForText) text.trim() else "") },
                enabled = valid,
            ) { Text(stringResource(prompt.confirmLabel)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                prompt.removeLabel?.let { label ->
                    TextButton(onClick = { onConfirm("") }) { Text(stringResource(label)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.native_nostr_cancel)) }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

private const val MAX_PROMPT_CHARS = 256

private val ZAP_PRESETS = listOf(21, 100, 500, 1_000, 5_000, 21_000)
private val ZAP_ORANGE = androidx.compose.ui.graphics.Color(0xFFF7931A)
private const val MAX_ZAP_SATS = 100_000_000

/** Amount picker and progress of one zap, as in the Flutter sheet. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NativeShellZapDialog(controller: NativeShellZapController, ui: NativeShellZapUi) {
    var selected by remember(ui.event.id) { mutableStateOf<Int?>(null) }
    var custom by remember(ui.event.id) { mutableStateOf("") }
    val amount = selected ?: custom.toIntOrNull()?.takeIf { it in 1..MAX_ZAP_SATS } ?: 0
    AlertDialog(
        onDismissRequest = controller::dismiss,
        title = { Text("⚡ " + stringResource(R.string.native_zap_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.native_zap_choose_amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZAP_PRESETS.forEach { sats ->
                        val on = selected == sats
                        androidx.compose.material3.FilterChip(
                            selected = on,
                            onClick = {
                                selected = if (on) null else sats
                                if (!on) custom = ""
                            },
                            enabled = !ui.sending,
                            label = { Text("$sats ⚡") },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZAP_ORANGE.copy(alpha = 0.18f),
                                selectedLabelColor = ZAP_ORANGE,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { value ->
                        custom = value.filter(Char::isDigit).take(9)
                        selected = null
                    },
                    label = { Text(stringResource(R.string.native_zap_custom_amount)) },
                    suffix = { Text("sat") },
                    singleLine = true,
                    enabled = !ui.sending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ui.status?.let { status ->
                    Text(
                        stringResource(status.text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { controller.send(amount) },
                enabled = !ui.sending && amount > 0,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ZAP_ORANGE),
            ) {
                Text(
                    if (ui.sending) {
                        stringResource(R.string.native_zap_sending)
                    } else {
                        stringResource(R.string.native_zap_send_button, amount)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = controller::dismiss, enabled = !ui.sending) {
                Text(stringResource(R.string.native_nostr_cancel))
            }
        },
    )
}
