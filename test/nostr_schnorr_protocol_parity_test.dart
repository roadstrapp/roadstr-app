import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_nostr_schnorr_fixture.dart' as fixture;

void main() {
  test('committed BIP-340 fixture is current and oracle checked', () {
    final generated = fixture.buildNostrSchnorrFixture();
    expect(File(fixture.outputPath).readAsStringSync(), generated);
    expect(
      generated
          .split('\n')
          .where((line) => line.isNotEmpty && !line.startsWith('#')),
      hasLength(43),
    );
  });
}
