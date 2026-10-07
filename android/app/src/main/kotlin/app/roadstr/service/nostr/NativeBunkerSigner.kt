package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrEventDraft

/**
 * Signs and encrypts through a remote signer reached over NIP-46 ("bunker"). The private key of the account
 * never reaches this phone. Each call can wait for the person to approve in the signer app, so this signer
 * is not silent: chores that are only nice to have are skipped for it, as for Amber.
 */
class NativeBunkerSigner(
    private val client: NativeBunkerClient,
    private val accountPubkeyHex: () -> String?,
) : NativeNostrSigner {
    override val pubkeyHex: String? get() = accountPubkeyHex()

    override val signsSilently: Boolean = false

    override suspend fun sign(draft: NostrEventDraft): Map<String, Any?>? {
        val account = pubkeyHex ?: return null
        if (draft.pubkey != account) return null
        val unsigned = app.roadstr.core.protocol.nostr.Nip46.eventJson(draft)
        val reply = client.call("sign_event", listOf(unsigned))
        val signed = reply.result?.takeIf { reply.ok }?.let {
            runCatching { BoundedJsonParser(it).parse() as? Map<*, *> }.getOrNull()
        } ?: return null
        @Suppress("UNCHECKED_CAST")
        val event = signed as Map<String, Any?>
        // The signer is trusted to sign, not to substitute: the event must be ours, from this account, and valid.
        if (event["pubkey"] != account) return null
        if (event["kind"]?.let(NativeNostrWire::integral) != draft.kind.toLong()) return null
        if (event["content"] != draft.content) return null
        if (NativeNostrWire.tags(event["tags"]) != draft.tags) return null
        if (!NativeNostrWire.verify(event)) return null
        return event
    }

    override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? {
        val reply = client.call("nip44_encrypt", listOf(peerPubkeyHex, plaintext))
        return reply.result?.takeIf { reply.ok && it.isNotEmpty() }
    }

    override suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String? {
        val reply = client.call("nip44_decrypt", listOf(peerPubkeyHex, payload))
        return reply.result?.takeIf { reply.ok }
    }
}
