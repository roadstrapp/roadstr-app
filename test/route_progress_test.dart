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

    test('an empty polyline answers 0, which is NOT a valid index into it — '
        'callers must check the polyline before indexing with this', () {
      // Pinned deliberately. This is a trap, not a feature: a degenerate
      // route reaching a caller that indexes blindly with this result is
      // what crashed the app mid-drive once, and the guards added since
      // (in _updateNavigationProgress, _updateRouteProgress, _stepProgressM)
      // exist because of it. If this contract is ever changed, those guards
      // must be revisited rather than quietly left behind.
      expect(RouteProgress.nearestIndex(const [], start), 0);
      expect(RouteProgress.cumulativeDistances(const []), isEmpty);
    });
  });

  // A long straight route, a vertex every 10 m, 5 km in all.
  final longRoute = [for (var i = 0; i <= 500; i++) _north(start, i * 10.0)];

  group('nearestIndexNear', () {
    test('agrees with the full scan wherever the driver actually is', () {
      // Every position along the route, hint always the previous answer — the
      // way the navigation loop uses it.
      var hint = 0;
      for (var metres = 0.0; metres <= 5000; metres += 7) {
        final position = _north(start, metres);
        final near =
            RouteProgress.nearestIndexNear(longRoute, position, hint: hint);
        expect(near, RouteProgress.nearestIndex(longRoute, position),
            reason: 'at ${metres.round()} m');
        hint = near;
      }
    });

    test('a jump far outside the window still finds the true nearest', () {
      // A fix after a long tunnel, or the polyline replaced by a reroute: the
      // window around the old hint holds nothing near the new position.
      final position = _north(start, 4000);
      expect(RouteProgress.nearestIndexNear(longRoute, position, hint: 5),
          400);
    });

    test('a hint past the end of a shorter, replacement route is harmless',
        () {
      final short = longRoute.sublist(0, 20);
      final idx = RouteProgress.nearestIndexNear(short, _north(start, 100),
          hint: 450);
      expect(idx, 10);
    });

    test('an empty polyline answers 0, like the full scan', () {
      expect(RouteProgress.nearestIndexNear(const [], start, hint: 0), 0);
    });

    test('on a route that doubles back it stays on the pass being driven', () {
      // North 1 km, then straight back south over the same road: every
      // vertex from the outbound leg appears again, at the same coordinates.
      final out = [for (var i = 0; i <= 100; i++) _north(start, i * 10.0)];
      final there = [...out, ...out.reversed.skip(1)];
      final position = _north(start, 500); // 500 m from the start either way

      // The full scan can only ever return the first occurrence …
      expect(RouteProgress.nearestIndex(there, position), 50);
      // … while a driver on the way back, hint near the return leg, is on the
      // second: index 150 (100 out + 50 back).
      expect(RouteProgress.nearestIndexNear(there, position, hint: 148), 150);
      // And on the way out it stays on the first.
      expect(RouteProgress.nearestIndexNear(there, position, hint: 48), 50);
    });
  });

  group('nearestIndicesAlong', () {
    test('matches a full scan per point on an ordinary route', () {
      final points = [
        longRoute[0],
        longRoute[120],
        longRoute[300],
        longRoute[500],
      ];
      expect(RouteProgress.nearestIndicesAlong(longRoute, points),
          [for (final p in points) RouteProgress.nearestIndex(longRoute, p)]);
    });

    test('a route that loops back to its start puts the last point at the end',
        () {
      // Out and back: the arrival point is the same coordinate as the
      // departure. A full scan puts it at index 0, so the "arrive" step would
      // claim to be at the very start of the route. In route order it is last.
      final out = [for (var i = 0; i <= 100; i++) _north(start, i * 10.0)];
      final loop = [...out, ...out.reversed.skip(1)];
      final steps = [loop.first, loop[100], loop.last];

      expect(RouteProgress.nearestIndex(loop, steps.last), 0,
          reason: 'the trap this avoids');
      expect(RouteProgress.nearestIndicesAlong(loop, steps),
          [0, 100, loop.length - 1]);
    });

    test('a point that is not on the route falls back rather than guessing',
        () {
      final far = LatLng(start.latitude + 2000 * _m, start.longitude);
      final result = RouteProgress.nearestIndicesAlong(
          longRoute, [longRoute[10], far, longRoute[400]]);
      expect(result[0], 10);
      expect(result[1], 200);
      expect(result[2], 400);
    });

    test('no points, or no route, is an empty or zero answer, never a throw',
        () {
      expect(RouteProgress.nearestIndicesAlong(longRoute, const []), isEmpty);
      expect(RouteProgress.nearestIndicesAlong(const [], [start]), [0]);
    });
  });
}
