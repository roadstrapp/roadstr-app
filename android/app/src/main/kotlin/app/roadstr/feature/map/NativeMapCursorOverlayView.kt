package app.roadstr.feature.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

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
        this.snapshot = snapshot
        invalidate()
    }

    fun refreshProjection() {
        if (snapshot.point != null) invalidate()
    }

    fun detach() {
        map = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val liveMap = map ?: return
        val point = snapshot.point ?: return
        val screen = liveMap.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
        val visual = NativeMapCursorVisualPolicy.frame(liveMap.cameraPosition.tilt)
        val scale = density * NativeMapCursorVisualPolicy.DISPLAY_SCALE.toFloat()

        canvas.save()
        canvas.translate(screen.x, screen.y)
        canvas.scale(scale, scale * visual.flatYScale.toFloat())
        canvas.translate(
            (-NativeMapCursorVisualPolicy.WIDTH_DP / 2.0).toFloat(),
            (-NativeMapCursorVisualPolicy.HEIGHT_DP / 2.0).toFloat(),
        )
        drawShadow(canvas, visual)
        drawArrow(canvas)
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

    private fun drawArrow(canvas: Canvas) {
        val color = snapshot.colorArgb
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
}
