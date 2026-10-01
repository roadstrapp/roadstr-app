import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final store = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativePendingReportStore.kt',
  );
  final snapshot = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativeSnapshotStore.kt',
  );
  final marker = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'FileNativePersistence.kt',
  );

  test('native queue preserves the exact Flutter Hive and FIFO contracts', () {
    final queue =
        File('lib/services/nostr_pending_report_queue.dart').readAsStringSync();
    final relay =
        File('lib/services/nostr_relay_service.dart').readAsStringSync();
    final native = store.readAsStringSync();

    expect(queue, contains('Exact Hive-facing representation'));
    expect(queue, contains('entries.map(jsonEncode).toList'));
    expect(queue, contains('for (final entry in pending)'));
    expect(queue, contains('remaining.add(entry)'));
    expect(relay, contains("_pendingReportsKey = 'pending_road_reports'"));
    expect(native, contains('NostrPendingReportQueue.flush('));
    expect(native, contains('PendingRoadReport::storageJson'));
    expect(native, contains('MAX_ROWS = 100_000'));
  });

  test('pending reports use a private encrypted recoverable file', () {
    final source = store.readAsStringSync();

    expect(source, contains('"pending_reports_v1.bin"'));
    expect(source, contains('"RSTRPND1"'));
    expect(source, contains('"RSTRPQG1"'));
    expect(source, contains('"roadstr-native-pending-reports-v1"'));
    expect(source, contains('AndroidKeystoreAead(keyAlias)'));
    expect(source, contains('RecoverableAtomicFile('));
    expect(source, contains('file.stage('));
    expect(source, contains('file.commit()'));
    expect(source, contains('store is not initialized'));
    expect(source, isNot(contains('Hive.')));
    expect(source, isNot(contains('SharedPreferences')));
  });

  test('migration strips queue rows and marker v5 binds ciphertext', () {
    final snapshotSource = snapshot.readAsStringSync();
    final markerSource = marker.readAsStringSync();
    final runtime = File(
      'android/app/src/main/kotlin/app/roadstr/migration/'
      'NativeMigrationRuntime.kt',
    ).readAsStringSync();

    expect(snapshotSource, contains('it != PENDING_REPORTS_KEY'));
    expect(snapshotSource, contains('pendingReportStore?.stageLegacy'));
    expect(snapshotSource, contains('pendingReportStore?.verifyLegacy'));
    expect(markerSource, contains('"RSTRMIG5"'));
    expect(
      markerSource,
      contains('pendingReportVerifier?.committedCiphertextDigest()'),
    );
    expect(markerSource, contains('PENDING_REPORT_DIGEST_DOMAIN'));
    expect(runtime, contains('FileNativePendingReportStore(paths)'));
    expect(runtime, contains('pendingReportVerifier = pendingReportStore'));
  });

  test('pending-report persistence remains dormant outside migration', () {
    final shell = File(
      'android/app/src/main/kotlin/app/roadstr/feature/home/'
      'NativeRoadstrShell.kt',
    ).readAsStringSync();
    final activity = File(
      'android/app/src/main/kotlin/app/roadstr/MainActivity.kt',
    ).readAsStringSync();

    expect(shell, isNot(contains('FileNativePendingReportStore')));
    expect(activity, isNot(contains('FileNativePendingReportStore')));
    expect(activity, isNot(contains('NATIVE_PENDING_REPORT_FILE')));
  });
}
