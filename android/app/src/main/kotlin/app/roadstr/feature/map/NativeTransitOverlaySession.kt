package app.roadstr.feature.map

import app.roadstr.core.network.TransitParsedPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Revision-fenced owner of public-transport alternatives and their selected geometry. */
class NativeTransitOverlaySession(
    initialAccentArgb: Long,
    initialTextSecondaryArgb: Long,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(
        NativeTransitOverlaySnapshot.empty(initialAccentArgb, initialTextSecondaryArgb),
    )

    private var revision = NO_TRANSIT_REVISION
    private var itineraries: List<NativeTransitItinerary> = emptyList()
    private var selectedIndex: Int? = null
    private var accentArgb = initialAccentArgb
    private var textSecondaryArgb = initialTextSecondaryArgb

    val state: StateFlow<NativeTransitOverlaySnapshot> = _state.asStateFlow()

    init {
        validateTheme(initialAccentArgb, initialTextSecondaryArgb)
    }

    fun submitItineraries(
        revision: Long,
        itineraries: List<NativeTransitItinerary>,
        selectedIndex: Int = 0,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Transit revision must be non-negative" }
        if (revision <= this.revision) return false
        require(itineraries.isNotEmpty()) { "Transit itineraries must not be empty" }
        require(itineraries.size <= NativeTransitOverlayCompiler.MAX_ITINERARIES) {
            "Transit has too many itineraries"
        }
        require(selectedIndex in itineraries.indices) {
            "Selected transit itinerary is outside the alternatives"
        }
        val prepared = prepare(itineraries)
        this.revision = revision
        this.itineraries = prepared
        this.selectedIndex = selectedIndex
        publish()
        true
    }

    /** Projects a parsed provider plan without coupling the service to MapLibre types. */
    fun submitPlan(
        revision: Long,
        plan: TransitParsedPlan,
        selectedIndex: Int = 0,
    ): Boolean = submitItineraries(
        revision = revision,
        itineraries = NativeTransitOverlayProjection.fromPlan(plan),
        selectedIndex = selectedIndex,
    )

    fun selectItinerary(revision: Long, selectedIndex: Int): Boolean = synchronized(lock) {
        if (revision != this.revision || selectedIndex !in itineraries.indices) return false
        if (selectedIndex == this.selectedIndex) return false
        this.selectedIndex = selectedIndex
        publish()
        true
    }

    /** Hides the overlay and fences provider callbacks at or below [revision]. */
    fun clear(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Transit revision must be non-negative" }
        if (revision < this.revision) return false
        this.revision = revision
        itineraries = emptyList()
        selectedIndex = null
        publish()
        true
    }

    fun updateTheme(accentArgb: Long, textSecondaryArgb: Long): Boolean = synchronized(lock) {
        validateTheme(accentArgb, textSecondaryArgb)
        if (this.accentArgb == accentArgb && this.textSecondaryArgb == textSecondaryArgb) {
            return false
        }
        this.accentArgb = accentArgb
        this.textSecondaryArgb = textSecondaryArgb
        publish()
        true
    }

    private fun prepare(source: List<NativeTransitItinerary>): List<NativeTransitItinerary> {
        var totalPoints = 0L
        return source.map { itinerary ->
            require(itinerary.legs.isNotEmpty()) { "Transit itinerary must contain legs" }
            require(itinerary.legs.size <= NativeTransitOverlayCompiler.MAX_LEGS_PER_ITINERARY) {
                "Transit itinerary has too many legs"
            }
            NativeTransitItinerary(
                itinerary.legs.map { leg ->
                    leg.routeColorArgb?.let {
                        NativeTransitOverlayCompiler.requireArgb(it, "Transit route color")
                    }
                    totalPoints += leg.points.size.toLong()
                    require(totalPoints <= NativeTransitOverlayCompiler.MAX_TRANSIT_POINTS.toLong()) {
                        "Transit alternatives have too many points"
                    }
                    val points = leg.points.map { point ->
                        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
                            "Transit latitude is outside the WGS84 range"
                        }
                        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
                            "Transit longitude is outside the WGS84 range"
                        }
                        point
                    }
                    leg.copy(points = points)
                },
            )
        }
    }

    private fun publish() {
        val selected = selectedIndex
        _state.value = NativeTransitOverlaySnapshot(
            revision = revision,
            selectedItineraryIndex = selected,
            itineraryCount = itineraries.size,
            legs = selected?.let { itineraries[it].legs } ?: emptyList(),
            accentArgb = accentArgb,
            textSecondaryArgb = textSecondaryArgb,
        )
    }

    private fun validateTheme(accentArgb: Long, textSecondaryArgb: Long) {
        NativeTransitOverlayCompiler.requireArgb(accentArgb, "Transit accent")
        NativeTransitOverlayCompiler.requireArgb(
            textSecondaryArgb,
            "Transit secondary text color",
        )
    }

    companion object {
        const val NO_TRANSIT_REVISION = -1L
    }
}
