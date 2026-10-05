package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.TextNormalizer
import app.roadstr.core.discovery.TextSimilarity

/** Reads the name of a business out of a web page title such as "Trattoria Verde - Menu | Verona". */
object WebTitleNames {
    private const val MIN_NAME_CHARS = 3
    private const val MAX_NAME_CHARS = 60

    private val separators = Regex("\\s[-\\u2013\\u2014|:\\u00b7\\u2022\\u00bb/]\\s|[|\\u00b7\\u2022\\u00bb]")

    /** Words that make a title segment describe the page, not the business. */
    private val pageWords = setOf(
        "menu", "menus", "home", "homepage", "official", "ufficiale", "sito", "website", "site",
        "sitio", "startseite", "accueil", "inicio", "reviews", "review", "recensioni", "opiniones",
        "bewertungen", "avis", "tripadvisor", "prezzi", "prices", "contatti", "contact", "contacts",
        "about", "orari", "hours", "carta", "speisekarte", "carte", "reservations", "prenota",
        "prenotazioni", "booking", "pagina", "page", "ristorante", "restaurant", "restaurants",
        "pizzeria", "bar", "hotel", "yelp", "facebook", "instagram", "maps", "mappa", "map",
    )

    fun segments(title: String): List<String> =
        title.split(separators).map { it.trim() }.filter { it.length in MIN_NAME_CHARS..MAX_NAME_CHARS }

    /** The segment most likely to be the business name, or null when every segment describes the page. */
    fun guess(title: String): String? = segments(title).firstOrNull { !isPageWords(it) }

    /** How closely any segment of [title] resembles [name]. */
    fun similarity(title: String, name: String): Double {
        if (name.isBlank()) return 0.0
        val parts = segments(title).ifEmpty { listOf(title.trim()) }
        return TextSimilarity.best(name, parts)
    }

    private fun isPageWords(segment: String): Boolean {
        val words = TextNormalizer.normalize(segment).split(' ').filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all { it in pageWords }
    }
}
