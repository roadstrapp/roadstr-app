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
      expect(
        NavigationGuidance.thresholds(130, 'driving'),
        (far: 800, near: 120),
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
