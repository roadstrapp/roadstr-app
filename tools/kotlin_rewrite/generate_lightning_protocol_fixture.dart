import 'dart:convert';
import 'dart:io';

import 'package:bech32/bech32.dart';
import 'package:crypto/crypto.dart';
import 'package:roadstr/services/bolt11_invoice.dart';
import 'package:roadstr/services/lightning_protocol.dart';

const outputPath =
    'android/app/src/test/resources/parity/lightning_protocol_v1.tsv';

const _wallet =
    '1111111111111111111111111111111111111111111111111111111111111111';
const _secret =
    '2222222222222222222222222222222222222222222222222222222222222222';
const _client =
    '3333333333333333333333333333333333333333333333333333333333333333';
const _requestId =
    '4444444444444444444444444444444444444444444444444444444444444444';
const _recipient =
    '5555555555555555555555555555555555555555555555555555555555555555';
const _eventId =
    '6666666666666666666666666666666666666666666666666666666666666666';
const _signer =
    '7777777777777777777777777777777777777777777777777777777777777777';
const _wrong =
    '8888888888888888888888888888888888888888888888888888888888888888';
const _fallbackRelay = 'wss://relay.damus.io';
const _createdAt = 1704067200;
const _preimage =
    '000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f';

typedef _FixtureCase = ({
  String operation,
  String name,
  Map<String, dynamic> payload,
  Map<String, dynamic> expected,
});

Map<String, dynamic> _connectionExpected({
  required String relay,
  String wallet = _wallet,
  String secret = _secret,
}) =>
    {
      'accepted': true,
      'walletPubkey': wallet,
      'secret': secret,
      'relay': relay,
    };

Map<String, dynamic> _draftExpected({
  required String pubkey,
  required int createdAt,
  required int kind,
  required List<List<String>> tags,
  required String content,
}) {
  final canonical = jsonEncode([0, pubkey, createdAt, kind, tags, content]);
  return {
    'accepted': true,
    'wire': {
      'id': sha256.convert(utf8.encode(canonical)).toString(),
      'pubkey': pubkey,
      'created_at': createdAt,
      'kind': kind,
      'tags': tags,
      'content': content,
      'sig': '',
    },
  };
}

Map<String, dynamic> _receiptPayload({
  int receiptKind = 9735,
  String receiptPubkey = _signer,
  bool receiptSignatureValid = true,
  int requestKind = 9734,
  bool requestSignatureValid = true,
  String requestRecipient = _recipient,
  String receiptRecipient = _recipient,
  String? requestEventId = _eventId,
  String? receiptEventId = _eventId,
  String? expectedEventId = _eventId,
  String? expectedRecipient = _recipient,
  String amount = '1000',
  bool includePreimage = true,
  String preimage = _preimage,
  String? invoiceDescriptionOverride,
  String? invoiceOverride,
  List<List<Object?>> extraRequestTags = const [],
  List<List<Object?>> extraReceiptTags = const [],
}) {
  final requestTags = <List<Object?>>[
    ['p', requestRecipient],
    if (requestEventId != null) ['e', requestEventId],
    ['amount', amount],
    ...extraRequestTags,
  ];
  final request = <String, dynamic>{
    'kind': requestKind,
    'tags': requestTags,
    'content': '',
  };
  final description = jsonEncode(request);
  final invoice = invoiceOverride ??
      _invoice(
        description: invoiceDescriptionOverride ?? description,
        preimageHex: _preimage,
      );
  final receipt = <String, dynamic>{
    'kind': receiptKind,
    'pubkey': receiptPubkey,
    'tags': <List<Object?>>[
      ['bolt11', invoice],
      ['description', description],
      if (includePreimage) ['preimage', preimage],
      ['p', receiptRecipient],
      if (receiptEventId != null) ['e', receiptEventId],
      ...extraReceiptTags,
    ],
  };
  return {
    'receipt': receipt,
    'receiptSigner': _signer,
    'receiptSignatureValid': receiptSignatureValid,
    'request': request,
    'requestSignatureValid': requestSignatureValid,
    'expectedEventId': expectedEventId,
    'expectedRecipient': expectedRecipient,
  };
}

Map<String, dynamic> _receiptExpected(
  int? amount, {
  int receiptVerifyCalls = 1,
  int requestVerifyCalls = 1,
}) =>
    {
      'amountMsat': amount,
      'receiptVerifyCalls': receiptVerifyCalls,
      'requestVerifyCalls': requestVerifyCalls,
    };

List<_FixtureCase> _cases() {
  final encodedRelay = Uri.encodeQueryComponent('wss://relay.example/path');
  final standardUri =
      'nostr+walletconnect://$_wallet?relay=$encodedRelay&secret=$_secret';
  final nwcInvoice = _invoice(
    description: 'nwc response fixture',
    preimageHex: _preimage,
  );
  final validResponseEvent = <String, dynamic>{
    'kind': 23195,
    'pubkey': _wallet,
    'tags': [
      ['e', _requestId],
      ['p', _client],
    ],
  };
  final validInfoEvent = <String, dynamic>{
    'kind': 13194,
    'pubkey': _wallet,
    'tags': [
      ['encryption', 'nip44_v2 nip04'],
    ],
    'content': 'pay_invoice get_balance',
  };

  return [
    (
      operation: 'nwc_uri',
      name: 'explicit-relay',
      payload: {'raw': standardUri, 'fallback': _fallbackRelay},
      expected: _connectionExpected(relay: 'wss://relay.example/path'),
    ),
    (
      operation: 'nwc_uri',
      name: 'fallback-relay',
      payload: {
        'raw': 'nostr+walletconnect://$_wallet?secret=$_secret',
        'fallback': _fallbackRelay,
      },
      expected: _connectionExpected(relay: _fallbackRelay),
    ),
    (
      operation: 'nwc_uri',
      name: 'surrounding-whitespace',
      payload: {'raw': '  $standardUri  ', 'fallback': _fallbackRelay},
      expected: _connectionExpected(relay: 'wss://relay.example/path'),
    ),
    (
      operation: 'nwc_uri',
      name: 'fragment-preserved-as-compatible',
      payload: {'raw': '$standardUri#ignored', 'fallback': _fallbackRelay},
      expected: _connectionExpected(relay: 'wss://relay.example/path'),
    ),
    (
      operation: 'nwc_uri',
      name: 'unknown-query-compatible',
      payload: {'raw': '$standardUri&lud16=x', 'fallback': _fallbackRelay},
      expected: _connectionExpected(relay: 'wss://relay.example/path'),
    ),
    for (final rejected in <(String, String)>[
      ('trailing-path', 'nostr+walletconnect://$_wallet/?secret=$_secret'),
      ('userinfo', 'nostr+walletconnect://user@$_wallet?secret=$_secret'),
      (
        'duplicate-secret',
        'nostr+walletconnect://$_wallet?secret=$_secret&secret=$_secret'
      ),
      (
        'duplicate-relay',
        'nostr+walletconnect://$_wallet?secret=$_secret&relay=$encodedRelay&relay=$encodedRelay'
      ),
      ('missing-secret', 'nostr+walletconnect://$_wallet?relay=$encodedRelay'),
      ('short-wallet', 'nostr+walletconnect://1234?secret=$_secret'),
      ('bad-secret', 'nostr+walletconnect://$_wallet?secret=${_secret}z'),
      (
        'https-relay',
        'nostr+walletconnect://$_wallet?secret=$_secret&relay=${Uri.encodeQueryComponent('https://relay.example')}'
      ),
      (
        'missing-relay-host',
        'nostr+walletconnect://$_wallet?secret=$_secret&relay=${Uri.encodeQueryComponent('wss:///path')}'
      ),
      ('wrong-scheme', 'nostr://$_wallet?secret=$_secret'),
    ])
      (
        operation: 'nwc_uri',
        name: rejected.$1,
        payload: {'raw': rejected.$2, 'fallback': _fallbackRelay},
        expected: {'accepted': false},
      ),
    for (final command in <(String, String)>[
      ('plain', 'lnbc1fixture'),
      ('escaped', 'quote"slash\\line\n'),
      ('unicode', 'fattura-⚡-日本語'),
    ])
      (
        operation: 'nwc_command',
        name: 'command-${command.$1}',
        payload: {'invoice': command.$2},
        expected: {
          'command': jsonEncode({
            'method': 'pay_invoice',
            'params': {'invoice': command.$2},
          }),
        },
      ),
    (
      operation: 'nwc_info_filter',
      name: 'info-filter',
      payload: {
        'subscriptionId': 'nwc-info-fixture',
        'walletPubkey': _wallet,
      },
      expected: {
        'accepted': true,
        'wire': [
          'REQ',
          'nwc-info-fixture',
          {
            'kinds': [13194],
            'authors': [_wallet],
            'limit': 1,
          }
        ],
      },
    ),
    (
      operation: 'nwc_info_filter',
      name: 'info-filter-invalid-wallet',
      payload: {
        'subscriptionId': 'nwc-info-fixture',
        'walletPubkey': 'bad',
      },
      expected: {'accepted': false},
    ),
    for (final info in <({
      String name,
      Map<String, dynamic> event,
      bool signatureValid,
      String decision,
      String? scheme,
      String? requestTag,
      int verifyCalls,
    })>[
      (
        name: 'prefer-nip44',
        event: validInfoEvent,
        signatureValid: true,
        decision: 'supported',
        scheme: 'nip44_v2',
        requestTag: 'nip44_v2',
        verifyCalls: 1,
      ),
      (
        name: 'nip44-only',
        event: {
          ...validInfoEvent,
          'tags': [
            ['encryption', 'nip44_v2']
          ],
        },
        signatureValid: true,
        decision: 'supported',
        scheme: 'nip44_v2',
        requestTag: 'nip44_v2',
        verifyCalls: 1,
      ),
      (
        name: 'explicit-nip04',
        event: {
          ...validInfoEvent,
          'tags': [
            ['encryption', 'nip04']
          ],
        },
        signatureValid: true,
        decision: 'supported',
        scheme: 'nip04',
        requestTag: 'nip04',
        verifyCalls: 1,
      ),
      (
        name: 'absent-encryption-is-legacy-nip04',
        event: {...validInfoEvent, 'tags': <List<String>>[]},
        signatureValid: true,
        decision: 'supported',
        scheme: 'nip04',
        requestTag: null,
        verifyCalls: 1,
      ),
      (
        name: 'unknown-and-nip04',
        event: {
          ...validInfoEvent,
          'tags': [
            ['encryption', 'future_mode nip04']
          ],
        },
        signatureValid: true,
        decision: 'supported',
        scheme: 'nip04',
        requestTag: 'nip04',
        verifyCalls: 1,
      ),
      for (final incompatible in <(String, Map<String, dynamic>)>[
        (
          'unknown-only',
          {
            ...validInfoEvent,
            'tags': [
              ['encryption', 'future_mode']
            ],
          },
        ),
        (
          'empty-encryption',
          {
            ...validInfoEvent,
            'tags': [
              ['encryption', '']
            ],
          },
        ),
        (
          'duplicate-encryption',
          {
            ...validInfoEvent,
            'tags': [
              ['encryption', 'nip44_v2'],
              ['encryption', 'nip04'],
            ],
          },
        ),
        (
          'missing-pay-invoice',
          {...validInfoEvent, 'content': 'get_balance get_info'},
        ),
      ])
        (
          name: incompatible.$1,
          event: incompatible.$2,
          signatureValid: true,
          decision: 'incompatible',
          scheme: null,
          requestTag: null,
          verifyCalls: 1,
        ),
      (
        name: 'whitespace-separated-values',
        event: {
          ...validInfoEvent,
          'tags': [
            ['encryption', '  nip04\t nip44_v2  ']
          ],
          'content': ' get_balance\n pay_invoice ',
        },
        signatureValid: true,
        decision: 'supported',
        scheme: 'nip44_v2',
        requestTag: 'nip44_v2',
        verifyCalls: 1,
      ),
      for (final ignored in <(String, Map<String, dynamic>)>[
        ('wrong-kind', {...validInfoEvent, 'kind': 23194}),
        ('wrong-author', {...validInfoEvent, 'pubkey': _wrong}),
        ('mistyped-content', {...validInfoEvent, 'content': 1}),
        ('non-list-tags', {...validInfoEvent, 'tags': 'bad'}),
        (
          'mistyped-tag-value',
          {
            ...validInfoEvent,
            'tags': [
              ['encryption', 44]
            ],
          },
        ),
      ])
        (
          name: ignored.$1,
          event: ignored.$2,
          signatureValid: true,
          decision: 'ignore',
          scheme: null,
          requestTag: null,
          verifyCalls: 0,
        ),
      (
        name: 'invalid-signature',
        event: validInfoEvent,
        signatureValid: false,
        decision: 'ignore',
        scheme: null,
        requestTag: null,
        verifyCalls: 1,
      ),
    ])
      (
        operation: 'nwc_info',
        name: info.name,
        payload: {
          'event': info.event,
          'walletPubkey': _wallet,
          'signatureValid': info.signatureValid,
        },
        expected: {
          'decision': info.decision,
          'scheme': info.scheme,
          'requestTag': info.requestTag,
          'verifyCalls': info.verifyCalls,
        },
      ),
    (
      operation: 'nwc_request_draft',
      name: 'request-draft',
      payload: {
        'clientPubkey': _client,
        'createdAt': _createdAt,
        'walletPubkey': _wallet,
        'encryptedContent': 'ciphertext?iv=fixture',
      },
      expected: _draftExpected(
        pubkey: _client,
        createdAt: _createdAt,
        kind: 23194,
        tags: const [
          ['p', _wallet],
        ],
        content: 'ciphertext?iv=fixture',
      ),
    ),
    for (final encryption in <(String, String)>[
      ('nip44', 'nip44_v2'),
      ('explicit-nip04', 'nip04'),
    ])
      (
        operation: 'nwc_request_draft',
        name: 'request-draft-${encryption.$1}',
        payload: {
          'clientPubkey': _client,
          'createdAt': _createdAt,
          'walletPubkey': _wallet,
          'encryptedContent': 'ciphertext',
          'encryption': encryption.$2,
        },
        expected: _draftExpected(
          pubkey: _client,
          createdAt: _createdAt,
          kind: 23194,
          tags: [
            ['encryption', encryption.$2],
            ['p', _wallet],
          ],
          content: 'ciphertext',
        ),
      ),
    for (final invalid in [
      {'clientPubkey': 'bad', 'walletPubkey': _wallet},
      {'clientPubkey': _client, 'walletPubkey': 'bad'},
    ])
      (
        operation: 'nwc_request_draft',
        name:
            'request-draft-invalid-${invalid['clientPubkey'] == 'bad' ? 'client' : 'wallet'}',
        payload: {
          ...invalid,
          'createdAt': _createdAt,
          'encryptedContent': 'ciphertext',
        },
        expected: {'accepted': false},
      ),
    (
      operation: 'nwc_response_filter',
      name: 'response-filter',
      payload: {
        'subscriptionId': 'nwc-fixture',
        'walletPubkey': _wallet,
        'requestEventId': _requestId,
      },
      expected: {
        'accepted': true,
        'wire': [
          'REQ',
          'nwc-fixture',
          {
            'kinds': [23195],
            'authors': [_wallet],
            '#e': [_requestId],
          }
        ],
      },
    ),
    for (final binding in <(String, Map<String, dynamic>, bool)>[
      ('bound', validResponseEvent, true),
      ('wrong-kind', {...validResponseEvent, 'kind': 23194}, false),
      ('wrong-author', {...validResponseEvent, 'pubkey': _wrong}, false),
      (
        'missing-request-tag',
        {
          ...validResponseEvent,
          'tags': [
            ['p', _client]
          ]
        },
        false,
      ),
      (
        'missing-client-tag',
        {
          ...validResponseEvent,
          'tags': [
            ['e', _requestId]
          ]
        },
        false,
      ),
      (
        'non-list-tags-ignored',
        {
          ...validResponseEvent,
          'tags': [
            'junk',
            ['e', _requestId],
            ['p', _client],
          ]
        },
        true,
      ),
      (
        'tag-values-stringified',
        {
          ...validResponseEvent,
          'tags': [
            ['e', 444],
            ['e', _requestId],
            ['p', _client],
          ]
        },
        true,
      ),
    ])
      (
        operation: 'nwc_response_binding',
        name: binding.$1,
        payload: {
          'event': binding.$2,
          'walletPubkey': _wallet,
          'requestEventId': _requestId,
          'clientPubkey': _client,
        },
        expected: {'bound': binding.$3},
      ),
    for (final response in <(String, Map<String, dynamic>, String, String?)>[
      (
        'success',
        {
          'result_type': 'pay_invoice',
          'error': null,
          'result': {'preimage': _preimage},
        },
        'success',
        _preimage,
      ),
      (
        'wallet-error',
        {
          'result_type': 'pay_invoice',
          'error': {'code': 'DENIED'}
        },
        'failure',
        null,
      ),
      (
        'wrong-result-type',
        {'result_type': 'get_balance', 'error': null},
        'failure',
        null,
      ),
      (
        'missing-result',
        {'result_type': 'pay_invoice', 'error': null},
        'failure',
        null,
      ),
      (
        'wrong-preimage',
        {
          'result_type': 'pay_invoice',
          'error': null,
          'result': {'preimage': '00' * 32},
        },
        'failure',
        null,
      ),
      (
        'mistyped-result',
        {'result_type': 'pay_invoice', 'error': null, 'result': 'bad'},
        'ignore',
        null,
      ),
      (
        'mistyped-preimage',
        {
          'result_type': 'pay_invoice',
          'error': null,
          'result': {'preimage': 1},
        },
        'ignore',
        null,
      ),
    ])
      (
        operation: 'nwc_response',
        name: response.$1,
        payload: {'response': response.$2, 'invoice': nwcInvoice},
        expected: {'decision': response.$3, 'preimage': response.$4},
      ),
    (
      operation: 'nip57_draft',
      name: 'zap-request',
      payload: {
        'senderPubkey': _client,
        'createdAt': _createdAt,
        'recipientPubkey': _recipient,
        'eventId': _eventId,
        'amountMsat': 1000,
        'relays': ['wss://one.example', 'wss://two.example'],
      },
      expected: _draftExpected(
        pubkey: _client,
        createdAt: _createdAt,
        kind: 9734,
        tags: const [
          ['p', _recipient],
          ['e', _eventId],
          ['amount', '1000'],
          ['relays', 'wss://one.example', 'wss://two.example'],
        ],
        content: '',
      ),
    ),
    (
      operation: 'nip57_draft',
      name: 'maximum-amount',
      payload: {
        'senderPubkey': _client,
        'createdAt': _createdAt,
        'recipientPubkey': _recipient,
        'eventId': _eventId,
        'amountMsat': Nip57Protocol.maxAmountMsat,
        'relays': <String>[],
      },
      expected: _draftExpected(
        pubkey: _client,
        createdAt: _createdAt,
        kind: 9734,
        tags: const [
          ['p', _recipient],
          ['e', _eventId],
          ['amount', '2100000000000000000'],
          ['relays'],
        ],
        content: '',
      ),
    ),
    for (final invalid in <(String, Map<String, dynamic>)>[
      ('sender', {'senderPubkey': 'bad'}),
      ('recipient', {'recipientPubkey': 'bad'}),
      ('event', {'eventId': 'bad'}),
      ('zero', {'amountMsat': 0}),
      ('over-maximum', {'amountMsat': Nip57Protocol.maxAmountMsat + 1}),
    ])
      (
        operation: 'nip57_draft',
        name: 'invalid-${invalid.$1}',
        payload: {
          'senderPubkey': _client,
          'createdAt': _createdAt,
          'recipientPubkey': _recipient,
          'eventId': _eventId,
          'amountMsat': 1000,
          'relays': <String>[],
          ...invalid.$2,
        },
        expected: {'accepted': false},
      ),
    (
      operation: 'nip57_receipt',
      name: 'valid-event-receipt',
      payload: _receiptPayload(),
      expected: _receiptExpected(1000),
    ),
    (
      operation: 'nip57_receipt',
      name: 'valid-without-optional-preimage',
      payload: _receiptPayload(includePreimage: false),
      expected: _receiptExpected(1000),
    ),
    (
      operation: 'nip57_receipt',
      name: 'valid-profile-receipt',
      payload: _receiptPayload(
        requestEventId: null,
        receiptEventId: null,
        expectedEventId: null,
      ),
      expected: _receiptExpected(1000),
    ),
    (
      operation: 'nip57_receipt',
      name: 'wrong-receipt-kind-skips-signature',
      payload: _receiptPayload(receiptKind: 9734),
      expected:
          _receiptExpected(null, receiptVerifyCalls: 0, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'wrong-receipt-signer-skips-signature',
      payload: _receiptPayload(receiptPubkey: _wrong),
      expected:
          _receiptExpected(null, receiptVerifyCalls: 0, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'invalid-receipt-signature',
      payload: _receiptPayload(receiptSignatureValid: false),
      expected: _receiptExpected(null, requestVerifyCalls: 0),
    ),
    for (final duplicate in <(String, List<Object?>)>[
      ('bolt11', ['bolt11', 'duplicate']),
      ('description', ['description', '{}']),
      ('preimage', ['preimage', _preimage]),
      ('recipient', ['p', _recipient]),
      ('event', ['e', _eventId]),
    ])
      (
        operation: 'nip57_receipt',
        name: 'duplicate-receipt-${duplicate.$1}',
        payload: _receiptPayload(extraReceiptTags: [duplicate.$2]),
        expected: _receiptExpected(null, requestVerifyCalls: 0),
      ),
    (
      operation: 'nip57_receipt',
      name: 'mistyped-receipt-tag',
      payload: _receiptPayload(extraReceiptTags: const [
        ['client', 1]
      ]),
      expected: _receiptExpected(null, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'invalid-invoice',
      payload: _receiptPayload(invoiceOverride: 'not-an-invoice'),
      expected: _receiptExpected(null, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'description-hash-mismatch',
      payload: _receiptPayload(invoiceDescriptionOverride: 'other'),
      expected: _receiptExpected(null, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'preimage-mismatch',
      payload: _receiptPayload(preimage: '00' * 32),
      expected: _receiptExpected(null, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'wrong-request-kind-skips-signature',
      payload: _receiptPayload(requestKind: 1),
      expected: _receiptExpected(null, requestVerifyCalls: 0),
    ),
    (
      operation: 'nip57_receipt',
      name: 'invalid-request-signature',
      payload: _receiptPayload(requestSignatureValid: false),
      expected: _receiptExpected(null),
    ),
    for (final duplicate in <(String, List<Object?>)>[
      ('amount', ['amount', '1000']),
      ('recipient', ['p', _recipient]),
      ('event', ['e', _eventId]),
    ])
      (
        operation: 'nip57_receipt',
        name: 'duplicate-request-${duplicate.$1}',
        payload: _receiptPayload(extraRequestTags: [duplicate.$2]),
        expected: _receiptExpected(null),
      ),
    (
      operation: 'nip57_receipt',
      name: 'amount-mismatch',
      payload: _receiptPayload(amount: '2000'),
      expected: _receiptExpected(null),
    ),
    (
      operation: 'nip57_receipt',
      name: 'receipt-request-recipient-mismatch',
      payload: _receiptPayload(requestRecipient: _wrong),
      expected: _receiptExpected(null),
    ),
    (
      operation: 'nip57_receipt',
      name: 'expected-recipient-mismatch',
      payload: _receiptPayload(expectedRecipient: _wrong),
      expected: _receiptExpected(null),
    ),
    (
      operation: 'nip57_receipt',
      name: 'expected-event-mismatch',
      payload: _receiptPayload(expectedEventId: _wrong),
      expected: _receiptExpected(null),
    ),
    (
      operation: 'nip57_receipt',
      name: 'event-presence-mismatch',
      payload: _receiptPayload(receiptEventId: null),
      expected: _receiptExpected(null),
    ),
    (
      operation: 'nip57_receipt',
      name: 'event-value-mismatch',
      payload: _receiptPayload(receiptEventId: _wrong),
      expected: _receiptExpected(null),
    ),
  ];
}

Map<String, dynamic> _evaluate(_FixtureCase fixtureCase) {
  final payload = fixtureCase.payload;
  switch (fixtureCase.operation) {
    case 'nwc_uri':
      final connection = NwcConnection.tryParse(
        payload['raw'] as String,
        fallbackRelay: payload['fallback'] as String,
      );
      return connection == null
          ? {'accepted': false}
          : {
              'accepted': true,
              'walletPubkey': connection.walletPubkey,
              'secret': connection.secret,
              'relay': connection.relayUri.toString(),
            };
    case 'nwc_command':
      return {
        'command': NwcProtocol.payInvoiceCommand(payload['invoice'] as String),
      };
    case 'nwc_info_filter':
      try {
        return {
          'accepted': true,
          'wire': NwcProtocol.infoRequest(
            subscriptionId: payload['subscriptionId'] as String,
            walletPubkey: payload['walletPubkey'] as String,
          ),
        };
      } catch (_) {
        return {'accepted': false};
      }
    case 'nwc_info':
      var verifyCalls = 0;
      final decision = NwcProtocol.inspectInfoEvent(
        (payload['event'] as Map).cast<String, dynamic>(),
        walletPubkey: payload['walletPubkey'] as String,
        verifySignature: () {
          verifyCalls++;
          return payload['signatureValid'] as bool;
        },
      );
      return {
        'decision': !decision.shouldComplete
            ? 'ignore'
            : decision.selection == null
                ? 'incompatible'
                : 'supported',
        'scheme': decision.selection?.scheme.code,
        'requestTag': decision.selection?.requestTag,
        'verifyCalls': verifyCalls,
      };
    case 'nwc_request_draft':
      try {
        final encryption = switch (payload['encryption']) {
          'nip44_v2' => const NwcEncryptionSelection.explicit(
              NwcEncryptionScheme.nip44V2,
            ),
          'nip04' => const NwcEncryptionSelection.explicit(
              NwcEncryptionScheme.nip04,
            ),
          _ => const NwcEncryptionSelection.legacyNip04(),
        };
        final draft = NwcProtocol.requestDraft(
          clientPubkey: payload['clientPubkey'] as String,
          createdAt: payload['createdAt'] as int,
          walletPubkey: payload['walletPubkey'] as String,
          encryptedContent: payload['encryptedContent'] as String,
          encryption: encryption,
        );
        return {'accepted': true, 'wire': draft.toJson()};
      } catch (_) {
        return {'accepted': false};
      }
    case 'nwc_response_filter':
      try {
        return {
          'accepted': true,
          'wire': NwcProtocol.responseRequest(
            subscriptionId: payload['subscriptionId'] as String,
            walletPubkey: payload['walletPubkey'] as String,
            requestEventId: payload['requestEventId'] as String,
          ),
        };
      } catch (_) {
        return {'accepted': false};
      }
    case 'nwc_response_binding':
      return {
        'bound': NwcProtocol.responseEventIsBound(
          (payload['event'] as Map).cast<String, dynamic>(),
          walletPubkey: payload['walletPubkey'] as String,
          requestEventId: payload['requestEventId'] as String,
          clientPubkey: payload['clientPubkey'] as String,
        ),
      };
    case 'nwc_response':
      final invoice = Bolt11Invoice.tryParse(payload['invoice'] as String)!;
      final decision = NwcProtocol.inspectResponse(
        (payload['response'] as Map).cast<String, dynamic>(),
        invoice,
      );
      return {
        'decision': !decision.shouldComplete
            ? 'ignore'
            : decision.preimage == null
                ? 'failure'
                : 'success',
        'preimage': decision.preimage,
      };
    case 'nip57_draft':
      try {
        final draft = Nip57Protocol.zapRequestDraft(
          senderPubkey: payload['senderPubkey'] as String,
          createdAt: payload['createdAt'] as int,
          recipientPubkey: payload['recipientPubkey'] as String,
          eventId: payload['eventId'] as String,
          amountMsat: payload['amountMsat'] as int,
          relays: List<String>.from(payload['relays'] as List),
        );
        return {'accepted': true, 'wire': draft.toJson()};
      } catch (_) {
        return {'accepted': false};
      }
    case 'nip57_receipt':
      var receiptVerifyCalls = 0;
      var requestVerifyCalls = 0;
      final receipt = (payload['receipt'] as Map).cast<String, dynamic>();
      final envelope = Nip57Protocol.inspectReceipt(
        receipt,
        receiptSigner: payload['receiptSigner'] as String,
        verifySignature: () {
          receiptVerifyCalls++;
          return payload['receiptSignatureValid'] as bool;
        },
      );
      int? amount;
      if (envelope != null) {
        final invoice = Bolt11Invoice.tryParse(envelope.bolt11);
        if (invoice != null) {
          amount = Nip57Protocol.boundReceiptAmount(
            envelope: envelope,
            invoice: invoice,
            request: (payload['request'] as Map).cast<String, dynamic>(),
            verifyRequestSignature: () {
              requestVerifyCalls++;
              return payload['requestSignatureValid'] as bool;
            },
            eventId: payload['expectedEventId'] as String?,
            recipientPubkey: payload['expectedRecipient'] as String?,
          );
        }
      }
      return {
        'amountMsat': amount,
        'receiptVerifyCalls': receiptVerifyCalls,
        'requestVerifyCalls': requestVerifyCalls,
      };
    default:
      throw StateError('Unknown operation: ${fixtureCase.operation}');
  }
}

String _packedJson(Object? value) =>
    base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');

String buildLightningProtocolFixture() {
  final lines = <String>[
    '# Roadstr deterministic NIP-47/NIP-57 fixture v1.',
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
        'Lightning mismatch for ${fixtureCase.operation}/${fixtureCase.name}: '
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
  final generated = buildLightningProtocolFixture();
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

String _invoice({
  required String description,
  required String preimageHex,
  int amountMsat = 1000,
}) {
  if (amountMsat % 100 != 0) {
    throw ArgumentError.value(amountMsat, 'amountMsat');
  }
  final preimage = <int>[
    for (var i = 0; i < preimageHex.length; i += 2)
      int.parse(preimageHex.substring(i, i + 2), radix: 16),
  ];
  final words = <int>[
    for (var shift = 30; shift >= 0; shift -= 5) (_createdAt >> shift) & 31,
    ..._tag(1, _toFiveBits(sha256.convert(preimage).bytes)),
    ..._tag(23, _toFiveBits(sha256.convert(utf8.encode(description)).bytes)),
    ..._tag(6, const [18, 24]),
    ...List<int>.filled(104, 0),
  ];
  final hrp = 'lnbc${amountMsat ~/ 100}n';
  return const Bech32Codec().encode(Bech32(hrp, words), 8192);
}

List<int> _tag(int type, List<int> value) =>
    [type, value.length >> 5, value.length & 31, ...value];

List<int> _toFiveBits(List<int> bytes) {
  var accumulator = 0;
  var bits = 0;
  final out = <int>[];
  for (final byte in bytes) {
    accumulator = (accumulator << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      out.add((accumulator >> bits) & 31);
    }
  }
  if (bits > 0) out.add((accumulator << (5 - bits)) & 31);
  return out;
}
