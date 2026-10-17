package app.roadstr.core.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {
    @Test
    fun `all seventeen thresholds unlock one meter before exact and above correctly`() {
        assertEquals(17, AchievementCatalog.achievements.size)
        for (achievement in AchievementCatalog.achievements) {
            assertFalse(
                achievement.name,
                AchievementCatalog.states(achievement.thresholdMeters - 1L, emptyList())
                    .first { it.definition.id == achievement.id }.unlocked,
            )
            val exact = AchievementCatalog.reconcile(
                achievement.thresholdMeters,
                emptyList(),
                unlockedAtEpochMillis = 123L,
            )
            assertTrue(exact.any { it.achievementId == achievement.id })
            val above = AchievementCatalog.reconcile(
                achievement.thresholdMeters + 1L,
                emptyList(),
                unlockedAtEpochMillis = 124L,
            )
            assertTrue(above.any { it.achievementId == achievement.id })
        }
    }

    @Test
    fun `one large session unlocks every intermediate badge once`() {
        val unlocks = AchievementCatalog.reconcile(100_000_000L, emptyList(), 456L)
        assertEquals(16, unlocks.size)
        assertEquals((1..16).toList(), AchievementCatalog.states(100_000_000L, unlocks)
            .filter { it.unlocked }.map { it.definition.level })
        val again = AchievementCatalog.reconcile(100_000_000L, unlocks, 999L)
        assertEquals(unlocks, again)
        assertNull(AchievementCatalog.progress(1_000_000_000L).next)
    }

    @Test
    fun `correction removes unlocks above corrected total`() {
        val all = AchievementCatalog.reconcile(1_000_000_000L, emptyList(), 100L)
        val corrected = AchievementCatalog.reconcile(999L, all, 200L)
        assertTrue(corrected.isEmpty())
    }
}

