package app.roadstr.feature.savedroute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.SheetGrabHandle
import app.roadstr.core.ui.rememberSheetDragState

@Composable
fun NativeSavedRoutesPanel(
    routes: List<NativeSavedRoute>,
    onStart: (NativeSavedRoute) -> Unit,
    onRecalculate: (NativeSavedRoute) -> Unit,
    onEdit: (NativeSavedRoute) -> Unit,
    onRename: (NativeSavedRoute) -> Unit,
    onDelete: (NativeSavedRoute) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.native_saved_routes_title)
    val drag = rememberSheetDragState(onClose)
    Surface(
        modifier = modifier.fillMaxWidth().then(drag.sheet).semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetGrabHandle(drag.handle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.native_nostr_cancel)) }
            }
            if (routes.isEmpty()) {
                Text(
                    text = stringResource(R.string.native_saved_routes_empty),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(routes, key = NativeSavedRoute::id) { route ->
                        SavedRouteRow(route, onStart, onRecalculate, onEdit, onRename, onDelete)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRouteRow(
    route: NativeSavedRoute,
    onStart: (NativeSavedRoute) -> Unit,
    onRecalculate: (NativeSavedRoute) -> Unit,
    onEdit: (NativeSavedRoute) -> Unit,
    onRename: (NativeSavedRoute) -> Unit,
    onDelete: (NativeSavedRoute) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = route.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = route.stops.joinToString(" → ") { it.label },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (route.state == NativeSavedRouteState.Calculated) {
            TextButton(onClick = { onStart(route) }) {
                Text(stringResource(R.string.native_saved_routes_start))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onRecalculate(route) }) {
                Text(stringResource(R.string.native_saved_routes_recalculate))
            }
            TextButton(onClick = { onEdit(route) }) {
                Text(stringResource(R.string.native_saved_routes_edit))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onRename(route) }) {
                Text(stringResource(R.string.native_saved_routes_rename))
            }
            TextButton(onClick = { onDelete(route) }) {
                Text(stringResource(R.string.native_saved_routes_delete))
            }
        }
    }
}
