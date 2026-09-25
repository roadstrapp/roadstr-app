import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

import '../tools/kotlin_rewrite/legacy_raw_hive_fixture.dart';
import '../tools/kotlin_rewrite/legacy_snapshot_collector.dart';
import '../tools/kotlin_rewrite/legacy_snapshot_envelope.dart';
import '../tools/kotlin_rewrite/legacy_snapshot_fixture.dart';

const _rawFixturePath =
    'android/app/src/test/resources/parity/legacy_settings_hive_v1.b64';
const _envelopeFixturePath =
    'android/app/src/test/resources/parity/legacy_snapshot_v1.b64';

void main() {
  test('encrypted Hive fixture generation is reproducible', () async {
    final generated = await generateSyntheticEncryptedHiveFixture();
    final committed = File(_rawFixturePath).readAsStringSync().trim();

    expect(base64Encode(generated), committed);
  });

  test('raw encrypted Hive reaches the exact shared Kotlin envelope', () async {
    await _withCommittedHiveFixture((directory, box) async {
      final rawBefore = Map<dynamic, dynamic>.of(box.toMap());
      expect(box.get('favorites'), isA<List<dynamic>>());
      expect((box.get('favorites') as List).single, isA<String>());
      expect(
        (box.get('activity_inbox_$fixturePublicKeyHex') as List).single,
        isA<Map<dynamic, dynamic>>(),
      );

      await _writeSyntheticAssets(directory);
      final assets = await LegacyAssetManifestCollector.collect(
        documentsDirectory: directory,
        relativePaths: [
          ...syntheticLegacyAssetContents.keys,
          'kokoro/optional-missing.bin',
        ],
      );
      final collector = LegacySnapshotCollector();
      final snapshot = await collector.collect(
        settingsBox: box,
        readSecureValues: () async => buildSyntheticLegacySecureValues(),
        assets: assets,
      );
      final expected = buildSyntheticLegacySnapshot();

      expect(snapshot.ordinaryValues, expected.ordinaryValues);
      expect(snapshot.secureValues, expected.secureValues);
      expect(snapshot.identity.publicKeyHex, expected.identity.publicKeyHex);
      expect(snapshot.identity.privateKeyHex, expected.identity.privateKeyHex);
      expect(snapshot.identity.flavor, expected.identity.flavor);
      expect(
        snapshot.assets.map(_assetTuple),
        orderedEquals(expected.assets.map(_assetTuple)),
      );
      expect(box.toMap(), rawBefore, reason: 'collector must be read-only');

      final encoded = LegacySnapshotEnvelopeCodec.encode(snapshot);
      expect(
        base64Encode(encoded),
        File(_envelopeFixturePath).readAsStringSync().trim(),
      );
    });
  });

  test('raw Hive exposes key names but not stored private values', () {
    final bytes = base64Decode(File(_rawFixturePath).readAsStringSync().trim());
    final rawText = latin1.decode(bytes);
    expect(rawText, contains('favorites'));
    for (final plaintext in [
      'Fixture home',
      '45.4642',
      'fixture-legacy-passphrase-not-real',
    ]) {
      expect(rawText, isNot(contains(plaintext)));
    }
  });

  test('normalizer preserves strings and canonicalizes supported Hive values',
      () {
    expect(LegacyHiveValueNormalizer.normalize('raw-json-string'),
        'raw-json-string');
    expect(LegacyHiveValueNormalizer.normalize(true), 'true');
    expect(LegacyHiveValueNormalizer.normalize(42), '42');
    expect(LegacyHiveValueNormalizer.normalize(0.25), '0.25');
    expect(
      LegacyHiveValueNormalizer.normalize({
        'z': 1,
        'a': [
          {'y': false, 'x': null}
        ],
      }),
      '{"a":[{"x":null,"y":false}],"z":1}',
    );
  });

  test('normalizer rejects unsafe values without rendering them in errors', () {
    final deeplyNested = <dynamic>[];
    var cursor = deeplyNested;
    for (var depth = 0;
        depth <= LegacyHiveValueNormalizer.maxNestingDepth;
        depth++) {
      final next = <dynamic>[];
      cursor.add(next);
      cursor = next;
    }

    for (final value in [
      double.nan,
      {1: 'non-string-key'},
      _SecretObject(),
      deeplyNested,
    ]) {
      expect(
        () => LegacyHiveValueNormalizer.normalize(value),
        throwsA(
          isA<LegacySnapshotCollectionException>().having(
            (error) => error.message,
            'message',
            isNot(contains(_SecretObject.secret)),
          ),
        ),
      );
    }
  });

  test('collector fails closed on unknown Hive and secure keys', () async {
    await _withPlainHiveBox({'future_key': _SecretObject.secret}, (box) async {
      await expectLater(
        LegacySnapshotCollector().collect(
          settingsBox: box,
          readSecureValues: () async => const {},
        ),
        throwsA(
          isA<LegacySnapshotCollectionException>().having(
            (error) => error.message,
            'message',
            isNot(contains(_SecretObject.secret)),
          ),
        ),
      );
    });

    await _withPlainHiveBox({'language': 'it'}, (box) async {
      await expectLater(
        LegacySnapshotCollector().collect(
          settingsBox: box,
          readSecureValues: () async => {
            'future_secret': _SecretObject.secret,
          },
        ),
        throwsA(isA<LegacySnapshotCollectionException>()),
      );
    });

    await _withPlainHiveBox({7: _SecretObject.secret}, (box) async {
      await expectLater(
        LegacySnapshotCollector().collect(
          settingsBox: box,
          readSecureValues: () async => const {},
        ),
        throwsA(
          isA<LegacySnapshotCollectionException>().having(
            (error) => error.message,
            'message',
            isNot(contains(_SecretObject.secret)),
          ),
        ),
      );
    });

    await _withPlainHiveBox({'language': 'it'}, (box) async {
      await expectLater(
        LegacySnapshotCollector().collect(
          settingsBox: box,
          readSecureValues: () async => throw StateError(
            _SecretObject.secret,
          ),
        ),
        throwsA(
          isA<LegacySnapshotCollectionException>().having(
            (error) => error.message,
            'message',
            isNot(contains(_SecretObject.secret)),
          ),
        ),
      );
    });

    await _withPlainHiveBox({'language': 'it'}, (box) async {
      const duplicateAsset = LegacyEnvelopeAsset(
        relativePath: 'kokoro/model.onnx',
        sizeBytes: 0,
        sha256:
            '0000000000000000000000000000000000000000000000000000000000000000',
      );
      await expectLater(
        LegacySnapshotCollector().collect(
          settingsBox: box,
          readSecureValues: () async => const {},
          assets: const [duplicateAsset, duplicateAsset],
        ),
        throwsA(isA<LegacySnapshotCollectionException>()),
      );
    });
  });

  test('asset manifest rejects traversal and symbolic links', () async {
    final directory =
        await Directory.systemTemp.createTemp('roadstr-asset-guard-');
    try {
      await expectLater(
        LegacyAssetManifestCollector.collect(
          documentsDirectory: directory,
          relativePaths: const ['../outside'],
        ),
        throwsA(isA<LegacySnapshotCollectionException>()),
      );

      if (!Platform.isWindows) {
        final outside = File('${directory.path}/outside');
        await outside.writeAsString(_SecretObject.secret);
        final kokoro = Directory('${directory.path}/kokoro');
        await kokoro.create();
        await Link('${kokoro.path}/model_q8f16.onnx').create(outside.path);
        await expectLater(
          LegacyAssetManifestCollector.collect(
            documentsDirectory: directory,
            relativePaths: const ['kokoro/model_q8f16.onnx'],
          ),
          throwsA(isA<LegacySnapshotCollectionException>()),
        );
      }
    } finally {
      await directory.delete(recursive: true);
    }
  });
}

Future<void> _withCommittedHiveFixture(
  Future<void> Function(Directory directory, Box<dynamic> box) body,
) async {
  final directory = await _materializeRawFixture();
  try {
    Hive.init(directory.path);
    final box = await Hive.openBox<dynamic>(
      'settings',
      encryptionCipher: HiveAesCipher(fixtureHiveKeyBytes),
      crashRecovery: false,
    );
    await body(directory, box);
  } finally {
    await Hive.close();
    await directory.delete(recursive: true);
  }
}

Future<Directory> _materializeRawFixture() async {
  final directory =
      await Directory.systemTemp.createTemp('roadstr-raw-hive-read-');
  final bytes = base64Decode(File(_rawFixturePath).readAsStringSync().trim());
  await File('${directory.path}/settings.hive')
      .writeAsBytes(bytes, flush: true);
  return directory;
}

Future<void> _withPlainHiveBox(
  Map<dynamic, dynamic> values,
  Future<void> Function(Box<dynamic> box) body,
) async {
  final directory =
      await Directory.systemTemp.createTemp('roadstr-collector-failure-');
  try {
    Hive.init(directory.path);
    final box = await Hive.openBox<dynamic>('settings');
    await box.putAll(values);
    await body(box);
  } finally {
    await Hive.close();
    await directory.delete(recursive: true);
  }
}

Future<void> _writeSyntheticAssets(Directory directory) async {
  for (final entry in syntheticLegacyAssetContents.entries) {
    final file = File('${directory.path}/${entry.key}');
    await file.parent.create(recursive: true);
    await file.writeAsBytes(entry.value, flush: true);
  }
}

String _assetTuple(LegacyEnvelopeAsset asset) =>
    '${asset.relativePath}|${asset.sizeBytes}|${asset.sha256}';

class _SecretObject {
  static const secret = 'must-not-appear-in-errors';

  @override
  String toString() => secret;
}
