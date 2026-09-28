package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingProviderConfigProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/routing_provider_config_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native provider resolution matches every Dart configuration`() {
        assertEquals(34, rows.size)
        for (fields in rows) {
            val input = BoundedJsonParser(decode(fields[1])).parse() as Map<*, *>
            val result = RoutingProviderConfigProtocol.resolve(
                providerKey = input["provider"] as String,
                secureApiKey = input["secure"] as String?,
                legacyApiKey = input["legacy"] as String,
                graphHopperServer = input["server"] as String,
                deferCredentialReadForOsrm = input["deferOsrm"] as Boolean,
            )
            val actual = "provider=${result.provider.fixtureName};" +
                "api=${result.apiKey ?: "-"};" +
                "server=${result.graphHopperServer ?: "-"};" +
                "issue=${result.issue.fixtureName};" +
                "reads=${if (result.readsCredentials) 1 else 0};" +
                "migration=${result.legacyApiKeyMigration ?: "-"}"
            assertEquals(fields[0], decode(fields[2]), actual)
        }
    }

    private val RoutingProvider.fixtureName: String
        get() = when (this) {
            RoutingProvider.OSRM -> "osrm"
            RoutingProvider.OPEN_ROUTE -> "openRoute"
            RoutingProvider.GRAPH_HOPPER -> "graphHopper"
        }

    private val RoutingProviderConfigurationIssue.fixtureName: String
        get() = when (this) {
            RoutingProviderConfigurationIssue.NONE -> "none"
            RoutingProviderConfigurationIssue.GRAPH_HOPPER_SERVER_MISSING ->
                "graphHopperServerMissing"
            RoutingProviderConfigurationIssue.GRAPH_HOPPER_API_KEY_MISSING ->
                "graphHopperApiKeyMissing"
            RoutingProviderConfigurationIssue.OPEN_ROUTE_API_KEY_MISSING ->
                "openRouteApiKeyMissing"
        }

    private fun decode(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
