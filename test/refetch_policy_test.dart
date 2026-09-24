import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/refetch_policy.dart';

void main() {
  const policy =
      RefetchPolicy(minMoveM: 750, maxAge: Duration(minutes: 15));
  const here = LatLng(45, 9);
  final now = DateTime(2026, 9, 24, 12);

  // A point `metres` due north of [here].
  LatLng north(double metres) => LatLng(45 + metres / 111320, 9);

  test('an empty cache is always due', () {
    expect(
        policy.isDue(
            lastQueryPos: null, pos: here, lastSuccessAt: null, now: now),
        isTrue);
  });

  test('a cache filled a moment ago and not moved from is not due', () {
    expect(
        policy.isDue(
            lastQueryPos: here, pos: here, lastSuccessAt: now, now: now),
        isFalse);
  });

  test('not due until the vehicle has actually travelled the distance', () {
    bool due(double metres) => policy.isDue(
        lastQueryPos: here,
        pos: north(metres),
        lastSuccessAt: now,
        now: now);
    expect(due(500), isFalse);
    expect(due(749), isFalse);
    expect(due(760), isTrue);
  });

  test('a parked phone is NOT refetched every couple of minutes', () {
    // The old rule: due again after 120 s with nothing having moved.
    for (final minutes in [2, 5, 10, 14]) {
      expect(
          policy.isDue(
              lastQueryPos: here,
              pos: here,
              lastSuccessAt: now,
              now: now.add(Duration(minutes: minutes))),
          isFalse,
          reason: 'refetched after $minutes min without moving');
    }
  });

  test('but the safety net still refreshes it eventually', () {
    expect(
        policy.isDue(
            lastQueryPos: here,
            pos: here,
            lastSuccessAt: now,
            now: now.add(const Duration(minutes: 16))),
        isTrue);
  });

  test('a position recorded without a success is treated as unfilled', () {
    // A query started (position remembered) but never completed.
    expect(
        policy.isDue(
            lastQueryPos: here, pos: here, lastSuccessAt: null, now: now),
        isTrue);
  });
}
