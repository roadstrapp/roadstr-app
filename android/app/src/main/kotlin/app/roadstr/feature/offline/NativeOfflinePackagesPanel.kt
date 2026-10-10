package app.roadstr.feature.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.service.offline.InstalledOfflinePackage
import app.roadstr.service.offline.OfflinePackageArtifact
import app.roadstr.service.offline.OfflinePackageManifest
import app.roadstr.service.offline.OfflineInstallOutcome

interface NativeOfflinePackagesGateway {
    fun installed(): List<InstalledOfflinePackage>
    fun availableBytes(): Long
    suspend fun loadManifest(url: String): OfflinePackageManifest
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
)

@Composable
fun NativeOfflinePackagesPanel(
    snapshot: NativeOfflinePackagesSnapshot,
    onManifestUrlChanged: (String) -> Unit,
    onLoadCatalog: () -> Unit,
    onDownload: (OfflinePackageArtifact, Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.native_offline_packages_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onClose) { Text(stringResource(R.string.native_browser_close)) }
                }
            }
            item {
                Text(
                    stringResource(R.string.native_offline_downloaded_areas_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text(
                    stringResource(
                        R.string.native_offline_free_space,
                        formatBytes(snapshot.availableBytes),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = snapshot.manifestUrl,
                    onValueChange = onManifestUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.native_offline_manifest_url)) },
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = onLoadCatalog,
                    enabled = snapshot.busyPackageId == null && snapshot.manifestUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                ) { Text(stringResource(R.string.native_offline_load_catalog)) }
            }
            snapshot.messageResource?.let { message ->
                item {
                    Text(
                        stringResource(message),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (snapshot.installed.isEmpty()) {
                item { Text(stringResource(R.string.native_offline_no_installed)) }
            } else {
                items(snapshot.installed, key = { "installed:${it.artifact.id}" }) { value ->
                    PackageCard(value.artifact, true, null, { onDelete(value.artifact.id) })
                }
            }
            item { HorizontalDivider(); Spacer(Modifier.height(2.dp)) }
            val available = snapshot.manifest?.artifacts.orEmpty()
            if (available.isEmpty()) {
                item { Text(stringResource(R.string.native_offline_catalog_unavailable)) }
            } else {
                items(available, key = { "catalog:${it.id}:${it.version}" }) { artifact ->
                    val progress = snapshot.progress.takeIf { snapshot.busyPackageId == artifact.id }
                    PackageCard(artifact, false, progress) {
                        if (progress == null) onDownload(artifact, false) else onCancelDownload()
                    }
                }
            }
            snapshot.mobileConfirmation?.let { artifact ->
                item {
                    OutlinedButton(
                        onClick = { onDownload(artifact, true) },
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                    ) { Text(stringResource(R.string.native_offline_mobile_once)) }
                }
            }
        }
    }
}

@Composable
private fun PackageCard(
    artifact: OfflinePackageArtifact,
    installed: Boolean,
    progress: Double?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(artifact.id, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.native_offline_package_size,
                    formatBytes(artifact.sizeBytes),
                    formatBytes(artifact.installedSizeBytes),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                artifact.license,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                artifact.attribution,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = { progress.toFloat() })
                    Text("${(progress * 100).toInt()}%", modifier = Modifier.padding(start = 12.dp))
                }
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_offline_cancel_download))
                }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (installed) R.string.native_saved_routes_delete else R.string.native_offline_download,
                        ),
                    )
                }
            }
        }
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1_000_000_000L -> "%.2f GB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.1f MB".format(value / 1_000_000.0)
    else -> "${value / 1_000} KB"
}
