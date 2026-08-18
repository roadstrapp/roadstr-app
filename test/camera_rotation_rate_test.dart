import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';

/// The map's self-driven rotation is rate-limited as well as eased. These
/// tests pin the arithmetic of that limiter, because the failure it prevents
/// is invisible in a widget test and only shows up as a jolt on the road.
///
/// Mirrors the step computed inside the follow ticker in map_screen.dart:
/// exponential easing toward the target, then a hard cap on how much of it may
/// be applied in one frame.
double rotationStep({
  required double fromRot,
  required double targetRot,
  required int dtMs,
  double tauMs = 350.0,
  double maxDegPerSec = 90.0,
}) {
  var delta = targetRot - fromRot;
  while (delta > 180) {
    delta -= 360;
  }
  while (delta < -180) {
    delta += 360;
  }
  final t = 1 - math.exp(-dtMs / tauMs);
  var step = delta * t;
  final maxStep = maxDegPerSec * dtMs / 1000.0;
  if (step > maxStep) step = maxStep;
  if (step < -maxStep) step = -maxStep;
  return step;
}

/// Degrees per second the camera would sweep at this frame's rate.
double rateOf(double step, int dtMs) => step.abs() * 1000.0 / dtMs;

void main() {
  const frameMs = 16;

  group('rate cap', () {
    test('a large correction never exceeds the ceiling', () {
      // The case that produced the complaint: the heading filter withholds a
      // bearing for several fixes near a roundabout, then releases the whole
      // accumulated angle at once.
      for (final target in [120.0, 150.0, 179.0, -150.0]) {
        final step = rotationStep(fromRot: 0, targetRot: target, dtMs: frameMs);
        expect(rateOf(step, frameMs), lessThanOrEqualTo(90.0 + 0.001),
            reason: 'a $target° jump swept faster than the cap');
      }
    });

    test('without the cap the same jump is violent', () {
      // Establishes that the cap is doing real work rather than being
      // decorative: unbounded easing sweeps a 150° correction at roughly
      // 400°/s, which is what reads as the map spinning away.
      final uncapped = rotationStep(
          fromRot: 0, targetRot: 150, dtMs: frameMs, maxDegPerSec: 1e9);
      expect(rateOf(uncapped, frameMs), greaterThan(300.0));
    });

    test('the cap is independent of frame duration', () {
      // A slow frame must not be allowed to bank a bigger jump.
      for (final dt in [8, 16, 33, 100]) {
        final step = rotationStep(fromRot: 0, targetRot: 170, dtMs: dt);
        expect(rateOf(step, dt), lessThanOrEqualTo(90.0 + 0.001),
            reason: 'cap leaked at ${dt}ms frames');
      }
    });
  });

  group('ordinary motion is untouched', () {
    test('a real roundabout yaw rate stays below the cap', () {
      // ~30°/s is a tight roundabout. Per frame that is a small delta, so the
      // easing — not the cap — decides the step, and following genuine motion
      // is not slowed down.
      const perFrame = 30.0 * frameMs / 1000.0;
      final step =
          rotationStep(fromRot: 0, targetRot: perFrame, dtMs: frameMs);
      final capped = 90.0 * frameMs / 1000.0;
      expect(step.abs(), lessThan(capped),
          reason: 'the cap must not bind during real driving');
    });

    test('small corrections still resolve quickly', () {
      // A 5° adjustment is ~75% resolved within half a second (one and a half
      // time constants) and essentially complete within one and a half — fast
      // enough to track a drifting heading without the cap ever entering it.
      var rot = 0.0;
      for (var i = 0; i < 30; i++) {
        rot += rotationStep(fromRot: rot, targetRot: 5, dtMs: frameMs);
      }
      expect(rot, closeTo(3.7, 0.3), reason: '~480 ms in');

      for (var i = 0; i < 70; i++) {
        rot += rotationStep(fromRot: rot, targetRot: 5, dtMs: frameMs);
      }
      expect(rot, closeTo(5, 0.2), reason: '~1.6 s in');
    });
  });

  group('shortest arc', () {
    test('crossing north turns the short way', () {
      // 350° → 10° is a 20° turn, not a 340° one. Getting this wrong would
      // spin the map almost all the way around at the boundary.
      final step = rotationStep(fromRot: 350, targetRot: 10, dtMs: frameMs);
      expect(step, greaterThan(0), reason: 'must turn clockwise through north');

      final back = rotationStep(fromRot: 10, targetRot: 350, dtMs: frameMs);
      expect(back, lessThan(0), reason: 'and anticlockwise coming back');
    });

    test('converges across the boundary rather than unwinding', () {
      var rot = 350.0;
      for (var i = 0; i < 400; i++) {
        rot = (rot + rotationStep(fromRot: rot, targetRot: 10, dtMs: frameMs)) %
            360;
      }
      expect(rot % 360, closeTo(10, 0.5));
    });
  });
}
