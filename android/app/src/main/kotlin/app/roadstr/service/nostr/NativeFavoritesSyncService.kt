package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.FavoritesSyncProtocol
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The two values sync must remember between launches. */
interface NativeFavoritesSyncStore {
    /** Newest snapshot timestamp this device has seen or published (anti-rollback). */
    var lastCreatedAt: Long?

    /** Whether the obsolete fixed-tag snapshot has already been wiped. */
    var legacyCleaned: Boolean
}

sealed interface NativeFavoritesPull {
    /** A verified, decrypted snapshot. Entries are raw maps for the caller to validate. */
    data class Ok(val favorites: List<Map<String, Any?>>) : NativeFavoritesPull

    /** No usable snapshot: none published, stale, unreadable or not ours. */
    data object None : NativeFavoritesPull

    /** The snapshot is passphrase-protected and the passphrase is missing or wrong. */
    data object Locked : NativeFavoritesPull
}

/**
 * Encrypted favourites backup on Nostr (NIP-78, kind 30078), a port of the
 * Flutter FavoritesSyncService.
 *
 * What a relay sees: a replaceable event under a per-user hashed `d` tag
 * (not a fixed, fingerprintable one), whose content is NIP-44 ciphertext
 * padded to a 4 KiB bucket so its length does not track the number of places,
 * and whose timestamp is rounded to the hour. An optional passphrase wraps the
 * list in a second, independent layer before the NIP-44 one.
 *
 * Relays are untrusted: every snapshot is bound to our key, kind and tag and
 * its signature verified; older ones than we have already seen are ignored.
 */
class NativeFavoritesSyncService(
    private val signer: NativeNostrSigner,
    connector: NativeRelayConnector,
    private val store: NativeFavoritesSyncStore,
    private val passphrase: () -> String?,
    private val customRelay: () -> String?,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val fetcher: NativeRelayFetcher = NativeRelayFetcher(connector),
    private val publisherFactory: (List<String>) -> NativeRelayPublisher = {
        NativeRelayPublisher(connector, it)
    },
) {
    private val pushLock = Mutex()

    /** Defaults plus the user's own relay, if it is a valid wss:// address. */
    fun relays(): List<String> {
        val extra = customRelay()?.let(FavoritesSyncProtocol::normaliseRelayUrl)
        return FavoritesSyncProtocol.defaultRelays + listOfNotNull(extra)
    }

    /**
     * Publishes [favorites]. Serialised: a snapshot is replaceable, so two
     * overlapping pushes would race on the same timestamp high-water mark.
     */
    suspend fun push(favorites: List<Map<String, Any?>>): Boolean = pushLock.withLock {
        runCatching { pushLocked(favorites) }.getOrDefault(false)
    }

    private suspend fun pushLocked(favorites: List<Map<String, Any?>>): Boolean {
        val pubkey = signer.pubkeyHex ?: return false
        var plaintext = FavoritesSyncProtocol.encodeFavorites(favorites)
        val secret = passphrase()
        if (!secret.isNullOrEmpty()) {
            // Off the caller's thread: key derivation is deliberately slow and
            // this runs on every favourite edit via auto-push.
            val sealed = withContext(Dispatchers.Default) { NativeFavoritesCrypto.encrypt(plaintext, secret) }
            plaintext = FavoritesSyncProtocol.wrapPassphraseEnvelope(sealed)
        }
        if (plaintext.toByteArray(Charsets.UTF_8).size > FavoritesSyncProtocol.MAX_PLAINTEXT_BYTES) return false
        val encrypted = signer.nip44Encrypt(pubkey, FavoritesSyncProtocol.padToBucket(plaintext)) ?: return false

        val createdAt = FavoritesSyncProtocol.nextCreatedAt(nowSeconds(), store.lastCreatedAt ?: 0L)
        val signed = signer.sign(FavoritesSyncProtocol.snapshotDraft(pubkey, createdAt, encrypted)) ?: return false

        val published = publisherFactory(relays()).publish(signed)
        if (!published) return false
        store.lastCreatedAt = createdAt
        // One-time hygiene: wipe and ask relays to delete the old fingerprintable
        // fixed-tag snapshot. Only when signing is silent: on an external signer
        // each event is another confirmation popup, and the cleanup is
        // best-effort anyway (archiving relays keep history regardless).
        if (signer.signsSilently && !store.legacyCleaned) {
            cleanupLegacy(pubkey)
            store.legacyCleaned = true
        }
        return true
    }

    private suspend fun cleanupLegacy(pubkey: String) {
        val publisher = publisherFactory(relays())
        val wipe = signer.sign(FavoritesSyncProtocol.legacyWipeDraft(pubkey, nextTimestamp()))
        val deletion = signer.sign(FavoritesSyncProtocol.legacyDeletionDraft(pubkey, nextTimestamp()))
        wipe?.let { publisher.publish(it) }
        deletion?.let { publisher.publish(it) }
    }

    private fun nextTimestamp(): Long =
        FavoritesSyncProtocol.nextCreatedAt(nowSeconds(), store.lastCreatedAt ?: 0L)

    /**
     * Fetches, verifies and decrypts the newest snapshot across all relays.
     * Snapshots older than the local high-water mark are ignored: a stale relay
     * or a deliberate replay of an outdated, validly signed snapshot.
     */
    suspend fun pull(passphraseOverride: String? = null): NativeFavoritesPull {
        val pubkey = signer.pubkeyHex ?: return NativeFavoritesPull.None
        val best = fetchNewest(pubkey, FavoritesSyncProtocol.hashedDTag(pubkey))
            // Snapshots pushed by versions that still used the fixed tag.
            ?: fetchNewest(pubkey, FavoritesSyncProtocol.LEGACY_D_TAG)
            ?: return NativeFavoritesPull.None

        val fetchedAt = NativeNostrWire.integral(best["created_at"]) ?: 0L
        if (!FavoritesSyncProtocol.passesRollbackGuard(fetchedAt, store.lastCreatedAt)) {
            return NativeFavoritesPull.None
        }
        val content = best["content"] as? String
        if (content.isNullOrEmpty()) return NativeFavoritesPull.None
        val plaintext = signer.nip44Decrypt(pubkey, content) ?: return NativeFavoritesPull.None

        val decoded = runCatching { BoundedJsonParser(plaintext.trim()).parse() }.getOrNull()
            ?: return NativeFavoritesPull.None
        val list: List<*> = when {
            decoded is Map<*, *> && decoded["encrypted"] == true -> {
                val secret = passphraseOverride ?: passphrase()
                if (secret.isNullOrEmpty()) return NativeFavoritesPull.Locked
                val inner = try {
                    withContext(Dispatchers.Default) { NativeFavoritesCrypto.decrypt(decoded, secret) }
                } catch (_: NativeFavoritesDecryptException) {
                    return NativeFavoritesPull.Locked // wrong passphrase: ask again
                }
                runCatching { BoundedJsonParser(inner).parse() }.getOrNull() as? List<*>
                    ?: return NativeFavoritesPull.None
            }

            decoded is List<*> -> decoded
            else -> return NativeFavoritesPull.None
        }
        store.lastCreatedAt = fetchedAt
        @Suppress("UNCHECKED_CAST")
        val entries = list.filterIsInstance<Map<*, *>>().map { it as Map<String, Any?> }
        return NativeFavoritesPull.Ok(entries)
    }

    private suspend fun fetchNewest(pubkey: String, dTag: String): Map<String, Any?>? {
        val results = coroutineScope {
            relays().map { url ->
                async {
                    val subscription = NativeNostrWire.randomSubscriptionId()
                    fetcher.fetch(
                        url = url,
                        subscriptionId = subscription,
                        filter = FavoritesSyncProtocol.fetchRequest(subscription, pubkey, dTag)[2]
                            as Map<String, Any?>,
                        kind = FavoritesSyncProtocol.KIND,
                        route = NostrIngressRoute.FAVORITE_SNAPSHOT,
                        maxEvents = MAX_EVENTS_PER_RELAY,
                        accept = { event ->
                            FavoritesSyncProtocol.snapshotEventIsBound(event, pubkey, dTag) {
                                NativeNostrWire.verify(event)
                            }
                        },
                    ).let(FavoritesSyncProtocol::newestSnapshot)
                }
            }.awaitAll()
        }
        return FavoritesSyncProtocol.newestSnapshot(results)
    }

    private companion object {
        const val MAX_EVENTS_PER_RELAY = 4
    }
}
