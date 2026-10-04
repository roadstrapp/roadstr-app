import 'dart:math' as math;

/// Paces automatic reroute attempts after a failed one.
///
/// Off-route detection runs on every GPS fix, about twice a second. When the
/// routing server is down or the phone has no signal, each failed request was
/// followed straight away by the next: a stream of requests against a free,
/// volunteer-run OSRM instance, and the radio kept busy for nothing on the
/// driver's battery. Consecutive failures now wait 5, 10, 20 and then 30 s;
/// one success, or a new navigation session, clears the wait.
///
/// Only automatic triggers consult [allowsAttempt]. A reroute the driver asks
/// for explicitly is always sent.
class RerouteBackoff {
  RerouteBackoff({DateTime Function()? now}) : _now = now ?? DateTime.now;

  static const List<Duration> delays = [
    Duration(seconds: 5),
    Duration(seconds: 10),
    Duration(seconds: 20),
    Duration(seconds: 30),
  ];

  final DateTime Function() _now;
  int _failures = 0;
  DateTime? _retryAt;

  bool get allowsAttempt {
    final retryAt = _retryAt;
    return retryAt == null || !_now().isBefore(retryAt);
  }

  void recordFailure() {
    final delay = delays[math.min(_failures, delays.length - 1)];
    _failures++;
    _retryAt = _now().add(delay);
  }

  void reset() {
    _failures = 0;
    _retryAt = null;
  }
}
