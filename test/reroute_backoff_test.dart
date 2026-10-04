import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/utils/reroute_backoff.dart';

void main() {
  late DateTime now;
  late RerouteBackoff backoff;

  setUp(() {
    now = DateTime(2026, 1, 1, 12);
    backoff = RerouteBackoff(now: () => now);
  });

  test('a fresh session reroutes immediately', () {
    expect(backoff.allowsAttempt, isTrue);
  });

  test('a failure blocks the next automatic attempt for five seconds', () {
    backoff.recordFailure();
    now = now.add(const Duration(milliseconds: 500)); // the next GPS fix
    expect(backoff.allowsAttempt, isFalse);
    now = now.add(const Duration(milliseconds: 4500));
    expect(backoff.allowsAttempt, isTrue);
  });

  test('consecutive failures back off up to a thirty second ceiling', () {
    final waits = <int>[];
    for (var i = 0; i < 6; i++) {
      backoff.recordFailure();
      var waited = 0;
      while (!backoff.allowsAttempt) {
        now = now.add(const Duration(seconds: 1));
        waited++;
      }
      waits.add(waited);
    }
    expect(waits, [5, 10, 20, 30, 30, 30]);
  });

  test('reset clears both the wait and the escalation', () {
    backoff
      ..recordFailure()
      ..recordFailure()
      ..reset();
    expect(backoff.allowsAttempt, isTrue);
    backoff.recordFailure();
    now = now.add(const Duration(seconds: 5));
    expect(backoff.allowsAttempt, isTrue);
  });
}
