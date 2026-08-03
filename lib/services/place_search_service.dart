import 'dart:async';

import 'package:latlong2/latlong.dart';

import '../utils/fuzzy_match.dart';
import 'photon_geocoder.dart';
import 'poi_search_service.dart';
import 'routing_service.dart' show NominatimResult, RoutingService;

/// How much of the provider set a search may use.
///
/// The search box fires on a 300 ms pause, which means most queries it sends
/// are half-typed words. That is fine for one provider and wasted work for
/// another — see [SearchPhase.typeAhead].
enum SearchPhase {
  /// Every pause in typing. Photon (and a category lookup, when the word is
  /// one) but never Nominatim.
  typeAhead,

  /// The query the user has settled on — a longer pause, or submit. Every
  /// provider, including the relaxed-query retry.
  settled,
}

/// Turns what the user typed into a ranked list of places.
///
/// Three providers answer in parallel, because each is bad at what the others
/// are good at:
///
///   * **Overpass** (category/brand near me) — the fix for generic terms like
///     "cinema", which Nominatim's global importance ranking would answer with
///     a same-named business on the other side of the world instead of the one
///     500 m away. Its hits lead the list when the query names a category.
///     Cheap on the wire despite the name: it only reaches the network when
///     the text actually resolves to a category, so it does not fire on
///     ordinary typing at all.
///   * **Photon** — typo-tolerant and prefix-based, so it answers while the
///     user is still typing and survives misspellings.
///   * **Nominatim** — strict, but the best at fully-qualified addresses.
///
/// Their combined output is then re-ranked by how well each result actually
/// matches the text ([FuzzyMatch]) rather than by provider order, and a query
/// that finds nothing anywhere is relaxed once and retried.
///
/// **Why Nominatim waits for [SearchPhase.settled].** It does not do prefix
/// matching: measured across the states of one query being typed out, it
/// answered with nothing for half of them ("tour eif" → 0 results, "tour
/// eiffel" → 2) while Photon answered all of them. So on a half-typed word it
/// contributes nothing and still costs a request — and its usage policy asks
/// specifically that it not be used for autocomplete. Both problems have the
/// same fix: ask it once, when the user has stopped typing. Nothing is lost in
/// the list, because [PhotonGeocoder] already normalises its results into the
/// same "street number, city" shape.
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

  /// Longest query dispatched to the providers.
  ///
  /// No place name comes close. The cap is here because the search box runs on
  /// every keystroke: an accidentally pasted wall of text would otherwise
  /// become a huge request and a quadratic amount of fuzzy scoring on the UI
  /// thread. Truncating rather than rejecting keeps a long-but-real address
  /// working.
  static const maxQueryLength = 200;

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
    SearchPhase phase = SearchPhase.settled,
    void Function(List<NominatimResult>)? onPartial,
  }) async {
    var trimmed = query.trim();
    if (trimmed.isEmpty) return const [];
    if (trimmed.length > maxQueryLength) {
      trimmed = trimmed.substring(0, maxQueryLength);
    }
    final full = phase == SearchPhase.settled;

    final nominatimFuture = full
        ? RoutingService.search(trimmed, near: near)
        : Future.value(const <NominatimResult>[]);
    final photonFuture =
        PhotonGeocoder.search(trimmed, near: near, languageCode: languageCode);
    // Kept in both phases: it stays local unless the word names a category, so
    // deferring it would only make "pharmacy" answer late for no saving.
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

    // Nominatim first in the merge order: the two providers agree on shape, so
    // this only decides which copy survives the dedupe.
    var geo =
        rankResults(trimmed, dedupeByProximity([...nominatim, ...photon]), near);

    // The relaxed retry is a "found nothing anywhere" recovery — it doubles the
    // requests, so it belongs to the settled query, not to a word in progress
    // that is about to gain another letter.
    if (full && geo.isEmpty && poi.isEmpty) {
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
  /// ("via Roberto Ricci") while OSM frequently stores only "type + surname"
  /// ("via Ricci"). Keeping the first and last words reproduces exactly that
  /// shape, which recovers the single most common miss.
  static String? relaxQuery(String query) {
    final words = query.trim().split(RegExp(r'\s+'))
      ..removeWhere((w) => w.isEmpty);
    return words.length < 3 ? null : '${words.first} ${words.last}';
  }
}
