import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/navigation_guidance.dart';

void main() {
  group('speed-sensitive driving announcements', () {
    test('starts at 150 m below urban-road speed', () {
      expect(
        NavigationGuidance.thresholds(40, 'driving'),
        (far: 150, near: 40),
      );
    });

    test('reaches half a mile at motorway speed', () {
      expect(
        NavigationGuidance.thresholds(100, 'driving'),
        (far: 800, near: 120),
      );
      // far caps at 100 km/h — 800 m of advance warning does not need to grow
      // further. near keeps scaling past it: 120 m is 3.6 s at 130 km/h,
      // enough to hear the cue but not enough to act on it.
      expect(
        NavigationGuidance.thresholds(130, 'driving'),
        (far: 800, near: 190),
      );
    });

    test('interpolates continuously instead of jumping between presets', () {
      final at60 = NavigationGuidance.thresholds(60, 'driving');
      final at80 = NavigationGuidance.thresholds(80, 'driving');
      expect(at60.far, greaterThan(150));
      expect(at80.far, greaterThan(at60.far));
      expect(at80.far, lessThan(800));
      expect(at80.near, greaterThan(at60.near));
    });

    test('keeps walking and cycling cues compact', () {
      expect(
        NavigationGuidance.thresholds(5, 'walking'),
        (far: 60, near: 15),
      );
      expect(
        NavigationGuidance.thresholds(25, 'cycling'),
        (far: 150, near: 30),
      );
    });
  });

  group('spoken distance', () {
    test('reports how far it is, never the window that triggered it', () {
      // The regression this guards: an announcement let through by an 800 m
      // motorway window, fired 200 m from the ramp, saying "in 800 metres".
      expect(NavigationGuidance.spokenDistanceM(200, imminentBelowM: 120), 200);
      expect(NavigationGuidance.spokenDistanceM(780, imminentBelowM: 120), 800);
    });

    test('rounds to 50 m, the way people give directions', () {
      expect(NavigationGuidance.spokenDistanceM(347, imminentBelowM: 80), 350);
      expect(NavigationGuidance.spokenDistanceM(324, imminentBelowM: 80), 300);
    });

    test('drops the distance once the maneuver is imminent', () {
      expect(NavigationGuidance.spokenDistanceM(80, imminentBelowM: 80), 0);
      expect(NavigationGuidance.spokenDistanceM(12, imminentBelowM: 80), 0);
      expect(NavigationGuidance.spokenDistanceM(0, imminentBelowM: 80), 0);
    });

    test('a non-finite distance is never spoken as a number', () {
      expect(
          NavigationGuidance.spokenDistanceM(double.nan, imminentBelowM: 80), 0);
      expect(
          NavigationGuidance.spokenDistanceM(double.infinity,
              imminentBelowM: 80),
          0);
    });
  });

  group('chained tail', () {
    test('a follow-up further than a few metres is announced with distance',
        () {
      // The regression this guards: chaining used to require the next maneuver
      // to be within 55 m, so anything beyond that appeared on screen and was
      // never spoken at this point at all.
      expect(
        NavigationGuidance.chainedTail(followDirection: 'off ramp', gapM: 300),
        ChainedTail.maneuverWithDistance,
      );
      expect(
        NavigationGuidance.chainedTail(followDirection: 'turn', gapM: 3000),
        ChainedTail.maneuverWithDistance,
      );
    });

    test('a back-to-back follow-up drops the distance', () {
      // "…take the first exit, then in 20 metres turn right" is noise.
      expect(
        NavigationGuidance.chainedTail(followDirection: 'turn', gapM: 20),
        ChainedTail.maneuver,
      );
    });

    test('the destination is only named once it is close', () {
      expect(
        NavigationGuidance.chainedTail(followDirection: 'arrive', gapM: 2000),
        ChainedTail.arrival,
      );
      expect(
        NavigationGuidance.chainedTail(
            followDirection: 'arrive',
            gapM: NavigationGuidance.arrivalChainWithinM),
        ChainedTail.arrival,
      );
    });

    test('a distant destination becomes "continue for X" instead', () {
      // Announcing arrival with eleven miles still to drive is what a field
      // report caught; the driver is told how far the road runs instead.
      expect(
        NavigationGuidance.chainedTail(followDirection: 'arrive', gapM: 17000),
        ChainedTail.continueAhead,
      );
      expect(
        NavigationGuidance.chainedTail(followDirection: 'arrive', gapM: 3001),
        ChainedTail.continueAhead,
      );
    });

    test('departure is never a follow-up, and garbage is never spoken', () {
      expect(
        NavigationGuidance.chainedTail(followDirection: 'depart', gapM: 100),
        ChainedTail.none,
      );
      expect(
        NavigationGuidance.chainedTail(
            followDirection: 'turn', gapM: double.nan),
        ChainedTail.none,
      );
      expect(
        NavigationGuidance.chainedTail(followDirection: 'turn', gapM: -5),
        ChainedTail.none,
      );
    });
  });

  test('maneuver distance follows the route rather than a straight chord', () {
    expect(
      NavigationGuidance.remainingToManeuver(
        maneuverProgressM: 1350,
        routeProgressM: 550,
      ),
      800,
    );
    expect(
      NavigationGuidance.remainingToManeuver(
        maneuverProgressM: 500,
        routeProgressM: 550,
      ),
      0,
    );
  });
}
