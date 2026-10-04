package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.core.protocol.nostr.NostrIngressRule
import app.roadstr.core.protocol.nostr.NostrRelayEoseMessage
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayIngress
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayWire
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot query of a single relay: send a REQ, collect the events that pass
 * [accept], stop at EOSE, at the event budget or at the deadline.
 *
 * The budget is counted before the signature check, so a relay ignoring the
 * `limit` in the filter and flooding events cannot make the client pay for
 * verifying all of them. Whatever was collected when the query ends, for any
 * reason, is returned; a failed relay returns an empty list.
 */
class NativeRelayFetcher(
    private val connector: NativeRelayConnector,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun fetch(
        url: String,
        subscriptionId: String,
        filter: Map<String, Any?>,
        kind: Int,
        route: NostrIngressRoute,
        maxEvents: Int,
        accept: (Map<String, Any?>) -> Boolean,
    ): List<Map<String, Any?>> {
        val collected = ArrayList<Map<String, Any?>>()
        val frame = NostrRelayWire.encode(listOf("REQ", subscriptionId, filter))
        val socketRef = AtomicReference<NativeRelaySocket?>(null)
        val ingress = NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "fetch",
                    subscriptionId = subscriptionId,
                    routes = mapOf(kind to route),
                    maxEvents = maxEvents,
                ),
            ),
        )
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val done = AtomicBoolean(false)
                val opened = AtomicBoolean(false)
                val sent = AtomicBoolean(false)
                fun finish() {
                    if (done.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                fun trySend() {
                    val socket = socketRef.get() ?: return
                    if (opened.get() && sent.compareAndSet(false, true) && !socket.send(frame)) finish()
                }
                val connected = try {
                    connector.connect(
                        url,
                        object : NativeRelayEvents {
                            override fun onOpen() {
                                opened.set(true)
                                trySend()
                            }

                            override fun onMessage(text: String) {
                                if (done.get()) return
                                when (val message = NostrRelayMessageDecoder.decode(text).message) {
                                    is NostrRelayEoseMessage ->
                                        if (message.subscriptionId == subscriptionId) finish()

                                    is NostrRelayEventMessage -> {
                                        val decision = ingress.inspect(
                                            message.subscriptionId,
                                            message.event["kind"],
                                        )
                                        if (decision.limitReached) {
                                            finish()
                                        } else if (decision.shouldVerify && accept(message.event)) {
                                            synchronized(collected) { collected += message.event }
                                        }
                                    }

                                    else -> Unit
                                }
                            }

                            override fun onEnded() = finish()
                        },
                    )
                } catch (_: Exception) {
                    finish()
                    return@suspendCancellableCoroutine
                }
                socketRef.set(connected)
                trySend()
                continuation.invokeOnCancellation { connected.close() }
            }
        }
        socketRef.get()?.close()
        return synchronized(collected) { collected.toList() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 6_000L
    }
}
