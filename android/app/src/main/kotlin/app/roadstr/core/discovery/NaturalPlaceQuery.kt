package app.roadstr.core.discovery

enum class QueryIntent { FIND_PLACE, NAME_OR_ADDRESS }

/** Where a search should look. */
sealed interface LocationConstraint {
    data object CurrentLocation : LocationConstraint

    data object MapCenter : LocationConstraint

    data object Destination : LocationConstraint

    /**
     * A place the user typed ("Florence"); it still has to resolve to an area.
     * [alternatives] are shorter readings of the same words, for names that contain
     * a preposition ("San Giovanni in Fiore" could also be read as "Fiore").
     */
    data class NamedPlace(
        val text: String,
        val alternatives: List<String> = emptyList(),
    ) : LocationConstraint

    /**
     * "Near the station": a reference place, optionally of a known category.
     * [generic] means the words name only a kind of place ("the station"), so the
     * nearest one will do; otherwise they name a particular place ("Central Park").
     */
    data class NearReference(
        val text: String,
        val category: PlaceCategory?,
        val generic: Boolean = false,
    ) : LocationConstraint

    data object RouteCorridor : LocationConstraint
}

/**
 * What the rule-based interpreter understood of a typed query.
 *
 * [classicQuery] is what the existing search should receive when this is not a
 * place search: the typed text with "near me" / "open now" style words removed.
 */
data class NaturalPlaceQuery(
    val rawText: String,
    val locale: String,
    val intent: QueryIntent,
    val categories: List<PlaceCategory>,
    val attributes: Set<PlaceAttribute>,
    val cuisines: Set<Cuisine>,
    val location: LocationConstraint,
    val locationExplicit: Boolean,
    val openNow: Boolean,
    val residualTerms: List<String>,
    /** Trailing residual words that might be a place name ("cinema Verona"). */
    val residualPlaceGuess: String?,
    val classicQuery: String,
) {
    /** Leftover words a web search could use (dish or menu words, a name). */
    val webHint: Boolean
        get() = intent == QueryIntent.FIND_PLACE && residualTerms.isNotEmpty()

    // The typed text is the user's search; keep it out of logs.
    override fun toString(): String = "NaturalPlaceQuery(intent=$intent)"
}

/** The seam a local model could later plug into; today it is rule-based. */
fun interface QueryInterpreter {
    fun interpret(rawText: String, locale: String): NaturalPlaceQuery
}
