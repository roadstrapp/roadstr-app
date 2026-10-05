package app.roadstr.service.routing

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.RoutingAvoidanceAttempt
import app.roadstr.core.network.RoutingAvoidanceAttemptOutcome
import app.roadstr.core.network.RoutingAvoidanceMode
import app.roadstr.core.network.RoutingAvoidanceProtocol
import app.roadstr.core.network.RoutingAttemptOutcome
import app.roadstr.core.network.RoutingEndpointDecision
import app.roadstr.core.network.RoutingEndpointPolicy
import app.roadstr.core.network.RoutingOrchestrationProtocol
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingProvider
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingProviderRequest
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.RoutingRequestProtocol
import app.roadstr.core.network.RoutingResponseException
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseProtocol
import app.roadstr.core.network.RoutingRetimePolicy
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.core.network.RoutingRouteAttempt
import app.roadstr.core.network.ValhallaCostingPolicy
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeRoutingHttpTransport
import java.io.IOException
import kotlinx.coroutines.CancellationException

data class NativeRoutingQuery(
    val origin: RoutingRequestPoint,
    val destination: RoutingRequestPoint,
    val configuration: RoutingProviderConfiguration,
    val languageCode: String = "en",
    val vehicle: String = "driving",
    val via: List<RoutingRequestPoint> = emptyList(),
    val requestAlternatives: Boolean = true,
    val originBearingDegrees: Double? = null,
)

data class NativeAvoidanceRoutingQuery(
    val origin: RoutingRequestPoint,
    val destination: RoutingRequestPoint,
    val mode: RoutingAvoidanceMode,
    val languageCode: String = "en",
)

enum class NativeRoutingFailureKind {
    InvalidConfiguration,
    HttpStatus,
    ResponseTooLarge,
    Timeout,
    Transport,
    InvalidResponse,
}

/**
 * Value-free provider failure. Response bodies, endpoint URLs, coordinates and
 * API keys are deliberately excluded from both the message and the cause.
 */
class NativeRoutingException(
    val kind: NativeRoutingFailureKind,
    val statusCode: Int? = null,
) : IOException(
    when (kind) {
        NativeRoutingFailureKind.InvalidConfiguration -> "Invalid routing configuration"
        NativeRoutingFailureKind.HttpStatus -> "Routing provider returned an HTTP error"
        NativeRoutingFailureKind.ResponseTooLarge -> "Routing response is too large"
        NativeRoutingFailureKind.Timeout -> "Routing request timed out"
        NativeRoutingFailureKind.Transport -> "Routing transport failed"
        NativeRoutingFailureKind.InvalidResponse -> "Routing provider returned an invalid response"
    },
)

/**
 * Native vertical routing slice: request composition, bounded dispatch,
 * response normalization, the shipped one-retry OSRM bearing fallback and
 * Valhalla avoidance execution with best-effort per-leg OSRM re-timing.
 *
 * The caller owns the coroutine scope. Cancelling that scope is never converted
 * to a routing failure, so the active OkHttp call is cancelled and no fallback
 * request is started. Secure-store reads and app/UI ownership remain outside
 * this service.
 */
class NativeRoutingService internal constructor(
    private val transport: NativeRoutingHttpTransport,
    private val valhallaEndpointOverride: String?,
    private val osrmRetimeEndpointOverride: String?,
) {
    constructor(transport: NativeRoutingHttpTransport) : this(transport, null, null)

    constructor() : this(NativeBoundedHttpClient(), null, null)

    suspend fun getRoutes(query: NativeRoutingQuery): List<RoutingParsedRoute> {
        val provider = query.configuration.provider
        val request = buildRequest(query)
        val response = execute(request, provider)
        if (response.statusCode != HTTP_OK) {
            throw NativeRoutingException(
                kind = NativeRoutingFailureKind.HttpStatus,
                statusCode = response.statusCode,
            )
        }
        return try {
            when (provider) {
                RoutingProvider.OSRM -> RoutingResponseProtocol.parseOsrmRoutes(
                    body = response.bodyUtf8,
                    languageCode = query.languageCode,
                )

                RoutingProvider.OPEN_ROUTE -> listOf(
                    RoutingResponseProtocol.parseOpenRouteService(
                        body = response.bodyUtf8,
                        fallbackOrigin = query.origin.toResponsePoint(),
                    ),
                )

                RoutingProvider.GRAPH_HOPPER -> listOf(
                    RoutingResponseProtocol.parseGraphHopper(
                        body = response.bodyUtf8,
                        fallbackOrigin = query.origin.toResponsePoint(),
                    ),
                )
            }
        } catch (_: RoutingResponseException) {
            throw NativeRoutingException(NativeRoutingFailureKind.InvalidResponse)
        }
    }

    /**
     * Whether [server] is a reachable, routing-capable GraphHopper instance,
     * checked the way the Flutter settings screen does: the probe route runs
     * between two points of Null Island, which no map covers, so a healthy
     * server answers HTTP 400 "cannot find point". That answer proves it is
     * GraphHopper; only unreachable hosts, rejected keys or other kinds of
     * server fail the check.
     */
    suspend fun probeGraphHopper(server: String, apiKey: String?): Boolean {
        val endpoint = RoutingRequestProtocol.graphHopperEndpoint(server)
        if (RoutingEndpointPolicy.graphHopperDecision(endpoint) != RoutingEndpointDecision.Accepted) {
            return false
        }
        val response = try {
            execute(RoutingRequestProtocol.graphHopperProbe(endpoint, apiKey), RoutingProvider.GRAPH_HOPPER)
        } catch (_: NativeRoutingException) {
            return false
        }
        val body = response.bodyUtf8
        if (response.statusCode == 400 && PROBE_REACHABLE_MARKERS.any(body::contains)) return true
        if (response.statusCode != HTTP_OK) return false
        val parsed = runCatching { app.roadstr.core.protocol.nostr.BoundedJsonParser(body).parse() }.getOrNull()
        return (parsed as? Map<*, *>)?.get("paths") != null
    }

    suspend fun getRerouteRoutes(
        query: NativeRoutingQuery,
        speedKilometresPerHour: Double,
        straightLineDistanceMeters: Double,
    ): List<RoutingParsedRoute> {
        val state = RoutingOrchestrationProtocol(
            provider = query.configuration.provider,
            speedKilometresPerHour = speedKilometresPerHour,
            originBearingDegrees = query.originBearingDegrees,
            straightLineDistanceMeters = straightLineDistanceMeters,
        )
        var attempt = state.initialAttempt
        while (true) {
            val decision = try {
                val routes = getRoutes(
                    query.copy(
                        originBearingDegrees = if (
                            attempt == RoutingRouteAttempt.CONSTRAINED
                        ) {
                            query.originBearingDegrees
                        } else {
                            null
                        },
                    ),
                )
                state.accept(
                    attempt = attempt,
                    outcome = RoutingAttemptOutcome.SUCCESS,
                    routes = routes,
                )
            } catch (failure: NativeRoutingException) {
                val failureDecision = state.accept(
                    attempt = attempt,
                    outcome = RoutingAttemptOutcome.ROUTING_FAILURE,
                )
                if (failureDecision.propagateFailure) throw failure
                failureDecision
            }

            decision.finalRoutes?.let { routes -> return routes }
            attempt = decision.nextAttempt
                ?: error("Routing orchestration produced no terminal action")
        }
    }

    /**
     * Executes the fixture-locked avoidance policy.
     *
     * Highway/toll mode first requires a hard Valhalla exclusion and retries
     * once with the soft preference after any rejected/failed hard attempt.
     * Off-road mode performs one tracks-disfavoured request. An accepted route
     * is then re-timed through OSRM when enough sampled legs can be verified;
     * re-timing failure never discards the Valhalla route.
     */
    suspend fun getAvoidanceRoute(query: NativeAvoidanceRoutingQuery): RoutingParsedRoute {
        val state = RoutingAvoidanceProtocol(query.mode)
        var attempt = state.initialAttempt
        while (true) {
            val decision = try {
                val route = getValhallaAvoidanceRoute(query, attempt)
                state.accept(
                    attempt = attempt,
                    outcome = RoutingAvoidanceAttemptOutcome.SUCCESS,
                    route = route,
                )
            } catch (failure: NativeRoutingException) {
                val failureDecision = state.accept(
                    attempt = attempt,
                    outcome = RoutingAvoidanceAttemptOutcome.ROUTING_FAILURE,
                )
                if (failureDecision.propagateFailure) throw failure
                failureDecision
            }

            decision.finalRoute?.let { route -> return retimeThroughOsrm(route) }
            attempt = decision.nextAttempt
                ?: error("Avoidance orchestration produced no terminal action")
        }
    }

    private suspend fun getValhallaAvoidanceRoute(
        query: NativeAvoidanceRoutingQuery,
        attempt: RoutingAvoidanceAttempt,
    ): RoutingParsedRoute {
        val costingPolicy = when (attempt) {
            RoutingAvoidanceAttempt.HARD -> ValhallaCostingPolicy.HardHighwayAndTollExclusion
            RoutingAvoidanceAttempt.SOFT -> ValhallaCostingPolicy.SoftHighwayAndTollAvoidance
            RoutingAvoidanceAttempt.TRACKS -> ValhallaCostingPolicy.AvoidTracks
        }
        val request = RoutingRequestProtocol.valhalla(
            origin = query.origin,
            destination = query.destination,
            languageCode = query.languageCode,
            costingPolicy = costingPolicy,
            endpoint = valhallaEndpointOverride,
        )
        val response = execute(request, VALHALLA_TIMEOUT_MILLIS)
        if (response.statusCode != HTTP_OK) {
            throw NativeRoutingException(
                kind = NativeRoutingFailureKind.HttpStatus,
                statusCode = response.statusCode,
            )
        }
        val parsed = try {
            RoutingResponseProtocol.parseValhalla(response.bodyUtf8)
        } catch (_: RoutingResponseException) {
            throw NativeRoutingException(NativeRoutingFailureKind.InvalidResponse)
        }
        val hasHighway = parsed.summary["has_highway"] == true
        val hasToll = parsed.summary["has_toll"] == true
        if (attempt == RoutingAvoidanceAttempt.HARD && (hasHighway || hasToll)) {
            throw NativeRoutingException(NativeRoutingFailureKind.InvalidResponse)
        }
        val avoidance = when (attempt) {
            RoutingAvoidanceAttempt.TRACKS -> RoutingRouteAvoidance.OffRoadAvoided
            RoutingAvoidanceAttempt.HARD,
            RoutingAvoidanceAttempt.SOFT,
            -> if (hasHighway || hasToll) {
                RoutingRouteAvoidance.MinimizedHighwaysAndTolls
            } else {
                RoutingRouteAvoidance.HighwayAndTollFree
            }
        }
        return parsed.route.copy(avoidance = avoidance)
    }

    private suspend fun retimeThroughOsrm(route: RoutingParsedRoute): RoutingParsedRoute {
        if (valhallaEndpointOverride != null && osrmRetimeEndpointOverride == null) {
            return route
        }
        val plan = RoutingRetimePolicy.buildPlan(route) ?: return route
        return try {
            val request = RoutingRequestProtocol.osrmRetime(
                waypoints = plan.waypoints,
                endpoint = osrmRetimeEndpointOverride,
            )
            val response = execute(request, OSRM_RETIME_TIMEOUT_MILLIS)
            if (response.statusCode != HTTP_OK) {
                route
            } else {
                val legs = RoutingResponseProtocol.parseOsrmRetimeLegs(response.bodyUtf8)
                RoutingRetimePolicy.apply(route, plan, legs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            route
        } catch (_: IOException) {
            route
        }
    }

    private fun buildRequest(query: NativeRoutingQuery): RoutingProviderRequest =
        when (query.configuration.provider) {
            RoutingProvider.OSRM -> RoutingRequestProtocol.osrmRoute(
                origin = query.origin,
                destination = query.destination,
                vehicle = query.vehicle,
                via = query.via,
                requestAlternatives = query.requestAlternatives,
                originBearingDegrees = query.originBearingDegrees,
            )

            RoutingProvider.OPEN_ROUTE -> RoutingRequestProtocol.openRouteService(
                origin = query.origin,
                destination = query.destination,
                apiKey = query.configuration.apiKey
                    ?: invalidConfiguration(),
                languageCode = query.languageCode,
                vehicle = query.vehicle,
            )

            RoutingProvider.GRAPH_HOPPER -> {
                val server = RoutingRequestProtocol.graphHopperEndpoint(
                    query.configuration.graphHopperServer,
                )
                if (RoutingEndpointPolicy.graphHopperDecision(server) != RoutingEndpointDecision.Accepted) {
                    invalidConfiguration()
                }
                if (
                    server == RoutingRequestProtocol.graphHopperPublicEndpoint &&
                    query.configuration.apiKey.isNullOrEmpty()
                ) {
                    invalidConfiguration()
                }
                RoutingRequestProtocol.graphHopperRoute(
                    origin = query.origin,
                    destination = query.destination,
                    server = server,
                    languageCode = query.languageCode,
                    vehicle = query.vehicle,
                    apiKey = query.configuration.apiKey,
                )
            }
        }

    private suspend fun execute(
        request: RoutingProviderRequest,
        provider: RoutingProvider,
    ): NativeHttpResponse = execute(
        request = request,
        timeoutMillis = when (provider) {
            RoutingProvider.GRAPH_HOPPER -> GRAPH_HOPPER_TIMEOUT_MILLIS
            RoutingProvider.OSRM,
            RoutingProvider.OPEN_ROUTE,
            -> DEFAULT_ROUTING_TIMEOUT_MILLIS
        },
    )

    private suspend fun execute(
        request: RoutingProviderRequest,
        timeoutMillis: Long,
    ): NativeHttpResponse {
        val limits = NativeHttpRequestLimits(
            timeoutMillis = timeoutMillis,
            maxResponseBytes = NetworkResponseLimit.JourneyRoute.bytes,
        )
        return try {
            transport.execute(request, limits)
        } catch (failure: NativeHttpException) {
            throw failure.toRoutingException()
        } catch (_: IOException) {
            throw NativeRoutingException(NativeRoutingFailureKind.Transport)
        }
    }

    private fun NativeHttpException.toRoutingException(): NativeRoutingException =
        NativeRoutingException(
            when (kind) {
                NativeHttpFailureKind.InvalidRequest ->
                    NativeRoutingFailureKind.InvalidConfiguration

                NativeHttpFailureKind.ResponseTooLarge ->
                    NativeRoutingFailureKind.ResponseTooLarge

                NativeHttpFailureKind.Timeout -> NativeRoutingFailureKind.Timeout
                NativeHttpFailureKind.Transport -> NativeRoutingFailureKind.Transport
            },
        )

    private fun RoutingRequestPoint.toResponsePoint(): RoutingResponsePoint =
        RoutingResponsePoint(latitude = latitude, longitude = longitude)

    private fun invalidConfiguration(): Nothing =
        throw NativeRoutingException(NativeRoutingFailureKind.InvalidConfiguration)

    companion object {
        const val DEFAULT_ROUTING_TIMEOUT_MILLIS = 10_000L
        const val GRAPH_HOPPER_TIMEOUT_MILLIS = 12_000L
        const val VALHALLA_TIMEOUT_MILLIS = 25_000L
        const val OSRM_RETIME_TIMEOUT_MILLIS = 30_000L
        private const val HTTP_OK = 200
        private val PROBE_REACHABLE_MARKERS = listOf(
            "Cannot find point",
            "PointNotFoundException",
            "PointOutOfBoundsException",
        )
    }
}
