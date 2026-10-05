package app.roadstr.core.discovery

import app.roadstr.core.discovery.lexicon.LexiconKind
import app.roadstr.core.discovery.lexicon.LexiconRegistry
import java.util.Locale

/** Category names for the card, taken from the search vocabulary so no second list is kept. */
object PlaceCategoryNames {
    fun of(category: PlaceCategory, locale: String): String {
        val language = LexiconRegistry.languageOf(locale)
        val text = firstPhrase(category, language) ?: firstPhrase(category, "en") ?: category.name
        return text.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun firstPhrase(category: PlaceCategory, language: String): String? =
        LexiconRegistry.ownLexicon(language).entries.firstOrNull {
            it.kind == LexiconKind.CATEGORY && it.id == category.name && !it.prefix
        }?.text
}
