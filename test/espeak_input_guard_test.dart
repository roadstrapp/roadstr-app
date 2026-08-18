import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/kokoro/espeak_phonemizer.dart';

/// The phonemizer calls a C library that has, in the field, taken the whole
/// process down with a SIGSEGV inside `espeak_TextToPhonemes`. These tests pin
/// the input guard that stands between navigation and that library.
///
/// The guard is tested directly rather than through `phonemize`, which would
/// need the native library loaded and the model data on disk.
void main() {
  group('word length cap', () {
    test('ordinary text is passed through untouched', () {
      // The common case must not be reshaped: any change here is a change to
      // how every street name is pronounced.
      const text = 'In 200 metri, svolta a destra in Via Giuseppe Garibaldi';
      expect(EspeakPhonemizer.debugCapWordLengths(text), text);
    });

    test('a single overlong token is truncated', () {
      final long = 'a' * 200;
      final out = EspeakPhonemizer.debugCapWordLengths('turn into $long now');
      expect(out.split(RegExp(r'\s+')).map((w) => w.length).reduce(
            (a, b) => a > b ? a : b,
          ),
          lessThanOrEqualTo(60));
      // The rest of the sentence has to survive — the guard is a cap, not a
      // rejection, because dropping the instruction is also a failure.
      expect(out, contains('turn into'));
      expect(out, contains('now'));
    });

    test('truncates rather than splits', () {
      // Splitting would hand the library the same suffix chain in pieces,
      // which is the very thing that overflows its stack buffer.
      final long = 'x' * 150;
      final out = EspeakPhonemizer.debugCapWordLengths(long);
      expect(out.split(RegExp(r'\s+')), hasLength(1));
      expect(out.length, 60);
    });

    test('a token exactly at the limit is left alone', () {
      final exact = 'y' * 60;
      expect(EspeakPhonemizer.debugCapWordLengths(exact), exact);
    });

    test('empty and whitespace-only input do not throw', () {
      expect(EspeakPhonemizer.debugCapWordLengths(''), '');
      expect(EspeakPhonemizer.debugCapWordLengths('   ').trim(), '');
    });

    test('real place names are never affected', () {
      // The longest official place names in Europe sit far below the cap; if
      // this ever fails the cap has been set too low, not too high.
      const names = [
        'Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch',
        'Sant Julià de Lòria',
        'Θεσσαλονίκη',
        'Vlissingen-Oost',
      ];
      for (final n in names) {
        expect(EspeakPhonemizer.debugCapWordLengths(n), n, reason: n);
      }
    });
  });
}
