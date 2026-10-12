package app.roadstr.feature.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Compact transit-specific guidance surface; it never infers boarding or alighting. */
@Composable
fun NativeTransitNavigationHud(
    snapshot: NativeTransitNavigationSnapshot,
    onBoarded: () -> Unit,
    onAlighted: () -> Unit,
    onWalkCompleted: () -> Unit,
    onMissed: () -> Unit,
    onVerify: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.stage == NativeTransitNavigationStage.Ended) return
    val stageLabel = transitStageLabel(snapshot.stage)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                paneTitle = stageLabel
                liveRegion = LiveRegionMode.Polite
            },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                snapshot.line?.let { line ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(7.dp),
                    ) {
                        Text(
                            text = line,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = stageLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            snapshot.direction?.let {
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_direction, it))
            }
            snapshot.nextStop?.let {
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_next_stop, it))
            }
            snapshot.alightingStop?.let {
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_alight_at, it))
            }
            snapshot.stopsRemaining?.let {
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_stops_remaining, it))
            }
            snapshot.expectedTime?.let { instant ->
                val label = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_expected_at, label))
            }
            snapshot.platform?.let {
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_platform, it))
            }
            snapshot.nextLine?.let {
                TransitNavigationDetail(stringResource(R.string.native_transit_nav_next_line, it))
            }
            when (snapshot.connectionStatus) {
                NativeTransitConnectionStatus.None -> Unit
                NativeTransitConnectionStatus.Feasible ->
                    TransitNavigationDetail(stringResource(R.string.native_transit_connection_feasible))
                NativeTransitConnectionStatus.AtRisk ->
                    TransitNavigationDetail(stringResource(R.string.native_transit_connection_at_risk))
                NativeTransitConnectionStatus.Missed ->
                    TransitNavigationDetail(stringResource(R.string.native_transit_connection_missed))
                NativeTransitConnectionStatus.Unknown ->
                    TransitNavigationDetail(stringResource(R.string.native_transit_connection_unknown))
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (snapshot.stage) {
                    NativeTransitNavigationStage.WaitingToBoard,
                    NativeTransitNavigationStage.WaitingForConnection,
                    -> Button(onClick = onBoarded) {
                        Text(stringResource(R.string.native_transit_nav_boarded))
                    }
                    NativeTransitNavigationStage.OnBoard,
                    NativeTransitNavigationStage.ApproachingAlightingStop,
                    -> Button(onClick = onAlighted) {
                        Text(stringResource(R.string.native_transit_nav_alighted))
                    }
                    NativeTransitNavigationStage.ApproachStop,
                    NativeTransitNavigationStage.TransferWalk,
                    NativeTransitNavigationStage.FinalWalk,
                    -> Button(onClick = onWalkCompleted) {
                        Text(stringResource(R.string.native_transit_nav_reached_stop))
                    }
                    else -> Unit
                }
                if (snapshot.active && snapshot.stage != NativeTransitNavigationStage.FinalWalk) {
                    OutlinedButton(onClick = onMissed) {
                        Text(stringResource(R.string.native_transit_nav_missed_action))
                    }
                }
                if (snapshot.active) {
                    OutlinedButton(onClick = onVerify) {
                        Text(stringResource(R.string.native_transit_nav_verify))
                    }
                }
                OutlinedButton(onClick = onEnd) {
                    Text(stringResource(R.string.native_transit_nav_end))
                }
            }
        }
    }
}

@Composable
private fun TransitNavigationDetail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun transitStageLabel(stage: NativeTransitNavigationStage): String = stringResource(
    when (stage) {
        NativeTransitNavigationStage.ApproachStop -> R.string.native_transit_nav_approach_stop
        NativeTransitNavigationStage.WaitingToBoard -> R.string.native_transit_nav_waiting_to_board
        NativeTransitNavigationStage.OnBoard -> R.string.native_transit_nav_on_board
        NativeTransitNavigationStage.ApproachingAlightingStop -> R.string.native_transit_nav_approaching_alight
        NativeTransitNavigationStage.TransferWalk -> R.string.native_transit_nav_transfer_walk
        NativeTransitNavigationStage.WaitingForConnection -> R.string.native_transit_nav_waiting_connection
        NativeTransitNavigationStage.FinalWalk -> R.string.native_transit_nav_final_walk
        NativeTransitNavigationStage.Arrived -> R.string.native_transit_nav_arrived
        NativeTransitNavigationStage.Suspended -> R.string.native_transit_nav_suspended
        NativeTransitNavigationStage.DataUnavailable -> R.string.native_transit_nav_data_unavailable
        NativeTransitNavigationStage.MissedTrip -> R.string.native_transit_nav_missed
        NativeTransitNavigationStage.Ended -> R.string.native_transit_nav_end
    },
)
