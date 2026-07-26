import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/utils/fuzzy_match.dart';

void main() {
  group('normalize', () {
    test('folds accents, case and punctuation', () {
      expect(FuzzyMatch.normalize('Città  di  Castello!'),
          'citta di castello');
      expect(FuzzyMatch.normalize("Sant'Apollinare in Classe"),
          'sant apollinare in classe');
      expect(FuzzyMatch.normalize('Straße'), 'strasse');
    });
  });

  group('wordScore', () {
    test('exact match scores 1', () {
      expect(FuzzyMatch.wordScore('ricci', 'ricci'), 1);
    });

    test('typos still match', () {
      expect(FuzzyMatch.wordScore('robberto', 'roberto'), greaterThan(0.8));
      expect(FuzzyMatch.wordScore('farmacie', 'farmacia'), greaterThan(0.8));
    });

    test('prefixes match while typing', () {
      expect(FuzzyMatch.wordScore('garib', 'garibaldi'), greaterThan(0.85));
    });

    test('unrelated words do not match', () {
      expect(FuzzyMatch.wordScore('ricci', 'roma'), 0);
      expect(FuzzyMatch.wordScore('banca', 'bar'), 0);
    });
  });

  group('score', () {
    test('an omitted given name still finds the street', () {
      // The real-world miss: OSM stores "Via Ricci", the user types the full
      // name from the street sign. It must not only match, it must outrank a
      // different street that happens to share the given name.
      final wanted = FuzzyMatch.score('via roberto ricci', 'Via Ricci');
      expect(wanted, greaterThan(0.5));
      expect(wanted,
          greaterThan(FuzzyMatch.score('via roberto ricci', 'Via Roberto Baldini')));
      expect(wanted,
          greaterThan(FuzzyMatch.score('via roberto ricci', 'Via Fabbri Roberto')));
    });

    test('the exact street outranks a same-surname neighbour', () {
      final exact =
          FuzzyMatch.score('via roberto ricci', 'Via Roberto Ricci, Torino');
      final other =
          FuzzyMatch.score('via roberto ricci', 'Via Roberto Baldini, Torino');
      expect(exact, greaterThan(other));
      // An untyped town in the label costs some score — callers also compare
      // against the street name alone, which matches perfectly.
      expect(FuzzyMatch.score('via roberto ricci', 'Via Roberto Ricci'), 1);
    });

    test('a misspelled query still ranks the right street first', () {
      final right =
          FuzzyMatch.score('via robberto ricc', 'Via Roberto Ricci, Torino');
      final wrong =
          FuzzyMatch.score('via robberto ricc', 'Via Fabbri Roberto, Torino');
      expect(right, greaterThan(wrong));
    });

    test('matching only the street type is not a match', () {
      // "via" alone must never lift an unrelated street.
      expect(FuzzyMatch.score('via garibaldi', 'Via Napoleone, Milano'), 0);
    });

    test('tighter names beat longer ones', () {
      expect(FuzzyMatch.score('via ricci', 'Via Ricci'),
          greaterThan(FuzzyMatch.score('via ricci', 'Via Ricci Berici Nord')));
    });

    test('empty inputs are safe', () {
      expect(FuzzyMatch.score('', 'Via Roma'), 0);
      expect(FuzzyMatch.score('Via Roma', ''), 0);
    });
  });
}
