package app.roadstr.core.search

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class SearchExecutionPlan(
    val query: String,
    val useNominatim: Boolean,
    val usePhoton: Boolean,
    val usePoi: Boolean,
    val allowRelaxedRetry: Boolean,
)

/** Exact socket-free provider planning, ranking and merge policy for search. */
object SearchRankingProtocol {
    const val DUPLICATE_RADIUS_METERS = 30.0
    const val MAX_RESULTS = 10
    const val MAX_QUERY_LENGTH = 200
    const val MATCH_THRESHOLD = 0.66

    fun executionPlan(
        query: String,
        settled: Boolean,
        hasNear: Boolean,
    ): SearchExecutionPlan? {
        var prepared = query.trimDart()
        if (prepared.isEmpty()) return null
        if (prepared.length > MAX_QUERY_LENGTH) {
            prepared = prepared.substring(0, MAX_QUERY_LENGTH)
        }
        return SearchExecutionPlan(
            query = prepared,
            useNominatim = settled,
            usePhoton = true,
            usePoi = hasNear,
            allowRelaxedRetry = settled,
        )
    }

    fun rankResults(
        query: String,
        results: List<SearchResult>,
        near: SearchResponsePoint?,
    ): List<SearchResult> {
        if (results.size < 2) return results
        val detected = detectQueryCity(query, results)
        val queryCity = detected?.city
        val matchQuery = if (detected == null) {
            query
        } else {
            detected.words
                .subList(0, detected.words.size - detected.cityWordCount)
                .joinToString(" ")
        }
        val effectiveQuery = matchQuery.ifEmpty { query }
        val scored = results.map { result ->
            ScoredResult(
                result = result,
                score = matchScore(effectiveQuery, result),
                distance = near?.let { roundedVincentyDistance(it, result.position) } ?: 0.0,
                inQueryCity = queryCity != null &&
                    result.city != null &&
                    FuzzyMatch.score(queryCity, result.city) >= MATCH_THRESHOLD,
                brandMatch = result.brand != null &&
                    FuzzyMatch.score(effectiveQuery, result.brand) >= MATCH_THRESHOLD,
            )
        }.toMutableList()
        scored.sortWith { first, second ->
            if (queryCity != null && first.inQueryCity != second.inQueryCity) {
                return@sortWith if (first.inQueryCity) -1 else 1
            }
            val firstMatches = first.score >= MATCH_THRESHOLD || first.brandMatch
            val secondMatches = second.score >= MATCH_THRESHOLD || second.brandMatch
            if (firstMatches != secondMatches) {
                return@sortWith if (firstMatches) -1 else 1
            }
            if (firstMatches) {
                return@sortWith first.distance.compareTo(second.distance)
            }
            val band = dartRound(second.score * 10).compareTo(dartRound(first.score * 10))
            if (band != 0) band else first.distance.compareTo(second.distance)
        }
        return scored.take(MAX_RESULTS).map(ScoredResult::result)
    }

    fun matchScore(query: String, result: SearchResult): Double {
        var best = FuzzyMatch.score(query, result.shortName)
        val comma = result.shortName.indexOf(',')
        if (comma > 0) {
            val name = FuzzyMatch.score(query, result.shortName.substring(0, comma))
            if (name > best) best = name
        }
        if (best >= 1) return best
        val full = FuzzyMatch.score(query, result.displayName) * 0.9
        return if (full > best) full else best
    }

    fun dedupeByProximity(results: List<SearchResult>): List<SearchResult> {
        val output = mutableListOf<SearchResult>()
        for (result in results) {
            if (!isNear(result, output)) output += result
        }
        return output
    }

    fun rankGeocoders(
        query: String,
        nominatim: List<SearchResult>,
        photon: List<SearchResult>,
        near: SearchResponsePoint?,
    ): List<SearchResult> = rankResults(
        query = query,
        results = dedupeByProximity(nominatim + photon),
        near = near,
    )

    fun relaxedRetryQuery(
        plan: SearchExecutionPlan,
        geocoded: List<SearchResult>,
        poi: List<SearchResult>,
    ): String? {
        if (!plan.allowRelaxedRetry || geocoded.isNotEmpty() || poi.isNotEmpty()) return null
        return relaxQuery(plan.query)
    }

    fun relaxQuery(query: String): String? {
        val words = splitDartWhitespace(query.trimDart())
        return if (words.size < 3) null else "${words.first()} ${words.last()}"
    }

    fun mergePoiFirst(
        poi: List<SearchResult>,
        geocoded: List<SearchResult>,
    ): List<SearchResult> {
        if (poi.isEmpty()) return geocoded
        val merged = poi.toMutableList()
        for (result in geocoded) {
            if (!isNear(result, poi)) merged += result
        }
        return merged
    }

    private data class QueryCity(
        val city: String,
        val words: List<String>,
        val cityWordCount: Int,
    )

    private data class ScoredResult(
        val result: SearchResult,
        val score: Double,
        val distance: Double,
        val inQueryCity: Boolean,
        val brandMatch: Boolean,
    )

    private fun detectQueryCity(
        query: String,
        results: List<SearchResult>,
    ): QueryCity? {
        val words = splitDartWhitespace(query.trimDart())
        if (words.isEmpty()) return null
        for (count in minOf(words.size, 3) downTo 1) {
            val chunk = words.takeLast(count).joinToString(" ")
            for (result in results) {
                val city = result.city ?: continue
                if (FuzzyMatch.score(chunk, city) >= MATCH_THRESHOLD) {
                    return QueryCity(city, words, count)
                }
            }
        }
        return null
    }

    private fun isNear(result: SearchResult, others: List<SearchResult>): Boolean =
        others.any { other ->
            roundedVincentyDistance(other.position, result.position) < DUPLICATE_RADIUS_METERS
        }

    private fun dartRound(value: Double): Long = floor(value + 0.5).toLong()

    /** Mirrors latlong2's default rounded WGS-84 Vincenty distance. */
    private fun roundedVincentyDistance(
        first: SearchResponsePoint,
        second: SearchResponsePoint,
    ): Double {
        val equatorRadius = 6_378_137.0
        val polarRadius = 6_356_752.314245
        val flattening = 1 / 298.257223563
        val latitude1 = Math.toRadians(first.latitude)
        val latitude2 = Math.toRadians(second.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val u1 = atan((1 - flattening) * tan(latitude1))
        val u2 = atan((1 - flattening) * tan(latitude2))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)
        var lambda = longitudeDelta
        var iterations = 200
        var sinSigma: Double
        var cosSigma: Double
        var sigma: Double
        var sinAlpha: Double
        var cosSqAlpha: Double
        var cos2SigmaM: Double
        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) *
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda),
            )
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            if (cos2SigmaM.isNaN()) cos2SigmaM = 0.0
            val c = flattening / 16 * cosSqAlpha * (4 + flattening * (4 - 3 * cosSqAlpha))
            val previousLambda = lambda
            lambda = longitudeDelta +
                (1 - c) * flattening * sinAlpha *
                (sigma + c * sinSigma *
                    (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - previousLambda) <= 1e-12) break
        } while (--iterations > 0)
        if (iterations == 0) error("Distance calculation failed to converge")
        val uSquared = cosSqAlpha *
            (equatorRadius * equatorRadius - polarRadius * polarRadius) /
            (polarRadius * polarRadius)
        val a = 1 + uSquared / 16_384 *
            (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)))
        val b = uSquared / 1024 *
            (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)))
        val deltaSigma = b * sinSigma *
            (
                cos2SigmaM + b / 4 *
                    (
                        cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                            b / 6 * cos2SigmaM *
                            (-3 + 4 * sinSigma * sinSigma) *
                            (-3 + 4 * cos2SigmaM * cos2SigmaM)
                    )
            )
        return floor(polarRadius * a * (sigma - deltaSigma) + 0.5)
    }
}

private fun splitDartWhitespace(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    val words = mutableListOf<String>()
    var start = -1
    for (index in value.indices) {
        if (value[index].isDartWhitespace()) {
            if (start >= 0) {
                words += value.substring(start, index)
                start = -1
            }
        } else if (start < 0) {
            start = index
        }
    }
    if (start >= 0) words += value.substring(start)
    return words
}

private fun String.trimDart(): String {
    var start = 0
    var end = length
    while (start < end && this[start].isDartWhitespace()) start++
    while (end > start && this[end - 1].isDartWhitespace()) end--
    return substring(start, end)
}

private fun Char.isDartWhitespace(): Boolean =
    this in '\u0009'..'\u000d' ||
        this == '\u0020' ||
        this == '\u0085' ||
        this == '\u00a0' ||
        this == '\u1680' ||
        this in '\u2000'..'\u200a' ||
        this == '\u2028' ||
        this == '\u2029' ||
        this == '\u202f' ||
        this == '\u205f' ||
        this == '\u3000' ||
        this == '\ufeff'
