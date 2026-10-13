package app.roadstr.feature.transit

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.network.TransitLine
import app.roadstr.core.network.TransitStopReference
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitTripDetails
import app.roadstr.service.transit.NativeTransitData
import app.roadstr.service.transit.NativeTransitDataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeTransitOperationalStatus { Hidden, Loading, Lines, Trip, Failure }

data class NativeTransitOperationalSnapshot(
    val revision: Long = -1L,
    val status: NativeTransitOperationalStatus = NativeTransitOperationalStatus.Hidden,
    val title: String = "",
    val stop: TransitStopReference? = null,
    val tripReference: TransitScopedId? = null,
    val lines: List<TransitLine> = emptyList(),
    val trip: TransitTripDetails? = null,
)

class NativeTransitOperationalSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeTransitOperationalSnapshot())
    val state: StateFlow<NativeTransitOperationalSnapshot> = _state.asStateFlow()

    fun beginLines(revision: Long, stop: TransitStopReference): Boolean = synchronized(lock) {
        if (revision <= _state.value.revision) return false
        _state.value = NativeTransitOperationalSnapshot(
            revision = revision,
            status = NativeTransitOperationalStatus.Loading,
            title = stop.name,
            stop = stop,
        )
        true
    }

    fun submitLines(revision: Long, result: NativeTransitDataResult<List<TransitLine>>): Boolean = synchronized(lock) {
        if (revision != _state.value.revision || _state.value.status == NativeTransitOperationalStatus.Hidden) return false
        _state.value = if (result is NativeTransitData) {
            _state.value.copy(status = NativeTransitOperationalStatus.Lines, lines = result.value.take(256))
        } else {
            _state.value.copy(status = NativeTransitOperationalStatus.Failure)
        }
        true
    }

    fun beginTrip(revision: Long, title: String, reference: TransitScopedId): Boolean = synchronized(lock) {
        if (revision <= _state.value.revision) return false
        _state.value = NativeTransitOperationalSnapshot(
            revision = revision,
            status = NativeTransitOperationalStatus.Loading,
            title = title.take(300),
            tripReference = reference,
        )
        true
    }

    fun submitTrip(revision: Long, result: NativeTransitDataResult<TransitTripDetails>): Boolean = synchronized(lock) {
        if (revision != _state.value.revision || _state.value.status == NativeTransitOperationalStatus.Hidden) return false
        _state.value = if (result is NativeTransitData) {
            _state.value.copy(status = NativeTransitOperationalStatus.Trip, trip = result.value)
        } else {
            _state.value.copy(status = NativeTransitOperationalStatus.Failure)
        }
        true
    }

    fun hide(): Boolean = synchronized(lock) {
        if (_state.value.status == NativeTransitOperationalStatus.Hidden) return false
        _state.value = NativeTransitOperationalSnapshot(revision = _state.value.revision)
        true
    }
}

@Composable
fun NativeTransitOperationalDetailsPanel(
    snapshot: NativeTransitOperationalSnapshot,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeTransitOperationalStatus.Hidden) return
    val fallbackTitle = androidx.compose.ui.res.stringResource(R.string.native_transport_mode_transit)
    val title = snapshot.title.ifBlank { fallbackTitle }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 8.dp,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onClose) { Text("×") }
            }
            when (snapshot.status) {
                NativeTransitOperationalStatus.Hidden -> Unit
                NativeTransitOperationalStatus.Loading -> Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                NativeTransitOperationalStatus.Failure -> Column(Modifier.fillMaxWidth()) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.native_transit_request_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.native_transit_retry))
                    }
                }
                NativeTransitOperationalStatus.Lines -> if (snapshot.lines.isEmpty()) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.native_transit_no_service_here),
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    items(snapshot.lines, key = { "${it.id.providerId}:${it.id.value}" }) { line ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(line.shortName ?: line.longName ?: "—", fontWeight = FontWeight.Bold)
                            listOfNotNull(line.longName?.takeIf { it != line.shortName }, line.agencyName, line.mode.wireName)
                                .joinToString(" · ").takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                        }
                        HorizontalDivider()
                    }
                }
                NativeTransitOperationalStatus.Trip -> {
                    val legs = snapshot.trip?.itinerary?.legs.orEmpty()
                    if (legs.isEmpty()) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.native_transit_nav_data_unavailable),
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                        items(legs.indices.toList(), key = { it }) { index ->
                            val leg = legs[index]
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    listOfNotNull(leg.displayLine, leg.headsign).joinToString(" · ").ifBlank { leg.mode.wireName },
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${leg.fromName ?: "—"} → ${leg.toName ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                leg.from?.expectedPlatform?.let { platform ->
                                    Text("◉ $platform", style = MaterialTheme.typography.labelSmall)
                                }
                                leg.intermediateStops.mapNotNull { it.name }.forEach { stop ->
                                    Text("  • $stop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (leg.cancelled) {
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.native_transit_cancelled),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
