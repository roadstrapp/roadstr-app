package app.roadstr.core.network

enum class RoutingProviderConfigurationIssue {
    NONE,
    GRAPH_HOPPER_SERVER_MISSING,
    GRAPH_HOPPER_API_KEY_MISSING,
    OPEN_ROUTE_API_KEY_MISSING,
}

data class RoutingProviderConfiguration(
    val provider: RoutingProvider,
    val apiKey: String?,
    val graphHopperServer: String?,
    val issue: RoutingProviderConfigurationIssue,
    val readsCredentials: Boolean,
    val legacyApiKeyMigration: String?,
)

/** Pure persisted provider/key/server resolution mirrored from production Dart. */
object RoutingProviderConfigProtocol {
    fun resolve(
        providerKey: String,
        secureApiKey: String?,
        legacyApiKey: String,
        graphHopperServer: String,
        deferCredentialReadForOsrm: Boolean,
    ): RoutingProviderConfiguration {
        if (deferCredentialReadForOsrm && providerKey == "osrm") {
            return RoutingProviderConfiguration(
                provider = RoutingProvider.OSRM,
                apiKey = null,
                graphHopperServer = null,
                issue = RoutingProviderConfigurationIssue.NONE,
                readsCredentials = false,
                legacyApiKeyMigration = null,
            )
        }

        val secure = secureApiKey ?: ""
        val legacyMigration = if (secure.isEmpty()) legacyApiKey.trimmedOrNull() else null
        val apiKey = (legacyMigration ?: secure).trimmedOrNull()
        val server = graphHopperServer.trimmedOrNull()

        val (provider, issue) = when (providerKey) {
            "graphhopper" -> if (server != null) {
                RoutingProvider.GRAPH_HOPPER to RoutingProviderConfigurationIssue.NONE
            } else {
                RoutingProvider.OSRM to
                    RoutingProviderConfigurationIssue.GRAPH_HOPPER_SERVER_MISSING
            }

            "graphhopper_public" -> if (apiKey != null) {
                RoutingProvider.GRAPH_HOPPER to RoutingProviderConfigurationIssue.NONE
            } else {
                RoutingProvider.OSRM to
                    RoutingProviderConfigurationIssue.GRAPH_HOPPER_API_KEY_MISSING
            }

            "openroute" -> if (apiKey != null) {
                RoutingProvider.OPEN_ROUTE to RoutingProviderConfigurationIssue.NONE
            } else {
                RoutingProvider.OSRM to RoutingProviderConfigurationIssue.OPEN_ROUTE_API_KEY_MISSING
            }

            else -> RoutingProvider.OSRM to RoutingProviderConfigurationIssue.NONE
        }

        return RoutingProviderConfiguration(
            provider = provider,
            apiKey = apiKey,
            graphHopperServer = server,
            issue = issue,
            readsCredentials = true,
            legacyApiKeyMigration = legacyMigration,
        )
    }

    private fun String.trimmedOrNull(): String? = trimDart().ifEmpty { null }

    private fun String.trimDart(): String {
        var start = 0
        var end = length
        while (start < end && this[start].isDartWhitespace()) start++
        while (end > start && this[end - 1].isDartWhitespace()) end--
        return substring(start, end)
    }

    private fun Char.isDartWhitespace(): Boolean =
        this in '\u0009'..'\u000d' ||
            this == '\u0020' ||
            this == '\u0085' ||
            this == '\u00a0' ||
            this == '\u1680' ||
            this in '\u2000'..'\u200a' ||
            this == '\u2028' ||
            this == '\u2029' ||
            this == '\u202f' ||
            this == '\u205f' ||
            this == '\u3000' ||
            this == '\ufeff'
}
