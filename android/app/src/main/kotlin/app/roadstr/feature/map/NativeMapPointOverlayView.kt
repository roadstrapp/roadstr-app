package app.roadstr.feature.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.max

/** Non-interactive billboard painter for bounded MapLibre point overlays. */
internal class NativeMapPointOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var map: MapLibreMap? = null
    private var snapshot = NativeMapPointOverlaySnapshot.Empty
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MAP_OVERLAY_DARK_ARGB.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(199, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(26, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
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

    fun update(snapshot: NativeMapPointOverlaySnapshot) {
        this.snapshot = snapshot
        invalidate()
    }

    fun refreshProjection() {
        if (snapshot.markers.isNotEmpty()) invalidate()
    }

    fun hitRoadEvent(tap: LatLng): NativeMapPointOverlayMarker? {
        val liveMap = map ?: return null
        val tapScreen = liveMap.projection.toScreenLocation(tap)
        return NativeMapInteractionPolicy.hitRoadEvent(
            snapshot = snapshot,
            zoom = liveMap.cameraPosition.zoom,
            density = density.toDouble(),
            tap = NativeMapScreenPoint(tapScreen.x.toDouble(), tapScreen.y.toDouble()),
        ) { point ->
            val projected = liveMap.projection.toScreenLocation(
                LatLng(point.latitude, point.longitude),
            )
            NativeMapScreenPoint(projected.x.toDouble(), projected.y.toDouble())
        }
    }

    val revision: Long
        get() = snapshot.revision

    fun detach() {
        map = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val liveMap = map ?: return
        val markers = NativeMapPointOverlayPolicy.visibleAtZoom(
            snapshot = snapshot,
            zoom = liveMap.cameraPosition.zoom,
        )
        markers.forEach { marker ->
            val screen = liveMap.projection.toScreenLocation(
                LatLng(marker.point.latitude, marker.point.longitude),
            )
            val radius = marker.kind.sizeDp.toFloat() * density / 2f
            if (screen.x < -radius || screen.x > width + radius ||
                screen.y < -radius || screen.y > height + radius
            ) {
                return@forEach
            }
            drawMarker(canvas, screen.x, screen.y, radius, marker.kind)
        }
    }

    private fun drawMarker(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        kind: NativeMapPointOverlayKind,
    ) {
        if (kind == NativeMapPointOverlayKind.Parking) {
            drawParking(canvas, centerX, centerY, radius)
            return
        }
        val accent = kind.accentArgb.toInt()
        val glowAlpha = if (kind.emphasized) 122 else 71
        outerPaint.shader = null
        outerPaint.color = Color.argb(glowAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(centerX, centerY + 2f * density, radius + if (kind.emphasized) density else 0f, outerPaint)
        outerPaint.shader = LinearGradient(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            withAlpha(accent, if (kind.emphasized) 245 else 199),
            withAlpha(accent, if (kind.emphasized) 173 else 107),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius, outerPaint)
        outerPaint.shader = null
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        val innerRadius = max(0f, radius - 2.5f * density)
        canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)
        canvas.drawCircle(centerX, centerY, innerRadius, innerBorderPaint)
        drawSymbol(
            canvas = canvas,
            symbol = kind.symbol,
            centerX = centerX,
            centerY = centerY,
            textSizeDp = if (kind.emphasized) 15f else 12.5f,
            bold = false,
        )
    }

    private fun drawParking(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        outerPaint.shader = null
        outerPaint.color = Color.argb(97, 0, 0, 0)
        canvas.drawCircle(centerX, centerY + 2f * density, radius + density, outerPaint)
        outerPaint.color = NativeMapPointOverlayKind.Parking.accentArgb.toInt()
        canvas.drawCircle(centerX, centerY, radius, outerPaint)
        val previousWidth = borderPaint.strokeWidth
        borderPaint.strokeWidth = 2f * density
        canvas.drawCircle(centerX, centerY, radius - density, borderPaint)
        borderPaint.strokeWidth = previousWidth
        drawSymbol(canvas, "P", centerX, centerY, 20f, bold = true)
    }

    private fun drawSymbol(
        canvas: Canvas,
        symbol: String,
        centerX: Float,
        centerY: Float,
        textSizeDp: Float,
        bold: Boolean,
    ) {
        symbolPaint.textSize = textSizeDp * density
        symbolPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val metrics = symbolPaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(symbol, centerX, baseline, symbolPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val MAP_OVERLAY_DARK_ARGB = 0xE6111018
    }
}
