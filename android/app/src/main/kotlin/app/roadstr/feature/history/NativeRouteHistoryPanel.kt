package app.roadstr.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.SheetGrabHandle
import app.roadstr.core.ui.rememberSheetDragState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Rows visible before the list scrolls; keeps the sheet from cluttering the home. */
const val NATIVE_ROUTE_HISTORY_VISIBLE_ROWS = 5

private val RowHeight = 60.dp

/**
 * The Activity sheet: the routes started recently, newest first. Tapping a row
 * behaves like tapping that place on the map; the cross removes one row and
 * the button at the top right empties the list.
 */
@Composable
fun NativeRouteHistoryPanel(
    entries: List<NativeRouteHistoryEntry>,
    onSelect: (NativeRouteHistoryEntry) -> Unit,
    onRemove: (NativeRouteHistoryEntry) -> Unit,
    onClear: () -> Unit,
    onOpenSavedRoutes: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.native_home_activity)
    val drag = rememberSheetDragState(onClose)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(drag.sheet)
            .semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetGrabHandle(drag.handle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = onOpenSavedRoutes,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) { Text(stringResource(R.string.native_saved_routes_title)) }
                TextButton(
                    onClick = onClear,
                    enabled = entries.isNotEmpty(),
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) { Text(stringResource(R.string.native_history_clear)) }
            }
            if (entries.isEmpty()) {
                EmptyHistory()
            } else {
                HistoryRows(entries, onSelect, onRemove)
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.native_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.native_history_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRows(
    entries: List<NativeRouteHistoryEntry>,
    onSelect: (NativeRouteHistoryEntry) -> Unit,
    onRemove: (NativeRouteHistoryEntry) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = RowHeight * NATIVE_ROUTE_HISTORY_VISIBLE_ROWS),
    ) {
        items(entries, key = { "${it.startedAtEpochMillis}:${it.point.latitude}:${it.point.longitude}" }) { entry ->
            HistoryRow(
                entry = entry,
                whenText = remember(entry.startedAtEpochMillis, formatter) {
                    formatter.format(Instant.ofEpochMilli(entry.startedAtEpochMillis).atZone(ZoneId.systemDefault()))
                },
                onSelect = { onSelect(entry) },
                onRemove = { onRemove(entry) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HistoryRow(
    entry: NativeRouteHistoryEntry,
    whenText: String,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val remove = stringResource(R.string.native_history_remove)
    Row(
        modifier = Modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .height(RowHeight)
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(start = 20.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = whenText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = remove },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.size(width = 8.dp, height = 1.dp))
    }
}
