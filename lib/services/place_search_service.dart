import 'dart:async';

import 'package:latlong2/latlong.dart';

import '../utils/fuzzy_match.dart';
import 'photon_geocoder.dart';
import 'poi_search_service.dart';
import 'routing_service.dart' show NominatimResult, RoutingService;

/// Turns what the user typed into a ranked list of places.
///
/// Three providers answer in parallel, because each is bad at what the others
/// are good at:
///
///   * **Overpass** (category/brand near me) — the fix for generic terms like
///     "cinema", which Nominatim's global importance ranking would answer with
///     a same-named business on the other side of the world instead of the one
///     500 m away. Its hits lead the list when the query names a category.
///   * **Photon** — typo-tolerant and prefix-based, so it answers while the
///     user is still typing and survives misspellings.
///   * **Nominatim** — strict, but the best at fully-qualified addresses.
///
/// Their combined output is then re-ranked by how well each result actually
/// matches the text ([FuzzyMatch]) rather than by provider order, and a query
/// that finds nothing anywhere is relaxed once and retried.
class PlaceSearchService {
  PlaceSearchService({PoiSearchService? poi}) : _poi = poi ?? PoiSearchService();

  final PoiSearchService _poi;

  /// Two results are the same place below this distance. Providers overlap
  /// heavily — they all read OSM — so without this the list shows every hit
  /// two or three times.
  static const _duplicateRadiusM = 30.0;

  /// Suggestions beyond this are not scanned by a driver; they only cost
  /// layout time.
  static const _maxResults = 10;

  static const _distance = Distance();

  /// Searches for [query], biased toward [near] when a GPS fix is available.
  ///
  /// [onPartial] is invoked as soon as the *first* provider answers, so the
  /// list paints without waiting for the slowest one — that wait is most of
  /// the perceived sluggishness of the search box. It may be called once, or
  /// not at all, before the returned future completes.
  Future<List<NominatimResult>> search(
    String query, {
    LatLng? near,
    String languageCode = 'en',
    void Function(List<NominatimResult>)? onPartial,
  }) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return const [];

    final nominatimFuture = RoutingService.search(trimmed, near: near);
    final photonFuture =
        PhotonGeocoder.search(trimmed, near: near, languageCode: languageCode);
    final poiFuture = near != null
        ? _poi.search(trimmed, near)
        : Future.value(const <NominatimResult>[]);

    if (onPartial != null) {
      var shown = false;
      for (final f in [photonFuture, nominatimFuture, poiFuture]) {
        unawaited(f.then((r) {
          if (shown || r.isEmpty) return;
          shown = true;
          onPartial(rankResults(trimmed, r, near));
        }).catchError((_) {}));
      }
    }

    final photon = await photonFuture;
    final nominatim = await nominatimFuture;
    final poi = await poiFuture;

    // Nominatim first in the merge order so its better-formatted entry wins
    // the dedupe when both geocoders return the same place.
    var geo =
        rankResults(trimmed, dedupeByProximity([...nominatim, ...photon]), near);

    if (geo.isEmpty && poi.isEmpty) {
      final relaxed = relaxQuery(trimmed);
      if (relaxed != null) {
        final retry = await Future.wait([
          RoutingService.search(relaxed, near: near),
          PhotonGeocoder.search(relaxed, near: near, languageCode: languageCode),
        ]);
        geo = rankResults(
            relaxed, dedupeByProximity([...retry[0], ...retry[1]]), near);
      }
    }

    if (poi.isEmpty) return geo;
    final merged = [...poi];
    for (final g in geo) {
      if (!_isNear(g, poi)) merged.add(g);
    }
    return merged;
  }

  /// Orders results by textual match quality first, proximity second, and caps
  /// the list at a scannable length.
  static List<NominatimResult> rankResults(
      String query, List<NominatimResult> results, LatLng? near) {
    if (results.length < 2) return results;
    final scored = results
        .map((r) => (
              result: r,
              score: matchScore(query, r),
              distance: near == null
                  ? 0.0
                  : _distance.as(LengthUnit.Meter, near, r.position),
            ))
        .toList();
    scored.sort((a, b) {
      // Coarse score bands: a 2 % scoring difference must not outweigh being
      // 40 km closer.
      final band = (b.score * 10).round().compareTo((a.score * 10).round());
      return band != 0 ? band : a.distance.compareTo(b.distance);
    });
    return scored.map((e) => e.result).take(_maxResults).toList();
  }

  /// Best match between the query and the several names a result carries.
  ///
  /// Scoring the street name alone and the "street, town" form separately
  /// matters: the town is in the label whether or not the user typed it, so
  /// comparing only against the full string would punish everyone who types
  /// just a street name — by far the common case.
  static double matchScore(String query, NominatimResult r) {
    var best = FuzzyMatch.score(query, r.shortName);
    final comma = r.shortName.indexOf(',');
    if (comma > 0) {
      final name = FuzzyMatch.score(query, r.shortName.substring(0, comma));
      if (name > best) best = name;
    }
    if (best >= 1) return best;
    // The full address is a weaker signal: it carries region and country words
    // that nobody types.
    final full = FuzzyMatch.score(query, r.displayName) * 0.9;
    return full > best ? full : best;
  }

  /// Removes results pointing at the same place, keeping the first occurrence.
  static List<NominatimResult> dedupeByProximity(List<NominatimResult> all) {
    final out = <NominatimResult>[];
    for (final r in all) {
      if (!_isNear(r, out)) out.add(r);
    }
    return out;
  }

  static bool _isNear(NominatimResult r, List<NominatimResult> others) =>
      others.any((o) =>
          _distance.as(LengthUnit.Meter, o.position, r.position) <
          _duplicateRadiusM);

  /// Builds a shorter, likelier-to-hit variant of a query that returned
  /// nothing, or null when there is nothing sensible to drop.
  ///
  /// European street names are usually "type + given name(s) + surname"
  /// ("via Attilio Monti") while OSM frequently stores only "type + surname"
  /// ("via Monti"). Keeping the first and last words reproduces exactly that
  /// shape, which recovers the single most common miss.
  static String? relaxQuery(String query) {
    final words = query.trim().split(RegExp(r'\s+'))
      ..removeWhere((w) => w.isEmpty);
    return words.length < 3 ? null : '${words.first} ${words.last}';
  }
}
