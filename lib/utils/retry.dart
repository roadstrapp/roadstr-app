/// Typed network failures, and the retry policy that reads them.
///
/// These two things belong in one file because neither is much use alone.
/// Before this, every service wrapped its request in `try { ... } catch (_) {}`
/// and returned an empty list, which meant "the server is busy, ask again in a
/// moment" and "this endpoint returned something that is not a route" were
/// indistinguishable — so nothing could be retried without also hammering an
/// endpoint that was never going to succeed.
///
/// Classifying the failure first is what makes a retry safe: [TransientFailure]
/// is worth repeating, [PermanentFailure] never is.
library;

import 'dart:async';
import 'dart:io';

import 'package:http/http.dart' as http;

/// Base type for every network failure the app raises deliberately.
sealed class NetworkFailure implements Exception {
  final String message;

  /// Endpoint that failed, for logs. Never contains query parameters — those
  /// carry the user's coordinates, which must not reach a log or a crash
  /// report.
  final String? host;

  const NetworkFailure(this.message, {this.host});

  @override
  String toString() =>
      '$runtimeType: $message${host == null ? '' : ' ($host)'}';
}

/// The request could plausibly succeed if repeated: a timeout, a dropped
/// connection, a 5xx, or an explicit "slow down" from the server.
class TransientFailure extends NetworkFailure {
  /// Server-requested delay from a `Retry-After` header, when it sent one.
  /// The community-run endpoints this app depends on (Nominatim, Overpass,
  /// Transitous) publish usage policies; honouring their own stated backoff is
  /// the difference between a well-behaved client and one that gets the whole
  /// app blocked.
  final Duration? retryAfter;

  const TransientFailure(super.message, {super.host, this.retryAfter});
}

/// The request will fail the same way forever: a malformed response, a 4xx
/// that is not 429, an endpoint that does not cover this area. Retrying only
/// wastes the user's battery and the operator's capacity.
class PermanentFailure extends NetworkFailure {
  const PermanentFailure(super.message, {super.host});
}

/// Turns an arbitrary thrown object into a classified failure.
///
/// Anything genuinely unexpected is treated as permanent. That is the safe
/// default: a bug in parsing should surface as a failure the user sees once,
/// not as three retries that hide it.
NetworkFailure classifyFailure(Object error, {String? host}) {
  if (error is NetworkFailure) return error;
  if (error is TimeoutException) {
    return TransientFailure('request timed out', host: host);
  }
  // Both mean the connection itself did not survive; the peer may well be
  // fine on the next attempt.
  if (error is SocketException || error is http.ClientException) {
    return TransientFailure('connection failed', host: host);
  }
  if (error is HttpException) {
    return PermanentFailure(error.message, host: host);
  }
  if (error is FormatException) {
    return PermanentFailure('malformed response', host: host);
  }
  return PermanentFailure('unexpected error: ${error.runtimeType}',
      host: host);
}

/// Classifies an HTTP status code. Returns null when the response is usable.
NetworkFailure? classifyStatus(int status, {String? host, String? retryAfter}) {
  if (status >= 200 && status < 300) return null;
  if (status == 429 || status >= 500) {
    return TransientFailure('HTTP $status',
        host: host, retryAfter: _parseRetryAfter(retryAfter));
  }
  return PermanentFailure('HTTP $status', host: host);
}

/// `Retry-After` is either a delay in seconds or an HTTP date. A value that is
/// absurdly far in the future is ignored rather than obeyed — waiting an hour
/// inside a route request would look like a freeze.
Duration? _parseRetryAfter(String? header) {
  if (header == null) return null;
  final seconds = int.tryParse(header.trim());
  final delay = seconds != null
      ? Duration(seconds: seconds)
      : _tryParseHttpDate(header)?.difference(DateTime.now().toUtc());
  if (delay == null || delay <= Duration.zero) return null;
  return delay > _maxHonouredRetryAfter ? null : delay;
}

const _maxHonouredRetryAfter = Duration(seconds: 30);

DateTime? _tryParseHttpDate(String value) {
  try {
    return HttpDate.parse(value);
  } catch (_) {
    return null;
  }
}

/// How many times to try, and how long to wait between attempts.
class RetryPolicy {
  /// Total attempts, including the first. 1 disables retrying.
  final int attempts;

  /// Delay before the second attempt; doubles each time after that.
  final Duration baseDelay;

  /// Ceiling on any single delay, so exponential growth cannot turn into a
  /// wait the user reads as a hang.
  final Duration maxDelay;

  const RetryPolicy({
    this.attempts = 3,
    this.baseDelay = const Duration(milliseconds: 400),
    this.maxDelay = const Duration(seconds: 4),
  }) : assert(attempts >= 1);

  /// A single attempt — for requests fired on the app's own initiative, where
  /// the next periodic sweep is itself the retry.
  static const none = RetryPolicy(attempts: 1);

  /// The default for anything the user is waiting on.
  static const interactive = RetryPolicy();

  /// Delay before attempt number [attempt] (1-based), capped by [maxDelay].
  /// A server's own `Retry-After` always wins over the computed backoff — it
  /// knows when it will be ready and we do not.
  Duration delayBefore(int attempt, {Duration? retryAfter}) {
    final backoff = baseDelay * (1 << (attempt - 2).clamp(0, 16));
    final chosen = retryAfter != null && retryAfter > backoff
        ? retryAfter
        : backoff;
    return chosen > maxDelay ? maxDelay : chosen;
  }
}

/// Runs [action], repeating it while it fails transiently.
///
/// Rethrows the classified failure once the attempts are spent, so callers can
/// still tell "the network is unhappy" from "this endpoint said no" and decide
/// whether to fall back to a mirror or surface an error.
Future<T> withRetry<T>(
  Future<T> Function() action, {
  RetryPolicy policy = RetryPolicy.interactive,
  String? host,
  // Called before each wait — lets tests observe the schedule without
  // sleeping through it.
  void Function(int attempt, Duration delay)? onRetry,
}) async {
  for (var attempt = 1;; attempt++) {
    try {
      return await action();
    } catch (error) {
      final failure = classifyFailure(error, host: host);
      if (failure is! TransientFailure || attempt >= policy.attempts) {
        throw failure;
      }
      final delay =
          policy.delayBefore(attempt + 1, retryAfter: failure.retryAfter);
      onRetry?.call(attempt, delay);
      await Future<void>.delayed(delay);
    }
  }
}
