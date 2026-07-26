import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/place_search_service.dart';
import 'package:roadstr/services/routing_service.dart' show NominatimResult;

NominatimResult r(String short, {String? display, double lat = 44.4, double lon = 12.2}) =>
    NominatimResult(
      displayName: display ?? short,
      shortName: short,
      position: LatLng(lat, lon),
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
        r('Via Ricci, Cesena', lat: 44.1400, lon: 12.2400),
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
}
