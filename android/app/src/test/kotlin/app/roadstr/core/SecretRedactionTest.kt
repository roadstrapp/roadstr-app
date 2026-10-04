package app.roadstr.core

import app.roadstr.core.network.RoutingProvider
import app.roadstr.core.network.RoutingProviderConfigProtocol
import app.roadstr.core.network.RoutingRequestHttpMethod
import app.roadstr.core.network.RoutingProviderRequest
import app.roadstr.core.protocol.nostr.NostrNip19
import app.roadstr.migration.LegacyAsset
import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kotlin data classes print every field. These types carry key material,
 * credentials or coordinates, so a stray "$value" must not be able to emit
 * them.
 */
class SecretRedactionTest {
    private val privateKey = "0000000000000000000000000000000000000000000000000000000000000003"

    @Test
    fun `legacy identity and snapshot never print their secrets`() {
        val identity = LegacyIdentity("pub", "nsec", privateKey)
        val snapshot = LegacyStorageSnapshot(
            schemaVersion = 1,
            ordinaryValues = mapOf("favorites" to "secret-ish"),
            secureValues = mapOf("nostr_priv_hex" to privateKey),
            identity = identity,
            assets = listOf(LegacyAsset("kokoro/a.bin", 1, "0".repeat(64))),
        )
        assertFalse(identity.toString().contains(privateKey))
        assertFalse(snapshot.toString().contains(privateKey))
        assertFalse(snapshot.toString().contains("secret-ish"))
        assertTrue(snapshot.toString().contains("secure=1"))
    }

    @Test
    fun `a decoded nsec does not print its key`() {
        val nsec = NostrNip19.encodePrivateKey(privateKey)
        val decoded = NostrNip19.decode(nsec)
        assertFalse(decoded.toString().contains(privateKey))
    }

    @Test
    fun `provider configuration and requests hide keys and coordinates`() {
        val config = RoutingProviderConfigProtocol.resolve(
            providerKey = "openroute",
            secureApiKey = "api-key-123",
            legacyApiKey = "",
            graphHopperServer = "",
            deferCredentialReadForOsrm = false,
        )
        assertTrue(config.provider == RoutingProvider.OPEN_ROUTE)
        assertFalse(config.toString().contains("api-key-123"))

        val routing = RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Get,
            uri = "https://example.test/route?key=api-key-123&c=38.7,-9.1",
            headers = mapOf("Authorization" to "api-key-123"),
        )
        assertFalse(routing.toString().contains("api-key-123"))
        assertFalse(routing.toString().contains("38.7"))

        val native = NativeHttpRequest(
            method = NativeHttpMethod.Get,
            uri = "https://example.test/?key=api-key-123",
            headers = mapOf("Authorization" to "api-key-123"),
        )
        assertFalse(native.toString().contains("api-key-123"))
    }
}
