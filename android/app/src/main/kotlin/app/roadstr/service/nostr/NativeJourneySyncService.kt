package app.roadstr.service.nostr

import app.roadstr.core.journey.JourneyRecord
import app.roadstr.core.journey.JourneySession
import app.roadstr.core.journey.JourneySessionStatus
import app.roadstr.core.journey.JourneySyncPhase
import app.roadstr.core.journey.JourneySyncResult
import app.roadstr.core.journey.JourneySyncSnapshot
import app.roadstr.core.protocol.nostr.FavoritesSyncProtocol
import app.roadstr.core.protocol.nostr.JourneySyncChunk
import app.roadstr.core.protocol.nostr.JourneySyncManifest
import app.roadstr.core.protocol.nostr.JourneySyncProtocol
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NativeJourneySyncStore {
    var lastCreatedAt: Long?
    var lastSuccessfulSyncMillis: Long?
    fun publishedFingerprint(address: String): String?
    fun setPublishedFingerprint(address: String, fingerprint: String)
}

/**
 * NIP-78 device shards encrypted to the account itself with NIP-44.
 *
 * Each device owns its addressable chunks. A manifest commits their hashes, so
 * a partial relay response can never erase history from another phone.
 */
class NativeJourneySyncService(
    private val signer: NativeNostrSigner,
    connector: NativeRelayConnector,
    private val store: NativeJourneySyncStore,
    private val deviceId: String,
    private val customRelay: () -> String?,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val fetcher: NativeRelayFetcher = NativeRelayFetcher(connector),
    private val publisherFactory: (List<String>) -> NativeRelayPublisher = { NativeRelayPublisher(connector, it) },
    private val diagnostics: (String) -> Unit = {},
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(
        JourneySyncSnapshot(lastSuccessfulSyncEpochMillis = store.lastSuccessfulSyncMillis),
    )
    val state: StateFlow<JourneySyncSnapshot> = _state.asStateFlow()

    fun markLocalChangesPending() {
        _state.value = _state.value.copy(localChangesPending = true)
    }

    fun relays(): List<String> {
        val extra = customRelay()?.let { app.roadstr.core.protocol.nostr.CustomRelayPolicy.normalise(it) }
        return (FavoritesSyncProtocol.defaultRelays + listOfNotNull(extra)).distinct()
    }

    suspend fun synchronize(local: JourneyRecord): JourneySyncResult = mutex.withLock {
        val pubkey = signer.pubkeyHex
        if (pubkey == null) return@withLock fail(JourneySyncPhase.SIGNER_REQUIRED, "No Nostr identity", JourneySyncResult.SignerRequired)
        _state.value = _state.value.copy(phase = JourneySyncPhase.SYNCING, detail = null)
        val pulled = pull(pubkey)
        if (pulled is Pull.Invalid) {
            return@withLock fail(JourneySyncPhase.ERROR, "Remote Journey data is incomplete or invalid", JourneySyncResult.InvalidRemoteData)
        }
        val remote = (pulled as Pull.Ok)
        val publishResult = pushOwnDevice(pubkey, local)
        if (publishResult == Push.CRYPTO) {
            return@withLock fail(JourneySyncPhase.CRYPTO_UNAVAILABLE, "The signer cannot use NIP-44", JourneySyncResult.CryptoUnavailable)
        }
        if (publishResult == Push.RELAY) {
            return@withLock fail(JourneySyncPhase.ERROR, "No relay accepted Journey data", JourneySyncResult.RelayUnavailable)
        }
        val remoteOtherDevices = remote.sessions.filter { it.deviceId != deviceId }
        val merged = (local.sessions + remoteOtherDevices).distinctBy(JourneySession::id)
        val overlaps = potentialOverlaps(merged)
        val now = System.currentTimeMillis()
        store.lastSuccessfulSyncMillis = now
        _state.value = JourneySyncSnapshot(
            phase = JourneySyncPhase.SYNCED,
            lastSuccessfulSyncEpochMillis = now,
            localChangesPending = false,
            potentialOverlaps = overlaps,
        )
        JourneySyncResult.Ok(
            JourneyRecord(sessions = remoteOtherDevices),
            remote.completeDeviceIds - deviceId,
            overlaps,
        )
    }

    private suspend fun pushOwnDevice(pubkey: String, local: JourneyRecord): Push {
        val stable = local.copy(sessions = local.sessions.filter {
            it.deviceId == deviceId && it.status != JourneySessionStatus.ACTIVE && it.status != JourneySessionStatus.PAUSED
        })
        val chunks = JourneySyncProtocol.chunks(stable, deviceId)
        val hashes = LinkedHashMap<Int, String>()
        for (chunk in chunks) {
            val plain = JourneySyncProtocol.encodeChunk(chunk)
            val hash = JourneySyncProtocol.chunkHash(plain)
            hashes[chunk.index] = hash
            val address = JourneySyncProtocol.chunkDTag(pubkey, deviceId, chunk.index)
            if (store.publishedFingerprint(address) == hash) continue
            val published = publishEncrypted(pubkey, address, plain) ?: return Push.CRYPTO
            if (!published) return Push.RELAY
            store.setPublishedFingerprint(address, hash)
        }
        val manifest = JourneySyncProtocol.encodeManifest(JourneySyncManifest(deviceId, hashes))
        val manifestHash = JourneySyncProtocol.chunkHash(manifest)
        val manifestAddress = JourneySyncProtocol.manifestDTag(pubkey, deviceId)
        if (store.publishedFingerprint(manifestAddress) != manifestHash) {
            val published = publishEncrypted(pubkey, manifestAddress, manifest) ?: return Push.CRYPTO
            if (!published) return Push.RELAY
            store.setPublishedFingerprint(manifestAddress, manifestHash)
        }
        return Push.OK
    }

    /** null means the signer could not encrypt/sign; false means relay failure. */
    private suspend fun publishEncrypted(pubkey: String, address: String, plaintext: String): Boolean? {
        if (plaintext.toByteArray(Charsets.UTF_8).size > FavoritesSyncProtocol.MAX_PLAINTEXT_BYTES) return null
        val padded = FavoritesSyncProtocol.padToBucket(plaintext)
        val encrypted = signer.nip44Encrypt(pubkey, padded) ?: return null
        val createdAt = FavoritesSyncProtocol.nextCreatedAt(nowSeconds(), store.lastCreatedAt ?: 0L)
        val signed = signer.sign(JourneySyncProtocol.draft(pubkey, createdAt, address, encrypted)) ?: return null
        if (!publisherFactory(relays()).publish(signed)) return false
        store.lastCreatedAt = createdAt
        diagnostics("published ${address.substringAfterLast(':')} at $createdAt")
        return true
    }

    private suspend fun pull(pubkey: String): Pull {
        val events = coroutineScope {
            relays().map { relay ->
                async {
                    val subscription = NativeNostrWire.randomSubscriptionId()
                    @Suppress("UNCHECKED_CAST")
                    val filter = JourneySyncProtocol.fetchRequest(subscription, pubkey)[2] as Map<String, Any?>
                    fetcher.fetch(
                        relay,
                        subscription,
                        filter,
                        JourneySyncProtocol.KIND,
                        NostrIngressRoute.JOURNEY_SNAPSHOT,
                        JourneySyncProtocol.MAX_EVENTS_PER_RELAY,
                    ) { event ->
                        JourneySyncProtocol.eventIsBound(event, pubkey) { NativeNostrWire.verify(event) }
                    }
                }
            }.awaitAll().flatten()
        }
        if (events.isEmpty()) return Pull.Ok(emptyList(), emptySet())
        val newest = JourneySyncProtocol.newestByAddress(events)
        val chunks = HashMap<Pair<String, Int>, Pair<String, JourneySyncChunk>>()
        val manifests = HashMap<String, JourneySyncManifest>()
        var unreadable = 0
        for ((address, event) in newest) {
            val encrypted = event["content"] as? String ?: continue
            val plaintext = signer.nip44Decrypt(pubkey, encrypted)?.trim()
            if (plaintext == null) {
                unreadable++
                continue
            }
            val chunk = JourneySyncProtocol.decodeChunk(plaintext)
            val manifest = if (chunk == null) JourneySyncProtocol.decodeManifest(plaintext) else null
            when {
                chunk != null && JourneySyncProtocol.dTagMatches(pubkey, chunk.deviceId, address, chunk.index) ->
                    chunks[chunk.deviceId to chunk.index] = JourneySyncProtocol.chunkHash(plaintext) to chunk
                manifest != null && JourneySyncProtocol.dTagMatches(pubkey, manifest.deviceId, address, null) ->
                    manifests[manifest.deviceId] = manifest
                else -> unreadable++
            }
        }
        if (unreadable > 0 && manifests.isEmpty()) return Pull.Invalid
        val complete = LinkedHashSet<String>()
        val sessions = ArrayList<JourneySession>()
        manifests.values.forEach { manifest ->
            val resolved = manifest.chunkHashes.map { (index, expectedHash) ->
                chunks[manifest.deviceId to index]?.takeIf { it.first == expectedHash }?.second
            }
            if (resolved.any { it == null }) return@forEach
            complete += manifest.deviceId
            resolved.filterNotNull().forEach { sessions += it.sessions }
        }
        return Pull.Ok(sessions.distinctBy(JourneySession::id), complete)
    }

    private fun potentialOverlaps(sessions: List<JourneySession>): Int {
        val ended = sessions.filter { it.endedAtEpochMillis != null }.sortedBy { it.startedAtEpochMillis }
        var count = 0
        for (leftIndex in ended.indices) {
            val left = ended[leftIndex]
            for (rightIndex in leftIndex + 1 until ended.size) {
                val right = ended[rightIndex]
                if (right.startedAtEpochMillis >= (left.endedAtEpochMillis ?: continue)) break
                if (left.deviceId != right.deviceId && right.endedAtEpochMillis!! > left.startedAtEpochMillis) count++
            }
        }
        return count
    }

    private fun fail(phase: JourneySyncPhase, detail: String, result: JourneySyncResult): JourneySyncResult {
        diagnostics(detail)
        _state.value = _state.value.copy(phase = phase, localChangesPending = true, detail = detail)
        return result
    }

    private enum class Push { OK, CRYPTO, RELAY }
    private sealed interface Pull {
        data class Ok(val sessions: List<JourneySession>, val completeDeviceIds: Set<String>) : Pull
        data object Invalid : Pull
    }
}
