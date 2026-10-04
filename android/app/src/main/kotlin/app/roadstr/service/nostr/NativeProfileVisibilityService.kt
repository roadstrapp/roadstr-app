package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Pseudonymous or clear presentation of the user's reports to other Roadstr
 * users, carried by a replaceable NIP-78 event (`roadstr-profile-visibility`).
 *
 * The public key is always published, because it is what lets anyone verify a
 * report. In pseudonymous mode other users see only a generic label; in clear
 * mode they may see the profile name and avatar. A missing event is read as
 * pseudonymous: the safe default is never to unmask anybody.
 */
class NativeProfileVisibilityService(
    private val signer: NativeNostrSigner,
    private val publisher: NativeRelayPublisher,
    private val fetcher: NativeRelayFetcher,
    private val relays: List<String>,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
) {
    /** Publishes our own preference. False if signing or every relay failed. */
    suspend fun publish(isPublic: Boolean): Boolean {
        val pubkey = signer.pubkeyHex ?: return false
        val signed = signer.sign(RoadstrNostrEvents.profileVisibility(pubkey, nowSeconds(), isPublic))
            ?: return false
        return publisher.publish(signed)
    }

    /**
     * The newest declared preference of [pubkey]: true for clear, false for
     * pseudonymous, null when nothing could be read (treat as pseudonymous).
     */
    suspend fun fetch(pubkey: String): Boolean? {
        if (!NativeNostrWire.isHex32(pubkey) || relays.isEmpty()) return null
        val perRelay = coroutineScope {
            relays.map { url ->
                async {
                    val subscription = NativeNostrWire.randomSubscriptionId()
                    fetcher.fetch(
                        url = url,
                        subscriptionId = subscription,
                        filter = linkedMapOf(
                            "kinds" to listOf(30078),
                            "authors" to listOf(pubkey),
                            "#d" to listOf(D_TAG),
                            "limit" to 5,
                        ),
                        kind = 30078,
                        route = NostrIngressRoute.PROFILE_VISIBILITY,
                        maxEvents = MAX_EVENTS,
                        accept = { event -> declared(event, pubkey) != null },
                    )
                }
            }.awaitAll()
        }
        return perRelay.flatten()
            .mapNotNull { event -> declared(event, pubkey) }
            .maxByOrNull { it.first }
            ?.second
    }

    /** (createdAt, isPublic) of a verified, correctly tagged visibility event of [pubkey]. */
    private fun declared(event: Map<String, Any?>, pubkey: String): Pair<Long, Boolean>? {
        if (event["pubkey"] != pubkey) return null
        if (NativeNostrWire.tags(event["tags"])?.any { it.size >= 2 && it[0] == "d" && it[1] == D_TAG } != true) {
            return null
        }
        if (!NativeNostrWire.verify(event)) return null
        val content = runCatching { BoundedJsonParser(event["content"] as String).parse() }.getOrNull()
        val public = (content as? Map<*, *>)?.get("public") as? Boolean ?: return null
        val createdAt = NativeNostrWire.integral(event["created_at"]) ?: return null
        return createdAt to public
    }

    private companion object {
        const val D_TAG = "roadstr-profile-visibility"
        const val MAX_EVENTS = 10
    }
}
