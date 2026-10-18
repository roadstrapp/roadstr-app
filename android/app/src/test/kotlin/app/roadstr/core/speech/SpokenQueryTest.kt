package app.roadstr.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenQueryTest {
    @Test
    fun removesTheFullStopThatProseAddsButKeepsTheRest() {
        assertEquals("Via Giuseppe Garibaldi, Bologna", SpokenQuery.normalize("Via Giuseppe Garibaldi, Bologna."))
        assertEquals("Ristorante e pizzeria vicino a me", SpokenQuery.normalize("Ristorante e pizzeria vicino a me."))
        assertEquals("Corso Vittorio Emanuele II Torino", SpokenQuery.normalize("Corso Vittorio Emanuele II Torino"))
    }

    @Test
    fun stripsQuotesAndCollapsesWhitespace() {
        assertEquals("Piazza del Duomo", SpokenQuery.normalize("  “Piazza   del\nDuomo”?! ".replace('“', '"').replace('”', '"')))
        assertEquals("a b", SpokenQuery.normalize("a\t b …"))
    }

    @Test
    fun keepsInnerHyphensAndApostrophes() {
        assertEquals("Sant'Antonio-Abate", SpokenQuery.normalize("Sant'Antonio-Abate."))
    }

    @Test
    fun anythingThatIsOnlyPunctuationBecomesEmpty() {
        assertEquals("", SpokenQuery.normalize(" . ! "))
        assertEquals("", SpokenQuery.normalize(""))
    }
}
