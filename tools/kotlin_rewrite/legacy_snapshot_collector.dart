import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:hive/hive.dart';

import 'legacy_snapshot_envelope.dart';
import 'legacy_storage_contract.dart';

class LegacySnapshotCollectionException implements Exception {
  const LegacySnapshotCollectionException(this.message);

  final String message;

  @override
  String toString() => 'LegacySnapshotCollectionException: $message';
}

/// Strict conversion from Hive's dynamic object graph to the normalized
/// string boundary consumed by native migration.
///
/// Existing top-level strings are preserved byte-for-byte. Other supported
/// values become canonical JSON with recursively sorted map keys. Unknown
/// objects, non-string map keys, non-finite doubles and excessive nesting fail
/// closed instead of being coerced with `toString()`.
abstract final class LegacyHiveValueNormalizer {
  static const int maxNestingDepth = 32;
  static const int maxVisitedValues = 100000;

  static String normalize(Object? value) {
    if (value is String) return value;
    final budget = _NormalizationBudget();
    final normalized = _normalizeJsonValue(value, depth: 0, budget: budget);
    return jsonEncode(normalized);
  }

  static Object? _normalizeJsonValue(
    Object? value, {
    required int depth,
    required _NormalizationBudget budget,
  }) {
    if (depth > maxNestingDepth) {
      throw const LegacySnapshotCollectionException(
        'Hive value exceeds the nesting limit',
      );
    }
    budget.consume();

    if (value == null || value is bool || value is int || value is String) {
      return value;
    }
    if (value is double) {
      if (!value.isFinite) {
        throw const LegacySnapshotCollectionException(
          'Hive value contains a non-finite number',
        );
      }
      return value;
    }
    if (value is List) {
      return [
        for (final item in value)
          _normalizeJsonValue(item, depth: depth + 1, budget: budget),
      ];
    }
    if (value is Map) {
      final entries = <MapEntry<String, Object?>>[];
      for (final entry in value.entries) {
        final key = entry.key;
        if (key is! String) {
          throw const LegacySnapshotCollectionException(
            'Hive map contains a non-string key',
          );
        }
        entries.add(MapEntry(
          key,
          _normalizeJsonValue(
            entry.value,
            depth: depth + 1,
            budget: budget,
          ),
        ));
      }
      entries.sort((left, right) => left.key.compareTo(right.key));
      return <String, Object?>{
        for (final entry in entries) entry.key: entry.value,
      };
    }
    throw const LegacySnapshotCollectionException(
      'Hive value contains an unsupported type',
    );
  }
}

class _NormalizationBudget {
  int _visited = 0;

  void consume() {
    _visited++;
    if (_visited > LegacyHiveValueNormalizer.maxVisitedValues) {
      throw const LegacySnapshotCollectionException(
        'Hive value exceeds the collection limit',
      );
    }
  }
}

/// Reads an already-open legacy Hive box and an injectable secure-storage
/// snapshot without modifying either source.
class LegacySnapshotCollector {
  const LegacySnapshotCollector();

  Future<LegacyEnvelopeSnapshot> collect({
    required Box<dynamic> settingsBox,
    required Future<Map<String, String>> Function() readSecureValues,
    List<LegacyEnvelopeAsset> assets = const [],
  }) async {
    final snapshot = await _collectSnapshot(
      settingsBox: settingsBox,
      readSecureValues: readSecureValues,
      assets: assets,
    );
    _encodeSnapshot(snapshot);
    return snapshot;
  }

  Future<Uint8List> collectEncoded({
    required Box<dynamic> settingsBox,
    required Future<Map<String, String>> Function() readSecureValues,
    List<LegacyEnvelopeAsset> assets = const [],
  }) async {
    final snapshot = await _collectSnapshot(
      settingsBox: settingsBox,
      readSecureValues: readSecureValues,
      assets: assets,
    );
    return _encodeSnapshot(snapshot);
  }

  Future<LegacyEnvelopeSnapshot> _collectSnapshot({
    required Box<dynamic> settingsBox,
    required Future<Map<String, String>> Function() readSecureValues,
    required List<LegacyEnvelopeAsset> assets,
  }) async {
    if (!settingsBox.isOpen || settingsBox.name != legacySettingsBoxName) {
      throw const LegacySnapshotCollectionException(
        'Legacy settings box is not open with the expected name',
      );
    }

    final rawKeys = settingsBox.keys.toList(growable: false);
    if (rawKeys.length > LegacyEnvelopeLimits.maxEntriesPerStore) {
      throw const LegacySnapshotCollectionException(
        'Legacy Hive source contains too many entries',
      );
    }
    final ordinaryValues = <String, String>{};
    for (final rawKey in rawKeys) {
      if (rawKey is! String) {
        throw const LegacySnapshotCollectionException(
          'Legacy Hive source contains a non-string key',
        );
      }
      if (!isKnownLegacyHiveKey(rawKey)) {
        throw const LegacySnapshotCollectionException(
          'Legacy Hive source contains unsupported keys',
        );
      }
      dynamic value;
      try {
        value = settingsBox.get(rawKey);
      } catch (_) {
        throw const LegacySnapshotCollectionException(
          'Legacy Hive value could not be read',
        );
      }
      final normalized = LegacyHiveValueNormalizer.normalize(value);
      if (utf8.encode(rawKey).length > LegacyEnvelopeLimits.maxKeyBytes ||
          utf8.encode(normalized).length >
              LegacyEnvelopeLimits.maxOrdinaryValueBytes) {
        throw const LegacySnapshotCollectionException(
          'Legacy Hive entry exceeds the envelope limit',
        );
      }
      ordinaryValues[rawKey] = normalized;
    }

    late final Map<String, String> secureValues;
    try {
      secureValues = await readSecureValues();
    } catch (_) {
      throw const LegacySnapshotCollectionException(
        'Legacy secure storage could not be read',
      );
    }
    if (secureValues.length > LegacyEnvelopeLimits.maxEntriesPerStore ||
        secureValues.keys.any((key) => !legacySecureKeys.contains(key))) {
      throw const LegacySnapshotCollectionException(
        'Legacy secure storage contains unsupported keys',
      );
    }
    for (final entry in secureValues.entries) {
      if (utf8.encode(entry.key).length > LegacyEnvelopeLimits.maxKeyBytes ||
          utf8.encode(entry.value).length >
              LegacyEnvelopeLimits.maxSecureValueBytes) {
        throw const LegacySnapshotCollectionException(
          'Legacy secure entry exceeds the envelope limit',
        );
      }
    }

    return LegacyEnvelopeSnapshot(
      schemaVersion: legacySnapshotSchemaVersion,
      ordinaryValues: ordinaryValues,
      secureValues: secureValues,
      identity: LegacyEnvelopeIdentity(
        publicKeyHex: secureValues['nostr_pub_hex'],
        flavor: secureValues['nostr_flavor'],
        privateKeyHex: secureValues['nostr_priv_hex'],
      ),
      assets: assets,
    );
  }

  Uint8List _encodeSnapshot(LegacyEnvelopeSnapshot snapshot) {
    try {
      return LegacySnapshotEnvelopeCodec.encode(snapshot);
    } on LegacyEnvelopeException {
      throw const LegacySnapshotCollectionException(
        'Collected legacy snapshot exceeds the envelope contract',
      );
    }
  }
}

/// Hashes only explicitly selected, app-private files below known voice roots.
/// It never follows symlinks and never walks an unbounded directory tree.
abstract final class LegacyAssetManifestCollector {
  static const Set<String> allowedRoots = {
    'kokoro',
    'piper',
    'espeak-ng-data',
  };

  static Future<List<LegacyEnvelopeAsset>> collect({
    required Directory documentsDirectory,
    required Iterable<String> relativePaths,
  }) async {
    final requested = relativePaths.toList(growable: false);
    if (requested.length > LegacyEnvelopeLimits.maxAssets) {
      throw const LegacySnapshotCollectionException(
        'Legacy asset manifest contains too many paths',
      );
    }
    if (requested.toSet().length != requested.length) {
      throw const LegacySnapshotCollectionException(
        'Legacy asset manifest contains duplicate paths',
      );
    }

    final assets = <LegacyEnvelopeAsset>[];
    for (final relativePath in requested) {
      try {
        final components = _validateRelativePath(relativePath);
        var currentPath = documentsDirectory.path;
        var missing = false;
        for (var index = 0; index < components.length; index++) {
          currentPath =
              '$currentPath${Platform.pathSeparator}${components[index]}';
          final type =
              await FileSystemEntity.type(currentPath, followLinks: false);
          if (type == FileSystemEntityType.link) {
            throw const LegacySnapshotCollectionException(
              'Legacy asset path traverses a symbolic link',
            );
          }
          final isFinal = index == components.length - 1;
          if (type == FileSystemEntityType.notFound) {
            missing = true;
            break;
          }
          if (!isFinal && type != FileSystemEntityType.directory) {
            throw const LegacySnapshotCollectionException(
              'Legacy asset parent is not a directory',
            );
          }
          if (isFinal && type != FileSystemEntityType.file) {
            throw const LegacySnapshotCollectionException(
              'Legacy asset is not a regular file',
            );
          }
        }
        if (missing) continue;

        final file = File(currentPath);
        final before = await file.stat();
        if (before.size < 0 ||
            before.size > LegacyEnvelopeLimits.maxAssetSizeBytes) {
          throw const LegacySnapshotCollectionException(
            'Legacy asset exceeds the size limit',
          );
        }
        final digest = await sha256.bind(file.openRead()).first;
        final after = await file.stat();
        if (after.size != before.size) {
          throw const LegacySnapshotCollectionException(
            'Legacy asset changed while it was hashed',
          );
        }
        assets.add(LegacyEnvelopeAsset(
          relativePath: relativePath,
          sizeBytes: before.size,
          sha256: digest.toString(),
        ));
      } on LegacySnapshotCollectionException {
        rethrow;
      } on FileSystemException {
        throw const LegacySnapshotCollectionException(
          'Legacy asset could not be read',
        );
      }
    }
    assets
        .sort((left, right) => left.relativePath.compareTo(right.relativePath));
    return assets;
  }

  static List<String> _validateRelativePath(String relativePath) {
    if (relativePath.isEmpty ||
        relativePath.startsWith('/') ||
        relativePath.startsWith('\\') ||
        relativePath.contains('\\') ||
        utf8.encode(relativePath).length >
            LegacyEnvelopeLimits.maxAssetPathBytes) {
      throw const LegacySnapshotCollectionException(
        'Legacy asset path is unsafe',
      );
    }
    final components = relativePath.split('/');
    if (components.length < 2 ||
        !allowedRoots.contains(components.first) ||
        components.any((part) => part.isEmpty || part == '.' || part == '..')) {
      throw const LegacySnapshotCollectionException(
        'Legacy asset path is outside the allowed roots',
      );
    }
    return components;
  }
}
