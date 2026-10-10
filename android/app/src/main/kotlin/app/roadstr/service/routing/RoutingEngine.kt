package app.roadstr.service.routing

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.feature.route.NativeRouteTransportMode
import kotlinx.coroutines.CancellationException

data class LocalRoutingDataset(
    val id: String,
    val version: Int,
    val tileExtractPath: String,
    val buildId: String,
)

data class RoutingEngineRequest(
    val origin: RoutingRequestPoint,
    val destination: RoutingRequestPoint,
    val via: List<RoutingRequestPoint> = emptyList(),
    val mode: NativeRouteTransportMode = NativeRouteTransportMode.Driving,
    val languageCode: String = "en",
    val avoidance: RoutingRouteAvoidance = RoutingRouteAvoidance.None,
    val requestAlternatives: Boolean = true,
    val localDataset: LocalRoutingDataset? = null,
)

enum class RoutingEngineUnavailableReason {
    AreaNotDownloaded,
    DatasetMissing,
    DatasetInvalid,
    ModeUnsupported,
    NetworkUnavailable,
}

enum class RoutingEngineFailureKind {
    InvalidRequest,
    InvalidResponse,
    Transport,
    EngineFailure,
}

sealed interface RoutingEngineOutcome {
    data class Success(val routes: List<RoutingParsedRoute>) : RoutingEngineOutcome
    data class Unavailable(val reason: RoutingEngineUnavailableReason) : RoutingEngineOutcome
    data class Failed(val kind: RoutingEngineFailureKind) : RoutingEngineOutcome
}

interface RoutingEngine {
    val id: String
    suspend fun route(request: RoutingEngineRequest): RoutingEngineOutcome
}

enum class RoutingCoverageResult { Covered, NotCovered, Unknown }

data class RoutingEngineSelectionInput(
    val offlineRoutingEnabled: Boolean,
    val coverage: RoutingCoverageResult,
    val networkAvailable: Boolean,
    val onlineFallbackAllowed: Boolean,
    val mode: NativeRouteTransportMode,
)

data class OfflineRoutingPreferences(
    val enabled: Boolean = false,
    val onlineFallbackAllowed: Boolean = false,
)

class NativeRoutingSelectionException(
    val reason: RoutingEngineUnavailableReason,
) : java.io.IOException("Selected routing engine is unavailable")

sealed interface RoutingEngineSelection {
    data object Online : RoutingEngineSelection
    data object Local : RoutingEngineSelection
    data class Unavailable(val reason: RoutingEngineUnavailableReason) : RoutingEngineSelection
}

object RoutingEngineSelector {
    fun select(input: RoutingEngineSelectionInput): RoutingEngineSelection {
        if (!input.offlineRoutingEnabled) return RoutingEngineSelection.Online
        if (input.mode != NativeRouteTransportMode.Driving) {
            return onlineOrUnavailable(input, RoutingEngineUnavailableReason.ModeUnsupported)
        }
        if (input.coverage == RoutingCoverageResult.Covered) return RoutingEngineSelection.Local
        val reason = when (input.coverage) {
            RoutingCoverageResult.NotCovered -> RoutingEngineUnavailableReason.AreaNotDownloaded
            RoutingCoverageResult.Unknown -> RoutingEngineUnavailableReason.DatasetInvalid
            RoutingCoverageResult.Covered -> error("Covered requests already select the local engine")
        }
        return onlineOrUnavailable(input, reason)
    }

    private fun onlineOrUnavailable(
        input: RoutingEngineSelectionInput,
        reason: RoutingEngineUnavailableReason,
    ): RoutingEngineSelection {
        if (input.networkAvailable && input.onlineFallbackAllowed) return RoutingEngineSelection.Online
        return RoutingEngineSelection.Unavailable(
            if (!input.networkAvailable && reason == RoutingEngineUnavailableReason.ModeUnsupported) {
                RoutingEngineUnavailableReason.NetworkUnavailable
            } else {
                reason
            },
        )
    }
}

class OnlineRoutingEngine(
    private val service: NativeRoutingService,
    private val configuration: () -> RoutingProviderConfiguration,
) : RoutingEngine {
    override val id: String = "online"

    override suspend fun route(request: RoutingEngineRequest): RoutingEngineOutcome {
        if (request.mode == NativeRouteTransportMode.Transit) {
            return RoutingEngineOutcome.Unavailable(RoutingEngineUnavailableReason.ModeUnsupported)
        }
        return try {
            val query = NativeRoutingQuery(
                origin = request.origin,
                destination = request.destination,
                configuration = configuration(),
                languageCode = request.languageCode,
                vehicle = request.mode.wireValue,
                via = request.via,
                requestAlternatives = request.requestAlternatives,
            )
            val routes = service.getRoutes(query)
            RoutingEngineOutcome.Success(routes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: NativeRoutingException) {
            RoutingEngineOutcome.Failed(failure.toEngineFailure())
        }
    }

    private fun NativeRoutingException.toEngineFailure(): RoutingEngineFailureKind = when (kind) {
        NativeRoutingFailureKind.InvalidConfiguration -> RoutingEngineFailureKind.InvalidRequest
        NativeRoutingFailureKind.InvalidResponse -> RoutingEngineFailureKind.InvalidResponse
        NativeRoutingFailureKind.HttpStatus,
        NativeRoutingFailureKind.ResponseTooLarge,
        NativeRoutingFailureKind.Timeout,
        NativeRoutingFailureKind.Transport,
        -> RoutingEngineFailureKind.Transport
    }
}
