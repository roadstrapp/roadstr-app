import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_nip04_fixture.dart' as fixture;

void main() {
  test('committed NIP-04 fixture is current and legacy-library checked', () {
    final generated = fixture.buildNip04Fixture();
    expect(File(fixture.outputPath).readAsStringSync(), generated);
    expect(
      generated
          .split('\n')
          .where((line) => line.isNotEmpty && !line.startsWith('#')),
      hasLength(64),
    );
  });
}
