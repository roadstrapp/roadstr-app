import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:nostr_tools/nostr_tools.dart' show Nip04;
import 'package:pointycastle/export.dart';
import 'package:roadstr/services/nip04.dart';

const outputPath = 'android/app/src/test/resources/parity/nip04_v1.tsv';

const _sec1 =
    '0000000000000000000000000000000000000000000000000000000000000001';
const _sec2 =
    '0000000000000000000000000000000000000000000000000000000000000002';
const _pub1 =
    '79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798';
const _pub2 =
    'c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5';

Uint8List _hexBytes(String value) {
  if (value.length.isOdd) throw const FormatException('odd hex length');
  return Uint8List.fromList([
    for (var index = 0; index < value.length; index += 2)
      int.parse(value.substring(index, index + 2), radix: 16),
  ]);
}

String _hex(List<int> value) =>
    value.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();

String _text64(String value) => base64Url.encode(utf8.encode(value));

String _sha256(String value) => sha256.convert(utf8.encode(value)).toString();

void _expectEqual(String name, Object actual, Object expected) {
  if (actual != expected) {
    throw StateError('$name mismatch: $actual != $expected');
  }
}

void _expectReject(String name, void Function() action) {
  try {
    action();
  } catch (_) {
    return;
  }
  throw StateError('$name was unexpectedly accepted');
}

Uint8List _rawAesCbcEncrypt(
  Uint8List key,
  Uint8List iv,
  Uint8List plaintext,
) {
  const blockSize = 16;
  final padding = blockSize - plaintext.length % blockSize;
  final padded = Uint8List(plaintext.length + padding)
    ..setRange(0, plaintext.length, plaintext)
    ..fillRange(plaintext.length, plaintext.length + padding, padding);
  final cipher = CBCBlockCipher(AESEngine())
    ..init(true, ParametersWithIV(KeyParameter(key), iv));
  final output = Uint8List(padded.length);
  for (var offset = 0; offset < padded.length; offset += blockSize) {
    cipher.processBlock(padded, offset, output, offset);
  }
  return output;
}

String _rawPayload(Uint8List secret, Uint8List iv, Uint8List plaintext) =>
    '${base64.encode(_rawAesCbcEncrypt(secret, iv, plaintext))}'
    '?iv=${base64.encode(iv)}';

final _sharedVectors = <({String name, String secret, String publicKey})>[
  (name: 'generator-self', secret: _sec1, publicKey: _pub1),
  (name: 'one-times-two-g', secret: _sec1, publicKey: _pub2),
  (name: 'two-times-generator', secret: _sec2, publicKey: _pub1),
  (name: 'two-times-two-g', secret: _sec2, publicKey: _pub2),
  (
    name: 'secret-n-minus-one',
    secret: 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140',
    publicKey: _pub1,
  ),
  (
    name: 'secret-n-minus-two',
    secret: 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd036413f',
    publicKey: _pub1,
  ),
  (
    name: 'random-01',
    secret: '315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268',
    publicKey:
        'c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133',
  ),
  (
    name: 'uppercase-hex',
    secret: 'A1E37752C9FDC1273BE53F68C5F74BE7C8905728E8DE75800B94262F9497C86E',
    publicKey:
        '03BB7947065DDE12BA991EA045132581D0954F042C84E06D8C00066E23C1A800',
  ),
];

const _invalidSharedVectors =
    <({String name, String secret, String publicKey})>[
  (
    name: 'secret-zero',
    secret: '0000000000000000000000000000000000000000000000000000000000000000',
    publicKey: _pub1,
  ),
  (
    name: 'secret-order',
    secret: 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141',
    publicKey: _pub1,
  ),
  (
    name: 'secret-all-ff',
    secret: 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
    publicKey: _pub1,
  ),
  (name: 'secret-short', secret: '01', publicKey: _pub1),
  (
    name: 'secret-non-hex',
    secret: 'gg00000000000000000000000000000000000000000000000000000000000000',
    publicKey: _pub1,
  ),
  (
    name: 'public-zero',
    secret: _sec1,
    publicKey:
        '0000000000000000000000000000000000000000000000000000000000000000',
  ),
  (
    name: 'public-no-square-root',
    secret: _sec2,
    publicKey:
        '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
  ),
  (
    name: 'public-all-ff',
    secret: _sec1,
    publicKey:
        'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
  ),
  (name: 'public-short', secret: _sec1, publicKey: '02'),
  (
    name: 'public-non-hex',
    secret: _sec1,
    publicKey:
        'zz00000000000000000000000000000000000000000000000000000000000000',
  ),
];

final _encryptVectors = <({String name, String plaintext, String iv})>[
  (
    name: 'empty',
    plaintext: '',
    iv: '00000000000000000000000000000000',
  ),
  (
    name: 'one-byte',
    plaintext: 'a',
    iv: '000102030405060708090a0b0c0d0e0f',
  ),
  (
    name: 'fifteen-bytes',
    plaintext: 'x' * 15,
    iv: '101112131415161718191a1b1c1d1e1f',
  ),
  (
    name: 'sixteen-bytes',
    plaintext: 'x' * 16,
    iv: '202122232425262728292a2b2c2d2e2f',
  ),
  (
    name: 'seventeen-bytes',
    plaintext: 'x' * 17,
    iv: '303132333435363738393a3b3c3d3e3f',
  ),
  (
    name: 'nwc-pay-invoice',
    plaintext: '{"method":"pay_invoice","params":{"invoice":"lnbc1roadstr"}}',
    iv: '404142434445464748494a4b4c4d4e4f',
  ),
  (
    name: 'nwc-response',
    plaintext:
        '{"result_type":"pay_invoice","error":null,"result":{"preimage":"${'ab' * 32}"}}',
    iv: '505152535455565758595a5b5c5d5e5f',
  ),
  (
    name: 'unicode',
    plaintext: 'Roadstr — ciao 🚗 Привет こんにちは',
    iv: '606162636465666768696a6b6c6d6e6f',
  ),
  (
    name: 'embedded-nul',
    plaintext: 'before\u0000after',
    iv: '707172737475767778797a7b7c7d7e7f',
  ),
  (
    name: 'newlines',
    plaintext: 'line one\nline two\r\nline three',
    iv: '808182838485868788898a8b8c8d8e8f',
  ),
  (
    name: 'base64-heavy-iv',
    plaintext: 'plus, slash and padding stay canonical',
    iv: 'ffffffffffffffffffffffffffffffff',
  ),
  (
    name: 'self-encryption',
    plaintext: 'same Nostr identity on both sides',
    iv: '90a0b0c0d0e0f0001020304050607080',
  ),
];

String buildNip04Fixture() {
  final lines = <String>[
    '# Roadstr NIP-04 parity fixture v1.',
    '# Valid payloads are replayed through production Dart and nostr_tools 1.0.9.',
    '# op\\tname\\tfields...',
  ];
  final legacy = Nip04();

  for (final vector in _sharedVectors) {
    final secret = Nip04Cipher.sharedSecret(vector.secret, vector.publicKey);
    lines.add([
      'shared',
      vector.name,
      vector.secret,
      vector.publicKey,
      _hex(secret),
    ].join('\t'));
  }
  _expectEqual(
    'generator shared secret',
    _hex(Nip04Cipher.sharedSecret(_sec1, _pub1)),
    _pub1,
  );
  _expectEqual(
    'ECDH symmetry',
    _hex(Nip04Cipher.sharedSecret(_sec1, _pub2)),
    _hex(Nip04Cipher.sharedSecret(_sec2, _pub1)),
  );

  for (final vector in _invalidSharedVectors) {
    _expectReject(
      vector.name,
      () => Nip04Cipher.sharedSecret(vector.secret, vector.publicKey),
    );
    lines.add([
      'shared_reject',
      vector.name,
      vector.secret,
      vector.publicKey,
    ].join('\t'));
  }

  for (final vector in _encryptVectors) {
    const privateKey = _sec1;
    final publicKey = vector.name == 'self-encryption' ? _pub1 : _pub2;
    final decryptPrivateKey = vector.name == 'self-encryption' ? _sec1 : _sec2;
    const decryptPublicKey = _pub1;
    final payload = Nip04Cipher.encryptWithIv(
      privateKey,
      publicKey,
      vector.plaintext,
      _hexBytes(vector.iv),
    );
    _expectEqual(
      '${vector.name} Roadstr round trip',
      Nip04Cipher.decrypt(decryptPrivateKey, decryptPublicKey, payload),
      vector.plaintext,
    );
    _expectEqual(
      '${vector.name} nostr_tools decrypt',
      legacy.decrypt(decryptPrivateKey, decryptPublicKey, payload),
      vector.plaintext,
    );
    lines.add([
      'encrypt',
      vector.name,
      privateKey,
      publicKey,
      vector.iv,
      _text64(vector.plaintext),
      payload,
    ].join('\t'));
  }

  final shared = Nip04Cipher.sharedSecret(_sec1, _pub2);
  final baselineIv = _hexBytes('000102030405060708090a0b0c0d0e0f');
  final baseline = Nip04Cipher.encryptWithSharedSecret(
    shared,
    'padding-check',
    baselineIv,
  );
  final baselineParts = baseline.split('?iv=');
  final compatibleBase64 = <({String name, String payload})>[
    (
      name: 'url-safe-alphabet',
      payload: '${baselineParts[0].replaceAll('+', '-').replaceAll('/', '_')}'
          '?iv=${baselineParts[1].replaceAll('+', '-').replaceAll('/', '_')}',
    ),
    (
      name: 'percent-escaped-base64',
      payload: '${baselineParts[0].replaceAll('=', '%3D')}'
          '?iv=${baselineParts[1].replaceAll('=', '%3D')}',
    ),
  ];
  for (final vector in compatibleBase64) {
    late final String roadstrPlaintext;
    try {
      roadstrPlaintext =
          Nip04Cipher.decryptWithSharedSecret(shared, vector.payload);
    } catch (error) {
      throw StateError('${vector.name} Roadstr decrypt rejected: $error');
    }
    _expectEqual(
      '${vector.name} Roadstr decrypt',
      roadstrPlaintext,
      'padding-check',
    );
    _expectEqual(
      '${vector.name} nostr_tools decrypt',
      legacy.decrypt(_sec2, _pub1, vector.payload),
      'padding-check',
    );
    lines.add([
      'decrypt',
      vector.name,
      _hex(shared),
      _text64(vector.payload),
      _text64('padding-check'),
    ].join('\t'));
  }
  final changedIv = Uint8List.fromList(baselineIv)..[15] ^= 1;
  final invalidUtf8 = _rawPayload(
    shared,
    baselineIv,
    Uint8List.fromList([0xc3, 0x28]),
  );
  final hostile = <({String name, String payload})>[
    (name: 'empty', payload: ''),
    (name: 'no-delimiter', payload: baselineParts[0]),
    (name: 'delimiter-only', payload: '?iv='),
    (name: 'empty-ciphertext', payload: '?iv=${baselineParts[1]}'),
    (name: 'empty-iv', payload: '${baselineParts[0]}?iv='),
    (name: 'double-delimiter', payload: '$baseline?iv=${baselineParts[1]}'),
    (name: 'invalid-cipher-char', payload: '%${baseline.substring(1)}'),
    (
      name: 'invalid-iv-char',
      payload: '${baselineParts[0]}?iv=%${baselineParts[1].substring(1)}'
    ),
    (
      name: 'cipher-whitespace',
      payload: ' ${baselineParts[0]}?iv=${baselineParts[1]}'
    ),
    (
      name: 'iv-whitespace',
      payload: '${baselineParts[0]}?iv=${baselineParts[1]} '
    ),
    (
      name: 'cipher-unpadded',
      payload: '${baselineParts[0].replaceAll('=', '')}?iv=${baselineParts[1]}'
    ),
    (
      name: 'iv-unpadded',
      payload: '${baselineParts[0]}?iv=${baselineParts[1].replaceAll('=', '')}'
    ),
    (
      name: 'cipher-nonzero-pad-bits',
      payload:
          '${baselineParts[0].substring(0, baselineParts[0].length - 3)}B=='
          '?iv=${baselineParts[1]}'
    ),
    (
      name: 'iv-nonzero-pad-bits',
      payload: '${baselineParts[0]}?iv='
          '${baselineParts[1].substring(0, baselineParts[1].length - 3)}B=='
    ),
    (name: 'cipher-padding-middle', payload: 'AA=A?iv=${baselineParts[1]}'),
    (name: 'iv-padding-middle', payload: '${baselineParts[0]}?iv=AA=A'),
    (
      name: 'iv-fifteen-bytes',
      payload: '${baselineParts[0]}?iv=${base64.encode(Uint8List(15))}'
    ),
    (
      name: 'iv-seventeen-bytes',
      payload: '${baselineParts[0]}?iv=${base64.encode(Uint8List(17))}'
    ),
    (
      name: 'cipher-one-byte',
      payload: '${base64.encode([0])}?iv=${baselineParts[1]}'
    ),
    (
      name: 'cipher-fifteen-bytes',
      payload: '${base64.encode(Uint8List(15))}?iv=${baselineParts[1]}'
    ),
    (
      name: 'cipher-seventeen-bytes',
      payload: '${base64.encode(Uint8List(17))}?iv=${baselineParts[1]}'
    ),
    (
      name: 'bad-pkcs7-padding',
      payload: '${baselineParts[0]}?iv=${base64.encode(changedIv)}'
    ),
    (name: 'invalid-utf8', payload: invalidUtf8),
  ];
  for (final vector in hostile) {
    _expectReject(
      vector.name,
      () => Nip04Cipher.decryptWithSharedSecret(shared, vector.payload),
    );
    lines.add([
      'decrypt_reject',
      vector.name,
      _hex(shared),
      _text64(vector.payload).isEmpty ? '-' : _text64(vector.payload),
    ].join('\t'));
  }

  final oversizedLength = Nip04Cipher.maxPayloadCharacters + 1;
  _expectReject(
    'oversized-payload',
    () => Nip04Cipher.decryptWithSharedSecret(
      shared,
      'A' * oversizedLength,
    ),
  );
  lines.add([
    'decrypt_reject_size',
    'oversized-payload',
    _hex(shared),
    oversizedLength,
  ].join('\t'));

  final longVectors = <({String name, String pattern, int repeats, String iv})>[
    (
      name: 'one-kibibyte',
      pattern: 'x',
      repeats: 1024,
      iv: 'a0a1a2a3a4a5a6a7a8a9aaabacadaeaf',
    ),
    (
      name: 'maximum-ascii',
      pattern: 'x',
      repeats: Nip04Cipher.maxPlaintextBytes,
      iv: 'b0b1b2b3b4b5b6b7b8b9babbbcbdbebf',
    ),
    (
      name: 'maximum-three-byte-utf8',
      pattern: '€',
      repeats: Nip04Cipher.maxPlaintextBytes ~/ 3,
      iv: 'c0c1c2c3c4c5c6c7c8c9cacbcccdcecf',
    ),
  ];
  for (final vector in longVectors) {
    final plaintext = vector.pattern * vector.repeats;
    final payload = Nip04Cipher.encryptWithSharedSecret(
      shared,
      plaintext,
      _hexBytes(vector.iv),
    );
    _expectEqual(
      '${vector.name} long round trip',
      Nip04Cipher.decryptWithSharedSecret(shared, payload),
      plaintext,
    );
    _expectEqual(
      '${vector.name} legacy decrypt',
      legacy.decrypt(_sec2, _pub1, payload),
      plaintext,
    );
    lines.add([
      'long',
      vector.name,
      _hex(shared),
      vector.iv,
      _text64(vector.pattern),
      vector.repeats,
      _sha256(plaintext),
      _sha256(payload),
    ].join('\t'));
  }

  final encryptRejects =
      <({String name, String secret, String iv, String pattern, int repeats})>[
    (
      name: 'short-secret',
      secret: '00',
      iv: '000102030405060708090a0b0c0d0e0f',
      pattern: 'x',
      repeats: 1,
    ),
    (
      name: 'short-iv',
      secret: _hex(shared),
      iv: '000102030405060708090a0b0c0d0e',
      pattern: 'x',
      repeats: 1,
    ),
    (
      name: 'long-iv',
      secret: _hex(shared),
      iv: '000102030405060708090a0b0c0d0e0f10',
      pattern: 'x',
      repeats: 1,
    ),
    (
      name: 'ascii-over-limit',
      secret: _hex(shared),
      iv: '000102030405060708090a0b0c0d0e0f',
      pattern: 'x',
      repeats: Nip04Cipher.maxPlaintextBytes + 1,
    ),
    (
      name: 'utf8-over-limit',
      secret: _hex(shared),
      iv: '000102030405060708090a0b0c0d0e0f',
      pattern: '€',
      repeats: Nip04Cipher.maxPlaintextBytes ~/ 3 + 1,
    ),
  ];
  for (final vector in encryptRejects) {
    _expectReject(
      vector.name,
      () => Nip04Cipher.encryptWithSharedSecret(
        _hexBytes(vector.secret),
        vector.pattern * vector.repeats,
        _hexBytes(vector.iv),
      ),
    );
    lines.add([
      'encrypt_reject',
      vector.name,
      vector.secret,
      vector.iv,
      _text64(vector.pattern),
      vector.repeats,
    ].join('\t'));
  }

  // The legacy library chooses its own random IV, so this compatibility check
  // deliberately affects no committed bytes in the deterministic fixture.
  const legacyPlaintext = 'nostr_tools random-IV compatibility';
  final legacyPayload = legacy.encrypt(_sec1, _pub2, legacyPlaintext);
  _expectEqual(
    'legacy encrypt Roadstr decrypt',
    Nip04Cipher.decrypt(_sec2, _pub1, legacyPayload),
    legacyPlaintext,
  );

  return '${lines.join('\n')}\n';
}

Future<void> main(List<String> arguments) async {
  final generated = buildNip04Fixture();
  final file = File(outputPath);
  if (arguments.contains('--check')) {
    if (!file.existsSync() || file.readAsStringSync() != generated) {
      stderr.writeln('$outputPath is stale; regenerate it without --check.');
      exitCode = 1;
    }
    return;
  }
  file.parent.createSync(recursive: true);
  file.writeAsStringSync(generated);
}
