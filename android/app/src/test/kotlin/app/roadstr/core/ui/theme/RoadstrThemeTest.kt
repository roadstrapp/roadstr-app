package app.roadstr.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadstrThemeTest {
    @Test
    fun `stored ordinals preserve all current and legacy theme aliases`() {
        assertEquals(RoadstrThemeId.LightNostr, RoadstrThemeId.fromStoredOrdinal(0))
        assertEquals(RoadstrThemeId.LightBitcoin, RoadstrThemeId.fromStoredOrdinal(1))
        assertEquals(RoadstrThemeId.DarkNostr, RoadstrThemeId.fromStoredOrdinal(2))
        assertEquals(RoadstrThemeId.DarkBitcoin, RoadstrThemeId.fromStoredOrdinal(3))
        assertEquals(RoadstrThemeId.LightNostr, RoadstrThemeId.fromStoredOrdinal(4))
        assertEquals(RoadstrThemeId.LightBitcoin, RoadstrThemeId.fromStoredOrdinal(5))
        assertEquals(RoadstrThemeId.DarkNostr, RoadstrThemeId.fromStoredOrdinal(6))
        assertEquals(RoadstrThemeId.DarkBitcoin, RoadstrThemeId.fromStoredOrdinal(7))
        assertEquals(RoadstrThemeId.LightNostr, RoadstrThemeId.fromStoredOrdinal(-1))
    }

    @Test
    fun `native palettes retain Flutter accent and light-dark surface values`() {
        val light = RoadstrThemeTokens.palette(RoadstrThemeId.LightNostr)
        val dark = RoadstrThemeTokens.palette(RoadstrThemeId.DarkBitcoin)

        assertFalse(RoadstrThemeId.LightNostr.dark)
        assertEquals(0xFF8B5CF6, light.accentArgb)
        assertEquals(0xFFF5F5F5, light.backgroundArgb)
        assertEquals(0xFFFFFFFF, light.surfaceArgb)
        assertTrue(RoadstrThemeId.DarkBitcoin.dark)
        assertEquals(0xFFF7931A, dark.accentArgb)
        assertEquals(0xFF0D0D1A, dark.backgroundArgb)
        assertEquals(0xFF1A1A2E, dark.surfaceArgb)
    }
}
