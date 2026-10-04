import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// flutter_secure_storage deletes protected data after a decryption error
/// unless told not to, and one transient Keystore failure is enough to cause
/// it. Every access to the store must therefore go through the single handle
/// that turns that off — see lib/services/app_secure_storage.dart.
void main() {
  test('the shared handle disables resetOnError', () {
    final source =
        File('lib/services/app_secure_storage.dart').readAsStringSync();
    expect(source, contains('resetOnError: false'));
    expect(source, contains('migrateWithBackup: true'));
  });

  test('no screen or service builds its own default-option storage', () {
    final offenders = <String>[];
    final constructor = RegExp(r'FlutterSecureStorage\s*\(');
    for (final entity in Directory('lib').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.dart')) continue;
      // The shared handle, and the migration reader that sets its own options.
      if (entity.path.endsWith('services/app_secure_storage.dart') ||
          entity.path.endsWith('migration/legacy_secure_storage_source.dart')) {
        continue;
      }
      final code = entity
          .readAsLinesSync()
          .where((line) => !line.trimLeft().startsWith('//'))
          .join('\n');
      if (constructor.hasMatch(code)) offenders.add(entity.path);
    }
    expect(offenders, isEmpty,
        reason: 'use appSecureStorage: a default FlutterSecureStorage() '
            'erases the Nostr key and the settings key on a transient error');
  });
}
