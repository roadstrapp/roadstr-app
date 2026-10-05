package app.roadstr.feature.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Closed marker catalogue matching the point overlays rendered by Flutter. */
enum class NativeMapPointOverlayKind(
    val symbol: String,
    val accentArgb: Long,
    val sizeDp: Double,
    val minimumZoom: Double,
    val emphasized: Boolean,
    internal val drawOrder: Int,
) {
    RoadPolice("👮", 0xFF2563EB, 36.0, 11.0, true, 0),
    RoadPoliceStation("🏛️", 0xFF1E3A8A, 36.0, 11.0, true, 0),
    RoadSpeedCamera("📷", 0xFF7C3AED, 36.0, 11.0, true, 0),
    RoadTrafficJam("🚗", 0xFFD97706, 36.0, 11.0, true, 0),
    RoadAccident("💥", 0xFFDC2626, 36.0, 11.0, true, 0),
    RoadClosure("🚫", 0xFF991B1B, 36.0, 11.0, true, 0),
    RoadConstruction("🚧", 0xFFF59E0B, 36.0, 11.0, true, 0),
    RoadHazard("⚠️", 0xFFF59E0B, 36.0, 11.0, true, 0),
    RoadCondition("🛣️", 0xFF92400E, 36.0, 11.0, true, 0),
    RoadPothole("🕳️", 0xFF6B7280, 36.0, 11.0, true, 0),
    RoadFog("🌫️", 0xFF9CA3AF, 36.0, 11.0, true, 0),
    RoadIce("🧊", 0xFF60A5FA, 36.0, 11.0, true, 0),
    RoadAnimal("🦌", 0xFF16A34A, 36.0, 11.0, true, 0),
    RoadOther("ℹ️", 0xFF6B7280, 36.0, 11.0, true, 0),
    OsmSpeedCamera("📷", 0xFF7C3AED, 30.0, Double.NEGATIVE_INFINITY, false, 1),
    Parking("P", 0xFF1E88E5, 38.0, Double.NEGATIVE_INFINITY, false, 2),
    TrafficLight("🚦", 0xFF70D69B, 24.0, 15.0, false, 3),
    SpeedBump("〰️", 0xFFFF8547, 22.0, 15.0, false, 4),
    Crosswalk("🚸", 0xFFFFB347, 20.0, 16.0, false, 5),
    DiscoveryResult("📍", 0xFF7C4DFF, 34.0, Double.NEGATIVE_INFINITY, false, 6);

    /** A tap on this marker opens a place sheet rather than a road report. */
    val opensPlaceDetail: Boolean
        get() = this == DiscoveryResult

    val opensRoadEventDetail: Boolean
        get() = when (this) {
            RoadPolice,
            RoadPoliceStation,
            RoadSpeedCamera,
            RoadTrafficJam,
            RoadAccident,
            RoadClosure,
            RoadConstruction,
            RoadHazard,
            RoadCondition,
            RoadPothole,
            RoadFog,
            RoadIce,
            RoadAnimal,
            RoadOther -> true
            else -> false
        }
}

data class NativeMapPointOverlayMarker(
    val id: String,
    val point: NativeMapPoint,
    val kind: NativeMapPointOverlayKind,
)

data class NativeMapPointOverlaySnapshot(
    val revision: Long,
    val markers: List<NativeMapPointOverlayMarker>,
) {
    companion object {
        val Empty = NativeMapPointOverlaySnapshot(revision = -1L, markers = emptyList())
    }
}

/** Validation and visibility rules shared by the session and Android painter. */
object NativeMapPointOverlayPolicy {
    const val MAX_MARKERS = 4_096
    const val MAX_ID_LENGTH = 128

    fun normalize(markers: List<NativeMapPointOverlayMarker>): List<NativeMapPointOverlayMarker> {
        require(markers.size <= MAX_MARKERS) { "Point overlay contains too many markers" }
        val ids = HashSet<String>(markers.size)
        markers.forEach { marker ->
            require(marker.id.isNotBlank() && marker.id.length <= MAX_ID_LENGTH) {
                "Point overlay marker id is invalid"
            }
            require(ids.add(marker.id)) { "Point overlay marker ids must be unique" }
            requireValidPoint(marker.point)
        }
        return markers.withIndex()
            .sortedWith(compareBy<IndexedValue<NativeMapPointOverlayMarker>> { it.value.kind.drawOrder }
                .thenBy { it.index })
            .map { it.value }
    }

    fun visibleAtZoom(
        snapshot: NativeMapPointOverlaySnapshot,
        zoom: Double,
    ): List<NativeMapPointOverlayMarker> {
        require(zoom.isFinite()) { "Map zoom must be finite" }
        return snapshot.markers.filter { zoom >= it.kind.minimumZoom }
    }

    private fun requireValidPoint(point: NativeMapPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Point overlay latitude is outside the WGS84 range"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Point overlay longitude is outside the WGS84 range"
        }
    }
}

/** Bulk-replacement boundary for independently fetched point-overlay caches. */
class NativeMapPointOverlaySession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeMapPointOverlaySnapshot.Empty)

    val state: StateFlow<NativeMapPointOverlaySnapshot> = _state.asStateFlow()

    fun replace(revision: Long, markers: List<NativeMapPointOverlayMarker>): Boolean =
        synchronized(lock) {
            require(revision >= 0) { "Point overlay revision must be non-negative" }
            if (revision <= _state.value.revision) return false
            val normalized = NativeMapPointOverlayPolicy.normalize(markers)
            _state.value = NativeMapPointOverlaySnapshot(revision, normalized)
            true
        }

    /** Clears every point source and fences callbacks at or below [revision]. */
    fun clear(revision: Long): Boolean = replace(revision, emptyList())
}
