package app.roadstr.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItalianNumbersTest {
    private fun word(text: String) = ItalianNumbers.parseWord(text)

    @Test
    fun readsTheNumbersUnderTwenty() {
        val expected = listOf(
            "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci", "undici", "dodici",
            "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove",
        )
        expected.forEachIndexed { index, text -> assertEquals(text, index + 1, word(text)) }
        assertEquals(0, word("zero"))
    }

    @Test
    fun readsTheTensWithTheirElisionsAndAccents() {
        assertEquals(20, word("venti"))
        assertEquals(21, word("ventuno"))
        assertEquals(28, word("ventotto"))
        assertEquals(23, word("ventitré"))
        assertEquals(33, word("trentatré"))
        assertEquals(38, word("trentotto"))
        assertEquals(64, word("sessantaquattro"))
        assertEquals(99, word("novantanove"))
        assertEquals(90, word("novanta"))
    }

    @Test
    fun readsHundredsAndThousandsGluedTogether() {
        assertEquals(100, word("cento"))
        assertEquals(101, word("centouno"))
        assertEquals(101, word("centuno"))
        assertEquals(108, word("centotto"))
        assertEquals(180, word("centottanta"))
        assertEquals(123, word("centoventitré"))
        assertEquals(247, word("duecentoquarantasette"))
        assertEquals(999, word("novecentonovantanove"))
        assertEquals(1000, word("mille"))
        assertEquals(1200, word("milleduecento"))
        assertEquals(2015, word("duemilaquindici"))
        assertEquals(3000, word("tremila"))
        assertEquals(20000, word("ventimila"))
    }

    @Test
    fun ignoresCaseAndRefusesWhatIsNotANumber() {
        assertEquals(17, word("Diciassette"))
        assertNull(word("pizzeria"))
        assertNull(word(""))
        assertNull(word("tretre"))
        assertNull(word("centocento"))
        assertNull(word("unmila"))
        assertNull(word("dieciuno"))
        assertNull(word("17"))
    }

    @Test
    fun joinsNumbersSplitOverSeveralWords() {
        assertEquals(247 to 3, ItalianNumbers.parseRun(listOf("duecento", "quaranta", "sette"), 0))
        assertEquals(21 to 2, ItalianNumbers.parseRun(listOf("venti", "uno"), 0))
        assertEquals(1200 to 2, ItalianNumbers.parseRun(listOf("mille", "duecento"), 0))
        assertEquals(1234 to 4, ItalianNumbers.parseRun(listOf("mille", "duecento", "trenta", "quattro"), 0))
    }

    @Test
    fun twoNumbersInARowStayTwoNumbers() {
        assertEquals(17 to 1, ItalianNumbers.parseRun(listOf("diciassette", "venti"), 0))
        assertEquals(100 to 1, ItalianNumbers.parseRun(listOf("cento", "cento"), 0))
        assertEquals(3 to 1, ItalianNumbers.parseRun(listOf("tre", "bis"), 0))
        assertEquals(5 to 1, ItalianNumbers.parseRun(listOf("via", "cinque"), 1))
        assertNull(ItalianNumbers.parseRun(listOf("via", "cinque"), 0))
    }

    @Test
    fun marksTheWordsThatAreAlsoEverydayItalian() {
        assertEquals(setOf("un", "uno", "una", "sei"), ItalianNumbers.ambiguousWords)
    }
}
