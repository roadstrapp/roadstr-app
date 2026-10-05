package app.roadstr.core.discovery

import app.roadstr.core.discovery.lexicon.Lexicon
import app.roadstr.core.discovery.lexicon.LexiconEntry
import app.roadstr.core.discovery.lexicon.LexiconKind
import app.roadstr.core.discovery.lexicon.LexiconRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconCoverageTest {
    private val allLocales = listOf(
        "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "ga", "hr", "hu", "it",
        "ja", "lt", "lv", "mt", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "zh",
    )

    // These languages mark "in <place>" with a suffix, so they have no standalone marker.
    private val suffixLocative = setOf("hu", "fi", "et", "lt", "lv")

    private val coreCategories = listOf(
        PlaceCategory.RESTAURANT, PlaceCategory.CAFE, PlaceCategory.SUPERMARKET,
        PlaceCategory.PHARMACY, PlaceCategory.FUEL, PlaceCategory.CHARGING_STATION,
        PlaceCategory.PARKING, PlaceCategory.HOTEL, PlaceCategory.HOSPITAL, PlaceCategory.ATM,
        PlaceCategory.BANK, PlaceCategory.POST_OFFICE, PlaceCategory.POLICE,
        PlaceCategory.CINEMA, PlaceCategory.TRAIN_STATION,
    )

    private fun ownEntries(locale: String): List<LexiconEntry> =
        LexiconRegistry.ownLexicon(locale).entries

    private fun categoriesOf(entries: List<LexiconEntry>): Set<String> =
        entries.filter { it.kind == LexiconKind.CATEGORY }.mapNotNull { it.id }.toSet()

    @Test
    fun `the registry knows exactly the 27 roadstr languages`() {
        assertEquals(allLocales, LexiconRegistry.locales)
    }

    @Test
    fun `every language parses`() {
        for (locale in allLocales) {
            assertTrue(locale, ownEntries(locale).isNotEmpty())
        }
    }

    @Test
    fun `every language covers the core categories directly or through a group`() {
        for (locale in allLocales) {
            val entries = ownEntries(locale)
            val categories = categoriesOf(entries)
            val missing = coreCategories.filter { it.name !in categories }
            assertTrue("$locale is missing $missing", missing.isEmpty())
        }
    }

    @Test
    fun `every language has the diet words`() {
        for (locale in allLocales) {
            val attributes = ownEntries(locale)
                .filter { it.kind == LexiconKind.ATTRIBUTE }.mapNotNull { it.id }.toSet()
            for (needed in listOf("VEGAN", "VEGETARIAN", "GLUTEN_FREE")) {
                assertTrue("$locale lacks $needed", needed in attributes)
            }
        }
    }

    @Test
    fun `every language has the connectors`() {
        for (locale in allLocales) {
            val kinds = ownEntries(locale).map { it.kind }.toSet()
            for (needed in listOf(LexiconKind.NEAR_ME, LexiconKind.OPEN_NOW, LexiconKind.FILLER)) {
                assertTrue("$locale lacks ${needed.key}", needed in kinds)
            }
            val place = setOf(
                LexiconKind.IN, LexiconKind.IN_AFTER, LexiconKind.NEAR,
                LexiconKind.NEAR_AFTER, LexiconKind.NEAR_BOTH,
            )
            assertTrue("$locale has no place marker", kinds.any { it in place })
            if (locale !in suffixLocative) {
                assertTrue(
                    "$locale lacks an 'in' marker",
                    LexiconKind.IN in kinds || LexiconKind.IN_AFTER in kinds,
                )
            }
        }
    }

    @Test
    fun `a phrase means one thing inside a language`() {
        val allowedPairs = setOf(
            setOf(LexiconKind.NEAR, LexiconKind.NEAR_AFTER),
            setOf(LexiconKind.NEAR, LexiconKind.NEAR_BOTH),
            setOf(LexiconKind.NEAR_AFTER, LexiconKind.NEAR_BOTH),
        )
        for (locale in allLocales) {
            val byPhrase = ownEntries(locale)
                .filterNot { it.prefix }
                .groupBy { it.tokens.joinToString(" ") }
            for ((phrase, entries) in byPhrase) {
                val meanings = entries.map { it.kind to it.id }.toSet()
                if (meanings.size <= 1) continue
                val kinds = meanings.map { it.first }.toSet()
                assertTrue("$locale: '$phrase' means $meanings", kinds in allowedPairs)
            }
        }
    }

    @Test
    fun `english stays behind every language as a fallback`() {
        val italian = LexiconRegistry.forLocale("it")
        val words = listOf("restaurant")
        assertEquals(PlaceCategory.RESTAURANT.name, italian.longestMatch(words, 0)?.entry?.id)
        assertTrue(LexiconRegistry.forLocale("xx").entries.isNotEmpty())
        assertEquals("pt", LexiconRegistry.languageOf("pt-BR"))
        assertEquals("zh", LexiconRegistry.languageOf("zh_TW"))
        assertEquals("en", LexiconRegistry.languageOf("tlh"))
    }

    @Test
    fun `a language's own words beat the english fallback`() {
        val italian: Lexicon = LexiconRegistry.forLocale("it")
        val match = italian.longestMatch(listOf("bar"), 0)
        assertEquals(LexiconKind.GROUP, match?.entry?.kind)
        val dutch = LexiconRegistry.forLocale("nl").longestMatch(listOf("diner"), 0)
        assertEquals(LexiconKind.GROUP, dutch?.entry?.kind)
    }
}
