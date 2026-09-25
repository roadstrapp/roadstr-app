import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:hive/hive.dart';

import 'legacy_snapshot_collector.dart';
import 'legacy_storage_contract.dart';

class LegacyMigrationBridgeException implements Exception {
  const LegacyMigrationBridgeException(this.message);

  final String message;

  @override
  String toString() => 'LegacyMigrationBridgeException: $message';
}

/// Designed to assemble the bounded migration envelope from a dedicated Dart
/// isolate.
///
/// The source Hive file is never opened directly. It is copied to a temporary
/// directory first, then the copy is opened with the exact Hive 2.2.3 cipher.
/// The secure-storage callback may use the dedicated plugin adapter in a future
/// headless entrypoint, but remains injectable for deterministic tests.
class LegacyMigrationBridgeReader {
  const LegacyMigrationBridgeReader();

  static const int maxLegacyHiveSourceBytes = 128 * 1024 * 1024;
  static final RegExp _canonicalHiveKey = RegExp(r'^[A-Za-z0-9+/]{43}=$');

  Future<Uint8List?> readEncoded({
    required Directory documentsDirectory,
    required Future<Map<String, String>> Function() readSecureValues,
    Iterable<String> assetRelativePaths = legacyVoiceAssetRelativePaths,
  }) async {
    final settingsFile = File(
      '${documentsDirectory.path}${Platform.pathSeparator}'
      '$legacySettingsFileName',
    );
    final backupFile = File(
      '${documentsDirectory.path}${Platform.pathSeparator}'
      '$legacySettingsMigrationBackupFileName',
    );

    final settingsType = await _safeType(settingsFile.path);
    final backupType = await _safeType(backupFile.path);
    if (backupType != FileSystemEntityType.notFound) {
      throw const LegacyMigrationBridgeException(
        'Legacy Hive encryption migration is incomplete',
      );
    }

    final hasSettings = settingsType != FileSystemEntityType.notFound;
    if (hasSettings && settingsType != FileSystemEntityType.file) {
      throw const LegacyMigrationBridgeException(
        'Legacy Hive source is not a regular file',
      );
    }

    final secureValues = await _readAndValidateSecureValues(readSecureValues);
    if (!hasSettings && secureValues.isEmpty) return null;
    if (!hasSettings || secureValues['hive_settings_key'] == null) {
      throw const LegacyMigrationBridgeException(
        'Legacy storage is incomplete',
      );
    }
    final hiveKey = _decodeHiveKey(secureValues['hive_settings_key']!);
    late final Directory temporaryDirectory;
    try {
      temporaryDirectory =
          await Directory.systemTemp.createTemp('roadstr-legacy-bridge-');
    } catch (_) {
      throw const LegacyMigrationBridgeException(
        'Legacy migration workspace could not be created',
      );
    }
    var hiveInitialized = false;
    try {
      final copiedSettings = File(
        '${temporaryDirectory.path}${Platform.pathSeparator}'
        '$legacySettingsFileName',
      );
      await _copyStableSource(settingsFile, copiedSettings);

      Hive.init(temporaryDirectory.path);
      hiveInitialized = true;
      final box = await _openCopiedBox(hiveKey);

      final assets = await LegacyAssetManifestCollector.collect(
        documentsDirectory: documentsDirectory,
        relativePaths: assetRelativePaths,
      );
      return LegacySnapshotCollector().collectEncoded(
        settingsBox: box,
        readSecureValues: () async => secureValues,
        assets: assets,
      );
    } on LegacyMigrationBridgeException {
      rethrow;
    } on LegacySnapshotCollectionException {
      throw const LegacyMigrationBridgeException(
        'Legacy snapshot failed validation',
      );
    } on FileSystemException {
      throw const LegacyMigrationBridgeException(
        'Legacy storage could not be copied safely',
      );
    } catch (_) {
      throw const LegacyMigrationBridgeException(
        'Legacy snapshot could not be assembled',
      );
    } finally {
      var cleanupFailed = false;
      try {
        if (hiveInitialized) await Hive.close();
      } catch (_) {
        cleanupFailed = true;
      }
      try {
        if (await temporaryDirectory.exists()) {
          await temporaryDirectory.delete(recursive: true);
        }
      } catch (_) {
        cleanupFailed = true;
      }
      if (cleanupFailed) {
        throw const LegacyMigrationBridgeException(
          'Legacy migration workspace cleanup failed',
        );
      }
    }
  }

  Future<Map<String, String>> _readAndValidateSecureValues(
    Future<Map<String, String>> Function() readSecureValues,
  ) async {
    try {
      return LegacySecureValueValidator.validate(await readSecureValues());
    } catch (_) {
      throw const LegacyMigrationBridgeException(
        'Legacy secure storage could not be read',
      );
    }
  }

  Future<FileSystemEntityType> _safeType(String path) async {
    try {
      return await FileSystemEntity.type(path, followLinks: false);
    } on FileSystemException {
      throw const LegacyMigrationBridgeException(
        'Legacy storage could not be inspected',
      );
    }
  }

  List<int> _decodeHiveKey(String encoded) {
    if (!_canonicalHiveKey.hasMatch(encoded)) {
      throw const LegacyMigrationBridgeException(
        'Legacy Hive encryption key is invalid',
      );
    }
    try {
      final decoded = base64Decode(encoded);
      if (decoded.length != 32 || base64Encode(decoded) != encoded) {
        throw const LegacyMigrationBridgeException(
          'Legacy Hive encryption key is invalid',
        );
      }
      return decoded;
    } on FormatException {
      throw const LegacyMigrationBridgeException(
        'Legacy Hive encryption key is invalid',
      );
    }
  }

  Future<Box<dynamic>> _openCopiedBox(List<int> hiveKey) {
    final result = Completer<Box<dynamic>>();
    runZonedGuarded(
      () async {
        try {
          final box = await Hive.openBox<dynamic>(
            legacySettingsBoxName,
            encryptionCipher: HiveAesCipher(hiveKey),
            crashRecovery: false,
          );
          if (!result.isCompleted) result.complete(box);
        } catch (_) {
          if (!result.isCompleted) {
            result.completeError(
              const LegacyMigrationBridgeException(
                'Legacy Hive source could not be opened',
              ),
            );
          }
        }
      },
      (_, __) {
        // Hive 2.2.3 can emit a second asynchronous lock-cleanup error after
        // reporting a checksum failure. Keep both failures inside this zone.
        if (!result.isCompleted) {
          result.completeError(
            const LegacyMigrationBridgeException(
              'Legacy Hive source could not be opened',
            ),
          );
        }
      },
    );
    return result.future;
  }

  Future<void> _copyStableSource(File source, File target) async {
    final before = await source.stat();
    if (before.size <= 0 || before.size > maxLegacyHiveSourceBytes) {
      throw const LegacyMigrationBridgeException(
        'Legacy Hive source has an invalid size',
      );
    }

    await source.openRead().pipe(target.openWrite());
    final copied = await target.stat();
    final after = await source.stat();
    if (copied.size != before.size ||
        after.size != before.size ||
        after.modified != before.modified ||
        after.changed != before.changed) {
      throw const LegacyMigrationBridgeException(
        'Legacy Hive source changed while it was copied',
      );
    }
  }
}
