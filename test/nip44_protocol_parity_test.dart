import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_nip44_fixture.dart' as fixture;

void main() {
  test('committed NIP-44 fixture is current and official-vector checked', () {
    final generated = fixture.buildNip44Fixture();
    expect(File(fixture.outputPath).readAsStringSync(), generated);
    expect(
      generated
          .split('\n')
          .where((line) => line.isNotEmpty && !line.startsWith('#')),
      hasLength(77),
    );
  });
}
