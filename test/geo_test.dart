import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/utils/geo.dart';

/// Torino — the latitude the distortion below was measured at.
const _lat = 45.070;
const _lon = 12.199;

/// Degrees of latitude for [m] metres north.
double _north(double m) => m / Geo.metresPerDegree;

/// Degrees of longitude for [m] metres east at [_lat].
double _east(double m) =>
    m / (Geo.metresPerDegree * math.cos(_lat * math.pi / 180));

void main() {
  group('projectOnSegment', () {
    // The regression this file exists for: an earlier copy of this function
    // scaled the latitude difference by cos(latitude) instead of the longitude
    // one, so a driver drifting north of the route measured only ~72 % of the
    // real distance — the 40 m reroute threshold effectively became 56 m.
    test('a point due north measures its true distance', () {
      final a = LatLng(_lat, _lon);
      final b = LatLng(_lat, _lon + _east(200)); // segment running east
      final p = LatLng(_lat + _north(40), _lon + _east(100));
      expect(Geo.distanceToSegmentM(p, a, b), closeTo(40, 0.5));
    });

    test('a point due east of a north-south segment measures its true distance',
        () {
      final a = LatLng(_lat, _lon);
      final b = LatLng(_lat + _north(200), _lon); // segment running north
      final p = LatLng(_lat + _north(100), _lon + _east(40));
      expect(Geo.distanceToSegmentM(p, a, b), closeTo(40, 0.5));
    });

    test('north and east deviations of equal size measure equal', () {
      final a = LatLng(_lat, _lon);
      final b = LatLng(_lat, _lon + _east(200));
      final c = LatLng(_lat + _north(200), _lon);
      final northOff = Geo.distanceToSegmentM(
          LatLng(_lat + _north(40), _lon + _east(100)), a, b);
      final eastOff = Geo.distanceToSegmentM(
          LatLng(_lat + _north(100), _lon + _east(40)), a, c);
      expect(northOff, closeTo(eastOff, 0.5));
    });

    test('clamps to the segment ends instead of the infinite line', () {
      final a = LatLng(_lat, _lon);
      final b = LatLng(_lat, _lon + _east(100));
      // 50 m past b, along the same line
      final p = LatLng(_lat, _lon + _east(150));
      final r = Geo.projectOnSegment(p, a, b);
      expect(r.t, 1.0);
      expect(r.distM, closeTo(50, 0.5));
    });

    test('t reports the position along the segment', () {
      final a = LatLng(_lat, _lon);
      final b = LatLng(_lat, _lon + _east(100));
      expect(Geo.projectOnSegment(LatLng(_lat, _lon + _east(25)), a, b).t,
          closeTo(0.25, 0.01));
    });

    test('degenerate segment falls back to the distance from its point', () {
      final a = LatLng(_lat, _lon);
      final r = Geo.projectOnSegment(LatLng(_lat + _north(30), _lon), a, a);
      expect(r.t, 0.0);
      expect(r.distM, closeTo(30, 0.5));
    });
  });

  group('distanceToPolylineM', () {
    test('takes the closest segment', () {
      final poly = [
        LatLng(_lat, _lon),
        LatLng(_lat, _lon + _east(100)),
        LatLng(_lat + _north(100), _lon + _east(100)),
      ];
      expect(
          Geo.distanceToPolylineM(
              LatLng(_lat + _north(50), _lon + _east(90)), poly),
          closeTo(10, 0.5));
    });

    test('a polyline with fewer than two points has no distance', () {
      expect(Geo.distanceToPolylineM(LatLng(_lat, _lon), []),
          double.infinity);
      expect(Geo.distanceToPolylineM(LatLng(_lat, _lon), [LatLng(_lat, _lon)]),
          double.infinity);
    });
  });

  group('pointInPolygon', () {
    final square = [
      LatLng(_lat, _lon),
      LatLng(_lat, _lon + _east(100)),
      LatLng(_lat + _north(100), _lon + _east(100)),
      LatLng(_lat + _north(100), _lon),
    ];

    test('inside and outside', () {
      expect(
          Geo.pointInPolygon(
              LatLng(_lat + _north(50), _lon + _east(50)), square),
          isTrue);
      expect(
          Geo.pointInPolygon(
              LatLng(_lat + _north(50), _lon + _east(150)), square),
          isFalse);
    });

    test('a degenerate ring contains nothing', () {
      expect(Geo.pointInPolygon(LatLng(_lat, _lon), []), isFalse);
      expect(
          Geo.pointInPolygon(
              LatLng(_lat, _lon), [LatLng(_lat, _lon), LatLng(_lat, _lon)]),
          isFalse);
    });
  });

  group('bearingBetween', () {
    test('cardinal directions', () {
      final o = LatLng(_lat, _lon);
      expect(Geo.bearingBetween(o, LatLng(_lat + _north(100), _lon)),
          closeTo(0, 0.5));
      expect(Geo.bearingBetween(o, LatLng(_lat, _lon + _east(100))),
          closeTo(90, 0.5));
      expect(Geo.bearingBetween(o, LatLng(_lat - _north(100), _lon)),
          closeTo(180, 0.5));
      expect(Geo.bearingBetween(o, LatLng(_lat, _lon - _east(100))),
          closeTo(270, 0.5));
    });

    test('always normalised to 0..360', () {
      final o = LatLng(_lat, _lon);
      for (final p in [
        LatLng(_lat + _north(50), _lon - _east(50)),
        LatLng(_lat - _north(50), _lon - _east(50)),
      ]) {
        final b = Geo.bearingBetween(o, p);
        expect(b, greaterThanOrEqualTo(0));
        expect(b, lessThan(360));
      }
    });
  });
}
