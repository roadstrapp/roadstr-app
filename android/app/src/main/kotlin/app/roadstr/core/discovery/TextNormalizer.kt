package app.roadstr.core.discovery

import java.text.Normalizer
import java.util.Locale

/** One word of normalised text and the span of the original string it came from. */
data class TextToken(val text: String, val rawStart: Int, val rawEnd: Int)

/**
 * Case, accent and width folding for every script Roadstr's 27 locales use.
 *
 * `FuzzyMatch.normalize` keeps only ASCII letters and a Latin accent table, so
 * Greek, Cyrillic, Japanese and Chinese text comes out empty. This keeps the
 * letters and digits of any script, drops combining marks (except the Japanese
 * voicing marks, which change the word), and treats Han, Hiragana and Katakana
 * as one token per character because those scripts have no spaces.
 */
object TextNormalizer {
    private const val DAKUTEN = 0x3099
    private const val HANDAKUTEN = 0x309A
    private const val KATAKANA_LONG_VOWEL = 0x30FC

    private val folds: Map<Int, String> = mapOf(
        'ß'.code to "ss",
        'æ'.code to "ae",
        'œ'.code to "oe",
        'ø'.code to "o",
        'đ'.code to "d",
        'ð'.code to "d",
        'ł'.code to "l",
        'ħ'.code to "h",
        'þ'.code to "th",
        'ı'.code to "i",
        'ς'.code to "σ",
    )

    fun normalize(input: String): String = tokenize(input).joinToString(" ") { it.text }

    fun tokenize(input: String): List<TextToken> {
        val tokens = ArrayList<TextToken>()
        val word = StringBuilder()
        var wordStart = -1
        var index = 0
        fun flush(end: Int) {
            if (word.isEmpty()) return
            tokens += TextToken(word.toString(), wordStart, end)
            word.setLength(0)
            wordStart = -1
        }
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            val width = Character.charCount(codePoint)
            val folded = fold(codePoint)
            when {
                folded.isEmpty() -> Unit
                isVoicingMark(folded) -> attachVoicingMark(tokens, word, folded, index + width)
                !isWordPart(folded) -> flush(index)
                isScriptless(codePoint) -> {
                    flush(index)
                    tokens += TextToken(folded, index, index + width)
                }
                else -> {
                    if (word.isEmpty()) wordStart = index
                    word.append(folded)
                }
            }
            index += width
        }
        flush(input.length)
        return tokens
    }

    private fun isVoicingMark(folded: String): Boolean =
        folded.length == 1 && (folded[0].code == DAKUTEN || folded[0].code == HANDAKUTEN)

    /** A separate voicing mark (half-width kana) belongs to the character before it. */
    private fun attachVoicingMark(
        tokens: MutableList<TextToken>,
        word: StringBuilder,
        mark: String,
        end: Int,
    ) {
        if (word.isNotEmpty()) {
            word.append(mark)
            return
        }
        val last = tokens.lastOrNull() ?: return
        tokens[tokens.lastIndex] = last.copy(text = last.text + mark, rawEnd = end)
    }

    private fun fold(codePoint: Int): String {
        val decomposed = Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFKD)
        val out = StringBuilder()
        decomposed.codePoints().forEach { part ->
            if (!isDroppedMark(part)) out.append(foldLetter(part))
        }
        return out.toString()
    }

    private fun foldLetter(codePoint: Int): String {
        val lower = Character.toLowerCase(codePoint)
        return folds[lower] ?: String(Character.toChars(lower)).lowercase(Locale.ROOT)
    }

    private fun isDroppedMark(codePoint: Int): Boolean =
        Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt() &&
            codePoint != DAKUTEN && codePoint != HANDAKUTEN

    private fun isWordPart(folded: String): Boolean {
        val first = folded.codePointAt(0)
        return Character.isLetterOrDigit(first) || first == DAKUTEN || first == HANDAKUTEN
    }

    private fun isScriptless(codePoint: Int): Boolean {
        if (codePoint == KATAKANA_LONG_VOWEL) return true
        return when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            -> true
            else -> false
        }
    }
}
