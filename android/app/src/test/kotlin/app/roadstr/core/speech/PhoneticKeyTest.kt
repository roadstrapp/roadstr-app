package app.roadstr.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticKeyTest {
    private fun distance(a: String, b: String) = SpokenAddressRefiner.boundedDistance(PhoneticKey.of(a), PhoneticKey.of(b), 9)

    @Test
    fun wordBoundariesApostrophesAndAccentsDoNotMatter() {
        assertEquals(PhoneticKey.of("Via Chiavica Romea"), PhoneticKey.of("via chiavi caromea"))
        assertEquals(PhoneticKey.of("Via Darsena"), PhoneticKey.of("via d'arsena"))
        assertEquals(PhoneticKey.of("Via Darsena"), PhoneticKey.of("Via D’Arsena"))
        assertEquals(PhoneticKey.of("Via Sant'Alberto"), PhoneticKey.of("via santalberto"))
        assertEquals(PhoneticKey.of("Perché"), PhoneticKey.of("perche"))
    }

    @Test
    fun spellingsThatSoundAlikeMeet() {
        assertEquals(PhoneticKey.of("Chiesa"), PhoneticKey.of("Kiesa"))
        assertEquals(PhoneticKey.of("Via Cavour"), PhoneticKey.of("via kavour"))
        assertEquals(PhoneticKey.of("Pizza"), PhoneticKey.of("Piza"))
        assertEquals(PhoneticKey.of("Bellini"), PhoneticKey.of("Belini"))
        assertEquals(PhoneticKey.of("Ghirlandaio"), PhoneticKey.of("Girlandaio"))
    }

    @Test
    fun aMisheardLetterIsAtMostOneEditAway() {
        assertEquals(1, distance("via d'arsena", "via d'assena") - 0)
        assertTrue(distance("Via Cavour", "via kawaur") <= 1)
    }

    @Test
    fun digitsSurviveAndNamesStayDifferent() {
        assertEquals("via20setembre", PhoneticKey.of("Via 20 Settembre"))
        assertNotEquals(PhoneticKey.of("Via Roma"), PhoneticKey.of("Via Rosa"))
        assertNotEquals(PhoneticKey.of("Via 20 Settembre"), PhoneticKey.of("Via 25 Settembre"))
    }

    @Test
    fun compactKeepsOnlyLettersAndDigits() {
        assertEquals("viadarsena17", PhoneticKey.compact("Via D'Arsena, 17!"))
    }
}
