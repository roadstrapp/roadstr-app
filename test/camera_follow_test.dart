import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/camera_follow.dart';

CameraFollowState _s({double lat = 45, double lng = 9, double zoom = 17, double rot = 0}) =>
    CameraFollowState(lat: lat, lng: lng, zoom: zoom, rotDeg: rot);

void main() {
  group('CameraFollowEasing.step', () {
    test('eases a small rotation smoothly, not instantly', () {
      final result =
          CameraFollowEasing.step(from: _s(rot: 0), target: _s(rot: 10), dtMs: 16);
      expect(result, isNotNull);
      expect(result!.rotDeg, greaterThan(0));
      expect(result.rotDeg, lessThan(10));
    });

    test('caps angular velocity on a large correction', () {
      // A 150° jump — what a heading filter correction near a roundabout can
      // produce — must not appear in one 16 ms frame.
      final result =
          CameraFollowEasing.step(from: _s(rot: 0), target: _s(rot: 150), dtMs: 16);
      final maxPerFrame = CameraFollowEasing.maxTurnDegPerSec * 16 / 1000.0;
      expect(result!.rotDeg, lessThanOrEqualTo(maxPerFrame + 1e-9));
    });

    test('does not cap genuine vehicle-speed turning', () {
      // ~30°/s, a tight roundabout — must pass through uncapped: the 90°/s
      // cap only ever bites above that, so the plain exponential-ease value
      // survives untouched.
      final result =
          CameraFollowEasing.step(from: _s(rot: 0), target: _s(rot: 30), dtMs: 1000);
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
      expect(CameraFollowEasing.hasCaughtUp(_s(zoom: 17), _s(zoom: 17.1)), isFalse);
      expect(
          CameraFollowEasing.hasCaughtUp(_s(lat: 45), _s(lat: 45.001)), isFalse);
    });
  });
}
