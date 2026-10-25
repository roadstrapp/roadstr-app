package app.roadstr.feature.journey

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin

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
        AnimatedContent(
            targetState = showCatalog,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(tween(420)) { it / 4 } + fadeIn(tween(260)) + scaleIn(tween(420), .985f))
                        .togetherWith(slideOutHorizontally(tween(300)) { -it / 7 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(420)) { -it / 4 } + fadeIn(tween(260)) + scaleIn(tween(420), .985f))
                        .togetherWith(slideOutHorizontally(tween(300)) { it / 7 } + fadeOut(tween(180)))
                }.using(SizeTransform(clip = false))
            },
            label = "journey-screen",
        ) { catalogVisible ->
            if (catalogVisible) {
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
    LazyColumn(modifier = Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding()) {
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
                Image(
                    painter = painterResource(R.drawable.journey_rewards_emblem),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
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
    var selectedAchievement by remember { mutableStateOf<JourneyAchievementState?>(null) }
    LazyColumn(modifier = Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding()) {
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
                onBadgeClick = { selectedAchievement = state },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    selectedAchievement?.let { state ->
        AchievementDetailDialog(
            state = state,
            languageCode = languageCode,
            onDismiss = { selectedAchievement = null },
        )
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
    onBadgeClick: () -> Unit,
) {
    val definition = state.definition
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBadgeClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JourneyBadgeIcon(definition.iconKey, state.unlocked, Modifier.size(58.dp))
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

/** ImageGen-authored reward coins; locked rewards reuse the silhouette in muted grayscale. */
@Composable
private fun JourneyBadgeIcon(iconKey: String, unlocked: Boolean, modifier: Modifier = Modifier) {
    val lockedMatrix = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }
    Image(
        painter = painterResource(journeyBadgeDrawable(iconKey)),
        contentDescription = null,
        colorFilter = if (unlocked) null else ColorFilter.colorMatrix(lockedMatrix),
        modifier = modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.38f },
    )
}

/**
 * Orthographically projects a short cylinder: the gold body is extruded from
 * many depth slices and the visible face sits on the cylinder's front or back
 * plane. At 90 degrees the face disappears while the body keeps its thickness.
 */
@Composable
private fun JourneySpinningCoin(
    iconKey: String,
    unlocked: Boolean,
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    if (!unlocked) {
        JourneyBadgeIcon(iconKey, unlocked = false, modifier = modifier)
        return
    }
    val radians = rotationDegrees * PI / 180.0
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val faceProjection = abs(cosine)
    val sideVisibility = abs(sine)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter = size.minDimension * 0.88f
            val projectedWidth = (diameter * faceProjection).coerceAtLeast(1f)
            val halfDepth = size.minDimension * 0.065f
            val center = Offset(size.width / 2f, size.height / 2f)
            val sideAlpha = (sideVisibility / 0.22f).coerceIn(0f, 1f)
            val slices = 42

            // Back-to-front projected discs form one continuous cylindrical body.
            repeat(slices) { index ->
                val depth = -1f + (2f * index / (slices - 1))
                val offsetX = depth * halfDepth * sine
                val gold = when (index % 7) {
                    0 -> Color(0xFF824400)
                    1, 6 -> Color(0xFFAA6207)
                    2, 5 -> Color(0xFFD89416)
                    else -> Color(0xFFFFC94E)
                }
                drawOval(
                    color = gold.copy(alpha = sideAlpha),
                    topLeft = Offset(
                        x = center.x + offsetX - projectedWidth / 2f,
                        y = center.y - diameter / 2f,
                    ),
                    size = Size(projectedWidth, diameter),
                )
            }

            // Fine reeding follows the curved side and is occluded by the face.
            if (sideVisibility > 0.18f) {
                val radius = diameter / 2f
                val depthHalfWidth = halfDepth * sideVisibility
                val lineAlpha = ((sideVisibility - 0.18f) / 0.45f).coerceIn(0f, 0.72f)
                val lineStep = size.minDimension * 0.052f
                var y = center.y - radius * 0.92f
                while (y <= center.y + radius * 0.92f) {
                    val normalizedY = ((y - center.y) / radius).coerceIn(-1f, 1f)
                    val ellipseHalfWidth = projectedWidth / 2f * sqrt(1f - normalizedY * normalizedY)
                    drawLine(
                        color = Color(0xFFFFE29A).copy(alpha = lineAlpha),
                        start = Offset(center.x - depthHalfWidth - ellipseHalfWidth, y),
                        end = Offset(center.x + depthHalfWidth + ellipseHalfWidth, y),
                        strokeWidth = (size.minDimension * 0.006f).coerceAtLeast(1f),
                    )
                    y += lineStep
                }
            }
        }
        JourneyBadgeIcon(
            iconKey = iconKey,
            unlocked = true,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val signedProjection = faceProjection.coerceAtLeast(0.012f) *
                    if (cosine >= 0f) 1f else -1f
                scaleX = signedProjection
                translationX = size.width * 0.065f * sine *
                    if (cosine >= 0f) 1f else -1f
            },
        )
    }
}

@DrawableRes
private fun journeyBadgeDrawable(iconKey: String): Int = when (iconKey) {
    "ignition" -> R.drawable.journey_badge_ignition
    "street_rookie" -> R.drawable.journey_badge_street_rookie
    "detour_hunter" -> R.drawable.journey_badge_detour_hunter
    "wanderlust" -> R.drawable.journey_badge_wanderlust
    "wayfinder" -> R.drawable.journey_badge_wayfinder
    "road_rebel" -> R.drawable.journey_badge_road_rebel
    "asphalt_nomad" -> R.drawable.journey_badge_asphalt_nomad
    "horizon_chaser" -> R.drawable.journey_badge_horizon_chaser
    "long_hauler" -> R.drawable.journey_badge_long_hauler
    "borderless" -> R.drawable.journey_badge_borderless
    "road_warrior" -> R.drawable.journey_badge_road_warrior
    "atlas_runner" -> R.drawable.journey_badge_atlas_runner
    "continental_drifter" -> R.drawable.journey_badge_continental_drifter
    "planet_roamer" -> R.drawable.journey_badge_planet_roamer
    "earthshaker" -> R.drawable.journey_badge_earthshaker
    "roadstr_myth" -> R.drawable.journey_badge_roadstr_myth
    "impossible_road" -> R.drawable.journey_badge_impossible_road
    else -> R.drawable.journey_badge_ignition
}

@Composable
private fun AchievementDetailDialog(
    state: JourneyAchievementState,
    languageCode: String,
    onDismiss: () -> Unit,
) {
    val spin = rememberInfiniteTransition(label = "achievement-coin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "achievement-coin-spin",
    )
    val sparkle by spin.animateFloat(
        initialValue = .22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "achievement-sparkle",
    )
    val definition = state.definition
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                if (state.unlocked) {
                    Text("✦", color = Color(0xFFFFC107).copy(alpha = sparkle), modifier = Modifier.align(Alignment.TopStart))
                    Text("✧", color = Color.White.copy(alpha = sparkle), modifier = Modifier.align(Alignment.CenterEnd))
                    Text("✦", color = Color(0xFFFFD54F).copy(alpha = 1f - sparkle / 2f), modifier = Modifier.align(Alignment.BottomStart))
                }
                JourneySpinningCoin(
                    iconKey = definition.iconKey,
                    unlocked = state.unlocked,
                    rotationDegrees = if (state.unlocked) rotation else 0f,
                    modifier = Modifier
                        .size(108.dp),
                )
            }
        },
        title = { Text(definition.name, fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(definition.description(languageCode), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    String.format(Locale.ROOT, "%,d km", definition.thresholdMeters / 1_000L),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                state.unlockedAtEpochMillis?.let { unlockedAt ->
                    Text(
                        "${stringResource(R.string.native_journey_unlocked)} · " +
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(unlockedAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
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
