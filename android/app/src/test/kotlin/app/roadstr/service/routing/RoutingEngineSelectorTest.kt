package app.roadstr.service.routing

import app.roadstr.feature.route.NativeRouteTransportMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingEngineSelectorTest {
    @Test
    fun `offline off always preserves online selection`() {
        val result = select(
            enabled = false,
            coverage = RoutingCoverageResult.Covered,
            network = false,
            fallback = false,
        )

        assertEquals(RoutingEngineSelection.Online, result)
    }

    @Test
    fun `covered driving request selects local engine`() {
        assertEquals(
            RoutingEngineSelection.Local,
            select(true, RoutingCoverageResult.Covered, network = true, fallback = true),
        )
    }

    @Test
    fun `missing coverage falls back only with network and consent`() {
        assertEquals(
            RoutingEngineSelection.Online,
            select(true, RoutingCoverageResult.NotCovered, network = true, fallback = true),
        )
        assertEquals(
            RoutingEngineSelection.Unavailable(RoutingEngineUnavailableReason.AreaNotDownloaded),
            select(true, RoutingCoverageResult.NotCovered, network = true, fallback = false),
        )
        assertEquals(
            RoutingEngineSelection.Unavailable(RoutingEngineUnavailableReason.AreaNotDownloaded),
            select(true, RoutingCoverageResult.NotCovered, network = false, fallback = true),
        )
    }

    @Test
    fun `unknown coverage never opens local engine`() {
        assertEquals(
            RoutingEngineSelection.Unavailable(RoutingEngineUnavailableReason.DatasetInvalid),
            select(true, RoutingCoverageResult.Unknown, network = true, fallback = false),
        )
    }

    @Test
    fun `unsupported mode uses disclosed online fallback`() {
        val result = RoutingEngineSelector.select(
            RoutingEngineSelectionInput(
                offlineRoutingEnabled = true,
                coverage = RoutingCoverageResult.Covered,
                networkAvailable = true,
                onlineFallbackAllowed = true,
                mode = NativeRouteTransportMode.Walking,
            ),
        )

        assertEquals(RoutingEngineSelection.Online, result)
    }

    private fun select(
        enabled: Boolean,
        coverage: RoutingCoverageResult,
        network: Boolean,
        fallback: Boolean,
    ): RoutingEngineSelection = RoutingEngineSelector.select(
        RoutingEngineSelectionInput(
            offlineRoutingEnabled = enabled,
            coverage = coverage,
            networkAvailable = network,
            onlineFallbackAllowed = fallback,
            mode = NativeRouteTransportMode.Driving,
        ),
    )
}
