import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/nav_phrases.dart';

void main() {
  final keys = navPhrases['en']!.keys.toList();

  group('coverage', () {
    test('every language the app ships has instructions of its own', () {
      // The gap this table closed: twenty-five of the twenty-seven supported
      // languages were navigated in English, because the builder was a pair of
      // ternaries on `lang == 'it'`.
      final shipped = Directory('lib/l10n')
          .listSync()
          .whereType<File>()
          .map((f) => f.path.split('/').last)
          .where((n) => n.startsWith('app_') && n.endsWith('.arb'))
          .map((n) => n.substring(4, n.length - 4))
          .toSet();

      expect(shipped, isNotEmpty, reason: 'sanity: locales were found');
      final missing = shipped.difference(navPhrases.keys.toSet());
      expect(missing, isEmpty,
          reason: 'these locales would fall back to English: $missing');
    });

    test('no language is missing a phrase', () {
      for (final entry in navPhrases.entries) {
        final absent = keys.toSet().difference(entry.value.keys.toSet());
        expect(absent, isEmpty,
            reason: '${entry.key} is missing $absent');
      }
    });

    test('no phrase was left blank', () {
      for (final entry in navPhrases.entries) {
        for (final phrase in entry.value.entries) {
          expect(phrase.value.trim(), isNotEmpty,
              reason: '${entry.key}/${phrase.key} is empty');
        }
      }
    });
  });

  group('placeholders survive translation', () {
    test('the exit label is kept', () {
      for (final entry in navPhrases.entries) {
        expect(entry.value['exitLabelled'], contains('{label}'),
            reason: '${entry.key} lost {label} — the exit number would '
                'never be spoken');
      }
    });

    test('the roundabout ordinal is kept', () {
      for (final entry in navPhrases.entries) {
        for (final key in ['roundabout', 'rotary']) {
          expect(entry.value[key], contains('{n}'),
              reason: '${entry.key}/$key lost {n} — the driver would be told '
                  'to take "an" exit');
        }
      }
    });

    test('no stray placeholder anywhere else', () {
      final placeholder = RegExp(r'\{(\w+)\}');
      for (final entry in navPhrases.entries) {
        for (final phrase in entry.value.entries) {
          final found = placeholder
              .allMatches(phrase.value)
              .map((m) => m[1])
              .toSet();
          final allowed = switch (phrase.key) {
            'exitLabelled' => {'label'},
            'roundabout' || 'rotary' => {'n'},
            _ => <String>{},
          };
          expect(found.difference(allowed), isEmpty,
              reason: '${entry.key}/${phrase.key} carries $found');
        }
      }
    });
  });

  group('lookup', () {
    test('a known language returns its own wording', () {
      expect(navPhrase('it', 'turnRight'), 'Svolta a destra');
      expect(navPhrase('de', 'turnRight'), 'Rechts abbiegen');
      expect(navPhrase('pl', 'turnRight'), 'Skręć w prawo');
    });

    test('an unknown language falls back to English rather than failing', () {
      // A locale the app does not ship must still navigate.
      expect(navPhrase('xx', 'turnRight'), 'Turn right');
    });

    test('an unknown key yields empty rather than throwing', () {
      expect(navPhrase('it', 'nosuchkey'), '');
    });
  });

  group('translations are real, not copies', () {
    test('non-English languages differ from English', () {
      // Guards against a language being added as a stub of English copies,
      // which would look complete and read as untranslated.
      final english = navPhrases['en']!;
      for (final entry in navPhrases.entries) {
        if (entry.key == 'en') continue;
        final identical = keys
            .where((k) => entry.value[k] == english[k])
            .toList();
        expect(identical.length, lessThan(keys.length ~/ 3),
            reason: '${entry.key} repeats English for $identical');
      }
    });

    test('the preposition is language-specific', () {
      // "on" glued to a street name in the wrong language is the most visible
      // possible tell that nothing was translated.
      expect(navPhrase('it', 'on').trim(), 'su');
      expect(navPhrase('de', 'on').trim(), 'auf');
      expect(navPhrase('fr', 'on').trim(), 'sur');
    });
  });

  group('the table is valid data', () {
    test('every phrase is plain text, not markup or code', () {
      for (final entry in navPhrases.entries) {
        for (final phrase in entry.value.entries) {
          expect(phrase.value, isNot(contains('<')),
              reason: '${entry.key}/${phrase.key}');
          expect(phrase.value, isNot(contains(r'$')),
              reason: '${entry.key}/${phrase.key} looks like an unresolved '
                  'Dart interpolation');
        }
      }
    });

    test('it serialises, so nothing carries an unpaired surrogate', () {
      expect(() => jsonEncode(navPhrases), returnsNormally);
    });
  });
}
