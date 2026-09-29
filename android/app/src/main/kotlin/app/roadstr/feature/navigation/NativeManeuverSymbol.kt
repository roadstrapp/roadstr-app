package app.roadstr.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Deterministic vector grammar matching Flutter's maneuver symbol families. */
@Composable
fun NativeManeuverSymbol(
    visual: NativeManeuverVisual,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
) {
    val accent = if (showBackground) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val muted = if (showBackground) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
    }
    val surface = if (showBackground) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (showBackground) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .padding(if (showBackground) 8.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({ scale(size.width / 100f, size.height / 100f, Offset.Zero) }) {
                ManeuverDrawScope(this, accent, muted).draw(visual)
            }
        }
        if (visual.kind == NativeManeuverKind.Roundabout && visual.roundaboutExit != null) {
            Box(
                modifier = Modifier
                    .background(surface.copy(alpha = 0.82f), RoundedCornerShape(50))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = visual.roundaboutExit.toString(),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

private class ManeuverDrawScope(
    private val scope: DrawScope,
    private val accent: Color,
    private val muted: Color,
) {
    private fun stroke(color: Color, width: Float = 9f) = Stroke(
        width = width,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    private fun route(path: Path) = scope.drawPath(path, accent, style = stroke(accent))

    private fun alternative(path: Path, width: Float = 7f) =
        scope.drawPath(path, muted, style = stroke(muted, width))

    private fun arrow(tip: Offset, angle: Double, length: Double = 13.0) {
        val spread = 0.58
        val back = angle + PI
        for (delta in listOf(-spread, spread)) {
            scope.drawLine(
                color = accent,
                start = tip,
                end = Offset(
                    (tip.x + length * cos(back + delta)).toFloat(),
                    (tip.y + length * sin(back + delta)).toFloat(),
                ),
                strokeWidth = 7f,
                cap = StrokeCap.Round,
            )
        }
    }

    fun draw(visual: NativeManeuverVisual) {
        when (visual.kind) {
            NativeManeuverKind.Straight -> straight()
            NativeManeuverKind.Depart -> depart()
            NativeManeuverKind.SlightLeft -> slight(-1)
            NativeManeuverKind.SlightRight -> slight(1)
            NativeManeuverKind.Left -> turn(-1)
            NativeManeuverKind.Right -> turn(1)
            NativeManeuverKind.SharpLeft -> sharp(-1)
            NativeManeuverKind.SharpRight -> sharp(1)
            NativeManeuverKind.UTurnLeft -> uTurn(-1)
            NativeManeuverKind.UTurnRight -> uTurn(1)
            NativeManeuverKind.ForkLeft -> fork(-1)
            NativeManeuverKind.ForkRight -> fork(1)
            NativeManeuverKind.MergeLeft -> merge(-1)
            NativeManeuverKind.MergeRight -> merge(1)
            NativeManeuverKind.RampLeft -> ramp(-1, false)
            NativeManeuverKind.RampRight -> ramp(1, false)
            NativeManeuverKind.ExitLeft -> ramp(-1, true)
            NativeManeuverKind.ExitRight -> ramp(1, true)
            NativeManeuverKind.Roundabout -> roundabout(
                visual.roundaboutExit,
                visual.roundaboutArmCount,
            )
            NativeManeuverKind.Arrive -> arrive()
            NativeManeuverKind.Ferry -> ferry()
        }
    }

    private fun straight() {
        route(Path().apply { moveTo(50f, 88f); lineTo(50f, 15f) })
        arrow(Offset(50f, 15f), -PI / 2)
    }

    private fun depart() {
        scope.drawCircle(accent, 7f, Offset(50f, 86f))
        route(Path().apply { moveTo(50f, 78f); lineTo(50f, 15f) })
        arrow(Offset(50f, 15f), -PI / 2)
    }

    private fun slight(side: Int) {
        val end = Offset(50f + side * 25f, 15f)
        route(
            Path().apply {
                moveTo(50f, 88f)
                cubicTo(50f, 58f, 55f + side * 12f, 37f, end.x, end.y)
            },
        )
        arrow(end, -PI / 2 + side * 0.45)
    }

    private fun turn(side: Int) {
        val end = Offset(50f + side * 37f, 35f)
        route(
            Path().apply {
                moveTo(50f, 88f)
                lineTo(50f, 55f)
                cubicTo(50f, 42f, 57f + side * 10f, 35f, end.x, end.y)
            },
        )
        arrow(end, if (side > 0) 0.0 else PI)
    }

    private fun sharp(side: Int) {
        val end = Offset(50f + side * 34f, 68f)
        route(
            Path().apply {
                moveTo(50f, 88f)
                lineTo(50f, 43f)
                cubicTo(50f, 31f, 63f + side * 18f, 42f, end.x, end.y)
            },
        )
        arrow(end, if (side > 0) 0.75 else PI - 0.75)
    }

    private fun uTurn(side: Int) {
        val startX = 50f + side * 10f
        val endX = 50f - side * 14f
        route(
            Path().apply {
                moveTo(startX, 88f)
                lineTo(startX, 40f)
                cubicTo(startX, 20f, endX, 20f, endX, 40f)
                lineTo(endX, 68f)
            },
        )
        arrow(Offset(endX, 68f), PI / 2)
    }

    private fun fork(side: Int) {
        val selected = Offset(50f + side * 28f, 16f)
        val other = Offset(50f - side * 25f, 18f)
        alternative(
            Path().apply {
                moveTo(50f, 88f)
                lineTo(50f, 55f)
                cubicTo(50f, 43f, other.x - side * 4f, 30f, other.x, other.y)
            },
        )
        route(
            Path().apply {
                moveTo(50f, 88f)
                lineTo(50f, 55f)
                cubicTo(50f, 43f, selected.x - side * 4f, 30f, selected.x, selected.y)
            },
        )
        arrow(selected, -PI / 2 + side * 0.48)
    }

    private fun merge(side: Int) {
        alternative(
            Path().apply {
                moveTo(50f + side * 27f, 88f)
                cubicTo(50f + side * 18f, 67f, 50f + side * 8f, 54f, 50f, 48f)
                lineTo(50f, 15f)
            },
        )
        route(
            Path().apply {
                moveTo(50f - side * 25f, 88f)
                cubicTo(50f - side * 20f, 65f, 50f - side * 8f, 52f, 50f, 47f)
                lineTo(50f, 15f)
            },
        )
        arrow(Offset(50f, 15f), -PI / 2)
    }

    private fun ramp(side: Int, exit: Boolean) {
        alternative(Path().apply { moveTo(50f, 90f); lineTo(50f, 12f) }, if (exit) 10f else 7f)
        val end = Offset(50f + side * 36f, 24f)
        route(
            Path().apply {
                moveTo(50f, 90f)
                lineTo(50f, 58f)
                cubicTo(50f, 46f, 62f + side * 12f, 35f, end.x, end.y)
            },
        )
        arrow(end, -PI / 2 + side * 0.78)
    }

    private fun roundabout(exit: Int?, armCount: Int?) {
        val center = Offset(50f, 48f)
        val radius = 24f
        val arms = armCount ?: if (exit == null) 4 else max(4, exit + 1)
        val selected = exit?.coerceIn(1, arms)
        val step = 2 * PI / arms
        val entry = PI / 2
        scope.drawCircle(muted, radius, center, style = stroke(muted, 8f))
        for (index in 1 until arms) {
            val angle = entry - index * step
            scope.drawLine(
                muted,
                Offset(
                    (center.x + radius * cos(angle)).toFloat(),
                    (center.y + radius * sin(angle)).toFloat(),
                ),
                Offset(
                    (center.x + 35 * cos(angle)).toFloat(),
                    (center.y + 35 * sin(angle)).toFloat(),
                ),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        scope.drawLine(accent, Offset(50f, 90f), Offset(50f, 72f), 9f, StrokeCap.Round)
        val sweep = -(selected?.times(step) ?: step * 0.72)
        scope.drawArc(
            color = accent,
            startAngle = 90f,
            sweepAngle = Math.toDegrees(sweep).toFloat(),
            useCenter = false,
            topLeft = Offset(26f, 24f),
            size = Size(48f, 48f),
            style = stroke(accent),
        )
        val angle = entry + sweep
        val ring = Offset(
            (center.x + radius * cos(angle)).toFloat(),
            (center.y + radius * sin(angle)).toFloat(),
        )
        val tipRadius = if (selected == null) radius.toDouble() else 39.0
        val tip = Offset(
            (center.x + tipRadius * cos(angle)).toFloat(),
            (center.y + tipRadius * sin(angle)).toFloat(),
        )
        if (selected != null) {
            scope.drawLine(accent, ring, tip, 9f, StrokeCap.Round)
        }
        arrow(tip, if (selected == null) angle - PI / 2 else angle, if (selected == null) 10.0 else 13.0)
    }

    private fun arrive() {
        scope.drawLine(accent, Offset(34f, 86f), Offset(34f, 18f), 8f, StrokeCap.Round)
        scope.drawPath(
            Path().apply {
                moveTo(38f, 22f)
                lineTo(77f, 22f)
                lineTo(68f, 42f)
                lineTo(38f, 42f)
                close()
            },
            accent,
        )
    }

    private fun ferry() {
        straight()
        for (y in listOf(68f, 81f)) {
            alternative(
                Path().apply {
                    moveTo(15f, y)
                    cubicTo(25f, y - 7f, 35f, y + 7f, 45f, y)
                    cubicTo(55f, y - 7f, 65f, y + 7f, 85f, y)
                },
                5f,
            )
        }
    }
}
