import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:hive/hive.dart';

import 'legacy_snapshot_fixture.dart';
import 'package:roadstr/migration/legacy_storage_contract.dart';

/// Generates a reproducible Hive 2.2.3 `settings.hive` test artifact.
///
/// The deterministic IV stream is intentionally confined to fixture tooling.
/// Production Roadstr continues to use HiveAesCipher's secure random IVs.
Future<Uint8List> generateSyntheticEncryptedHiveFixture() async {
  final temporaryDirectory =
      await Directory.systemTemp.createTemp('roadstr-legacy-hive-fixture-');
  try {
    Hive.init(temporaryDirectory.path);
    final box = await Hive.openBox<dynamic>(
      legacySettingsBoxName,
      encryptionCipher: _DeterministicFixtureCipher(fixtureHiveKeyBytes),
      crashRecovery: false,
    );
    await box.putAll(buildSyntheticLegacyHiveValues());
    await box.close();
    return File(
      '${temporaryDirectory.path}${Platform.pathSeparator}$legacySettingsFileName',
    ).readAsBytes();
  } finally {
    await Hive.close();
    if (await temporaryDirectory.exists()) {
      await temporaryDirectory.delete(recursive: true);
    }
  }
}

class _DeterministicFixtureCipher extends HiveAesCipher {
  _DeterministicFixtureCipher(super.key);

  int _counter = 0;

  @override
  Uint8List generateIv() {
    final bytes = sha256
        .convert(utf8.encode('roadstr-hive-fixture-iv-${_counter++}'))
        .bytes;
    return Uint8List.fromList(bytes.take(16).toList(growable: false));
  }
}
