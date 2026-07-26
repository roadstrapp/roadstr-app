import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/utils/heading_filter.dart';

/// One metre of latitude, near enough for these fixtures.
const _m = 1 / 111320.0;

LatLng north(LatLng from, double metres) =>
    LatLng(from.latitude + metres * _m, from.longitude);

void main() {
  const start = LatLng(45.0703, 7.6869);

  double resolve(
    HeadingFilter filter, {
    required double current,
    LatLng? from,
    required LatLng to,
    double speedKmh = 50,
    double accuracyM = 5,
    double? providerHeading,
    bool navigating = true,
    ({double distM, double bearing})? routeLocal,
  }) =>
      filter.resolve(
        current: current,
        from: from,
        to: to,
        speedKmh: speedKmh,
        accuracyM: accuracyM,
        providerHeading: providerHeading,
        navigating: navigating,
        routeLocalBearingAt: routeLocal == null ? null : (_) => routeLocal,
      );

  test('below walking pace the provider heading is used', () {
    final filter = HeadingFilter();
    expect(
      resolve(filter,
          current: 90,
          from: start,
          to: north(start, 50),
          speedKmh: 1,
          providerHeading: 42),
      42,
    );
  });

  test('a movement smaller than the noise floor is ignored', () {
    final filter = HeadingFilter();
    // 4 m of movement with 20 m accuracy: the floor is 16 m.
    expect(
      resolve(filter,
          current: 90,
          from: start,
          to: north(start, 4),
          accuracyM: 20,
          providerHeading: null),
      90,
    );
  });

  test('driving north sets the heading to north', () {
    final filter = HeadingFilter();
    expect(
      resolve(filter, current: 350, from: start, to: north(start, 40)),
      closeTo(0, 0.5),
    );
  });

  test('outside navigation even a reversal is taken at once', () {
    final filter = HeadingFilter();
    expect(
      resolve(filter,
          current: 180, from: start, to: north(start, 40), navigating: false),
      closeTo(0, 0.5),
    );
  });

  group('while navigating', () {
    test('a single reversed fix does not flip the map', () {
      final filter = HeadingFilter();
      // Heading south, one fix jumps 40 m north: a bearing 180° out.
      expect(
        resolve(filter, current: 180, from: start, to: north(start, 40)),
        180,
      );
    });

    test('but a second, agreeing one does — that is a U-turn', () {
      final filter = HeadingFilter();
      resolve(filter, current: 180, from: start, to: north(start, 40));
      expect(
        resolve(filter,
            current: 180, from: north(start, 40), to: north(start, 80)),
        closeTo(0, 0.5),
      );
    });

    test('a reversal against the route is held: road-snap, not a U-turn', () {
      final filter = HeadingFilter();
      // The route runs south here, and the fix is on it, so a northward
      // bearing is the fused provider snapping to the other carriageway.
      for (var i = 0; i < 4; i++) {
        expect(
          resolve(filter,
              current: 180,
              from: start,
              to: north(start, 40),
              routeLocal: (distM: 5, bearing: 180)),
          180,
          reason: 'sample ${i + 1} must not flip the map',
        );
      }
    });

    test('and released after five, so the heading can never lock up', () {
      // At crawling speed the route-tangent easing is off (see _towardRoute),
      // which is where the safety valve is actually observable: five samples
      // are vetoed, then the ordinary two-sample rule takes over again.
      final filter = HeadingFilter();
      final held = [
        for (var i = 0; i < 6; i++)
          resolve(filter,
              current: 180,
              from: start,
              to: north(start, 40),
              speedKmh: 4,
              routeLocal: (distM: 5, bearing: 180)),
      ];
      expect(held, everyElement(180));
      expect(
        resolve(filter,
            current: 180,
            from: north(start, 40),
            to: north(start, 80),
            speedKmh: 4,
            routeLocal: (distM: 5, bearing: 180)),
        closeTo(0, 0.5),
      );
    });

    test('on the route above walking pace the route wins over a held flip', () {
      // The companion of the test above: while the easing stage is active it
      // pins the heading to the route regardless, so a wrong reversal cannot
      // turn the map even once the veto has expired.
      final filter = HeadingFilter();
      double last = 180;
      for (var i = 0; i < 8; i++) {
        last = resolve(filter,
            current: 180,
            from: start,
            to: north(start, 40),
            routeLocal: (distM: 5, bearing: 180));
      }
      expect(last, 180);
    });

    test('a fix far off the route gets no route veto', () {
      final filter = HeadingFilter();
      // Same geometry as the road-snap case, but 200 m off the route: there is
      // nothing to contradict, so the ordinary two-sample rule applies.
      resolve(filter,
          current: 180,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 200, bearing: 180));
      expect(
        resolve(filter,
            current: 180,
            from: north(start, 40),
            to: north(start, 80),
            routeLocal: (distM: 200, bearing: 180)),
        closeTo(0, 0.5),
      );
    });

    test('a heading near the route is eased toward it, not snapped', () {
      final filter = HeadingFilter();
      // Bearing 0 (north), route says 20°: 35 % of the 20° gap is taken.
      final resolved = resolve(filter,
          current: 5,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 5, bearing: 20));
      expect(resolved, closeTo(7, 0.5));
    });

    test('a heading wildly off the route is replaced by it', () {
      final filter = HeadingFilter();
      final resolved = resolve(filter,
          current: 350,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 5, bearing: 150));
      expect(resolved, 150);
    });

    test('reset forgets a pending reversal', () {
      final filter = HeadingFilter();
      resolve(filter, current: 180, from: start, to: north(start, 40));
      filter.reset();
      // Without the remembered first sample this is a first sample again.
      expect(
        resolve(filter,
            current: 180, from: north(start, 40), to: north(start, 80)),
        180,
      );
    });
  });
}
