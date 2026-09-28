package app.roadstr.service.search

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.search.FuzzyMatch
import java.util.Locale
import kotlin.math.abs

/** Pure category admission and Overpass query composition used by native search. */
internal object NativePoiSearchProtocol {
    const val RADIUS_METERS = 4_000
    const val ELEMENT_LIMIT = 12

    private val categories: Map<String, List<String>> = linkedMapOf(
        "supermercato" to listOf("shop=supermarket"),
        "supermarket" to listOf("shop=supermarket"),
        "grocery" to listOf("shop=supermarket"),
        "alimentari" to listOf("shop=supermarket", "shop=convenience"),
        "cinema" to listOf("amenity=cinema"),
        "movie theater" to listOf("amenity=cinema"),
        "teatro" to listOf("amenity=theatre"),
        "theatre" to listOf("amenity=theatre"),
        "theater" to listOf("amenity=theatre"),
        "benzinaio" to listOf("amenity=fuel"),
        "distributore" to listOf("amenity=fuel"),
        "gas station" to listOf("amenity=fuel"),
        "petrol station" to listOf("amenity=fuel"),
        "fuel" to listOf("amenity=fuel"),
        "colonnina elettrica" to listOf("amenity=charging_station"),
        "ev charging" to listOf("amenity=charging_station"),
        "ristorante" to listOf("amenity=restaurant"),
        "restaurant" to listOf("amenity=restaurant"),
        "pizzeria" to listOf("amenity=restaurant;cuisine=pizza"),
        "bar" to listOf("amenity=bar"),
        "pub" to listOf("amenity=pub"),
        "caffe" to listOf("amenity=cafe"),
        "caffè" to listOf("amenity=cafe"),
        "cafe" to listOf("amenity=cafe"),
        "coffee" to listOf("amenity=cafe"),
        "fast food" to listOf("amenity=fast_food"),
        "farmacia" to listOf("amenity=pharmacy"),
        "pharmacy" to listOf("amenity=pharmacy"),
        "ospedale" to listOf("amenity=hospital"),
        "hospital" to listOf("amenity=hospital"),
        "pronto soccorso" to listOf("amenity=hospital"),
        "bancomat" to listOf("amenity=atm"),
        "atm" to listOf("amenity=atm"),
        "banca" to listOf("amenity=bank"),
        "bank" to listOf("amenity=bank"),
        "parcheggio" to listOf("amenity=parking"),
        "parking" to listOf("amenity=parking"),
        "stazione" to listOf("railway=station"),
        "train station" to listOf("railway=station"),
        "aeroporto" to listOf("aeroway=aerodrome"),
        "airport" to listOf("aeroway=aerodrome"),
        "hotel" to listOf("tourism=hotel"),
        "albergo" to listOf("tourism=hotel"),
        "scuola" to listOf("amenity=school"),
        "school" to listOf("amenity=school"),
        "posta" to listOf("amenity=post_office"),
        "post office" to listOf("amenity=post_office"),
        "polizia" to listOf("amenity=police"),
        "police" to listOf("amenity=police"),
        "carabinieri" to listOf("amenity=police"),
        "commissariato" to listOf("amenity=police"),
        "polizei" to listOf("amenity=police"),
        "policia" to listOf("amenity=police"),
        "chiesa" to listOf("amenity=place_of_worship"),
        "church" to listOf("amenity=place_of_worship"),
        "supermercati" to listOf("shop=supermarket"),
    )

    fun categoryFilters(query: String): List<String>? {
        val normalized = FuzzyMatch.normalize(query)
        if (normalized.isEmpty()) return null
        categories[normalized]?.let { return it }
        if (normalized.length < 4) return null

        var best: List<String>? = null
        var bestScore = 0.8
        for ((candidate, filters) in categories) {
            if (abs(candidate.length - normalized.length) > 2) continue
            val score = FuzzyMatch.wordScore(normalized, candidate)
            if (score > bestScore) {
                bestScore = score
                best = filters
            }
        }
        return best
    }

    fun overpassQuery(
        filters: List<String>,
        center: SearchResponsePoint,
    ): String {
        val latitude = coordinate(center.latitude)
        val longitude = coordinate(center.longitude)
        val clauses = filters.joinToString("") { filter ->
            val tags = filter.split(';').joinToString("") { part ->
                val pair = part.split('=', limit = 2)
                require(pair.size == 2) { "Invalid native POI filter" }
                "[\"${pair[0]}\"=\"${pair[1]}\"]"
            }
            "node$tags(around:$RADIUS_METERS,$latitude,$longitude);" +
                "way$tags(around:$RADIUS_METERS,$latitude,$longitude);"
        }
        return "[out:json][timeout:5];($clauses);out center $ELEMENT_LIMIT;"
    }

    private fun coordinate(value: Double): String =
        String.format(Locale.ROOT, "%.7f", value)
}
