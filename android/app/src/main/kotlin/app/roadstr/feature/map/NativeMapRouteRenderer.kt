package app.roadstr.feature.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** MapLibre adapter for alternative, active, restricted, completed and traffic routes. */
internal class NativeMapRouteRenderer(
    displayDensity: Float,
    initialSnapshot: NativeRouteOverlaySnapshot,
) {
    private val haloWidth = NativeRouteLayerMetrics.widthPixels(
        NativeRouteLayerMetrics.HALO_LOGICAL_WIDTH,
        displayDensity,
    )
    private val coreWidth = NativeRouteLayerMetrics.widthPixels(
        NativeRouteLayerMetrics.CORE_LOGICAL_WIDTH,
        displayDensity,
    )
    private val completedWidth = NativeRouteLayerMetrics.widthPixels(
        NativeRouteLayerMetrics.COMPLETED_LOGICAL_WIDTH,
        displayDensity,
    )
    private val alternativeWidth = NativeRouteLayerMetrics.widthPixels(
        NativeRouteLayerMetrics.ALTERNATIVE_LOGICAL_WIDTH,
        displayDensity,
    )
    private val trafficWidth = NativeRouteLayerMetrics.widthPixels(
        NativeRouteLayerMetrics.TRAFFIC_LOGICAL_WIDTH,
        displayDensity,
    )

    private var payload = NativeRouteOverlayCompiler.compile(initialSnapshot)
    private var style: Style? = null

    fun update(snapshot: NativeRouteOverlaySnapshot) {
        val next = NativeRouteOverlayCompiler.compile(snapshot)
        payload = next
        style?.let(::applyPayload)
    }

    fun attach(style: Style) {
        this.style = style
        ensureSources(style)
        ensureLayers(style)
        applyPayload(style)
    }

    fun detach() {
        style = null
    }

    private fun ensureSources(style: Style) {
        if (style.getSource(ALTERNATIVES_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(ALTERNATIVES_SOURCE_ID, payload.alternativesGeoJson))
        }
        if (style.getSource(ACTIVE_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(ACTIVE_SOURCE_ID, payload.activeGeoJson))
        }
        if (style.getSource(COMPLETED_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(COMPLETED_SOURCE_ID, payload.completedGeoJson))
        }
        if (style.getSource(TRAFFIC_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(TRAFFIC_SOURCE_ID, payload.trafficGeoJson))
        }
    }

    private fun ensureLayers(style: Style) {
        if (style.getLayer(COMPLETED_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(COMPLETED_LAYER_ID, COMPLETED_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(COMPLETED_GREY_ARGB.toInt()),
                    PropertyFactory.lineWidth(completedWidth),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(ALTERNATIVES_LAYER_ID) == null) {
            style.addLayerBelow(
                LineLayer(ALTERNATIVES_LAYER_ID, ALTERNATIVES_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(ALTERNATIVE_GREY_ARGB.toInt()),
                    PropertyFactory.lineWidth(alternativeWidth),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
                COMPLETED_LAYER_ID,
            )
        }
        if (style.getLayer(ACTIVE_HALO_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(ACTIVE_HALO_LAYER_ID, ACTIVE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(routeColorExpression(payload.accentArgb, HALO_ALPHA)),
                    PropertyFactory.lineWidth(haloWidth),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(ACTIVE_CORE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(ACTIVE_CORE_LAYER_ID, ACTIVE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(routeColorExpression(payload.accentArgb, 1.0)),
                    PropertyFactory.lineWidth(coreWidth),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(TRAFFIC_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(TRAFFIC_LAYER_ID, TRAFFIC_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(TRAFFIC_RED_ARGB.toInt()),
                    PropertyFactory.lineWidth(trafficWidth),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
    }

    private fun applyPayload(style: Style) {
        val alternativesSource = style.getSource(ALTERNATIVES_SOURCE_ID) as? GeoJsonSource
            ?: error("Native alternative-route source has an unexpected type")
        val activeSource = style.getSource(ACTIVE_SOURCE_ID) as? GeoJsonSource
            ?: error("Native route source has an unexpected type")
        val completedSource = style.getSource(COMPLETED_SOURCE_ID) as? GeoJsonSource
            ?: error("Native completed-route source has an unexpected type")
        val trafficSource = style.getSource(TRAFFIC_SOURCE_ID) as? GeoJsonSource
            ?: error("Native traffic-route source has an unexpected type")
        alternativesSource.setGeoJson(payload.alternativesGeoJson)
        activeSource.setGeoJson(payload.activeGeoJson)
        completedSource.setGeoJson(payload.completedGeoJson)
        trafficSource.setGeoJson(payload.trafficGeoJson)

        (style.getLayer(ACTIVE_HALO_LAYER_ID) as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(routeColorExpression(payload.accentArgb, HALO_ALPHA)),
        )
        (style.getLayer(ACTIVE_CORE_LAYER_ID) as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(routeColorExpression(payload.accentArgb, 1.0)),
        )
    }

    private fun routeColorExpression(accentArgb: Long, alpha: Double): Expression =
        Expression.switchCase(
            Expression.eq(Expression.get(RESTRICTED_PROPERTY), true),
            colorExpression(ZTL_RED_ARGB, alpha),
            colorExpression(accentArgb, alpha),
        )

    private fun colorExpression(argb: Long, alpha: Double): Expression =
        Expression.rgba(
            (argb shr 16) and 0xFF,
            (argb shr 8) and 0xFF,
            argb and 0xFF,
            alpha,
        )

    companion object {
        const val ALTERNATIVES_SOURCE_ID = "roadstr-route-alternatives-source"
        const val ACTIVE_SOURCE_ID = "roadstr-route-active-source"
        const val COMPLETED_SOURCE_ID = "roadstr-route-completed-source"
        const val TRAFFIC_SOURCE_ID = "roadstr-route-traffic-source"
        const val ALTERNATIVES_LAYER_ID = "roadstr-route-alternatives"
        const val COMPLETED_LAYER_ID = "roadstr-route-completed"
        const val ACTIVE_HALO_LAYER_ID = "roadstr-route-active-halo"
        const val ACTIVE_CORE_LAYER_ID = "roadstr-route-active-core"
        const val TRAFFIC_LAYER_ID = "roadstr-route-traffic"
        const val RESTRICTED_PROPERTY = "restricted"

        const val ZTL_RED_ARGB = 0xFFE53935
        const val COMPLETED_GREY_ARGB = 0xFF9E9E9E
        const val ALTERNATIVE_GREY_ARGB = 0x99757575
        const val TRAFFIC_RED_ARGB = 0xFFEF4444
        const val HALO_ALPHA = 0.28
    }
}
