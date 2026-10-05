package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.OsmRef
import app.roadstr.core.discovery.TextNormalizer
import java.net.URI
import java.util.Locale

/** Checks that look for a place's own details inside the text of a search result. */
object TextEvidence {
    private const val MIN_PHONE_DIGITS = 7
    private const val PHONE_SUFFIX_DIGITS = 9
    private val phoneLike = Regex("[+(]?\\d[\\d\\s().\\-/]{5,}\\d")

    /** Whether [text] contains [phone], with or without the country and area prefix. */
    fun phoneIn(phone: String?, text: String): Boolean {
        val own = phone?.filter(Char::isDigit).orEmpty()
        if (own.length < MIN_PHONE_DIGITS) return false
        val suffix = own.takeLast(PHONE_SUFFIX_DIGITS)
        return phoneLike.findAll(text).any { run ->
            val digits = run.value.filter(Char::isDigit)
            digits.length >= MIN_PHONE_DIGITS && (digits.endsWith(suffix) || own.endsWith(digits))
        }
    }

    /** Whether [text] gives the place's street and house number together, in either order. */
    fun addressIn(tags: Map<String, String>, text: String): Boolean {
        val street = TextNormalizer.normalize(tags["addr:street"].orEmpty())
        val number = TextNormalizer.normalize(tags["addr:housenumber"].orEmpty())
        if (street.isEmpty() || number.isEmpty()) return false
        val haystack = " ${TextNormalizer.normalize(text)} "
        return haystack.contains(" $street $number ") || haystack.contains(" $number $street ")
    }

    /** The OSM element an openstreetmap.org link points at, or null for any other address. */
    fun osmRefOf(url: URI): OsmRef? {
        val host = url.host?.lowercase(Locale.ROOT) ?: return null
        if (host != "openstreetmap.org" && host != "www.openstreetmap.org") return null
        val parts = url.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val type = when (parts[0]) {
            "node" -> OsmElementType.NODE
            "way" -> OsmElementType.WAY
            "relation" -> OsmElementType.RELATION
            else -> return null
        }
        val id = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return OsmRef(type, id)
    }
}
