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

data class NativeMapDestinationPinSnapshot(
    val point: NativeMapPoint?,
    val colorArgb: Long,
)

/** A lightweight, non-interactive destination pin anchored by its tip. */
internal class NativeMapDestinationOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var map: MapLibreMap? = null
    private var snapshot = NativeMapDestinationPinSnapshot(
        point = null,
        colorArgb = NativeMapCursorVisualPolicy.DEFAULT_COLOR_ARGB,
    )
    private var coordinate: LatLng? = null
    private var fillColor: Long? = null

    private val pinPath = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-2f, -6f, -17f, -18f, -17f, -33f)
        cubicTo(-17f, -44f, -9.4f, -52f, 0f, -52f)
        cubicTo(9.4f, -52f, 17f, -44f, 17f, -33f)
        cubicTo(17f, -18f, 2f, -6f, 0f, 0f)
        close()
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f,
            0f,
            10f * density,
            intArrayOf(Color.argb(92, 0, 0, 0), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val centerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attach(map: MapLibreMap) {
        this.map = map
        postInvalidateOnAnimation()
    }

    fun update(value: NativeMapDestinationPinSnapshot) {
        if (snapshot == value) return
        snapshot = value
        coordinate = value.point?.let { LatLng(it.latitude, it.longitude) }
        if (fillColor != value.colorArgb) {
            fillColor = value.colorArgb
            fillPaint.shader = LinearGradient(
                0f,
                -52f,
                0f,
                0f,
                NativeMapCursorVisualPolicy.adjustLightness(value.colorArgb, 0.14),
                NativeMapCursorVisualPolicy.adjustLightness(value.colorArgb, -0.12),
                Shader.TileMode.CLAMP,
            )
        }
        postInvalidateOnAnimation()
    }

    fun refreshProjection() {
        if (coordinate != null) postInvalidateOnAnimation()
    }

    fun detach() {
        map = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val liveMap = map ?: return
        val pinCoordinate = coordinate ?: return
        val screen = liveMap.projection.toScreenLocation(pinCoordinate)
        val margin = PIN_HEIGHT_DP * density
        if (screen.x < -margin || screen.x > width + margin ||
            screen.y < -margin || screen.y > height + margin
        ) {
            return
        }

        canvas.save()
        canvas.translate(screen.x, screen.y)
        canvas.drawOval(-10f * density, -3f * density, 10f * density, 4f * density, shadowPaint)
        canvas.scale(density, density)
        canvas.drawPath(pinPath, fillPaint)
        canvas.drawPath(pinPath, borderPaint)
        canvas.drawCircle(0f, -34f, 6.5f, centerPaint)
        canvas.drawCircle(0f, -34f, 6.5f, centerBorderPaint)
        canvas.restore()
    }

    private companion object {
        const val PIN_HEIGHT_DP = 52f
    }
}
