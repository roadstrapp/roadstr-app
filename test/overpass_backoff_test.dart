import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/overpass_client.dart';

/// The mirrors are free and volunteer-run. What is checked here is not that
/// retrying works, but that it gets *quieter* the longer a mirror keeps
/// failing — a client that retries at a fixed rate through an outage is the
/// one that keeps the outage going.
void main() {
  test('no back-off until something fails', () {
    expect(OverpassClient().failureBackoff(), Duration.zero);
  });

  test('back-off grows with consecutive failures and then stops growing', () {
    final client = OverpassClient();
    const base = Duration(seconds: 15);
    const max = Duration(minutes: 5);

    client.noteFailure(null);
    expect(client.failureBackoff(base: base, max: max), base);
    client.noteFailure(null);
    expect(client.failureBackoff(base: base, max: max), base * 2);
    client.noteFailure(null);
    expect(client.failureBackoff(base: base, max: max), base * 4);

    for (var i = 0; i < 20; i++) {
      client.noteFailure(null);
    }
    expect(client.failureBackoff(base: base, max: max), max,
        reason: 'capped, and the streak counter cannot overflow the shift');
  });

  test('one success clears the whole streak', () {
    final client = OverpassClient();
    client.noteFailure(null);
    client.noteFailure(null);
    client.noteSuccess();
    expect(client.failureBackoff(), Duration.zero);
  });

  test('being throttled starts further back than a transport error', () {
    const base = Duration(seconds: 15);
    final throttled = OverpassClient()..noteFailure(const OverpassException(429));
    final refused = OverpassClient()..noteFailure(const OverpassException(500));
    expect(throttled.failureBackoff(base: base),
        greaterThan(refused.failureBackoff(base: base)));
    // 503/504 are the other two ways a mirror says it is out of capacity.
    for (final status in [503, 504]) {
      final overloaded = OverpassClient()
        ..noteFailure(OverpassException(status));
      expect(overloaded.failureBackoff(base: base), base * 4, reason: '$status');
    }
  });
}
