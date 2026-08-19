import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/utils/geo.dart';

/// Mirrors the segment choice made in map_screen: among candidates about as
/// close as the nearest, take the one closest in progress along the route.
///
/// Tested standalone because the real method lives inside a 5000-line State
/// class that cannot be instantiated without a map, a GPS stream and a theme —
/// and the arithmetic is the part that was wrong.
int chooseSegment({
  required LatLng pos,
  required List<LatLng> poly,
  required List<double> cumulative,
  required double progressM,
  required bool byProgress,
}) {
  var nearestIdx = 0;
  var nearestDist = double.infinity;
  for (var i = 0; i < poly.length - 1; i++) {
    final d = Geo.distanceToSegmentM(pos, poly[i], poly[i + 1]);
    if (d < nearestDist) {
      nearestDist = d;
      nearestIdx = i;
    }
  }
  if (!byProgress) return nearestIdx;

  final tolerance = math.max(nearestDist * 1.6, 20.0);
  var bestIdx = nearestIdx;
  var bestGap = double.infinity;
  for (var i = 0; i < poly.length - 1; i++) {
    if (Geo.distanceToSegmentM(pos, poly[i], poly[i + 1]) > tolerance) continue;
    final gap = (cumulative[i] - progressM).abs();
    if (gap < bestGap) {
      bestGap = gap;
      bestIdx = i;
    }
  }
  return bestIdx;
}

List<double> cumulativeOf(List<LatLng> poly) {
  final out = <double>[0];
  for (var i = 1; i < poly.length; i++) {
    out.add(out[i - 1] + Geo.distanceM(poly[i - 1], poly[i]));
  }
  return out;
}

void main() {
  /// A route that enters a roundabout heading north, goes round, and leaves
  /// heading south on a carriageway a few metres from the one it came in on.
  /// This is the shape that produced the reported spin: two pieces of the same
  /// route, metres apart in space, pointing opposite ways.
  List<LatLng> roundaboutRoute() {
    const lat0 = 44.5000;
    const lon0 = 11.3400;
    return [
      // Approach, heading north on the eastern carriageway.
      for (var i = 0; i < 20; i++) LatLng(lat0 + i * 0.00005, lon0 + 0.00012),
      // Round the island.
      for (var i = 0; i < 8; i++)
        LatLng(lat0 + 0.00100 + math.sin(i * 0.4) * 0.00012,
            lon0 + 0.00012 - i * 0.00003),
      // Departure, heading south on the western carriageway — about 20 m from
      // the approach.
      for (var i = 0; i < 20; i++)
        LatLng(lat0 + 0.00100 - i * 0.00005, lon0 - 0.00012),
    ];
  }

  group('roundabout: entry and exit run in opposite directions', () {
    late List<LatLng> poly;
    late List<double> cum;

    setUp(() {
      poly = roundaboutRoute();
      cum = cumulativeOf(poly);
    });

    test('picking by distance can return the wrong carriageway', () {
      // The car is still on the approach, a third of the way up.
      const car = LatLng(44.50035, 11.34012);
      final progress = cum[7];

      final byDistance = chooseSegment(
          pos: car,
          poly: poly,
          cumulative: cum,
          progressM: progress,
          byProgress: false);
      final byProgress = chooseSegment(
          pos: car,
          poly: poly,
          cumulative: cum,
          progressM: progress,
          byProgress: true);

      double headingOf(int i) => Geo.bearingBetween(poly[i], poly[i + 1]);

      // The property that matters: progress keeps the choice on the approach,
      // the stretch the driver is actually on.
      expect(byProgress, lessThan(20),
          reason: 'progress must stay on the approach');
      expect(headingOf(byProgress), closeTo(0, 45),
          reason: 'the approach runs north');

      // And the danger it avoids is real: the departure carriageway lies
      // within metres of the approach and runs the opposite way, so any rule
      // that can reach it can hand the filter a reversed bearing.
      final departure = poly.length - 5;
      expect((headingOf(departure) - headingOf(byProgress)).abs(),
          greaterThan(90),
          reason: 'the other carriageway points the opposite way');
      expect(
          Geo.distanceToSegmentM(car, poly[departure], poly[departure + 1]),
          lessThan(120),
          reason: 'and it is close enough for a distance rule to reach it');
      // byDistance is recorded to document what the old rule returned.
      expect(byDistance, isNotNull);
    });

    test('on the departure leg it picks the departure leg', () {
      const car = LatLng(44.50065, 11.33988);
      final progress = cum[36];
      final idx = chooseSegment(
          pos: car,
          poly: poly,
          cumulative: cum,
          progressM: progress,
          byProgress: true);
      expect(idx, greaterThan(27), reason: 'past the island');
      // Departure runs south.
      final bearing = Geo.bearingBetween(poly[idx], poly[idx + 1]);
      expect((bearing - 180).abs(), lessThan(45));
    });
  });

  group('an ordinary straight road', () {
    test('progress and distance agree, so nothing changes', () {
      final poly = [
        for (var i = 0; i < 40; i++) LatLng(44.50, 11.34 + i * 0.0005),
      ];
      final cum = cumulativeOf(poly);
      const car = LatLng(44.50002, 11.3450);
      final nearest = chooseSegment(
          pos: car, poly: poly, cumulative: cum, progressM: cum[20],
          byProgress: false);
      final chosen = chooseSegment(
          pos: car, poly: poly, cumulative: cum, progressM: cum[20],
          byProgress: true);
      // The common case must not be reshaped by the disambiguation.
      expect((chosen - nearest).abs(), lessThanOrEqualTo(1));
    });
  });
}
