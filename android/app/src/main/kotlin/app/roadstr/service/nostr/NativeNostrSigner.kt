package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrEventDraft

/**
 * Signing and NIP-44 encryption for the logged-in account.
 *
 * Two implementations exist: the Android signer app (Amber, NIP-55), which lives
 * next to the Activity because it needs an Intent round trip, and a remote signer
 * reached over relays ([NativeBunkerSigner], NIP-46). The account's private key
 * never reaches this app, and callers never see key material.
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
