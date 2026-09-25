import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/kokoro/kokoro_voices.dart';
import 'package:roadstr/services/piper/piper_voices.dart';

import 'package:roadstr/migration/legacy_migration_bridge_reader.dart';
import 'package:roadstr/migration/legacy_secure_storage_source.dart';
import '../tools/kotlin_rewrite/legacy_snapshot_fixture.dart';
import 'package:roadstr/migration/legacy_storage_contract.dart';

const _rawFixturePath =
    'android/app/src/test/resources/parity/legacy_settings_hive_v1.b64';
const _envelopeFixturePath =
    'android/app/src/test/resources/parity/legacy_snapshot_v1.b64';
const _secret = 'must-not-appear-in-bridge-errors';

void main() {
  test(
      'secure-storage adapter disables wiping and requests backed-up migration',
      () async {
    final options = LegacySecureStorageSource.androidOptions.toMap();
    expect(options['resetOnError'], 'false');
    expect(options['migrateOnAlgorithmChange'], 'true');
    expect(options['migrateWithBackup'], 'true');
    expect(
      legacyVoiceAssetRelativePaths,
      {
        'espeak-ng-data/.roadstr_extracted',
        'kokoro/model_q8f16.onnx',
        'kokoro/tokenizer.json',
        for (final voice in kKokoroVoiceSha256.keys) 'kokoro/$voice.bin',
        'piper/$kPiperModelFile',
        'piper/$kPiperConfigFile',
      },
    );

    final original = buildSyntheticLegacySecureValues();
    final source = LegacySecureStorageSource(readAll: () async => original);
    final snapshot = await source.readAll();
    original.clear();
    expect(snapshot, buildSyntheticLegacySecureValues());

    final failing = LegacySecureStorageSource(
      readAll: () async => throw StateError(_secret),
    );
    await expectLater(
      failing.readAll(),
      throwsA(
        isA<LegacySecureStorageReadException>().having(
          (error) => error.message,
          'message',
          isNot(contains(_secret)),
        ),
      ),
    );
  });

  test('bridge copies Hive and produces the exact shared Kotlin envelope',
      () async {
    final directory = await _createLegacyDocuments();
    try {
      final before = await _snapshotFiles(directory);
      final source = LegacySecureStorageSource(
        readAll: () async => buildSyntheticLegacySecureValues(),
      );

      final encoded = await const LegacyMigrationBridgeReader().readEncoded(
        documentsDirectory: directory,
        readSecureValues: source.readAll,
      );

      expect(encoded, isNotNull);
      expect(
        base64Encode(encoded!),
        File(_envelopeFixturePath).readAsStringSync().trim(),
      );
      expect(await _snapshotFiles(directory), before);
    } finally {
      await directory.delete(recursive: true);
    }
  });

  test('bridge returns no snapshot only when all legacy state is absent',
      () async {
    final directory =
        await Directory.systemTemp.createTemp('roadstr-bridge-empty-');
    try {
      final encoded = await const LegacyMigrationBridgeReader().readEncoded(
        documentsDirectory: directory,
        readSecureValues: () async => const {},
        assetRelativePaths: const [],
      );
      expect(encoded, isNull);
    } finally {
      await directory.delete(recursive: true);
    }
  });

  test('bridge fails closed when Hive and secure state disagree', () async {
    final withHive = await _createLegacyDocuments(writeAssets: false);
    final withoutHive =
        await Directory.systemTemp.createTemp('roadstr-bridge-no-hive-');
    try {
      final before = await _snapshotFiles(withHive);
      await _expectBridgeFailure(
        const LegacyMigrationBridgeReader().readEncoded(
          documentsDirectory: withHive,
          readSecureValues: () async => const {},
          assetRelativePaths: const [],
        ),
      );
      await _expectBridgeFailure(
        const LegacyMigrationBridgeReader().readEncoded(
          documentsDirectory: withoutHive,
          readSecureValues: () async => buildSyntheticLegacySecureValues(),
          assetRelativePaths: const [],
        ),
      );
      await _expectBridgeFailure(
        const LegacyMigrationBridgeReader().readEncoded(
          documentsDirectory: withHive,
          readSecureValues: () async => throw StateError(_secret),
          assetRelativePaths: const [],
        ),
      );
      expect(await _snapshotFiles(withHive), before);
    } finally {
      await withHive.delete(recursive: true);
      await withoutHive.delete(recursive: true);
    }
  });

  test('bridge rejects invalid keys and unknown secure values without leaks',
      () async {
    final directory = await _createLegacyDocuments(writeAssets: false);
    try {
      final before = await _snapshotFiles(directory);
      for (final secureValues in [
        {
          ...buildSyntheticLegacySecureValues(),
          'hive_settings_key': _secret,
        },
        {
          ...buildSyntheticLegacySecureValues(),
          'hive_settings_key': base64Encode(List<int>.filled(32, 9)),
        },
        {
          ...buildSyntheticLegacySecureValues(),
          'future_secret': _secret,
        },
      ]) {
        await _expectBridgeFailure(
          const LegacyMigrationBridgeReader().readEncoded(
            documentsDirectory: directory,
            readSecureValues: () async => secureValues,
            assetRelativePaths: const [],
          ),
        );
      }
      expect(await _snapshotFiles(directory), before);
    } finally {
      await directory.delete(recursive: true);
    }
  });

  test('bridge rejects corrupt Hive without changing the source', () async {
    final directory = await _createLegacyDocuments(writeAssets: false);
    try {
      final settings = File('${directory.path}/settings.hive');
      final corrupt = List<int>.filled((await settings.length()), 0xa5);
      await settings.writeAsBytes(corrupt, flush: true);
      final before = await _snapshotFiles(directory);

      await _expectBridgeFailure(
        const LegacyMigrationBridgeReader().readEncoded(
          documentsDirectory: directory,
          readSecureValues: () async => buildSyntheticLegacySecureValues(),
          assetRelativePaths: const [],
        ),
      );
      expect(await _snapshotFiles(directory), before);
    } finally {
      await directory.delete(recursive: true);
    }
  });

  test('bridge rejects interrupted migration backups and source symlinks',
      () async {
    final interrupted = await _createLegacyDocuments(writeAssets: false);
    final linked =
        await Directory.systemTemp.createTemp('roadstr-bridge-linked-');
    try {
      await File('${interrupted.path}/settings.hive.migration-backup')
          .writeAsString(_secret);
      await _expectBridgeFailure(
        const LegacyMigrationBridgeReader().readEncoded(
          documentsDirectory: interrupted,
          readSecureValues: () async => buildSyntheticLegacySecureValues(),
          assetRelativePaths: const [],
        ),
      );

      if (!Platform.isWindows) {
        final source = File('${linked.path}/source.hive');
        await source.writeAsBytes(_rawFixtureBytes());
        await Link('${linked.path}/settings.hive').create(source.path);
        await _expectBridgeFailure(
          const LegacyMigrationBridgeReader().readEncoded(
            documentsDirectory: linked,
            readSecureValues: () async => buildSyntheticLegacySecureValues(),
            assetRelativePaths: const [],
          ),
        );
      }
    } finally {
      await interrupted.delete(recursive: true);
      await linked.delete(recursive: true);
    }
  });
}

Future<Directory> _createLegacyDocuments({bool writeAssets = true}) async {
  final directory =
      await Directory.systemTemp.createTemp('roadstr-bridge-source-');
  await File('${directory.path}/settings.hive')
      .writeAsBytes(_rawFixtureBytes(), flush: true);
  if (writeAssets) {
    for (final entry in syntheticLegacyAssetContents.entries) {
      final file = File('${directory.path}/${entry.key}');
      await file.parent.create(recursive: true);
      await file.writeAsBytes(entry.value, flush: true);
    }
  }
  return directory;
}

List<int> _rawFixtureBytes() =>
    base64Decode(File(_rawFixturePath).readAsStringSync().trim());

Future<Map<String, List<int>>> _snapshotFiles(Directory directory) async {
  final files = await directory
      .list(recursive: true, followLinks: false)
      .where((entity) => entity is File)
      .cast<File>()
      .toList();
  files.sort((left, right) => left.path.compareTo(right.path));
  return {
    for (final file in files)
      file.path.substring(directory.path.length + 1): await file.readAsBytes(),
  };
}

Future<void> _expectBridgeFailure(Future<Object?> operation) => expectLater(
      operation,
      throwsA(
        isA<LegacyMigrationBridgeException>().having(
          (error) => error.message,
          'message',
          isNot(contains(_secret)),
        ),
      ),
    );
