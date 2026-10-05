package app.roadstr.core.discovery.lexicon

import app.roadstr.core.discovery.CategoryGroup
import app.roadstr.core.discovery.Cuisine
import app.roadstr.core.discovery.PlaceAttribute
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.TextNormalizer

/** What a vocabulary phrase means to the query interpreter. */
enum class LexiconKind(val key: String, val takesId: Boolean) {
    CATEGORY("c", true),
    GROUP("g", true),
    ATTRIBUTE("a", true),
    CUISINE("u", true),

    /** "near me", "nearby". */
    NEAR_ME("near_me", false),
    OPEN_NOW("open_now", false),

    /** "in <place>": the place follows the word. */
    IN("in", false),

    /** "<place> の": the place comes before the word (Japanese, Chinese). */
    IN_AFTER("in_after", false),

    /** "near <place>": the reference follows the word. */
    NEAR("near", false),

    /** "<place> nearby": the reference comes before the word. */
    NEAR_AFTER("near_after", false),

    /** Either order ("near X", "X nearby"): Finnish, Hungarian and other postposition languages. */
    NEAR_BOTH("near_both", false),
    NEAR_DESTINATION("near_dest", false),
    ROUTE("route", false),
    FILLER("filler", false),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): LexiconKind? = byKey[key]
    }
}

/** One phrase, already tokenised the same way queries are. */
data class LexiconEntry(
    val kind: LexiconKind,
    val id: String?,
    val tokens: List<String>,
    /** The last token is a prefix ("restaurac*" matches "restaurace", "restauraci"). */
    val prefix: Boolean,
)

data class LexiconMatch(val entry: LexiconEntry, val length: Int)

/**
 * A searchable vocabulary. Earlier entries win over later ones for the same
 * phrase, so a locale's words override the English fallback merged behind them.
 */
class Lexicon(val locale: String, entries: List<LexiconEntry>) {
    val entries: List<LexiconEntry> = entries.toList()

    /** Japanese and Chinese have no spaces; a place name there spans more tokens. */
    val scriptless: Boolean = locale == "ja" || locale == "zh"

    private val exact = HashMap<String, LexiconEntry>()
    private val prefixed = HashMap<String, MutableList<LexiconEntry>>()
    private val longest: Int

    init {
        var max = 1
        for (entry in this.entries) {
            max = maxOf(max, entry.tokens.size)
            if (entry.prefix) {
                prefixed.getOrPut(headKey(entry.tokens)) { mutableListOf() } += entry
            } else {
                exact.putIfAbsent(entry.tokens.joinToString(" "), entry)
            }
        }
        longest = max
    }

    /** The longest phrase starting at [start]; an exact phrase beats a prefix one. */
    fun longestMatch(tokens: List<String>, start: Int): LexiconMatch? {
        val most = minOf(longest, tokens.size - start)
        for (length in most downTo 1) {
            matchOfLength(tokens, start, length)?.let { return it }
        }
        return null
    }

    private fun matchOfLength(tokens: List<String>, start: Int, length: Int): LexiconMatch? {
        val key = tokens.subList(start, start + length).joinToString(" ")
        exact[key]?.let { return LexiconMatch(it, length) }
        val head = tokens.subList(start, start + length - 1).joinToString(" ")
        val last = tokens[start + length - 1]
        val hit = prefixed[head]?.firstOrNull { last.startsWith(it.tokens.last()) }
        return hit?.let { LexiconMatch(it, length) }
    }

    private fun headKey(tokens: List<String>): String = tokens.dropLast(1).joinToString(" ")

    companion object {
        fun merged(locale: String, primary: Lexicon, fallback: Lexicon): Lexicon =
            Lexicon(locale, primary.entries + fallback.entries)
    }
}

/**
 * Reads the compact vocabulary text: one line per meaning,
 * `kind [ID]: phrase, phrase, …`; blank lines and `#` comments are skipped. A
 * phrase ending in `*` is a prefix (at least four characters before the star).
 */
object LexiconParser {
    private const val MIN_PREFIX_CHARS = 4

    fun parse(source: String, text: String): List<LexiconEntry> {
        val entries = ArrayList<LexiconEntry>()
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            try {
                entries += parseLine(line)
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException("$source line ${index + 1}: ${failure.message}", failure)
            }
        }
        return entries
    }

    private fun parseLine(line: String): List<LexiconEntry> {
        val colon = line.indexOf(':')
        require(colon > 0) { "missing ':'" }
        val head = line.substring(0, colon).trim().split(Regex("\\s+"))
        val kind = LexiconKind.fromKey(head[0]) ?: throw IllegalArgumentException("unknown kind '${head[0]}'")
        val id = head.getOrNull(1)
        require(head.size == (if (kind.takesId) 2 else 1)) { "bad header '${line.substring(0, colon)}'" }
        if (id != null) requireKnownId(kind, id)
        val phrases = line.substring(colon + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(phrases.isNotEmpty()) { "no phrases" }
        return phrases.map { phrase -> entry(kind, id, phrase) }
    }

    private fun entry(kind: LexiconKind, id: String?, phrase: String): LexiconEntry {
        val prefix = phrase.endsWith("*")
        val body = if (prefix) phrase.dropLast(1) else phrase
        val tokens = TextNormalizer.tokenize(body).map { it.text }
        require(tokens.isNotEmpty()) { "empty phrase '$phrase'" }
        if (prefix) {
            require(tokens.last().length >= MIN_PREFIX_CHARS) { "prefix too short in '$phrase'" }
        }
        return LexiconEntry(kind, id, tokens, prefix)
    }

    private fun requireKnownId(kind: LexiconKind, id: String) {
        val known = when (kind) {
            LexiconKind.CATEGORY -> PlaceCategory.entries.any { it.name == id }
            LexiconKind.GROUP -> CategoryGroup.entries.any { it.name == id }
            LexiconKind.ATTRIBUTE -> PlaceAttribute.entries.any { it.name == id }
            LexiconKind.CUISINE -> Cuisine.entries.any { it.name == id }
            else -> true
        }
        require(known) { "unknown ${kind.key} id '$id'" }
    }
}
