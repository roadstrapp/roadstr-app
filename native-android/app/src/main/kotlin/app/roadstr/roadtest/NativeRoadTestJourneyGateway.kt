package app.roadstr.roadtest

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingProviderConfigProtocol
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.feature.home.NativeShellJourneyGateway
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.routing.NativeRoutingQuery
import app.roadstr.service.routing.NativeRoutingService
import app.roadstr.service.search.NativeSearchPhase
import app.roadstr.service.search.NativeSearchQuery
import app.roadstr.service.search.NativeSearchService

/** Live, credential-free OSM/OSRM composition used only by the road-test APK. */
class NativeRoadTestJourneyGateway(
    transport: NativeBoundedHttpClient = NativeBoundedHttpClient(),
) : NativeShellJourneyGateway {
    private val searchService = NativeSearchService(transport)
    private val routingService = NativeRoutingService(transport)
    private val routingConfiguration = RoutingProviderConfigProtocol.resolve(
        providerKey = "osrm",
        secureApiKey = null,
        legacyApiKey = "",
        graphHopperServer = "",
        deferCredentialReadForOsrm = true,
    )

    override suspend fun search(
        query: String,
        near: SearchResponsePoint?,
        languageCode: String,
        onPartial: (List<SearchResult>) -> Unit,
    ): List<SearchResult> = searchService.search(
        query = NativeSearchQuery(
            query = query,
            near = near,
            languageCode = languageCode,
            phase = NativeSearchPhase.SETTLED,
        ),
        onPartial = onPartial,
    )

    override suspend fun routes(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        via: List<SearchResponsePoint>,
        mode: NativeRouteTransportMode,
        languageCode: String,
    ): List<RoutingParsedRoute> {
        require(mode != NativeRouteTransportMode.Transit) {
            "Public transport is not available in the road-test routing gateway"
        }
        return routingService.getRoutes(
            NativeRoutingQuery(
                origin = origin.toRoutingPoint(),
                destination = destination.toRoutingPoint(),
                configuration = routingConfiguration,
                languageCode = languageCode,
                vehicle = mode.wireValue,
                via = via.map { point -> point.toRoutingPoint() },
                requestAlternatives = via.isEmpty(),
            ),
        )
    }

    override suspend fun reroute(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        mode: NativeRouteTransportMode,
        languageCode: String,
        speedKilometresPerHour: Double,
        headingDegrees: Double?,
        straightLineDistanceMeters: Double,
    ): List<RoutingParsedRoute> {
        require(mode != NativeRouteTransportMode.Transit) {
            "Public transport is not available in the road-test routing gateway"
        }
        return routingService.getRerouteRoutes(
            query = NativeRoutingQuery(
                origin = origin.toRoutingPoint(),
                destination = destination.toRoutingPoint(),
                configuration = routingConfiguration,
                languageCode = languageCode,
                vehicle = mode.wireValue,
                requestAlternatives = false,
                originBearingDegrees = headingDegrees,
            ),
            speedKilometresPerHour = speedKilometresPerHour,
            straightLineDistanceMeters = straightLineDistanceMeters,
        )
    }

    private fun SearchResponsePoint.toRoutingPoint() = RoutingRequestPoint(
        latitude = latitude,
        longitude = longitude,
    )
}
