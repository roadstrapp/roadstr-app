import 'dart:convert';
import 'dart:io';

import 'package:bech32/bech32.dart';
import 'package:crypto/crypto.dart';
import 'package:roadstr/services/lnurl_protocol.dart';

const outputPath =
    'android/app/src/test/resources/parity/lnurl_protocol_v1.tsv';
const _createdAt = 1704067200;
const _pubkey =
    'ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD';

typedef _FixtureCase = ({
  String operation,
  String name,
  Map<String, dynamic> payload,
  Map<String, dynamic> expected,
});

Map<String, dynamic> _validMetadata({
  String callback = 'https://pay.example/callback',
  num min = 1000,
  num max = 5000000,
  String metadata = '[["text/plain","Roadstr tip"]]',
  bool? allowsNostr,
  Object? nostrPubkey,
}) =>
    {
      'callback': callback,
      'minSendable': min,
      'maxSendable': max,
      'metadata': metadata,
      if (allowsNostr != null) 'allowsNostr': allowsNostr,
      if (nostrPubkey != null) 'nostrPubkey': nostrPubkey,
    };

Map<String, dynamic> _acceptedMetadata({
  String callback = 'https://pay.example/callback',
  int min = 1000,
  int max = 5000000,
  String metadata = '[["text/plain","Roadstr tip"]]',
  bool allowsNostr = false,
  String? nostrPubkey,
}) =>
    {
      'accepted': true,
      'callback': callback,
      'minSendable': min,
      'maxSendable': max,
      'metadata': metadata,
      'nostrPubkey': nostrPubkey,
      'allowsNostr': allowsNostr,
    };

Map<String, dynamic> _payInfoPayload({
  String callback = 'https://pay.example/callback',
  int min = 1000,
  int max = 5000,
  String metadata = '[["text/plain","Roadstr tip"]]',
  bool allowsNostr = false,
  String? nostrPubkey,
}) =>
    {
      'callback': callback,
      'minSendable': min,
      'maxSendable': max,
      'metadata': metadata,
      'allowsNostr': allowsNostr,
      'nostrPubkey': nostrPubkey,
    };

List<_FixtureCase> _cases() {
  final lnurl = _encodeLnurl('https://pay.example/lnurl?tag=payRequest');
  final unsafeLnurl = _encodeLnurl('https://127.0.0.1/pay');
  final oversizedLnurl = _encodeLnurl('https://pay.example/${'a' * 2049}');
  final zap = <String, dynamic>{
    'kind': 9734,
    'content': 'ciao ⚡',
  };
  final zapJson = jsonEncode(zap);
  final zapQuery = Uri.encodeQueryComponent(zapJson);
  const metadata = '[["text/plain","Roadstr tip"]]';
  final validInvoice = _invoice(description: metadata);
  final zapInvoice = _invoice(description: zapJson);

  return [
    for (final source in <(String, String, String?)>[
      (
        'address',
        'alice@example.com',
        'https://example.com/.well-known/lnurlp/alice'
      ),
      (
        'address-whitespace',
        '  alice @ EXAMPLE.COM  ',
        'https://example.com/.well-known/lnurlp/alice'
      ),
      (
        'encoded-user',
        'alice+tips @example.com',
        'https://example.com/.well-known/lnurlp/alice+tips'
      ),
      (
        'encoded-user-space',
        'alice smith@example.com',
        'https://example.com/.well-known/lnurlp/alice%20smith'
      ),
      ('lud06', lnurl, 'https://pay.example/lnurl?tag=payRequest'),
      (
        'uppercase-lud06',
        lnurl.toUpperCase(),
        'https://pay.example/lnurl?tag=payRequest'
      ),
      ('multiple-at', 'alice@example.com@evil.example', null),
      ('empty-user', '@example.com', null),
      ('empty-domain', 'alice@', null),
      ('slash-in-user', 'alice/tips@example.com', null),
      ('port-in-domain', 'alice@example.com:443', null),
      ('overlong-address', '${'a' * 250}@example.com', null),
      ('localhost', 'alice@localhost', null),
      ('localhost-subdomain', 'alice@wallet.localhost', null),
      ('private-ip', 'alice@192.168.1.2', null),
      (
        'invalid-lnurl-checksum',
        '${lnurl.substring(0, lnurl.length - 1)}q',
        null
      ),
      ('unsafe-lnurl', unsafeLnurl, null),
      ('oversized-lnurl-payload', oversizedLnurl, null),
      ('lnurl-surrounding-space', ' $lnurl ', null),
    ])
      (
        operation: 'source',
        name: source.$1,
        payload: {'input': source.$2},
        expected: {'url': source.$3},
      ),
    for (final uri in <(String, String, bool)>[
      ('ordinary', 'https://pay.example/callback', true),
      ('uppercase', 'HTTPS://EXAMPLE.COM/callback', true),
      ('port-query-fragment', 'https://pay.example:8443/cb?x=1#ok', true),
      ('empty-userinfo-compatible', 'https://@pay.example/cb', true),
      ('http', 'http://pay.example/callback', false),
      ('missing-host', 'https:///callback', false),
      ('userinfo', 'https://user@pay.example/cb', false),
      ('localhost', 'https://localhost/cb', false),
      ('localhost-subdomain', 'https://x.localhost/cb', false),
      ('public-ipv4', 'https://8.8.8.8/cb', true),
      ('invalid-ipv4', 'https://999.1.1.1/cb', false),
      ('private-10', 'https://10.0.0.1/cb', false),
      ('loopback-127', 'https://127.0.0.1/cb', false),
      ('link-local', 'https://169.254.2.3/cb', false),
      ('private-172', 'https://172.31.255.255/cb', false),
      ('public-172', 'https://172.32.0.1/cb', true),
      ('private-192', 'https://192.168.0.1/cb', false),
      // Lexical parity only: DNS/IP classification rejects this later.
      ('ipv6-loopback-lexically-safe', 'https://[::1]/cb', true),
    ])
      (
        operation: 'safe_https',
        name: uri.$1,
        payload: {'url': uri.$2},
        expected: {'safe': uri.$3},
      ),
    (
      operation: 'metadata',
      name: 'plain',
      payload: {'data': _validMetadata()},
      expected: _acceptedMetadata(),
    ),
    (
      operation: 'metadata',
      name: 'nostr-uppercase-normalized',
      payload: {
        'data': _validMetadata(allowsNostr: true, nostrPubkey: _pubkey),
      },
      expected: _acceptedMetadata(
        allowsNostr: true,
        nostrPubkey: _pubkey.toLowerCase(),
      ),
    ),
    (
      operation: 'metadata',
      name: 'callback-canonicalized',
      payload: {
        'data': _validMetadata(callback: 'HTTPS://PAY.EXAMPLE/a%2fb'),
      },
      expected: _acceptedMetadata(callback: 'https://pay.example/a%2Fb'),
    ),
    (
      operation: 'metadata',
      name: 'fractional-amounts-truncated',
      payload: {'data': _validMetadata(min: 1000.9, max: 5000.9)},
      expected: _acceptedMetadata(min: 1000, max: 5000),
    ),
    (
      operation: 'metadata',
      name: 'hundred-entries',
      payload: {
        'data': _validMetadata(
          metadata: jsonEncode(List.generate(100, (i) => ['text/plain', '$i'])),
        ),
      },
      expected: _acceptedMetadata(
        metadata: jsonEncode(List.generate(100, (i) => ['text/plain', '$i'])),
      ),
    ),
    for (final invalid in <(String, Map<String, dynamic>)>[
      ('server-error', {..._validMetadata(), 'status': 'ERROR'}),
      ('missing-callback', {..._validMetadata()}..remove('callback')),
      ('http-callback', _validMetadata(callback: 'http://pay.example/cb')),
      (
        'userinfo-callback',
        _validMetadata(callback: 'https://u@pay.example/cb')
      ),
      ('missing-min', {..._validMetadata()}..remove('minSendable')),
      ('mistyped-min', {..._validMetadata(), 'minSendable': '1000'}),
      ('zero-min', _validMetadata(min: 0)),
      ('max-before-min', _validMetadata(min: 2000, max: 1000)),
      ('missing-metadata', {..._validMetadata()}..remove('metadata')),
      ('malformed-metadata', _validMetadata(metadata: '{bad')),
      ('empty-metadata', _validMetadata(metadata: '[]')),
      (
        'mistyped-metadata-item',
        _validMetadata(metadata: '[["text/plain",1]]')
      ),
      (
        'metadata-item-arity',
        _validMetadata(metadata: '[["text/plain","x","y"]]')
      ),
      (
        'too-many-metadata-items',
        _validMetadata(
          metadata: jsonEncode(List.generate(101, (i) => ['text/plain', '$i'])),
        ),
      ),
      ('missing-nostr-key', _validMetadata(allowsNostr: true)),
      ('bad-nostr-key', _validMetadata(allowsNostr: true, nostrPubkey: 'bad')),
      ('mistyped-optional-key', _validMetadata(nostrPubkey: 7)),
    ])
      (
        operation: 'metadata',
        name: invalid.$1,
        payload: {'data': invalid.$2},
        expected: {'accepted': false},
      ),
    for (final request in <(
      String,
      Map<String, dynamic>,
      int,
      Map<String, dynamic>?,
      String?,
      String?
    )>[
      (
        'minimum',
        _payInfoPayload(),
        1000,
        null,
        'https://pay.example/callback?amount=1000',
        metadata,
      ),
      (
        'maximum',
        _payInfoPayload(),
        5000,
        null,
        'https://pay.example/callback?amount=5000',
        metadata,
      ),
      ('below-minimum', _payInfoPayload(), 999, null, null, null),
      ('above-maximum', _payInfoPayload(), 5001, null, null, null),
      (
        'existing-query-last-duplicate-wins',
        _payInfoPayload(
          callback: 'https://pay.example/cb?x=first&x=last&e=&flag#receipt',
        ),
        1000,
        null,
        'https://pay.example/cb?x=last&e&flag&amount=1000#receipt',
        metadata,
      ),
      (
        'existing-amount-overridden-in-place',
        _payInfoPayload(callback: 'https://pay.example/cb?amount=9&tag=road'),
        1000,
        null,
        'https://pay.example/cb?amount=1000&tag=road',
        metadata,
      ),
      (
        'zap-request',
        _payInfoPayload(allowsNostr: true, nostrPubkey: _pubkey.toLowerCase()),
        1000,
        zap,
        'https://pay.example/callback?amount=1000&nostr=$zapQuery',
        zapJson,
      ),
      (
        'zap-ignored-when-not-advertised',
        _payInfoPayload(),
        1000,
        zap,
        'https://pay.example/callback?amount=1000',
        metadata,
      ),
      (
        'unsafe-callback',
        _payInfoPayload(callback: 'https://127.0.0.1/cb'),
        1000,
        null,
        null,
        null,
      ),
    ])
      (
        operation: 'invoice_request',
        name: request.$1,
        payload: {
          'payInfo': request.$2,
          'amountMsat': request.$3,
          'zapRequest': request.$4,
        },
        expected: {'url': request.$5, 'description': request.$6},
      ),
    for (final response
        in <(String, Map<String, dynamic>, int, String, int, bool)>[
      ('valid', {'pr': validInvoice}, 1000, metadata, _createdAt + 100, true),
      ('valid-zap', {'pr': zapInvoice}, 1000, zapJson, _createdAt + 100, true),
      (
        'server-error',
        {'status': 'ERROR', 'pr': validInvoice},
        1000,
        metadata,
        _createdAt + 100,
        false
      ),
      ('missing-invoice', {}, 1000, metadata, _createdAt + 100, false),
      ('mistyped-invoice', {'pr': 7}, 1000, metadata, _createdAt + 100, false),
      (
        'invalid-invoice',
        {'pr': 'not-an-invoice'},
        1000,
        metadata,
        _createdAt + 100,
        false
      ),
      (
        'amount-mismatch',
        {'pr': validInvoice},
        2000,
        metadata,
        _createdAt + 100,
        false
      ),
      (
        'description-mismatch',
        {'pr': validInvoice},
        1000,
        'other',
        _createdAt + 100,
        false
      ),
      (
        'expiry-boundary',
        {'pr': validInvoice},
        1000,
        metadata,
        _createdAt + 600,
        false
      ),
    ])
      (
        operation: 'invoice_response',
        name: response.$1,
        payload: {
          'data': response.$2,
          'requestUrl': 'https://pay.example/callback?amount=${response.$3}',
          'description': response.$4,
          'amountMsat': response.$3,
          'now': response.$5,
        },
        expected: {
          'accepted': response.$6,
          'invoice': response.$6 ? response.$2['pr'] : null,
        },
      ),
  ];
}

Map<String, dynamic> _evaluate(_FixtureCase fixtureCase) {
  final payload = fixtureCase.payload;
  switch (fixtureCase.operation) {
    case 'source':
      return {
        'url': LnurlProtocol.resolveMetadataUri(payload['input'] as String)
            ?.toString(),
      };
    case 'safe_https':
      final uri = Uri.tryParse(payload['url'] as String);
      return {'safe': uri != null && LnurlProtocol.isSafeHttpsUri(uri)};
    case 'metadata':
      final info = LnurlProtocol.parsePayInfo(
        (payload['data'] as Map).cast<String, dynamic>(),
      );
      return info == null
          ? {'accepted': false}
          : {
              'accepted': true,
              'callback': info.callback,
              'minSendable': info.minSendable,
              'maxSendable': info.maxSendable,
              'metadata': info.metadata,
              'nostrPubkey': info.nostrPubkey,
              'allowsNostr': info.allowsNostr,
            };
    case 'invoice_request':
      final info =
          _payInfo((payload['payInfo'] as Map).cast<String, dynamic>());
      final request = LnurlProtocol.buildInvoiceRequest(
        payInfo: info,
        amountMsat: payload['amountMsat'] as int,
        zapRequest: (payload['zapRequest'] as Map?)?.cast<String, dynamic>(),
      );
      return {
        'url': request?.uri.toString(),
        'description': request?.description,
      };
    case 'invoice_response':
      final request = LnurlInvoiceRequest(
        uri: Uri.parse(payload['requestUrl'] as String),
        description: payload['description'] as String,
      );
      final invoice = LnurlProtocol.validateInvoiceResponse(
        data: (payload['data'] as Map).cast<String, dynamic>(),
        request: request,
        amountMsat: payload['amountMsat'] as int,
        nowUnixSeconds: payload['now'] as int,
      );
      return {'accepted': invoice != null, 'invoice': invoice};
    default:
      throw StateError('Unknown operation: ${fixtureCase.operation}');
  }
}

LnurlPayInfo _payInfo(Map<String, dynamic> value) => LnurlPayInfo(
      callback: value['callback'] as String,
      minSendable: value['minSendable'] as int,
      maxSendable: value['maxSendable'] as int,
      metadata: value['metadata'] as String,
      nostrPubkey: value['nostrPubkey'] as String?,
      allowsNostr: value['allowsNostr'] as bool,
    );

String _packedJson(Object? value) =>
    base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');

String buildLnurlProtocolFixture() {
  final lines = <String>[
    '# Roadstr deterministic LNURL-pay fixture v1.',
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
        'LNURL mismatch for ${fixtureCase.operation}/${fixtureCase.name}: '
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
  final generated = buildLnurlProtocolFixture();
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

String _encodeLnurl(String value) => const Bech32Codec().encode(
      Bech32('lnurl', _toFiveBits(utf8.encode(value))),
      8192,
    );

String _invoice({required String description, int amountMsat = 1000}) {
  final preimage = List<int>.generate(32, (index) => index);
  final words = <int>[
    for (var shift = 30; shift >= 0; shift -= 5) (_createdAt >> shift) & 31,
    ..._tag(1, _toFiveBits(sha256.convert(preimage).bytes)),
    ..._tag(23, _toFiveBits(sha256.convert(utf8.encode(description)).bytes)),
    ..._tag(6, const [18, 24]),
    ...List<int>.filled(104, 0),
  ];
  return const Bech32Codec().encode(
    Bech32('lnbc${amountMsat ~/ 100}n', words),
    8192,
  );
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
