import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/nostr_relay_message.dart';

import '../tools/kotlin_rewrite/generate_nostr_relay_message_fixture.dart'
    as fixture;

const fixturePath =
    'android/app/src/test/resources/parity/nostr_relay_messages_v1.tsv';

void main() {
  late List<List<String>> rows;

  setUpAll(() {
    rows = File(fixturePath)
        .readAsLinesSync()
        .where((line) => line.isNotEmpty && !line.startsWith('#'))
        .map((line) => line.split('\t'))
        .toList();
  });

  test('committed inbound relay fixture is current', () async {
    expect(
      File(fixturePath).readAsStringSync(),
      await fixture.buildRelayMessageFixture(),
    );
  });

  test('Dart decoder reproduces every shared accepted and rejected case', () {
    expect(rows, hasLength(25));
    for (final fields in rows) {
      final result = NostrRelayMessageDecoder.decode(
        fixture.materializeRelayFixtureSource(fields[2]),
      );
      final message = result.message;
      final outcome = message == null
          ? 'reject:${result.failure!.name}'
          : fixture.relayMessageType(message);

      expect(outcome, fields[3], reason: fields[1]);
      expect(
        message == null
            ? '-'
            : fixture.b64(fixture.summarizeRelayMessage(message)),
        fields[4],
        reason: fields[1],
      );
    }
  });

  test('event envelope is immutable and verification remains out of scope', () {
    final result = NostrRelayMessageDecoder.decode(
      '["EVENT","sub",{"id":"not-verified","kind":1315}]',
    );
    final message = result.message as NostrRelayEventMessage;

    expect(message.subscriptionId, 'sub');
    expect(message.event['id'], 'not-verified');
    expect(() => message.event['id'] = 'changed', throwsUnsupportedError);
  });

  test('malformed OK status is fail-closed for publication acknowledgement',
      () {
    final result = NostrRelayMessageDecoder.decode('["OK","id",1]');
    final message = result.message as NostrRelayOkMessage;

    expect(message.accepted, isNull);
    expect(message.accepted == true, isFalse);
  });

  test('hostile inputs return a result instead of escaping parser errors', () {
    final corpus = <Object?>[
      null,
      1,
      '',
      'null',
      '{}',
      '[' * 100,
      '"unterminated',
      '["EVENT",{},{}]',
      '["EOSE",null]',
      '["AUTH",7]',
    ];

    for (final input in corpus) {
      expect(() => NostrRelayMessageDecoder.decode(input), returnsNormally);
    }
  });
}
