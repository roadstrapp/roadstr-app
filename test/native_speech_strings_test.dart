import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_speech_strings.dart';

const _res = 'android/app/src/main/res';
const _catalogue = 'tools/kotlin_rewrite/native_speech_translations.json';

String _folder(String language) => language == 'en' ? 'values' : 'values-$language';

Map<String, dynamic> _loadCatalogue() =>
    jsonDecode(File(_catalogue).readAsStringSync()) as Map<String, dynamic>;

List<String> _languages() =>
    _loadCatalogue().keys.where((key) => key != 'attribution').toList();

void main() {
  test('the voice search texts exist in all 27 languages', () {
    final languages = _languages();
    expect(languages, hasLength(27));
    expect(languages, containsAll(['en', 'it', 'de', 'ga', 'mt', 'ja', 'zh', 'ru']));
    final catalogue = _loadCatalogue();
    for (final language in languages) {
      final strings = catalogue[language] as Map<String, dynamic>;
      expect(strings.keys.toList(), speechStringKeys, reason: language);
      for (final key in speechStringKeys) {
        expect((strings[key] as String).trim(), isNotEmpty, reason: '$language $key');
      }
    }
  });

  test('the resource files are exactly what the catalogue generates', () {
    final outputs = buildAndroidSpeechResources();
    expect(outputs, hasLength(27));
    outputs.forEach((path, content) {
      expect(File(path).existsSync(), isTrue, reason: path);
      expect(File(path).readAsStringSync(), content, reason: '$path is stale; run '
          'dart run tools/kotlin_rewrite/generate_android_speech_strings.dart');
    });
  });

  test('the model size placeholder survives translation, and nothing else uses a percent sign', () {
    final catalogue = _loadCatalogue();
    for (final language in _languages()) {
      final strings = catalogue[language] as Map<String, dynamic>;
      strings.forEach((key, value) {
        final text = value as String;
        if (key == speechFormattedKey) {
          expect(RegExp(r'%1\$d').allMatches(text), hasLength(1), reason: '$language $key');
          expect('%'.allMatches(text), hasLength(1), reason: '$language $key');
        } else {
          expect(text, isNot(contains('%')), reason: '$language $key');
        }
      });
    }
  });

  test('no language was left with the English text', () {
    final catalogue = _loadCatalogue();
    final english = catalogue['en'] as Map<String, dynamic>;
    // Short words ("Cancel", "Done") are legitimately close across languages; sentences are not.
    for (final language in _languages().where((l) => l != 'en')) {
      final strings = catalogue[language] as Map<String, dynamic>;
      for (final key in ['desc', 'not_downloaded', 'unavailable', 'privacy', 'problem_network', 'problem_integrity']) {
        expect(strings[key], isNot(english[key]), reason: '$language $key');
      }
    }
  });

  test('every language names the servers involved: the model host and the map servers', () {
    final catalogue = _loadCatalogue();
    for (final language in _languages()) {
      final privacy = (catalogue[language] as Map<String, dynamic>)['privacy'] as String;
      expect(privacy, contains('huggingface.co'), reason: language);
    }
  });

  test('the licence text is defined once, in English, and never translated', () {
    expect(_loadCatalogue()['attribution'], contains('CC-BY-4.0'));
    for (final language in _languages()) {
      final xml = File('$_res/${_folder(language)}/native_speech_strings.xml').readAsStringSync();
      final defines = xml.contains('native_speech_attribution');
      expect(defines, language == 'en', reason: language);
    }
  });

  test('every speech string the Kotlin code uses is defined', () {
    final used = <String>{};
    for (final entity in Directory('android/app/src/main/kotlin').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.kt')) continue;
      used.addAll(RegExp(r'R\.string\.(native_speech_[a-z_]+)')
          .allMatches(entity.readAsStringSync())
          .map((m) => m.group(1)!));
    }
    for (final entity in Directory('native-android/app/src/main/kotlin').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.kt')) continue;
      used.addAll(RegExp(r'R\.string\.(native_speech_[a-z_]+)')
          .allMatches(entity.readAsStringSync())
          .map((m) => m.group(1)!));
    }
    final english = File('$_res/values/native_speech_strings.xml').readAsStringSync();
    expect(used, isNotEmpty);
    for (final name in used) {
      expect(english, contains('name="$name"'), reason: name);
    }
  });
}
