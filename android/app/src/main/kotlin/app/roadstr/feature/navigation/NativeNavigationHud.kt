package app.roadstr.feature.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dormant, provider-free Compose counterpart of Flutter's turn-by-turn HUD. */
@Composable
fun NativeNavigationHud(
    snapshot: NativeNavigationHudSnapshot,
    onStop: () -> Unit,
    onToggleVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeNavigationHudStatus.Hidden) return
    val current = snapshot.current ?: return
    val instruction = currentInstruction(current)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                paneTitle = instruction
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        val landscape = maxWidth > maxHeight
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NavigationInstructionCard(current, instruction, landscape)
            NavigationSecondaryRow(
                next = snapshot.next,
                voiceMuted = snapshot.voiceMuted,
                onToggleVoice = onToggleVoice,
                onOpenSettings = onOpenSettings,
                landscape = landscape,
            )
        }
        snapshot.altitudeLabel?.let { altitude ->
            AltitudeBadge(
                altitude = altitude,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
            )
        }
        NavigationSummaryPanel(
            snapshot = snapshot,
            onStop = onStop,
            landscape = landscape,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NavigationInstructionCard(
    current: NativeNavigationStepPresentation,
    instruction: String,
    landscape: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 5.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = if (landscape) 10.dp else 16.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeManeuverSymbol(
                visual = current.maneuver,
                modifier = Modifier.size(if (landscape) 60.dp else 88.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instruction,
                    modifier = Modifier.semantics { heading() },
                    maxLines = if (landscape) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    style = if (landscape) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.Bold,
                )
                current.roadName
                    ?.takeUnless { instruction.contains(it, ignoreCase = true) }
                    ?.let { road ->
                        Text(
                            text = road,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (current.prominentDistance) {
                        "↑  ${current.distanceLabel}"
                    } else {
                        current.distanceLabel
                    },
                    color = if (current.prominentDistance) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (current.prominentDistance) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun NavigationSecondaryRow(
    next: NativeNavigationStepPresentation?,
    voiceMuted: Boolean,
    onToggleVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    landscape: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        next?.let { value ->
            Surface(
                modifier = Modifier.widthIn(max = if (landscape) 260.dp else 220.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NativeManeuverSymbol(
                        visual = value.maneuver,
                        showBackground = false,
                        modifier = Modifier.size(if (landscape) 28.dp else 36.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.native_nav_then, value.instruction),
                            maxLines = if (landscape) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = value.distanceLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        HudAction(
            symbol = "⚙",
            description = stringResource(R.string.native_nav_settings),
            onClick = onOpenSettings,
        )
        Spacer(modifier = Modifier.width(8.dp))
        HudAction(
            symbol = if (voiceMuted) "⊘" else "◖))",
            description = stringResource(R.string.native_nav_voice),
            onClick = onToggleVoice,
            subdued = voiceMuted,
        )
    }
}

@Composable
private fun HudAction(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    subdued: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (subdued) 0.45f else 1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AltitudeBadge(altitude: String, modifier: Modifier) {
    val description = stringResource(R.string.native_nav_altitude)
    Surface(
        modifier = modifier.semantics {
            contentDescription = description
        },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = "△  $altitude",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NavigationSummaryPanel(
    snapshot: NativeNavigationHudSnapshot,
    onStop: () -> Unit,
    landscape: Boolean,
    modifier: Modifier,
) {
    val stopDescription = stringResource(R.string.native_nav_exit)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 5.dp,
        shadowElevation = 9.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 14.dp,
                top = if (landscape) 6.dp else 12.dp,
                bottom = if (landscape) 7.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeSpeedometer(snapshot, if (landscape) 66.dp else 88.dp)
            snapshot.speedLimit?.let { limit ->
                Spacer(modifier = Modifier.width(8.dp))
                SpeedLimitSign(limit)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = durationLabel(snapshot),
                    style = if (landscape) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${snapshot.remainingDistanceLabel}  ·  ${stringResource(R.string.native_nav_eta, snapshot.etaLabel.orEmpty())}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                onClick = onStop,
                modifier = Modifier
                    .size(width = 48.dp, height = 66.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = stopDescription
                    },
                color = Color(0xFF72_141A),
                contentColor = Color(0xFFFF_7B7B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFFF_4444).copy(alpha = 0.55f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun durationLabel(snapshot: NativeNavigationHudSnapshot): String =
    if (snapshot.durationMinutes < 60) {
        stringResource(R.string.native_nav_duration_min, snapshot.durationMinutes)
    } else {
        stringResource(
            R.string.native_nav_duration_hour_min,
            snapshot.durationHours,
            snapshot.durationMinuteRemainder,
        )
    }

@Composable
private fun currentInstruction(current: NativeNavigationStepPresentation): String =
    when (current.arrivalSide) {
        NativeArrivalSide.None -> current.instruction
        NativeArrivalSide.Ahead -> stringResource(R.string.native_nav_arrival_ahead)
        NativeArrivalSide.Left -> stringResource(R.string.native_nav_arrival_left)
        NativeArrivalSide.Right -> stringResource(R.string.native_nav_arrival_right)
    }

@Composable
private fun SpeedLimitSign(limit: Int) {
    val description = stringResource(R.string.native_nav_speed_limit)
    Surface(
        modifier = Modifier
            .size(62.dp)
            .semantics {
                contentDescription = description
            },
        shape = CircleShape,
        color = Color.White,
        contentColor = Color.Black,
        border = BorderStroke(5.dp, Color.Red),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = limit.toString(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun NativeSpeedometer(snapshot: NativeNavigationHudSnapshot, dimension: androidx.compose.ui.unit.Dp) {
    val accent = if (snapshot.overSpeedLimit) Color(0xFFEF_5350) else MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val maxSpeed = if (snapshot.speedUnit == "mph") 130f else 200f
    val progress = (snapshot.speed / maxSpeed).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .size(dimension)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f), CircleShape)
            .semantics { contentDescription = "${snapshot.speed} ${snapshot.speedUnit}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            when (snapshot.speedometerStyle) {
                NativeSpeedometerStyle.Classic -> {
                    drawArc(inactive, 135f, 270f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(accent, 135f, 270f * progress, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                }
                NativeSpeedometerStyle.Digital -> {
                    drawLine(inactive, Offset(size.width * .15f, size.height * .25f), Offset(size.width * .85f, size.height * .25f), 1.dp.toPx())
                    drawLine(inactive, Offset(size.width * .15f, size.height * .75f), Offset(size.width * .85f, size.height * .75f), 1.dp.toPx())
                    drawLine(accent, Offset(size.width * .15f, size.height * .86f), Offset(size.width * (.15f + .7f * progress), size.height * .86f), 3.dp.toPx(), StrokeCap.Round)
                }
                NativeSpeedometerStyle.Analog -> {
                    val center = this.center
                    val radius = size.minDimension * .40f
                    for (index in 0..20) {
                        val angle = (135.0 + 270.0 * index / 20.0) * PI / 180.0
                        val outer = Offset((center.x + radius * cos(angle)).toFloat(), (center.y + radius * sin(angle)).toFloat())
                        val innerRadius = radius * if (index % 5 == 0) .78f else .86f
                        val inner = Offset((center.x + innerRadius * cos(angle)).toFloat(), (center.y + innerRadius * sin(angle)).toFloat())
                        drawLine(if (index / 20f <= progress) accent else inactive, inner, outer, if (index % 5 == 0) 2.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
                    }
                    val needle = (135.0 + 270.0 * progress) * PI / 180.0
                    drawLine(accent, center, Offset((center.x + radius * .68f * cos(needle)).toFloat(), (center.y + radius * .68f * sin(needle)).toFloat()), 2.dp.toPx(), StrokeCap.Round)
                }
                NativeSpeedometerStyle.Sport -> {
                    for (index in 0 until 28) {
                        drawArc(
                            if ((index + 1) / 28f <= progress) accent else inactive,
                            130f + 280f * index / 28f,
                            7f,
                            false,
                            style = Stroke(4.dp.toPx()),
                        )
                    }
                }
                NativeSpeedometerStyle.Minimal -> {
                    drawArc(inactive, 117f, 306f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(accent, 117f, 306f * progress, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = snapshot.speed.toString(),
                color = accent,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = snapshot.speedUnit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
