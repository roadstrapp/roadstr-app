package app.roadstr.feature.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.format.UnitFormatter
import app.roadstr.core.journey.JourneyAchievementState
import app.roadstr.core.journey.JourneyMode
import app.roadstr.core.journey.JourneySnapshot
import java.util.Locale

@Composable
fun NativeJourneyPanel(
    snapshot: JourneySnapshot,
    languageCode: String,
    imperial: Boolean,
    showCatalog: Boolean,
    onShowCatalog: () -> Unit,
    onHideCatalog: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(if (showCatalog) R.string.native_journey_achievements else R.string.native_journey_title)
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.background,
    ) {
        if (showCatalog) {
            AchievementCatalogContent(snapshot, languageCode, onHideCatalog)
        } else {
            JourneyDashboardContent(snapshot, imperial, onShowCatalog, onClear, onClose)
        }
    }
}

@Composable
private fun JourneyDashboardContent(
    snapshot: JourneySnapshot,
    imperial: Boolean,
    onShowCatalog: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val formatter = UnitFormatter(imperial)
    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        item { JourneyHeader(stringResource(R.string.native_journey_title), onClose) }
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(
                    formatter.formatDistance(snapshot.statistics.totalMeters.toDouble()),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.native_journey_travelled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                val next = snapshot.progress.next
                Text(
                    if (next == null) stringResource(R.string.native_journey_all_unlocked)
                    else stringResource(R.string.native_journey_next_badge, next.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(
                    progress = { snapshot.progress.fraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 6.dp),
                )
                if (next != null) {
                    Text(
                        "${formatter.formatDistance(snapshot.statistics.totalMeters.toDouble())} / " +
                            formatter.formatDistance(next.thresholdMeters.toDouble()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                JourneyStatCard(stringResource(R.string.native_journey_trips), snapshot.statistics.completedTrips.toString(), Modifier.weight(1f))
                JourneyStatCard(stringResource(R.string.native_journey_month), formatter.formatDistance(snapshot.statistics.monthMeters.toDouble()), Modifier.weight(1f))
                JourneyStatCard(stringResource(R.string.native_journey_year), formatter.formatDistance(snapshot.statistics.yearMeters.toDouble()), Modifier.weight(1f))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.native_journey_by_mode), fontWeight = FontWeight.Bold)
                    JourneyMode.entries.forEach { mode ->
                        val meters = snapshot.statistics.metersByMode[mode] ?: 0L
                        if (meters > 0L) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(modeLabel(mode), Modifier.weight(1f))
                                Text(formatter.formatDistance(meters.toDouble()), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item {
            val unlocked = snapshot.achievements.count { it.unlocked }
            Button(
                onClick = onShowCatalog,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).sizeIn(minHeight = 48.dp),
            ) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.native_journey_badges_count, unlocked, snapshot.achievements.size))
            }
            OutlinedButton(
                onClick = onClear,
                enabled = snapshot.activeSession == null && snapshot.record.sessions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(18.dp).sizeIn(minHeight = 48.dp),
            ) { Text(stringResource(R.string.native_journey_clear)) }
        }
    }
}

@Composable
private fun AchievementCatalogContent(
    snapshot: JourneySnapshot,
    languageCode: String,
    onClose: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        item { JourneyHeader(stringResource(R.string.native_journey_achievements), onClose) }
        items(snapshot.achievements, key = { it.definition.id }) { state ->
            AchievementRow(state, languageCode)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun JourneyHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.native_journey_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun AchievementRow(state: JourneyAchievementState, languageCode: String) {
    val definition = state.definition
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JourneyBadgeIcon(definition.level, state.unlocked, Modifier.size(58.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(definition.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                definition.description(languageCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                String.format(Locale.ROOT, "%,d km", definition.thresholdMeters / 1_000L),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(if (state.unlocked) R.string.native_journey_unlocked else R.string.native_journey_locked),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Compact vector badge: road geometry gains rings and spokes with each tier. */
@Composable
private fun JourneyBadgeIcon(level: Int, unlocked: Boolean, modifier: Modifier = Modifier) {
    val primary = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val secondary = if (unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val stroke = size.minDimension * 0.08f
        drawCircle(secondary, radius = size.minDimension * 0.48f)
        drawCircle(primary, radius = size.minDimension * 0.42f, style = Stroke(stroke))
        if (level >= 8) drawCircle(primary.copy(alpha = 0.65f), radius = size.minDimension * 0.31f, style = Stroke(stroke / 2f))
        val cx = size.width / 2f
        drawLine(primary, Offset(cx * 0.72f, size.height * 0.82f), Offset(cx * 0.94f, size.height * 0.18f), stroke, StrokeCap.Round)
        drawLine(primary, Offset(cx * 1.28f, size.height * 0.82f), Offset(cx * 1.06f, size.height * 0.18f), stroke, StrokeCap.Round)
        val marks = when { level >= 16 -> 8; level >= 12 -> 6; level >= 7 -> 4; else -> 2 }
        repeat(marks) { index ->
            val y = size.height * (0.25f + index * 0.5f / maxOf(1, marks - 1))
            drawLine(primary, Offset(cx - stroke / 2f, y), Offset(cx + stroke / 2f, y), stroke / 2f, StrokeCap.Round)
        }
        if (level == 17) drawArc(Color.White, 205f, 130f, false, Offset(stroke, stroke), Size(size.width - 2 * stroke, size.height - 2 * stroke), style = Stroke(stroke / 2f))
    }
}

@Composable
private fun JourneyStatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun modeLabel(mode: JourneyMode): String = stringResource(
    when (mode) {
        JourneyMode.CAR -> R.string.native_journey_mode_car
        JourneyMode.MOTORCYCLE -> R.string.native_journey_mode_motorcycle
        JourneyMode.BICYCLE -> R.string.native_journey_mode_bicycle
        JourneyMode.WALKING -> R.string.native_journey_mode_walking
        JourneyMode.TRANSIT -> R.string.native_journey_mode_transit
        JourneyMode.OTHER -> R.string.native_journey_mode_other
    },
)

