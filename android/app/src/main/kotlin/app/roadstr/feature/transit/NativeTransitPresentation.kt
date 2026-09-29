package app.roadstr.feature.transit

import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.feature.map.NativeTransitOverlayCompiler
import app.roadstr.feature.map.NativeTransitOverlaySession
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeTransitUiStatus {
    Hidden,
    Loading,
    Ready,
    Unavailable,
    Failure,
}

enum class NativeTransitTransportMode {
    Driving,
    Cycling,
    Walking,
    Transit,
}

data class NativeTransitLegPresentation(
    val mode: TransitMode,
    val line: String?,
    val durationMinutes: Long,
    val backgroundArgb: Long?,
    val foregroundArgb: Long?,
)

data class NativeTransitItineraryPresentation(
    val durationLabel: String,
    val startTimeLabel: String,
    val endTimeLabel: String,
    val transfers: Int,
    val boardingName: String?,
    val boardingTimeLabel: String?,
    val accessWalkMinutes: Long,
    val walkingDistanceLabel: String,
    val scheduledTimes: Boolean,
    val legs: List<NativeTransitLegPresentation>,
)

data class NativeTransitUiSnapshot(
    val revision: Long,
    val status: NativeTransitUiStatus,
    val destinationLabel: String?,
    val itineraries: List<NativeTransitItineraryPresentation>,
    val selectedIndex: Int?,
) {
    companion object {
        fun hidden() = NativeTransitUiSnapshot(
            revision = NativeTransitOverlaySession.NO_TRANSIT_REVISION,
            status = NativeTransitUiStatus.Hidden,
            destinationLabel = null,
            itineraries = emptyList(),
            selectedIndex = null,
        )
    }
}

/** Pure Flutter-parity formatting and color projection for transit cards. */
object NativeTransitPresenter {
    private const val BLACK_ARGB = 0xFF00_0000L
    private const val WHITE_ARGB = 0xFFFF_FFFFL

    fun plan(
        plan: TransitParsedPlan,
        accentArgb: Long,
        imperial: Boolean,
        zoneId: ZoneId,
    ): List<NativeTransitItineraryPresentation> {
        NativeTransitOverlayCompiler.requireArgb(accentArgb, "Transit UI accent")
        require(plan.itineraries.isNotEmpty()) { "Transit UI plan must not be empty" }
        return plan.itineraries.take(NativeTransitOverlayCompiler.MAX_ITINERARIES).map { itinerary ->
            itinerary(itinerary, accentArgb, imperial, zoneId)
        }
    }

    fun itinerary(
        itinerary: TransitItinerary,
        accentArgb: Long,
        imperial: Boolean,
        zoneId: ZoneId,
    ): NativeTransitItineraryPresentation {
        require(itinerary.legs.isNotEmpty()) { "Transit UI itinerary must contain legs" }
        require(itinerary.legs.size <= NativeTransitOverlayCompiler.MAX_LEGS_PER_ITINERARY) {
            "Transit UI itinerary has too many legs"
        }
        val boarding = itinerary.boarding
        return NativeTransitItineraryPresentation(
            durationLabel = formatDuration(itinerary.durationSeconds),
            startTimeLabel = formatClock(itinerary.startTime, zoneId),
            endTimeLabel = formatClock(itinerary.endTime, zoneId),
            transfers = itinerary.transfers,
            boardingName = boarding?.name,
            boardingTimeLabel = boarding?.let { formatClock(it.time, zoneId) },
            accessWalkMinutes = itinerary.accessWalkSeconds / 60,
            walkingDistanceLabel = formatDistance(itinerary.walkingDistanceMeters, imperial),
            scheduledTimes = !itinerary.isFullyRealTime,
            legs = itinerary.legs.map { leg ->
                val line = leg.displayLine
                if (!leg.mode.isTransit || line == null) {
                    NativeTransitLegPresentation(
                        mode = leg.mode,
                        line = null,
                        durationMinutes = leg.durationSeconds / 60,
                        backgroundArgb = null,
                        foregroundArgb = null,
                    )
                } else {
                    val background = leg.routeColorArgb ?: accentArgb
                    NativeTransitOverlayCompiler.requireArgb(background, "Transit UI line color")
                    val foreground = leg.routeTextColorArgb ?: readableForeground(background)
                    NativeTransitOverlayCompiler.requireArgb(foreground, "Transit UI line text color")
                    NativeTransitLegPresentation(
                        mode = leg.mode,
                        line = line,
                        durationMinutes = leg.durationSeconds / 60,
                        backgroundArgb = background,
                        foregroundArgb = foreground,
                    )
                }
            },
        )
    }

    fun formatDuration(seconds: Long): String {
        val totalMinutes = seconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun formatClock(value: Instant, zoneId: ZoneId): String {
        val local = value.atZone(zoneId)
        return "%02d:%02d".format(Locale.ROOT, local.hour, local.minute)
    }

    fun formatDistance(meters: Double, imperial: Boolean): String {
        if (meters < 50) return if (imperial) "0 ft" else "0 m"
        if (imperial) {
            val feet = meters / 0.3048
            if (feet < 500) return "${((feet / 10).roundToInt() * 10)} ft"
            val miles = meters / 1_609.344
            return if (miles < 10) {
                "%.1f mi".format(Locale.ROOT, miles)
            } else {
                "${miles.roundToInt()} mi"
            }
        }
        if (meters < 1_000) return "${meters.roundToInt()} m"
        return "%.1f km".format(Locale.ROOT, meters / 1_000)
    }

    private fun readableForeground(backgroundArgb: Long): Long {
        fun channel(shift: Int): Double {
            val value = ((backgroundArgb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        return if (luminance > 0.5) BLACK_ARGB else WHITE_ARGB
    }
}

/**
 * Revision-safe in-memory owner for the dormant itinerary panel.
 *
 * It coordinates selection with the map overlay but owns no service, storage,
 * coordinates or provider errors. A future ViewModel may feed typed outcomes.
 */
class NativeTransitJourneySession(
    private val overlaySession: NativeTransitOverlaySession,
    initialAccentArgb: Long,
    initialImperial: Boolean = false,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeTransitUiSnapshot.hidden())
    private var revision = NativeTransitOverlaySession.NO_TRANSIT_REVISION
    private var accentArgb = initialAccentArgb
    private var imperial = initialImperial
    private var plan: TransitParsedPlan? = null

    val state: StateFlow<NativeTransitUiSnapshot> = _state.asStateFlow()

    init {
        NativeTransitOverlayCompiler.requireArgb(initialAccentArgb, "Transit UI accent")
    }

    fun begin(revision: Long, destinationLabel: String? = null): Boolean = synchronized(lock) {
        require(revision >= 0) { "Transit UI revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        plan = null
        publish(
            status = NativeTransitUiStatus.Loading,
            destinationLabel = normalizedLabel(destinationLabel),
        )
        true
    }

    fun submitPlan(
        revision: Long,
        plan: TransitParsedPlan,
        destinationLabel: String? = _state.value.destinationLabel,
        selectedIndex: Int = 0,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Transit UI revision must be non-negative" }
        if (revision < this.revision) return false
        if (revision == this.revision && _state.value.status != NativeTransitUiStatus.Loading) {
            return false
        }
        val itineraries = NativeTransitPresenter.plan(plan, accentArgb, imperial, zoneId)
        require(selectedIndex in itineraries.indices) {
            "Selected transit UI itinerary is outside the alternatives"
        }
        if (!overlaySession.submitPlan(revision, plan, selectedIndex)) return false
        this.revision = revision
        this.plan = plan
        _state.value = NativeTransitUiSnapshot(
            revision = revision,
            status = NativeTransitUiStatus.Ready,
            destinationLabel = normalizedLabel(destinationLabel),
            itineraries = itineraries,
            selectedIndex = selectedIndex,
        )
        true
    }

    fun submitUnavailable(
        revision: Long,
        destinationLabel: String? = _state.value.destinationLabel,
    ): Boolean = submitTerminal(revision, destinationLabel, NativeTransitUiStatus.Unavailable)

    fun submitFailure(
        revision: Long,
        destinationLabel: String? = _state.value.destinationLabel,
    ): Boolean = submitTerminal(revision, destinationLabel, NativeTransitUiStatus.Failure)

    fun select(revision: Long, selectedIndex: Int): Boolean = synchronized(lock) {
        val current = _state.value
        if (
            revision != this.revision ||
            current.status != NativeTransitUiStatus.Ready ||
            selectedIndex !in current.itineraries.indices ||
            selectedIndex == current.selectedIndex
        ) {
            return false
        }
        if (!overlaySession.selectItinerary(revision, selectedIndex)) return false
        _state.value = current.copy(selectedIndex = selectedIndex)
        true
    }

    fun clear(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Transit UI revision must be non-negative" }
        if (revision < this.revision) return false
        if (!overlaySession.clear(revision)) return false
        this.revision = revision
        plan = null
        _state.value = NativeTransitUiSnapshot.hidden().copy(revision = revision)
        true
    }

    fun updatePresentation(accentArgb: Long, imperial: Boolean): Boolean = synchronized(lock) {
        NativeTransitOverlayCompiler.requireArgb(accentArgb, "Transit UI accent")
        if (this.accentArgb == accentArgb && this.imperial == imperial) return false
        this.accentArgb = accentArgb
        this.imperial = imperial
        val currentPlan = plan
        if (currentPlan != null) {
            _state.value = _state.value.copy(
                itineraries = NativeTransitPresenter.plan(
                    currentPlan,
                    accentArgb,
                    imperial,
                    zoneId,
                ),
            )
        }
        true
    }

    private fun submitTerminal(
        revision: Long,
        destinationLabel: String?,
        status: NativeTransitUiStatus,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Transit UI revision must be non-negative" }
        if (revision < this.revision) return false
        if (revision == this.revision && _state.value.status != NativeTransitUiStatus.Loading) {
            return false
        }
        if (!overlaySession.clear(revision)) return false
        this.revision = revision
        plan = null
        publish(status, normalizedLabel(destinationLabel))
        true
    }

    private fun publish(status: NativeTransitUiStatus, destinationLabel: String?) {
        _state.value = NativeTransitUiSnapshot(
            revision = revision,
            status = status,
            destinationLabel = destinationLabel,
            itineraries = emptyList(),
            selectedIndex = null,
        )
    }

    private fun normalizedLabel(value: String?): String? =
        value?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_DESTINATION_LABEL_CHARS)

    companion object {
        const val MAX_DESTINATION_LABEL_CHARS = 1_000
    }
}
