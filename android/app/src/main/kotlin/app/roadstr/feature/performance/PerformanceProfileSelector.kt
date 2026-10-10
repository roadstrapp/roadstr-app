package app.roadstr.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.feature.map.NativeMapEngine
import kotlin.math.roundToInt

/** Shared four-stop selector used by both first-run setup and Settings. */
@Composable
fun PerformanceProfileSelector(
    profile: NativePerformanceProfile,
    onProfileCommitted: (NativePerformanceProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(profile) { mutableStateOf(profile) }
    val draftName = stringResource(draft.nameResource)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Slider(
                value = (draft.level - 1).toFloat(),
                onValueChange = { value ->
                    draft = NativePerformanceProfile.fromLevel(value.roundToInt() + 1)
                },
                onValueChangeFinished = {
                    if (draft != profile) onProfileCommitted(draft)
                },
                valueRange = 0f..3f,
                steps = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = draftName
                        contentDescription = draftName
                    },
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NativePerformanceProfile.entries.forEach { value ->
                    val selected = value == draft
                    val name = stringResource(value.nameResource)
                    val description = stringResource(value.descriptionResource)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .sizeIn(minHeight = 58.dp)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = {
                                    draft = value
                                    if (value != profile) onProfileCommitted(value)
                                },
                            )
                            .semantics { contentDescription = "$name. $description" }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = value.icon.imageVector(),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                text = draftName,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(draft.descriptionResource),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(
                        if (draft.mapEngine == NativeMapEngine.MapLibre) {
                            R.string.native_performance_engine_3d
                        } else {
                            R.string.native_performance_engine_2d
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.native_performance_fps, draft.maximumFps),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(
                        when (draft) {
                            NativePerformanceProfile.Eco,
                            NativePerformanceProfile.Balanced,
                            -> R.string.native_performance_gps_1hz
                            NativePerformanceProfile.ThreeDEco ->
                                R.string.native_performance_gps_adaptive
                            NativePerformanceProfile.Performance ->
                                R.string.native_performance_gps_2hz
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun NativePerformanceIcon.imageVector(): ImageVector = when (this) {
    NativePerformanceIcon.BatteryFull -> Icons.Outlined.BatteryFull
    NativePerformanceIcon.BatteryMedium -> Icons.Outlined.Battery5Bar
    NativePerformanceIcon.Layers -> Icons.Outlined.Layers
    NativePerformanceIcon.Bolt -> Icons.Outlined.Bolt
}
