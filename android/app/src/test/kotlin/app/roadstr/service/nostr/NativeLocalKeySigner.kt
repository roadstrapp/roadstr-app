package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrSchnorr

/** A signer holding a private key, for tests only: the app itself never keeps the account's key. */
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
