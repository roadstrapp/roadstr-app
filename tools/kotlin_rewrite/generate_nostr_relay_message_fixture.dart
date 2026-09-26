import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:roadstr/services/nostr_protocol_codec.dart';
import 'package:roadstr/services/nostr_relay_message.dart';

const outputPath =
    'android/app/src/test/resources/parity/nostr_relay_messages_v1.tsv';
const nostrFixturePath =
    'android/app/src/test/resources/parity/nostr_protocol_v1.tsv';

class _Case {
  const _Case(this.id, this.source);

  final String id;
  final String source;
}

String b64(String value) =>
    base64Url.encode(utf8.encode(value)).replaceAll('=', '');

String unb64(String value) {
  final padded = value.padRight((value.length + 3) ~/ 4 * 4, '=');
  return utf8.decode(base64Url.decode(padded));
}

String _textSource(String value) => 'text:${b64(value)}';

String _paddedNotice(int targetLength, String alphabet) {
  const prefix = '["NOTICE","';
  const suffix = '"]';
  final contentLength = targetLength - prefix.length - suffix.length;
  if (contentLength < 0) throw ArgumentError.value(targetLength);
  final content = switch (alphabet) {
    'ascii' => 'a' * contentLength,
    'emoji' =>
      '${'🚗' * (contentLength ~/ 2)}${contentLength.isOdd ? 'a' : ''}',
    _ => throw ArgumentError.value(alphabet),
  };
  final result = '$prefix$content$suffix';
  if (result.length != targetLength) {
    throw StateError('Padded notice length drifted: ${result.length}');
  }
  return result;
}

String _deepEvent(int depth) {
  if (depth < 2) throw ArgumentError.value(depth);
  final nested = depth - 2;
  return '["EVENT","deep",{"kind":1315,"nested":'
      '${'[' * nested}null${']' * nested}}]';
}

Object? materializeRelayFixtureSource(String source) {
  final parts = source.split(':');
  return switch (parts[0]) {
    'text' => unb64(parts[1]),
    'non-string-int' => 7,
    'padded-notice' => _paddedNotice(int.parse(parts[1]), parts[2]),
    'deep-event' => _deepEvent(int.parse(parts[1])),
    _ => throw ArgumentError.value(source, 'source'),
  };
}

Map<String, dynamic> _fingerprint(Object? value) {
  final encoded = jsonEncode(value);
  return {
    'jsonUtf16Length': encoded.length,
    'sha256': sha256.convert(utf8.encode(encoded)).toString(),
  };
}

String relayMessageType(NostrRelayMessage message) => switch (message) {
      NostrRelayEventMessage() => 'EVENT',
      NostrRelayEoseMessage() => 'EOSE',
      NostrRelayOkMessage() => 'OK',
      NostrRelayNoticeMessage() => 'NOTICE',
      NostrRelayClosedMessage() => 'CLOSED',
      NostrRelayAuthMessage() => 'AUTH',
    };

String summarizeRelayMessage(NostrRelayMessage message) {
  final summary = switch (message) {
    NostrRelayEventMessage(:final subscriptionId, :final event) => {
        'type': 'EVENT',
        'subscriptionId': subscriptionId,
        'eventId': event['id'],
        'kind': event['kind'],
        'fieldCount': event.length,
      },
    NostrRelayEoseMessage(:final subscriptionId) => {
        'type': 'EOSE',
        'subscriptionId': subscriptionId,
      },
    NostrRelayOkMessage(:final eventId, :final accepted, :final reason) => {
        'type': 'OK',
        'eventId': eventId,
        'accepted': accepted,
        'reason': _fingerprint(reason),
      },
    NostrRelayNoticeMessage(:final detail) => {
        'type': 'NOTICE',
        'detail': _fingerprint(detail),
      },
    NostrRelayClosedMessage(:final subscriptionId, :final detail) => {
        'type': 'CLOSED',
        'subscriptionId': subscriptionId,
        'detail': _fingerprint(detail),
      },
    NostrRelayAuthMessage(:final challenge) => {
        'type': 'AUTH',
        'challenge': _fingerprint(challenge),
      },
  };
  return jsonEncode(summary);
}

Future<String> buildRelayMessageFixture() async {
  final nostrRows = File(nostrFixturePath)
      .readAsLinesSync()
      .where((line) => line.isNotEmpty && !line.startsWith('#'))
      .map((line) => line.split('\t'))
      .toList();
  final report = nostrRows.singleWhere(
    (row) => row[0] == 'report' && row[1] == 'report-speedCamera',
  );
  final signature = nostrRows.singleWhere((row) => row[0] == 'wire-publish')[3];
  final signedReport = RoadstrNostrEvents.report(
    pubkey: report[2],
    createdAt: int.parse(report[3]),
    latitude: double.parse(report[4]),
    longitude: double.parse(report[5]),
    category: report[6],
    expiresAt: int.parse(report[7]),
    speedLimit: int.parse(report[8]),
    content: unb64(report[9]),
  ).toJson(signature: signature);

  final cases = <_Case>[
    _Case(
      'signed-event',
      _textSource(jsonEncode(['EVENT', 'events-sub', signedReport])),
    ),
    _Case(
      'event-extra-field',
      _textSource('["EVENT","events",{"id":"abc","kind":1315},42]'),
    ),
    _Case('eose-extra-field', _textSource('["EOSE","events",42]')),
    _Case('ok-accepted', _textSource('["OK","event-a",true,"saved"]')),
    _Case(
      'ok-rejected-unicode',
      _textSource('["OK","event-b",false,"bloccato 🚗"]'),
    ),
    _Case('ok-malformed-status', _textSource('["OK","event-c",1]')),
    _Case('notice-missing-detail', _textSource('["NOTICE"]')),
    _Case(
      'notice-unicode-brackets',
      _textSource('["NOTICE","caffè 🚗 [{ still text }]"]'),
    ),
    _Case(
      'notice-json-escapes-raw',
      _textSource(
        r'["NOTICE","quote:\" slash:\\ solidus:\/ controls:\b\f\n\r\t unicode:\u00e9 surrogate:\ud83d\ude97"]',
      ),
    ),
    _Case('closed', _textSource('["CLOSED","events","rate-limited"]')),
    _Case('auth', _textSource('["AUTH","challenge-123"]')),
    _Case('unsupported', _textSource('["COUNT","events",7]')),
    _Case('empty-array', _textSource('[]')),
    _Case('top-level-object', _textSource('{"type":"EVENT"}')),
    _Case('event-short', _textSource('["EVENT","events"]')),
    _Case('event-non-string-sub', _textSource('["EVENT",7,{"kind":1315}]')),
    _Case('event-non-object', _textSource('["EVENT","events",[]]')),
    _Case('eose-short', _textSource('["EOSE"]')),
    _Case('malformed-json', _textSource('["EVENT",')),
    const _Case('non-string-input', 'non-string-int'),
    const _Case('exact-char-cap', 'padded-notice:262144:ascii'),
    const _Case('over-char-cap', 'padded-notice:262145:ascii'),
    const _Case('exact-char-cap-emoji', 'padded-notice:262144:emoji'),
    const _Case('depth-at-cap', 'deep-event:64'),
    const _Case('depth-over-cap', 'deep-event:65'),
  ];

  final lines = <String>[
    '# Roadstr bounded inbound Nostr relay message fixture v1.',
    '# Generated by tools/kotlin_rewrite/generate_nostr_relay_message_fixture.dart.',
  ];
  for (final testCase in cases) {
    final result = NostrRelayMessageDecoder.decode(
      materializeRelayFixtureSource(testCase.source),
    );
    final message = result.message;
    lines.add([
      'decode',
      testCase.id,
      testCase.source,
      message == null
          ? 'reject:${result.failure!.name}'
          : relayMessageType(message),
      message == null ? '-' : b64(summarizeRelayMessage(message)),
    ].join('\t'));
  }
  return '${lines.join('\n')}\n';
}

Future<void> main(List<String> arguments) async {
  final generated = await buildRelayMessageFixture();
  final output = File(outputPath);
  if (arguments.contains('--check')) {
    if (!output.existsSync() || output.readAsStringSync() != generated) {
      stderr.writeln('$outputPath is stale; regenerate it without --check.');
      exitCode = 1;
    }
    return;
  }
  output.writeAsStringSync(generated);
}
