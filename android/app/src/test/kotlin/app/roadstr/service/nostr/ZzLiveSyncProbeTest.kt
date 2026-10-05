package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.FavoritesSyncProtocol
import app.roadstr.core.protocol.nostr.NostrSchnorr
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ZzLiveSyncProbeTest {
    private class MemoryStore : NativeFavoritesSyncStore {
        override var lastCreatedAt: Long? = null
        override var legacyCleaned: Boolean = false
    }

    @Test
    fun live() = runBlocking {
        if (System.getenv("ROADSTR_LIVE_PROBE") != "1") return@runBlocking
        val key = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val signer = NativeLocalKeySigner { key }
        println("PUB " + NostrSchnorr.publicKey(key))
        val connector = OkHttpRelayConnector()
        val store = MemoryStore()
        val service = NativeFavoritesSyncService(signer, connector, store, { null }, { null })
        val places = listOf(mapOf<String, Any?>("label" to "Casa", "address" to "x", "lat" to 38.7, "lon" to -9.1))
        println("PUSH " + service.push(places))
        val fresh = MemoryStore()
        val reader = NativeFavoritesSyncService(signer, connector, fresh, { null }, { null })
        for (url in service.relays()) {
            val fetcher = NativeRelayFetcher(connector)
            val got = fetcher.fetch(
                url, "probe01", FavoritesSyncProtocol.fetchRequest("probe01", NostrSchnorr.publicKey(key), FavoritesSyncProtocol.hashedDTag(NostrSchnorr.publicKey(key)))[2] as Map<String, Any?>,
                30078, app.roadstr.core.protocol.nostr.NostrIngressRoute.FAVORITE_SNAPSHOT, 4,
            ) { true }
            println("RELAY $url -> ${got.size} events")
        }
        println("PULL " + reader.pull())
    }
}
