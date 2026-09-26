import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_lnurl_protocol_fixture.dart'
    as fixture;

void main() {
  test('committed LNURL fixture matches the production core', () {
    final generated = fixture.buildLnurlProtocolFixture();
    expect(File(fixture.outputPath).readAsStringSync(), generated);
    expect(
      generated
          .split('\n')
          .where((line) => line.isNotEmpty && !line.startsWith('#')),
      hasLength(77),
    );
  });
}
