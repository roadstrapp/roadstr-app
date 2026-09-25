import 'dart:convert';
import 'dart:io';

import 'package:roadstr/migration/legacy_snapshot_envelope.dart';
import 'legacy_snapshot_fixture.dart';

const fixturePath =
    'android/app/src/test/resources/parity/legacy_snapshot_v1.b64';

void main(List<String> arguments) {
  final generated = base64Encode(
    LegacySnapshotEnvelopeCodec.encode(buildSyntheticLegacySnapshot()),
  );
  if (arguments.contains('--check')) {
    final file = File(fixturePath);
    if (!file.existsSync() || file.readAsStringSync().trim() != generated) {
      stderr.writeln('Legacy fixture is missing or stale: $fixturePath');
      exitCode = 1;
    }
    return;
  }
  stdout.writeln(generated);
}
