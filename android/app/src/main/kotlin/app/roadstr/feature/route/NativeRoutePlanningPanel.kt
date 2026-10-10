package app.roadstr.feature.route

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.RoadstrSwitch
import kotlin.math.roundToInt

/** Dormant Compose counterpart of Flutter's planner, alternatives and preview sheets. */
@Composable
fun NativeRoutePlanningPanel(
    snapshot: NativeRoutePlanningSnapshot,
    onOriginChanged: (String) -> Unit,
    onUseMyLocation: () -> Unit,
    onStopChanged: (Int, String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onModeChanged: (NativeRouteTransportMode) -> Unit,
    onCalculate: () -> Unit,
    onSelectAlternative: (Int) -> Unit,
    onAvoidanceChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onConfirm: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = snapshot.status,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(360)) { height -> height / 5 })
                .togetherWith(
                    fadeOut(tween(180)) + slideOutVertically(tween(300)) { height -> -height / 10 },
                )
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(360) }))
        },
        label = "route-panel-transition",
    ) { status ->
        when (status) {
        NativeRoutePlanningStatus.Hidden -> Box(Modifier)
        NativeRoutePlanningStatus.Planner -> PlannerPanel(
            snapshot = snapshot,
            onOriginChanged = onOriginChanged,
            onUseMyLocation = onUseMyLocation,
            onStopChanged = onStopChanged,
            onAddStop = onAddStop,
            onRemoveStop = onRemoveStop,
            onMoveStop = onMoveStop,
            onModeChanged = onModeChanged,
            onCalculate = onCalculate,
            onCancel = onCancel,
            modifier = Modifier,
        )
        NativeRoutePlanningStatus.Loading -> RouteLoadingPanel(
            onCancel = onCancel,
            modifier = Modifier,
        )
        NativeRoutePlanningStatus.Alternatives -> AlternativesPanel(
            snapshot = snapshot,
            onModeChanged = onModeChanged,
            onSelectAlternative = onSelectAlternative,
            onAvoidanceChanged = onAvoidanceChanged,
            onSave = onSave,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = Modifier,
        )
        NativeRoutePlanningStatus.Preview -> PreviewPanel(
            snapshot = snapshot,
            onModeChanged = onModeChanged,
            onStart = onStart,
            onCancel = onCancel,
            modifier = Modifier,
        )
        }
    }
}

@Composable
private fun PlannerPanel(
    snapshot: NativeRoutePlanningSnapshot,
    onOriginChanged: (String) -> Unit,
    onUseMyLocation: () -> Unit,
    onStopChanged: (Int, String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onModeChanged: (NativeRouteTransportMode) -> Unit,
    onCalculate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val loading = snapshot.status == NativeRoutePlanningStatus.Loading
    val focusManager = LocalFocusManager.current
    val pane = stringResource(R.string.native_route_calculate)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.9f)
                .semantics { paneTitle = pane },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
            val myLocation = stringResource(R.string.native_route_my_location)
            if (snapshot.hasGps && snapshot.originQuery.trim() == myLocation.trim()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = myLocation,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = { onOriginChanged("") },
                            enabled = !loading,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) { Text("×") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = snapshot.originQuery,
                    onValueChange = onOriginChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    singleLine = true,
                    label = { Text(stringResource(R.string.native_route_from_hint)) },
                    trailingIcon = if (snapshot.hasGps) {
                        {
                            TextButton(
                                onClick = onUseMyLocation,
                                enabled = !loading,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            ) { Text("⌖") }
                        }
                    } else null,
                )
            }
            snapshot.stops.forEachIndexed { index, stop ->
                val last = index == snapshot.stops.lastIndex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = stop.query,
                        onValueChange = { onStopChanged(index, it) },
                        modifier = Modifier.weight(1f),
                        enabled = !loading,
                        singleLine = true,
                        label = {
                            Text(
                                stringResource(
                                    if (last) R.string.native_route_to_hint else R.string.native_route_stop_hint,
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (snapshot.canCalculate) onCalculate()
                            },
                        ),
                    )
                    if (snapshot.stops.size > 1) {
                        Column {
                            TextButton(
                                onClick = { onMoveStop(index, index - 1) },
                                enabled = !loading && index > 0,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 40.dp),
                            ) { Text("↑") }
                            TextButton(
                                onClick = { onMoveStop(index, index + 1) },
                                enabled = !loading && index < snapshot.stops.lastIndex,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 40.dp),
                            ) { Text("↓") }
                        }
                        TextButton(
                            onClick = { onRemoveStop(index) },
                            enabled = !loading,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) { Text("×") }
                    }
                }
            }
            if (snapshot.stops.size < NativeRoutePlanningSession.MAX_STOPS) {
                TextButton(onClick = onAddStop, enabled = !loading) {
                    Text("＋ ${stringResource(R.string.native_route_add_stop)}")
                }
            }
            RouteModeRow(snapshot.mode, !loading, onModeChanged)
            snapshot.failure?.let { failure ->
                Text(
                    text = stringResource(
                        when (failure) {
                            NativeRoutePlanningFailure.AreaNotDownloaded -> R.string.native_offline_area_missing
                            NativeRoutePlanningFailure.DatasetInvalid -> R.string.native_offline_dataset_invalid
                            NativeRoutePlanningFailure.Generic -> R.string.native_offline_download_failed
                        },
                    ),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !loading) {
                    Text(stringResource(R.string.native_route_cancel))
                }
                Button(
                    onClick = onCalculate,
                    enabled = snapshot.canCalculate && !loading,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.sizeIn(maxWidth = 22.dp, maxHeight = 22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.native_route_loading),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        Text(stringResource(R.string.native_route_calculate))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun AlternativesPanel(
    snapshot: NativeRoutePlanningSnapshot,
    onModeChanged: (NativeRouteTransportMode) -> Unit,
    onSelectAlternative: (Int) -> Unit,
    onAvoidanceChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val title = stringResource(R.string.native_route_choose)
    RouteBottomSheet(title, modifier, onCancel) {
        RouteModeRow(snapshot.mode, true, onModeChanged)
        if (snapshot.mode == NativeRouteTransportMode.Driving) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !snapshot.avoidanceLoading) {
                        onAvoidanceChanged(!snapshot.avoidanceEnabled)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.native_route_avoid_highways_tolls),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (snapshot.alternatives.any { it.badge == NativeRouteBadge.UnavoidableHighwayOrToll }) {
                        Text(
                            text = stringResource(R.string.native_route_unavoidable_section),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (snapshot.avoidanceLoading) {
                    CircularProgressIndicator(modifier = Modifier.sizeIn(maxWidth = 32.dp, maxHeight = 32.dp))
                } else {
                    RoadstrSwitch(
                        checked = snapshot.avoidanceEnabled,
                        onCheckedChange = onAvoidanceChanged,
                    )
                }
            }
        }
        snapshot.weather?.let { weather ->
            val temperature = if (snapshot.imperialUnits) {
                "${(weather.temperatureCelsius * 9.0 / 5.0 + 32.0).roundToInt()}°F"
            } else {
                "${weather.temperatureCelsius.roundToInt()}°C"
            }
            val wind = if (snapshot.imperialUnits) {
                "${(weather.windKilometresPerHour * 0.621371).roundToInt()} mph"
            } else {
                "${weather.windKilometresPerHour.roundToInt()} km/h"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(weather.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "$temperature  ·  $wind",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = snapshot.alternatives,
                key = { index, card -> "$index:${card.distanceLabel}" },
            ) { index, card ->
                RouteCard(
                    card = card,
                    selected = index == snapshot.selectedIndex,
                    onClick = { onSelectAlternative(index) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
        ) { Text(stringResource(R.string.native_saved_routes_save)) }
        RouteActions(onCancel = onCancel, onPrimary = onConfirm)
    }
}

@Composable
private fun PreviewPanel(
    snapshot: NativeRoutePlanningSnapshot,
    onModeChanged: (NativeRouteTransportMode) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    RouteBottomSheet(snapshot.destinationLabel, modifier, onCancel) {
        val route = snapshot.alternatives.firstOrNull()
        if (route != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = route.durationText(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (snapshot.departureLabel != null && snapshot.arrivalLabel != null) {
                            Text(
                                stringResource(
                                    R.string.native_route_depart_eta,
                                    snapshot.departureLabel,
                                    snapshot.arrivalLabel,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(route.distanceLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        snapshot.trafficStatus?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (snapshot.conditions.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = stringResource(R.string.native_route_conditions),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            snapshot.conditions.forEach { condition ->
                Text(
                    text = listOfNotNull(condition.categoryLabel, condition.comment).joinToString(" · "),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        RouteModeRow(snapshot.mode, true, onModeChanged)
        Spacer(modifier = Modifier.height(10.dp))
        RouteActions(onCancel = onCancel, onPrimary = onStart)
    }
}

@Composable
private fun RouteBottomSheet(
    title: String?,
    modifier: Modifier,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pane = title ?: stringResource(R.string.native_route_choose)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.82f)
                .offset { IntOffset(0, dragOffset.roundToInt()) }
                .semantics { paneTitle = pane },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(onDismiss) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                                },
                                onDragEnd = {
                                    if (dragOffset >= 120.dp.toPx()) onDismiss()
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.width(40.dp).height(4.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    ) {}
                }
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 8.dp)
                            .semantics { heading() },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun RouteLoadingPanel(
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    RouteBottomSheet(
        title = stringResource(R.string.native_route_choose),
        modifier = modifier,
        onDismiss = onCancel,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.native_route_loading))
        }
    }
}

@Composable
private fun RouteModeRow(
    selectedMode: NativeRouteTransportMode,
    enabled: Boolean,
    onModeChanged: (NativeRouteTransportMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            NativeRouteTransportMode.Driving,
            NativeRouteTransportMode.Cycling,
            NativeRouteTransportMode.Walking,
        ).forEach { mode ->
            RouteModeChip(
                mode = mode,
                selected = mode == selectedMode,
                enabled = enabled,
                onModeChanged = onModeChanged,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RouteModeChip(
    mode: NativeRouteTransportMode,
    selected: Boolean,
    enabled: Boolean,
    onModeChanged: (NativeRouteTransportMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        NativeRouteTransportMode.Driving -> stringResource(R.string.native_route_mode_car)
        NativeRouteTransportMode.Cycling -> stringResource(R.string.native_route_mode_bike)
        NativeRouteTransportMode.Walking -> stringResource(R.string.native_route_mode_walk)
        NativeRouteTransportMode.Transit -> stringResource(R.string.native_route_mode_transit)
    }
    Surface(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = enabled) { onModeChanged(mode) }
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(15.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = when (mode) {
                NativeRouteTransportMode.Driving -> "🚗  $label"
                NativeRouteTransportMode.Cycling -> "🚲  $label"
                NativeRouteTransportMode.Walking -> "🚶  $label"
                NativeRouteTransportMode.Transit -> label
            },
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RouteCard(
    card: NativeRouteCardPresentation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val badge = when (card.badge) {
        NativeRouteBadge.None -> null
        NativeRouteBadge.Fastest -> stringResource(R.string.native_route_fastest)
        NativeRouteBadge.AvoidHighwaysAndTolls -> stringResource(R.string.native_route_avoid_highways_tolls)
        NativeRouteBadge.UnavoidableHighwayOrToll -> stringResource(R.string.native_route_unavoidable_section)
        NativeRouteBadge.AvoidUnpavedRoads -> stringResource(R.string.native_route_avoid_unpaved)
    }
    Surface(
        modifier = Modifier
            .width(if (badge == null || card.badge == NativeRouteBadge.Fastest) 132.dp else 178.dp)
            .heightIn(min = 124.dp)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = card.durationText(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = card.distanceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (badge != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NativeRouteCardPresentation.durationText(): String = if (durationHours > 0) {
    stringResource(R.string.native_route_duration_hour_min, durationHours, durationMinuteRemainder)
} else {
    stringResource(R.string.native_route_duration_min, durationMinutes)
}

@Composable
private fun RouteActions(
    onCancel: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .sizeIn(minHeight = 48.dp),
        ) {
            Text(stringResource(R.string.native_route_cancel))
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .weight(2f)
                .sizeIn(minHeight = 48.dp),
        ) {
            Text(stringResource(R.string.native_route_start))
        }
    }
}
