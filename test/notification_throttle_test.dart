import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/navigation_notification_service.dart';

void main() {
  final t0 = DateTime(2026, 9, 24, 12);
  Duration s(int n) => Duration(seconds: n);

  test('the first update of a trip always goes out', () {
    final throttle = NotificationThrottle();
    expect(throttle.shouldPost('Turn left', '400 m', t0), isTrue);
  });

  test('the distance ticking down is rate-limited, not posted every fix', () {
    // ~2 fixes a second, the distance label changing on each one.
    final throttle = NotificationThrottle();
    var posted = 0;
    for (var i = 0; i < 20; i++) {
      final at = t0.add(Duration(milliseconds: 500 * i));
      if (throttle.shouldPost('Turn left', '${400 - 7 * i} m', at)) posted++;
    }
    // Ten seconds of driving: the first, then one every three seconds.
    expect(posted, lessThanOrEqualTo(4));
    expect(posted, greaterThanOrEqualTo(3));
  });

  test('a new manoeuvre is never held back', () {
    final throttle = NotificationThrottle();
    expect(throttle.shouldPost('Turn left', '60 m', t0), isTrue);
    // A tenth of a second later, but a different instruction: the driver has
    // to see it now, not in three seconds.
    expect(
        throttle.shouldPost(
            'Turn right', '900 m', t0.add(const Duration(milliseconds: 100))),
        isTrue);
  });

  test('an unchanged label is not a reason to post again, however long', () {
    final throttle = NotificationThrottle();
    expect(throttle.shouldPost('Continue', '1.2 km', t0), isTrue);
    expect(throttle.shouldPost('Continue', '1.2 km', t0.add(s(60))), isFalse);
  });

  test('once the interval has passed a changed distance goes out', () {
    final throttle = NotificationThrottle();
    expect(throttle.shouldPost('Turn left', '400 m', t0), isTrue);
    expect(throttle.shouldPost('Turn left', '380 m', t0.add(s(2))), isFalse);
    expect(throttle.shouldPost('Turn left', '350 m', t0.add(s(3))), isTrue);
  });

  test('reset makes the next trip start clean', () {
    final throttle = NotificationThrottle();
    expect(throttle.shouldPost('Turn left', '400 m', t0), isTrue);
    throttle.reset();
    // Same text, a moment later: from a fresh trip this is a first update,
    // not a repeat of the old one.
    expect(throttle.shouldPost('Turn left', '400 m', t0.add(s(1))), isTrue);
  });
}
