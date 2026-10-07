import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The Kotlin app reads these files too (the Android string generators), so a key that exists only in
/// English shows up untranslated in both apps.
void main() {
  final directory = Directory('lib/l10n');
  final english = _load(File('lib/l10n/app_en.arb'));
  final locales = directory
      .listSync()
      .whereType<File>()
      .where((file) => RegExp(r'app_[a-z]{2}\.arb$').hasMatch(file.path))
      .where((file) => !file.path.endsWith('app_en.arb'))
      .toList()
    ..sort((a, b) => a.path.compareTo(b.path));

  test('there are 26 translations next to the English template', () {
    expect(locales, hasLength(26));
  });

  for (final file in locales) {
    final code = RegExp(r'app_([a-z]{2})\.arb$').firstMatch(file.path)![1];

    test('$code has every key of the English template', () {
      final translated = _load(file);
      final missing =
          english.keys.where((key) => !translated.containsKey(key)).toList();
      expect(missing, isEmpty);
    });

    test('$code keeps the placeholders of the English text', () {
      final translated = _load(file);
      final broken = <String>[];
      for (final entry in english.entries) {
        final value = translated[entry.key];
        if (value == null) continue;
        if (!_sameSet(_placeholders(entry.value), _placeholders(value))) {
          broken.add(entry.key);
        }
      }
      expect(broken, isEmpty);
    });

    test('$code translated the first screens a new person sees', () {
      final translated = _load(file);
      final untranslated = english.keys
          .where((key) => key.startsWith('onboarding'))
          // Same in every language by nature: the key prefix a pasted private key starts with.
          .where((key) => key != 'onboardingNsecHint')
          // Short words that several languages share with English ("Download", "Start").
          .where((key) => english[key]!.split(' ').length > 3)
          .where((key) => translated[key] == english[key])
          .toList();
      expect(untranslated, isEmpty);
    });
  }
}

Map<String, String> _load(File file) {
  final decoded = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  return {
    for (final entry in decoded.entries)
      if (!entry.key.startsWith('@') && entry.value is String)
        entry.key: entry.value as String,
  };
}

Set<String> _placeholders(String text) =>
    RegExp(r'\{[A-Za-z0-9_]+\}').allMatches(text).map((m) => m[0]!).toSet();

bool _sameSet(Set<String> a, Set<String> b) =>
    a.length == b.length && a.containsAll(b);
