import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/utils/retry.dart';

void main() {
  group('failure classification', () {
    test('a timeout or a dropped connection is worth repeating', () {
      expect(classifyFailure(TimeoutException('slow')), isA<TransientFailure>());
      expect(classifyFailure(const SocketException('reset')),
          isA<TransientFailure>());
    });

    test('a malformed body is not', () {
      // Retrying a parse error just burns battery on the same broken answer.
      expect(classifyFailure(const FormatException('bad json')),
          isA<PermanentFailure>());
    });

    test('an unexpected error defaults to permanent', () {
      // Safer: a bug surfaces once instead of being hidden behind retries.
      expect(classifyFailure(StateError('bug')), isA<PermanentFailure>());
    });

    test('status codes split along "will this ever work" lines', () {
      expect(classifyStatus(200), isNull);
      expect(classifyStatus(204), isNull);
      expect(classifyStatus(429), isA<TransientFailure>());
      expect(classifyStatus(503), isA<TransientFailure>());
      expect(classifyStatus(500), isA<TransientFailure>());
      expect(classifyStatus(404), isA<PermanentFailure>());
      expect(classifyStatus(400), isA<PermanentFailure>());
    });

    test('a host is recorded but query parameters never are', () {
      // Query strings carry the user's coordinates; those must not reach a log.
      final failure = classifyStatus(503, host: 'api.example.org')!;
      expect(failure.host, 'api.example.org');
      expect(failure.toString(), contains('api.example.org'));
      expect(failure.toString(), isNot(contains('?')));
    });
  });

  group('Retry-After', () {
    test('a delay in seconds is honoured', () {
      final failure =
          classifyStatus(429, retryAfter: '3') as TransientFailure;
      expect(failure.retryAfter, const Duration(seconds: 3));
    });

    test('an absurd wait is ignored rather than obeyed', () {
      // Sitting still for an hour inside a route request reads as a freeze.
      final failure =
          classifyStatus(429, retryAfter: '3600') as TransientFailure;
      expect(failure.retryAfter, isNull);
    });

    test('nonsense is ignored', () {
      final failure =
          classifyStatus(429, retryAfter: 'soon-ish') as TransientFailure;
      expect(failure.retryAfter, isNull);
    });

    test("the server's own delay beats the computed backoff", () {
      const policy = RetryPolicy(baseDelay: Duration(milliseconds: 100));
      expect(policy.delayBefore(2), const Duration(milliseconds: 100));
      expect(
        policy.delayBefore(2, retryAfter: const Duration(seconds: 2)),
        const Duration(seconds: 2),
        reason: 'the server knows when it will be ready and we do not',
      );
    });
  });

  group('backoff schedule', () {
    test('doubles, then stops at the ceiling', () {
      const policy = RetryPolicy(
        attempts: 6,
        baseDelay: Duration(milliseconds: 100),
        maxDelay: Duration(milliseconds: 500),
      );
      expect(policy.delayBefore(2), const Duration(milliseconds: 100));
      expect(policy.delayBefore(3), const Duration(milliseconds: 200));
      expect(policy.delayBefore(4), const Duration(milliseconds: 400));
      // Capped: exponential growth must not become a wait read as a hang.
      expect(policy.delayBefore(5), const Duration(milliseconds: 500));
      expect(policy.delayBefore(9), const Duration(milliseconds: 500));
    });
  });

  group('withRetry', () {
    const fast = RetryPolicy(
      attempts: 3,
      baseDelay: Duration(milliseconds: 1),
      maxDelay: Duration(milliseconds: 1),
    );

    test('returns the first success without waiting', () async {
      var calls = 0;
      final result = await withRetry(() async {
        calls++;
        return 'ok';
      }, policy: fast);
      expect(result, 'ok');
      expect(calls, 1);
    });

    test('repeats a transient failure and succeeds', () async {
      var calls = 0;
      final result = await withRetry(() async {
        calls++;
        if (calls < 3) throw TimeoutException('slow');
        return 'ok';
      }, policy: fast);
      expect(result, 'ok');
      expect(calls, 3);
    });

    test('gives up after the configured attempts', () async {
      var calls = 0;
      await expectLater(
        withRetry(() async {
          calls++;
          throw TimeoutException('slow');
        }, policy: fast),
        throwsA(isA<TransientFailure>()),
      );
      expect(calls, 3, reason: 'exactly the budget, not one more');
    });

    test('does not repeat a permanent failure', () async {
      var calls = 0;
      await expectLater(
        withRetry(() async {
          calls++;
          throw const FormatException('bad json');
        }, policy: fast),
        throwsA(isA<PermanentFailure>()),
      );
      expect(calls, 1, reason: 'a 404 is not going to become a 200');
    });

    test('a single-attempt policy never repeats', () async {
      var calls = 0;
      await expectLater(
        withRetry(() async {
          calls++;
          throw TimeoutException('slow');
        }, policy: RetryPolicy.none),
        throwsA(isA<TransientFailure>()),
      );
      expect(calls, 1);
    });
  });
}
