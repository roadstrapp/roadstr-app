import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/kokoro/kokoro_tts_service.dart';

/// The regression this guards: init() ran once per navigation, unawaited,
/// right as GPS/map/routing were also starting up. A transient failure there
/// — plausible exactly at that moment, under that resource pressure — left
/// voice guidance completely silent for the rest of the drive, with nothing
/// but a debugPrint (silenced in release builds) to show for it. The next
/// navigation worked only because it called init() fresh.
///
/// This is the backoff decision alone: whether the model, engine and FFI
/// phonemizer that init() actually touches are available is not something a
/// unit test can arrange, but the policy that decides *when* to retry them is
/// a pure function of two timestamps, and is tested as one here.
void main() {
  test('never failed — always retriable', () {
    expect(KokoroTtsService.shouldRetryInit(null, DateTime(2026, 1, 1)), isTrue);
  });

  test('right after a failure — not yet', () {
    final failedAt = DateTime(2026, 1, 1, 12, 0, 0);
    expect(
      KokoroTtsService.shouldRetryInit(
          failedAt, failedAt.add(const Duration(seconds: 1))),
      isFalse,
    );
  });

  test('just under the cooldown — still no', () {
    final failedAt = DateTime(2026, 1, 1, 12, 0, 0);
    expect(
      KokoroTtsService.shouldRetryInit(
          failedAt, failedAt.add(const Duration(seconds: 7, milliseconds: 999))),
      isFalse,
    );
  });

  test('at or past the cooldown — retriable again', () {
    final failedAt = DateTime(2026, 1, 1, 12, 0, 0);
    expect(
      KokoroTtsService.shouldRetryInit(
          failedAt, failedAt.add(const Duration(seconds: 8))),
      isTrue,
    );
    expect(
      KokoroTtsService.shouldRetryInit(
          failedAt, failedAt.add(const Duration(minutes: 5))),
      isTrue,
    );
  });

  test('a stream of speak() calls while broken retries on a schedule, '
      'not on every call', () {
    // This is the failure mode a missing cooldown would reintroduce: a
    // one-shot repair attempt turning into a hot loop of full model
    // re-initialisation on every single announcement while still broken.
    final failedAt = DateTime(2026, 1, 1, 12, 0, 0);
    final attemptsInFirstEightSeconds = [1, 2, 3, 4, 5, 6, 7]
        .where((s) => KokoroTtsService.shouldRetryInit(
            failedAt, failedAt.add(Duration(seconds: s))))
        .length;
    expect(attemptsInFirstEightSeconds, 0);
  });
}
