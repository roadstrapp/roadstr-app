import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/utils/off_route_detector.dart';

/// Feeds a run of distances and returns the index of the sample that first
/// reported a deviation, or null if none did.
int? firstDeviationAt(OffRouteDetector d, List<double> distances) {
  for (var i = 0; i < distances.length; i++) {
    if (d.sawDeviation(distances[i])) return i;
  }
  return null;
}

void main() {
  group('OffRouteDetector', () {
    test('a fix well past the hard threshold is off route immediately', () {
      final d = OffRouteDetector();
      expect(d.sawDeviation(80), isTrue);
    });

    test('driving along the route, wandering a few metres, never fires', () {
      final d = OffRouteDetector();
      // A lane's worth of drift around the polyline, in both directions.
      expect(
        firstDeviationAt(d, [4, 9, 6, 12, 8, 3, 11, 7, 5, 10, 6, 8]),
        isNull,
      );
    });

    test('the reported bug: one block off in a grid, gap never reaching the '
        'hard threshold, is still caught', () {
      final d = OffRouteDetector();
      // Turned off the route and drove parallel one block over: the gap opens
      // and then plateaus around 45 m, under the 55 m hard threshold, which
      // is exactly the case distance-alone detection missed.
      final at = firstDeviationAt(d, [8, 22, 34, 45, 46, 45, 44]);
      expect(at, isNotNull);
      expect(at, lessThanOrEqualTo(5),
          reason: 'must fire within a few seconds of leaving the route');
    });

    test('a gap that opens but stays inside the noise floor does not fire',
        () {
      final d = OffRouteDetector();
      // 2 m to 24 m is a 22 m swing — more than awayGrowthM — but the car is
      // still on the road, which is what the noise floor is there to say.
      expect(
        firstDeviationAt(d, [2, 18, 22, 24, 23, 24, 22, 24]),
        isNull,
      );
    });

    test('a single jittery fix does not fire on its own', () {
      final d = OffRouteDetector();
      // One bad fix 40 m out, then straight back onto the route.
      expect(firstDeviationAt(d, [6, 40, 7, 5, 8]), isNull);
    });

    test('coming back towards the route resets the trend', () {
      final d = OffRouteDetector();
      // Drifts out, comes back, drifts out again — neither run is long
      // enough on its own, and they must not add up across the return.
      expect(
        firstDeviationAt(d, [5, 32, 38, 4, 33, 36]),
        isNull,
      );
    });

    test('reset() forgets the closest approach, so a new route starts clean',
        () {
      final d = OffRouteDetector();
      d.sawDeviation(5);
      d.sawDeviation(35);
      d.sawDeviation(36);
      d.reset();
      // Without the reset these two would be the third and fourth samples
      // held away from a 5 m baseline and would fire.
      expect(d.sawDeviation(37), isFalse);
      expect(d.sawDeviation(38), isFalse);
    });

    test('firing clears the trend, so the next fix does not fire again', () {
      final d = OffRouteDetector();
      expect(firstDeviationAt(d, [8, 22, 34, 45, 46, 45]), isNotNull);
      // A reroute is now in flight; the very next fix must not immediately
      // demand another one.
      expect(d.sawDeviation(46), isFalse);
    });

    test('a non-finite distance is ignored rather than trusted', () {
      final d = OffRouteDetector();
      expect(d.sawDeviation(double.nan), isFalse);
      expect(d.sawDeviation(double.infinity), isFalse);
    });
  });
}
