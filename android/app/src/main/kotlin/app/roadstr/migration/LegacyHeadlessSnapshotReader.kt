package app.roadstr.migration

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed interface LegacyEnvelopeTransportResult {
    class Available(envelope: ByteArray) : LegacyEnvelopeTransportResult {
        val envelope: ByteArray = envelope.copyOf()
    }

    data object NotNeeded : LegacyEnvelopeTransportResult
    data object Failed : LegacyEnvelopeTransportResult
}

interface LegacyEnvelopeTransport : AutoCloseable {
    fun request(callback: (LegacyEnvelopeTransportResult) -> Unit)
}

fun interface LegacyEnvelopeTransportFactory {
    fun create(): LegacyEnvelopeTransport
}

class LegacyHeadlessBridgeException(message: String) : IllegalStateException(message)

/**
 * Adapts the asynchronous headless-engine transport to [LegacySnapshotReader].
 *
 * The transactional migration API is intentionally synchronous, so callers
 * must run this reader on a worker thread. Every transport/codec failure is
 * reduced to a bounded, value-free exception and the transport is always
 * closed before decoding or returning.
 */
class LegacyHeadlessSnapshotReader(
    private val transportFactory: LegacyEnvelopeTransportFactory,
    private val timeoutMillis: Long,
    private val isMainThread: () -> Boolean,
) : LegacySnapshotReader {
    init {
        require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS) {
            "Invalid legacy bridge timeout"
        }
    }

    override fun read(): LegacyStorageSnapshot? {
        if (isMainThread()) {
            throw LegacyHeadlessBridgeException(
                "Legacy bridge must run off the main thread",
            )
        }

        val transport = try {
            transportFactory.create()
        } catch (_: RuntimeException) {
            throw LegacyHeadlessBridgeException("Legacy bridge could not start")
        }
        val response = AtomicReference<LegacyEnvelopeTransportResult?>()
        val completed = CountDownLatch(1)

        val reply = try {
            try {
                transport.request { candidate ->
                    if (response.compareAndSet(null, candidate)) {
                        completed.countDown()
                    }
                }
            } catch (_: RuntimeException) {
                throw LegacyHeadlessBridgeException("Legacy bridge request failed")
            }

            val arrived = try {
                completed.await(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw LegacyHeadlessBridgeException("Legacy bridge wait was interrupted")
            }
            if (!arrived) {
                throw LegacyHeadlessBridgeException("Legacy bridge timed out")
            }
            response.get()
                ?: throw LegacyHeadlessBridgeException("Legacy bridge returned no result")
        } finally {
            try {
                transport.close()
            } catch (_: RuntimeException) {
                // The Android transport disposes the engine before completing
                // its callback. close() is an idempotent timeout safeguard.
            }
        }

        return when (reply) {
            LegacyEnvelopeTransportResult.NotNeeded -> null
            LegacyEnvelopeTransportResult.Failed -> {
                throw LegacyHeadlessBridgeException("Legacy bridge failed")
            }
            is LegacyEnvelopeTransportResult.Available -> decode(reply.envelope)
        }
    }

    private fun decode(envelope: ByteArray): LegacyStorageSnapshot {
        if (envelope.size > LegacySnapshotLimits.MAX_ENVELOPE_BYTES) {
            throw LegacyHeadlessBridgeException("Legacy bridge returned invalid data")
        }
        return try {
            LegacySnapshotEnvelope.decode(envelope)
        } catch (_: RuntimeException) {
            throw LegacyHeadlessBridgeException("Legacy bridge returned invalid data")
        }
    }

    private companion object {
        const val MAX_TIMEOUT_MILLIS = 120_000L
    }
}
