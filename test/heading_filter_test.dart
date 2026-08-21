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

  test('zero provider heading waits for a reliable movement baseline', () {
    final filter = HeadingFilter();
    expect(
      resolve(filter,
          current: 90,
          from: start,
          to: north(start, 2),
          speedKmh: 30,
          providerHeading: 0),
      90,
    );
  });

  test('travel-course threshold rejects non-finite and stationary speeds', () {
    expect(HeadingFilter.usesTravelHeading(double.nan), isFalse);
    expect(HeadingFilter.usesTravelHeading(3), isFalse);
    expect(HeadingFilter.usesTravelHeading(3.1), isTrue);
  });

  group('motion hysteresis', () {
    test('needs the enter threshold to start, the exit one to stop', () {
      final filter = HeadingFilter();
      expect(filter.isMoving, isFalse, reason: 'no sample yet');
      // Between the two thresholds and rising: still stationary. This is the
      // band a single threshold would flip-flop across.
      expect(filter.updateMotion(4), isFalse);
      expect(filter.updateMotion(HeadingFilter.movingEnterKmh), isFalse);
      expect(filter.updateMotion(5.5), isTrue);
      // Same band, now falling: stays moving. Crawling in traffic must not
      // hand the heading back to the magnetometer.
      expect(filter.updateMotion(4), isTrue);
      // Both thresholds are exclusive: sitting exactly on one is not a
      // crossing, so neither edge can chatter.
      expect(filter.updateMotion(HeadingFilter.movingExitKmh), isTrue);
      expect(filter.updateMotion(HeadingFilter.movingExitKmh - 0.1), isFalse);
    });

    test('a garbage speed sample is never movement', () {
      final filter = HeadingFilter();
      expect(filter.updateMotion(50), isTrue);
      expect(filter.updateMotion(double.nan), isFalse);
      expect(filter.updateMotion(50), isTrue);
      expect(filter.updateMotion(-1), isFalse);
    });

    test('reset keeps the motion state — it outlives a journey', () {
      final filter = HeadingFilter();
      expect(filter.updateMotion(50), isTrue);
      filter.reset();
      expect(filter.isMoving, isTrue);
    });
  });

  test('small fixes can accumulate against a retained bearing origin', () {
    final first = north(start, 4);
    final second = north(start, 9);
    expect(HeadingFilter.hasReliableMovement(start, first, 5), isFalse);
    expect(HeadingFilter.hasReliableMovement(start, second, 5), isTrue);
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

    test(
        'a single stray route bearing does not flip the heading — '
        'the regression this guards is a 180° map spin near a roundabout',
        () {
      // The bug: routeLocalBearingAt picks the nearest polyline *segment* by
      // raw distance, with no idea which way that road runs. Near a
      // roundabout or a junction, a different arm can be the nearest point
      // and report a bearing pointing the wrong way — reported live as the
      // map spinning 180° right where it matters most. One disagreeing
      // sample must not be enough to act on.
      final filter = HeadingFilter();
      final resolved = resolve(filter,
          current: 350,
          from: start,
          to: north(start, 40), // GPS bearing: 0° (already-trusted value)
          routeLocal: (distM: 5, bearing: 150)); // a stray, wrong-arm match
      expect(resolved, 0,
          reason: 'holds the GPS bearing instead of snapping to the glitch');
    });

    test('the same route bearing repeating is trusted on the second fix',
        () {
      // A genuine sharp turn keeps producing the same route bearing on
      // consecutive fixes; a stray nearest-segment match by a roundabout
      // ring does not. Two agreements in a row is what tells them apart.
      //
      // Trusted, not snapped to: the raw two-fix bearing that got here was
      // itself taken across the turn, so jumping straight to the route's
      // exact line the instant it corroborates would be a second
      // discontinuity on top of the first — the eased fraction is the same
      // one ordinary route-following uses.
      final filter = HeadingFilter();
      resolve(filter,
          current: 350,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 5, bearing: 150));
      final confirmed = resolve(filter,
          current: 0,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 5, bearing: 150));
      // heading 0 eased 35% of the way to 150.
      expect(confirmed, closeTo(52.5, 0.001));
    });

    test('a stray sample does not poison the next, unambiguous one', () {
      // After the glitch, ordinary route-following resumes normally — the
      // held state does not linger and distort an unrelated later fix.
      final filter = HeadingFilter();
      resolve(filter,
          current: 350,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 5, bearing: 150)); // stray, held
      final next = resolve(filter,
          current: 0,
          from: start,
          to: north(start, 40),
          routeLocal: (distM: 5, bearing: 20)); // ordinary, close disagreement
      // Eased toward 20°, not snapped to the earlier stray 150°.
      expect(next, closeTo(7, 0.5));
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
