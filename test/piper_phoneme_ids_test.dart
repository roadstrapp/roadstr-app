import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/piper/piper_engine.dart';

void main() {
  // A tiny id map covering just what these tests need — real Thorsten-Voice
  // ids differ, but the sequencing algorithm under test doesn't care about
  // the actual numbers, only where BOS/PAD/EOS land relative to each symbol.
  const idMap = <String, List<int>>{
    '_': [0], // PAD
    '^': [1], // BOS
    r'$': [2], // EOS
    'a': [10],
    'b': [11],
    ' ': [3],
  };

  group('PiperEngine.phonemesToIds', () {
    test('wraps BOS/PAD at the start and EOS at the end, with a PAD after '
        'every symbol including the last', () {
      final ids = PiperEngine.phonemesToIds('ab', idMap);
      // ^ _ a _ b _ $
      expect(ids, [1, 0, 10, 0, 11, 0, 2]);
    });

    test('an empty phoneme string still yields BOS/PAD/EOS only', () {
      final ids = PiperEngine.phonemesToIds('', idMap);
      expect(ids, [1, 0, 2]);
    });

    test('spaces between words are just another symbol with its own id', () {
      final ids = PiperEngine.phonemesToIds('a b', idMap);
      // ^ _ a _ (space) _ b _ $
      expect(ids, [1, 0, 10, 0, 3, 0, 11, 0, 2]);
    });

    test('a symbol missing from the id map is skipped, not inserted as a '
        'gap or a crash', () {
      final missing = <String>[];
      final ids = PiperEngine.phonemesToIds('axb', idMap,
          onMissing: missing.add);
      expect(missing, ['x']);
      // ^ _ a _ b _ $ — 'x' contributes nothing, not even a PAD of its own.
      expect(ids, [1, 0, 10, 0, 11, 0, 2]);
    });

    test('a multi-id map entry (if a voice ever ships one) is fully '
        'inserted before the following PAD', () {
      final wideMap = <String, List<int>>{...idMap, 'c': [20, 21]};
      final ids = PiperEngine.phonemesToIds('c', wideMap);
      expect(ids, [1, 0, 20, 21, 0, 2]);
    });
  });
}
