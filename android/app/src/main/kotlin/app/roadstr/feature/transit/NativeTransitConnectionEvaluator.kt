package app.roadstr.feature.transit

import app.roadstr.core.network.TransitItinerary
import java.time.Duration
import java.time.Instant

enum class NativeTransitConnectionStatus {
    None,
    Feasible,
    AtRisk,
    Missed,
    Unknown,
}

data class NativeTransitConnectionAssessment(
    val status: NativeTransitConnectionStatus,
    val availableSeconds: Long?,
    val requiredSeconds: Long?,
    val realtime: Boolean,
)

/** Pure connection check. Unknown transfer time can raise risk, never certainty. */
object NativeTransitConnectionEvaluator {
    fun evaluate(
        expectedArrival: Instant?,
        expectedDeparture: Instant?,
        walkingTransfer: Duration?,
        minimumTransfer: Duration?,
        safetyMargin: Duration = Duration.ofMinutes(2),
        nextServiceCancelled: Boolean = false,
        realtime: Boolean = false,
        now: Instant? = null,
    ): NativeTransitConnectionAssessment {
        require(!safetyMargin.isNegative)
        if (nextServiceCancelled) {
            return NativeTransitConnectionAssessment(
                NativeTransitConnectionStatus.Missed,
                availableSeconds = null,
                requiredSeconds = null,
                realtime = realtime,
            )
        }
        if (expectedArrival == null || expectedDeparture == null) {
            return NativeTransitConnectionAssessment(
                NativeTransitConnectionStatus.Unknown,
                availableSeconds = null,
                requiredSeconds = null,
                realtime = realtime,
            )
        }
        val available = Duration.between(expectedArrival, expectedDeparture).seconds
        val requiredDurations = listOfNotNull(walkingTransfer, minimumTransfer)
        if (requiredDurations.isEmpty()) {
            return NativeTransitConnectionAssessment(
                NativeTransitConnectionStatus.AtRisk,
                availableSeconds = available,
                requiredSeconds = null,
                realtime = realtime,
            )
        }
        val required = requiredDurations.maxOf(Duration::getSeconds).coerceAtLeast(0L)
        val status = when {
            now != null && realtime && !now.isBefore(expectedDeparture) -> NativeTransitConnectionStatus.Missed
            available < required -> NativeTransitConnectionStatus.Missed
            available < required + safetyMargin.seconds -> NativeTransitConnectionStatus.AtRisk
            else -> NativeTransitConnectionStatus.Feasible
        }
        return NativeTransitConnectionAssessment(status, available, required, realtime)
    }

    fun evaluateJourney(
        itinerary: TransitItinerary,
        completedLegIndex: Int,
        safetyMargin: Duration = Duration.ofMinutes(2),
        now: Instant? = null,
    ): NativeTransitConnectionAssessment {
        val completed = itinerary.legs.getOrNull(completedLegIndex)
            ?: return unknown()
        val nextTransitIndex = itinerary.legs.indices.firstOrNull { index ->
            index > completedLegIndex && itinerary.legs[index].mode.isTransit
        } ?: return NativeTransitConnectionAssessment(
            NativeTransitConnectionStatus.None,
            availableSeconds = null,
            requiredSeconds = null,
            realtime = false,
        )
        val next = itinerary.legs[nextTransitIndex]
        val walking = itinerary.legs
            .subList(completedLegIndex + 1, nextTransitIndex)
            .filterNot { it.mode.isTransit }
            .takeIf(List<*>::isNotEmpty)
            ?.sumOf { it.durationSeconds }
            ?.let(Duration::ofSeconds)
        val minimumTransfer = inferredMinimumTransfer(completed, next, walking)
        return evaluate(
            expectedArrival = completed.to?.expectedArrival ?: completed.endTime,
            expectedDeparture = next.from?.expectedDeparture ?: next.startTime,
            walkingTransfer = walking,
            minimumTransfer = minimumTransfer,
            safetyMargin = safetyMargin,
            nextServiceCancelled = next.cancelled || next.from?.cancelled == true,
            realtime = completed.realTime || next.realTime,
            now = now,
        )
    }

    /**
     * Providers do not consistently publish station transfer rules. When both legs identify the
     * same interchange, use a deliberately conservative platform-based floor; unrelated or
     * unidentified stops remain unknown and therefore only raise a risk warning.
     */
    private fun inferredMinimumTransfer(
        completed: app.roadstr.core.network.TransitLeg,
        next: app.roadstr.core.network.TransitLeg,
        walking: Duration?,
    ): Duration? {
        if (walking != null) return walking
        val arrival = completed.to ?: return null
        val departure = next.from ?: return null
        val sameStation = when {
            arrival.id != null && departure.id != null -> arrival.id == departure.id
            !arrival.name.isNullOrBlank() && !departure.name.isNullOrBlank() ->
                arrival.name.trim().equals(departure.name.trim(), ignoreCase = true)
            else -> false
        }
        if (!sameStation) return null
        val fromPlatform = arrival.expectedPlatform ?: arrival.scheduledPlatform
        val toPlatform = departure.expectedPlatform ?: departure.scheduledPlatform
        return when {
            fromPlatform != null && toPlatform != null && fromPlatform == toPlatform -> Duration.ofSeconds(60)
            fromPlatform != null && toPlatform != null -> Duration.ofMinutes(3)
            else -> Duration.ofMinutes(2)
        }
    }

    private fun unknown() = NativeTransitConnectionAssessment(
        NativeTransitConnectionStatus.Unknown,
        availableSeconds = null,
        requiredSeconds = null,
        realtime = false,
    )
}
