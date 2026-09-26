import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/lightning_protocol.dart';

import '../tools/kotlin_rewrite/generate_lightning_protocol_fixture.dart'
    as fixture;

void main() {
  test('committed NIP-47/NIP-57 fixture matches the production core', () {
    final generated = fixture.buildLightningProtocolFixture();
    expect(File(fixture.outputPath).readAsStringSync(), generated);
    expect(
      generated
          .split('\n')
          .where((line) => line.isNotEmpty && !line.startsWith('#')),
      hasLength(69),
    );
  });

  test('NWC connection diagnostics never expose private material', () {
    final secret = '2' * 64;
    final connection = NwcConnection.tryParse(
      'nostr+walletconnect://${'1' * 64}?secret=$secret',
      fallbackRelay: 'wss://relay.damus.io',
    );

    expect(connection, isNotNull);
    expect(connection.toString(), isNot(contains(secret)));
    expect(connection.toString(), contains('redacted'));
  });
}
