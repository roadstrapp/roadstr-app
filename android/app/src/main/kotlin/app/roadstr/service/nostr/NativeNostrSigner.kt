package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrSchnorr

/**
 * Signing and NIP-44 encryption for the logged-in account.
 *
 * Two implementations exist: a local key ([NativeLocalKeySigner]) and the
 * Android signer app (Amber, NIP-55), which lives next to the Activity because
 * it needs an Intent round trip. Callers never see key material.
 */
interface NativeNostrSigner {
    /** Public key of the account, or null when nobody is logged in. */
    val pubkeyHex: String?

    /**
     * True when signing needs no user interaction. An external signer app can
     * pop a confirmation for every call, so best-effort chores (like wiping an
     * obsolete event) are skipped for it.
     */
    val signsSilently: Boolean

    /** Signs [draft] and returns the full wire event, or null if declined or failed. */
    suspend fun sign(draft: NostrEventDraft): Map<String, Any?>?

    /** NIP-44 v2 encryption to [peerPubkeyHex]; null on failure. */
    suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String?

    /** NIP-44 v2 decryption from [peerPubkeyHex]; null on failure. */
    suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String?
}

/** Pure-Kotlin signer holding a private key supplied by a key store. */
class NativeLocalKeySigner(private val privateKeyProvider: () -> String?) : NativeNostrSigner {
    override val signsSilently: Boolean = true

    override val pubkeyHex: String?
        get() = privateKeyProvider()?.let { runCatching { NostrSchnorr.publicKey(it) }.getOrNull() }

    override suspend fun sign(draft: NostrEventDraft): Map<String, Any?>? = runCatching {
        val key = privateKeyProvider() ?: return null
        NostrSchnorr.signEvent(draft, key)
    }.getOrNull()

    override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? = runCatching {
        val key = privateKeyProvider() ?: return null
        Nip44V2.encrypt(key, peerPubkeyHex, plaintext)
    }.getOrNull()

    override suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String? = runCatching {
        val key = privateKeyProvider() ?: return null
        Nip44V2.decrypt(key, peerPubkeyHex, payload)
    }.getOrNull()
}
