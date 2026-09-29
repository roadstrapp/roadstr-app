package app.roadstr.feature.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** MapLibre adapter for the selected public-transport itinerary. */
internal class NativeMapTransitRenderer(
    displayDensity: Float,
    initialSnapshot: NativeTransitOverlaySnapshot,
) {
    private val transitWidth = NativeTransitLayerMetrics.widthPixels(
        NativeTransitLayerMetrics.TRANSIT_LOGICAL_WIDTH,
        displayDensity,
    )
    private val streetWidth = NativeTransitLayerMetrics.widthPixels(
        NativeTransitLayerMetrics.STREET_LOGICAL_WIDTH,
        displayDensity,
    )
    private var payload = NativeTransitOverlayCompiler.compile(initialSnapshot)
    private var style: Style? = null

    fun update(snapshot: NativeTransitOverlaySnapshot) {
        payload = NativeTransitOverlayCompiler.compile(snapshot)
        style?.let(::applyPayload)
    }

    fun attach(style: Style) {
        this.style = style
        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, payload.geoJson))
        }
        if (style.getLayer(LAYER_ID) == null) {
            style.addLayer(
                LineLayer(LAYER_ID, SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(Expression.toColor(Expression.get(COLOR_PROPERTY))),
                    PropertyFactory.lineWidth(
                        Expression.switchCase(
                            Expression.eq(Expression.get(TRANSIT_PROPERTY), true),
                            Expression.literal(transitWidth),
                            Expression.literal(streetWidth),
                        ),
                    ),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        applyPayload(style)
    }

    fun detach() {
        style = null
    }

    private fun applyPayload(style: Style) {
        val source = style.getSource(SOURCE_ID) as? GeoJsonSource
            ?: error("Native transit source has an unexpected type")
        source.setGeoJson(payload.geoJson)
    }

    companion object {
        const val SOURCE_ID = "roadstr-transit-source"
        const val LAYER_ID = "roadstr-transit-legs"
        const val COLOR_PROPERTY = "color"
        const val TRANSIT_PROPERTY = "transit"
        const val STREET_ALPHA = 0.7
    }
}
