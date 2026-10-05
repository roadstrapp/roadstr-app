package app.roadstr.feature.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roadstr.R
import app.roadstr.core.ui.RoadstrGlassBox
import app.roadstr.core.ui.RoadstrGlassLevel
import app.roadstr.core.ui.RoadstrIconButton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Provider-free Compose counterpart of Flutter's turn-by-turn HUD. */
@Composable
fun NativeNavigationHud(
    snapshot: NativeNavigationHudSnapshot,
    onStop: () -> Unit,
    onToggleVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Height in pixels of the bottom panel, so the map can keep the vehicle clear above it. */
    onBottomPanelHeight: (Int) -> Unit = {},
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
                .padding(horizontal = 10.dp)
                .padding(top = 4.dp),
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
        snapshot.currentStreetName?.let { roadName ->
            CurrentStreetLabel(
                name = roadName,
                maxWidth = maxWidth * 0.52f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 136.dp),
            )
        }
        SpeedLimitSign(
            limit = snapshot.speedLimit,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 150.dp),
        )
        NavigationSummaryPanel(
            snapshot = snapshot,
            onStop = onStop,
            landscape = landscape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { onBottomPanelHeight(it.height) },
        )
    }
}

/** Six-second, dismissible counterpart of Flutter's post-arrival banner. */
@Composable
fun NativeNavigationArrivalBanner(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val title = stringResource(R.string.native_nav_arrived)
    val close = stringResource(R.string.native_nav_close)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color(0xFF22_C55E),
            contentColor = Color.White,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = close
                        },
                    color = Color.Transparent,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationInstructionCard(
    current: NativeNavigationStepPresentation,
    instruction: String,
    landscape: Boolean,
) {
    RoadstrGlassBox(
        modifier = Modifier.fillMaxWidth(),
        level = RoadstrGlassLevel.Strong,
        shape = RoundedCornerShape(22.dp),
        padding = 0.dp,
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
    RoadstrIconButton(
        contentDescription = description,
        onClick = onClick,
    ) {
        val icon = if (symbol == "⚙") Icons.Outlined.Settings
        else if (subdued) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (subdued) 0.45f else 1f),
        )
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
    RoadstrGlassBox(
        modifier = modifier
            .fillMaxWidth(),
        level = RoadstrGlassLevel.Strong,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        padding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 14.dp,
                    top = if (landscape) 6.dp else 12.dp,
                    bottom = if (landscape) 7.dp else 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeSpeedometer(snapshot, if (landscape) 66.dp else 88.dp)
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
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stopDescription,
                    )
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
private fun SpeedLimitSign(limit: Int?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.native_nav_speed_limit)
    Surface(
        modifier = modifier
            .size(78.dp)
            .semantics {
                contentDescription = description
            },
        shape = CircleShape,
        color = Color.White,
        contentColor = Color.Black,
        border = BorderStroke(6.dp, Color.Red),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = limit?.toString() ?: "–",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun CurrentStreetLabel(
    name: String,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = maxWidth),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NativeSpeedometer(snapshot: NativeNavigationHudSnapshot, dimension: androidx.compose.ui.unit.Dp) {
    val accent = if (snapshot.overSpeedLimit) Color(0xFFEF_5350) else MaterialTheme.colorScheme.primary
    // Main's tokens: surface2 is `surface`, surface3 is `surfaceVariant`,
    // the hairline colour is `outline` and secondary text `onSurfaceVariant`.
    val surface2 = MaterialTheme.colorScheme.surface
    val surface3 = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val darkSurface = surface2.luminance() < 0.5f
    val maxSpeed = if (snapshot.speedUnit == "mph") 130f else 200f
    val progress = (snapshot.speed / maxSpeed).coerceIn(0f, 1f)
    val shape = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Digital) {
        RoundedCornerShape(dimension * .24f)
    } else {
        CircleShape
    }
    val background = when (snapshot.speedometerStyle) {
        NativeSpeedometerStyle.Digital -> surface2
        NativeSpeedometerStyle.Sport -> Color(0xFF11_1522).copy(alpha = if (darkSurface) .94f else .9f)
        NativeSpeedometerStyle.Minimal -> surface2.copy(alpha = .86f)
        NativeSpeedometerStyle.Analog -> surface2.copy(alpha = .96f)
        else -> surface2.copy(alpha = .92f)
    }
    val digitalBrush = Brush.linearGradient(listOf(surface3, surface2, accent.copy(alpha = .12f)))
    Box(
        modifier = Modifier
            .size(dimension)
            .shadow(
                elevation = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Minimal) 0.dp else 12.dp,
                shape = shape,
                // Sport glows in the accent colour instead of casting a grey shadow.
                ambientColor = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Sport) accent else Color.Black,
                spotColor = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Sport) accent else Color.Black,
            )
            .then(
                if (snapshot.speedometerStyle == NativeSpeedometerStyle.Digital) {
                    Modifier.background(digitalBrush, shape)
                } else {
                    Modifier.background(background, shape)
                },
            )
            .border(
                width = when (snapshot.speedometerStyle) {
                    NativeSpeedometerStyle.Digital -> 1.4.dp
                    NativeSpeedometerStyle.Sport, NativeSpeedometerStyle.Analog -> 1.2.dp
                    NativeSpeedometerStyle.Classic -> if (snapshot.overSpeedLimit) 1.5.dp else .5.dp
                    NativeSpeedometerStyle.Minimal -> .6.dp
                },
                color = when (snapshot.speedometerStyle) {
                    NativeSpeedometerStyle.Digital -> accent.copy(alpha = .8f)
                    NativeSpeedometerStyle.Sport -> accent.copy(alpha = .75f)
                    NativeSpeedometerStyle.Analog, NativeSpeedometerStyle.Classic ->
                        if (snapshot.overSpeedLimit) accent else outline
                    NativeSpeedometerStyle.Minimal -> outline.copy(alpha = .7f)
                },
                shape = shape,
            )
            .semantics { contentDescription = "${snapshot.speed} ${snapshot.speedUnit}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (snapshot.speedometerStyle) {
                NativeSpeedometerStyle.Classic -> {
                    val inset = 12.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    drawArc(surface3, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    if (progress > 0f) drawArc(accent, 135f, 270f * progress, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                }
                NativeSpeedometerStyle.Digital -> {
                    val grid = outline.copy(alpha = .45f)
                    val thin = maxOf(1f, size.width * .008f)
                    drawLine(grid, Offset(size.width * .14f, size.height * .28f), Offset(size.width * .86f, size.height * .28f), thin)
                    drawLine(grid, Offset(size.width * .14f, size.height * .72f), Offset(size.width * .86f, size.height * .72f), thin)
                    val barTop = size.height * .82f
                    val barHeight = size.height * .035f
                    drawRoundRect(grid.copy(alpha = .4f), Offset(size.width * .14f, barTop), Size(size.width * .72f, barHeight), androidx.compose.ui.geometry.CornerRadius(barHeight * .5f))
                    if (progress > 0f) drawRoundRect(accent, Offset(size.width * .14f, barTop), Size(size.width * .72f * progress, barHeight), androidx.compose.ui.geometry.CornerRadius(barHeight * .5f))
                }
                NativeSpeedometerStyle.Analog -> {
                    val center = this.center
                    val radius = size.minDimension * .39f
                    for (index in 0..20) {
                        val angle = (135.0 + 270.0 * index / 20.0) * PI / 180.0
                        val outer = Offset((center.x + radius * cos(angle)).toFloat(), (center.y + radius * sin(angle)).toFloat())
                        val innerRadius = radius - size.minDimension * if (index % 5 == 0) .095f else .055f
                        val inner = Offset((center.x + innerRadius * cos(angle)).toFloat(), (center.y + innerRadius * sin(angle)).toFloat())
                        drawLine(if (index / 20f <= progress) accent else secondaryText.copy(alpha = .5f), inner, outer, size.minDimension * if (index % 5 == 0) .024f else .012f, StrokeCap.Round)
                    }
                    val needle = (135.0 + 270.0 * progress) * PI / 180.0
                    drawLine(accent, center, Offset((center.x + radius * .7f * cos(needle)).toFloat(), (center.y + radius * .7f * sin(needle)).toFloat()), size.minDimension * .035f, StrokeCap.Round)
                    drawCircle(surface3, size.minDimension * .075f, center)
                    drawCircle(accent, size.minDimension * .035f, center)
                }
                NativeSpeedometerStyle.Sport -> {
                    for (index in 0 until 28) {
                        drawArc(
                            if ((index + 1) / 28f <= progress) accent else Color(0xFF31_394C),
                            129.6f + 280.8f * index / 28f,
                            280.8f / 28f - 1.432f,
                            false,
                            topLeft = Offset(size.width * .1f, size.height * .1f),
                            size = Size(size.width * .8f, size.height * .8f),
                            style = Stroke(size.minDimension * .065f, cap = StrokeCap.Butt),
                        )
                    }
                }
                NativeSpeedometerStyle.Minimal -> {
                    val inset = size.minDimension * .07f
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val stroke = size.minDimension * .025f
                    drawArc(secondaryText.copy(alpha = .18f), 117f, 306f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (progress > 0f) {
                        drawArc(accent, 117f, 306f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        val angle = (117.0 + 306.0 * progress) * PI / 180.0
                        val radius = size.minDimension * .43f
                        drawCircle(accent, size.minDimension * .035f, Offset((center.x + radius * cos(angle)).toFloat(), (center.y + radius * sin(angle)).toFloat()))
                    }
                }
            }
        }
        if (snapshot.speedometerStyle == NativeSpeedometerStyle.Analog) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dimension * .18f),
                shape = RoundedCornerShape(dimension * .06f),
                color = surface3,
                border = BorderStroke(.5.dp, outline),
            ) {
                Text(
                    text = snapshot.speed.toString(),
                    modifier = Modifier.padding(horizontal = dimension * .085f, vertical = dimension * .025f),
                    color = accent,
                    fontSize = (dimension.value * .16f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = (dimension.value * .16f).sp,
                )
            }
        } else {
            val speedColor = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Minimal && !snapshot.overSpeedLimit) {
                MaterialTheme.colorScheme.onSurface
            } else accent
            val fontFactor = when (snapshot.speedometerStyle) {
                NativeSpeedometerStyle.Classic -> .27f
                NativeSpeedometerStyle.Digital -> .31f
                NativeSpeedometerStyle.Sport -> .28f
                NativeSpeedometerStyle.Minimal -> .30f
                NativeSpeedometerStyle.Analog -> .16f
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = snapshot.speed.toString(),
                    color = speedColor,
                    fontSize = (dimension.value * fontFactor).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (dimension.value * fontFactor).sp,
                    letterSpacing = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Digital) 1.2.sp else TextUnit.Unspecified,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = snapshot.speedUnit,
                    color = if (snapshot.speedometerStyle == NativeSpeedometerStyle.Sport) Color(0xFF9A_A6BF) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (dimension.value * fontFactor * .29f).sp,
                )
            }
        }
    }
}
