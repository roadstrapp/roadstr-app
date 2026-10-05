package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.core.protocol.nostr.NostrIngressRule
import app.roadstr.core.protocol.nostr.NostrRelayEoseMessage
import app.roadstr.core.protocol.nostr.NostrRelayClosedMessage
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayNoticeMessage
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
    /** Host-level notes on how each relay behaved; never event content. */
    private val diagnostics: (String) -> Unit = {},
) {
    suspend fun fetch(
        url: String,
        subscriptionId: String,
        filter: Map<String, Any?>,
        kind: Int,
        route: NostrIngressRoute,
        maxEvents: Int,
        accept: (Map<String, Any?>) -> Boolean,
    ): List<Map<String, Any?>> =
        fetch(url, subscriptionId, filter, mapOf(kind to route), maxEvents, accept)

    /** Same query for a filter that asks for several kinds, each routed on its own. */
    suspend fun fetch(
        url: String,
        subscriptionId: String,
        filter: Map<String, Any?>,
        routes: Map<Int, NostrIngressRoute>,
        maxEvents: Int,
        accept: (Map<String, Any?>) -> Boolean,
    ): List<Map<String, Any?>> {
        val collected = ArrayList<Map<String, Any?>>()
        val host = url.substringAfter("://").substringBefore('/')
        var seen = 0
        val everOpened = AtomicBoolean(false)
        val frame = NostrRelayWire.encode(listOf("REQ", subscriptionId, filter))
        val socketRef = AtomicReference<NativeRelaySocket?>(null)
        val ingress = NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "fetch",
                    subscriptionId = subscriptionId,
                    routes = routes,
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
                                everOpened.set(true)
                                opened.set(true)
                                trySend()
                            }

                            override fun onMessage(text: String) {
                                if (done.get()) return
                                when (val message = NostrRelayMessageDecoder.decode(text).message) {
                                    is NostrRelayEoseMessage ->
                                        if (message.subscriptionId == subscriptionId) {
                                            diagnostics("fetch $host: EOSE, $seen events seen, ${collected.size} accepted")
                                            finish()
                                        }

                                    is NostrRelayClosedMessage ->
                                        if (message.subscriptionId == subscriptionId) {
                                            diagnostics("fetch $host: CLOSED ${message.detail.toString().take(80)}")
                                            finish()
                                        }

                                    is NostrRelayNoticeMessage ->
                                        diagnostics("fetch $host: NOTICE ${message.detail.toString().take(80)}")

                                    is NostrRelayEventMessage -> {
                                        seen++
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

                            override fun onEnded() {
                                diagnostics("fetch $host: connection ended ($seen events seen)")
                                finish()
                            }
                        },
                    )
                } catch (error: Exception) {
                    diagnostics("fetch $host: could not connect (${error.javaClass.simpleName})")
                    finish()
                    return@suspendCancellableCoroutine
                }
                socketRef.set(connected)
                trySend()
                continuation.invokeOnCancellation { connected.close() }
            }
        }
        socketRef.get()?.close()
        if (!everOpened.get()) diagnostics("fetch $host: never opened")
        return synchronized(collected) { collected.toList() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 6_000L
    }
}
