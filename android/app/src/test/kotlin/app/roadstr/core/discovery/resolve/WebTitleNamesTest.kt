package app.roadstr.core.discovery.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTitleNamesTest {
    @Test
    fun `a title is cut at its separators`() {
        assertEquals(listOf("Trattoria Verde", "Menu", "Verona"), WebTitleNames.segments("Trattoria Verde - Menu | Verona"))
        assertEquals(listOf("Osteria Blu", "Recensioni"), WebTitleNames.segments("Osteria Blu · Recensioni"))
    }

    @Test
    fun `the name is the first segment that does not describe the page`() {
        assertEquals("Trattoria Verde", WebTitleNames.guess("Trattoria Verde - Menu | Verona"))
        assertEquals("Pizzeria Da Gino", WebTitleNames.guess("Menu | Pizzeria Da Gino"))
        assertNull(WebTitleNames.guess("Menu - Home"))
        assertNull(WebTitleNames.guess("Ristorante"))
    }

    @Test
    fun `a name with a generic word in it is still a name`() {
        assertEquals("Ristorante Verde", WebTitleNames.guess("Ristorante Verde - Menu"))
    }

    @Test
    fun `similarity looks at every segment`() {
        assertEquals(1.0, WebTitleNames.similarity("Menu - Trattoria Verde | Verona", "Trattoria Verde"), 0.0)
        assertTrue(WebTitleNames.similarity("Pizzeria Rossa - Menu", "Trattoria Verde") < 0.5)
        assertEquals(0.0, WebTitleNames.similarity("Trattoria Verde", ""), 0.0)
    }

    @Test
    fun `non latin titles are read too`() {
        assertEquals("寿司 さくら", WebTitleNames.guess("寿司 さくら - メニュー | 東京"))
    }
}
