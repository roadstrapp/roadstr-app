package app.roadstr.core.network

import app.roadstr.core.navigation.HeadingFilter
import java.util.Collections

enum class RoutingProvider {
    OSRM,
    OPEN_ROUTE,
    GRAPH_HOPPER,
}

enum class RoutingRouteAttempt {
    CONSTRAINED,
    UNCONSTRAINED,
}

enum class RoutingAttemptOutcome {
    SUCCESS,
    ROUTING_FAILURE,
}

data class RoutingOrchestrationDecision(
    val nextAttempt: RoutingRouteAttempt? = null,
    val finalRoutes: List<RoutingParsedRoute>? = null,
    val propagateFailure: Boolean = false,
)

/** Socket-free state machine for Roadstr's one-retry bearing fallback. */
class RoutingOrchestrationProtocol(
    provider: RoutingProvider,
    speedKilometresPerHour: Double,
    val originBearingDegrees: Double?,
    val straightLineDistanceMeters: Double,
) {
    private var expectedAttempt = if (
        provider == RoutingProvider.OSRM &&
        originBearingDegrees != null &&
        HeadingFilter.usesTravelHeading(speedKilometresPerHour)
    ) {
        RoutingRouteAttempt.CONSTRAINED
    } else {
        RoutingRouteAttempt.UNCONSTRAINED
    }
    private var completed = false

    val initialAttempt: RoutingRouteAttempt
        get() = expectedAttempt

    val isCompleted: Boolean
        get() = completed

    fun accept(
        attempt: RoutingRouteAttempt,
        outcome: RoutingAttemptOutcome,
        routes: List<RoutingParsedRoute> = emptyList(),
    ): RoutingOrchestrationDecision {
        if (completed || attempt != expectedAttempt) return NONE

        if (attempt == RoutingRouteAttempt.CONSTRAINED) {
            val retry = outcome == RoutingAttemptOutcome.ROUTING_FAILURE ||
                routes.isEmpty() ||
                isImplausibleReroute(
                    routes.minOf { route -> route.totalDistanceM },
                    straightLineDistanceMeters,
                )
            if (retry) {
                expectedAttempt = RoutingRouteAttempt.UNCONSTRAINED
                return RoutingOrchestrationDecision(
                    nextAttempt = RoutingRouteAttempt.UNCONSTRAINED,
                )
            }
            completed = true
            return RoutingOrchestrationDecision(
                finalRoutes = Collections.unmodifiableList(routes.toList()),
            )
        }

        completed = true
        return if (outcome == RoutingAttemptOutcome.ROUTING_FAILURE) {
            RoutingOrchestrationDecision(propagateFailure = true)
        } else {
            RoutingOrchestrationDecision(
                finalRoutes = Collections.unmodifiableList(routes.toList()),
            )
        }
    }

    companion object {
        private val NONE = RoutingOrchestrationDecision()

        fun isImplausibleReroute(
            routeDistanceMeters: Double,
            straightLineDistanceMeters: Double,
        ): Boolean = routeDistanceMeters > straightLineDistanceMeters * 8.0 + 5_000.0
    }
}
