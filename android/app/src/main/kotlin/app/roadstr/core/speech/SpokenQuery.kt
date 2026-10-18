package app.roadstr.core.speech

/** Turns what a speech model wrote into something fit for a search box. */
object SpokenQuery {
    private val trailingPunctuation = Regex("""[\s.!?,;:…"'«»]+$""")
    private val leadingPunctuation = Regex("""^[\s"'«»]+""")
    private val spaces = Regex("""\s+""")

    /**
     * Speech models punctuate like prose ("Via Garibaldi, Bologna."); a search box wants no full
     * stop at the end. Inner punctuation is kept: it separates street from town and carries hyphens.
     */
    fun normalize(raw: String): String = raw
        .replace(spaces, " ")
        .replace(leadingPunctuation, "")
        .replace(trailingPunctuation, "")
        .trim()
}
