package app.roadstr.core.discovery

import app.roadstr.core.discovery.lexicon.Lexicon
import app.roadstr.core.discovery.lexicon.LexiconKind
import app.roadstr.core.discovery.lexicon.LexiconRegistry

/**
 * Rule-based interpreter: finds the vocabulary phrases of a typed query, splits
 * off a location clause, and says whether the rest is a place search.
 *
 * It decides nothing about the network. When it does not recognise a category,
 * an attribute or a cuisine the query is returned as a name or address and the
 * existing search runs unchanged.
 */
class NaturalQueryParser(
    private val lexicons: (String) -> Lexicon = LexiconRegistry::forLocale,
) : QueryInterpreter {
    override fun interpret(rawText: String, locale: String): NaturalPlaceQuery {
        val text = rawText.trim().take(MAX_QUERY_CHARS)
        val tokens = TextNormalizer.tokenize(text)
        val lexicon = lexicons(locale)
        val marks = scan(tokens, lexicon)
        return QueryAnalysis(text, locale, tokens, marks, lexicon.scriptless).build()
    }

    private fun scan(tokens: List<TextToken>, lexicon: Lexicon): List<Mark> {
        val words = tokens.map { it.text }
        val marks = ArrayList<Mark>()
        var index = 0
        while (index < words.size) {
            val match = lexicon.longestMatch(words, index)
            if (match == null) {
                index++
            } else {
                marks += Mark(index, index + match.length, match.entry.kind, match.entry.id)
                index += match.length
            }
        }
        return marks
    }

    companion object {
        const val MAX_QUERY_CHARS = 200
    }
}

private data class Mark(val start: Int, val end: Int, val kind: LexiconKind, val id: String?)

private val connectorKinds = setOf(
    LexiconKind.NEAR_ME,
    LexiconKind.OPEN_NOW,
    LexiconKind.NEAR_DESTINATION,
    LexiconKind.ROUTE,
)
private val placeStopKinds = setOf(
    LexiconKind.CATEGORY,
    LexiconKind.GROUP,
    LexiconKind.ATTRIBUTE,
    LexiconKind.CUISINE,
)
private val clauseKinds = setOf(
    LexiconKind.IN,
    LexiconKind.IN_AFTER,
    LexiconKind.NEAR,
    LexiconKind.NEAR_AFTER,
    LexiconKind.NEAR_BOTH,
)
private val referenceKinds = setOf(
    LexiconKind.NEAR,
    LexiconKind.NEAR_AFTER,
    LexiconKind.NEAR_BOTH,
)

private class QueryAnalysis(
    private val text: String,
    private val locale: String,
    private val tokens: List<TextToken>,
    private val marks: List<Mark>,
    scriptless: Boolean,
) {
    private val maxPlaceTokens = if (scriptless) 12 else 6
    private val maxGuessTokens = if (scriptless) 6 else 3
    private val cover = arrayOfNulls<Mark>(tokens.size)
    private val taken = BooleanArray(tokens.size)
    private val strippedFromClassic = BooleanArray(tokens.size)
    private var nearMe = false
    private var openNow = false
    private var nearDestination = false
    private var route = false
    private var clause: LocationConstraint? = null

    init {
        for (mark in marks) for (i in mark.start until mark.end) cover[i] = mark
    }

    fun build(): NaturalPlaceQuery {
        takeConnectors()
        takeLocationClause()
        val head = readHead()
        val categories = resolveCategories(head)
        val intent = if (categories.isEmpty()) QueryIntent.NAME_OR_ADDRESS else QueryIntent.FIND_PLACE
        val explicit = clause != null || route || nearDestination || nearMe
        return NaturalPlaceQuery(
            rawText = text,
            locale = locale,
            intent = intent,
            categories = categories,
            attributes = head.attributes,
            cuisines = head.cuisines,
            location = clause ?: defaultLocation(),
            locationExplicit = explicit,
            openNow = openNow,
            residualTerms = head.residual.map { spanText(it) },
            residualPlaceGuess = placeGuess(head, intent, clause),
            classicQuery = classicQuery(),
        )
    }

    private fun defaultLocation(): LocationConstraint = when {
        route -> LocationConstraint.RouteCorridor
        nearDestination -> LocationConstraint.Destination
        else -> LocationConstraint.CurrentLocation
    }

    private fun takeConnectors() {
        for (mark in marks) {
            if (mark.kind !in connectorKinds) continue
            when (mark.kind) {
                LexiconKind.NEAR_ME -> nearMe = true
                LexiconKind.OPEN_NOW -> openNow = true
                LexiconKind.NEAR_DESTINATION -> nearDestination = true
                else -> route = true
            }
            take(mark, strip = true)
        }
    }

    private fun take(mark: Mark, strip: Boolean = false) {
        for (i in mark.start until mark.end) {
            taken[i] = true
            if (strip) strippedFromClassic[i] = true
        }
    }

    private fun takeLocationClause() {
        val inMarks = marks.filter { it.kind == LexiconKind.IN }
        val nearKinds = setOf(LexiconKind.NEAR, LexiconKind.NEAR_BOTH)
        val nearMarks = marks.filter { it.kind in nearKinds }.asReversed()
        val suffixKinds = setOf(LexiconKind.IN_AFTER, LexiconKind.NEAR_AFTER)
        val suffix = marks.filter { it.kind in suffixKinds }
        for (mark in inMarks + nearMarks + suffix) {
            if (clause != null || taken[mark.start]) continue
            takeClauseAround(mark)
        }
        for (mark in marks) {
            if (mark.kind in clauseKinds && !taken[mark.start]) take(mark)
        }
    }

    private fun takeClauseAround(mark: Mark) {
        val reference = mark.kind in referenceKinds
        val range = clauseRange(mark, reference)
        if (range == null || (reference && isOnlyTheHeadNoun(range, mark))) {
            if (reference) {
                nearMe = true
                take(mark, strip = true)
            }
            return
        }
        val placeText = spanText(range)
        clause = if (reference) {
            LocationConstraint.NearReference(placeText, referenceCategory(range))
        } else {
            LocationConstraint.NamedPlace(placeText, alternativeReadings(mark, range))
        }
        take(mark)
        for (i in range) taken[i] = true
    }

    /** Shorter readings of a place that holds more than one "in": the text after each later one. */
    private fun alternativeReadings(first: Mark, range: IntRange): List<String> {
        val placeText = spanText(range)
        val readings = LinkedHashSet<String>()
        for (other in marks) {
            if (other.kind != LexiconKind.IN || other.start <= first.end - 1) continue
            if (other.end > range.last) continue
            val tail = trimFillers(other.end..range.last) ?: continue
            readings += spanText(tail)
        }
        readings.remove(placeText)
        return readings.toList()
    }

    /**
     * "<thing> nearby" in a postposition language: when the words before the marker are only the
     * category being searched for, nothing else names a reference place, so it means "near me".
     */
    private fun isOnlyTheHeadNoun(range: IntRange, marker: Mark): Boolean {
        val before = marker.kind == LexiconKind.NEAR_AFTER || marker.kind == LexiconKind.NEAR_BOTH
        if (!before || range.last >= marker.start) return false
        val inside = marks.any { it.kind in placeStopKinds && it.start in range }
        val outside = marks.any { it.kind in placeStopKinds && it.start !in range && !taken[it.start] }
        return inside && !outside
    }

    private fun clauseRange(mark: Mark, reference: Boolean): IntRange? = when (mark.kind) {
        LexiconKind.IN_AFTER, LexiconKind.NEAR_AFTER -> placeRange(mark, true, reference)
        LexiconKind.NEAR_BOTH -> placeRange(mark, false, true) ?: placeRange(mark, true, true)
        else -> placeRange(mark, false, reference)
    }

    /** The tokens that name the place next to a marker, trimmed of filler words. */
    private fun placeRange(mark: Mark, before: Boolean, reference: Boolean): IntRange? {
        val raw = if (before) {
            rangeBefore(mark.start, reference)
        } else {
            rangeAfter(mark.end, reference, allowIn = mark.kind == LexiconKind.IN)
        }
        val trimmed = trimFillers(raw) ?: return null
        return if (trimmed.count() > maxPlaceTokens) null else trimmed
    }

    private fun rangeAfter(from: Int, reference: Boolean, allowIn: Boolean): IntRange {
        var end = from
        while (end < tokens.size && !isStop(end, reference, allowIn)) end++
        return from until end
    }

    private fun rangeBefore(until: Int, reference: Boolean): IntRange {
        var start = until
        var collected = 0
        while (start > 0 && !isStop(start - 1, reference, false)) {
            val mark = cover[start - 1]
            val headNoun = mark != null && mark.kind in placeStopKinds
            if (collected > 0 && headNoun) break
            start = mark?.start ?: (start - 1)
            collected++
        }
        return start until until
    }

    private fun isStop(index: Int, reference: Boolean, allowIn: Boolean): Boolean {
        if (taken[index]) return true
        val mark = cover[index] ?: return false
        if (mark.kind == LexiconKind.IN && allowIn) return false
        if (mark.kind in clauseKinds) return true
        return !reference && mark.kind in placeStopKinds
    }

    private fun trimFillers(range: IntRange): IntRange? {
        var start = range.first
        var end = range.last
        while (start <= end && isFiller(start)) start++
        while (end >= start && isFiller(end)) end--
        return if (start > end) null else start..end
    }

    private fun isFiller(index: Int): Boolean = cover[index]?.kind == LexiconKind.FILLER

    private fun referenceCategory(range: IntRange): PlaceCategory? {
        for (i in range) {
            val mark = cover[i] ?: continue
            if (mark.kind == LexiconKind.CATEGORY && mark.start == i) {
                return PlaceCategory.valueOf(requireNotNull(mark.id))
            }
        }
        return null
    }

    private class Head {
        val categories = LinkedHashSet<PlaceCategory>()
        val attributes = LinkedHashSet<PlaceAttribute>()
        val cuisines = LinkedHashSet<Cuisine>()
        val residual = ArrayList<IntRange>()
        var firstMarkStart = Int.MAX_VALUE
        var lastMarkEnd = 0
    }

    private fun readHead(): Head {
        val head = Head()
        var index = 0
        while (index < tokens.size) {
            val mark = cover[index]
            index = when {
                taken[index] -> index + 1
                mark == null -> {
                    head.residual += index..index
                    index + 1
                }
                else -> {
                    absorb(head, mark)
                    mark.end
                }
            }
        }
        return head
    }

    private fun absorb(head: Head, mark: Mark) {
        val id = mark.id
        when (mark.kind) {
            LexiconKind.CATEGORY -> head.categories += PlaceCategory.valueOf(requireNotNull(id))
            LexiconKind.GROUP -> head.categories += PlaceCategory.members(CategoryGroup.valueOf(requireNotNull(id)))
            LexiconKind.ATTRIBUTE -> head.attributes += PlaceAttribute.valueOf(requireNotNull(id))
            LexiconKind.CUISINE -> head.cuisines += Cuisine.valueOf(requireNotNull(id))
            else -> return
        }
        head.firstMarkStart = minOf(head.firstMarkStart, mark.start)
        head.lastMarkEnd = mark.end
    }

    private fun resolveCategories(head: Head): List<PlaceCategory> {
        val result = LinkedHashSet(head.categories)
        if (result.isEmpty()) {
            if (head.cuisines.isNotEmpty()) result += PlaceCategory.members(CategoryGroup.EAT)
            for (attribute in head.attributes) result += impliedBy(attribute)
        }
        return result.toList()
    }

    private fun impliedBy(attribute: PlaceAttribute): List<PlaceCategory> {
        attribute.impliedCategory?.let { return listOf(it) }
        return attribute.impliedGroup?.let { PlaceCategory.members(it) } ?: emptyList()
    }

    private fun placeGuess(head: Head, intent: QueryIntent, clause: LocationConstraint?): String? {
        if (intent != QueryIntent.FIND_PLACE || clause != null) return null
        val trailing = head.residual.filter { it.first >= head.lastMarkEnd }
        val leading = head.residual.filter { it.last < head.firstMarkStart }
        val group = if (trailing.isNotEmpty()) trailing else leading
        if (group.isEmpty() || group.size > maxGuessTokens) return null
        val contiguous = group.zipWithNext().all { (a, b) -> b.first - a.last == 1 }
        return if (contiguous) spanText(group.first().first..group.last().last) else null
    }

    private fun spanText(range: IntRange): String =
        text.substring(tokens[range.first].rawStart, tokens[range.last].rawEnd).trim()

    private fun classicQuery(): String {
        val builder = StringBuilder()
        var cursor = 0
        for (i in tokens.indices) {
            if (!strippedFromClassic[i]) continue
            builder.append(text, cursor, tokens[i].rawStart)
            cursor = tokens[i].rawEnd
        }
        builder.append(text, cursor, text.length)
        val cleaned = builder.toString().replace(Regex("\\s+"), " ").trim()
        return cleaned.ifEmpty { text }
    }

}
