import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/utils/ui_language.dart';

void main() {
  const supported = [Locale('de'), Locale('en'), Locale('es'), Locale('it')];

  group('resolveUiLanguage', () {
    test('an explicit choice in Settings wins over the phone', () {
      expect(
        resolveUiLanguage(
            stored: 'en',
            deviceLocales: const [Locale('es', 'ES')],
            supported: supported),
        'en',
      );
    });

    test('on "system default" it follows the phone, not Italian', () {
      for (final stored in [null, '']) {
        expect(
          resolveUiLanguage(
              stored: stored,
              deviceLocales: const [Locale('es', 'ES')],
              supported: supported),
          'es',
        );
      }
    });

    test('walks the phone\'s locale list to the first one the app ships', () {
      expect(
        resolveUiLanguage(
            stored: null,
            deviceLocales: const [Locale('is'), Locale('de', 'AT')],
            supported: supported),
        'de',
      );
    });

    test('agrees with what MaterialApp would resolve', () {
      // Nothing matches: Flutter falls back to the first supported locale,
      // and the routing and voice language must be that same one.
      expect(
        resolveUiLanguage(
            stored: null,
            deviceLocales: const [Locale('is')],
            supported: supported),
        supported.first.languageCode,
      );
    });
  });

  group('orsLanguage', () {
    test('maps onto ORS\'s own codes', () {
      expect(RoutingService.orsLanguage('el'), 'gr');
      expect(RoutingService.orsLanguage('uk'), 'ua');
      expect(RoutingService.orsLanguage('DE'), 'de');
    });

    test('a language ORS does not know becomes English, not an error', () {
      expect(RoutingService.orsLanguage('sl'), 'en');
      expect(RoutingService.orsLanguage(''), 'en');
    });
  });

  // The MapLibre screen asked every routing server for Italian, and both
  // screens started the voice engine in Italian when the language setting was
  // empty — so a Spanish or German phone got Italian directions. A source scan
  // because the screens are too heavy to pump in a unit test.
  group('no hard-coded Italian in the map screens', () {
    for (final path in [
      'lib/screens/maplibre_map_screen.dart',
      'lib/screens/map_screen.dart',
    ]) {
      test(path, () {
        final source = File(path).readAsStringSync();
        expect(source, isNot(contains("lang: 'it'")));
        expect(source, isNot(contains(": 'it'));")));
      });
    }
  });
}
