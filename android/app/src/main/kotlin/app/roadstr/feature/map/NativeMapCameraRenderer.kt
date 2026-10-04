package app.roadstr.feature.map

import org.maplibre.android.camera.CameraPosition
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
        val update = constrained.bounds?.let { bounds ->
            val mapBounds = LatLngBounds.Builder()
                .include(LatLng(bounds.southWest.latitude, bounds.southWest.longitude))
                .include(LatLng(bounds.northEast.latitude, bounds.northEast.longitude))
                .build()
            CameraUpdateFactory.newLatLngBounds(
                mapBounds,
                bounds.paddingLeftPixels,
                bounds.paddingTopPixels,
                bounds.paddingRightPixels,
                bounds.paddingBottomPixels,
            )
        } ?: run {
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
}
