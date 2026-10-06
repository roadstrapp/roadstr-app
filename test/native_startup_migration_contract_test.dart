import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// The Kotlin app brings the old app's data over before it shows the map. These checks pin the
// promises that protect a person's profile: the old files are never touched, the new stores are
// written, read back and only then recorded as done, and the real app never shares a file or a key
// with the road-test APK.

const _startup = 'android/app/src/main/kotlin/app/roadstr/startup';
const _roadTest = 'native-android/app/src/main/kotlin/app/roadstr/roadtest';

String _read(String path) => File(path).readAsStringSync();

void main() {
  final startupFiles = Directory(_startup)
      .listSync()
      .whereType<File>()
      .where((file) => file.path.endsWith('.kt'))
      .toList();

  test('the startup code never deletes or rewrites anything the old app left', () {
    expect(startupFiles, isNotEmpty);
    for (final file in startupFiles) {
      final source = file.readAsStringSync();
      for (final forbidden in ['.delete(', 'deleteRecursively', 'renameTo(', 'File(', 'FileOutputStream', 'writeText', 'writeBytes']) {
        // `File(` appears once, to ask whether a legacy file exists.
        if (forbidden == 'File(' && file.path.endsWith('NativeStartupMigration.kt')) continue;
        expect(source, isNot(contains(forbidden)), reason: '${file.path} uses $forbidden');
      }
    }
    final owner = _read('$_startup/NativeStartupMigration.kt');
    expect(owner, contains('File(dataDir, it).isFile'));
    expect(owner, contains('"app_flutter/settings.hive"'));
  });

  test('the profile is written, read back, and only then recorded as imported', () {
    final importer = _read('$_startup/NativeProfileImport.kt');
    final stage = importer.indexOf('override fun stage(snapshot: LegacyStorageSnapshot)');
    final commit = importer.indexOf('override fun commit()');
    final verify = importer.indexOf('override fun verify(snapshot: LegacyStorageSnapshot)');
    expect(stage, greaterThan(0));
    expect(commit, greaterThan(stage));
    expect(verify, greaterThan(commit));
    expect(importer, contains('targets.mismatches(profile)'));
    expect(importer, contains('check(flag.set())'));

    // The shared transaction already orders these, and records the marker last.
    final transaction = _read('android/app/src/main/kotlin/app/roadstr/migration/TransactionalMigration.kt');
    expect(transaction.indexOf('writer.stage(snapshot)'), lessThan(transaction.indexOf('writer.commit()')));
    expect(transaction.indexOf('writer.commit()'), lessThan(transaction.indexOf('writer.verify(snapshot)')));
    expect(transaction.indexOf('writer.verify(snapshot)'), lessThan(transaction.indexOf('marker.markComplete()')));
  });

  test('nothing printable carries a secret', () {
    final importer = _read('$_startup/NativeProfileImport.kt');
    for (final type in ['ImportedIdentity', 'ImportedSync', 'ImportedProfile']) {
      expect(importer, contains('override fun toString(): String = "$type(<redacted>)"'));
    }
    final targets = _read('$_startup/AndroidProfileImportTargets.kt');
    expect(targets, isNot(contains('Log.')));
    expect(importer, isNot(contains('Log.')));
  });

  test('a failure keeps the person out of an empty profile', () {
    final owner = _read('$_startup/NativeStartupMigration.kt');
    // Failed leaves the flag unset and shows the recovery screen; the next launch tries again.
    expect(owner, contains('MigrationOutcome.Failed -> NativeMigrationReadiness.Failed'));
    expect(owner, contains('NativeMigrationReadiness.Failed'));
    final activity = _read('$_startup/NativeAppActivity.kt');
    expect(activity, contains('if (readiness == NativeMigrationReadiness.Ready)'));
    expect(activity, contains('NativeOnboardingFlow('));
    // The shell only reads its stores once the gate is open.
    final base = _read('$_roadTest/NativeRoadTestActivity.kt');
    expect(base, contains('setContent { StartupGate { RoadstrContent() } }'));
    expect(base, contains('val initialFavorites = remember { favoritesStore.load() }'));
  });

  test('the real app and the road-test APK never share a file or a key', () {
    final names = _read('$_roadTest/NativeLiveStoreNames.kt');
    expect(names, contains('const val ROADTEST = "roadtest"'));
    expect(names, contains('const val LIVE = "live"'));
    final activity = _read('$_startup/NativeAppActivity.kt');
    expect(activity, contains('NativeLiveStoreNames(NativeLiveStoreNames.LIVE)'));
    // No store may name a file or alias without going through the prefix.
    for (final file in Directory(_roadTest).listSync().whereType<File>().where((f) => f.path.endsWith('.kt'))) {
      if (file.path.endsWith('NativeLiveStoreNames.kt')) continue;
      final source = file.readAsStringSync();
      expect(source, isNot(contains('"roadtest_')), reason: '${file.path} names a file directly');
      expect(source, isNot(contains('"app.roadstr.roadtest.')), reason: '${file.path} names a key directly');
    }
  });

  test('the legacy reader runs off the main thread with the exact old reader and no deletion', () {
    final owner = _read('$_startup/NativeStartupMigration.kt');
    expect(owner, contains('createLegacyHeadlessSnapshotReader(context, READER_TIMEOUT_MILLIS)'));
    expect(owner, contains('executor.execute(::run)'));
    final dart = _read('lib/migration/legacy_secure_storage_source.dart');
    expect(dart, contains('resetOnError: false'));
  });
}
