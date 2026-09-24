import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/camera_follow.dart';

CameraFollowState _s(
        {double lat = 45, double lng = 9, double zoom = 17, double rot = 0}) =>
    CameraFollowState(lat: lat, lng: lng, zoom: zoom, rotDeg: rot);

void main() {
  group('CameraFollowEasing.step', () {
    test('eases a small rotation smoothly, not instantly', () {
      final result = CameraFollowEasing.step(
          from: _s(rot: 0), target: _s(rot: 10), dtMs: 16);
      expect(result, isNotNull);
      expect(result!.rotDeg, greaterThan(0));
      expect(result.rotDeg, lessThan(10));
    });

    test('caps angular velocity on a large correction', () {
      // A 150° jump — what a heading filter correction near a roundabout can
      // produce — must not appear in one 16 ms frame.
      final result = CameraFollowEasing.step(
          from: _s(rot: 0), target: _s(rot: 150), dtMs: 16);
      final maxPerFrame = CameraFollowEasing.maxTurnDegPerSec * 16 / 1000.0;
      expect(result!.rotDeg, lessThanOrEqualTo(maxPerFrame + 1e-9));
    });

    test('does not cap genuine vehicle-speed turning', () {
      // ~30°/s, a tight roundabout — must pass through uncapped: the 90°/s
      // cap only ever bites above that, so the plain exponential-ease value
      // survives untouched.
      final result = CameraFollowEasing.step(
          from: _s(rot: 0), target: _s(rot: 30), dtMs: 1000);
      final eased = 30 * (1 - math.exp(-1000 / CameraFollowEasing.tauMs));
      expect(result!.rotDeg, closeTo(eased, 1e-9));
    });

    test('rotation wraps the short way across the 0/360 seam', () {
      final result = CameraFollowEasing.step(
          from: _s(rot: 350), target: _s(rot: 10), dtMs: 16);
      // Should move toward 360/0, not back down through 180.
      expect(result!.rotDeg, greaterThan(350));
    });

    test('zoom and position ease toward the target, never past it', () {
      final result = CameraFollowEasing.step(
          from: _s(lat: 45, lng: 9, zoom: 15),
          target: _s(lat: 45.01, lng: 9.01, zoom: 17),
          dtMs: 16);
      expect(result!.zoom, greaterThan(15));
      expect(result.zoom, lessThan(17));
      expect(result.lat, greaterThan(45));
      expect(result.lat, lessThan(45.01));
    });

    test('a huge dt (backgrounded app) does not produce garbage', () {
      final result = CameraFollowEasing.step(
          from: _s(rot: 0), target: _s(rot: 45), dtMs: 60000);
      expect(result, isNotNull);
      expect(result!.rotDeg.isFinite, isTrue);
    });
  });

  group('CameraFollowEasing.hasCaughtUp', () {
    test('true once close enough on every axis', () {
      expect(CameraFollowEasing.hasCaughtUp(_s(), _s()), isTrue);
    });

    test('false while any axis still differs meaningfully', () {
      expect(CameraFollowEasing.hasCaughtUp(_s(rot: 0), _s(rot: 1)), isFalse);
      expect(CameraFollowEasing.hasCaughtUp(_s(zoom: 17), _s(zoom: 17.1)),
          isFalse);
      expect(CameraFollowEasing.hasCaughtUp(_s(lat: 45), _s(lat: 45.001)),
          isFalse);
    });
  });

  group('CameraFollowEasing.navCameraCenter', () {
    test('shifts due north when heading is 0°', () {
      final (lat, lng) = CameraFollowEasing.navCameraCenter(45, 9, 0, 17);
      expect(lat, greaterThan(45));
      expect(lng, closeTo(9, 1e-9));
    });

    test('shifts due east when heading is 90°', () {
      final (lat, lng) = CameraFollowEasing.navCameraCenter(45, 9, 90, 17);
      expect(lat, closeTo(45, 1e-9));
      expect(lng, greaterThan(9));
    });

    test('a tighter zoom (more metres per pixel) shifts further', () {
      final (latFar, _) = CameraFollowEasing.navCameraCenter(45, 9, 0, 15);
      final (latNear, _) = CameraFollowEasing.navCameraCenter(45, 9, 0, 17);
      expect(latFar - 45, greaterThan(latNear - 45));
    });

    test('clamps latitude at the pole', () {
      final (lat, _) = CameraFollowEasing.navCameraCenter(89.9, 9, 0, 1);
      expect(lat, lessThanOrEqualTo(89.9));
      expect(lat.isFinite, isTrue);
    });

    test('steeper pitch compensates the shift so it does not shrink', () {
      final (latFlat, _) =
          CameraFollowEasing.navCameraCenter(45, 9, 0, 17, pitchDeg: 0);
      final (latTilted, _) =
          CameraFollowEasing.navCameraCenter(45, 9, 0, 17, pitchDeg: 60);
      expect(latTilted - 45, greaterThan(latFlat - 45));
    });
  });

  group('CameraFollowEasing.shiftByMetres', () {
    test('shifts due north when heading is 0°', () {
      final (lat, lng) = CameraFollowEasing.shiftByMetres(45, 9, 0, 100);
      expect(lat, greaterThan(45));
      expect(lng, closeTo(9, 1e-9));
    });

    test('a larger metre shift moves further', () {
      final (latNear, _) = CameraFollowEasing.shiftByMetres(45, 9, 0, 50);
      final (latFar, _) = CameraFollowEasing.shiftByMetres(45, 9, 0, 200);
      expect(latFar - 45, greaterThan(latNear - 45));
    });

    test('zero shift is a no-op', () {
      final (lat, lng) = CameraFollowEasing.shiftByMetres(45, 9, 37, 0);
      expect(lat, closeTo(45, 1e-9));
      expect(lng, closeTo(9, 1e-9));
    });
  });

  group('CameraFrameGate', () {
    // Metres of northward travel → degrees of latitude.
    double north(double metres) => 45 + metres / 111320;

    /// Drives the gate the way the follow ticker does: a frame every 33 ms
    /// for [seconds], the camera moving steadily north at [kmh], on a straight
    /// road. Returns how many of those frames were let through.
    int framesSent({required double kmh, double seconds = 10, double zoom = 17}) {
      final gate = CameraFrameGate();
      final metresPerSecond = kmh / 3.6;
      var sent = 0;
      for (var ms = 0; ms <= seconds * 1000; ms += 33) {
        final state = _s(lat: north(metresPerSecond * ms / 1000), zoom: zoom);
        if (gate.shouldSend(state, ms)) {
          gate.markSent(state, ms);
          sent++;
        }
      }
      return sent;
    }

    final totalFrames = (10 * 1000 / 33).floor() + 1;

    test('the very first frame always goes out', () {
      expect(CameraFrameGate().shouldSend(_s(), 0), isTrue);
    });

    test('a crawl is throttled hard — most frames would be the same picture',
        () {
      // 5 km/h: about 0.06 m a frame, a seventh of a pixel.
      final sent = framesSent(kmh: 5);
      expect(sent, lessThan(totalFrames * 0.35),
          reason: 'sent $sent of $totalFrames');
    });

    test('stop-and-go speed roughly halves the frames', () {
      final sent = framesSent(kmh: 20);
      expect(sent, lessThan(totalFrames * 0.75));
      expect(sent, greaterThan(totalFrames * 0.35));
    });

    test('at road speed every frame goes out — nothing is lost where it shows',
        () {
      // 50 km/h at zoom 17 is over a pixel a frame.
      expect(framesSent(kmh: 50), totalFrames);
      expect(framesSent(kmh: 120), totalFrames);
    });

    test('zoomed out, the same speed moves less on screen, so more is skipped',
        () {
      expect(framesSent(kmh: 50, zoom: 15),
          lessThan(framesSent(kmh: 50, zoom: 17)));
    });

    test('a slow crawl still advances steadily, not in long lurches', () {
      // 3 km/h: a frame is far below the threshold, but the gap limit must
      // keep it moving. Between two sends there may be at most maxGapMs.
      final gate = CameraFrameGate();
      final mps = 3 / 3.6;
      var lastSentMs = 0;
      var longest = 0;
      for (var ms = 0; ms <= 10000; ms += 33) {
        final state = _s(lat: north(mps * ms / 1000));
        if (gate.shouldSend(state, ms)) {
          longest = math.max(longest, ms - lastSentMs);
          lastSentMs = ms;
          gate.markSent(state, ms);
        }
      }
      expect(longest, lessThanOrEqualTo(gate.maxGapMs + 33));
    });

    test('turning is never throttled, even standing still', () {
      // 30°/s is about a degree a frame — ~7 dp at the screen edge.
      final gate = CameraFrameGate();
      var sent = 0;
      var frames = 0;
      for (var ms = 0; ms <= 3000; ms += 33) {
        final state = _s(rot: 30 * ms / 1000);
        frames++;
        if (gate.shouldSend(state, ms)) {
          gate.markSent(state, ms);
          sent++;
        }
      }
      expect(sent, frames);
    });

    test('a camera that has genuinely stopped is not re-sent every gap', () {
      final gate = CameraFrameGate();
      final still = _s();
      expect(gate.shouldSend(still, 0), isTrue);
      gate.markSent(still, 0);
      for (var ms = 33; ms <= 5000; ms += 33) {
        expect(gate.shouldSend(still, ms), isFalse);
      }
    });

    test('rotation is measured the short way round the compass', () {
      final gate = CameraFrameGate();
      // 359° → 1° is two degrees of turning, not 358.
      final small = gate.changeDp(_s(rot: 359), _s(rot: 1));
      final large = gate.changeDp(_s(rot: 0), _s(rot: 180));
      expect(small, lessThan(large / 50));
    });

    test('the last frame is worth sending only if it differs from the last', () {
      final gate = CameraFrameGate();
      final state = _s();
      // Nothing sent yet: anything is a change.
      expect(gate.hasVisibleChange(state), isTrue);
      gate.markSent(state, 0);
      // The camera already is where it is going: re-sending it is a redundant
      // native frame, which is what a parked phone did on every GPS fix.
      expect(gate.hasVisibleChange(state), isFalse);
      // Even a change too small for a normal frame counts for the last one —
      // the camera should not be left a fraction of a pixel short.
      expect(gate.hasVisibleChange(_s(lat: north(0.05))), isTrue);
    });

    test('reset makes the next frame go out whatever it is', () {
      final gate = CameraFrameGate();
      final state = _s();
      gate.markSent(state, 0);
      expect(gate.shouldSend(state, 10), isFalse);
      gate.reset();
      expect(gate.shouldSend(state, 20), isTrue);
    });
  });
}
