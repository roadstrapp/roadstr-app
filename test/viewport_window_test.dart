import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/utils/viewport_window.dart';

/// A plain pinhole camera, written from scratch here rather than derived from
/// the same formulas as the code under test — so agreement means something.
///
/// Ground units are "ground pixels" (one unit = the ground one screen pixel
/// covers at the map centre). The camera is `1.5 × H` from the centre along
/// its view axis, tilted [pitchDeg] from straight down, with the focal length
/// MapLibre's fixed 36.87° vertical field of view implies (also `1.5 × H`).
/// For a ground point `(x, y)` — x to the right, y ahead of the centre — the
/// depth along the view axis works out to `D + y·sin(p)`, giving screen
/// coordinates `(D·x / depth, D·y·cos(p) / depth)` from the screen centre.
({double sx, double sy})? project({
  required double x,
  required double y,
  required double pitchDeg,
  required double heightDp,
}) {
  final p = pitchDeg * math.pi / 180;
  final d = 1.5 * heightDp;
  final depth = d + y * math.sin(p);
  if (depth <= 0) return null;
  return (sx: d * x / depth, sy: d * y * math.cos(p) / depth);
}

bool onScreen(double x, double y,
    {required double pitchDeg,
    required double widthDp,
    required double heightDp}) {
  final s = project(x: x, y: y, pitchDeg: pitchDeg, heightDp: heightDp);
  if (s == null) return false;
  return s.sx.abs() <= widthDp / 2 && s.sy.abs() <= heightDp / 2;
}

void main() {
  const widthDp = 392.0;
  const heightDp = 800.0;

  group('ground extents against an independent pinhole projection', () {
    for (final pitch in [0.0, 20.0, 40.0, 55.0, 60.0]) {
      final e = ViewportWindow.groundExtentsDp(
          pitchDeg: pitch, screenWidthDp: widthDp, screenHeightDp: heightDp);

      test('pitch $pitch°: nothing on screen is ever outside the window', () {
        // A dense grid comfortably wider and deeper than the screen can show.
        var visible = 0;
        for (var y = -3 * heightDp; y <= 4 * heightDp; y += 6) {
          for (var x = -2 * widthDp; x <= 2 * widthDp; x += 6) {
            if (!onScreen(x, y,
                pitchDeg: pitch, widthDp: widthDp, heightDp: heightDp)) {
              continue;
            }
            visible++;
            expect(y, lessThanOrEqualTo(e.forward),
                reason: '($x, $y) is on screen but beyond the far edge');
            expect(y, greaterThanOrEqualTo(-e.backward),
                reason: '($x, $y) is on screen but behind the near edge');
            expect(x.abs(), lessThanOrEqualTo(e.halfWidth),
                reason: '($x, $y) is on screen but outside the width');
          }
        }
        expect(visible, greaterThan(1000), reason: 'the sampling saw nothing');
      });

      test('pitch $pitch°: and the window is not absurdly bigger than the view',
          () {
        // Soundness alone is trivially satisfied by a huge rectangle, which
        // would cull nothing. Measure the actual visible ground area on the
        // same grid and require the window to stay within a small multiple.
        const step = 4.0;
        var visibleCells = 0;
        for (var y = -3 * heightDp; y <= 4 * heightDp; y += step) {
          for (var x = -2 * widthDp; x <= 2 * widthDp; x += step) {
            if (onScreen(x, y,
                pitchDeg: pitch, widthDp: widthDp, heightDp: heightDp)) {
              visibleCells++;
            }
          }
        }
        final visibleArea = visibleCells * step * step;
        final windowArea = 2 * e.halfWidth * (e.forward + e.backward);
        expect(windowArea / visibleArea, lessThan(2.6),
            reason: 'window ${windowArea.round()} vs visible '
                '${visibleArea.round()}');
      });
    }

    test('tilting the camera reaches further ahead than behind', () {
      final flat = ViewportWindow.groundExtentsDp(
          pitchDeg: 0, screenWidthDp: widthDp, screenHeightDp: heightDp);
      final tilted = ViewportWindow.groundExtentsDp(
          pitchDeg: 55, screenWidthDp: widthDp, screenHeightDp: heightDp);
      expect((flat.forward - flat.backward).abs(), lessThan(1e-6));
      expect(tilted.forward, greaterThan(tilted.backward * 2));
      expect(tilted.forward, greaterThan(flat.forward));
    });

    test('a pitch beyond what MapLibre allows is clamped, not extrapolated', () {
      final max = ViewportWindow.groundExtentsDp(
          pitchDeg: 60, screenWidthDp: widthDp, screenHeightDp: heightDp);
      final beyond = ViewportWindow.groundExtentsDp(
          pitchDeg: 89, screenWidthDp: widthDp, screenHeightDp: heightDp);
      expect(beyond.forward, max.forward);
      expect(beyond.forward.isFinite, isTrue);
    });
  });

  group('ViewportWindow', () {
    // Zoom 17 at latitude 45°, phone-sized screen, driving pitch.
    ViewportWindow at({
      double bearing = 0,
      double pitch = 55,
      double zoom = 17,
      double margin = 1.0,
      double extra = 0,
    }) =>
        ViewportWindow(
          centerLat: 45,
          centerLng: 9,
          zoom: zoom,
          bearingDeg: bearing,
          pitchDeg: pitch,
          screenWidthDp: widthDp,
          screenHeightDp: heightDp,
          margin: margin,
          extraMetres: extra,
        );

    // Metres → degrees at this latitude, for placing test points.
    const mPerDegLat = 111320.0;
    final mPerDegLng = mPerDegLat * math.cos(45 * math.pi / 180);
    ({double lat, double lng}) offset({double north = 0, double east = 0}) =>
        (lat: 45 + north / mPerDegLat, lng: 9 + east / mPerDegLng);

    test('the centre is always inside', () {
      final w = at();
      expect(w.contains(45, 9), isTrue);
    });

    test('what is ahead is inside, and much further ahead is not', () {
      final w = at();
      final near = offset(north: w.forwardMetres * 0.9);
      final far = offset(north: w.forwardMetres * 1.1);
      expect(w.contains(near.lat, near.lng), isTrue);
      expect(w.contains(far.lat, far.lng), isFalse);
    });

    test('behind reaches less far than ahead when tilted', () {
      final w = at();
      final justBehind = offset(north: -w.backwardMetres * 0.9);
      final wayBehind = offset(north: -w.backwardMetres * 1.1);
      expect(w.contains(justBehind.lat, justBehind.lng), isTrue);
      expect(w.contains(wayBehind.lat, wayBehind.lng), isFalse);
      expect(w.backwardMetres, lessThan(w.forwardMetres));
    });

    test('sideways is narrower than ahead on a portrait screen', () {
      final w = at();
      final wide = offset(east: w.halfWidthMetres * 1.1);
      expect(w.contains(wide.lat, wide.lng), isFalse);
      expect(w.halfWidthMetres, lessThan(w.forwardMetres));
    });

    test('the window turns with the camera', () {
      // Facing east, "ahead" is east: a point due east well inside the forward
      // reach is in, and the same distance due north — now off to the side —
      // is out. Facing north it is the other way round.
      final distance = at().halfWidthMetres * 2;
      final east = offset(east: distance);
      final north = offset(north: distance);

      final facingNorth = at(bearing: 0);
      expect(facingNorth.contains(north.lat, north.lng), isTrue);
      expect(facingNorth.contains(east.lat, east.lng), isFalse);

      final facingEast = at(bearing: 90);
      expect(facingEast.contains(east.lat, east.lng), isTrue);
      expect(facingEast.contains(north.lat, north.lng), isFalse);
    });

    test('zooming out widens the window with the ground it shows', () {
      final close = at(zoom: 17);
      final out = at(zoom: 15);
      // Two levels out is four times the ground per pixel.
      expect(out.forwardMetres / close.forwardMetres, closeTo(4, 0.05));
      expect(out.halfWidthMetres / close.halfWidthMetres, closeTo(4, 0.05));
    });

    test('margin and extra grow every side', () {
      final tight = at(margin: 1, extra: 0);
      final loose = at(margin: 1.5, extra: 100);
      expect(loose.forwardMetres, greaterThan(tight.forwardMetres));
      expect(loose.backwardMetres, greaterThan(tight.backwardMetres));
      expect(loose.halfWidthMetres, greaterThan(tight.halfWidthMetres));
      expect(loose.halfWidthMetres,
          closeTo(tight.halfWidthMetres * 1.5 + 100, 1e-6));
    });

    test('in a city the window keeps a few dozen of a thousand markers', () {
      // The point of it: ~1500 points scattered over a 1.5 km radius, as the
      // crosswalk cache holds in a built-up area, and a camera in the middle
      // driving north at zoom 17.
      final w = ViewportWindow(
        centerLat: 45,
        centerLng: 9,
        zoom: 17,
        bearingDeg: 0,
        pitchDeg: 55,
        screenWidthDp: widthDp,
        screenHeightDp: heightDp,
      );
      final rng = math.Random(7);
      var kept = 0;
      const total = 1500;
      for (var i = 0; i < total; i++) {
        final r = 1500 * math.sqrt(rng.nextDouble());
        final a = rng.nextDouble() * 2 * math.pi;
        final p = offset(north: r * math.cos(a), east: r * math.sin(a));
        if (w.contains(p.lat, p.lng)) kept++;
      }
      expect(kept, lessThan(total * 0.15),
          reason: 'kept $kept of $total — barely culling anything');
      expect(kept, greaterThan(0));
    });
  });
}
