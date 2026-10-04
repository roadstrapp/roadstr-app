package app.roadstr.feature.route

import app.roadstr.core.format.UnitFormatter
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteCandidate
import app.roadstr.feature.map.NativeRouteOverlayCompiler
import app.roadstr.feature.map.NativeRouteOverlaySession
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeRoutePlanningStatus {
    Hidden,
    Planner,
    Loading,
    Alternatives,
    Preview,
}

enum class NativeRouteTransportMode(val wireValue: String) {
    Driving("driving"),
    Cycling("cycling"),
    Walking("walking"),
    Transit("transit");

    companion object {
        fun fromWire(value: String?): NativeRouteTransportMode = entries.firstOrNull {
            it.wireValue == value?.trim()?.lowercase(Locale.ROOT)
        } ?: Driving
    }
}

enum class NativeRouteBadge {
    None,
    Fastest,
    AvoidHighwaysAndTolls,
    UnavoidableHighwayOrToll,
    AvoidUnpavedRoads,
}

data class NativeRoutePlannerStop(
    val id: Long,
    val query: String,
)

data class NativeRouteCardPresentation(
    val index: Int,
    val durationMinutes: Int,
    val durationHours: Int,
    val durationMinuteRemainder: Int,
    val distanceLabel: String,
    val badge: NativeRouteBadge,
)

data class NativeRouteConditionPresentation(
    val categoryLabel: String,
    val comment: String?,
)

data class NativeRouteWeatherPresentation(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val windKilometresPerHour: Double,
) {
    init {
        require(temperatureCelsius.isFinite() && temperatureCelsius in -100.0..100.0)
        require(weatherCode in 0..99)
        require(windKilometresPerHour.isFinite() && windKilometresPerHour in 0.0..500.0)
    }

    val emoji: String
        get() = when {
            weatherCode == 0 -> "☀️"
            weatherCode <= 2 -> "🌤️"
            weatherCode == 3 -> "☁️"
            weatherCode <= 48 -> "🌫️"
            weatherCode <= 55 -> "🌦️"
            weatherCode <= 65 -> "🌧️"
            weatherCode <= 77 -> "🌨️"
            weatherCode <= 82 -> "🌦️"
            else -> "⛈️"
        }
}

data class NativeRoutePlanningSnapshot(
    val revision: Long,
    val status: NativeRoutePlanningStatus,
    val originQuery: String,
    val stops: List<NativeRoutePlannerStop>,
    val activeStopIndex: Int?,
    val hasGps: Boolean,
    val mode: NativeRouteTransportMode,
    val alternatives: List<NativeRouteCardPresentation>,
    val selectedIndex: Int?,
    val destinationLabel: String?,
    val departureLabel: String?,
    val arrivalLabel: String?,
    val conditions: List<NativeRouteConditionPresentation>,
    val trafficStatus: String?,
    val weather: NativeRouteWeatherPresentation?,
    val avoidanceEnabled: Boolean,
    val avoidanceLoading: Boolean,
    val imperialUnits: Boolean,
) {
    val canCalculate: Boolean
        get() = originQuery.isNotBlank() && stops.isNotEmpty() && stops.all { it.query.isNotBlank() }

    companion object {
        const val NO_ROUTE_PLANNING_REVISION = -1L

        fun hidden(revision: Long = NO_ROUTE_PLANNING_REVISION) = NativeRoutePlanningSnapshot(
            revision = revision,
            status = NativeRoutePlanningStatus.Hidden,
            originQuery = "",
            stops = emptyList(),
            activeStopIndex = null,
            hasGps = false,
            mode = NativeRouteTransportMode.Driving,
            alternatives = emptyList(),
            selectedIndex = null,
            destinationLabel = null,
            departureLabel = null,
            arrivalLabel = null,
            conditions = emptyList(),
            trafficStatus = null,
            weather = null,
            avoidanceEnabled = false,
            avoidanceLoading = false,
            imperialUnits = false,
        )
    }
}

data class NativeRoutePlanningCandidate(
    val route: RoutingParsedRoute,
    val restricted: List<Boolean> = List(route.polyline.size) { false },
)

data class NativeRoutePreviewMetadata(
    val destinationLabel: String? = null,
    val trafficStatus: String? = null,
    val conditions: List<NativeRouteConditionPresentation> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
)

/** Pure bounded projection of Flutter route cards and pre-navigation summary. */
object NativeRoutePlanningPresenter {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val controls = Regex("[\\u0000-\\u001f]")

    fun alternatives(
        candidates: List<NativeRoutePlanningCandidate>,
        imperial: Boolean,
    ): List<NativeRouteCardPresentation> {
        require(candidates.isNotEmpty()) { "Route alternatives must not be empty" }
        require(candidates.size <= NativeRouteOverlayCompiler.MAX_ROUTE_ALTERNATIVES) {
            "Route has too many alternatives"
        }
        val formatter = UnitFormatter(imperial)
        return candidates.mapIndexed { index, candidate ->
            requireValidCandidate(candidate)
            val minutes = dartRound(candidate.route.totalDurationS / 60.0)
            NativeRouteCardPresentation(
                index = index,
                durationMinutes = minutes,
                durationHours = minutes / 60,
                durationMinuteRemainder = minutes % 60,
                distanceLabel = formatter.formatDistance(candidate.route.totalDistanceM),
                badge = badge(candidate.route, index == 0),
            )
        }
    }

    fun departureAndArrival(
        route: RoutingParsedRoute,
        now: LocalDateTime,
    ): Pair<String, String> {
        requireValidRoute(route)
        val arrival = now.plusSeconds(dartRoundLong(route.totalDurationS))
        return now.format(clock) to arrival.format(clock)
    }

    fun normalizeConditions(
        values: List<NativeRouteConditionPresentation>,
    ): List<NativeRouteConditionPresentation> = values.asSequence()
        .mapNotNull { value ->
            val category = normalizeText(value.categoryLabel, MAX_CATEGORY_CHARS) ?: return@mapNotNull null
            NativeRouteConditionPresentation(
                categoryLabel = category,
                comment = normalizeText(value.comment, MAX_COMMENT_CHARS),
            )
        }
        .take(MAX_CONDITIONS)
        .toList()

    fun normalizedLabel(value: String?, limit: Int = MAX_LABEL_CHARS): String? = normalizeText(value, limit)

    private fun badge(route: RoutingParsedRoute, fastest: Boolean): NativeRouteBadge = when (route.avoidance) {
        RoutingRouteAvoidance.OffRoadAvoided -> NativeRouteBadge.AvoidUnpavedRoads
        RoutingRouteAvoidance.HighwayAndTollFree -> NativeRouteBadge.AvoidHighwaysAndTolls
        RoutingRouteAvoidance.MinimizedHighwaysAndTolls -> NativeRouteBadge.UnavoidableHighwayOrToll
        RoutingRouteAvoidance.None -> if (fastest) NativeRouteBadge.Fastest else NativeRouteBadge.None
    }

    private fun requireValidCandidate(candidate: NativeRoutePlanningCandidate) {
        requireValidRoute(candidate.route)
        require(candidate.restricted.size == candidate.route.polyline.size) {
            "Route restriction flags must match route points"
        }
    }

    private fun requireValidRoute(route: RoutingParsedRoute) {
        require(route.polyline.size >= 2) { "Route must contain drawable geometry" }
        require(route.polyline.size <= NativeRouteOverlaySession.MAX_SESSION_ROUTE_POINTS) {
            "Route has too many preview points"
        }
        require(route.totalDistanceM.isFinite() && route.totalDistanceM > 0.0) {
            "Route distance must be finite and positive"
        }
        require(route.totalDurationS.isFinite() && route.totalDurationS > 0.0) {
            "Route duration must be finite and positive"
        }
        route.polyline.forEach { point ->
            require(
                point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
                    point.longitude.isFinite() && point.longitude in -180.0..180.0,
            ) { "Route contains an invalid coordinate" }
        }
    }

    private fun normalizeText(value: String?, limit: Int): String? = value
        ?.replace(controls, " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(limit)

    private fun dartRound(value: Double): Int = floor(value + 0.5).toInt()

    private fun dartRoundLong(value: Double): Long = floor(value + 0.5).toLong()

    const val MAX_LABEL_CHARS = 1_000
    const val MAX_QUERY_CHARS = 1_000
    const val MAX_CATEGORY_CHARS = 120
    const val MAX_COMMENT_CHARS = 500
    const val MAX_CONDITIONS = 3
}

/**
 * Revision-safe in-memory coordinator for the dormant planner and route sheets.
 *
 * It synchronizes alternative selection with the MapLibre overlay but owns no
 * geocoder, router, weather/event provider, location source, settings or storage.
 */
class NativeRoutePlanningSession(
    private val overlaySession: NativeRouteOverlaySession,
    initialImperial: Boolean = false,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeRoutePlanningSnapshot.hidden())
    private var revision = NativeRoutePlanningSnapshot.NO_ROUTE_PLANNING_REVISION
    private var imperial = initialImperial
    private var nextStopId = 1L
    private var candidates: List<NativeRoutePlanningCandidate> = emptyList()

    val state: StateFlow<NativeRoutePlanningSnapshot> = _state.asStateFlow()

    fun showPlanner(
        revision: Long,
        originQuery: String = "",
        destinationQuery: String = "",
        hasGps: Boolean = false,
        mode: NativeRouteTransportMode = NativeRouteTransportMode.Driving,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route planner revision must be non-negative" }
        if (revision <= this.revision) return false
        if (!overlaySession.clearRoute(revision)) return false
        this.revision = revision
        candidates = emptyList()
        _state.value = NativeRoutePlanningSnapshot.hidden(revision).copy(
            status = NativeRoutePlanningStatus.Planner,
            originQuery = query(originQuery),
            stops = listOf(NativeRoutePlannerStop(nextStopId++, query(destinationQuery))),
            hasGps = hasGps,
            mode = mode,
            imperialUnits = imperial,
        )
        true
    }

    fun failRouteRequest(revision: Long): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeRoutePlanningStatus.Loading) return false
        _state.value = current.copy(status = NativeRoutePlanningStatus.Planner)
        true
    }

    fun updateOrigin(revision: Long, value: String): Boolean = mutatePlanner(revision) { current ->
        current.copy(originQuery = query(value))
    }

    fun useMyLocation(revision: Long, localizedLabel: String): Boolean = mutatePlanner(revision) { current ->
        if (!current.hasGps) return@mutatePlanner current
        current.copy(originQuery = query(localizedLabel))
    }

    fun updateStop(revision: Long, index: Int, value: String): Boolean = mutatePlanner(revision) { current ->
        if (index !in current.stops.indices) return@mutatePlanner current
        current.copy(
            stops = current.stops.toMutableList().also {
                it[index] = it[index].copy(query = query(value))
            },
            activeStopIndex = index,
        )
    }

    fun addStop(revision: Long): Boolean = mutatePlanner(revision) { current ->
        if (current.stops.size >= MAX_STOPS) return@mutatePlanner current
        val stops = current.stops.toMutableList()
        stops.add(stops.lastIndex.coerceAtLeast(0), NativeRoutePlannerStop(nextStopId++, ""))
        current.copy(stops = stops, activeStopIndex = stops.lastIndex - 1)
    }

    fun removeStop(revision: Long, index: Int): Boolean = mutatePlanner(revision) { current ->
        if (current.stops.size <= 1 || index !in current.stops.indices) return@mutatePlanner current
        current.copy(
            stops = current.stops.filterIndexed { stopIndex, _ -> stopIndex != index },
            activeStopIndex = null,
        )
    }

    fun reorderStop(revision: Long, fromIndex: Int, toIndex: Int): Boolean = mutatePlanner(revision) { current ->
        if (fromIndex !in current.stops.indices || toIndex !in current.stops.indices || fromIndex == toIndex) {
            return@mutatePlanner current
        }
        val stops = current.stops.toMutableList()
        val moved = stops.removeAt(fromIndex)
        stops.add(toIndex, moved)
        current.copy(stops = stops, activeStopIndex = toIndex)
    }

    fun selectMode(revision: Long, mode: NativeRouteTransportMode): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status == NativeRoutePlanningStatus.Hidden || current.mode == mode) {
            return false
        }
        _state.value = current.copy(
            mode = mode,
            avoidanceEnabled = if (mode == NativeRouteTransportMode.Driving) current.avoidanceEnabled else false,
            avoidanceLoading = false,
        )
        true
    }

    fun beginRouteRequest(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route request revision must be non-negative" }
        val current = _state.value
        if (revision <= this.revision || current.status == NativeRoutePlanningStatus.Hidden || !current.canCalculate) {
            return false
        }
        this.revision = revision
        candidates = emptyList()
        _state.value = current.copy(
            revision = revision,
            status = NativeRoutePlanningStatus.Loading,
            alternatives = emptyList(),
            selectedIndex = null,
            avoidanceLoading = false,
        )
        true
    }

    fun submitAlternatives(
        revision: Long,
        values: List<NativeRoutePlanningCandidate>,
        selectedIndex: Int = 0,
        destinationLabel: String? = null,
    ): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeRoutePlanningStatus.Loading) return false
        val cards = NativeRoutePlanningPresenter.alternatives(values, imperial)
        require(selectedIndex in cards.indices) { "Selected route is outside alternatives" }
        if (!overlaySession.submitAlternatives(revision, values.map { it.overlay() }, selectedIndex)) {
            return false
        }
        candidates = values.toList()
        _state.value = current.copy(
            status = NativeRoutePlanningStatus.Alternatives,
            alternatives = cards,
            selectedIndex = selectedIndex,
            destinationLabel = NativeRoutePlanningPresenter.normalizedLabel(destinationLabel),
        )
        true
    }

    fun selectAlternative(revision: Long, selectedIndex: Int): Boolean = synchronized(lock) {
        val current = _state.value
        if (
            revision != this.revision ||
            current.status != NativeRoutePlanningStatus.Alternatives ||
            selectedIndex !in candidates.indices ||
            selectedIndex == current.selectedIndex
        ) {
            return false
        }
        if (!overlaySession.selectAlternative(revision, selectedIndex)) return false
        _state.value = current.copy(selectedIndex = selectedIndex)
        true
    }

    fun selectAlternativeAt(revision: Long, point: NativeMapPoint): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeRoutePlanningStatus.Alternatives) return false
        if (!overlaySession.selectAlternativeAt(revision, point)) return false
        val selected = overlaySession.state.value.selectedAlternativeIndex ?: return false
        _state.value = current.copy(selectedIndex = selected)
        true
    }

    fun setAvoidanceState(revision: Long, enabled: Boolean, loading: Boolean): Boolean = synchronized(lock) {
        val current = _state.value
        if (
            revision != this.revision ||
            current.status != NativeRoutePlanningStatus.Alternatives ||
            current.mode != NativeRouteTransportMode.Driving ||
            (current.avoidanceEnabled == enabled && current.avoidanceLoading == loading)
        ) {
            return false
        }
        _state.value = current.copy(avoidanceEnabled = enabled, avoidanceLoading = loading)
        true
    }

    fun updateWeather(revision: Long, weather: NativeRouteWeatherPresentation?): Boolean = synchronized(lock) {
        val current = _state.value
        if (
            revision != this.revision ||
            current.status != NativeRoutePlanningStatus.Alternatives ||
            current.weather == weather
        ) {
            return false
        }
        _state.value = current.copy(weather = weather)
        true
    }

    fun confirmSelection(
        revision: Long,
        metadata: NativeRoutePreviewMetadata = NativeRoutePreviewMetadata(),
    ): Boolean = synchronized(lock) {
        val current = _state.value
        val selected = current.selectedIndex
        if (
            revision != this.revision ||
            current.status != NativeRoutePlanningStatus.Alternatives ||
            selected == null ||
            selected !in candidates.indices
        ) {
            return false
        }
        if (!overlaySession.commitSelectedAlternative(revision)) return false
        val (departure, arrival) = NativeRoutePlanningPresenter.departureAndArrival(
            candidates[selected].route,
            metadata.now,
        )
        _state.value = current.copy(
            status = NativeRoutePlanningStatus.Preview,
            alternatives = listOf(current.alternatives[selected]),
            selectedIndex = 0,
            destinationLabel = NativeRoutePlanningPresenter.normalizedLabel(
                metadata.destinationLabel ?: current.destinationLabel,
            ),
            departureLabel = departure,
            arrivalLabel = arrival,
            trafficStatus = NativeRoutePlanningPresenter.normalizedLabel(metadata.trafficStatus, MAX_TRAFFIC_STATUS_CHARS),
            conditions = NativeRoutePlanningPresenter.normalizeConditions(metadata.conditions),
            avoidanceLoading = false,
        )
        candidates = listOf(candidates[selected])
        true
    }

    fun selectedNavigationRoute(revision: Long): RoutingParsedRoute? = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeRoutePlanningStatus.Preview) {
            return null
        }
        candidates.singleOrNull()?.route?.let { route ->
            route.copy(
                polyline = route.polyline.toList(),
                steps = route.steps.toList(),
                speedLimits = route.speedLimits.toList(),
            )
        }
    }

    /** Hides the preview while deliberately retaining its committed map route. */
    fun beginNavigation(revision: Long): Boolean = synchronized(lock) {
        val current = _state.value
        if (
            revision != this.revision ||
            current.status != NativeRoutePlanningStatus.Preview ||
            candidates.size != 1
        ) {
            return false
        }
        candidates = emptyList()
        _state.value = NativeRoutePlanningSnapshot.hidden(revision)
        true
    }

    /**
     * Advances the hidden planner fence after an in-navigation route
     * replacement without clearing the route owned by the map/navigation pair.
     */
    fun synchronizeNavigationRevision(revision: Long): Boolean = synchronized(lock) {
        if (
            revision <= this.revision ||
            _state.value.status != NativeRoutePlanningStatus.Hidden
        ) {
            return false
        }
        this.revision = revision
        _state.value = NativeRoutePlanningSnapshot.hidden(revision)
        true
    }

    fun updateUnits(imperial: Boolean): Boolean = synchronized(lock) {
        if (this.imperial == imperial) return false
        this.imperial = imperial
        _state.value = _state.value.copy(
            alternatives = if (candidates.isEmpty()) {
                _state.value.alternatives
            } else {
                NativeRoutePlanningPresenter.alternatives(candidates, imperial)
            },
            imperialUnits = imperial,
        )
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route planner revision must be non-negative" }
        if (revision < this.revision) return false
        if (!overlaySession.clearRoute(revision)) return false
        this.revision = revision
        candidates = emptyList()
        _state.value = NativeRoutePlanningSnapshot.hidden(revision)
        true
    }

    private fun mutatePlanner(
        revision: Long,
        transform: (NativeRoutePlanningSnapshot) -> NativeRoutePlanningSnapshot,
    ): Boolean = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeRoutePlanningStatus.Planner) return false
        val updated = transform(current)
        if (updated == current) return false
        _state.value = updated
        true
    }

    private fun query(value: String): String = value
        .replace(Regex("[\\u0000-\\u001f]"), " ")
        .take(NativeRoutePlanningPresenter.MAX_QUERY_CHARS)

    private fun NativeRoutePlanningCandidate.overlay() = NativeRouteCandidate(
        points = route.polyline.map { NativeMapPoint(it.latitude, it.longitude) },
        restricted = restricted.toList(),
    )

    companion object {
        const val MAX_STOPS = 5
        const val MAX_TRAFFIC_STATUS_CHARS = 240
    }
}
