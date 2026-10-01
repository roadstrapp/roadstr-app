package app.roadstr.feature.map

enum class NativeMapEngine(val storageValue: String) {
    MapLibre("maplibre"),
    LegacyRaster("osm");

    companion object {
        fun fromStorage(value: Any?): NativeMapEngine = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: MapLibre
    }
}

data class NativeMapEngineProfile(
    val engine: NativeMapEngine,
    val initialZoom: Double,
    val minimumZoom: Double,
    val maximumZoom: Double,
    val initialPitchDegrees: Double,
    val maximumPitchDegrees: Double,
    val tiltGesturesEnabled: Boolean,
) {
    init {
        require(minimumZoom <= initialZoom && initialZoom <= maximumZoom)
        require(initialPitchDegrees in 0.0..maximumPitchDegrees)
    }

    /** Keeps shared camera commands inside the selected renderer's shipped bounds. */
    fun constrain(command: NativeMapCameraCommand): NativeMapCameraCommand = command.copy(
        zoom = command.zoom.coerceIn(minimumZoom, maximumZoom),
        pitchDegrees = command.pitchDegrees.coerceIn(0.0, maximumPitchDegrees),
    )

    fun rasterStyle(dark: Boolean, tileUrl: String): String =
        NativeMapStyle.rasterStyle(dark = dark, tileUrl = tileUrl)
}

/**
 * Compatibility policy for the persisted Flutter `mapEngine` value.
 *
 * The old `osm` renderer remains a user-visible top-down raster mode, but is
 * implemented by the same native MapLibre host instead of retaining a second
 * Flutter renderer. Route, transit, marker and interaction layers therefore
 * keep one implementation while the old camera and zoom semantics survive.
 */
object NativeMapEngineCompatibility {
    val mapLibre = NativeMapEngineProfile(
        engine = NativeMapEngine.MapLibre,
        initialZoom = 17.0,
        minimumZoom = 0.0,
        maximumZoom = 25.5,
        initialPitchDegrees = 40.0,
        maximumPitchDegrees = 60.0,
        tiltGesturesEnabled = true,
    )

    val legacyRaster = NativeMapEngineProfile(
        engine = NativeMapEngine.LegacyRaster,
        initialZoom = 6.0,
        minimumZoom = 2.0,
        maximumZoom = 19.0,
        initialPitchDegrees = 0.0,
        maximumPitchDegrees = 0.0,
        tiltGesturesEnabled = false,
    )

    fun profile(engine: NativeMapEngine): NativeMapEngineProfile = when (engine) {
        NativeMapEngine.MapLibre -> mapLibre
        NativeMapEngine.LegacyRaster -> legacyRaster
    }

    fun rasterStyle(
        engine: NativeMapEngine,
        dark: Boolean,
        tileUrl: String,
    ): String = profile(engine).rasterStyle(dark = dark, tileUrl = tileUrl)
}
