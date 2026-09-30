package app.roadstr.feature.activity

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeActivityInboxPresentationTest {
    @Test
    fun `per-pubkey inbox and cursor keys preserve Flutter names`() {
        assertEquals(
            "activity_inbox_$PUBKEY",
            NativeActivityInboxProtocol.storageKey(PUBKEY),
        )
        assertEquals(
            "activity_zap_cursor_$PUBKEY",
            NativeActivityCursorProtocol.storageKey(NativeActivityCursorKind.Zap, PUBKEY),
        )
        assertEquals(
            "activity_confirmation_cursor_$PUBKEY",
            NativeActivityCursorProtocol.storageKey(
                NativeActivityCursorKind.Confirmation,
                PUBKEY,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeActivityInboxProtocol.storageKey("A".repeat(64))
        }
    }

    @Test
    fun `normalized legacy maps round trip all three activity types`() {
        val values = listOf(
            zap(1, amountSat = 21, isRead = true),
            reaction(2, NativeActivityNotificationType.Confirmed, RoadCategoryWire.ROAD_CLOSURE),
            reaction(3, NativeActivityNotificationType.Denied, RoadCategoryWire.FOG),
        )

        val encoded = NativeActivityInboxProtocol.encodeNormalized(values)
        val decoded = NativeActivityInboxProtocol.decodeNormalized(encoded)

        assertTrue(encoded.contains("\"amountSat\":21"))
        assertTrue(encoded.contains("\"category\":\"road_closure\""))
        assertEquals(values.reversed(), decoded)
    }

    @Test
    fun `unknown stored category follows Flutter other fallback`() {
        val raw = """[{"id":"${id(1)}","type":"confirmed","createdAt":1,"isRead":false,"category":"future_kind"}]"""

        val decoded = NativeActivityInboxProtocol.decodeNormalized(raw)

        assertEquals(RoadCategoryWire.OTHER, decoded.single().category)
    }

    @Test
    fun `decoder skips malformed identity type timestamp and payload rows`() {
        val raw = """[
            {"id":"bad","type":"zap","createdAt":1,"amountSat":1},
            {"id":"${id(2)}","type":"future","createdAt":2},
            {"id":"${id(3)}","type":"zap","createdAt":1.5,"amountSat":1},
            {"id":"${id(4)}","type":"zap","createdAt":4},
            {"id":"${id(5)}","type":"confirmed","createdAt":5},
            {"id":"${id(6)}","type":"zap","createdAt":6,"amountSat":7}
        ]""".trimIndent()

        val decoded = NativeActivityInboxProtocol.decodeNormalized(raw)

        assertEquals(listOf(id(6)), decoded.map(NativeActivityNotification::id))
        assertTrue(NativeActivityInboxProtocol.decodeNormalized("not-json").isEmpty())
        assertTrue(
            NativeActivityInboxProtocol.decodeNormalized(
                "x".repeat(NativeActivityInboxProtocol.MAX_NORMALIZED_BYTES + 1),
            ).isEmpty(),
        )
    }

    @Test
    fun `decoder sorts newest first and caps work at one hundred valid rows`() {
        val raw = NativeActivityInboxProtocol.encodeNormalized(
            List(NativeActivityInboxProtocol.MAX_ENTRIES + 20) { index ->
                zap(index + 1, createdAt = (index + 1).toLong())
            },
        )

        val decoded = NativeActivityInboxProtocol.decodeNormalized(raw)

        assertEquals(NativeActivityInboxProtocol.MAX_ENTRIES, decoded.size)
        assertEquals(100, decoded.first().createdAtSeconds)
        assertEquals(1, decoded.last().createdAtSeconds)
    }

    @Test
    fun `record prepends deduplicates and trims the oldest storage tail`() {
        val current = List(NativeActivityInboxProtocol.MAX_ENTRIES) { index ->
            zap(index + 1, createdAt = (200 - index).toLong())
        }
        assertNull(NativeActivityInboxProtocol.record(PUBKEY, current, current[20]))

        val write = NativeActivityInboxProtocol.record(
            PUBKEY,
            current,
            zap(500, createdAt = 500),
        )!!

        assertEquals("activity_inbox_$PUBKEY", write.storageKey)
        assertEquals(NativeActivityInboxProtocol.MAX_ENTRIES, write.items.size)
        assertEquals(id(500), write.items.first().id)
        assertFalse(write.normalizedValue.contains(id(100)))
    }

    @Test
    fun `mark all read preserves rows and is idempotent`() {
        val current = listOf(zap(1), zap(2, isRead = true))

        val write = NativeActivityInboxProtocol.markAllRead(PUBKEY, current)!!

        assertTrue(write.items.all(NativeActivityNotification::isRead))
        assertEquals(0, NativeActivityInboxProtocol.unreadCount(write.items))
        assertNull(NativeActivityInboxProtocol.markAllRead(PUBKEY, write.items))
    }

    @Test
    fun `decoded rows are immutable`() {
        val decoded = NativeActivityInboxProtocol.decodeNormalized(
            NativeActivityInboxProtocol.encodeNormalized(listOf(zap(1))),
        )

        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (decoded as MutableList<NativeActivityNotification>).clear()
        }
    }

    @Test
    fun `first activation seeds missing or malformed cursors at now`() {
        assertEquals(100, NativeActivityCursorProtocol.seed(null, 100))
        assertEquals(100, NativeActivityCursorProtocol.seed("bad", 100))
        assertEquals(90, NativeActivityCursorProtocol.seed("90", 100))
        assertThrows(IllegalArgumentException::class.java) {
            NativeActivityCursorProtocol.seed(null, -1)
        }
    }

    @Test
    fun `cursor updates only advance monotonically`() {
        assertNull(
            NativeActivityCursorProtocol.advance(
                NativeActivityCursorKind.Zap,
                PUBKEY,
                100,
                100,
            ),
        )
        val write = NativeActivityCursorProtocol.advance(
            NativeActivityCursorKind.Confirmation,
            PUBKEY,
            100,
            101,
        )!!

        assertEquals("activity_confirmation_cursor_$PUBKEY", write.storageKey)
        assertEquals(101, write.value)
    }

    @Test
    fun `session exposes logged-out and empty states with revision fencing`() {
        val session = NativeActivityInboxSession()
        assertTrue(session.showLoggedOut(1))
        assertEquals(NativeActivityInboxStatus.LoggedOut, session.state.value.status)
        assertFalse(session.showLoggedOut(1))

        assertTrue(session.show(2, PUBKEY, "[]"))
        assertEquals(NativeActivityInboxStatus.Empty, session.state.value.status)
        assertFalse(session.show(1, PUBKEY, "[]"))
    }

    @Test
    fun `session shows sorted unread state and returns persistence writes`() {
        val session = NativeActivityInboxSession()
        val raw = NativeActivityInboxProtocol.encodeNormalized(listOf(zap(1), zap(2)))
        assertTrue(session.show(3, PUBKEY, raw))
        assertEquals(NativeActivityInboxStatus.Ready, session.state.value.status)
        assertEquals(2, session.state.value.unreadCount)

        val write = session.record(3, PUBKEY, zap(4, createdAt = 4))!!

        assertEquals(id(4), write.items.first().id)
        assertEquals(3, session.state.value.unreadCount)
        assertNull(session.record(2, PUBKEY, zap(5)))
    }

    @Test
    fun `session mark-read and hide reject late callbacks`() {
        val session = NativeActivityInboxSession()
        session.show(
            7,
            PUBKEY,
            NativeActivityInboxProtocol.encodeNormalized(listOf(zap(1))),
        )

        assertNull(session.markAllRead(6))
        assertTrue(session.markAllRead(7)!!.items.single().isRead)
        assertNull(session.markAllRead(7))
        assertFalse(session.hide(6))
        assertTrue(session.hide(7))
        assertNull(session.record(7, PUBKEY, zap(2)))
    }

    @Test
    fun `timestamp formatting uses the requested locale and device timezone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val formatted = formatActivityTimestamp(1_700_000_000L, Locale.US)
            assertTrue(formatted.contains("2023"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun zap(
        index: Int,
        amountSat: Long = 21,
        createdAt: Long = index.toLong(),
        isRead: Boolean = false,
    ) = NativeActivityNotification(
        id = id(index),
        type = NativeActivityNotificationType.Zap,
        createdAtSeconds = createdAt,
        isRead = isRead,
        amountSat = amountSat,
    )

    private fun reaction(
        index: Int,
        type: NativeActivityNotificationType,
        category: RoadCategoryWire,
    ) = NativeActivityNotification(
        id = id(index),
        type = type,
        createdAtSeconds = index.toLong(),
        category = category,
    )

    private fun id(value: Int): String = value.toString(16).padStart(64, '0')

    private companion object {
        val PUBKEY = "a".repeat(64)
    }
}
