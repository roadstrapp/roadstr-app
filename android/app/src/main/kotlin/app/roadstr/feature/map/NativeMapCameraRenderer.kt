package app.roadstr.feature.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/** Imperative, sequence-safe adapter from camera commands to MapLibre. */
internal class NativeMapCameraRenderer(
    private val engineProfile: NativeMapEngineProfile,
) {
    private var map: MapLibreMap? = null
    private var pending: NativeMapCameraCommand? = null
    private var appliedSequence = 0L

    fun attach(map: MapLibreMap) {
        if (this.map !== map) appliedSequence = 0L
        this.map = map
        pending?.let(::apply)
    }

    fun update(command: NativeMapCameraCommand?) {
        if (command == null || command.sequence <= (pending?.sequence ?: 0L)) return
        pending = command
        if (command.sequence > appliedSequence) apply(command)
    }

    fun detach() {
        map = null
    }

    private fun apply(command: NativeMapCameraCommand) {
        val liveMap = map ?: return
        if (command.sequence <= appliedSequence) return
        val constrained = engineProfile.constrain(command)
        val bounds = constrained.bounds
        val update = if (bounds != null) {
            boundsUpdate(liveMap, bounds)
        } else {
            val position = CameraPosition.Builder()
                .target(LatLng(constrained.center.latitude, constrained.center.longitude))
                .zoom(constrained.zoom)
                .bearing(constrained.bearingDegrees)
                .tilt(constrained.pitchDegrees)
                .padding(
                    constrained.paddingLeftPixels,
                    constrained.paddingTopPixels,
                    constrained.paddingRightPixels,
                    constrained.paddingBottomPixels,
                )
                .build()
            CameraUpdateFactory.newCameraPosition(position)
        }
        when (constrained.motion) {
            NativeMapCameraMotion.Move -> liveMap.moveCamera(update)
            NativeMapCameraMotion.Ease -> liveMap.easeCamera(
                update,
                constrained.durationMillis,
                false,
            )
        }
        appliedSequence = command.sequence
    }

    /**
     * Frames [bounds] flat and north-up in the part of the screen the insets
     * leave free.
     *
     * Only the zoom comes from MapLibre's own fit (it already accounts for the
     * insets). The target is the true Mercator centre of the box and the insets
     * go on the camera itself, so the box lands in the middle of the visible
     * area; the camera's pitch, bearing and any padding that follow mode left
     * behind are all replaced rather than inherited.
     */
    private fun boundsUpdate(liveMap: MapLibreMap, bounds: NativeMapCameraBounds): CameraUpdate {
        val mapBounds = LatLngBounds.Builder()
            .include(LatLng(bounds.southWest.latitude, bounds.southWest.longitude))
            .include(LatLng(bounds.northEast.latitude, bounds.northEast.longitude))
            .build()
        val insets = intArrayOf(
            bounds.paddingLeftPixels,
            bounds.paddingTopPixels,
            bounds.paddingRightPixels,
            bounds.paddingBottomPixels,
        )
        val fitted = liveMap.getCameraForLatLngBounds(mapBounds, insets, 0.0, 0.0)
            ?: return CameraUpdateFactory.newLatLngBounds(
                mapBounds,
                insets[0],
                insets[1],
                insets[2],
                insets[3],
            )
        val position = CameraPosition.Builder()
            .target(mercatorCentre(bounds))
            .zoom(fitted.zoom.coerceIn(engineProfile.minimumZoom, engineProfile.maximumZoom))
            .bearing(0.0)
            .tilt(0.0)
            .padding(
                insets[0].toDouble(),
                insets[1].toDouble(),
                insets[2].toDouble(),
                insets[3].toDouble(),
            )
            .build()
        return CameraUpdateFactory.newCameraPosition(position)
    }

    private fun mercatorCentre(bounds: NativeMapCameraBounds): LatLng {
        fun y(latitude: Double): Double =
            ln(tan(PI / 4 + Math.toRadians(latitude.coerceIn(-85.0, 85.0)) / 2))
        val middle = (y(bounds.southWest.latitude) + y(bounds.northEast.latitude)) / 2
        val latitude = Math.toDegrees(2 * atan(exp(middle)) - PI / 2)
        val longitude = (bounds.southWest.longitude + bounds.northEast.longitude) / 2
        return LatLng(latitude, longitude)
    }
}
