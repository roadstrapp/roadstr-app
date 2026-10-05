package app.roadstr.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `latin accents and case fold away`() {
        assertEquals("citta e perche", TextNormalizer.normalize("Città è Perché"))
        assertEquals("strasse oeuvre", TextNormalizer.normalize("Straße ŒUVRE"))
        assertEquals("lodz hejnal", TextNormalizer.normalize("Łódź Hejnał"))
    }

    @Test
    fun `greek keeps its letters, loses accents and the final sigma`() {
        assertEquals("οδοσ", TextNormalizer.normalize("ΟΔΟΣ"))
        assertEquals("οδοσ", TextNormalizer.normalize("οδός"))
        assertEquals("φαρμακειο", TextNormalizer.normalize("Φαρμακείο"))
    }

    @Test
    fun `cyrillic keeps its letters and folds io and short i`() {
        assertEquals("ресторан рядом", TextNormalizer.normalize("Ресторан Рядом"))
        assertEquals(TextNormalizer.normalize("ёлка"), TextNormalizer.normalize("елка"))
        assertEquals(TextNormalizer.normalize("район"), TextNormalizer.normalize("раион"))
    }

    @Test
    fun `japanese and chinese become one token per character`() {
        val tokens = TextNormalizer.tokenize("東京のカフェ").map { it.text }
        assertEquals(listOf("東", "京", "の", "カ", "フ", "ェ"), tokens)
        assertEquals(listOf("上", "海", "的", "餐", "厅"), TextNormalizer.tokenize("上海的餐厅").map { it.text })
    }

    @Test
    fun `voicing marks are kept so different kana stay different`() {
        assertTrue(TextNormalizer.normalize("が") != TextNormalizer.normalize("か"))
        assertEquals(TextNormalizer.normalize("ガ"), TextNormalizer.normalize("ガ"))
    }

    @Test
    fun `full width and half width forms are unified`() {
        assertEquals("atm 24", TextNormalizer.normalize("ＡＴＭ ２４"))
        assertEquals(TextNormalizer.tokenize("ﾋﾟｻﾞ").map { it.text }, TextNormalizer.tokenize("ピザ").map { it.text })
    }

    @Test
    fun `maltese h with stroke and dotted letters fold`() {
        assertEquals("hanut", TextNormalizer.normalize("ħanut"))
        assertEquals("cinema", TextNormalizer.normalize("ċinema"))
    }

    @Test
    fun `punctuation and apostrophes separate words and tokens keep their raw span`() {
        val text = "dell'acqua, bar-caffè!"
        val tokens = TextNormalizer.tokenize(text)
        assertEquals(listOf("dell", "acqua", "bar", "caffe"), tokens.map { it.text })
        assertEquals("acqua", text.substring(tokens[1].rawStart, tokens[1].rawEnd))
        assertEquals("caffè", text.substring(tokens[3].rawStart, tokens[3].rawEnd))
    }

    @Test
    fun `empty and symbol only input has no tokens`() {
        assertTrue(TextNormalizer.tokenize("").isEmpty())
        assertTrue(TextNormalizer.tokenize("  ,.!? --- ").isEmpty())
    }
}
