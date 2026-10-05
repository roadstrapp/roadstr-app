package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayOkMessage
import app.roadstr.core.protocol.nostr.NostrRelayWire
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends one signed event to every relay in parallel and reports whether any of
 * them accepted it.
 *
 * Each relay gets its own short-lived connection: publishing must keep working
 * while the long-lived area subscription is down, and a single relay refusing
 * or dropping the event never loses it. The relay's OK has to name this very
 * event: one answering "accepted" for another id has stored nothing of ours.
 */
class NativeRelayPublisher(
    private val connector: NativeRelayConnector,
    private val relays: List<String>,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    /** Host-level notes on how each relay answered; never event content. */
    private val diagnostics: (String) -> Unit = {},
) {
    suspend fun publish(event: Map<String, Any?>): Boolean {
        val id = event["id"] as? String ?: return false
        if (relays.isEmpty()) return false
        val frame = NostrRelayWire.encode(NostrRelayWire.publish(event))
        return coroutineScope {
            relays.map { url -> async { publishOne(url, id, frame) } }.awaitAll().any { it }
        }
    }

    private suspend fun publishOne(url: String, eventId: String, frame: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/')
        val socketRef = AtomicReference<NativeRelaySocket?>(null)
        val outcome = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                val done = AtomicBoolean(false)
                val opened = AtomicBoolean(false)
                val sent = AtomicBoolean(false)
                fun finish(value: Boolean) {
                    if (done.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(value)
                    }
                }
                // The open callback can fire before connect() has returned the
                // socket, and the socket can be returned before the open
                // callback: send only when both have happened, exactly once.
                fun trySend() {
                    val socket = socketRef.get() ?: return
                    if (opened.get() && sent.compareAndSet(false, true) && !socket.send(frame)) {
                        finish(false)
                    }
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
                                val message = NostrRelayMessageDecoder.decode(text).message
                                if (message is NostrRelayOkMessage && message.eventId == eventId) {
                                    diagnostics("publish $host: OK accepted=${message.accepted} ${message.reason.toString().take(80)}")
                                    finish(message.accepted == true)
                                }
                            }

                            override fun onEnded() = finish(false)
                        },
                    )
                } catch (error: Exception) {
                    diagnostics("publish $host: could not connect (${error.javaClass.simpleName})")
                    finish(false)
                    return@suspendCancellableCoroutine
                }
                socketRef.set(connected)
                trySend()
                continuation.invokeOnCancellation { connected.close() }
            }
        } ?: false
        if (!outcome) diagnostics("publish $host: not accepted")
        socketRef.get()?.close()
        return outcome
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 8_000L
    }
}
