package app.roadstr.feature.savedtransit

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NativeSavedTransitJourneysPanel(
    journeys: List<NativeSavedTransitJourney>,
    selected: NativeSavedTransitJourney?,
    onSelect: (NativeSavedTransitJourney) -> Unit,
    onView: (NativeSavedTransitJourney, Boolean) -> Unit,
    onVerify: (NativeSavedTransitJourney) -> Unit,
    onDelete: (NativeSavedTransitJourney) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.native_saved_transit_title)
    Surface(
        modifier = modifier.fillMaxWidth().semantics { paneTitle = title },
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("×") }
            }
            if (journeys.isEmpty()) {
                Text(
                    stringResource(R.string.native_saved_transit_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(journeys, key = NativeSavedTransitJourney::id) { journey ->
                        SavedTransitRow(
                            journey,
                            expanded = selected?.id == journey.id,
                            onSelect,
                            onView,
                            onVerify,
                            onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTransitRow(
    journey: NativeSavedTransitJourney,
    expanded: Boolean,
    onSelect: (NativeSavedTransitJourney) -> Unit,
    onView: (NativeSavedTransitJourney, Boolean) -> Unit,
    onVerify: (NativeSavedTransitJourney) -> Unit,
    onDelete: (NativeSavedTransitJourney) -> Unit,
) {
    Surface(
        onClick = { onSelect(journey) },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(journey.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${journey.query.originLabel} → ${journey.query.destinationLabel}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(runCatching { ZoneId.of(journey.timezone) }.getOrDefault(ZoneId.systemDefault()))
                .format(journey.original.startTime)
            Text("$time · ${journey.provider.displayName}", style = MaterialTheme.typography.labelSmall)
            Text(
                stringResource(
                    when (journey.state) {
                        NativeSavedTransitState.SavedSnapshot -> R.string.native_saved_transit_original
                        NativeSavedTransitState.NeedsVerification -> R.string.native_saved_transit_needs_verification
                        NativeSavedTransitState.Updated -> R.string.native_saved_transit_updated
                        NativeSavedTransitState.Unavailable -> R.string.native_saved_transit_unavailable
                    },
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            if (expanded) {
                Text(
                    stringResource(R.string.native_saved_transit_offline_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                journey.updated?.let { updated ->
                    val differences = NativeSavedTransitComparator.compare(journey.original, updated)
                    Text(stringResource(R.string.native_saved_transit_differences, differences.size))
                    differences.forEach { difference ->
                        SavedTransitDifferenceRow(journey, difference)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onView(journey, false) }) {
                        Text(stringResource(R.string.native_saved_transit_original))
                    }
                    if (journey.updated != null) {
                        TextButton(onClick = { onView(journey, true) }) {
                            Text(stringResource(R.string.native_saved_transit_updated))
                        }
                    }
                    TextButton(onClick = { onVerify(journey) }) {
                        Text(stringResource(R.string.native_saved_transit_verify))
                    }
                    TextButton(onClick = { onDelete(journey) }) {
                        Text(stringResource(R.string.native_saved_transit_delete))
                    }
                }
            } else {
                TextButton(onClick = { onView(journey, journey.updated != null) }) {
                    Text(stringResource(R.string.native_saved_transit_view))
                }
            }
        }
    }
}

@Composable
private fun SavedTransitDifferenceRow(
    journey: NativeSavedTransitJourney,
    difference: NativeSavedTransitDifference,
) {
    val before = journey.original.legs.getOrNull(difference.legIndex)
    val after = journey.updated?.legs?.getOrNull(difference.legIndex)
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(runCatching { ZoneId.of(journey.timezone) }.getOrDefault(ZoneId.systemDefault()))
    val detail = when (difference.kind) {
        NativeSavedTransitDifferenceKind.DelayChanged -> stringResource(
            R.string.native_saved_transit_delay_changed,
            before?.startTime?.let(formatter::format) ?: "—",
            after?.startTime?.let(formatter::format) ?: "—",
        )
        NativeSavedTransitDifferenceKind.PlatformChanged -> stringResource(
            R.string.native_saved_transit_platform_changed,
            before?.from?.expectedPlatform ?: before?.from?.scheduledPlatform ?: "—",
            after?.from?.expectedPlatform ?: after?.from?.scheduledPlatform ?: "—",
        )
        NativeSavedTransitDifferenceKind.CancellationChanged ->
            stringResource(R.string.native_saved_transit_cancellation_changed)
        NativeSavedTransitDifferenceKind.TripChanged -> stringResource(R.string.native_saved_transit_trip_changed)
        NativeSavedTransitDifferenceKind.StopsChanged -> stringResource(R.string.native_saved_transit_stops_changed)
        NativeSavedTransitDifferenceKind.GeometryChanged -> stringResource(R.string.native_saved_transit_geometry_changed)
        NativeSavedTransitDifferenceKind.UncertainIdentity -> stringResource(R.string.native_saved_transit_identity_uncertain)
    }
    Text(
        stringResource(R.string.native_saved_transit_leg_change, difference.legIndex + 1, detail),
        style = MaterialTheme.typography.bodySmall,
        color = if (difference.kind == NativeSavedTransitDifferenceKind.CancellationChanged) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
