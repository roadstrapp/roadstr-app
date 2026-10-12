package app.roadstr.feature.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.network.TransitServiceState

@Composable
fun NativeTransitStopBoardPanel(
    snapshot: NativeTransitStopBoardSnapshot,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeTransitStopBoardStatus.Hidden) return
    val title = snapshot.stop?.name ?: androidx.compose.ui.res.stringResource(R.string.native_transit_departures)
    Surface(
        modifier = modifier.fillMaxWidth().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◉", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    snapshot.updatedAtLabel?.let {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.native_transit_updated_at, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onRefresh) { Text("↻") }
                TextButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("×") }
            }
            when (snapshot.status) {
                NativeTransitStopBoardStatus.Hidden -> Unit
                NativeTransitStopBoardStatus.Loading -> Row(
                    Modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                NativeTransitStopBoardStatus.Empty -> BoardMessage(R.string.native_transit_no_departures)
                NativeTransitStopBoardStatus.Failure -> {
                    BoardMessage(R.string.native_transit_request_failed)
                    TextButton(onClick = onRefresh, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.native_transit_retry))
                    }
                }
                NativeTransitStopBoardStatus.Ready -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                ) {
                    items(snapshot.departures, key = NativeTransitDeparturePresentation::stableId) { row ->
                        DepartureRow(row)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardMessage(resource: Int) {
    Text(
        androidx.compose.ui.res.stringResource(resource),
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun DepartureRow(row: NativeTransitDeparturePresentation) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(row.expectedTimeLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (row.expectedTimeLabel != row.scheduledTimeLabel) {
                Text(row.scheduledTimeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.line, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(row.direction, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val detail = buildString {
                row.platform?.let { append("◉ ").append(it) }
                row.delayMinutes?.takeIf { it > 0 }?.let {
                    if (isNotEmpty()) append(" · ")
                    append('+').append(it).append(" min")
                }
                if (row.realtime) {
                    if (isNotEmpty()) append(" · ")
                    append("LIVE")
                }
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.state == TransitServiceState.Cancelled) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.native_transit_cancelled),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            row.alertTitles.forEach { alert ->
                Text("⚠ $alert", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
