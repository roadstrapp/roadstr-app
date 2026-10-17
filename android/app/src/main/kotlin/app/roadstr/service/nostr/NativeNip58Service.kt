package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.Nip58BadgeAward
import app.roadstr.core.protocol.nostr.Nip58BadgeReference
import app.roadstr.core.protocol.nostr.Nip58Protocol
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Client-side NIP-58 flow. It never issues Roadstr awards; those require a separate issuer key/service. */
class NativeNip58Service(
    private val signer: NativeNostrSigner,
    private val fetcher: NativeRelayFetcher,
    private val publisher: NativeRelayPublisher,
    private val relays: List<String>,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
) {
    suspend fun findAward(reference: Nip58BadgeReference, recipient: String): Nip58BadgeAward? {
        val events = coroutineScope {
            relays.map { relay ->
                async {
                    val subscription = NativeNostrWire.randomSubscriptionId()
                    @Suppress("UNCHECKED_CAST")
                    val filter = Nip58Protocol.awardRequest(subscription, reference.issuerPubkey, recipient)[2]
                        as Map<String, Any?>
                    fetcher.fetch(
                        relay,
                        subscription,
                        filter,
                        Nip58Protocol.BADGE_AWARD_KIND,
                        NostrIngressRoute.BADGE_AWARD,
                        100,
                    ) { NativeNostrWire.verify(it) }
                }
            }.awaitAll().flatten()
        }
        return events.distinctBy { it["id"] }.mapNotNull { event ->
            Nip58Protocol.parseAward(event) { NativeNostrWire.verify(event) }
        }.filter { it.reference == reference && recipient in it.recipients }
            .maxByOrNull { award ->
                val event = events.firstOrNull { it["id"] == award.eventId }
                NativeNostrWire.integral(event?.get("created_at")) ?: 0L
            }
    }

    /** Explicitly accepts one verified award while preserving the account's existing NIP-58 list. */
    suspend fun addToProfile(award: Nip58BadgeAward): Boolean {
        val owner = signer.pubkeyHex ?: return false
        if (owner !in award.recipients) return false
        val existing = fetchProfile(owner)
        val draft = Nip58Protocol.profileBadgesDraft(
            ownerPubkey = owner,
            createdAt = nowSeconds(),
            existingTags = existing?.let { NativeNostrWire.tags(it["tags"]) }.orEmpty(),
            accepted = award,
        )
        val signed = signer.sign(draft) ?: return false
        return publisher.publish(signed)
    }

    private suspend fun fetchProfile(owner: String): Map<String, Any?>? {
        val events = coroutineScope {
            relays.map { relay ->
                async {
                    val subscription = NativeNostrWire.randomSubscriptionId()
                    @Suppress("UNCHECKED_CAST")
                    val filter = Nip58Protocol.profileRequest(subscription, owner)[2] as Map<String, Any?>
                    fetcher.fetch(
                        relay,
                        subscription,
                        filter,
                        Nip58Protocol.PROFILE_BADGES_KIND,
                        NostrIngressRoute.PROFILE_BADGES,
                        4,
                    ) { event ->
                        event["pubkey"] == owner && NativeNostrWire.verify(event)
                    }
                }
            }.awaitAll().flatten()
        }
        return events.distinctBy { it["id"] }.maxWithOrNull(
            compareBy<Map<String, Any?>> { NativeNostrWire.integral(it["created_at"]) ?: 0L }
                .thenBy { it["id"].toString() },
        )
    }
}
