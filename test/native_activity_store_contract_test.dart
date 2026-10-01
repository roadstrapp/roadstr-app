import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final store = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativeActivityStore.kt',
  );
  final snapshot = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativeSnapshotStore.kt',
  );
  final marker = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'FileNativePersistence.kt',
  );

  test('native activity persistence preserves Flutter dynamic shapes', () {
    final service = File('lib/services/activity_notification_service.dart')
        .readAsStringSync();
    final relay =
        File('lib/services/nostr_relay_service.dart').readAsStringSync();
    final native = store.readAsStringSync();

    expect(service, contains('static const _maxEntries = 100'));
    expect(service, contains("'activity_inbox_\$pubkey'"));
    expect(service, contains('b.createdAt.compareTo(a.createdAt)'));
    expect(service, contains('items.removeRange(_maxEntries, items.length)'));
    expect(relay, contains("'activity_\${kind}_cursor_\$pubkey'"));
    expect(relay, contains("_activityCursorKey('zap', pub)"));
    expect(relay, contains("_activityCursorKey('confirmation', pub)"));
    expect(native, contains('NativeActivityInboxProtocol.decodeNormalized'));
    expect(native, contains('NativeActivityCursorProtocol.advance'));
    expect(native, contains('NativeActivityInboxProtocol.MAX_ENTRIES'));
  });

  test('activity state uses an independent encrypted recoverable file', () {
    final source = store.readAsStringSync();

    expect(source, contains('"activity_state_v1.bin"'));
    expect(source, contains('"RSTRACT1"'));
    expect(source, contains('"RSTRACG1"'));
    expect(source, contains('"roadstr-native-activity-v1"'));
    expect(source, contains('AndroidKeystoreAead(keyAlias)'));
    expect(source, contains('RecoverableAtomicFile('));
    expect(source, contains('file.stage('));
    expect(source, contains('file.commit()'));
    expect(source, contains('Native activity reopen verification failed'));
    expect(source, contains('store is not initialized'));
    expect(source, isNot(contains('Hive.')));
    expect(source, isNot(contains('SharedPreferences')));
  });

  test('migration strips dynamic rows and marker v4 binds ciphertext', () {
    final snapshotSource = snapshot.readAsStringSync();
    final markerSource = marker.readAsStringSync();
    final runtime = File(
      'android/app/src/main/kotlin/app/roadstr/migration/'
      'NativeMigrationRuntime.kt',
    ).readAsStringSync();

    expect(
      snapshotSource,
      contains('!LegacyStorageContract.isDynamicKey(it)'),
    );
    expect(snapshotSource, contains('activityStore?.stageLegacy'));
    expect(snapshotSource, contains('activityStore?.verifyLegacy'));
    expect(markerSource, contains('"RSTRMIG4"'));
    expect(
      markerSource,
      contains('activityVerifier?.committedCiphertextDigest()'),
    );
    expect(markerSource, contains('ACTIVITY_DIGEST_DOMAIN'));
    expect(runtime, contains('FileNativeActivityStore(paths)'));
    expect(runtime, contains('activityVerifier = activityStore'));
  });

  test('activity persistence remains dormant outside migration', () {
    final shell = File(
      'android/app/src/main/kotlin/app/roadstr/feature/home/'
      'NativeRoadstrShell.kt',
    ).readAsStringSync();
    final activity = File(
      'android/app/src/main/kotlin/app/roadstr/MainActivity.kt',
    ).readAsStringSync();

    expect(shell, isNot(contains('FileNativeActivityStore')));
    expect(activity, isNot(contains('FileNativeActivityStore')));
    expect(activity, isNot(contains('NATIVE_ACTIVITY_FILE')));
  });
}
