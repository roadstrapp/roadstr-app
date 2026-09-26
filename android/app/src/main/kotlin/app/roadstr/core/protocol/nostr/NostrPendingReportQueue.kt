package app.roadstr.core.protocol.nostr

import java.util.Collections

enum class PendingReportDisposition(val wireName: String) {
    EXPIRED("expired"),
    INVALID("invalid"),
    PUBLISHED("published"),
    RETRY("retry"),
}

class PendingRoadReport(
    event: Map<String, Any?>?,
    val expiresAt: Long?,
) {
    val event: Map<String, Any?>? = event?.let(::copyJsonObject)

    val id: String
        get() = event?.get("id") as? String ?: "<invalid>"

    fun storageJson(): String {
        val validEvent = requireNotNull(event) { "A stored pending report needs an event" }
        val expiration = requireNotNull(expiresAt) {
            "A stored pending report needs an expiration"
        }
        return NostrJson.encode(
            linkedMapOf(
                "event" to validEvent,
                "expiresAt" to expiration,
            ),
        )
    }
}

data class PendingReportDecision(
    val id: String,
    val disposition: PendingReportDisposition,
)

class PendingReportFlushResult(
    remaining: List<PendingRoadReport>,
    decisions: List<PendingReportDecision>,
    attemptedIds: List<String>,
) {
    val remaining: List<PendingRoadReport> =
        Collections.unmodifiableList(ArrayList(remaining))
    val decisions: List<PendingReportDecision> =
        Collections.unmodifiableList(ArrayList(decisions))
    val attemptedIds: List<String> =
        Collections.unmodifiableList(ArrayList(attemptedIds))
}

/**
 * Pure FIFO policy for one offline-report flush.
 *
 * A caller must persist [PendingReportFlushResult.remaining] once, only after
 * this pass returns. A crash before that final commit can retry an already
 * published Nostr ID, but cannot lose an entry through a partial queue write.
 */
object NostrPendingReportQueue {
    fun flush(
        pending: List<PendingRoadReport>,
        now: Long,
        verify: (Map<String, Any?>) -> Boolean,
        publish: (Map<String, Any?>) -> Boolean,
    ): PendingReportFlushResult {
        val remaining = mutableListOf<PendingRoadReport>()
        val decisions = mutableListOf<PendingReportDecision>()
        val attemptedIds = mutableListOf<String>()

        for (entry in pending) {
            val expiration = entry.expiresAt ?: 0L
            if (expiration <= now) {
                decisions += PendingReportDecision(
                    entry.id,
                    PendingReportDisposition.EXPIRED,
                )
                continue
            }
            val event = entry.event
            if (event == null || !verify(event)) {
                decisions += PendingReportDecision(
                    entry.id,
                    PendingReportDisposition.INVALID,
                )
                continue
            }
            attemptedIds += entry.id
            val published = try {
                publish(event)
            } catch (_: Exception) {
                false
            }
            if (published) {
                decisions += PendingReportDecision(
                    entry.id,
                    PendingReportDisposition.PUBLISHED,
                )
            } else {
                remaining += entry
                decisions += PendingReportDecision(
                    entry.id,
                    PendingReportDisposition.RETRY,
                )
            }
        }

        return PendingReportFlushResult(remaining, decisions, attemptedIds)
    }
}

private fun copyJsonObject(source: Map<String, Any?>): Map<String, Any?> {
    val copy = linkedMapOf<String, Any?>()
    for ((key, value) in source) copy[key] = copyJsonValue(value)
    return Collections.unmodifiableMap(copy)
}

private fun copyJsonValue(value: Any?): Any? = when (value) {
    is Map<*, *> -> {
        val copy = linkedMapOf<String, Any?>()
        for ((key, nested) in value) {
            require(key is String) { "JSON object keys must be strings" }
            copy[key] = copyJsonValue(nested)
        }
        Collections.unmodifiableMap(copy)
    }

    is List<*> -> Collections.unmodifiableList(value.map(::copyJsonValue))
    else -> value
}
