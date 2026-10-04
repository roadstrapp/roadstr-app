package app.roadstr.roadtest

import android.content.Intent
import android.net.Uri
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.feature.profile.NativeIdentityGateway
import app.roadstr.feature.profile.NativeProfileIdentityFlavor
import app.roadstr.service.nostr.NativeLocalKeySigner
import app.roadstr.service.nostr.NativeNostrSigner
import app.roadstr.service.nostr.NativeNostrWire
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One NIP-55 round trip at a time with the Android signer app (Amber).
 *
 * The signer answers through the Activity's result callback, which can be
 * re-created (rotation) while the signer is in front, so the pending request
 * lives in the companion rather than in the instance.
 */
internal class NativeRoadTestAmberBridge(
    private val launch: (Intent) -> Unit,
    private val signerPackage: () -> String?,
) {
    /** The signer's reply, or null if it was declined, closed, missing or too slow. */
    suspend fun request(
        type: String,
        payload: String,
        currentUser: String,
        id: String,
        peerPubkey: String? = null,
    ): Intent? = gate.withLock {
        val reply = CompletableDeferred<Intent?>()
        pending = reply
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$payload"))
            .putExtra("type", type)
            .putExtra("id", id)
            .putExtra("current_user", currentUser)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        peerPubkey?.let { intent.putExtra("pubKey", it) }
        signerPackage()?.let { intent.setPackage(it) }
        try {
            val launched = runCatching { withContext(Dispatchers.Main) { launch(intent) } }.isSuccess
            if (!launched) return@withLock null
            withTimeoutOrNull(REPLY_TIMEOUT_MILLIS) { reply.await() }
        } finally {
            if (pending === reply) pending = null
        }
    }

    /** Called from the Activity's result callback. */
    fun deliver(resultOk: Boolean, data: Intent?) {
        pending?.complete(if (resultOk) data else null)
    }

    private companion object {
        const val REPLY_TIMEOUT_MILLIS = 120_000L
        val gate = Mutex()

        @Volatile
        var pending: CompletableDeferred<Intent?>? = null
    }
}

/** Signs and encrypts through Amber; the private key never reaches this app. */
internal class NativeAmberSigner(
    private val bridge: NativeRoadTestAmberBridge,
    private val currentUser: () -> String?,
) : NativeNostrSigner {
    override val pubkeyHex: String? get() = currentUser()

    /** Every call can pop a confirmation, so best-effort chores are skipped. */
    override val signsSilently: Boolean = false

    override suspend fun sign(draft: NostrEventDraft): Map<String, Any?>? {
        val user = currentUser()
        if (user == null || draft.pubkey != user) return null
        val reply = bridge.request(
            type = "sign_event",
            payload = NostrJson.encode(draft.toWireMap()),
            currentUser = user,
            id = draft.id(),
        ) ?: return null
        val signature = signatureOf(reply) ?: return null
        // Rebuilt from our own draft and verified: whatever the signer app
        // answers can never put different content on the wire.
        val signed = draft.toWireMap(signature)
        return signed.takeIf { NativeNostrWire.verify(it) }
    }

    override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? =
        transform("nip44_encrypt", peerPubkeyHex, plaintext)

    override suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String? =
        transform("nip44_decrypt", peerPubkeyHex, payload)

    private suspend fun transform(type: String, peer: String, input: String): String? {
        val user = currentUser() ?: return null
        if (!NativeNostrWire.isHex32(peer)) return null
        val reply = bridge.request(
            type = type,
            payload = input,
            currentUser = user,
            id = NativeNostrWire.randomSubscriptionId(),
            peerPubkey = peer,
        ) ?: return null
        return listOfNotNull(reply.getStringExtra("result"), reply.getStringExtra("signature"))
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() && it.length <= MAX_REPLY_CHARS }
    }

    private fun signatureOf(reply: Intent): String? {
        reply.getStringExtra("signature")?.trim()?.takeIf(SIGNATURE::matches)?.let { return it }
        val eventJson = reply.getStringExtra("event")?.takeIf { it.length <= MAX_REPLY_CHARS } ?: return null
        val event = runCatching { BoundedJsonParser(eventJson).parse() as? Map<*, *> }.getOrNull()
        return (event?.get("sig") as? String)?.trim()?.takeIf(SIGNATURE::matches)
    }

    private companion object {
        const val MAX_REPLY_CHARS = 262_144
        val SIGNATURE = Regex("[0-9a-f]{128}")
    }
}

/** Picks the signer that matches how the user logged in. */
internal class NativeRoadTestSigner(
    private val identity: NativeIdentityGateway,
    privateKey: () -> String?,
    amber: NativeRoadTestAmberBridge,
) : NativeNostrSigner {
    private val local = NativeLocalKeySigner(privateKey)
    private val external = NativeAmberSigner(amber) { identity.state.value.pubkeyHex }

    private fun active(): NativeNostrSigner? = when (identity.state.value.flavor) {
        NativeProfileIdentityFlavor.Nsec -> local
        NativeProfileIdentityFlavor.Amber -> external
        null -> null
    }

    override val pubkeyHex: String? get() = active()?.pubkeyHex

    override val signsSilently: Boolean get() = active()?.signsSilently ?: false

    override suspend fun sign(draft: NostrEventDraft): Map<String, Any?>? = active()?.sign(draft)

    override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? =
        active()?.nip44Encrypt(peerPubkeyHex, plaintext)

    override suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String? =
        active()?.nip44Decrypt(peerPubkeyHex, payload)
}
