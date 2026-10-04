package app.roadstr.roadtest

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.core.protocol.nostr.NostrIngressRule
import app.roadstr.core.protocol.nostr.NostrRelayEoseMessage
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayIngress
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.feature.profile.NativeProfileMetadata
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Read-only NIP-01 kind-0 metadata reader used by the standalone road-test app. */
class NativeRoadTestNostrProfileService {
    private data class ProfileCandidate(
        val createdAt: Long,
        val metadata: NativeProfileMetadata,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(pubkeyHex: String): NativeProfileMetadata? {
        if (!HEX_64.matches(pubkeyHex)) return null
        val results = withContext(Dispatchers.IO) {
            supervisorScope {
                RELAYS.map { relay ->
                    async {
                        withTimeoutOrNull(PROFILE_RELAY_TIMEOUT_MILLIS) {
                            runCatching { fetchFromRelay(relay, pubkeyHex) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
        }
        return results.filterNotNull().maxByOrNull(ProfileCandidate::createdAt)?.metadata
    }

    private suspend fun fetchFromRelay(
        relay: String,
        pubkeyHex: String,
    ): ProfileCandidate? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(relay).build()
        val socketReference = AtomicReference<WebSocket?>(null)
        val completed = AtomicBoolean(false)
        var latest: ProfileCandidate? = null
        val subscriptionId = "roadstr-profile-${UUID.randomUUID()}"
        val ingress = NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "profile",
                    subscriptionId = subscriptionId,
                    routes = mapOf(0 to NostrIngressRoute.PROFILE_METADATA),
                    maxEvents = PROFILE_MAX_EVENTS,
                ),
            ),
        )

        fun finish(value: ProfileCandidate?) {
            if (completed.compareAndSet(false, true)) {
                socketReference.get()?.close(1000, "done")
                if (continuation.isActive) continuation.resume(value)
            }
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketReference.set(webSocket)
                webSocket.send(
                    NostrRelayWire.encode(
                        listOf(
                            "REQ",
                            subscriptionId,
                            linkedMapOf(
                                "kinds" to listOf(0),
                                "authors" to listOf(pubkeyHex),
                                "limit" to 1,
                            ),
                        ),
                    ),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (completed.get()) return
                val message = NostrRelayMessageDecoder.decode(text).message ?: return
                when (message) {
                    is NostrRelayEoseMessage -> {
                        if (message.subscriptionId == subscriptionId) finish(latest)
                    }

                    is NostrRelayEventMessage -> {
                        val decision = ingress.inspect(
                            subscriptionId = message.subscriptionId,
                            claimedKind = message.event["kind"],
                        )
                        if (decision.limitReached) {
                            finish(latest)
                            return
                        }
                        if (!decision.shouldVerify) return
                        verifyAndRead(message, pubkeyHex)?.let { candidate ->
                            if (latest == null || candidate.createdAt > latest!!.createdAt) {
                                latest = candidate
                            }
                        }
                    }

                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(latest)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(latest)
            }
        }
        val socket = client.newWebSocket(request, listener)
        socketReference.compareAndSet(null, socket)
        continuation.invokeOnCancellation {
            socketReference.get()?.cancel()
        }
    }

    private fun verifyAndRead(
        message: NostrRelayEventMessage,
        expectedPubkey: String,
    ): ProfileCandidate? = runCatching {
        val event = message.event
        val pubkey = event["pubkey"] as? String ?: return null
        if (pubkey != expectedPubkey) return null
        val id = event["id"] as? String ?: return null
        val signature = event["sig"] as? String ?: return null
        val createdAt = (event["created_at"] as? Number)?.toLong() ?: return null
        val kind = (event["kind"] as? Number)?.toInt() ?: return null
        if (kind != 0 || createdAt < 0 || id.length != 64 || signature.length != 128) {
            return null
        }
        val tags = eventTags(event["tags"]) ?: return null
        val content = event["content"] as? String ?: return null
        val draft = NostrEventDraft(pubkey, createdAt, kind, tags, content)
        val computedId = draft.id()
        if (computedId != id) {
            return null
        }
        if (!NostrSchnorr.verifyHash(pubkey, id, signature)) {
            return null
        }
        val metadata = BoundedJsonParser(content).parse() as? Map<*, *> ?: return null
        ProfileCandidate(
            createdAt = createdAt,
            metadata = NativeProfileMetadata(
                name = boundedText(metadata["name"], MAX_TEXT),
                displayName = boundedText(metadata["display_name"], MAX_TEXT),
                pictureUrl = safePictureUrl(metadata["picture"]),
            ),
        )
    }.getOrNull()

    private fun eventTags(raw: Any?): List<List<String>>? {
        val values = raw as? List<*> ?: return emptyList()
        return values.map { rawTag ->
            val tag = rawTag as? List<*> ?: return null
            tag.map { value -> value as? String ?: return null }
        }
    }

    private fun boundedText(raw: Any?, maxLength: Int): String? {
        val value = raw as? String ?: return null
        return value.trim().takeIf { it.isNotEmpty() }?.take(maxLength)
    }

    private fun safePictureUrl(raw: Any?): String? {
        val value = boundedText(raw, MAX_URL) ?: return null
        return runCatching {
            URI(value).takeIf { uri ->
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }?.toASCIIString()
        }.getOrNull()
    }

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
        const val MAX_TEXT = 200
        const val MAX_URL = 2_048
        const val PROFILE_MAX_EVENTS = 8
        const val PROFILE_RELAY_TIMEOUT_MILLIS = 8_000L
        val RELAYS = listOf(
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.oxtr.dev",
            "wss://relay.damus.io",
        )
    }
}
