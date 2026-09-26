import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:roadstr/models/favorite_place.dart';
import 'package:roadstr/services/favorites_sync_protocol.dart';

const outputPath =
    'android/app/src/test/resources/parity/favorites_sync_protocol_v1.tsv';
const _pubkey =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const _otherPubkey =
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';
const _createdAt = 1704067200;

typedef _FixtureCase = ({
  String operation,
  String name,
  Map<String, dynamic> payload,
  Map<String, dynamic> expected,
});

Map<String, dynamic> _draftExpected({
  required int kind,
  required int createdAt,
  required List<List<String>> tags,
  required String content,
}) {
  final canonical = jsonEncode([0, _pubkey, createdAt, kind, tags, content]);
  return {
    'wire': {
      'id': sha256.convert(utf8.encode(canonical)).toString(),
      'pubkey': _pubkey,
      'created_at': createdAt,
      'kind': kind,
      'tags': tags,
      'content': content,
      'sig': '',
    },
  };
}

List<_FixtureCase> _cases() {
  final dTag = FavoritesSyncProtocol.hashedDTag(_pubkey);
  final ordinaryFavorite = {
    'label': 'Casa',
    'address': 'Via Roma 1',
    'lat': 45.0,
    'lon': 9.0,
  };
  final unicodeFavorite = {
    'label': 'Città ⚡',
    'address': 'Piazza 日本',
    'lat': -33.865143,
    'lon': 151.2099,
  };
  final validEvent = <String, dynamic>{
    'id': 'first',
    'pubkey': _pubkey,
    'kind': 30078,
    'created_at': 100,
    'content': 'ciphertext',
    'tags': [
      ['d', dTag],
    ],
  };

  return [
    for (final relay in <(String, String, String?)>[
      ('ordinary', 'wss://relay.example.com', 'wss://relay.example.com'),
      ('whitespace', '  wss://relay.example.com  ', 'wss://relay.example.com'),
      ('trailing-slash', 'wss://relay.example.com/', 'wss://relay.example.com'),
      (
        'path',
        'wss://relay.example.com/nostr',
        'wss://relay.example.com/nostr'
      ),
      (
        'uppercase',
        'WSS://RELAY.EXAMPLE.COM/NOSTR',
        'wss://relay.example.com/NOSTR'
      ),
      ('port', 'wss://relay.example.com:443', 'wss://relay.example.com:443'),
      ('public-ip', 'wss://8.8.8.8', 'wss://8.8.8.8'),
      (
        'escaped-path',
        'wss://relay.example.com/a%2fb',
        'wss://relay.example.com/a%2Fb'
      ),
      ('cleartext', 'ws://relay.example.com', null),
      ('https', 'https://relay.example.com', null),
      ('no-scheme', 'relay.example.com', null),
      ('javascript', 'javascript:alert(1)', null),
      ('credentials', 'wss://user:pw@relay.example.com', null),
      ('query', 'wss://relay.example.com?token=abc', null),
      ('empty-query', 'wss://relay.example.com?', null),
      ('fragment', 'wss://relay.example.com#frag', null),
      ('missing-host', 'wss://', null),
      ('localhost', 'wss://localhost', null),
      ('builtin-damus', 'wss://relay.damus.io/', null),
      ('builtin-nos', 'wss://nos.lol', null),
      ('builtin-purple', 'wss://purplerelay.com', null),
      ('over-length', 'wss://${'a' * 190}.example.com', null),
    ])
      (
        operation: 'relay',
        name: relay.$1,
        payload: {'input': relay.$2},
        expected: {'normalized': relay.$3},
      ),
    for (final value in <(String, String)>[
      ('first-key', _pubkey),
      ('second-key', _otherPubkey),
      ('case-sensitive-input', _pubkey.toUpperCase()),
    ])
      (
        operation: 'd_tag',
        name: value.$1,
        payload: {'pubkey': value.$2},
        expected: {
          'dTag': sha256
              .convert(utf8.encode('roadstr-favorites:${value.$2}'))
              .toString(),
        },
      ),
    for (final padding in <(String, Map<String, dynamic>, bool, int, bool)>[
      ('empty', {'value': ''}, true, 0, true),
      ('empty-list', {'value': '[]'}, true, 4096, false),
      ('unicode-bytes', {'value': 'à⚡'}, true, 4096, false),
      ('exact-bucket', {'repeat': 'a', 'count': 4096}, true, 4096, true),
      ('one-over-bucket', {'repeat': 'b', 'count': 4097}, true, 8192, false),
      ('last-full-bucket', {'repeat': 'c', 'count': 61440}, true, 61440, true),
      ('capped-tail', {'repeat': 'd', 'count': 61441}, true, 65535, false),
      ('exact-maximum', {'repeat': 'e', 'count': 65535}, true, 65535, true),
      ('over-maximum', {'repeat': 'f', 'count': 65536}, false, 0, false),
    ])
      (
        operation: 'padding',
        name: padding.$1,
        payload: padding.$2,
        expected: {
          'accepted': padding.$3,
          'byteLength': padding.$4,
          'unchanged': padding.$5,
        },
      ),
    for (final timestamp in <(String, int, int, int)>[
      ('hour-start', _createdAt + 123, 0, _createdAt),
      ('same-hour-bump', _createdAt + 123, _createdAt, _createdAt + 1),
      (
        'future-high-water',
        _createdAt + 123,
        _createdAt + 9999,
        _createdAt + 10000
      ),
      ('next-hour', _createdAt + 3600, _createdAt, _createdAt + 3600),
      (
        'previous-hour-high-water',
        _createdAt + 3601,
        _createdAt + 3599,
        _createdAt + 3600
      ),
      ('epoch', 0, 0, 1),
    ])
      (
        operation: 'timestamp',
        name: timestamp.$1,
        payload: {'now': timestamp.$2, 'last': timestamp.$3},
        expected: {'createdAt': timestamp.$4},
      ),
    (
      operation: 'snapshot_draft',
      name: 'snapshot',
      payload: {
        'pubkey': _pubkey,
        'createdAt': _createdAt,
        'content': 'nip44:ciphertext',
      },
      expected: _draftExpected(
        kind: 30078,
        createdAt: _createdAt,
        tags: [
          ['d', dTag],
        ],
        content: 'nip44:ciphertext',
      ),
    ),
    (
      operation: 'legacy_wipe_draft',
      name: 'wipe',
      payload: {'pubkey': _pubkey, 'createdAt': _createdAt + 1},
      expected: _draftExpected(
        kind: 30078,
        createdAt: _createdAt + 1,
        tags: const [
          ['d', 'roadstr-favorites'],
        ],
        content: '',
      ),
    ),
    (
      operation: 'legacy_deletion_draft',
      name: 'nip09-delete',
      payload: {'pubkey': _pubkey, 'createdAt': _createdAt + 2},
      expected: _draftExpected(
        kind: 5,
        createdAt: _createdAt + 2,
        tags: const [
          ['a', '30078:$_pubkey:roadstr-favorites'],
        ],
        content: '',
      ),
    ),
    (
      operation: 'fetch_request',
      name: 'hashed-tag-filter',
      payload: {
        'subscriptionId': 'fav-fixture',
        'pubkey': _pubkey,
        'dTag': dTag
      },
      expected: {
        'wire': [
          'REQ',
          'fav-fixture',
          {
            'kinds': [30078],
            'authors': [_pubkey],
            '#d': [dTag],
            'limit': 1,
          }
        ],
      },
    ),
    for (final binding in <(String, Map<String, dynamic>, bool, int, bool)>[
      ('valid', validEvent, true, 0, true),
      ('invalid-signature', validEvent, false, 0, false),
      ('throwing-signature', validEvent, true, 0, false),
      ('wrong-author', {...validEvent, 'pubkey': _otherPubkey}, true, 0, false),
      ('wrong-kind', {...validEvent, 'kind': 30077}, true, 0, false),
      ('fractional-kind', {...validEvent, 'kind': 30078.5}, true, 0, false),
      ('missing-d-tag', {...validEvent, 'tags': <Object?>[]}, true, 0, false),
      (
        'wrong-d-tag',
        {
          ...validEvent,
          'tags': [
            ['d', 'wrong']
          ]
        },
        true,
        0,
        false
      ),
      ('mistyped-tags', {...validEvent, 'tags': 'bad'}, true, 0, false),
      (
        'non-string-content-compatible',
        {...validEvent, 'content': 7},
        true,
        0,
        true
      ),
      ('exact-content-limit', validEvent, true, 200000, true),
      ('over-content-limit', validEvent, true, 200001, false),
    ])
      (
        operation: 'event_binding',
        name: binding.$1,
        payload: {
          'event': binding.$2,
          'pubkey': _pubkey,
          'dTag': dTag,
          'signatureValid': binding.$3,
          'signatureThrows': binding.$1 == 'throwing-signature',
          'contentLength': binding.$4,
        },
        expected: {
          'bound': binding.$5,
          'verifyCalls': binding.$5 ||
                  binding.$1 == 'invalid-signature' ||
                  binding.$1 == 'throwing-signature'
              ? 1
              : 0,
        },
      ),
    for (final newest in <(String, List<Object?>, String?)>[
      ('empty', <Object?>[], null),
      ('nulls', <Object?>[null, null], null),
      ('single', [validEvent], 'first'),
      (
        'newer-wins',
        [
          validEvent,
          {...validEvent, 'id': 'second', 'created_at': 101}
        ],
        'second'
      ),
      (
        'first-wins-tie',
        [
          validEvent,
          {...validEvent, 'id': 'second'}
        ],
        'first'
      ),
      (
        'missing-time-is-zero',
        [
          {...validEvent}..remove('created_at'),
          validEvent
        ],
        'first'
      ),
    ])
      (
        operation: 'newest',
        name: newest.$1,
        payload: {'events': newest.$2},
        expected: {'id': newest.$3},
      ),
    for (final rollback in <(String, int, int?, bool)>[
      ('no-high-water', 10, null, true),
      ('newer', 11, 10, true),
      ('equal', 10, 10, true),
      ('older', 9, 10, false),
    ])
      (
        operation: 'rollback',
        name: rollback.$1,
        payload: {'fetched': rollback.$2, 'last': rollback.$3},
        expected: {'accepted': rollback.$4},
      ),
    for (final favorites in <(String, List<Map<String, dynamic>>)>[
      ('ordinary', [ordinaryFavorite]),
      ('unicode-and-order', [unicodeFavorite, ordinaryFavorite]),
      (
        'json-escaping',
        [
          {
            'label': 'Quote " slash \\ line\nend',
            'address': 'Control \u0001 and lone surrogate \uD800',
            'lat': 1.0,
            'lon': 2.0,
          }
        ],
      ),
      (
        'scientific-and-negative-zero',
        [
          {
            'label': 'Tiny',
            'address': '',
            'lat': 1e-7,
            'lon': -0.0,
          }
        ],
      ),
    ])
      (
        operation: 'favorites_json',
        name: favorites.$1,
        payload: {'favorites': favorites.$2},
        expected: {'json': jsonEncode(favorites.$2)},
      ),
    for (final envelope in <(String, Map<String, dynamic>)>[
      (
        'standard',
        {
          'v': 1,
          'iterations': 600000,
          'salt': 'c2FsdA==',
          'iv': 'aXY=',
          'ciphertext': 'Y2lwaGVy',
        },
      ),
      ('spread-overrides-version', {'v': 2, 'ciphertext': 'x'}),
    ])
      (
        operation: 'passphrase_envelope',
        name: envelope.$1,
        payload: {'encrypted': envelope.$2},
        expected: {
          'json': jsonEncode({'v': 1, 'encrypted': true, ...envelope.$2}),
        },
      ),
  ];
}

Map<String, dynamic> _evaluate(_FixtureCase fixtureCase) {
  final payload = fixtureCase.payload;
  switch (fixtureCase.operation) {
    case 'relay':
      return {
        'normalized':
            FavoritesSyncProtocol.normaliseRelayUrl(payload['input'] as String),
      };
    case 'd_tag':
      return {
        'dTag': FavoritesSyncProtocol.hashedDTag(payload['pubkey'] as String)
      };
    case 'padding':
      final value = _materialize(payload);
      try {
        final padded = FavoritesSyncProtocol.padToBucket(value);
        return {
          'accepted': true,
          'byteLength': utf8.encode(padded).length,
          'unchanged': padded == value,
        };
      } catch (_) {
        return {'accepted': false, 'byteLength': 0, 'unchanged': false};
      }
    case 'timestamp':
      return {
        'createdAt': FavoritesSyncProtocol.nextCreatedAt(
          nowUnixSeconds: payload['now'] as int,
          lastCreatedAt: payload['last'] as int,
        ),
      };
    case 'snapshot_draft':
      return {
        'wire': FavoritesSyncProtocol.snapshotDraft(
          pubkey: payload['pubkey'] as String,
          createdAt: payload['createdAt'] as int,
          encryptedContent: payload['content'] as String,
        ).toJson(),
      };
    case 'legacy_wipe_draft':
      return {
        'wire': FavoritesSyncProtocol.legacyWipeDraft(
          pubkey: payload['pubkey'] as String,
          createdAt: payload['createdAt'] as int,
        ).toJson(),
      };
    case 'legacy_deletion_draft':
      return {
        'wire': FavoritesSyncProtocol.legacyDeletionDraft(
          pubkey: payload['pubkey'] as String,
          createdAt: payload['createdAt'] as int,
        ).toJson(),
      };
    case 'fetch_request':
      return {
        'wire': FavoritesSyncProtocol.fetchRequest(
          subscriptionId: payload['subscriptionId'] as String,
          pubkey: payload['pubkey'] as String,
          dTag: payload['dTag'] as String,
        ),
      };
    case 'event_binding':
      final event = (payload['event'] as Map).cast<String, dynamic>();
      final contentLength = payload['contentLength'] as int;
      if (contentLength > 0) event['content'] = 'x' * contentLength;
      var verifyCalls = 0;
      final bound = FavoritesSyncProtocol.snapshotEventIsBound(
        event,
        pubkey: payload['pubkey'] as String,
        dTag: payload['dTag'] as String,
        verifySignature: () {
          verifyCalls++;
          if (payload['signatureThrows'] == true) {
            throw StateError('synthetic verifier failure');
          }
          return payload['signatureValid'] as bool;
        },
      );
      return {'bound': bound, 'verifyCalls': verifyCalls};
    case 'newest':
      final events = (payload['events'] as List).map((event) =>
          event == null ? null : (event as Map).cast<String, dynamic>());
      return {'id': FavoritesSyncProtocol.newestSnapshot(events)?['id']};
    case 'rollback':
      return {
        'accepted': FavoritesSyncProtocol.passesRollbackGuard(
          fetchedCreatedAt: payload['fetched'] as int,
          lastCreatedAt: payload['last'] as int?,
        ),
      };
    case 'favorites_json':
      final favorites = (payload['favorites'] as List)
          .map((value) => FavoritePlace.fromMapSafe(value as Map)!)
          .toList();
      return {'json': FavoritesSyncProtocol.encodeFavorites(favorites)};
    case 'passphrase_envelope':
      return {
        'json': FavoritesSyncProtocol.wrapPassphraseEnvelope(
          (payload['encrypted'] as Map).cast<String, dynamic>(),
        ),
      };
    default:
      throw StateError('Unknown operation: ${fixtureCase.operation}');
  }
}

String _materialize(Map<String, dynamic> payload) {
  final value = payload['value'];
  if (value is String) return value;
  return (payload['repeat'] as String) * (payload['count'] as int);
}

String _packedJson(Object? value) =>
    base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');

String buildFavoritesSyncProtocolFixture() {
  final lines = <String>[
    '# Roadstr deterministic NIP-78 favourites-sync fixture v1.',
    '# operation\tcase\tpayload_base64url\texpected_base64url',
  ];
  final names = <String>{};
  for (final fixtureCase in _cases()) {
    if (!names.add('${fixtureCase.operation}/${fixtureCase.name}')) {
      throw StateError('Duplicate fixture case: ${fixtureCase.name}');
    }
    final actual = _evaluate(fixtureCase);
    if (jsonEncode(actual) != jsonEncode(fixtureCase.expected)) {
      throw StateError(
        'Favorites sync mismatch for ${fixtureCase.operation}/${fixtureCase.name}: '
        '$actual != ${fixtureCase.expected}',
      );
    }
    lines.add([
      fixtureCase.operation,
      fixtureCase.name,
      _packedJson(fixtureCase.payload),
      _packedJson(fixtureCase.expected),
    ].join('\t'));
  }
  return '${lines.join('\n')}\n';
}

void main(List<String> arguments) {
  final generated = buildFavoritesSyncProtocolFixture();
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
