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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import app.roadstr.core.journey.JourneyAchievement
import app.roadstr.core.journey.JourneyAchievementState
import app.roadstr.core.journey.JourneyMode
import app.roadstr.core.journey.JourneySnapshot
import app.roadstr.core.journey.JourneySyncPhase
import app.roadstr.core.journey.JourneySyncSnapshot
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class JourneyPublicBadgePhase {
    CHECKING,
    PUBLISHING,
    NOT_ISSUED,
    PUBLISHED,
    ERROR,
}

data class JourneyPublicBadgeState(val phase: JourneyPublicBadgePhase)

@Composable
fun NativeJourneyPanel(
    snapshot: JourneySnapshot,
    languageCode: String,
    imperial: Boolean,
    showCatalog: Boolean,
    onShowCatalog: () -> Unit,
    onHideCatalog: () -> Unit,
    onClear: () -> Unit,
    syncSnapshot: JourneySyncSnapshot? = null,
    syncEnabled: Boolean = false,
    onSync: () -> Unit = {},
    publicBadgesConfigured: Boolean = false,
    publicBadgeStates: Map<String, JourneyPublicBadgeState> = emptyMap(),
    onShowPublicBadge: (JourneyAchievement) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(if (showCatalog) R.string.native_journey_achievements else R.string.native_journey_title)
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.background,
    ) {
        if (showCatalog) {
            AchievementCatalogContent(
                snapshot,
                languageCode,
                publicBadgesConfigured,
                publicBadgeStates,
                onShowPublicBadge,
                onHideCatalog,
            )
        } else {
            JourneyDashboardContent(snapshot, imperial, onShowCatalog, onClear, syncSnapshot, syncEnabled, onSync, onClose)
        }
    }
}

@Composable
private fun JourneyDashboardContent(
    snapshot: JourneySnapshot,
    imperial: Boolean,
    onShowCatalog: () -> Unit,
    onClear: () -> Unit,
    syncSnapshot: JourneySyncSnapshot?,
    syncEnabled: Boolean,
    onSync: () -> Unit,
    onClose: () -> Unit,
) {
    val formatter = UnitFormatter(imperial)
    var confirmClear by remember { mutableStateOf(false) }
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
        snapshot.statistics.lastSession?.let { last ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.native_journey_last_session), fontWeight = FontWeight.Bold)
                        Text(
                            "${modeLabel(last.mode)} · ${formatter.formatDistance(last.validatedMeters.toDouble())}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(
                                Date(last.endedAtEpochMillis ?: last.startedAtEpochMillis),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (syncEnabled && syncSnapshot != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.native_journey_sync_title), fontWeight = FontWeight.Bold)
                        Text(syncStatus(syncSnapshot), style = MaterialTheme.typography.bodySmall)
                        if (syncSnapshot.potentialOverlaps > 0) {
                            Text(
                                stringResource(R.string.native_journey_sync_overlap, syncSnapshot.potentialOverlaps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(
                            onClick = onSync,
                            enabled = syncSnapshot.phase != JourneySyncPhase.SYNCING,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(R.string.native_journey_sync_now)) }
                    }
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
                onClick = { confirmClear = true },
                enabled = snapshot.activeSession == null && snapshot.record.sessions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(18.dp).sizeIn(minHeight = 48.dp),
            ) { Text(stringResource(R.string.native_journey_clear)) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.native_journey_clear_confirm_title)) },
            text = { Text(stringResource(R.string.native_journey_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) {
                    Text(stringResource(R.string.native_journey_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun syncStatus(snapshot: JourneySyncSnapshot): String = when (snapshot.phase) {
    JourneySyncPhase.IDLE -> if (snapshot.localChangesPending) {
        stringResource(R.string.native_journey_sync_pending)
    } else {
        stringResource(R.string.native_journey_sync_idle)
    }
    JourneySyncPhase.SYNCING -> stringResource(R.string.native_journey_syncing)
    JourneySyncPhase.SYNCED -> snapshot.lastSuccessfulSyncEpochMillis?.let {
        stringResource(
            R.string.native_journey_sync_success,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)),
        )
    } ?: stringResource(R.string.native_journey_sync_idle)
    JourneySyncPhase.SIGNER_REQUIRED -> stringResource(R.string.native_journey_sync_signer)
    JourneySyncPhase.CRYPTO_UNAVAILABLE -> stringResource(R.string.native_journey_sync_crypto)
    JourneySyncPhase.ERROR -> snapshot.detail ?: stringResource(R.string.native_journey_sync_error)
}

@Composable
private fun AchievementCatalogContent(
    snapshot: JourneySnapshot,
    languageCode: String,
    publicBadgesConfigured: Boolean,
    publicBadgeStates: Map<String, JourneyPublicBadgeState>,
    onShowPublicBadge: (JourneyAchievement) -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        item { JourneyHeader(stringResource(R.string.native_journey_achievements), onClose) }
        item {
            Text(
                stringResource(
                    if (publicBadgesConfigured) R.string.native_journey_public_badges_ready
                    else R.string.native_journey_public_badges_unavailable,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(snapshot.achievements, key = { it.definition.id }) { state ->
            AchievementRow(
                state,
                languageCode,
                publicBadgesConfigured,
                publicBadgeStates[state.definition.id],
                onShowPublicBadge,
            )
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
private fun AchievementRow(
    state: JourneyAchievementState,
    languageCode: String,
    publicBadgesConfigured: Boolean,
    publicBadgeState: JourneyPublicBadgeState?,
    onShowPublicBadge: (JourneyAchievement) -> Unit,
) {
    val definition = state.definition
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JourneyBadgeIcon(definition.iconKey, definition.level, state.unlocked, Modifier.size(58.dp))
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
            if (state.unlocked && publicBadgesConfigured) {
                val phase = publicBadgeState?.phase
                OutlinedButton(
                    onClick = { onShowPublicBadge(definition) },
                    enabled = phase != JourneyPublicBadgePhase.CHECKING &&
                        phase != JourneyPublicBadgePhase.PUBLISHING &&
                        phase != JourneyPublicBadgePhase.PUBLISHED,
                    modifier = Modifier.padding(top = 6.dp).sizeIn(minHeight = 40.dp),
                ) {
                    Text(
                        stringResource(
                            when (phase) {
                                JourneyPublicBadgePhase.CHECKING -> R.string.native_journey_public_badge_checking
                                JourneyPublicBadgePhase.PUBLISHING -> R.string.native_journey_public_badge_publishing
                                JourneyPublicBadgePhase.PUBLISHED -> R.string.native_journey_public_badge_published
                                JourneyPublicBadgePhase.NOT_ISSUED -> R.string.native_journey_public_badge_not_issued
                                JourneyPublicBadgePhase.ERROR -> R.string.native_journey_public_badge_retry
                                null -> R.string.native_journey_public_badge_show
                            },
                        ),
                    )
                }
            }
        }
        Text(
            stringResource(if (state.unlocked) R.string.native_journey_unlocked else R.string.native_journey_locked),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Seventeen compact vector marks; no raster payload and no emoji stand-ins. */
@Composable
private fun JourneyBadgeIcon(iconKey: String, level: Int, unlocked: Boolean, modifier: Modifier = Modifier) {
    val primary = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val secondary = if (unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val stroke = size.minDimension * 0.055f
        drawCircle(secondary, radius = size.minDimension * 0.48f)
        drawCircle(primary, radius = size.minDimension * 0.42f, style = Stroke(stroke))
        if (level >= 8) {
            drawCircle(primary.copy(alpha = 0.45f), radius = size.minDimension * 0.34f, style = Stroke(stroke / 2f))
        }
        drawJourneyGlyph(iconKey, primary, if (unlocked && level >= 12) Color.White else primary, stroke)
    }
}

private fun DrawScope.drawJourneyGlyph(iconKey: String, color: Color, highlight: Color, stroke: Float) {
    fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = stroke) =
        drawLine(color, point(x1, y1), point(x2, y2), width, StrokeCap.Round)
    fun path(vararg points: Pair<Float, Float>, closePath: Boolean = false, fill: Boolean = false) {
        val value = Path().apply {
            moveTo(size.width * points.first().first, size.height * points.first().second)
            points.drop(1).forEach { lineTo(size.width * it.first, size.height * it.second) }
            if (closePath) close()
        }
        drawPath(value, color, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else Stroke(stroke, cap = StrokeCap.Round))
    }

    when (iconKey) {
        "ignition" -> {
            drawCircle(color, size.minDimension * 0.09f, point(.5f, .5f))
            listOf(.24f to .5f, .76f to .5f, .5f to .24f, .5f to .76f).forEach { (x, y) ->
                line(.5f + (x - .5f) * .52f, .5f + (y - .5f) * .52f, x, y)
            }
        }
        "street_rookie" -> {
            line(.37f, .76f, .45f, .24f)
            line(.63f, .76f, .55f, .24f)
            line(.5f, .7f, .5f, .6f, stroke * .65f)
            line(.5f, .5f, .5f, .4f, stroke * .65f)
            line(.5f, .32f, .5f, .25f, stroke * .65f)
        }
        "detour_hunter" -> {
            line(.5f, .78f, .5f, .51f)
            line(.5f, .54f, .3f, .34f)
            line(.5f, .54f, .7f, .31f)
            path(.64f to .32f, .72f to .29f, .7f to .38f)
        }
        "wanderlust" -> {
            val road = Path().apply {
                moveTo(size.width * .32f, size.height * .77f)
                cubicTo(size.width * .72f, size.height * .66f, size.width * .25f, size.height * .43f, size.width * .65f, size.height * .22f)
            }
            drawPath(road, color, style = Stroke(stroke, cap = StrokeCap.Round))
            drawCircle(color, stroke * .72f, point(.65f, .22f))
        }
        "wayfinder" -> {
            path(.5f to .18f, .68f to .5f, .5f to .82f, .32f to .5f, closePath = true)
            path(.5f to .27f, .56f to .5f, .43f to .58f, closePath = true, fill = true)
        }
        "road_rebel" -> path(
            .58f to .18f, .34f to .53f, .49f to .53f, .4f to .82f,
            .69f to .42f, .53f to .42f, closePath = true, fill = true,
        )
        "asphalt_nomad" -> {
            line(.22f, .68f, .78f, .68f, stroke * .65f)
            path(.32f to .68f, .5f to .35f, .68f to .68f, closePath = true)
            line(.5f, .35f, .5f, .68f, stroke * .65f)
            line(.43f, .82f, .48f, .7f, stroke * .7f)
            line(.57f, .82f, .52f, .7f, stroke * .7f)
        }
        "horizon_chaser" -> {
            drawArc(color, 180f, 180f, false, point(.31f, .38f), Size(size.width * .38f, size.height * .38f), style = Stroke(stroke))
            line(.2f, .69f, .8f, .69f, stroke * .7f)
            listOf(.3f, .4f, .5f, .6f, .7f).forEach { x -> line(x, .55f, x, .47f, stroke * .55f) }
        }
        "long_hauler" -> {
            drawRect(color, point(.25f, .4f), Size(size.width * .34f, size.height * .25f), style = Stroke(stroke))
            path(.59f to .48f, .7f to .48f, .77f to .58f, .77f to .65f, .59f to .65f)
            drawCircle(color, stroke, point(.36f, .7f))
            drawCircle(color, stroke, point(.68f, .7f))
        }
        "borderless" -> {
            path(.25f to .38f, .25f to .25f, .39f to .25f)
            path(.61f to .25f, .75f to .25f, .75f to .39f)
            path(.75f to .61f, .75f to .75f, .61f to .75f)
            path(.39f to .75f, .25f to .75f, .25f to .61f)
            val route = Path().apply {
                moveTo(size.width * .18f, size.height * .61f)
                cubicTo(size.width * .35f, size.height * .38f, size.width * .59f, size.height * .7f, size.width * .82f, size.height * .36f)
            }
            drawPath(route, highlight, style = Stroke(stroke * .75f, cap = StrokeCap.Round))
        }
        "road_warrior" -> {
            path(.5f to .18f, .72f to .29f, .68f to .63f, .5f to .8f, .32f to .63f, .28f to .29f, closePath = true)
            line(.42f, .62f, .47f, .34f, stroke * .7f)
            line(.58f, .62f, .53f, .34f, stroke * .7f)
        }
        "atlas_runner" -> {
            path(.2f to .3f, .4f to .22f, .6f to .3f, .8f to .22f, .8f to .7f, .6f to .78f, .4f to .7f, .2f to .78f, closePath = true)
            line(.4f, .22f, .4f, .7f, stroke * .55f)
            line(.6f, .3f, .6f, .78f, stroke * .55f)
            line(.28f, .59f, .7f, .4f, stroke * .65f)
        }
        "continental_drifter" -> {
            path(.31f to .25f, .5f to .2f, .62f to .32f, .74f to .36f, .65f to .49f, .68f to .64f, .53f to .76f, .42f to .63f, .29f to .57f, .34f to .43f, closePath = true)
            listOf(point(.34f, .69f), point(.5f, .55f), point(.67f, .42f)).forEach { drawCircle(highlight, stroke * .6f, it) }
        }
        "planet_roamer" -> {
            drawCircle(color, size.minDimension * .29f, point(.5f, .5f), style = Stroke(stroke))
            drawOval(color, point(.38f, .21f), Size(size.width * .24f, size.height * .58f), style = Stroke(stroke * .55f))
            line(.22f, .5f, .78f, .5f, stroke * .55f)
            drawArc(highlight, 202f, 136f, false, point(.16f, .31f), Size(size.width * .68f, size.height * .38f), style = Stroke(stroke * .55f))
        }
        "earthshaker" -> {
            drawCircle(color, size.minDimension * .28f, point(.5f, .5f), style = Stroke(stroke))
            path(.5f to .2f, .44f to .42f, .54f to .47f, .42f to .59f, .51f to .8f)
            path(.31f to .33f, .43f to .44f, .32f to .53f, .25f to .66f)
            path(.69f to .3f, .57f to .45f, .7f to .56f, .75f to .7f)
        }
        "roadstr_myth" -> {
            path(.25f to .68f, .3f to .34f, .43f to .48f, .5f to .22f, .58f to .48f, .72f to .34f, .76f to .68f, closePath = true)
            line(.29f, .72f, .72f, .72f, stroke * 1.2f)
            drawCircle(highlight, stroke * .72f, point(.5f, .22f))
            drawCircle(highlight, stroke * .55f, point(.3f, .34f))
            drawCircle(highlight, stroke * .55f, point(.72f, .34f))
        }
        "impossible_road" -> {
            val infinity = Path().apply {
                moveTo(size.width * .2f, size.height * .5f)
                cubicTo(size.width * .31f, size.height * .25f, size.width * .43f, size.height * .27f, size.width * .5f, size.height * .5f)
                cubicTo(size.width * .58f, size.height * .73f, size.width * .7f, size.height * .75f, size.width * .8f, size.height * .5f)
                cubicTo(size.width * .7f, size.height * .25f, size.width * .58f, size.height * .27f, size.width * .5f, size.height * .5f)
                cubicTo(size.width * .43f, size.height * .73f, size.width * .31f, size.height * .75f, size.width * .2f, size.height * .5f)
            }
            drawPath(infinity, highlight, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 205f, 130f, false, point(.1f, .1f), Size(size.width * .8f, size.height * .8f), style = Stroke(stroke * .55f))
        }
        else -> {
            line(.37f, .76f, .46f, .24f)
            line(.63f, .76f, .54f, .24f)
        }
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
