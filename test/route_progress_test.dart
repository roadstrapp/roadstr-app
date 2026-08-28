import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/route_progress.dart';

/// One metre of latitude, near enough for these fixtures.
const _m = 1 / 111320.0;

LatLng _north(LatLng from, double metres) =>
    LatLng(from.latitude + metres * _m, from.longitude);

void main() {
  const start = LatLng(45.0, 9.0);
  final straightLine = [
    start,
    _north(start, 100),
    _north(start, 200),
    _north(start, 300),
  ];

  group('cumulativeDistances', () {
    test('starts at zero and accumulates each segment', () {
      final cum = RouteProgress.cumulativeDistances(straightLine);
      expect(cum[0], 0);
      expect(cum[1], closeTo(100, 0.5));
      expect(cum[2], closeTo(200, 0.5));
      expect(cum[3], closeTo(300, 0.5));
    });

    test('a single-point polyline has one zero entry', () {
      expect(RouteProgress.cumulativeDistances([start]), [0]);
    });
  });

  group('nearestIndex', () {
    test('finds the closest vertex, not just the first or last', () {
      final idx = RouteProgress.nearestIndex(straightLine, _north(start, 195));
      expect(idx, 2); // the 200 m point, closer than the 100 m or 300 m ones
    });

    test('a position exactly on a vertex returns that vertex', () {
      expect(RouteProgress.nearestIndex(straightLine, _north(start, 100)), 1);
    });

    test('off to the side still resolves to the nearest vertex along the line',
        () {
      // 5 m east of the 200 m point — still much closer to index 2 than
      // to its neighbours 100 m away along the line.
      final off = LatLng(_north(start, 200).latitude, 9.0 + 5 * _m);
      expect(RouteProgress.nearestIndex(straightLine, off), 2);
    });
  });
}
