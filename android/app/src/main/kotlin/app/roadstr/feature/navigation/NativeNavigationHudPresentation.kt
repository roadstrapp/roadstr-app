package app.roadstr.feature.navigation

import app.roadstr.core.format.UnitFormatter
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponseProtocol
import app.roadstr.core.network.RoutingResponseStep
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeNavigationHudStatus {
    Hidden,
    Active,
}

enum class NativeManeuverKind {
    Straight,
    SlightLeft,
    Left,
    SharpLeft,
    SlightRight,
    Right,
    SharpRight,
    UTurnLeft,
    UTurnRight,
    ForkLeft,
    ForkRight,
    MergeLeft,
    MergeRight,
    RampLeft,
    RampRight,
    ExitLeft,
    ExitRight,
    Roundabout,
    Arrive,
    Depart,
    Ferry,
}

enum class NativeArrivalSide {
    None,
    Ahead,
    Left,
    Right,
}

enum class NativeSpeedometerStyle(val storageValue: String) {
    Classic("classic"),
    Digital("digital"),
    Analog("analog"),
    Sport("sport"),
    Minimal("minimal");

    companion object {
        fun fromStorage(value: Any?): NativeSpeedometerStyle = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Classic
    }
}

data class NativeManeuverVisual(
    val kind: NativeManeuverKind,
    val roundaboutExit: Int? = null,
    val roundaboutArmCount: Int? = null,
)

data class NativeNavigationStepPresentation(
    val index: Int,
    val instruction: String,
    val roadName: String?,
    val arrivalSide: NativeArrivalSide,
    val distanceLabel: String,
    val prominentDistance: Boolean,
    val maneuver: NativeManeuverVisual,
)

data class NativeNavigationHudInput(
    val route: RoutingParsedRoute,
    val stepIndex: Int,
    val distanceToManeuverM: Double = 0.0,
    val remainingDistanceM: Double = 0.0,
    val remainingSeconds: Double = 0.0,
    val speedKmh: Double = 0.0,
    val speedLimitKmh: Int? = null,
    val altitudeM: Double? = null,
    val showAltitude: Boolean = false,
    val voiceMuted: Boolean = false,
    val speedometerStyle: NativeSpeedometerStyle = NativeSpeedometerStyle.Classic,
    val imperial: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
)

data class NativeNavigationHudSnapshot(
    val revision: Long,
    val status: NativeNavigationHudStatus,
    val current: NativeNavigationStepPresentation?,
    val next: NativeNavigationStepPresentation?,
    val speed: Int,
    val speedUnit: String,
    val overSpeedLimit: Boolean,
    val speedLimit: Int?,
    val altitudeLabel: String?,
    val remainingDistanceLabel: String?,
    val durationMinutes: Int,
    val durationHours: Int,
    val durationMinuteRemainder: Int,
    val etaLabel: String?,
    val voiceMuted: Boolean,
    val speedometerStyle: NativeSpeedometerStyle,
) {
    companion object {
        const val NO_NAVIGATION_REVISION = -1L

        fun hidden(revision: Long = NO_NAVIGATION_REVISION) = NativeNavigationHudSnapshot(
            revision = revision,
            status = NativeNavigationHudStatus.Hidden,
            current = null,
            next = null,
            speed = 0,
            speedUnit = "km/h",
            overSpeedLimit = false,
            speedLimit = null,
            altitudeLabel = null,
            remainingDistanceLabel = null,
            durationMinutes = 0,
            durationHours = 0,
            durationMinuteRemainder = 0,
            etaLabel = null,
            voiceMuted = false,
            speedometerStyle = NativeSpeedometerStyle.Classic,
        )
    }
}

/** Pure bounded projection of Flutter's navigation banner and bottom panel. */
object NativeNavigationHudPresenter {
    private val controls = Regex("[\\u0000-\\u001f]")
    private val longStraightDirections = setOf(
        "new name",
        "continue",
        "notification",
        "use lane",
        "depart",
    )

    fun project(
        revision: Long,
        input: NativeNavigationHudInput,
        nowLabel: String,
    ): NativeNavigationHudSnapshot {
        require(revision >= 0) { "Navigation revision must be non-negative" }
        requireValid(input)
        val formatter = UnitFormatter(input.imperial)
        val currentStep = input.route.steps[input.stepIndex]
        val distance = input.distanceToManeuverM.takeIf { it > 0 } ?: currentStep.distanceM
        val current = step(
            currentStep,
            index = input.stepIndex,
            distanceLabel = formatter.formatDistance(distance, nowLabel),
            prominentDistance = currentStep.distanceM > 2_000 &&
                currentStep.direction.lowercase(Locale.ROOT).trim() in longStraightDirections,
        )
        val nextStep = input.route.steps.getOrNull(input.stepIndex + 1)
            ?.takeUnless { it.direction.lowercase(Locale.ROOT).trim() == "arrive" }
        val next = nextStep?.let {
            step(
                it,
                index = input.stepIndex + 1,
                distanceLabel = formatter.formatDistance(currentStep.distanceM),
                prominentDistance = false,
            ).copy(instruction = uncapitalised(clean(it.instruction, 1_000)))
        }
        val remainingDistance = input.remainingDistanceM.takeIf { it > 0 }
            ?: input.route.totalDistanceM
        val remainingSeconds = input.remainingSeconds.takeIf { it > 0 }
            ?: input.route.totalDurationS
        val totalMinutes = dartRound(remainingSeconds / 60.0).coerceAtLeast(0)
        val maxDisplaySpeed = if (input.imperial) 130.0 else 200.0
        val displaySpeed = formatter.toDisplaySpeed(input.speedKmh)
            .coerceIn(0.0, maxDisplaySpeed)
        val displayLimit = input.speedLimitKmh?.let {
            dartRound(formatter.toDisplaySpeed(it.toDouble()))
        }

        return NativeNavigationHudSnapshot(
            revision = revision,
            status = NativeNavigationHudStatus.Active,
            current = current,
            next = next,
            speed = dartRound(displaySpeed),
            speedUnit = formatter.speedUnit,
            overSpeedLimit = input.speedLimitKmh?.let { input.speedKmh > it } ?: false,
            speedLimit = displayLimit,
            altitudeLabel = input.altitudeM
                ?.takeIf { input.showAltitude }
                ?.let(formatter::formatAltitude),
            remainingDistanceLabel = formatter.formatDistance(remainingDistance),
            durationMinutes = totalMinutes,
            durationHours = totalMinutes / 60,
            durationMinuteRemainder = totalMinutes % 60,
            etaLabel = input.now
                .plusSeconds(dartRound(remainingSeconds).toLong())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)),
            voiceMuted = input.voiceMuted,
            speedometerStyle = input.speedometerStyle,
        )
    }

    fun maneuver(step: RoutingResponseStep): NativeManeuverVisual {
        val direction = step.direction.lowercase(Locale.ROOT).trim()
        val modifier = step.modifier.lowercase(Locale.ROOT).trim()
        val left = modifier.contains("left")
        val right = modifier.contains("right")
        val kind = when (direction) {
            "arrive" -> NativeManeuverKind.Arrive
            "depart" -> NativeManeuverKind.Depart
            "roundabout", "rotary" -> NativeManeuverKind.Roundabout
            "fork", "use lane" -> if (left) NativeManeuverKind.ForkLeft else NativeManeuverKind.ForkRight
            "merge" -> if (left) NativeManeuverKind.MergeLeft else NativeManeuverKind.MergeRight
            "on ramp" -> if (left) NativeManeuverKind.RampLeft else NativeManeuverKind.RampRight
            "off ramp" -> if (left) NativeManeuverKind.ExitLeft else NativeManeuverKind.ExitRight
            "ferry" -> NativeManeuverKind.Ferry
            else -> when (modifier) {
                "slight left" -> NativeManeuverKind.SlightLeft
                "left" -> NativeManeuverKind.Left
                "sharp left" -> NativeManeuverKind.SharpLeft
                "slight right" -> NativeManeuverKind.SlightRight
                "right" -> NativeManeuverKind.Right
                "sharp right" -> NativeManeuverKind.SharpRight
                "uturn left" -> NativeManeuverKind.UTurnLeft
                "uturn right" -> NativeManeuverKind.UTurnRight
                "uturn" -> if (right) NativeManeuverKind.UTurnRight else NativeManeuverKind.UTurnLeft
                else -> NativeManeuverKind.Straight
            }
        }
        return NativeManeuverVisual(
            kind = kind,
            roundaboutExit = step.exitNumber
                ?.coerceIn(1, RoutingResponseProtocol.MAX_ROUNDABOUT_ARMS)
                ?.takeIf { kind == NativeManeuverKind.Roundabout },
            roundaboutArmCount = step.roundaboutArmCount
                ?.coerceIn(3, RoutingResponseProtocol.MAX_ROUNDABOUT_ARMS)
                ?.takeIf { kind == NativeManeuverKind.Roundabout },
        )
    }

    fun uncapitalised(value: String): String {
        if (value.length < 2) return value
        val second = value[1]
        if (second.isUpperCase()) return value
        return value.replaceFirstChar { it.lowercase(Locale.ROOT) }
    }

    private fun step(
        value: RoutingResponseStep,
        index: Int,
        distanceLabel: String,
        prominentDistance: Boolean,
    ): NativeNavigationStepPresentation {
        val direction = value.direction.lowercase(Locale.ROOT).trim()
        val arrival = if (direction == "arrive") {
            when (value.modifier.lowercase(Locale.ROOT).trim()) {
                "left" -> NativeArrivalSide.Left
                "right" -> NativeArrivalSide.Right
                else -> NativeArrivalSide.Ahead
            }
        } else {
            NativeArrivalSide.None
        }
        return NativeNavigationStepPresentation(
            index = index,
            instruction = clean(value.instruction, 1_000),
            roadName = clean(value.roadName, 200).takeIf { it.isNotEmpty() },
            arrivalSide = arrival,
            distanceLabel = distanceLabel,
            prominentDistance = prominentDistance,
            maneuver = maneuver(value),
        )
    }

    private fun requireValid(input: NativeNavigationHudInput) {
        require(input.route.steps.isNotEmpty()) { "Navigation route must contain a step" }
        require(input.route.steps.size <= RoutingResponseProtocol.MAX_ROUTE_STEPS) {
            "Navigation route contains too many steps"
        }
        require(input.stepIndex in input.route.steps.indices) { "Navigation step index is invalid" }
        requireFiniteNonNegative(input.route.totalDistanceM, "route distance")
        requireFiniteNonNegative(input.route.totalDurationS, "route duration")
        requireFiniteNonNegative(input.distanceToManeuverM, "maneuver distance")
        requireFiniteNonNegative(input.remainingDistanceM, "remaining distance")
        requireFiniteNonNegative(input.remainingSeconds, "remaining duration")
        requireFiniteNonNegative(input.speedKmh, "speed")
        require(input.speedKmh <= 1_000) { "Navigation speed is invalid" }
        require(input.speedLimitKmh == null || input.speedLimitKmh in 1..500) {
            "Navigation speed limit is invalid"
        }
        require(input.altitudeM == null || input.altitudeM.isFinite()) {
            "Navigation altitude is invalid"
        }
    }

    private fun requireFiniteNonNegative(value: Double, label: String) {
        require(value.isFinite() && value >= 0) { "Navigation $label is invalid" }
    }

    private fun clean(value: String, max: Int): String = value
        .replace(controls, " ")
        .trim()
        .let { if (it.length <= max) it else it.substring(0, max) + "…" }

    private fun dartRound(value: Double): Int = floor(value + 0.5).toInt()
}

/** Revision-safe value owner. It deliberately owns no GPS, route service or storage. */
class NativeNavigationHudSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeNavigationHudSnapshot.hidden())
    private var revision = NativeNavigationHudSnapshot.NO_NAVIGATION_REVISION

    val state: StateFlow<NativeNavigationHudSnapshot> = _state.asStateFlow()

    fun show(revision: Long, input: NativeNavigationHudInput, nowLabel: String): Boolean =
        synchronized(lock) {
            if (revision <= this.revision) return false
            val projected = NativeNavigationHudPresenter.project(revision, input, nowLabel)
            this.revision = revision
            _state.value = projected
            true
        }

    fun update(revision: Long, input: NativeNavigationHudInput, nowLabel: String): Boolean =
        synchronized(lock) {
            val current = _state.value
            if (
                revision != this.revision ||
                current.status != NativeNavigationHudStatus.Active ||
                input.stepIndex < requireNotNull(current.current).index
            ) {
                return false
            }
            _state.value = NativeNavigationHudPresenter.project(revision, input, nowLabel)
            true
        }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Navigation revision must be non-negative" }
        if (revision < this.revision) return false
        this.revision = revision
        _state.value = NativeNavigationHudSnapshot.hidden(revision)
        true
    }
}
