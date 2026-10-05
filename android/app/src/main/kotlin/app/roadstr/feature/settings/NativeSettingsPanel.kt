package app.roadstr.feature.settings

import android.graphics.BitmapFactory
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import app.roadstr.core.ui.RoadstrSwitch
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.navigation.NativeSpeedometerStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface NativeSettingsUiAction {
    data object Close : NativeSettingsUiAction
    data class BooleanChanged(val key: NativeSettingsBooleanKey, val value: Boolean) :
        NativeSettingsUiAction
    data class ThemeChanged(val value: RoadstrThemeId) : NativeSettingsUiAction
    data class LanguageChanged(val value: String?) : NativeSettingsUiAction
    data class MapEngineChanged(val value: NativeSettingsMapEngine) : NativeSettingsUiAction
    data class BrightnessChanged(val value: Double) : NativeSettingsUiAction
    data class TileUrlChanged(val value: String) : NativeSettingsUiAction
    data class RoutingProviderChanged(val value: NativeSettingsRoutingProvider) :
        NativeSettingsUiAction
    data class GraphHopperServerChanged(val value: String) : NativeSettingsUiAction
    data object ConfigureRoutingKey : NativeSettingsUiAction
    data object TestGraphHopper : NativeSettingsUiAction
    data class SpeedometerChanged(val value: NativeSpeedometerStyle) : NativeSettingsUiAction
    data class CursorStyleChanged(val value: NativeSettingsCursorStyle) : NativeSettingsUiAction
    data class CursorColorChanged(val value: NativeSettingsCursorColor) : NativeSettingsUiAction
    data class SearchEngineChanged(val value: NativeSettingsSearchEngine) : NativeSettingsUiAction
    data object ConfigureNwc : NativeSettingsUiAction
    data object OpenSavedPlaces : NativeSettingsUiAction
    data object OpenWebSearch : NativeSettingsUiAction
    data object ExportFavorites : NativeSettingsUiAction
    data object ImportFavorites : NativeSettingsUiAction
    data object SyncPush : NativeSettingsUiAction
    data object SyncPull : NativeSettingsUiAction
    data object EditSyncPassphrase : NativeSettingsUiAction
    data object EditSyncRelay : NativeSettingsUiAction
    data object DownloadVoiceModel : NativeSettingsUiAction
    data class VoiceGenderChanged(val value: NativeSettingsVoiceGender) : NativeSettingsUiAction
    data class VoiceSpeedChanged(val stage: Int) : NativeSettingsUiAction
    data class VoiceVolumeChanged(val value: Double) : NativeSettingsUiAction
    data object OpenMapsAttribution : NativeSettingsUiAction
    data object OpenSource : NativeSettingsUiAction
    data object SupportRoadstr : NativeSettingsUiAction
}

/** Complete dormant settings catalogue; storage, secrets, network and intents remain external. */
@Composable
fun NativeSettingsPanel(
    snapshot: NativeSettingsSnapshot,
    onAction: (Long, NativeSettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether web results are switched on and pointed at an instance; the web screen holds the rest. */
    webSearchActive: Boolean = false,
) {
    if (snapshot.status == NativeSettingsStatus.Hidden) return
    val title = stringResource(R.string.native_settings_title)
    val revision = snapshot.revision
    val values = snapshot.values
    // Open from the start: the crosswalk and traffic-light switches sit behind
    // this card, and a closed card is just a row titled "Road element overlays"
    // that nobody connects with them.
    var overlaysExpanded by remember { mutableStateOf(true) }
    // Decode the cursor thumbnails while the panel opens, off the main thread,
    // so expanding the cursor menu later never stalls its own animation.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { CursorPreviewCache.preload(appContext) }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            item { SettingsHeader(title) { onAction(revision, NativeSettingsUiAction.Close) } }
            item { ThemeSection(values) { onAction(revision, it) } }
            item { LanguageSection(values) { onAction(revision, it) } }
            item { VisibilitySection(values) { onAction(revision, it) } }
            item {
                MapSection(
                    values = values,
                    overlaysExpanded = overlaysExpanded,
                    onToggleOverlays = { overlaysExpanded = !overlaysExpanded },
                    onAction = { onAction(revision, it) },
                )
            }
            item { AppearanceSection(values) { onAction(revision, it) } }
            item { SearchSection(values, webSearchActive) { onAction(revision, it) } }
            item { LightningSection(values) { onAction(revision, it) } }
            item { FavoritesSection(values) { onAction(revision, it) } }
            item { SyncSection(values) { onAction(revision, it) } }
            item { VoiceSection(values) { onAction(revision, it) } }
            item { InfoSection(values) { onAction(revision, it) } }
            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }
}

@Composable
internal fun SettingsHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.native_settings_close),
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ThemeSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_theme_section) {
        ThemeChoiceRow(
            selected = values.themeId,
            onSelected = { onAction(NativeSettingsUiAction.ThemeChanged(it)) },
        )
        ToggleRow(
            R.string.native_settings_auto_dark,
            R.string.native_settings_auto_dark_desc,
            values.autoDarkEnabled,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.AutoDark, it)) }
        ToggleRow(
            R.string.native_settings_dark_map,
            R.string.native_settings_dark_map_desc,
            values.darkMapEnabled,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.DarkMap, it)) }
    }
}

@Composable
private fun ThemeChoiceRow(
    selected: RoadstrThemeId,
    onSelected: (RoadstrThemeId) -> Unit,
) {
    var expanded by remember(selected) { mutableStateOf(false) }
    SettingsCard(
        modifier = Modifier
            .clickable { expanded = !expanded }
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.native_settings_theme_section),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
            )
            ThemeDot(selected)
            Text(
                themeLabel(selected),
                modifier = Modifier.padding(start = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.primary)
        }
        ExpandSection(expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RoadstrThemeId.entries.forEach { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(value)
                                expanded = false
                            }
                            .sizeIn(minHeight = 48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeDot(value)
                        Text(
                            themeLabel(value),
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        if (value == selected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeDot(value: RoadstrThemeId) {
    val label = themeLabel(value)
    Surface(
        modifier = Modifier
            .size(20.dp)
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = if (value.dark) Color(0xFF161B22) else Color.White,
        border = BorderStroke(3.dp, Color(value.accentArgb.toInt())),
    ) {}
}

@Composable
private fun LanguageSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_language_section) {
        ChoiceRow(
            label = stringResource(R.string.native_settings_language_section),
            values = LanguageChoices,
            selected = LanguageChoices.firstOrNull { it.first == values.languageCode },
            labelFor = {
                if (it.first == null) {
                    stringResource(R.string.native_settings_language_system)
                } else {
                    it.second
                }
            },
            keyFor = { it.first ?: "system" },
            selectedWhen = { it.first == values.languageCode },
            onSelected = { onAction(NativeSettingsUiAction.LanguageChanged(it.first)) },
        )
    }
}

@Composable
private fun VisibilitySection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_visibility_section) {
        ToggleRow(
            title = if (values.profilePublic) {
                R.string.native_settings_visibility_clear
            } else {
                R.string.native_settings_visibility_pseudonymous
            },
            subtitle = R.string.native_settings_visibility_desc,
            checked = values.profilePublic,
        ) {
            onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.ProfilePublic, it))
        }
    }
}

@Composable
private fun MapSection(
    values: NativeSettingsInput,
    overlaysExpanded: Boolean,
    onToggleOverlays: () -> Unit,
    onAction: (NativeSettingsUiAction) -> Unit,
) {
    SettingsSection(R.string.native_settings_map_section) {
        ToggleRow(
            R.string.native_settings_avoid_unpaved,
            R.string.native_settings_avoid_unpaved_desc,
            values.avoidUnpavedRoads,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.AvoidUnpavedRoads, it)) }
        ChoiceRow(
            label = stringResource(R.string.native_settings_map_engine),
            values = NativeSettingsMapEngine.entries,
            selected = values.mapEngine,
            labelFor = { if (it == NativeSettingsMapEngine.MapLibre) "MapLibre" else "OpenStreetMap" },
            onSelected = { onAction(NativeSettingsUiAction.MapEngineChanged(it)) },
        )
        ToggleRow(
            R.string.native_settings_keep_screen_on,
            R.string.native_settings_keep_screen_on_desc,
            values.keepScreenOn,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.KeepScreenOn, it)) }
        ToggleRow(
            R.string.native_settings_keep_screen_always,
            R.string.native_settings_keep_screen_always_desc,
            values.keepScreenOnAlways,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.KeepScreenOnAlways, it)) }
        SliderRow(
            title = stringResource(R.string.native_settings_min_brightness),
            description = stringResource(R.string.native_settings_min_brightness_desc),
            value = values.minimumBrightness,
            valueLabel = if (values.minimumBrightness == 0.0) {
                stringResource(R.string.native_settings_min_brightness_off)
            } else {
                "${(values.minimumBrightness * 100).toInt()}%"
            },
            range = 0f..1f,
            steps = 9,
        ) { onAction(NativeSettingsUiAction.BrightnessChanged(it.toDouble())) }
        ToggleRow(
            R.string.native_settings_show_altitude,
            R.string.native_settings_show_altitude_desc,
            values.showAltitude,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.ShowAltitude, it)) }
        ExpandableCard(
            title = stringResource(R.string.native_settings_road_overlays),
            expanded = overlaysExpanded,
            onClick = onToggleOverlays,
        ) {
            ToggleRow(
                R.string.native_settings_crosswalks,
                R.string.native_settings_crosswalks_desc,
                values.showCrosswalks,
            ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.ShowCrosswalks, it)) }
            ToggleRow(
                R.string.native_settings_traffic_lights,
                R.string.native_settings_traffic_lights_desc,
                values.showTrafficLights,
            ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.ShowTrafficLights, it)) }
        }
        ToggleRow(
            R.string.native_settings_auto_center,
            R.string.native_settings_auto_center_desc,
            values.autoCenterOnLaunch,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.AutoCenterOnLaunch, it)) }
        ToggleRow(
            R.string.native_settings_imperial,
            R.string.native_settings_imperial_desc,
            values.imperialUnits,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.ImperialUnits, it)) }
        SettingsTextField(
            revisionValue = values.mapTileUrl,
            label = stringResource(R.string.native_settings_tile_url),
            onSubmit = { onAction(NativeSettingsUiAction.TileUrlChanged(it)) },
        )
        ChoiceRow(
            label = stringResource(R.string.native_settings_routing_provider),
            values = NativeSettingsRoutingProvider.entries,
            selected = values.routingProvider,
            labelFor = { routingProviderLabel(it) },
            onSelected = { onAction(NativeSettingsUiAction.RoutingProviderChanged(it)) },
        )
        if (values.routingProvider == NativeSettingsRoutingProvider.GraphHopperSelfHosted) {
            SettingsTextField(
                revisionValue = values.graphHopperServer,
                label = stringResource(R.string.native_settings_gh_server_hint),
                onSubmit = { onAction(NativeSettingsUiAction.GraphHopperServerChanged(it)) },
            )
            ActionButton(stringResource(R.string.native_settings_verify)) {
                onAction(NativeSettingsUiAction.TestGraphHopper)
            }
        }
        if (
            values.routingProvider == NativeSettingsRoutingProvider.GraphHopperCloud ||
            values.routingProvider == NativeSettingsRoutingProvider.OpenRouteService
        ) {
            SecureConfigurationRow(
                title = stringResource(R.string.native_settings_api_key_hint),
                configured = values.routingApiKeyConfigured,
            ) { onAction(NativeSettingsUiAction.ConfigureRoutingKey) }
        }
    }
}

@Composable
private fun AppearanceSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_appearance_section) {
        ChoiceRow(
            label = stringResource(R.string.native_settings_speedometer),
            values = NativeSpeedometerStyle.entries,
            selected = values.speedometerStyle,
            labelFor = { speedometerLabel(it) },
            preview = { SpeedometerChoicePreview(it) },
            onSelected = { onAction(NativeSettingsUiAction.SpeedometerChanged(it)) },
        )
        ChoiceRow(
            label = stringResource(R.string.native_settings_cursor_vehicle),
            values = NativeSettingsCursorStyle.entries,
            selected = values.cursorStyle,
            labelFor = { cursorStyleLabel(it) },
            preview = { CursorChoicePreview(it, cursorChoiceColor(values.cursorColor)) },
            onSelected = { onAction(NativeSettingsUiAction.CursorStyleChanged(it)) },
        )
        ChoiceRow(
            label = stringResource(R.string.native_settings_cursor_color),
            values = NativeSettingsCursorColor.entries,
            selected = values.cursorColor,
            labelFor = { cursorColorLabel(it) },
            preview = { CursorColorPreview(cursorChoiceColor(it)) },
            onSelected = { onAction(NativeSettingsUiAction.CursorColorChanged(it)) },
        )
    }
}

@Composable
private fun SearchSection(
    values: NativeSettingsInput,
    webSearchActive: Boolean,
    onAction: (NativeSettingsUiAction) -> Unit,
) {
    SettingsSection(R.string.native_settings_web_search_section) {
        ChoiceRow(
            label = stringResource(R.string.native_settings_web_search_section),
            values = NativeSettingsSearchEngine.entries,
            selected = values.searchEngine,
            labelFor = { searchEngineName(it) },
            onSelected = { onAction(NativeSettingsUiAction.SearchEngineChanged(it)) },
        )
        Text(
            searchEngineDescription(values.searchEngine),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecureConfigurationRow(
            title = stringResource(R.string.native_settings_web_results),
            configured = webSearchActive,
            description = stringResource(R.string.native_settings_web_results_desc),
        ) { onAction(NativeSettingsUiAction.OpenWebSearch) }
    }
}

@Composable
private fun LightningSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_lightning_section) {
        SecureConfigurationRow(
            title = stringResource(R.string.native_settings_nwc),
            configured = values.nwcConfigured,
            description = stringResource(R.string.native_settings_nwc_desc),
        ) { onAction(NativeSettingsUiAction.ConfigureNwc) }
    }
}

@Composable
private fun FavoritesSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_favorites_section) {
        ActionButton(stringResource(R.string.native_settings_add_favorite)) {
            onAction(NativeSettingsUiAction.OpenSavedPlaces)
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onAction(NativeSettingsUiAction.ExportFavorites) },
                enabled = values.favoritesCount > 0,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp),
            ) { Text(stringResource(R.string.native_settings_export_favorites)) }
            OutlinedButton(
                onClick = { onAction(NativeSettingsUiAction.ImportFavorites) },
                modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp),
            ) { Text(stringResource(R.string.native_settings_import_favorites)) }
        }
    }
}

@Composable
private fun SyncSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_sync_section) {
        Text(
            stringResource(R.string.native_settings_sync_desc),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
            R.string.native_settings_sync_auto,
            R.string.native_settings_sync_auto_desc,
            values.favoritesSyncAutoEnabled,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.FavoritesSyncAuto, it)) }
        if (!values.syncIdentityAvailable) {
            Text(
                stringResource(R.string.native_settings_sync_no_identity),
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = stringResource(R.string.native_settings_sync_push),
                    enabled = !values.syncBusy,
                    modifier = Modifier.weight(1f),
                ) { onAction(NativeSettingsUiAction.SyncPush) }
                ActionButton(
                    text = stringResource(R.string.native_settings_sync_pull),
                    enabled = !values.syncBusy,
                    modifier = Modifier.weight(1f),
                ) { onAction(NativeSettingsUiAction.SyncPull) }
            }
            SecureConfigurationRow(
                title = stringResource(R.string.native_settings_sync_passphrase),
                configured = values.syncPassphraseConfigured,
            ) { onAction(NativeSettingsUiAction.EditSyncPassphrase) }
            ActionRow(
                title = stringResource(R.string.native_settings_sync_relay),
                value = values.customSyncRelay,
            ) { onAction(NativeSettingsUiAction.EditSyncRelay) }
            if (values.syncBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_voice_section) {
        ToggleRow(
            R.string.native_settings_voice_guidance,
            R.string.native_settings_voice_guidance_desc,
            values.voiceEnabled,
        ) { onAction(NativeSettingsUiAction.BooleanChanged(NativeSettingsBooleanKey.VoiceEnabled, it)) }
        SettingsCard {
            Text(stringResource(R.string.native_settings_voice_model), fontWeight = FontWeight.SemiBold)
            Text(
                voiceStatusLabel(values.voiceModelStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (values.voiceModelStatus == NativeSettingsVoiceModelStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { values.voiceDownloadProgress.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text("${(values.voiceDownloadProgress * 100).toInt()}%")
            }
            Text(
                stringResource(R.string.native_settings_voice_languages),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (values.voiceModelStatus != NativeSettingsVoiceModelStatus.Downloading &&
                values.voiceModelStatus != NativeSettingsVoiceModelStatus.Ready
            ) {
                ActionButton(stringResource(R.string.native_settings_voice_download)) {
                    onAction(NativeSettingsUiAction.DownloadVoiceModel)
                }
            }
            if (values.voiceModelStatus == NativeSettingsVoiceModelStatus.Ready) {
                ChoiceRow(
                    label = stringResource(R.string.native_settings_voice_gender),
                    values = NativeSettingsVoiceGender.entries,
                    selected = values.voiceGender,
                    labelFor = {
                        stringResource(
                            if (it == NativeSettingsVoiceGender.Female) {
                                R.string.native_settings_voice_female
                            } else {
                                R.string.native_settings_voice_male
                            },
                        )
                    },
                    onSelected = { onAction(NativeSettingsUiAction.VoiceGenderChanged(it)) },
                )
                if (!values.voiceGenderChoiceAvailable) {
                    Text(
                        stringResource(R.string.native_settings_voice_gender_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SliderRow(
                    title = stringResource(R.string.native_settings_voice_speed),
                    description = "",
                    value = values.voiceSpeedStage.toDouble(),
                    valueLabel = "${NativeSettingsPresenter.voiceSpeed(values.voiceSpeedStage)}×",
                    range = 0f..(NativeSettingsPresenter.VOICE_SPEED_STAGES.size - 1).toFloat(),
                    steps = NativeSettingsPresenter.VOICE_SPEED_STAGES.size - 2,
                ) { onAction(NativeSettingsUiAction.VoiceSpeedChanged(it.toInt())) }
                SliderRow(
                    title = stringResource(R.string.native_settings_voice_volume),
                    description = "",
                    value = values.voiceVolume,
                    valueLabel = "${(values.voiceVolume * 100).toInt()}%",
                    range = 0.2f..1f,
                    steps = 7,
                ) { onAction(NativeSettingsUiAction.VoiceVolumeChanged(it.toDouble())) }
            }
        }
    }
}

@Composable
private fun InfoSection(values: NativeSettingsInput, onAction: (NativeSettingsUiAction) -> Unit) {
    SettingsSection(R.string.native_settings_info_section) {
        InfoRow(stringResource(R.string.native_settings_info_version), values.appVersion)
        InfoRow(stringResource(R.string.native_settings_info_protocol), "Nostr")
        InfoRow(stringResource(R.string.native_settings_info_maps), "openstreetmap.org") {
            onAction(NativeSettingsUiAction.OpenMapsAttribution)
        }
        InfoRow(
            stringResource(R.string.native_settings_info_routing),
            routingInfoLabel(values.routingProvider),
        )
        InfoRow(stringResource(R.string.native_settings_info_source), "github.com/roadstrapp/roadstr-app") {
            onAction(NativeSettingsUiAction.OpenSource)
        }
        ActionButton(
            text = stringResource(R.string.native_settings_support),
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        ) {
            onAction(NativeSettingsUiAction.SupportRoadstr)
        }
    }
}

@Composable
internal fun SettingsSection(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(
            text = stringResource(title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
internal fun ToggleRow(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RoadstrSwitch(checked = checked, onCheckedChange = onChanged)
        }
    }
}

@Composable
internal fun <T> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T?,
    labelFor: @Composable (T) -> String,
    preview: (@Composable (T) -> Unit)? = null,
    onSelected: (T) -> Unit,
    keyFor: (T) -> String = { it.toString() },
    selectedWhen: (T) -> Boolean = { it == selected },
) {
    var expanded by remember(label, selected) { mutableStateOf(false) }
    val selectedValue = values.firstOrNull(selectedWhen)
    val selectedLabel = if (selectedValue != null) labelFor(selectedValue) else label
    SettingsCard(
        modifier = Modifier
            .clickable { expanded = !expanded }
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            selectedValue?.let { value ->
                preview?.invoke(value)
                if (preview != null) Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                selectedLabel,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.primary)
        }
        ExpandSection(expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                values.forEach { value ->
                    FilterChip(
                        selected = selectedWhen(value),
                        onClick = { onSelected(value); expanded = false },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                preview?.invoke(value)
                                if (preview != null) Spacer(modifier = Modifier.width(10.dp))
                                Text(labelFor(value))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedometerChoicePreview(style: NativeSpeedometerStyle) {
    val accent = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                val progress = .42f
                when (style) {
                    NativeSpeedometerStyle.Classic -> {
                        drawArc(inactive, 135f, 270f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(accent, 135f, 270f * progress, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    NativeSpeedometerStyle.Digital -> {
                        drawLine(inactive, Offset(size.width * .16f, size.height * .25f), Offset(size.width * .84f, size.height * .25f), 1.dp.toPx())
                        drawLine(accent, Offset(size.width * .16f, size.height * .82f), Offset(size.width * .46f, size.height * .82f), 2.dp.toPx(), StrokeCap.Round)
                    }
                    NativeSpeedometerStyle.Analog -> {
                        val radius = size.minDimension * .42f
                        for (index in 0..10) {
                            val angle = (135.0 + 270.0 * index / 10.0) * PI / 180.0
                            val outer = Offset(
                                (center.x + radius * cos(angle)).toFloat(),
                                (center.y + radius * sin(angle)).toFloat(),
                            )
                            val inner = Offset(
                                (center.x + radius * .77f * cos(angle)).toFloat(),
                                (center.y + radius * .77f * sin(angle)).toFloat(),
                            )
                            drawLine(if (index <= 4) accent else inactive, inner, outer, 1.dp.toPx())
                        }
                        val needle = (135.0 + 270.0 * progress) * PI / 180.0
                        drawLine(
                            accent,
                            center,
                            Offset(
                                (center.x + radius * .65f * cos(needle)).toFloat(),
                                (center.y + radius * .65f * sin(needle)).toFloat(),
                            ),
                            1.5.dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                    NativeSpeedometerStyle.Sport -> repeat(14) { index ->
                        drawArc(
                            if (index < 6) accent else inactive,
                            130f + 280f * index / 14f,
                            10f,
                            false,
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    NativeSpeedometerStyle.Minimal -> {
                        drawArc(inactive, 117f, 306f, false, style = Stroke(1.dp.toPx()))
                        drawArc(accent, 117f, 306f * progress, false, style = Stroke(1.dp.toPx()))
                    }
                }
            }
            Text("42", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CursorChoicePreview(style: NativeSettingsCursorStyle, color: Color) {
    if (style != NativeSettingsCursorStyle.Arrow) {
        val context = LocalContext.current.applicationContext
        val bitmap by produceState(CursorPreviewCache.peek(style), style) {
            value = CursorPreviewCache.load(context, style)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.Fit,
                colorFilter = cursorHueFilter(color),
            )
        }
        return
    }
    Canvas(modifier = Modifier.size(width = 30.dp, height = 38.dp)) {
        when (style) {
            NativeSettingsCursorStyle.Arrow -> {
                val path = Path().apply {
                    moveTo(size.width / 2, size.height * .08f)
                    lineTo(size.width * .82f, size.height * .78f)
                    lineTo(size.width / 2, size.height * .62f)
                    lineTo(size.width * .18f, size.height * .78f)
                    close()
                }
                drawPath(path, color)
            }
            else -> Unit
        }
    }
}

/** Thumbnails of the cursor skins, decoded once per process and kept small. */
private object CursorPreviewCache {
    private const val TARGET_EDGE_PIXELS = 160
    private val cache = java.util.concurrent.ConcurrentHashMap<NativeSettingsCursorStyle, ImageBitmap>()

    fun peek(style: NativeSettingsCursorStyle): ImageBitmap? = cache[style]

    suspend fun load(context: Context, style: NativeSettingsCursorStyle): ImageBitmap? {
        cache[style]?.let { return it }
        val decoded = withContext(Dispatchers.IO) { decode(context, style) } ?: return null
        cache[style] = decoded
        return decoded
    }

    suspend fun preload(context: Context) {
        NativeSettingsCursorStyle.entries
            .filter { it != NativeSettingsCursorStyle.Arrow }
            .forEach { load(context, it) }
    }

    private fun decode(context: Context, style: NativeSettingsCursorStyle): ImageBitmap? = runCatching {
        val name = "cursors/${style.assetFileName()}"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(name).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_EDGE_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.assets.open(name).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }.getOrNull()
}

private fun NativeSettingsCursorStyle.assetFileName(): String = when (this) {
    NativeSettingsCursorStyle.Formula1 -> "formula1.png"
    NativeSettingsCursorStyle.Suv -> "suv.png"
    NativeSettingsCursorStyle.Racing -> "racing.png"
    NativeSettingsCursorStyle.Electric -> "electric.png"
    NativeSettingsCursorStyle.City -> "city.png"
    NativeSettingsCursorStyle.Classic500 -> "classic500.png"
    NativeSettingsCursorStyle.Arrow -> error("The arrow preview is vector-backed")
}

private fun cursorHueFilter(color: Color): ColorFilter? {
    val degrees = when (color) {
        Color(0xFF5856D6) -> 8.0
        Color(0xFF0A84FF) -> -30.0
        Color(0xFF34C759) -> -150.0
        Color(0xFFFFCC00) -> 150.0
        Color(0xFFFF9500) -> 120.0
        Color(0xFFFF3B30) -> 90.0
        else -> return null
    }
    val radians = degrees * PI / 180.0
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
                .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
                .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

@Composable
private fun CursorColorPreview(color: Color) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {}
}

private fun cursorChoiceColor(value: NativeSettingsCursorColor): Color = Color(
    when (value) {
        NativeSettingsCursorColor.Violet -> 0xFF8B3DFF
        NativeSettingsCursorColor.Indigo -> 0xFF5856D6
        NativeSettingsCursorColor.Blue -> 0xFF0A84FF
        NativeSettingsCursorColor.Green -> 0xFF34C759
        NativeSettingsCursorColor.Yellow -> 0xFFFFCC00
        NativeSettingsCursorColor.Orange -> 0xFFFF9500
        NativeSettingsCursorColor.Red -> 0xFFFF3B30
    },
)

@Composable
private fun SliderRow(
    title: String,
    description: String,
    value: Double,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChanged: (Float) -> Unit,
) {
    SettingsCard {
        Row {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        if (description.isNotEmpty()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onChanged,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun SettingsTextField(revisionValue: String, label: String, onSubmit: (String) -> Unit) {
    var value by remember(revisionValue) { mutableStateOf(revisionValue) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(NativeSettingsPresenter.MAX_URL_CHARS) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onSubmit(value)
            focusManager.moveFocus(FocusDirection.Down)
        }),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
internal fun SecureConfigurationRow(
    title: String,
    configured: Boolean,
    description: String? = null,
    onClick: () -> Unit,
) {
    SettingsCard(
        modifier = Modifier.clickable(onClick = onClick).semantics { role = Role.Button },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (configured) "●" else "○", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .sizeIn(minHeight = 48.dp).semantics { role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "⌃" else "⌄")
        }
        ExpandSection(expanded, content)
    }
}

@Composable
private fun ActionRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .sizeIn(minHeight = 48.dp).padding(horizontal = 12.dp)
            .semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        value?.let {
            Text(
                it,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(" ›")
    }
}

@Composable
internal fun ActionButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
    ) { Text(text) }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClick = onClick).semantics { role = Role.Button }
    }
    Row(
        modifier = modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider()
}

/**
 * Reveals a collapsed block with a short expand-and-fade instead of letting it
 * pop in. It is the only thing animating the card's height: stacking a second
 * size animation on the card made the two fight and the section stutter.
 */
@Composable
private fun ExpandSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(durationMillis = 240, easing = FastOutSlowInEasing)) +
            fadeIn(tween(durationMillis = 180)),
        exit = shrinkVertically(tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
            fadeOut(tween(durationMillis = 120)),
    ) {
        // AnimatedVisibility stacks its children on top of each other, so a
        // section with several rows (the road overlays: crosswalks and traffic
        // lights) drew them all in the same place and only the last one showed.
        Column { content() }
    }
}

@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.onSurface.luminance() >
        MaterialTheme.colorScheme.surface.luminance()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (dark) Color(0xFF1A1A2E) else Color(0xFFE8E8ED),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (dark) 0.8f else 0.9f),
        ),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun themeLabel(value: RoadstrThemeId): String = stringResource(
    when (value) {
        RoadstrThemeId.LightNostr -> R.string.native_settings_theme_light_nostr
        RoadstrThemeId.LightBitcoin -> R.string.native_settings_theme_light_bitcoin
        RoadstrThemeId.DarkNostr -> R.string.native_settings_theme_dark_nostr
        RoadstrThemeId.DarkBitcoin -> R.string.native_settings_theme_dark_bitcoin
    },
)

@Composable
private fun routingProviderLabel(value: NativeSettingsRoutingProvider): String = stringResource(
    when (value) {
        NativeSettingsRoutingProvider.Osrm -> R.string.native_settings_provider_osrm
        NativeSettingsRoutingProvider.GraphHopperSelfHosted -> R.string.native_settings_provider_gh_local
        NativeSettingsRoutingProvider.GraphHopperCloud -> R.string.native_settings_provider_gh_cloud
        NativeSettingsRoutingProvider.OpenRouteService -> R.string.native_settings_provider_openroute
    },
)

@Composable
private fun routingInfoLabel(value: NativeSettingsRoutingProvider): String = stringResource(
    when (value) {
        NativeSettingsRoutingProvider.Osrm -> R.string.native_settings_info_osrm
        NativeSettingsRoutingProvider.GraphHopperSelfHosted -> R.string.native_settings_info_gh_local
        NativeSettingsRoutingProvider.GraphHopperCloud -> R.string.native_settings_info_gh_cloud
        NativeSettingsRoutingProvider.OpenRouteService -> R.string.native_settings_info_openroute
    },
)

@Composable
private fun speedometerLabel(value: NativeSpeedometerStyle): String = stringResource(
    when (value) {
        NativeSpeedometerStyle.Classic -> R.string.native_settings_speedometer_classic
        NativeSpeedometerStyle.Digital -> R.string.native_settings_speedometer_digital
        NativeSpeedometerStyle.Analog -> R.string.native_settings_speedometer_analog
        NativeSpeedometerStyle.Sport -> R.string.native_settings_speedometer_sport
        NativeSpeedometerStyle.Minimal -> R.string.native_settings_speedometer_minimal
    },
)

@Composable
private fun cursorStyleLabel(value: NativeSettingsCursorStyle): String = stringResource(
    when (value) {
        NativeSettingsCursorStyle.Arrow -> R.string.native_settings_cursor_standard
        NativeSettingsCursorStyle.Formula1 -> R.string.native_settings_cursor_formula1
        NativeSettingsCursorStyle.Suv -> R.string.native_settings_cursor_suv
        NativeSettingsCursorStyle.Racing -> R.string.native_settings_cursor_racing
        NativeSettingsCursorStyle.Electric -> R.string.native_settings_cursor_electric
        NativeSettingsCursorStyle.City -> R.string.native_settings_cursor_city
        NativeSettingsCursorStyle.Classic500 -> R.string.native_settings_cursor_classic500
    },
)

@Composable
private fun cursorColorLabel(value: NativeSettingsCursorColor): String = stringResource(
    when (value) {
        NativeSettingsCursorColor.Violet -> R.string.native_settings_color_violet
        NativeSettingsCursorColor.Indigo -> R.string.native_settings_color_indigo
        NativeSettingsCursorColor.Blue -> R.string.native_settings_color_blue
        NativeSettingsCursorColor.Green -> R.string.native_settings_color_green
        NativeSettingsCursorColor.Yellow -> R.string.native_settings_color_yellow
        NativeSettingsCursorColor.Orange -> R.string.native_settings_color_orange
        NativeSettingsCursorColor.Red -> R.string.native_settings_color_red
    },
)

private fun searchEngineName(value: NativeSettingsSearchEngine): String = when (value) {
    NativeSettingsSearchEngine.Qwant -> "Qwant"
    NativeSettingsSearchEngine.Brave -> "Brave Search"
    NativeSettingsSearchEngine.DuckDuckGo -> "DuckDuckGo"
    NativeSettingsSearchEngine.Startpage -> "Startpage"
    NativeSettingsSearchEngine.Google -> "Google"
}

@Composable
private fun searchEngineDescription(value: NativeSettingsSearchEngine): String = stringResource(
    when (value) {
        NativeSettingsSearchEngine.Qwant -> R.string.native_settings_search_qwant_desc
        NativeSettingsSearchEngine.Brave -> R.string.native_settings_search_brave_desc
        NativeSettingsSearchEngine.DuckDuckGo -> R.string.native_settings_search_ddg_desc
        NativeSettingsSearchEngine.Startpage -> R.string.native_settings_search_startpage_desc
        NativeSettingsSearchEngine.Google -> R.string.native_settings_search_google_desc
    },
)

@Composable
private fun voiceStatusLabel(value: NativeSettingsVoiceModelStatus): String = stringResource(
    when (value) {
        NativeSettingsVoiceModelStatus.Ready -> R.string.native_settings_voice_ready
        NativeSettingsVoiceModelStatus.Downloading -> R.string.native_settings_voice_downloading
        NativeSettingsVoiceModelStatus.Unknown,
        NativeSettingsVoiceModelStatus.NotDownloaded,
        -> R.string.native_settings_voice_not_downloaded
    },
)

private val LanguageChoices = listOf(
    null to "System",
    "bg" to "Български", "cs" to "Čeština", "da" to "Dansk", "de" to "Deutsch",
    "el" to "Ελληνικά", "en" to "English", "es" to "Español", "et" to "Eesti",
    "fi" to "Suomi", "fr" to "Français", "ga" to "Gaeilge", "hr" to "Hrvatski",
    "hu" to "Magyar", "it" to "Italiano", "ja" to "日本語", "lt" to "Lietuvių",
    "lv" to "Latviešu", "mt" to "Malti", "nl" to "Nederlands", "pl" to "Polski",
    "pt" to "Português", "ro" to "Română", "ru" to "Русский", "sk" to "Slovenčina",
    "sl" to "Slovenščina", "sv" to "Svenska", "zh" to "中文",
)
