import 'package:latlong2/latlong.dart';

import '../utils/fuzzy_match.dart';
import 'search_response_protocol.dart';

typedef SearchExecutionPlan = ({
  String query,
  bool useNominatim,
  bool usePhoton,
  bool usePoi,
  bool allowRelaxedRetry,
});

/// Deterministic provider planning, ranking and merge policy for place search.
///
/// This boundary does not execute requests. It consumes normalized provider
/// results and is shared with the native rewrite through generated fixtures.
class SearchRankingProtocol {
  static const duplicateRadiusM = 30.0;
  static const maxResults = 10;
  static const maxQueryLength = 200;
  static const matchThreshold = 0.66;
  static const _distance = Distance();

  /// Normalizes the query and freezes which providers may run in this phase.
  static SearchExecutionPlan? executionPlan(
    String query, {
    required bool settled,
    required bool hasNear,
  }) {
    var prepared = query.trim();
    if (prepared.isEmpty) return null;
    if (prepared.length > maxQueryLength) {
      prepared = prepared.substring(0, maxQueryLength);
    }
    return (
      query: prepared,
      useNominatim: settled,
      usePhoton: true,
      usePoi: hasNear,
      allowRelaxedRetry: settled,
    );
  }

  /// Ranks a provider result set using city, confidence, brand and distance.
  static List<NominatimResult> rankResults(
    String query,
    List<NominatimResult> results,
    LatLng? near,
  ) {
    if (results.length < 2) return results;
    final detected = _detectQueryCity(query, results);
    final queryCity = detected?.city;
    final matchQuery = detected == null
        ? query
        : detected.words
            .sublist(0, detected.words.length - detected.cityWordCount)
            .join(' ');
    final effectiveQuery = matchQuery.isEmpty ? query : matchQuery;
    final scored = results
        .map((result) => (
              result: result,
              score: matchScore(effectiveQuery, result),
              distance: near == null
                  ? 0.0
                  : _distance.as(
                      LengthUnit.Meter,
                      near,
                      result.position,
                    ),
              inQueryCity: queryCity != null &&
                  result.city != null &&
                  FuzzyMatch.score(queryCity, result.city!) >= matchThreshold,
              brandMatch: result.brand != null &&
                  FuzzyMatch.score(effectiveQuery, result.brand!) >=
                      matchThreshold,
            ))
        .toList();
    scored.sort((a, b) {
      if (queryCity != null && a.inQueryCity != b.inQueryCity) {
        return a.inQueryCity ? -1 : 1;
      }
      final aMatch = a.score >= matchThreshold || a.brandMatch;
      final bMatch = b.score >= matchThreshold || b.brandMatch;
      if (aMatch != bMatch) return aMatch ? -1 : 1;
      if (aMatch) return a.distance.compareTo(b.distance);
      final band = (b.score * 10).round().compareTo((a.score * 10).round());
      return band != 0 ? band : a.distance.compareTo(b.distance);
    });
    return scored.map((entry) => entry.result).take(maxResults).toList();
  }

  /// Best match between the query and the short/full names of one result.
  static double matchScore(String query, NominatimResult result) {
    var best = FuzzyMatch.score(query, result.shortName);
    final comma = result.shortName.indexOf(',');
    if (comma > 0) {
      final name = FuzzyMatch.score(
        query,
        result.shortName.substring(0, comma),
      );
      if (name > best) best = name;
    }
    if (best >= 1) return best;
    final full = FuzzyMatch.score(query, result.displayName) * 0.9;
    return full > best ? full : best;
  }

  /// Removes co-located provider duplicates while keeping the first result.
  static List<NominatimResult> dedupeByProximity(
    List<NominatimResult> results,
  ) {
    final output = <NominatimResult>[];
    for (final result in results) {
      if (!_isNear(result, output)) output.add(result);
    }
    return output;
  }

  /// Applies the production Nominatim-first geocoder merge and ranking.
  static List<NominatimResult> rankGeocoders(
    String query,
    List<NominatimResult> nominatim,
    List<NominatimResult> photon,
    LatLng? near,
  ) =>
      rankResults(
        query,
        dedupeByProximity([...nominatim, ...photon]),
        near,
      );

  /// Returns the one allowed relaxed retry, or null when no retry may run.
  static String? relaxedRetryQuery(
    SearchExecutionPlan plan,
    List<NominatimResult> geocoded,
    List<NominatimResult> poi,
  ) {
    if (!plan.allowRelaxedRetry || geocoded.isNotEmpty || poi.isNotEmpty) {
      return null;
    }
    return relaxQuery(plan.query);
  }

  /// Builds the first+last-word fallback used after an empty settled search.
  static String? relaxQuery(String query) {
    final words = query.trim().split(RegExp(r'\s+'))
      ..removeWhere((word) => word.isEmpty);
    return words.length < 3 ? null : '${words.first} ${words.last}';
  }

  /// Gives category/brand POIs precedence and suppresses nearby geo copies.
  static List<NominatimResult> mergePoiFirst(
    List<NominatimResult> poi,
    List<NominatimResult> geocoded,
  ) {
    if (poi.isEmpty) return geocoded;
    final merged = [...poi];
    for (final result in geocoded) {
      if (!_isNear(result, poi)) merged.add(result);
    }
    return merged;
  }

  static ({String city, List<String> words, int cityWordCount})?
      _detectQueryCity(String query, List<NominatimResult> results) {
    final words = query.trim().split(RegExp(r'\s+'))
      ..removeWhere((word) => word.isEmpty);
    if (words.isEmpty) return null;
    for (var count = words.length < 3 ? words.length : 3; count >= 1; count--) {
      final chunk = words.sublist(words.length - count).join(' ');
      for (final result in results) {
        final city = result.city;
        if (city == null) continue;
        if (FuzzyMatch.score(chunk, city) >= matchThreshold) {
          return (city: city, words: words, cityWordCount: count);
        }
      }
    }
    return null;
  }

  static bool _isNear(
    NominatimResult result,
    List<NominatimResult> others,
  ) =>
      others.any(
        (other) =>
            _distance.as(
              LengthUnit.Meter,
              other.position,
              result.position,
            ) <
            duplicateRadiusM,
      );
}
