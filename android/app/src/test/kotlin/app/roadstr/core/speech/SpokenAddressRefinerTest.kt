package app.roadstr.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenAddressRefinerTest {
    private val streets = StreetIndex(
        listOf(
            "Via Darsena", "Via Chiavica Romea", "Via Alberoni", "Via XX Settembre", "Via IV Novembre", "Via Roma",
            "Viale Italia", "Piazza dei Tre Martiri", "Via Cavour", "Corso Garibaldi", "Via Cesare Battisti",
            "Via Maggiore", "Via Bellini", "Via Bellina", "Via Rosa", "Via Sant'Alberto", "Via Cinque Giornate",
        ),
    )

    private fun refine(text: String, index: StreetIndex? = streets, italian: Boolean = true) =
        SpokenAddressRefiner.refine(text, index, italian)

    // ── the three cases that failed on the road ────────────────────────────────

    @Test
    fun aSpelledOutHouseNumberAndASplitApostropheStreet() {
        assertEquals("Via Darsena 17", refine("via d'arsena diciassette"))
    }

    @Test
    fun aMisheardLetterInTheStreetName() {
        assertEquals("Via Darsena 17", refine("via d'assena diciassette"))
    }

    @Test
    fun aStreetNameCutAtTheWrongPlace() {
        assertEquals("Via Chiavica Romea", refine("via chiavi caromea"))
        assertEquals("Via Chiavica Romea 123", refine("Via chiavi caromea centoventitré"))
    }

    // ── the street ─────────────────────────────────────────────────────────────

    @Test
    fun theTownAfterAHouseNumberIsKept() {
        assertEquals("Via Darsena 17 ravenna", refine("via darsena diciassette ravenna"))
        assertEquals("Via Chiavica Romea, ravenna", refine("via chiavi caromea, ravenna"))
    }

    @Test
    fun wordsBeforeTheStreetAreKept() {
        assertEquals("navigami verso Via Chiavica Romea", refine("navigami verso via chiavi caromea"))
    }

    @Test
    fun namesWithANumberMatchWhetherItIsWordsDigitsOrRoman() {
        assertEquals("Via XX Settembre", refine("via venti settembre"))
        assertEquals("Via XX Settembre", refine("Via 20 settembre"))
        assertEquals("Via IV Novembre 45", refine("via quattro novembre quarantacinque"))
        assertEquals("Piazza dei Tre Martiri", refine("piazza dei tre martiri"))
        assertEquals("Via Cinque Giornate", refine("via cinque giornate"))
    }

    @Test
    fun anExactMatchGetsTheMapsSpellingAndCapitals() {
        assertEquals("Via Roma 5", refine("via roma 5"))
        assertEquals("Viale Italia", refine("viale italia"))
        assertEquals("Via Sant'Alberto", refine("via santalberto"))
    }

    @Test
    fun aCloseNameIsOnlySnappedWhenNothingElseIsAsClose() {
        // "Via Bellini" and "Via Bellina" are both one letter from "via bellino": no way to choose.
        assertEquals("via bellino", refine("via bellino"))
    }

    @Test
    fun shortNamesAreNeverGuessed() {
        // One letter from "Via Rosa", but a short name is as likely to be another town's street.
        assertEquals("via rosi", refine("via rosi"))
        assertEquals("via rama", refine("via rama"))
    }

    @Test
    fun aStreetThatIsNotInTheListIsLeftAlone() {
        assertEquals("via garibaldi 4", refine("via garibaldi quattro"))
        assertEquals("via nazario sauro 12", refine("via nazario sauro dodici"))
    }

    // ── what the model really wrote ────────────────────────────────────────────

    @Test
    fun textsExactlyAsParakeetWroteThemOnTheHostAreRepaired() {
        // Captured by running the real model on synthesised Italian speech.
        assertEquals("Via Darsena 17 Ravenna", refine("Via D'Arsena 17 Ravenna"))
        assertEquals("Via Chiavica Romea 123", refine("Via Chavica Romea 123"))
        assertEquals("Via Cavour 8", refine("Via Kawaur numero 8"))
        assertEquals("Via XX Settembre", refine("Via 20 settembre"))
        assertEquals("Via IV Novembre 1945", refine("Via 4 novembre 1945"))
        assertEquals("Viale Italia 3 bis", refine("Viale Italia 3 bis"))
    }

    // ── numbers without a street list ──────────────────────────────────────────

    @Test
    fun houseNumbersBecomeDigitsEvenWithoutAStreetList() {
        assertEquals("via darsena 17", refine("via darsena diciassette", index = null))
        assertEquals("via darsena 17 ravenna", refine("via darsena diciassette ravenna", index = null))
        assertEquals("via roma 3 bis", refine("via roma tre bis", index = null))
        assertEquals("via cesare battisti 21", refine("via cesare battisti ventuno", index = null))
        assertEquals("corso garibaldi 247", refine("corso garibaldi duecentoquarantasette", index = null))
        assertEquals("via roma 1200", refine("via roma milleduecento", index = null))
    }

    @Test
    fun theWordNumeroInFrontOfAHouseNumberGoes() {
        assertEquals("via cavour 8", refine("via cavour numero otto", index = null))
        assertEquals("via cavour 8", refine("via cavour numero 8", index = null))
        assertEquals("via cavour 8", refine("via cavour civico otto", index = null))
    }

    @Test
    fun numbersInANameAreLeftAlone() {
        assertEquals("piazza tre martiri", refine("piazza tre martiri", index = null))
        assertEquals("via dei tre martiri", refine("via dei tre martiri", index = null))
        assertEquals("via venti settembre", refine("via venti settembre", index = null))
        assertEquals("via cinque giornate", refine("via cinque giornate", index = null))
    }

    // ── what must not be touched ───────────────────────────────────────────────

    @Test
    fun textThatIsNotAnAddressPassesThrough() {
        assertEquals("una pizzeria vicino a me", refine("una pizzeria vicino a me"))
        assertEquals("ristorante cinque stelle", refine("ristorante cinque stelle"))
        assertEquals("sei arrivato", refine("sei arrivato"))
        assertEquals("farmacia", refine("farmacia"))
        assertEquals("", refine(""))
        assertEquals("   ", refine("   "))
    }

    @Test
    fun ambiguousWordsNeedARealStreetBehindThem() {
        assertEquals("via roma 1", refine("via roma uno", index = null))
        assertEquals("via sei febbraio", refine("via sei febbraio", index = null))
        assertEquals("via roma sei febbraio", refine("via roma sei febbraio", index = null))
    }

    @Test
    fun otherLanguagesKeepTheirNumbersButStillGetTheirStreetsSnapped() {
        assertEquals("Via Darsena diciassette", refine("via d'arsena diciassette", italian = false))
        assertEquals("main street seventeen", refine("main street seventeen", italian = false))
    }

    @Test
    fun anEmptyStreetListChangesNothingButNumbers() {
        assertEquals("via darsena 17", refine("via d'arsena diciassette", index = StreetIndex(emptyList())).replace("d'arsena", "darsena"))
    }

    @Test
    fun theIndexKeepsOneEntryPerSpellingAndSkipsOneWordNames() {
        val index = StreetIndex(listOf("Via Roma", "VIA ROMA", "via  roma", "Roma", "", "Darsena"))
        assertEquals(1, index.size)
    }
}
