import 'dart:convert';
import 'dart:io';

import 'legacy_raw_hive_fixture.dart';

const fixturePath =
    'android/app/src/test/resources/parity/legacy_settings_hive_v1.b64';

Future<void> main(List<String> arguments) async {
  final generated = base64Encode(await generateSyntheticEncryptedHiveFixture());
  if (arguments.contains('--check')) {
    final file = File(fixturePath);
    if (!file.existsSync() || file.readAsStringSync().trim() != generated) {
      stderr
          .writeln('Legacy raw Hive fixture is missing or stale: $fixturePath');
      exitCode = 1;
    }
    return;
  }
  stdout.writeln(generated);
}
