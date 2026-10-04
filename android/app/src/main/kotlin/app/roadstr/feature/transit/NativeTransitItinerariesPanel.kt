package app.roadstr.feature.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roadstr.R
import app.roadstr.core.network.TransitMode

/** Flutter-parity public-transport panel for the private Compose shell. */
@Composable
fun NativeTransitItinerariesPanel(
    snapshot: NativeTransitUiSnapshot,
    transportMode: NativeTransitTransportMode,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)?,
    onModeChanged: (NativeTransitTransportMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeTransitUiStatus.Hidden) return
    val transitTitle = androidx.compose.ui.res.stringResource(R.string.native_transport_mode_transit)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = snapshot.destinationLabel ?: transitTitle },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
        ) {
            TransitPanelHeader(
                title = snapshot.destinationLabel ?: transitTitle,
                onCancel = onCancel,
            )
            when (snapshot.status) {
                NativeTransitUiStatus.Hidden -> Unit
                NativeTransitUiStatus.Loading -> TransitLoadingState()
                NativeTransitUiStatus.Unavailable -> TransitEmptyState(failed = false, onRetry = null)
                NativeTransitUiStatus.Failure -> TransitEmptyState(failed = true, onRetry = onRetry)
                NativeTransitUiStatus.Ready -> TransitReadyState(snapshot, onSelect)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TransitModeChoices(transportMode, onModeChanged)
        }
    }
}

@Composable
private fun TransitPanelHeader(title: String, onCancel: () -> Unit) {
    val cancel = androidx.compose.ui.res.stringResource(R.string.native_cancel)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "●",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = cancel },
        ) {
            Text("×", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransitLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.sizeIn(maxWidth = 28.dp, maxHeight = 28.dp))
    }
}

@Composable
private fun TransitEmptyState(failed: Boolean, onRetry: (() -> Unit)?) {
    val message = androidx.compose.ui.res.stringResource(
        if (failed) R.string.native_transit_request_failed else R.string.native_transit_no_service_here,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (failed && onRetry != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(R.string.native_transit_retry))
            }
        }
    }
}

@Composable
private fun TransitReadyState(snapshot: NativeTransitUiSnapshot, onSelect: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = snapshot.itineraries,
            key = { index, _ -> "${snapshot.revision}:$index" },
        ) { index, itinerary ->
            TransitItineraryCard(
                itinerary = itinerary,
                selected = snapshot.selectedIndex == index,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun TransitItineraryCard(
    itinerary: NativeTransitItineraryPresentation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val transferLabel = if (itinerary.transfers == 0) {
        androidx.compose.ui.res.stringResource(R.string.native_transit_direct)
    } else {
        androidx.compose.ui.res.stringResource(R.string.native_transit_transfers, itinerary.transfers)
    }
    val description = buildString {
        append(itinerary.durationLabel)
        append(", ")
        append(itinerary.startTimeLabel)
        append(" – ")
        append(itinerary.endTimeLabel)
        append(", ")
        append(transferLabel)
        append(", ")
        append(itinerary.walkingDistanceLabel)
    }
    val shape = RoundedCornerShape(12.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                itinerary.durationLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${itinerary.startTimeLabel} – ${itinerary.endTimeLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                transferLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (itinerary.boardingName != null && itinerary.boardingTimeLabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (itinerary.accessWalkMinutes > 0) {
                    Text(
                        "${itinerary.accessWalkMinutes}\u2009min  →  ",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    itinerary.boardingName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    itinerary.boardingTimeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itinerary.legs.forEachIndexed { index, leg ->
                if (index > 0) {
                    Text(
                        "  ›  ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TransitLegBadge(leg)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${modeMark(TransitMode.Walk)}  ${itinerary.walkingDistanceLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (itinerary.scheduledTimes) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.native_transit_scheduled_times),
                    modifier = Modifier.weight(2f, fill = false),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TransitLegBadge(leg: NativeTransitLegPresentation) {
    val line = leg.line
    if (line == null || leg.backgroundArgb == null || leg.foregroundArgb == null) {
        Text(
            "${modeMark(leg.mode)} ${leg.durationMinutes}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        return
    }
    Surface(
        color = Color(leg.backgroundArgb),
        contentColor = Color(leg.foregroundArgb),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = "${modeMark(leg.mode)}  $line",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransitModeChoices(
    transportMode: NativeTransitTransportMode,
    onModeChanged: (NativeTransitTransportMode) -> Unit,
) {
    val walkingFamily = transportMode == NativeTransitTransportMode.Walking ||
        transportMode == NativeTransitTransportMode.Transit
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModeChip(
            label = androidx.compose.ui.res.stringResource(R.string.native_mode_car),
            selected = transportMode == NativeTransitTransportMode.Driving,
            onClick = { onModeChanged(NativeTransitTransportMode.Driving) },
        )
        ModeChip(
            label = androidx.compose.ui.res.stringResource(R.string.native_mode_bike),
            selected = transportMode == NativeTransitTransportMode.Cycling,
            onClick = { onModeChanged(NativeTransitTransportMode.Cycling) },
        )
        ModeChip(
            label = androidx.compose.ui.res.stringResource(R.string.native_mode_walk),
            selected = walkingFamily,
            onClick = { onModeChanged(NativeTransitTransportMode.Walking) },
        )
    }
    if (walkingFamily) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↳", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModeChip(
                label = androidx.compose.ui.res.stringResource(R.string.native_transport_mode_walk),
                selected = transportMode == NativeTransitTransportMode.Walking,
                onClick = { onModeChanged(NativeTransitTransportMode.Walking) },
            )
            ModeChip(
                label = androidx.compose.ui.res.stringResource(R.string.native_transport_mode_transit),
                selected = transportMode == NativeTransitTransportMode.Transit,
                onClick = { onModeChanged(NativeTransitTransportMode.Transit) },
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.semantics {
            this.selected = selected
            role = Role.Button
        },
    )
}

private fun modeMark(mode: TransitMode): String = when (mode) {
    TransitMode.Walk -> "W"
    TransitMode.Bike -> "B"
    TransitMode.Car -> "C"
    TransitMode.Tram -> "T"
    TransitMode.Subway, TransitMode.Metro -> "M"
    TransitMode.Suburban -> "S"
    TransitMode.RegionalRail, TransitMode.RegionalFastRail, TransitMode.Rail -> "R"
    TransitMode.LongDistance, TransitMode.HighspeedRail, TransitMode.NightRail -> "IC"
    TransitMode.Bus -> "B"
    TransitMode.Coach -> "C"
    TransitMode.Ferry -> "F"
    TransitMode.Airplane -> "A"
    TransitMode.Funicular, TransitMode.AerialLift -> "L"
    TransitMode.OnDemand -> "D"
    TransitMode.Other -> "•"
}
