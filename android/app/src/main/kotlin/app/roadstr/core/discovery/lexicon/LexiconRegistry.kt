package app.roadstr.core.discovery.lexicon

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** The 27 vocabularies, each with the English one behind it as a fallback. */
object LexiconRegistry {
    private val sources: Map<String, String> = mapOf(
        "bg" to LexiconBg.TEXT,
        "cs" to LexiconCs.TEXT,
        "da" to LexiconDa.TEXT,
        "de" to LexiconDe.TEXT,
        "el" to LexiconEl.TEXT,
        "en" to LexiconEn.TEXT,
        "es" to LexiconEs.TEXT,
        "et" to LexiconEt.TEXT,
        "fi" to LexiconFi.TEXT,
        "fr" to LexiconFr.TEXT,
        "ga" to LexiconGa.TEXT,
        "hr" to LexiconHr.TEXT,
        "hu" to LexiconHu.TEXT,
        "it" to LexiconIt.TEXT,
        "ja" to LexiconJa.TEXT,
        "lt" to LexiconLt.TEXT,
        "lv" to LexiconLv.TEXT,
        "mt" to LexiconMt.TEXT,
        "nl" to LexiconNl.TEXT,
        "pl" to LexiconPl.TEXT,
        "pt" to LexiconPt.TEXT,
        "ro" to LexiconRo.TEXT,
        "ru" to LexiconRu.TEXT,
        "sk" to LexiconSk.TEXT,
        "sl" to LexiconSl.TEXT,
        "sv" to LexiconSv.TEXT,
        "zh" to LexiconZh.TEXT,
    )

    val locales: List<String> = sources.keys.sorted()

    private val own = ConcurrentHashMap<String, Lexicon>()
    private val merged = ConcurrentHashMap<String, Lexicon>()

    /** The language part of a tag such as `pt-BR`, or `en` for one Roadstr has no words for. */
    fun languageOf(tag: String): String {
        val language = tag.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        return if (language in sources) language else "en"
    }

    /** The vocabulary of [tag]'s language followed by the English one. */
    fun forLocale(tag: String): Lexicon {
        val language = languageOf(tag)
        return merged.getOrPut(language) {
            val primary = ownLexicon(language)
            if (language == "en") primary else Lexicon.merged(language, primary, ownLexicon("en"))
        }
    }

    /** Only the vocabulary of [language], for coverage checks. */
    fun ownLexicon(language: String): Lexicon = own.getOrPut(language) {
        val text = requireNotNull(sources[language]) { "No vocabulary for $language" }
        Lexicon(language, LexiconParser.parse("lexicon $language", text))
    }
}
