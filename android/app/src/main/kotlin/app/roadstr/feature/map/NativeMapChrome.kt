package app.roadstr.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.RoadstrGlassBox
import app.roadstr.core.ui.RoadstrGlassLevel
import app.roadstr.core.ui.RoadstrIconButton
import kotlin.math.roundToInt

@Composable
fun NativeMapSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoadstrGlassBox(
        modifier = modifier.fillMaxWidth(),
        level = RoadstrGlassLevel.Strong,
        shape = RoundedCornerShape(18.dp),
        padding = 13.dp,
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.native_home_navigate),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                Icons.Outlined.CompassCalibration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NativeMapAltitudeBadge(
    altitudeMeters: Double,
    imperial: Boolean,
    modifier: Modifier = Modifier,
) {
    val value = if (imperial) altitudeMeters * 3.28084 else altitudeMeters
    RoadstrGlassBox(
        modifier = modifier,
        level = RoadstrGlassLevel.Medium,
        shape = RoundedCornerShape(12.dp),
        padding = 9.dp,
    ) {
        Text(
            text = "△ ${value.roundToInt()} ${if (imperial) "ft" else "m"}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun NativeMapNavigationControls(
    headingActive: Boolean,
    bearingDegrees: Float = 0f,
    onToggleHeading: () -> Unit,
    onRecenter: () -> Unit,
    onReport: () -> Unit,
    onAddWaypoint: () -> Unit,
    modifier: Modifier = Modifier,
    // In landscape there is no room for a column between the guidance buttons and the summary panel.
    horizontal: Boolean = false,
) {
    val buttons: @Composable () -> Unit = {
        MapControlButton(
            description = stringResource(R.string.native_map_compass),
            onClick = onToggleHeading,
        ) {
            Icon(
                Icons.Outlined.CompassCalibration,
                contentDescription = null,
                tint = if (headingActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer(rotationZ = -bearingDegrees),
            )
        }
        MapControlButton(description = stringResource(R.string.native_map_my_location), onClick = onRecenter) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        MapControlButton(description = stringResource(R.string.native_map_report_event), onClick = onReport) {
            Icon(Icons.Outlined.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        MapControlButton(description = stringResource(R.string.native_map_add_stop), onClick = onAddWaypoint) {
            Icon(Icons.Outlined.AddLocationAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (horizontal) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) { buttons() }
    }
}

/**
 * Compass and recentre outside navigation, as on the Flutter map: the compass
 * is always there and turns the map between heading-up and north-up, the
 * recentre button appears once the driver has panned away from their position.
 */
@Composable
fun NativeMapFreeDriveControls(
    headingActive: Boolean,
    bearingDegrees: Float,
    showRecenter: Boolean,
    onToggleHeading: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        MapControlButton(description = stringResource(R.string.native_map_compass), onClick = onToggleHeading) {
            Icon(
                Icons.Outlined.CompassCalibration,
                contentDescription = null,
                tint = if (headingActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer(rotationZ = -bearingDegrees),
            )
        }
        if (showRecenter) {
            MapControlButton(description = stringResource(R.string.native_map_my_location), onClick = onRecenter) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MapControlButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    RoadstrIconButton(
        contentDescription = description,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) { content() }
}
