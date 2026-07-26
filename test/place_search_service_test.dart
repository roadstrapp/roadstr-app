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
      expect(PlaceSearchService.relaxQuery('via attilio monti'), 'via monti');
      expect(PlaceSearchService.relaxQuery('corso giuseppe garibaldi'),
          'corso garibaldi');
    });

    test('nothing sensible to drop below three words', () {
      expect(PlaceSearchService.relaxQuery('via monti'), isNull);
      expect(PlaceSearchService.relaxQuery('roma'), isNull);
      expect(PlaceSearchService.relaxQuery('   '), isNull);
    });

    test('collapses irregular spacing', () {
      expect(PlaceSearchService.relaxQuery('  via   attilio   monti  '),
          'via monti');
    });
  });

  group('dedupeByProximity', () {
    test('drops the same place seen by two providers', () {
      final list = PlaceSearchService.dedupeByProximity([
        r('Via Monti, Ravenna', lat: 44.4000, lon: 12.2000),
        r('Via Monti', lat: 44.40005, lon: 12.20005), // ~7 m away
      ]);
      expect(list, hasLength(1));
      expect(list.first.shortName, 'Via Monti, Ravenna'); // first one wins
    });

    test('keeps genuinely different places', () {
      final list = PlaceSearchService.dedupeByProximity([
        r('Via Monti, Ravenna', lat: 44.4000, lon: 12.2000),
        r('Via Monti, Cesena', lat: 44.1400, lon: 12.2400),
      ]);
      expect(list, hasLength(2));
    });

    test('empty input is safe', () {
      expect(PlaceSearchService.dedupeByProximity([]), isEmpty);
    });
  });

  group('rankResults', () {
    test('the street actually typed comes first, not the fuller name', () {
      // The reported bug: OSM has "Via Monti", the user types the street sign.
      final ranked = PlaceSearchService.rankResults(
        'via attilio monti',
        [
          r('Via Attilio Rivalta, Ravenna', lat: 44.41, lon: 12.21),
          r('Via Monti, Ravenna', lat: 44.42, lon: 12.22),
        ],
        null,
      );
      expect(ranked.first.shortName, 'Via Monti, Ravenna');
    });

    test('a typo still ranks the right street first', () {
      final ranked = PlaceSearchService.rankResults(
        'via attillio mnti',
        [
          r('Via Orioli Attilio, Ravenna', lat: 44.41, lon: 12.21),
          r('Via Attilio Monti, Ravenna', lat: 44.42, lon: 12.22),
        ],
        null,
      );
      expect(ranked.first.shortName, 'Via Attilio Monti, Ravenna');
    });

    test('equally good matches are ordered by distance', () {
      final near = LatLng(44.40, 12.20);
      final ranked = PlaceSearchService.rankResults(
        'via roma',
        [
          r('Via Roma, Milano', lat: 45.46, lon: 9.19),
          r('Via Roma, Ravenna', lat: 44.41, lon: 12.20),
        ],
        near,
      );
      expect(ranked.first.shortName, 'Via Roma, Ravenna');
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
              'via attilio monti', r('Via Attilio Monti, Ravenna')),
          1);
    });

    test('the full address is only a fallback signal', () {
      final score = PlaceSearchService.matchScore(
        'emilia romagna',
        r('Via Monti', display: 'Via Monti, Ravenna, Emilia-Romagna, Italia'),
      );
      expect(score, greaterThan(0));
      expect(score, lessThan(0.9)); // scaled down vs a direct name match
    });
  });
}
