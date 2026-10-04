package app.roadstr.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.view.Choreographer
import android.view.View
import java.util.concurrent.Executors
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Transparent, non-interactive cursor overlay projected by MapLibre. */
internal class NativeMapCursorOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var map: MapLibreMap? = null
    private var snapshot = NativeMapCursorSnapshot(
        sequence = -1L,
        point = null,
        colorArgb = NativeMapCursorVisualPolicy.DEFAULT_COLOR_ARGB,
    )

    private val arrowPath = Path().apply {
        moveTo(24f, 5.5f)
        lineTo(36f, 33f)
        cubicTo(36.3f, 33.7f, 35.6f, 34.4f, 34.9f, 34.1f)
        lineTo(24f, 29.6f)
        lineTo(13.1f, 34.1f)
        cubicTo(12.4f, 34.4f, 11.7f, 33.7f, 12f, 33f)
        close()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 255, 255, 255)
        strokeWidth = 1.4f
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frameLoader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "roadstr-ostrich-frames").apply { isDaemon = true }
    }
    private val ostrichFrames = mutableMapOf<Boolean, List<Bitmap>>()
    private val vehicleBitmaps = mutableMapOf<NativeMapCursorStyle, Bitmap>()
    private val loadingVehicles = mutableSetOf<NativeMapCursorStyle>()
    private val loadingSequences = mutableSetOf<Boolean>()
    private var animationStartNanos = 0L
    private var animationFrameNanos = 0L
    private var lastOstrichRunning = false
    private var transitionFrom: NativeMapCursorSnapshot? = null
    private var transitionStartNanos = 0L
    private var frameScheduled = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!usesOstrich(snapshot) && transitionFrom == null) return
            if (animationStartNanos == 0L) animationStartNanos = frameTimeNanos
            animationFrameNanos = frameTimeNanos
            invalidate()
            scheduleAnimationFrame()
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attach(map: MapLibreMap) {
        this.map = map
        invalidate()
    }

    fun update(snapshot: NativeMapCursorSnapshot) {
        val previous = this.snapshot
        val wasRunning = lastOstrichRunning
        this.snapshot = snapshot
        val styleChanged = effectiveStyle(previous) != effectiveStyle(snapshot) ||
            previous.colorArgb != snapshot.colorArgb
        if (styleChanged && previous.point != null) {
            transitionFrom = previous.copy(point = snapshot.point)
            transitionStartNanos = System.nanoTime()
            animationFrameNanos = transitionStartNanos
        }
        lastOstrichRunning = snapshot.speedKilometresPerHour >= OSTRICH_MOVING_KMH
        if (wasRunning != lastOstrichRunning) animationStartNanos = 0L
        loadVehicleAsset(effectiveStyle(snapshot))
        if (usesOstrich(snapshot)) {
            loadOstrichSequence(lastOstrichRunning)
        }
        scheduleAnimationFrame()
        invalidate()
    }

    fun refreshProjection() {
        if (snapshot.point != null) invalidate()
    }

    fun detach() {
        map = null
        if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameScheduled = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val liveMap = map ?: return
        val point = snapshot.point ?: return
        val screen = liveMap.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
        val visual = NativeMapCursorVisualPolicy.frame(liveMap.cameraPosition.tilt)
        val from = transitionFrom
        if (from != null) {
            val elapsed = (animationFrameNanos - transitionStartNanos).coerceAtLeast(0L)
            val t = (elapsed.toDouble() / POOF_DURATION_NANOS).coerceIn(0.0, 1.0)
            if (t < 1.0) {
                val beforeSwap = t < .5
                val iconSnapshot = if (beforeSwap) from else snapshot
                val iconT = if (beforeSwap) 1.0 - t * 2.0 else (t - .5) * 2.0
                drawSnapshotCursor(
                    canvas = canvas,
                    screenX = screen.x,
                    screenY = screen.y,
                    visual = visual,
                    value = iconSnapshot,
                    alpha = iconT.toFloat(),
                    iconScale = (.25 + .75 * iconT).toFloat(),
                )
                drawPuffCloud(canvas, screen.x, screen.y, t.toFloat())
                scheduleAnimationFrame()
                return
            }
            transitionFrom = null
        }
        drawSnapshotCursor(canvas, screen.x, screen.y, visual, snapshot, 1f, 1f)
    }

    private fun drawSnapshotCursor(
        canvas: Canvas,
        screenX: Float,
        screenY: Float,
        visual: NativeMapCursorVisualFrame,
        value: NativeMapCursorSnapshot,
        alpha: Float,
        iconScale: Float,
    ) {
        canvas.save()
        canvas.scale(iconScale, iconScale, screenX, screenY)
        val layer = canvas.saveLayerAlpha(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            (alpha.coerceIn(0f, 1f) * 255).roundToInt(),
        )
        if (usesOstrich(value)) {
            drawOstrich(canvas, screenX, screenY, value)
            canvas.restoreToCount(layer)
            canvas.restore()
            return
        }
        val scale = density * NativeMapCursorVisualPolicy.DISPLAY_SCALE.toFloat()

        canvas.translate(screenX, screenY)
        canvas.scale(scale, scale * visual.flatYScale.toFloat())
        canvas.translate(
            (-NativeMapCursorVisualPolicy.WIDTH_DP / 2.0).toFloat(),
            (-NativeMapCursorVisualPolicy.HEIGHT_DP / 2.0).toFloat(),
        )
        val style = effectiveStyle(value)
        if (style == NativeMapCursorStyle.Arrow) {
            canvas.save()
            canvas.translate(0f, CURSOR_ASSET_TOP_DP)
            drawShadow(canvas, visual)
            drawArrow(canvas, value.colorArgb)
            canvas.restore()
        } else {
            drawAssetCursor(canvas, style, value.colorArgb)
        }
        canvas.restoreToCount(layer)
        canvas.restore()
    }

    private fun drawShadow(canvas: Canvas, visual: NativeMapCursorVisualFrame) {
        val alpha = (visual.shadowAlpha * 255.0).roundToInt().coerceIn(0, 255)
        shadowPaint.shader = RadialGradient(
            0f,
            0f,
            18f,
            intArrayOf(Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.save()
        canvas.translate(24f, visual.shadowCenterY.toFloat())
        canvas.scale(visual.shadowScaleX.toFloat(), visual.shadowScaleY.toFloat())
        canvas.drawCircle(0f, 0f, 18f, shadowPaint)
        canvas.restore()
        shadowPaint.shader = null
    }

    private fun drawArrow(canvas: Canvas, color: Long) {
        // Puff rendering changes the reusable paints' alpha. Reset every
        // channel here so switching back from a bitmap cursor cannot leave
        // the standard arrow with only its outline visible.
        fillPaint.alpha = 255
        outlinePaint.alpha = 255
        outlinePaint.strokeWidth = 1.5f
        highlightPaint.alpha = 128
        fillPaint.shader = LinearGradient(
            0f,
            5.5f,
            0f,
            34.1f,
            NativeMapCursorVisualPolicy.adjustLightness(color, 0.18),
            NativeMapCursorVisualPolicy.adjustLightness(color, -0.10),
            Shader.TileMode.CLAMP,
        )
        outlinePaint.color = NativeMapCursorVisualPolicy.adjustLightness(color, -0.30)
        canvas.drawPath(arrowPath, fillPaint)
        canvas.drawPath(arrowPath, outlinePaint)
        canvas.drawLine(24f, 10f, 24f, 25.5f, highlightPaint)
        fillPaint.shader = null
    }

    private fun drawAssetCursor(canvas: Canvas, style: NativeMapCursorStyle, colorArgb: Long) {
        val bitmap = vehicleBitmaps[style]
        if (bitmap == null) {
            loadVehicleAsset(style)
            return
        }
        bitmapPaint.alpha = 255
        bitmapPaint.colorFilter = colorFilter(colorArgb)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, CURSOR_ASSET_TOP_DP, 48f, CURSOR_ASSET_TOP_DP + 48f),
            bitmapPaint,
        )
        bitmapPaint.colorFilter = null
    }

    private fun drawRacingBody(canvas: Canvas, formula: Boolean) {
        val body = Path().apply {
            moveTo(24f, 4f)
            lineTo(if (formula) 34f else 38f, 31f)
            lineTo(24f, 27f)
            lineTo(if (formula) 14f else 10f, 31f)
            close()
        }
        canvas.drawPath(body, fillPaint)
        canvas.drawPath(body, outlinePaint)
        canvas.drawLine(24f, 9f, 24f, 24f, highlightPaint)
    }

    private fun drawSuvBody(canvas: Canvas) {
        canvas.drawRoundRect(11f, 8f, 37f, 34f, 6f, 6f, fillPaint)
        canvas.drawRoundRect(11f, 8f, 37f, 34f, 6f, 6f, outlinePaint)
        canvas.drawLine(15f, 19f, 33f, 19f, highlightPaint)
    }

    private fun drawElectricBody(canvas: Canvas) {
        canvas.drawCircle(24f, 21f, 14f, fillPaint)
        canvas.drawCircle(24f, 21f, 14f, outlinePaint)
        val bolt = Path().apply {
            moveTo(26f, 8f)
            lineTo(18f, 23f)
            lineTo(24f, 22f)
            lineTo(21f, 35f)
            lineTo(31f, 18f)
            lineTo(25f, 19f)
            close()
        }
        canvas.drawPath(bolt, highlightPaint)
    }

    private fun drawCityBody(canvas: Canvas) {
        val body = Path().apply {
            moveTo(10f, 15f)
            lineTo(24f, 6f)
            lineTo(38f, 15f)
            lineTo(35f, 34f)
            lineTo(13f, 34f)
            close()
        }
        canvas.drawPath(body, fillPaint)
        canvas.drawPath(body, outlinePaint)
        canvas.drawLine(24f, 12f, 24f, 31f, highlightPaint)
    }

    private fun drawClassicBody(canvas: Canvas) {
        canvas.drawOval(10f, 7f, 38f, 35f, fillPaint)
        canvas.drawOval(10f, 7f, 38f, 35f, outlinePaint)
        canvas.drawLine(14f, 21f, 34f, 21f, highlightPaint)
    }

    private fun drawOstrich(canvas: Canvas, x: Float, y: Float, value: NativeMapCursorSnapshot) {
        val running = value.speedKilometresPerHour >= OSTRICH_MOVING_KMH
        val frames = ostrichFrames[running]
        if (frames.isNullOrEmpty()) {
            loadOstrichSequence(running)
            vehicleBitmaps[NativeMapCursorStyle.Ostrich]?.let { bitmap ->
                val size = OSTRICH_SIZE_DP * density
                bitmapPaint.colorFilter = colorFilter(value.colorArgb)
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f),
                    bitmapPaint,
                )
                bitmapPaint.colorFilter = null
            }
            return
        }
        val referenceSpeed = 4.8
        val rate = if (running) {
            (value.speedKilometresPerHour.takeIf { it > 0.0 } ?: referenceSpeed)
                .div(referenceSpeed)
                .coerceIn(.45, 1.6)
        } else {
            1.0
        }
        val durationNanos = (OSTRICH_CYCLE_NANOS / rate).toLong()
        val elapsed = (animationFrameNanos - animationStartNanos).coerceAtLeast(0L)
        val exactFrame = (elapsed % durationNanos).toDouble() / durationNanos * frames.size
        val frameIndex = exactFrame.toInt().coerceIn(0, frames.lastIndex)
        val nextIndex = (frameIndex + 1) % frames.size
        val blend = (exactFrame - frameIndex).toFloat().coerceIn(0f, 1f)
        val size = OSTRICH_SIZE_DP * density
        val destination = RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)

        // Source frames are authored at 30 fps. Cross-fading adjacent frames
        // on every display VSYNC provides a genuinely smooth 60/90/120 Hz
        // presentation without doubling the animation's authored speed.
        bitmapPaint.alpha = ((1f - blend) * 255).roundToInt()
        bitmapPaint.colorFilter = colorFilter(value.colorArgb)
        canvas.drawBitmap(frames[frameIndex], null, destination, bitmapPaint)
        if (blend > 0f) {
            bitmapPaint.alpha = (blend * 255).roundToInt()
            canvas.drawBitmap(frames[nextIndex], null, destination, bitmapPaint)
        }
        bitmapPaint.alpha = 255
        bitmapPaint.colorFilter = null
    }

    private fun loadVehicleAsset(style: NativeMapCursorStyle) {
        val path = style.assetPath() ?: return
        if (vehicleBitmaps.containsKey(style) || !loadingVehicles.add(style)) return
        frameLoader.execute {
            val decoded = runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            post {
                loadingVehicles.remove(style)
                if (decoded != null) vehicleBitmaps[style] = decoded
                invalidate()
            }
        }
    }

    private fun loadOstrichSequence(running: Boolean) {
        if (ostrichFrames.containsKey(running) || !loadingSequences.add(running)) return
        frameLoader.execute {
            val directory = if (running) "cursors/ostrich_run" else "cursors/ostrich_idle"
            val decoded = buildList(OSTRICH_FRAME_COUNT) {
                for (index in 0 until OSTRICH_FRAME_COUNT) {
                    val path = "$directory/frame-${index.toString().padStart(3, '0')}.png"
                    val bitmap = runCatching {
                        context.assets.open(path).use(BitmapFactory::decodeStream)
                    }.getOrNull() ?: break
                    add(bitmap)
                }
            }
            post {
                loadingSequences.remove(running)
                if (decoded.size == OSTRICH_FRAME_COUNT) ostrichFrames[running] = decoded
                if (usesOstrich(snapshot)) {
                    animationStartNanos = 0L
                    scheduleAnimationFrame()
                    invalidate()
                }
            }
        }
    }

    private fun scheduleAnimationFrame() {
        if (frameScheduled || !isAttachedToWindow) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun drawPuffCloud(canvas: Canvas, x: Float, y: Float, t: Float) {
        val puffT = (1f - abs(t - .5f) * 2f).coerceIn(0f, 1f)
        if (puffT <= 0f) return
        val progress = 1f - puffT
        val size = OSTRICH_SIZE_DP * density * 1.3f
        val spread = 1f + progress * .9f
        val alpha = (235f * puffT * puffT).roundToInt().coerceIn(0, 235)
        fillPaint.shader = null
        fillPaint.color = Color.argb(alpha, 255, 255, 255)
        outlinePaint.color = Color.argb((alpha * .5f).roundToInt(), 128, 128, 128)
        outlinePaint.strokeWidth = 1.2f * density
        for (puff in POOF_PUFFS) {
            val cx = x + puff[0] * size * spread
            val cy = y + puff[1] * size * spread
            val radius = size * puff[2] * (1f + progress * .35f)
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, outlinePaint)
        }
    }

    private fun effectiveStyle(value: NativeMapCursorSnapshot): NativeMapCursorStyle =
        if (value.walkingMode) NativeMapCursorStyle.Ostrich else value.style

    private fun usesOstrich(value: NativeMapCursorSnapshot): Boolean =
        effectiveStyle(value) == NativeMapCursorStyle.Ostrich

    private fun NativeMapCursorStyle.assetPath(): String? = when (this) {
        NativeMapCursorStyle.Arrow -> null
        NativeMapCursorStyle.Formula1 -> "cursors/formula1.png"
        NativeMapCursorStyle.Suv -> "cursors/suv.png"
        NativeMapCursorStyle.Racing -> "cursors/racing.png"
        NativeMapCursorStyle.Electric -> "cursors/electric.png"
        NativeMapCursorStyle.City -> "cursors/city.png"
        NativeMapCursorStyle.Classic500 -> "cursors/classic500.png"
        NativeMapCursorStyle.Ostrich -> "cursors/ostrich.png"
    }

    private fun colorFilter(argb: Long): ColorMatrixColorFilter? {
        val degrees = when (argb) {
            0xFF5856D6 -> 8.0
            0xFF0A84FF -> -30.0
            0xFF34C759 -> -150.0
            0xFFFFCC00 -> 150.0
            0xFFFF9500 -> 120.0
            0xFFFF3B30 -> 90.0
            else -> return null
        }
        val radians = degrees * Math.PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return ColorMatrixColorFilter(
            floatArrayOf(
                .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
                .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
                .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private companion object {
        const val OSTRICH_FRAME_COUNT = 150
        const val OSTRICH_MOVING_KMH = 1.0
        const val OSTRICH_CYCLE_NANOS = 5_000_000_000L
        const val OSTRICH_SIZE_DP = 68f
        const val CURSOR_ASSET_TOP_DP = 14f
        const val POOF_DURATION_NANOS = 420_000_000L
        val POOF_PUFFS = arrayOf(
            floatArrayOf(0f, 0f, .34f),
            floatArrayOf(-.28f, -.12f, .22f),
            floatArrayOf(.30f, -.08f, .20f),
            floatArrayOf(-.14f, .26f, .20f),
            floatArrayOf(.20f, .24f, .18f),
        )
    }
}
