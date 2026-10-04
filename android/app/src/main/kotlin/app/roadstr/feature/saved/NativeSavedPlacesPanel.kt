package app.roadstr.feature.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R

/** Settings-like saved-places sheet packaged dormant in the private shell. */
@Composable
fun NativeSavedPlacesPanel(
    snapshot: NativeSavedPlacesSnapshot,
    onAdd: () -> Unit,
    onEdit: (Int, NativeSavedPlace) -> Unit,
    onDelete: (Int, NativeSavedPlace) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onNavigateParking: (NativeParkingPosition) -> Unit,
    onRemoveParking: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeSavedPlacesStatus.Hidden) return
    val title = stringResource(R.string.native_saved_title)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.82f)
                .semantics { paneTitle = title },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                androidx.compose.material3.MaterialTheme.colorScheme.outline,
            ),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                item {
                    SavedPlacesHeader(title = title, onClose = onClose)
                }
                snapshot.lastImportedCount?.let { count ->
                    item {
                        Text(
                            text = stringResource(R.string.native_saved_imported, count),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                itemsIndexed(
                    items = snapshot.favorites,
                    key = { index, place -> "$index:${place.label}" },
                ) { index, place ->
                    SavedPlaceRow(
                        place = place,
                        onEdit = { onEdit(index, place) },
                        onDelete = { onDelete(index, place) },
                    )
                    HorizontalDivider()
                }
                item {
                    SavedPlacesActions(
                        hasFavorites = snapshot.favorites.isNotEmpty(),
                        onAdd = onAdd,
                        onExport = onExport,
                        onImport = onImport,
                    )
                }
                snapshot.parking?.let { parking ->
                    item {
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                        ParkingCard(
                            parking = parking,
                            onNavigate = { onNavigateParking(parking) },
                            onRemove = onRemoveParking,
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(10.dp)) }
            }
        }
    }
}

@Composable
private fun SavedPlacesHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Text(stringResource(R.string.native_saved_close))
        }
    }
}

@Composable
private fun SavedPlaceRow(
    place: NativeSavedPlace,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val description = listOf(place.label, place.address).filter(String::isNotEmpty).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.sizeIn(minWidth = 36.dp, minHeight = 36.dp),
            shape = RoundedCornerShape(9.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = "♥",
                modifier = Modifier.padding(8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            if (place.address.isNotEmpty()) {
                Text(
                    text = place.address,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = place.label },
        ) {
            Text("×", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SavedPlacesActions(
    hasFavorites: Boolean,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        SavedAction(text = stringResource(R.string.native_saved_add), onClick = onAdd)
        if (hasFavorites) {
            SavedAction(text = stringResource(R.string.native_saved_export), onClick = onExport)
        }
        SavedAction(text = stringResource(R.string.native_saved_import), onClick = onImport)
    }
}

@Composable
private fun SavedAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
    ) {
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ParkingCard(
    parking: NativeParkingPosition,
    onNavigate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.native_saved_parking_title),
            modifier = Modifier.semantics { heading() },
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "%.5f, %.5f".format(
                java.util.Locale.ROOT,
                parking.point.latitude,
                parking.point.longitude,
            ),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onNavigate,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.native_saved_parking_navigate))
            }
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.native_saved_parking_remove))
            }
        }
    }
}
