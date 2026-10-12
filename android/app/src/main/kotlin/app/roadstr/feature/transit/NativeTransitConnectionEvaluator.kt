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
        return evaluate(
            expectedArrival = completed.to?.expectedArrival ?: completed.endTime,
            expectedDeparture = next.from?.expectedDeparture ?: next.startTime,
            walkingTransfer = walking,
            minimumTransfer = null,
            safetyMargin = safetyMargin,
            nextServiceCancelled = next.cancelled || next.from?.cancelled == true,
            realtime = completed.realTime || next.realTime,
            now = now,
        )
    }

    private fun unknown() = NativeTransitConnectionAssessment(
        NativeTransitConnectionStatus.Unknown,
        availableSeconds = null,
        requiredSeconds = null,
        realtime = false,
    )
}
