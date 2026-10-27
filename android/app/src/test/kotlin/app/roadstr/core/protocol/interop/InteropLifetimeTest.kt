package app.roadstr.core.protocol.interop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteropLifetimeTest {
    private val created = 1_700_000_000L
    private val police = CompactRoadType.POLICE // 4 h
    private fun vote(key: String, at: Long, still: Boolean) = InteropVote(key.padEnd(64, '0'), at, still)

    @Test
    fun `without votes a report lives for the type's own lifetime, not the 14 day relay time`() {
        assertEquals(created + 14_400, InteropLifetime.effectiveExpiry(created, police, emptyList()))
        assertEquals(created + 2_592_000, InteropLifetime.effectiveExpiry(created, CompactRoadType.SPEED_CAMERA, emptyList()))
    }

    @Test
    fun `a confirmation pushes the expiry forward from its own time`() {
        val votes = listOf(vote("a", created + 3_600, true))
        assertEquals(created + 3_600 + 14_400, InteropLifetime.effectiveExpiry(created, police, votes))
    }

    @Test
    fun `an early confirmation never shortens the lifetime`() {
        val votes = listOf(vote("a", created + 1, true))
        assertEquals(created + 1 + 14_400, InteropLifetime.effectiveExpiry(created, police, votes))
    }

    @Test
    fun `a denial ends the report at the time of the denial`() {
        val votes = listOf(vote("a", created + 600, false))
        assertEquals(created + 600, InteropLifetime.effectiveExpiry(created, police, votes))
    }

    @Test
    fun `a later confirmation revives a denied report`() {
        val votes = listOf(vote("a", created + 600, false), vote("b", created + 900, true))
        assertEquals(created + 900 + 14_400, InteropLifetime.effectiveExpiry(created, police, votes))
    }

    @Test
    fun `a tie keeps the report alive`() {
        val votes = listOf(vote("a", created + 600, false), vote("b", created + 600, true))
        assertEquals(created + 600 + 14_400, InteropLifetime.effectiveExpiry(created, police, votes))
        assertFalse(InteropLifetime.deniedByRecentMajority(votes, created + 610))
    }

    @Test
    fun `order of arrival does not change the result`() {
        val votes = listOf(vote("a", created + 600, false), vote("b", created + 700, true), vote("c", created + 650, false))
        val forward = InteropLifetime.effectiveExpiry(created, police, votes)
        assertEquals(forward, InteropLifetime.effectiveExpiry(created, police, votes.reversed()))
    }

    @Test
    fun `a key counts once and its latest vote wins`() {
        val votes = listOf(vote("a", created + 100, false), vote("a", created + 200, true), vote("a", created + 150, false))
        val admitted = InteropLifetime.admissible(votes, created, created + 1_000)
        assertEquals(1, admitted.size)
        assertEquals(created + 200, admitted.single().createdAt)
        assertTrue(admitted.single().stillThere)
    }

    @Test
    fun `votes that predate the report or lie in the future are not admitted`() {
        val now = created + 1_000
        val votes = listOf(vote("a", created - 1, false), vote("b", now + 301, false), vote("c", now + 300, true))
        val admitted = InteropLifetime.admissible(votes, created, now)
        assertEquals(listOf("c".padEnd(64, '0')), admitted.map { it.pubkey })
    }

    @Test
    fun `a recent majority of denials hides the report even if its lifetime has not run out`() {
        val now = created + 1_000
        val votes = listOf(vote("a", now - 60, false), vote("b", now - 30, false), vote("c", now - 20, true))
        assertTrue(InteropLifetime.deniedByRecentMajority(votes, now))
        // The same denials older than five minutes no longer count as a contest.
        assertFalse(InteropLifetime.deniedByRecentMajority(votes, now + 400))
    }

    @Test
    fun `visibility combines type lifetime, votes and the event's own expiration`() {
        val expiration = CompactRoadCodec.expirationFor(created, police)
        assertTrue(InteropLifetime.isVisible(created, police, emptyList(), created + 7_199, expiration))
        assertFalse(InteropLifetime.isVisible(created, police, emptyList(), created + 14_400, expiration))
        // Kept alive by confirmations, but never past what relays serve.
        val keepAlive = (1..400).map { vote("k$it", created + it * 3_000L, true) }
        assertTrue(InteropLifetime.isVisible(created, police, keepAlive, created + 1_000_000, expiration))
        assertFalse(InteropLifetime.isVisible(created, police, keepAlive, expiration, expiration))
    }

    @Test
    fun `a speed camera is shown for its 30 days, not cut off at day 14`() {
        val camera = CompactRoadType.SPEED_CAMERA
        val expiration = CompactRoadCodec.expirationFor(created, camera)
        assertEquals(created + 30 * 86_400L, expiration)
        assertTrue(InteropLifetime.isVisible(created, camera, emptyList(), created + 20 * 86_400L, expiration))
        assertFalse(InteropLifetime.isVisible(created, camera, emptyList(), created + 30 * 86_400L, expiration))
    }

    @Test
    fun `an expiration tag shorter than the lifetime ends the report early`() {
        // Older clients wrote created_at + lifetime; an event may also carry less.
        assertFalse(InteropLifetime.isVisible(created, police, emptyList(), created + 3_600, created + 3_600))
        assertEquals(created + 3_600, InteropLifetime.effectiveExpiry(created, police, emptyList(), created + 3_600))
    }
}
