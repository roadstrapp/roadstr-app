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
    onOpenAttribution: (String) -> Unit,
    onStart: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    showModeChoices: Boolean = true,
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
                NativeTransitUiStatus.Ready -> {
                    TransitReadyState(snapshot, onSelect)
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.native_transit_privacy_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (snapshot.selectedIndex != null && onStart != null) {
                        Row(modifier = Modifier.align(Alignment.End)) {
                            if (onSave != null) {
                                TextButton(onClick = onSave) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.native_saved_transit_save))
                                }
                            }
                            TextButton(onClick = onStart) {
                                Text(androidx.compose.ui.res.stringResource(R.string.native_route_start))
                            }
                        }
                    }
                }
            }
            val attributionUrl = snapshot.attributionUrl
            val providerName = snapshot.providerName
            if (attributionUrl != null && providerName != null) {
                TextButton(
                    onClick = { onOpenAttribution(attributionUrl) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("$providerName · sources ↗", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (showModeChoices) {
                Spacer(modifier = Modifier.height(10.dp))
                TransitModeChoices(transportMode, onModeChanged)
            }
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
            .heightIn(max = 420.dp),
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
        if (selected) {
            Spacer(modifier = Modifier.height(10.dp))
            itinerary.legs.forEachIndexed { index, leg ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                TransitLegDetail(leg)
            }
        }
    }
}

@Composable
private fun TransitLegDetail(leg: NativeTransitLegPresentation) {
    val category = transitCategoryLabel(leg.category)
    val title = if (leg.mode.isTransit) {
        buildString {
            append(category)
            leg.line?.let { append("  ·  ").append(it) }
        }
    } else {
        "$category · ${leg.durationMinutes} min"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(modeMark(leg.mode), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (leg.cancelled) {
                Text("⚠", color = MaterialTheme.colorScheme.error)
            }
        }
        val endpoints = listOfNotNull(leg.fromName, leg.toName).joinToString("  →  ")
        if (endpoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(endpoints, style = MaterialTheme.typography.bodySmall)
        }
        leg.serviceName?.let { service ->
            Text(
                service,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        leg.direction?.let { direction ->
            Text(
                "→ $direction",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val timing = buildString {
            append(leg.startTimeLabel).append(" – ").append(leg.endTimeLabel)
            leg.platform?.let { append("  ·  ◉ ").append(it) }
            leg.delayMinutes?.takeIf { it != 0L }?.let {
                append("  ·  ").append(if (it > 0) "+" else "").append(it).append(" min")
            }
            if (leg.realtime) append("  ·  LIVE")
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            timing,
            color = if (leg.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        leg.operatorName?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        if (leg.intermediateStopNames.isNotEmpty()) {
            Text(
                leg.intermediateStopNames.take(4).joinToString("  ·  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        leg.alertTitles.forEach { alert ->
            Text(
                "⚠ $alert",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun transitCategoryLabel(category: NativeTransitModeCategory): String =
    androidx.compose.ui.res.stringResource(category.stringResourceId())

internal fun NativeTransitModeCategory.stringResourceId(): Int = when (this) {
    NativeTransitModeCategory.Walk -> R.string.native_transit_category_walk
    NativeTransitModeCategory.Bike -> R.string.native_transit_category_bike
    NativeTransitModeCategory.Car -> R.string.native_transit_category_car
    NativeTransitModeCategory.SharedRide -> R.string.native_transit_category_shared_ride
    NativeTransitModeCategory.Tram -> R.string.native_transit_category_tram
    NativeTransitModeCategory.Metro -> R.string.native_transit_category_metro
    NativeTransitModeCategory.SuburbanRail -> R.string.native_transit_category_suburban_rail
    NativeTransitModeCategory.RegionalRail -> R.string.native_transit_category_regional_rail
    NativeTransitModeCategory.RegionalFastRail -> R.string.native_transit_category_regional_fast_rail
    NativeTransitModeCategory.LongDistanceRail -> R.string.native_transit_category_long_distance_rail
    NativeTransitModeCategory.HighSpeedRail -> R.string.native_transit_category_high_speed_rail
    NativeTransitModeCategory.NightRail -> R.string.native_transit_category_night_rail
    NativeTransitModeCategory.Rail -> R.string.native_transit_category_rail
    NativeTransitModeCategory.Bus -> R.string.native_transit_category_bus
    NativeTransitModeCategory.Coach -> R.string.native_transit_category_coach
    NativeTransitModeCategory.Ferry -> R.string.native_transit_category_ferry
    NativeTransitModeCategory.Air -> R.string.native_transit_category_air
    NativeTransitModeCategory.Funicular -> R.string.native_transit_category_funicular
    NativeTransitModeCategory.CableTransport -> R.string.native_transit_category_cable
    NativeTransitModeCategory.OnDemand -> R.string.native_transit_category_on_demand
    NativeTransitModeCategory.Other -> R.string.native_transit_category_other
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModeChip(
                label = androidx.compose.ui.res.stringResource(R.string.native_mode_car),
                selected = transportMode == NativeTransitTransportMode.Driving,
                onClick = { onModeChanged(NativeTransitTransportMode.Driving) },
                modifier = Modifier.weight(1f),
            )
            ModeChip(
                label = androidx.compose.ui.res.stringResource(R.string.native_mode_bike),
                selected = transportMode == NativeTransitTransportMode.Cycling,
                onClick = { onModeChanged(NativeTransitTransportMode.Cycling) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModeChip(
                label = androidx.compose.ui.res.stringResource(R.string.native_mode_walk),
                selected = transportMode == NativeTransitTransportMode.Walking,
                onClick = { onModeChanged(NativeTransitTransportMode.Walking) },
                modifier = Modifier.weight(1f),
            )
            ModeChip(
                label = androidx.compose.ui.res.stringResource(R.string.native_transport_mode_transit),
                selected = transportMode == NativeTransitTransportMode.Transit,
                onClick = { onModeChanged(NativeTransitTransportMode.Transit) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.semantics {
            this.selected = selected
            role = Role.Button
        },
    )
}

private fun modeMark(mode: TransitMode): String = when (mode) {
    TransitMode.Walk -> "W"
    TransitMode.Bike -> "B"
    TransitMode.Rental -> "↗"
    TransitMode.Car -> "C"
    TransitMode.HeavyGoodsVehicle -> "H"
    TransitMode.CarParking -> "P"
    TransitMode.CarDropoff -> "D"
    TransitMode.RideSharing -> "R"
    TransitMode.Flex -> "F"
    TransitMode.Tram -> "T"
    TransitMode.Subway, TransitMode.Metro -> "M"
    TransitMode.Suburban -> "S"
    TransitMode.RegionalRail, TransitMode.RegionalFastRail, TransitMode.Rail -> "R"
    TransitMode.LongDistance, TransitMode.HighspeedRail, TransitMode.NightRail -> "IC"
    TransitMode.Bus -> "B"
    TransitMode.Coach -> "C"
    TransitMode.Ferry -> "F"
    TransitMode.Airplane -> "A"
    TransitMode.Funicular, TransitMode.AerialLift, TransitMode.CableCar -> "L"
    TransitMode.OnDemand -> "D"
    TransitMode.Other -> "•"
}
