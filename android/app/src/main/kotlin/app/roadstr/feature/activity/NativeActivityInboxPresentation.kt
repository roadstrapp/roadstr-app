package app.roadstr.feature.activity

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeActivityNotificationType(val wireName: String) {
    Zap("zap"),
    Confirmed("confirmed"),
    Denied("denied"),
}

data class NativeActivityNotification(
    val id: String,
    val type: NativeActivityNotificationType,
    val createdAtSeconds: Long,
    val isRead: Boolean = false,
    val amountSat: Long? = null,
    val category: RoadCategoryWire? = null,
)

data class NativeActivityInboxWrite(
    val storageKey: String,
    val normalizedValue: String,
    val items: List<NativeActivityNotification>,
)

enum class NativeActivityCursorKind(val wireName: String) {
    Zap("zap"),
    Confirmation("confirmation"),
}

data class NativeActivityCursorWrite(
    val storageKey: String,
    val value: Long,
)

/**
 * Bounded mirror of ActivityNotification and ActivityNotificationService.
 *
 * Values use the normalized JSON list-of-maps produced by the legacy Hive
 * migration bridge. This object owns no Hive, filesystem, relay or Android
 * notification adapter.
 */
object NativeActivityInboxProtocol {
    const val MAX_ENTRIES = 100
    const val MAX_NORMALIZED_BYTES = 512 * 1024
    const val MAX_EVENT_ID_CHARS = 64
    const val MAX_TIMESTAMP_SECONDS = 253_402_300_799L
    const val MAX_AMOUNT_SAT = 2_100_000_000_000_000L

    private const val INBOX_PREFIX = "activity_inbox_"
    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    fun storageKey(pubkey: String): String {
        requirePubkey(pubkey)
        return INBOX_PREFIX + pubkey
    }

    fun decodeNormalized(raw: String?): List<NativeActivityNotification> {
        if (raw == null || raw.toByteArray(Charsets.UTF_8).size > MAX_NORMALIZED_BYTES) {
            return emptyList()
        }
        val values = try {
            BoundedJsonParser(raw).parse() as? List<*>
        } catch (_: RuntimeException) {
            null
        } ?: return emptyList()

        val decoded = ArrayList<NativeActivityNotification>(minOf(values.size, MAX_ENTRIES))
        for (value in values) {
            if (decoded.size == MAX_ENTRIES) break
            val item = (value as? Map<*, *>)?.let(::decodeItem) ?: continue
            decoded += item
        }
        return immutable(decoded.sortedByDescending(NativeActivityNotification::createdAtSeconds))
    }

    fun encodeNormalized(items: Iterable<NativeActivityNotification>): String = NostrJson.encode(
        items.mapNotNull(::normalize).take(MAX_ENTRIES).map(::encodedItem),
    )

    fun record(
        pubkey: String,
        current: Iterable<NativeActivityNotification>,
        notification: NativeActivityNotification,
    ): NativeActivityInboxWrite? {
        requirePubkey(pubkey)
        val normalized = normalize(notification) ?: return null
        val existing = current.mapNotNull(::normalize)
            .take(MAX_ENTRIES)
            .toMutableList()
        if (existing.any { it.id == normalized.id }) return null
        existing.add(0, normalized)
        if (existing.size > MAX_ENTRIES) {
            existing.subList(MAX_ENTRIES, existing.size).clear()
        }
        return write(pubkey, existing)
    }

    fun markAllRead(
        pubkey: String,
        current: Iterable<NativeActivityNotification>,
    ): NativeActivityInboxWrite? {
        requirePubkey(pubkey)
        val normalized = current.mapNotNull(::normalize).take(MAX_ENTRIES)
        if (normalized.none { !it.isRead }) return null
        return write(pubkey, normalized.map { it.copy(isRead = true) })
    }

    fun unreadCount(items: Iterable<NativeActivityNotification>): Int =
        items.count { !it.isRead }.coerceAtMost(MAX_ENTRIES)

    private fun write(
        pubkey: String,
        items: List<NativeActivityNotification>,
    ): NativeActivityInboxWrite {
        val visible = immutable(items.sortedByDescending(NativeActivityNotification::createdAtSeconds))
        return NativeActivityInboxWrite(
            storageKey = storageKey(pubkey),
            normalizedValue = encodeNormalized(items),
            items = visible,
        )
    }

    private fun decodeItem(value: Map<*, *>): NativeActivityNotification? {
        val id = value["id"] as? String ?: return null
        val typeName = value["type"] as? String ?: return null
        val createdAt = value["createdAt"] as? Long ?: return null
        val type = NativeActivityNotificationType.entries.firstOrNull {
            it.wireName == typeName
        } ?: return null
        return normalize(
            NativeActivityNotification(
                id = id,
                type = type,
                createdAtSeconds = createdAt,
                isRead = value["isRead"] == true,
                amountSat = (value["amountSat"] as? Long).takeIf {
                    type == NativeActivityNotificationType.Zap
                },
                category = (value["category"] as? String)?.let(::category),
            ),
        )
    }

    private fun normalize(value: NativeActivityNotification): NativeActivityNotification? {
        if (value.id.length != MAX_EVENT_ID_CHARS || !HEX_64.matches(value.id)) return null
        if (value.createdAtSeconds !in 0..MAX_TIMESTAMP_SECONDS) return null
        return when (value.type) {
            NativeActivityNotificationType.Zap -> {
                val amount = value.amountSat ?: return null
                if (amount !in 0..MAX_AMOUNT_SAT) return null
                value.copy(amountSat = amount, category = null)
            }

            NativeActivityNotificationType.Confirmed,
            NativeActivityNotificationType.Denied,
            -> value.copy(amountSat = null, category = value.category ?: return null)
        }
    }

    private fun encodedItem(value: NativeActivityNotification): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "id" to value.id,
            "type" to value.type.wireName,
            "createdAt" to value.createdAtSeconds,
            "isRead" to value.isRead,
        ).apply {
            value.amountSat?.let { put("amountSat", it) }
            value.category?.let { put("category", it.wireKey) }
        }

    private fun category(wireName: String): RoadCategoryWire =
        RoadCategoryWire.entries.firstOrNull { it.wireKey == wireName }
            ?: RoadCategoryWire.OTHER

    private fun requirePubkey(pubkey: String) {
        require(HEX_64.matches(pubkey)) { "Activity inbox pubkey must be lowercase hex" }
    }

    private fun <T> immutable(values: List<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}

/** Exact per-identity cursor names and first-activation/monotonic update policy. */
object NativeActivityCursorProtocol {
    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    fun storageKey(kind: NativeActivityCursorKind, pubkey: String): String {
        require(HEX_64.matches(pubkey)) { "Activity cursor pubkey must be lowercase hex" }
        return "activity_${kind.wireName}_cursor_$pubkey"
    }

    fun seed(raw: String?, nowSeconds: Long): Long {
        require(nowSeconds in 0..NativeActivityInboxProtocol.MAX_TIMESTAMP_SECONDS) {
            "Activity cursor time is invalid"
        }
        return raw?.toLongOrNull()
            ?.takeIf { it in 0..NativeActivityInboxProtocol.MAX_TIMESTAMP_SECONDS }
            ?: nowSeconds
    }

    fun advance(
        kind: NativeActivityCursorKind,
        pubkey: String,
        currentSeconds: Long,
        eventSeconds: Long,
    ): NativeActivityCursorWrite? {
        storageKey(kind, pubkey)
        if (currentSeconds !in 0..NativeActivityInboxProtocol.MAX_TIMESTAMP_SECONDS) return null
        if (eventSeconds !in 0..NativeActivityInboxProtocol.MAX_TIMESTAMP_SECONDS) return null
        if (eventSeconds <= currentSeconds) return null
        return NativeActivityCursorWrite(storageKey(kind, pubkey), eventSeconds)
    }
}

enum class NativeActivityInboxStatus { Hidden, LoggedOut, Empty, Ready }

data class NativeActivityInboxSnapshot(
    val revision: Long,
    val status: NativeActivityInboxStatus,
    val pubkey: String?,
    val items: List<NativeActivityNotification>,
    val unreadCount: Int,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden(revision: Long = NO_REVISION) = NativeActivityInboxSnapshot(
            revision = revision,
            status = NativeActivityInboxStatus.Hidden,
            pubkey = null,
            items = emptyList(),
            unreadCount = 0,
        )
    }
}

/** Revision-fenced UI state with no Hive, relay, socket or notification owner. */
class NativeActivityInboxSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeActivityInboxSnapshot.hidden())
    private var revision = NativeActivityInboxSnapshot.NO_REVISION

    val state: StateFlow<NativeActivityInboxSnapshot> = _state.asStateFlow()

    fun showLoggedOut(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Activity revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        _state.value = NativeActivityInboxSnapshot(
            revision,
            NativeActivityInboxStatus.LoggedOut,
            null,
            emptyList(),
            0,
        )
        true
    }

    fun show(revision: Long, pubkey: String, normalizedLegacyValue: String?): Boolean =
        synchronized(lock) {
            require(revision >= 0) { "Activity revision must be non-negative" }
            if (revision <= this.revision) return false
            val items = NativeActivityInboxProtocol.decodeNormalized(normalizedLegacyValue)
            NativeActivityInboxProtocol.storageKey(pubkey)
            this.revision = revision
            _state.value = snapshot(revision, pubkey, items)
            true
        }

    fun record(
        revision: Long,
        pubkey: String,
        notification: NativeActivityNotification,
    ): NativeActivityInboxWrite? = synchronized(lock) {
        if (!active(revision, pubkey)) return null
        val write = NativeActivityInboxProtocol.record(pubkey, _state.value.items, notification)
            ?: return null
        _state.value = snapshot(revision, pubkey, write.items)
        write
    }

    fun markAllRead(revision: Long): NativeActivityInboxWrite? = synchronized(lock) {
        val pubkey = _state.value.pubkey ?: return null
        if (!active(revision, pubkey)) return null
        val write = NativeActivityInboxProtocol.markAllRead(pubkey, _state.value.items)
            ?: return null
        _state.value = snapshot(revision, pubkey, write.items)
        write
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeActivityInboxStatus.Hidden) {
            return false
        }
        _state.value = NativeActivityInboxSnapshot.hidden(revision)
        true
    }

    private fun active(value: Long, pubkey: String): Boolean =
        value == revision && _state.value.pubkey == pubkey &&
            _state.value.status in setOf(
                NativeActivityInboxStatus.Empty,
                NativeActivityInboxStatus.Ready,
            )

    private fun snapshot(
        revision: Long,
        pubkey: String,
        items: List<NativeActivityNotification>,
    ) = NativeActivityInboxSnapshot(
        revision = revision,
        status = if (items.isEmpty()) {
            NativeActivityInboxStatus.Empty
        } else {
            NativeActivityInboxStatus.Ready
        },
        pubkey = pubkey,
        items = items,
        unreadCount = NativeActivityInboxProtocol.unreadCount(items),
    )
}
