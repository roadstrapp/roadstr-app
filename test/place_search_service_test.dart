import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/place_search_service.dart';
import 'package:roadstr/services/poi_search_service.dart';
import 'package:roadstr/services/routing_service.dart' show NominatimResult;

NominatimResult r(String short,
        {String? display,
        double lat = 44.4,
        double lon = 12.2,
        String? city,
        String? brand}) =>
    NominatimResult(
      displayName: display ?? short,
      shortName: short,
      position: LatLng(lat, lon),
      city: city,
      brand: brand,
    );

void main() {
  group('relaxQuery', () {
    test('keeps first and last word — the shape OSM usually stores', () {
      expect(PlaceSearchService.relaxQuery('via roberto ricci'), 'via ricci');
      expect(PlaceSearchService.relaxQuery('corso giuseppe garibaldi'),
          'corso garibaldi');
    });

    test('nothing sensible to drop below three words', () {
      expect(PlaceSearchService.relaxQuery('via ricci'), isNull);
      expect(PlaceSearchService.relaxQuery('roma'), isNull);
      expect(PlaceSearchService.relaxQuery('   '), isNull);
    });

    test('collapses irregular spacing', () {
      expect(PlaceSearchService.relaxQuery('  via   roberto   ricci  '),
          'via ricci');
    });
  });

  group('dedupeByProximity', () {
    test('drops the same place seen by two providers', () {
      final list = PlaceSearchService.dedupeByProximity([
        r('Via Ricci, Torino', lat: 45.0700, lon: 7.6800),
        r('Via Ricci', lat: 45.07005, lon: 7.68005), // ~7 m away
      ]);
      expect(list, hasLength(1));
      expect(list.first.shortName, 'Via Ricci, Torino'); // first one wins
    });

    test('keeps genuinely different places', () {
      final list = PlaceSearchService.dedupeByProximity([
        r('Via Ricci, Torino', lat: 45.0700, lon: 7.6800),
        r('Via Ricci, Porto', lat: 41.1500, lon: -8.6100),
      ]);
      expect(list, hasLength(2));
    });

    test('empty input is safe', () {
      expect(PlaceSearchService.dedupeByProximity([]), isEmpty);
    });
  });

  group('rankResults', () {
    test('the street actually typed comes first, not the fuller name', () {
      // The reported bug: OSM has "Via Ricci", the user types the street sign.
      final ranked = PlaceSearchService.rankResults(
        'via roberto ricci',
        [
          r('Via Roberto Baldini, Torino', lat: 45.08, lon: 7.69),
          r('Via Ricci, Torino', lat: 45.09, lon: 7.70),
        ],
        null,
      );
      expect(ranked.first.shortName, 'Via Ricci, Torino');
    });

    test('a typo still ranks the right street first', () {
      final ranked = PlaceSearchService.rankResults(
        'via robberto ricc',
        [
          r('Via Fabbri Roberto, Torino', lat: 45.08, lon: 7.69),
          r('Via Roberto Ricci, Torino', lat: 45.09, lon: 7.70),
        ],
        null,
      );
      expect(ranked.first.shortName, 'Via Roberto Ricci, Torino');
    });

    test('equally good matches are ordered by distance', () {
      final near = LatLng(45.07, 7.68);
      final ranked = PlaceSearchService.rankResults(
        'via roma',
        [
          r('Via Roma, Milano', lat: 45.46, lon: 9.19),
          r('Via Roma, Torino', lat: 45.08, lon: 7.68),
        ],
        near,
      );
      expect(ranked.first.shortName, 'Via Roma, Torino');
    });

    test('caps the list at a scannable length', () {
      final many = List.generate(
          25, (i) => r('Via Roma $i', lat: 44.4 + i * 0.01, lon: 12.2));
      expect(PlaceSearchService.rankResults('via roma', many, null),
          hasLength(10));
    });

    test('a single result is returned untouched', () {
      final one = [r('Via Roma')];
      expect(PlaceSearchService.rankResults('qualsiasi cosa', one, null), one);
    });

    test(
        'a generic/franchise query lists confident matches nearest first, '
        'even when they score slightly differently as plain text', () {
      final near = LatLng(38.72, -9.14); // Lisbon-ish
      final ranked = PlaceSearchService.rankResults(
        'mercatino usato',
        [
          r('Mercatino Usato - Sintra (Via Nova)',
              lat: 38.80, lon: -9.38, city: 'Sintra'), // ~22 km, worded oddly
          r('Mercatino dell\'Usato',
              lat: 38.73, lon: -9.14), // ~1 km, plain name
        ],
        near,
      );
      expect(ranked.first.shortName, "Mercatino dell'Usato");
    });

    test(
        'a brand match ranks with the confident tier even when its own '
        'name text scores lower than a same-worded unrelated shop', () {
      final near = LatLng(38.72, -9.14);
      final ranked = PlaceSearchService.rankResults(
        'mercatino usato',
        [
          // Closer, shares generic wording, NOT the franchise.
          r('Mercatino delle Pulci', lat: 38.73, lon: -9.13),
          // Farther, but tagged as the real franchise via `brand`.
          r('Il Mercatino di Paolo',
              lat: 38.80, lon: -9.36, brand: "Mercatino dell'Usato"),
        ],
        near,
      );
      expect(ranked.first.shortName, 'Il Mercatino di Paolo');
    });

    test('a city named in the query outranks plain distance', () {
      final near = LatLng(38.72, -9.14); // near Lisbon
      final ranked = PlaceSearchService.rankResults(
        'mercatino usato sintra',
        [
          // Closer to the user, but not in Sintra.
          r('Mercatino Usato Lisboa', lat: 38.73, lon: -9.14, city: 'Lisboa'),
          // Farther from the user, but actually in Sintra — the named city.
          r('Mercatino Usato Sintra', lat: 38.80, lon: -9.38, city: 'Sintra'),
        ],
        near,
      );
      expect(ranked.first.shortName, 'Mercatino Usato Sintra');
    });

    test(
        'a city name that matches no result is not invented — plain '
        'distance ordering still applies', () {
      final near = LatLng(44.4, 12.2);
      final ranked = PlaceSearchService.rankResults(
        'mercatino usato nowhereville',
        [
          r('Mercatino Usato A', lat: 38.73, lon: -9.14, city: 'Lisboa'),
          r('Mercatino Usato B', lat: 38.80, lon: -9.38, city: 'Sintra'),
        ],
        near,
      );
      // Neither result is "in Nowhereville", so the closer one still wins.
      expect(ranked.first.shortName, 'Mercatino Usato A');
    });

    test(
        'multiple cities named in different results only boost the one the '
        'query actually names, not every city-tagged result', () {
      final near = LatLng(44.4, 12.2);
      final ranked = PlaceSearchService.rankResults(
        'mercatino usato sintra',
        [
          r('Mercatino Usato Cascais', lat: 38.70, lon: -9.42, city: 'Cascais'),
          r('Mercatino Usato Sintra', lat: 38.80, lon: -9.38, city: 'Sintra'),
        ],
        near,
      );
      expect(ranked.first.shortName, 'Mercatino Usato Sintra');
    });
  });

  group('matchScore', () {
    test('the town in the label does not penalise a street-only query', () {
      expect(
          PlaceSearchService.matchScore(
              'via roberto ricci', r('Via Roberto Ricci, Torino')),
          1);
    });

    test('the full address is only a fallback signal', () {
      final score = PlaceSearchService.matchScore(
        'torino piemonte',
        r('Via Ricci', display: 'Via Ricci, Torino, Piemonte, Italia'),
      );
      expect(score, greaterThan(0));
      expect(score, lessThan(0.9)); // scaled down vs a direct name match
    });
  });

  group('search phases', () {
    // What is verifiable without a network: the category provider is the one
    // deliberately kept in BOTH phases, because it only reaches Overpass when
    // the word actually names a category — deferring it would make "pharmacy"
    // answer late for no saving. Nominatim's absence from the fast pass is
    // enforced in PlaceSearchService.search itself; its endpoint is a const,
    // so there is no seam to assert on here without a live request.
    test('the category provider is asked in both phases', () async {
      final poi = _RecordingPoi();
      final service = PlaceSearchService(poi: poi);
      const near = LatLng(44.4, 12.2);

      await service.search('pharmacy',
          near: near, phase: SearchPhase.typeAhead);
      expect(poi.calls, 1, reason: 'fast pass must still answer categories');

      await service.search('pharmacy', near: near, phase: SearchPhase.settled);
      expect(poi.calls, 2);
    });

    test('settled is the default, so existing callers are unchanged', () async {
      final poi = _RecordingPoi();
      await PlaceSearchService(poi: poi)
          .search('pharmacy', near: const LatLng(44.4, 12.2));
      expect(poi.calls, 1);
    });

    test('an empty query reaches no provider at all', () async {
      final poi = _RecordingPoi();
      final service = PlaceSearchService(poi: poi);
      expect(
          await service.search('   ', near: const LatLng(44.4, 12.2)), isEmpty);
      expect(poi.calls, 0);
    });
  });

  group('async provider orchestration', () {
    test('first non-empty completion is partial and final waits for all',
        () async {
      final nominatim = Completer<List<NominatimResult>>();
      final photon = Completer<List<NominatimResult>>();
      final poi = _CompleterPoi();
      final partials = <List<NominatimResult>>[];
      final service = PlaceSearchService(
        poi: poi,
        nominatimSearch: (_, __) => nominatim.future,
        photonSearch: (_, __, ___) => photon.future,
      );

      var finished = false;
      final search = service.search(
        'via roberto ricci',
        near: const LatLng(45, 9),
        onPartial: partials.add,
      )..then((_) => finished = true);

      photon.complete([r('Photon', lat: 45.1, lon: 9.1)]);
      await pumpEventQueue();
      expect(partials.single.single.shortName, 'Photon');
      expect(finished, isFalse);

      poi.completer.complete([r('Poi', lat: 46, lon: 10)]);
      await pumpEventQueue();
      expect(partials, hasLength(1));
      expect(finished, isFalse);

      nominatim.complete([r('Nominatim', lat: 47, lon: 11)]);
      expect(
        (await search).map((result) => result.shortName),
        ['Poi', 'Photon', 'Nominatim'],
      );
      expect(finished, isTrue);
    });

    test('one failing provider degrades to empty without hiding peer results',
        () async {
      final service = PlaceSearchService(
        poi: _ValuePoi([r('Poi', lat: 46, lon: 10)]),
        nominatimSearch: (_, __) => throw StateError('sync failure'),
        photonSearch: (_, __, ___) => Future.error(StateError('async failure')),
      );

      final results = await service.search(
        'via roberto ricci',
        near: const LatLng(45, 9),
      );
      expect(results.map((result) => result.shortName), ['Poi']);
    });

    test('all-empty settled search starts exactly one relaxed provider batch',
        () async {
      final nominatimQueries = <String>[];
      final photonQueries = <String>[];
      final service = PlaceSearchService(
        poi: _ValuePoi(const []),
        nominatimSearch: (query, _) async {
          nominatimQueries.add(query);
          return query == 'via ricci' ? [r('Recovered')] : const [];
        },
        photonSearch: (query, _, __) async {
          photonQueries.add(query);
          return const [];
        },
      );

      final results = await service.search(
        'via roberto ricci',
        near: const LatLng(45, 9),
      );
      expect(nominatimQueries, ['via roberto ricci', 'via ricci']);
      expect(photonQueries, ['via roberto ricci', 'via ricci']);
      expect(results.single.shortName, 'Recovered');
    });

    test('a throwing partial callback cannot fail the final search', () async {
      final service = PlaceSearchService(
        poi: _ValuePoi(const []),
        nominatimSearch: (_, __) async => const [],
        photonSearch: (_, __, ___) async => [r('Photon')],
      );

      final results = await service.search(
        'museum',
        phase: SearchPhase.typeAhead,
        onPartial: (_) => throw StateError('widget already gone'),
      );
      expect(results.single.shortName, 'Photon');
    });
  });
}

/// Counts how often the category provider is consulted. Returns nothing, so
/// the surrounding search still runs its merge and ranking paths.
class _RecordingPoi extends PoiSearchService {
  int calls = 0;

  @override
  Future<List<NominatimResult>> search(String query, LatLng center) async {
    calls++;
    return const [];
  }
}

class _CompleterPoi extends PoiSearchService {
  final completer = Completer<List<NominatimResult>>();

  @override
  Future<List<NominatimResult>> search(String query, LatLng center) =>
      completer.future;
}

class _ValuePoi extends PoiSearchService {
  _ValuePoi(this.results);

  final List<NominatimResult> results;

  @override
  Future<List<NominatimResult>> search(String query, LatLng center) async =>
      results;
}
