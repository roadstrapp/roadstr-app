package app.roadstr.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchParityTest {
    @Test
    fun `normalization folds the same accents and punctuation`() {
        assertEquals("citta di castello", FuzzyMatch.normalize("Città  di  Castello!"))
        assertEquals("sant apollinare in classe", FuzzyMatch.normalize("Sant'Apollinare in Classe"))
        assertEquals("strasse", FuzzyMatch.normalize("Straße"))
    }

    @Test
    fun `word score preserves typo prefix and rejection thresholds`() {
        assertEquals(1.0, FuzzyMatch.wordScore("ricci", "ricci"), 0.0)
        assertTrue(FuzzyMatch.wordScore("robberto", "roberto") > 0.8)
        assertTrue(FuzzyMatch.wordScore("garib", "garibaldi") > 0.85)
        assertEquals(0.0, FuzzyMatch.wordScore("ricci", "roma"), 0.0)
    }

    @Test
    fun `address ranking handles omitted names typos and stop words`() {
        val wanted = FuzzyMatch.score("via roberto ricci", "Via Ricci")
        assertTrue(wanted > 0.5)
        assertTrue(wanted > FuzzyMatch.score("via roberto ricci", "Via Roberto Baldini"))
        assertTrue(
            FuzzyMatch.score("via robberto ricc", "Via Roberto Ricci, Torino") >
                FuzzyMatch.score("via robberto ricc", "Via Fabbri Roberto, Torino"),
        )
        assertEquals(0.0, FuzzyMatch.score("via garibaldi", "Via Napoleone, Milano"), 0.0)
        assertEquals(1.0, FuzzyMatch.score("via roberto ricci", "Via Roberto Ricci"), 0.0)
        assertEquals(0.0, FuzzyMatch.score("", "Via Roma"), 0.0)
    }
}
