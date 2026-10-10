package app.roadstr.feature.offline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import app.roadstr.R
import app.roadstr.service.offline.InstalledOfflinePackage
import app.roadstr.service.offline.OfflineDatasetType
import app.roadstr.service.offline.OfflineMapFormat
import app.roadstr.service.offline.OfflinePackageArtifact
import app.roadstr.service.offline.OfflinePackageManifest
import app.roadstr.service.offline.OfflineInstallOutcome

interface NativeOfflinePackagesGateway {
    fun installed(): List<InstalledOfflinePackage>
    fun availableBytes(): Long
    suspend fun loadManifest(url: String): OfflinePackageManifest
    suspend fun loadProvider(provider: OfflineMapProvider): OfflineProviderCatalog
    suspend fun install(
        artifact: OfflinePackageArtifact,
        allowMobileDataOnce: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): OfflineInstallOutcome
    fun delete(packageId: String): Boolean
}

data class NativeOfflinePackagesSnapshot(
    val installed: List<InstalledOfflinePackage> = emptyList(),
    val manifest: OfflinePackageManifest? = null,
    val manifestUrl: String = "",
    val busyPackageId: String? = null,
    val progress: Double = 0.0,
    val messageResource: Int? = null,
    val mobileConfirmation: OfflinePackageArtifact? = null,
    val availableBytes: Long = 0L,
    val catalogRoot: OfflineCatalogNode = OfflineGeographicCatalog.world(),
    val provider: OfflineMapProvider = OfflineMapProvider.Geofabrik,
    val providerLoadedFromCache: Boolean = false,
)

@Composable
fun NativeOfflinePackagesPanel(
    snapshot: NativeOfflinePackagesSnapshot,
    onManifestUrlChanged: (String) -> Unit,
    onLoadCatalog: () -> Unit,
    onProviderSelected: (OfflineMapProvider) -> Unit,
    onDownload: (OfflinePackageArtifact, Boolean) -> Unit,
    onDownloadSelected: (List<OfflinePackageArtifact>) -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = snapshot.catalogRoot
    var pathIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var advancedVisible by rememberSaveable { mutableStateOf(false) }
    val availableArtifacts = snapshot.manifest?.artifacts.orEmpty().filter {
        it.datasetType == OfflineDatasetType.ValhallaRouting ||
            it.datasetType == OfflineDatasetType.PmTilesMap ||
            it.datasetType == OfflineDatasetType.MbTilesMap
    }
    val currentPath = OfflineGeographicCatalog.resolve(catalog, pathIds)
    val currentNode = currentPath.lastOrNull() ?: catalog
    val searchResults = OfflineGeographicCatalog.search(catalog, searchQuery)
    val selection = OfflineGeographicCatalog.selection(catalog, selectedIds, availableArtifacts, snapshot.installed)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.native_offline_packages_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.native_browser_close)) }
                }
            }
            item { Text(stringResource(R.string.native_offline_downloaded_areas_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item { Text(stringResource(R.string.native_offline_free_space, formatBytes(snapshot.availableBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.native_offline_provider_title), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflineMapProvider.entries.forEach { provider ->
                            if (provider == snapshot.provider) {
                                Button(onClick = { onProviderSelected(provider) }) { Text(provider.displayName) }
                            } else {
                                OutlinedButton(onClick = { onProviderSelected(provider) }) { Text(provider.displayName) }
                            }
                        }
                    }
                    Text(
                        when (snapshot.provider) {
                            OfflineMapProvider.Geofabrik -> stringResource(R.string.native_offline_provider_geofabrik)
                            OfflineMapProvider.VersaTiles -> stringResource(R.string.native_offline_provider_versatiles)
                            OfflineMapProvider.OpenMapTiles -> stringResource(R.string.native_offline_provider_openmaptiles)
                            OfflineMapProvider.RoadstrManifest -> stringResource(R.string.native_offline_provider_manifest)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (snapshot.provider.sourceUrl.isNotBlank()) {
                        Text(snapshot.provider.sourceUrl, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    if (snapshot.providerLoadedFromCache) {
                        Text(stringResource(R.string.native_offline_provider_cached), style = MaterialTheme.typography.labelSmall)
                    }
                    if (snapshot.busyPackageId == PROVIDER_BUSY_ID) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Text(stringResource(R.string.native_offline_provider_loading), Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.native_offline_catalog_search)) },
                    placeholder = { Text(stringResource(R.string.native_offline_catalog_search_hint)) },
                    singleLine = true,
                )
            }
            if (searchQuery.isBlank()) {
                item { Breadcrumbs(currentPath, onRoot = { pathIds = emptyList() }, onSelect = { pathIds = it }) }
                item { Text(if (currentNode == catalog) stringResource(R.string.native_offline_catalog_root) else currentNode.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item(key = "animated-catalog") {
                    AnimatedContent(
                        targetState = pathIds,
                        transitionSpec = {
                            val forward = targetState.size > initialState.size
                            val enter = slideInHorizontally(tween(240)) { width -> if (forward) -width / 3 else width / 3 } + fadeIn(tween(180))
                            val exit = slideOutHorizontally(tween(240)) { width -> if (forward) width / 3 else -width / 3 } + fadeOut(tween(140))
                            enter togetherWith exit
                        },
                        label = "offline-catalog-navigation",
                    ) { targetPath ->
                        val animatedNode = OfflineGeographicCatalog.resolve(catalog, targetPath).lastOrNull() ?: catalog
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            animatedNode.children.forEach { node ->
                                CatalogAreaCard(
                                    node = node,
                                    plan = OfflineGeographicCatalog.plan(node, availableArtifacts, snapshot.installed),
                                    selected = node.id in selectedIds,
                                    busy = snapshot.busyPackageId != null,
                                    onChecked = { checked -> selectedIds = if (checked) selectedIds + node.id else selectedIds - node.id },
                                    onNavigate = { pathIds = targetPath + node.id },
                                    onDownload = { plan -> onDownloadSelected(plan.artifacts) },
                                )
                            }
                        }
                    }
                }
            } else {
                if (searchResults.isEmpty()) {
                    item { Text(stringResource(R.string.native_offline_catalog_no_results)) }
                } else {
                    items(searchResults, key = { "search:${it.node.id}" }) { result ->
                        CatalogAreaCard(
                            node = result.node,
                            plan = OfflineGeographicCatalog.plan(result.node, availableArtifacts, snapshot.installed),
                            selected = result.node.id in selectedIds,
                            busy = snapshot.busyPackageId != null,
                            pathLabel = result.path.drop(1).joinToString(" › ") { it.name },
                            onChecked = { checked -> selectedIds = if (checked) selectedIds + result.node.id else selectedIds - result.node.id },
                            onNavigate = { pathIds = result.path.drop(1).map { it.id }; searchQuery = "" },
                            onDownload = { plan -> onDownloadSelected(plan.artifacts) },
                        )
                    }
                }
            }
            if (selection.artifacts.isNotEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(stringResource(R.string.native_offline_catalog_selected, selectedIds.size), fontWeight = FontWeight.SemiBold)
                            if (selection.downloadBytes > 0L) {
                                Text(stringResource(R.string.native_offline_package_size, formatBytes(selection.downloadBytes), formatBytes(selection.installedBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(stringResource(R.string.native_offline_size_checked_before_download), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onDownloadSelected(selection.artifacts) }, enabled = snapshot.busyPackageId == null && selection.artifacts.isNotEmpty(), modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)) {
                                Text(stringResource(R.string.native_offline_catalog_download_selected))
                            }
                        }
                    }
                }
            }
            snapshot.messageResource?.let { message -> item { Text(stringResource(message), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) } }
            item { HorizontalDivider(); Spacer(Modifier.height(2.dp)) }
            if (snapshot.installed.isEmpty()) {
                item { Text(stringResource(R.string.native_offline_no_installed)) }
            } else {
                item { Text(stringResource(R.string.native_offline_downloaded_areas), fontWeight = FontWeight.SemiBold) }
                items(snapshot.installed, key = { "installed:${it.artifact.id}" }) { value -> PackageCard(value.artifact, true, null) { onDelete(value.artifact.id) } }
            }
            item { TextButton(onClick = { advancedVisible = !advancedVisible }) { Text(stringResource(R.string.native_offline_catalog_advanced)) } }
            if (advancedVisible || snapshot.provider == OfflineMapProvider.RoadstrManifest) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.native_offline_how_it_works_title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.native_offline_how_it_works_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = snapshot.manifestUrl, onValueChange = onManifestUrlChanged, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.native_offline_manifest_url)) }, placeholder = { Text(stringResource(R.string.native_offline_manifest_example)) }, supportingText = { Text(stringResource(R.string.native_offline_manifest_hint)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                            Button(onClick = onLoadCatalog, enabled = snapshot.busyPackageId == null && snapshot.manifestUrl.isNotBlank(), modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)) { Text(stringResource(R.string.native_offline_load_catalog)) }
                            availableArtifacts.forEach { artifact ->
                                val progress = snapshot.progress.takeIf { snapshot.busyPackageId == artifact.id }
                                PackageCard(artifact, false, progress) { if (progress == null) onDownload(artifact, false) else onCancelDownload() }
                            }
                            if (availableArtifacts.isEmpty()) Text(stringResource(R.string.native_offline_catalog_unavailable))
                        }
                    }
                }
            }
            snapshot.mobileConfirmation?.let { artifact ->
                item { OutlinedButton(onClick = { onDownload(artifact, true) }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)) { Text(stringResource(R.string.native_offline_mobile_once)) } }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: List<OfflineCatalogNode>, onRoot: () -> Unit, onSelect: (List<String>) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onRoot) { Text(stringResource(R.string.native_offline_catalog_root)) }
        path.forEachIndexed { index, node ->
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onSelect(path.take(index + 1).map { it.id }) }) { Text(node.name) }
        }
    }
}

@Composable
private fun CatalogAreaCard(
    node: OfflineCatalogNode,
    plan: OfflineCatalogPlan,
    selected: Boolean,
    busy: Boolean,
    pathLabel: String? = null,
    onChecked: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    onDownload: (OfflineCatalogPlan) -> Unit,
) {
    val selectable = plan.artifacts.isNotEmpty() && plan.availability != OfflineCatalogAvailability.Installed
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = onChecked, enabled = !busy && selectable)
            Column(Modifier.weight(1f).clickable(onClick = onNavigate).padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(node.name, fontWeight = FontWeight.SemiBold)
                pathLabel?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                val status = when (plan.availability) {
                    OfflineCatalogAvailability.Available -> R.string.native_offline_catalog_available
                    OfflineCatalogAvailability.Installed -> R.string.native_offline_installed
                    OfflineCatalogAvailability.Partial -> R.string.native_offline_catalog_partial
                    OfflineCatalogAvailability.Unavailable -> R.string.native_offline_catalog_not_available
                }
                Text(stringResource(status), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                val downloadBytes = plan.artifacts.sumOf { it.sizeBytes }
                if (downloadBytes > 0L) Text(stringResource(R.string.native_offline_package_size, formatBytes(downloadBytes), formatBytes(plan.artifacts.sumOf { it.installedSizeBytes })), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                else if (plan.artifacts.isNotEmpty()) Text(stringResource(R.string.native_offline_size_checked_before_download), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onDownload(plan) }, enabled = !busy && selectable, modifier = Modifier.sizeIn(minWidth = 48.dp)) { Text("↓") }
        }
    }
}

@Composable
private fun PackageCard(artifact: OfflinePackageArtifact, installed: Boolean, progress: Double?, onAction: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(artifact.label?.takeIf(String::isNotBlank) ?: artifact.id, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (artifact.datasetType == OfflineDatasetType.PmTilesMap || artifact.datasetType == OfflineDatasetType.MbTilesMap) Text(stringResource(if (artifact.mapFormat == OfflineMapFormat.Raster) R.string.native_offline_map_raster else R.string.native_offline_map_shortbread), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            if (artifact.sizeBytes > 0L) Text(stringResource(R.string.native_offline_package_size, formatBytes(artifact.sizeBytes), formatBytes(artifact.installedSizeBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(artifact.license, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(artifact.attribution, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.size(28.dp)); Text("${(progress * 100).toInt()}%", Modifier.padding(start = 12.dp)) }
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_offline_cancel_download)) }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (installed) R.string.native_saved_routes_delete else R.string.native_offline_download)) }
            }
        }
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1_000_000_000L -> "%.2f GB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.1f MB".format(value / 1_000_000.0)
    else -> "${value / 1_000} KB"
}

private const val PROVIDER_BUSY_ID = "provider-catalog"
