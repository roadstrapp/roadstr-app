package app.roadstr.roadtest

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
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

/** What a signer app answered; any field can be missing. */
internal class NativeAmberReply(
    val signature: String?,
    val result: String?,
    val event: String?,
)

/**
 * One NIP-55 request at a time to the Android signer app (Amber).
 *
 * The signer's content provider answers silently once the user has chosen
 * "remember my choice", exactly like the Flutter plugin does, and an Intent
 * round trip with the signer's UI is the fallback. The Intent result arrives
 * through the Activity's result callback, which can be re-created (rotation)
 * while the signer is in front, so the pending request lives in the companion
 * rather than in the instance.
 */
internal class NativeRoadTestAmberBridge(
    private val resolver: () -> ContentResolver,
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
    ): NativeAmberReply? = gate.withLock {
        silentReply(type, payload, currentUser, peerPubkey)?.let {
            Log.d(TAG, "$type answered silently")
            return@withLock it
        }
        val reply = CompletableDeferred<NativeAmberReply?>()
        pending = reply
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$payload"))
            .putExtra("type", type)
            .putExtra("id", id)
            .putExtra("current_user", currentUser)
            .putExtra("pubKey", peerPubkey.orEmpty())
        signerPackage()?.let { intent.setPackage(it) }
        try {
            val launched = runCatching { withContext(Dispatchers.Main) { launch(intent) } }.isSuccess
            if (!launched) {
                Log.d(TAG, "$type: the signer app could not be opened")
                return@withLock null
            }
            withTimeoutOrNull(REPLY_TIMEOUT_MILLIS) { reply.await() }.also {
                Log.d(TAG, "$type: ${if (it == null) "declined, closed or timed out" else "answered by the signer UI"}")
            }
        } finally {
            if (pending === reply) pending = null
        }
    }

    /** Called from the Activity's result callback. */
    fun deliver(resultOk: Boolean, data: Intent?) {
        val reply = if (resultOk && data != null) {
            NativeAmberReply(
                signature = data.getStringExtra("signature"),
                result = data.getStringExtra("result"),
                event = data.getStringExtra("event"),
            )
        } else {
            null
        }
        pending?.complete(reply)
    }

    private suspend fun silentReply(
        type: String,
        payload: String,
        currentUser: String,
        peerPubkey: String?,
    ): NativeAmberReply? = withContext(Dispatchers.IO) {
        val authority = "${signerPackage() ?: DEFAULT_SIGNER_PACKAGE}.${type.uppercase()}"
        runCatching {
            resolver().query(
                Uri.parse("content://$authority"),
                arrayOf(payload, peerPubkey.orEmpty(), currentUser),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                fun column(name: String): String? =
                    cursor.getColumnIndex(name).takeIf { it >= 0 }?.let(cursor::getString)
                // A "rejected" column means the user denied it permanently.
                if (cursor.getColumnIndex("rejected") >= 0) return@use null
                NativeAmberReply(column("signature"), column("result"), column("event"))
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "RoadstrAmber"
        const val REPLY_TIMEOUT_MILLIS = 120_000L
        const val DEFAULT_SIGNER_PACKAGE = "com.greenart7c3.nostrsigner"
        val gate = Mutex()

        @Volatile
        var pending: CompletableDeferred<NativeAmberReply?>? = null
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
        return listOfNotNull(reply.result, reply.signature)
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() && it.length <= MAX_REPLY_CHARS }
    }

    private fun signatureOf(reply: NativeAmberReply): String? {
        reply.signature?.trim()?.takeIf(SIGNATURE::matches)?.let { return it }
        val eventJson = reply.event?.takeIf { it.length <= MAX_REPLY_CHARS } ?: return null
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
