import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/legacy_snapshot_envelope.dart';
import '../tools/kotlin_rewrite/legacy_snapshot_fixture.dart';

const _fixturePath =
    'android/app/src/test/resources/parity/legacy_snapshot_v1.b64';

void main() {
  test('Dart producer exactly matches the committed Kotlin fixture', () {
    final committed = File(_fixturePath).readAsStringSync().trim();
    final generated = LegacySnapshotEnvelopeCodec.encode(
      buildSyntheticLegacySnapshot(),
    );

    expect(base64Encode(generated), committed);
  });

  test('shared fixture covers every fixed, dynamic and secure key', () {
    final decoded = _decodeCommittedFixture();

    expect(decoded.schemaVersion, legacySnapshotSchemaVersion);
    expect(
      decoded.ordinaryValues.keys,
      unorderedEquals(legacyFixedHiveKeys.union(fixtureDynamicHiveKeys)),
    );
    expect(decoded.secureValues.keys, unorderedEquals(legacySecureKeys));
    expect(decoded.identity.publicKeyHex, fixturePublicKeyHex);
    expect(decoded.identity.privateKeyHex, fixturePrivateKeyHex);
    expect(decoded.identity.flavor, 'nsec');
    expect(
      decoded.assets.map((asset) => asset.relativePath),
      [
        'espeak-ng-data/.roadstr-ready',
        'kokoro/model.onnx',
        'piper/model.onnx'
      ],
    );
  });

  test('encoding is stable across map and asset insertion order', () {
    final original = buildSyntheticLegacySnapshot();
    final reordered = LegacyEnvelopeSnapshot(
      schemaVersion: original.schemaVersion,
      ordinaryValues: Map.fromEntries(
        original.ordinaryValues.entries.toList().reversed,
      ),
      secureValues: Map.fromEntries(
        original.secureValues.entries.toList().reversed,
      ),
      identity: original.identity,
      assets: original.assets.reversed.toList(),
    );

    expect(
      LegacySnapshotEnvelopeCodec.encode(reordered),
      orderedEquals(LegacySnapshotEnvelopeCodec.encode(original)),
    );
  });

  test('corruption and truncation fail without exposing fixture values', () {
    final encoded = LegacySnapshotEnvelopeCodec.encode(
      buildSyntheticLegacySnapshot(),
    );
    final corrupted = Uint8List.fromList(encoded)..[24] ^= 0xff;
    final truncated =
        Uint8List.fromList(encoded.sublist(0, encoded.length - 1));

    for (final invalid in [corrupted, truncated]) {
      expect(
        () => LegacySnapshotEnvelopeCodec.decode(invalid),
        throwsA(
          isA<LegacyEnvelopeException>().having(
            (error) => error.message,
            'message',
            isNot(contains('fixture-routing-key-not-real')),
          ),
        ),
      );
    }
  });
}

LegacyEnvelopeSnapshot _decodeCommittedFixture() {
  final encoded = base64Decode(File(_fixturePath).readAsStringSync().trim());
  return LegacySnapshotEnvelopeCodec.decode(encoded);
}
