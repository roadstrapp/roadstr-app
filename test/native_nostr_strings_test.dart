import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_nostr_strings.dart';

void main() {
  final resourceRoot = Directory('android/app/src/main/res');
  final namePattern = RegExp(r'<string name="([^"]+)">([\s\S]*?)</string>');

  test('all 27 Nostr resource sets are current and complete', () {
    final generated = buildAndroidNostrResources();

    expect(generated, hasLength(27));
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = namePattern.allMatches(entry.value).toList();
      final names = strings.map((match) => match.group(1)).toSet();
      expect(names, hasLength(strings.length), reason: entry.key);
      expect(names, contains('native_nostr_action_failed'), reason: entry.key);
    }
  });

  test('every locale defines the same names, so none silently falls back', () {
    final generated = buildAndroidNostrResources();
    final reference = namePattern
        .allMatches(
            generated['${resourceRoot.path}/values/native_nostr_strings.xml']!)
        .map((match) => match.group(1)!)
        .toSet();

    for (final entry in generated.entries) {
      final names = namePattern
          .allMatches(entry.value)
          .map((match) => match.group(1)!)
          .toSet();
      expect(names, reference, reason: entry.key);
    }
  });

  test('the import count keeps its single positional placeholder', () {
    final generated = buildAndroidNostrResources();

    for (final entry in generated.entries) {
      final line = namePattern
          .allMatches(entry.value)
          .firstWhere(
              (match) => match.group(1) == 'native_nostr_import_success')
          .group(2)!;
      expect(RegExp(r'%1\$d').allMatches(line), hasLength(1),
          reason: entry.key);
      expect(line, isNot(contains('{n}')), reason: entry.key);
    }
  });

  test('the moved strings are no longer defined twice', () {
    const moved = [
      'native_settings_dark_map',
      'native_settings_map_engine',
      'native_settings_nwc_save',
      'native_nav_exit_body',
      'native_saved_label',
    ];
    final owners = <String, int>{for (final name in moved) name: 0};
    for (final file in resourceRoot
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) =>
            file.path.endsWith('/values/native_nostr_strings.xml') ||
            file.path.endsWith('/values/native_settings_strings.xml') ||
            file.path.endsWith('/values/native_navigation_strings.xml') ||
            file.path.endsWith('/values/native_saved_places_strings.xml'))) {
      final text = file.readAsStringSync();
      for (final name in moved) {
        if (text.contains('name="$name"')) owners[name] = owners[name]! + 1;
      }
    }

    expect(owners.values, everyElement(1), reason: '$owners');
  });
}
