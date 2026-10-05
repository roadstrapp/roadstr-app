package app.roadstr.feature.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.SheetGrabHandle
import app.roadstr.core.ui.rememberSheetDragState
import app.roadstr.feature.map.NativeMapPoint
import java.util.Locale

private val ParkingBlue = Color(0xFF42A5F5)

/**
 * The parking sheet, as on the Flutter map: only the parking spot. With one
 * saved it offers the way there and removal; without one it offers to save the
 * driver's current position. Saved places have their own panel.
 */
@Composable
fun NativeParkingPanel(
    parking: NativeParkingPosition?,
    canSaveHere: Boolean,
    onSaveHere: () -> Unit,
    onNavigate: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.native_saved_parking_title)
    SheetSurface(modifier = modifier.semantics { paneTitle = title }, onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParkingBadge()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                if (parking != null) {
                    Text(
                        text = coordinates(parking.point),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Text(stringResource(R.string.native_saved_close))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (parking != null) {
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.native_saved_parking_navigate)) }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            ) {
                Text(stringResource(R.string.native_saved_parking_remove), color = MaterialTheme.colorScheme.error)
            }
        } else {
            Button(
                onClick = onSaveHere,
                enabled = canSaveHere,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.native_parking_save_here)) }
        }
    }
}

/**
 * What a long press on the map offers: park here, or find out what is there.
 * The second one opens the same place sheet a tap does, with the address, the
 * opening hours, a short Wikipedia description and "navigate here".
 */
@Composable
fun NativeMapContextMenu(
    point: NativeMapPoint,
    onSaveParking: () -> Unit,
    onWhatsHere: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.native_map_whats_here)
    SheetSurface(modifier = modifier.semantics { paneTitle = title }, onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = coordinates(point),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Text(stringResource(R.string.native_saved_close))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onSaveParking,
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text(stringResource(R.string.native_parking_save_here)) }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onWhatsHere,
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text(title) }
    }
}

@Composable
private fun SheetSurface(modifier: Modifier, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val drag = rememberSheetDragState(onDismiss)
    Surface(
        modifier = modifier.fillMaxWidth().then(drag.sheet),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        // The system bar inset goes inside the surface, so the sheet reaches
        // the bottom edge instead of floating above it.
        Column(modifier = Modifier.navigationBarsPadding()) {
            SheetGrabHandle(drag.handle)
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.Top,
            ) { content() }
        }
    }
}

@Composable
private fun ParkingBadge() {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = ParkingBlue.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("P", color = ParkingBlue, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun coordinates(point: NativeMapPoint): String =
    String.format(Locale.ROOT, "%.5f, %.5f", point.latitude, point.longitude)
