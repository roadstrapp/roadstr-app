import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('the bundled page extension scripts behave against a fake page', () async {
    final probe = await Process.run('node', ['--version']).catchError(
      (_) => ProcessResult(0, 127, '', 'node not found'),
    );
    if (probe.exitCode != 0) {
      markTestSkipped('node is not available');
      return;
    }
    final result = await Process.run('node', ['--test', 'test/place_extractor_script.test.js']);
    expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
  });
}
