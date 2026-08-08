import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:roadstr/utils/units.dart';

/// The inline form exists so a distance can sit inside a chained instruction
/// — "take the first exit, then in 300 metres take the off-ramp" — instead of
/// only opening one. Getting it wrong is audible, not visible.
void main() {
  late Box settings;

  setUpAll(() async {
    Hive.init((await Directory.systemTemp.createTemp('roadstr-units')).path);
    settings = await Hive.openBox('settings');
  });
  tearDownAll(Hive.close);
  setUp(() => settings.put('imperialUnits', false));

  group('ttsDistInline', () {
    test('drops the separator and the opening capital', () {
      expect(Units.ttsDistInline(300, 'en'), 'in 300 meters');
      expect(Units.ttsDistInline(300, 'it'), 'tra 300 metri');
      expect(Units.ttsDistInline(300, 'fr'), 'dans 300 mètres');
    });

    test('languages written without spacing keep their own punctuation rules',
        () {
      // No Latin capital to lower and no trailing ", " — only the ideographic
      // comma, which must go so the phrase can be joined mid-sentence.
      expect(Units.ttsDistInline(300, 'ja'), '300メートル先で');
      expect(Units.ttsDistInline(300, 'zh'), '在300米后');
    });

    test('an imminent maneuver has no distance to speak', () {
      expect(Units.ttsDistInline(0, 'en'), '');
      expect(Units.ttsDistInline(-10, 'it'), '');
    });

    test('imperial units come through the same shaping', () async {
      await settings.put('imperialUnits', true);
      expect(Units.ttsDistInline(1609, 'en'), 'in 1.0 miles');
      expect(Units.ttsDistInline(60, 'en'), startsWith('in '));
    });
  });

  group('joinDistance', () {
    test('inserts a space only where the language uses one', () {
      expect(Units.joinDistance('in 300 meters', 'take the exit', 'en'),
          'in 300 meters take the exit');
      expect(Units.joinDistance('300メートル先で', '出口です', 'ja'), '300メートル先で出口です');
      expect(Units.joinDistance('在300米后', '驶出', 'zh'), '在300米后驶出');
    });

    test('an empty distance leaves the instruction untouched', () {
      expect(Units.joinDistance('', 'take the exit', 'en'), 'take the exit');
    });
  });
}
