import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:nostr_tools/nostr_tools.dart' show KeyApi;
import 'package:roadstr/services/nip44.dart';

const outputPath = 'android/app/src/test/resources/parity/nip44_v2_v1.tsv';

Uint8List _hexBytes(String value) {
  if (value.length.isOdd) throw FormatException('odd hex length');
  return Uint8List.fromList([
    for (var index = 0; index < value.length; index += 2)
      int.parse(value.substring(index, index + 2), radix: 16),
  ]);
}

String _hex(List<int> value) =>
    value.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();

String _text64(String value) => base64Url.encode(utf8.encode(value));

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

const _conversationVectors =
    <({String name, String secret, String publicKey, String expected})>[
  (
    name: 'random-01',
    secret: '315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268',
    publicKey:
        'c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133',
    expected:
        '3dfef0ce2a4d80a25e7a328accf73448ef67096f65f79588e358d9a0eb9013f1',
  ),
  (
    name: 'random-02',
    secret: 'a1e37752c9fdc1273be53f68c5f74be7c8905728e8de75800b94262f9497c86e',
    publicKey:
        '03bb7947065dde12ba991ea045132581d0954f042c84e06d8c00066e23c1a800',
    expected:
        '4d14f36e81b8452128da64fe6f1eae873baae2f444b02c950b90e43553f2178b',
  ),
  (
    name: 'random-03',
    secret: '98a5902fd67518a0c900f0fb62158f278f94a21d6f9d33d30cd3091195500311',
    publicKey:
        'aae65c15f98e5e677b5050de82e3aba47a6fe49b3dab7863cf35d9478ba9f7d1',
    expected:
        '9c00b769d5f54d02bf175b7284a1cbd28b6911b06cda6666b2243561ac96bad7',
  ),
  (
    name: 'random-04',
    secret: '86ae5ac8034eb2542ce23ec2f84375655dab7f836836bbd3c54cefe9fdc9c19f',
    publicKey:
        '59f90272378089d73f1339710c02e2be6db584e9cdbe86eed3578f0c67c23585',
    expected:
        '19f934aafd3324e8415299b64df42049afaa051c71c98d0aa10e1081f2e3e2ba',
  ),
  (
    name: 'secret-n-minus-two',
    secret: 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364139',
    publicKey:
        '0000000000000000000000000000000000000000000000000000000000000002',
    expected:
        '8b6392dbf2ec6a2b2d5b1477fc2be84d63ef254b667cadd31bd3f444c44ae6ba',
  ),
  (
    name: 'secret-two',
    secret: '0000000000000000000000000000000000000000000000000000000000000002',
    publicKey:
        '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdeb',
    expected:
        'be234f46f60a250bef52a5ee34c758800c4ca8e5030bf4cc1a31d37ba2104d43',
  ),
  (
    name: 'self-generator',
    secret: '0000000000000000000000000000000000000000000000000000000000000001',
    publicKey:
        '79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798',
    expected:
        '3b4610cb7189beb9cc29eb3716ecc6102f1247e8f3101a03a1787d8908aeb54e',
  ),
];

const _invalidConversationVectors =
    <({String name, String secret, String publicKey})>[
  (
    name: 'secret-all-ff',
    secret: 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
    publicKey:
        '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
  ),
  (
    name: 'secret-zero',
    secret: '0000000000000000000000000000000000000000000000000000000000000000',
    publicKey:
        '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
  ),
  (
    name: 'public-all-ff',
    secret: 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364139',
    publicKey:
        'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
  ),
  (
    name: 'secret-equals-order',
    secret: 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141',
    publicKey:
        '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
  ),
  (
    name: 'public-no-square-root',
    secret: '0000000000000000000000000000000000000000000000000000000000000002',
    publicKey:
        '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
  ),
  (
    name: 'twist-order-3',
    secret: '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20',
    publicKey:
        '0000000000000000000000000000000000000000000000000000000000000000',
  ),
  (
    name: 'twist-order-13',
    secret: '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20',
    publicKey:
        'eb1f7200aecaa86682376fb1c13cd12b732221e774f553b0a0857f88fa20f86d',
  ),
  (
    name: 'twist-order-3319',
    secret: '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20',
    publicKey:
        '709858a4c121e4a84eb59c0ded0261093c71e8ca29efeef21a6161c447bcaf9f',
  ),
];

const _messageKeyConversation =
    'a1a3d60f3470a8612633924e91febf96dc5366ce130f658b1f0fc652c20b3b54';
const _messageKeyVectors = <({
  String nonce,
  String chachaKey,
  String chachaNonce,
  String hmacKey,
})>[
  (
    nonce: 'e1e6f880560d6d149ed83dcc7e5861ee62a5ee051f7fde9975fe5d25d2a02d72',
    chachaKey:
        'f145f3bed47cb70dbeaac07f3a3fe683e822b3715edb7c4fe310829014ce7d76',
    chachaNonce: 'c4ad129bb01180c0933a160c',
    hmacKey: '027c1db445f05e2eee864a0975b0ddef5b7110583c8c192de3732571ca5838c4',
  ),
  (
    nonce: 'e1d6d28c46de60168b43d79dacc519698512ec35e8ccb12640fc8e9f26121101',
    chachaKey:
        'e35b88f8d4a8f1606c5082f7a64b100e5d85fcdb2e62aeafbec03fb9e860ad92',
    chachaNonce: '22925e920cee4a50a478be90',
    hmacKey: '46a7c55d4283cb0df1d5e29540be67abfe709e3b2e14b7bf9976e6df994ded30',
  ),
  (
    nonce: 'cfc13bef512ac9c15951ab00030dfaf2626fdca638dedb35f2993a9eeb85d650',
    chachaKey:
        '020783eb35fdf5b80ef8c75377f4e937efb26bcbad0e61b4190e39939860c4bf',
    chachaNonce: 'd3594987af769a52904656ac',
    hmacKey: '237ec0ccb6ebd53d179fa8fd319e092acff599ef174c1fdafd499ef2b8dee745',
  ),
  (
    nonce: 'ea6eb84cac23c5c1607c334e8bdf66f7977a7e374052327ec28c6906cbe25967',
    chachaKey:
        'ff68db24b34fa62c78ac5ffeeaf19533afaedf651fb6a08384e46787f6ce94be',
    chachaNonce: '50bb859aa2dde938cc49ec7a',
    hmacKey: '06ff32e1f7b29753a727d7927b25c2dd175aca47751462d37a2039023ec6b5a6',
  ),
  (
    nonce: '8c2e1dd3792802f1f9f7842e0323e5d52ad7472daf360f26e15f97290173605d',
    chachaKey:
        '2f9daeda8683fdeede81adac247c63cc7671fa817a1fd47352e95d9487989d8b',
    chachaNonce: '400224ba67fc2f1b76736916',
    hmacKey: '465c05302aeeb514e41c13ed6405297e261048cfb75a6f851ffa5b445b746e4b',
  ),
];

const _paddingVectors = <(int, int)>[
  (1, 32),
  (16, 32),
  (32, 32),
  (33, 64),
  (37, 64),
  (45, 64),
  (49, 64),
  (64, 64),
  (65, 96),
  (100, 128),
  (111, 128),
  (200, 224),
  (250, 256),
  (256, 256),
  (257, 320),
  (320, 320),
  (383, 384),
  (384, 384),
  (400, 448),
  (500, 512),
  (512, 512),
  (515, 640),
  (700, 768),
  (800, 896),
  (900, 1024),
  (1020, 1024),
  (65535, 65536),
  (65536, 65536),
];

const _encryptionVectors = <({
  String name,
  String secret1,
  String secret2,
  String conversationKey,
  String nonce,
  String plaintext,
  String payload,
})>[
  (
    name: 'ascii-one-byte',
    secret1: '0000000000000000000000000000000000000000000000000000000000000001',
    secret2: '0000000000000000000000000000000000000000000000000000000000000002',
    conversationKey:
        'c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d',
    nonce: '0000000000000000000000000000000000000000000000000000000000000001',
    plaintext: 'a',
    payload:
        'AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb',
  ),
  (
    name: 'emoji',
    secret1: '0000000000000000000000000000000000000000000000000000000000000002',
    secret2: '0000000000000000000000000000000000000000000000000000000000000001',
    conversationKey:
        'c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d',
    nonce: 'f00000000000000000000000000000f00000000000000000000000000000000f',
    plaintext: '🍕🫃',
    payload:
        'AvAAAAAAAAAAAAAAAAAAAPAAAAAAAAAAAAAAAAAAAAAPSKSK6is9ngkX2+cSq85Th16oRTISAOfhStnixqZziKMDvB0QQzgFZdjLTPicCJaV8nDITO+QfaQ61+KbWQIOO2Yj',
  ),
  (
    name: 'multilingual-long',
    secret1: '5c0c523f52a5b6fad39ed2403092df8cebc36318b39383bca6c00808626fab3a',
    secret2: '4b22aa260e4acb7021e32f38a6cdf4b673c6a277755bfce287e370c924dc936d',
    conversationKey:
        '3e2b52a63be47d34fe0a80e34e73d436d6963bc8f39827f327057a9986c20a45',
    nonce: 'b635236c42db20f021bb8d1cdff5ca75dd1a0cc72ea742ad750f33010b24f73b',
    plaintext: '表ポあA鷗ŒéＢ逍Üßªąñ丂㐀𠀀',
    payload:
        'ArY1I2xC2yDwIbuNHN/1ynXdGgzHLqdCrXUPMwELJPc7s7JqlCMJBAIIjfkpHReBPXeoMCyuClwgbT419jUWU1PwaNl4FEQYKCDKVJz+97Mp3K+Q2YGa77B6gpxB/lr1QgoqpDf7wDVrDmOqGoiPjWDqy8KzLueKDcm9BVP8xeTJIxs=',
  ),
  (
    name: 'mixed-script',
    secret1: '8f40e50a84a7462e2b8d24c28898ef1f23359fff50d8c509e6fb7ce06e142f9c',
    secret2: 'b9b0a1e9cc20100c5faa3bbe2777303d25950616c4c6a3fa2e3e046f936ec2ba',
    conversationKey:
        'd5a2f879123145a4b291d767428870f5a8d9e5007193321795b40183d4ab8c2b',
    nonce: 'b20989adc3ddc41cd2c435952c0d59a91315d8c5218d5040573fc3749543acaf',
    plaintext: 'ability🤝的 ȺȾ',
    payload:
        'ArIJia3D3cQc0sQ1lSwNWakTFdjFIY1QQFc/w3SVQ6yvbG2S0x4Yu86QGwPTy7mP3961I1XqB6SFFTzqDZZavhxoWMj7mEVGMQIsh2RLWI5EYQaQDIePSnXPlzf7CIt+voTD',
  ),
  (
    name: 'cyrillic-emoji',
    secret1: '875adb475056aec0b4809bd2db9aa00cff53a649e7b59d8edcbf4e6330b0995c',
    secret2: '9c05781112d5b0a2a7148a222e50e0bd891d6b60c5483f03456e982185944aae',
    conversationKey:
        '3b15c977e20bfe4b8482991274635edd94f366595b1a3d2993515705ca3cedb8',
    nonce: '8d4442713eb9d4791175cb040d98d6fc5be8864d6ec2f89cf0895a2b2b72d1b1',
    plaintext: 'pepper👀їжак',
    payload:
        'Ao1EQnE+udR5EXXLBA2Y1vxb6IZNbsL4nPCJWisrctGxY3AduCS+jTUgAAnfvKafkmpy15+i9YMwCdccisRa8SvzW671T2JO4LFSPX31K4kYUKelSAdSPwe9NwO6LhOsnoJ+',
  ),
];

const _longVectors = <({
  String name,
  String conversationKey,
  String nonce,
  String pattern,
  int repeat,
  String plaintextHash,
  String payloadHash,
})>[
  (
    name: 'maximum-ascii-x',
    conversationKey:
        '8fc262099ce0d0bb9b89bac05bb9e04f9bc0090acc181fef6840ccee470371ed',
    nonce: '326bcb2c943cd6bb717588c9e5a7e738edf6ed14ec5f5344caa6ef56f0b9cff7',
    pattern: 'x',
    repeat: 65535,
    plaintextHash:
        '09ab7495d3e61a76f0deb12cb0306f0696cbb17ffc12131368c7a939f12f56d3',
    payloadHash:
        '90714492225faba06310bff2f249ebdc2a5e609d65a629f1c87f2d4ffc55330a',
  ),
  (
    name: 'maximum-ascii-bang',
    conversationKey:
        '56adbe3720339363ab9c3b8526ffce9fd77600927488bfc4b59f7a68ffe5eae0',
    nonce: 'ad68da81833c2a8ff609c3d2c0335fd44fe5954f85bb580c6a8d467aa9fc5dd0',
    pattern: '!',
    repeat: 65535,
    plaintextHash:
        '6af297793b72ae092c422e552c3bb3cbc310da274bd1cf9e31023a7fe4a2d75e',
    payloadHash:
        '8013e45a109fad3362133132b460a2d5bce235fe71c8b8f4014793fb52a49844',
  ),
  (
    name: 'near-maximum-unicode',
    conversationKey:
        '7fc540779979e472bb8d12480b443d1e5eb1098eae546ef2390bee499bbf46be',
    nonce: '34905e82105c20de9a2f6cd385a0d541e6bcc10601d12481ff3a7575dc622033',
    pattern: '🦄',
    repeat: 16383,
    plaintextHash:
        'a249558d161b77297bc0cb311dde7d77190f6571b25c7e4429cd19044634a61f',
    payloadHash:
        'b3348422471da1f3c59d79acfe2fe103f3cd24488109e5b18734cdb5953afd15',
  ),
];

const _invalidDecryptVectors = <({String name, String key, String payload})>[
  (
    name: 'unknown-version-marker',
    key: 'ca2527a037347b91bea0c8a30fc8d9600ffd81ec00038671e3a0f0cb0fc9f642',
    payload:
        '#Atqupco0WyaOW2IGDKcshwxI9xO8HgD/P8Ddt46CbxDbrhdG8VmJdU0MIDf06CUvEvdnr1cp1fiMtlM/GrE92xAc1K5odTpCzUB+mjXgbaqtntBUbTToSUoT0ovrlPwzGjyp',
  ),
  (
    name: 'version-zero',
    key: '36f04e558af246352dcf73b692fbd3646a2207bd8abd4b1cd26b234db84d9481',
    payload:
        'AK1AjUvoYW3IS7C/BGRUoqEC7ayTfDUgnEPNeWTF/reBZFaha6EAIRueE9D1B1RuoiuFScC0Q94yjIuxZD3JStQtE8JMNacWFs9rlYP+ZydtHhRucp+lxfdvFlaGV/sQlqZz',
  ),
  (
    name: 'non-base64-unicode',
    key: 'ca2527a037347b91bea0c8a30fc8d9600ffd81ec00038671e3a0f0cb0fc9f642',
    payload:
        'Atфupco0WyaOW2IGDKcshwxI9xO8HgD/P8Ddt46CbxDbrhdG8VmJZE0UICD06CUvEvdnr1cp1fiMtlM/GrE92xAc1EwsVCQEgWEu2gsHUVf4JAa3TpgkmFc3TWsax0v6n/Wq',
  ),
  (
    name: 'invalid-mac-zeroed',
    key: 'cff7bd6a3e29a450fd27f6c125d5edeb0987c475fd1e8d97591e0d4d8a89763c',
    payload:
        'Agn/l3ULCEAS4V7LhGFM6IGA17jsDUaFCKhrbXDANholyySBfeh+EN8wNB9gaLlg4j6wdBYh+3oK+mnxWu3NKRbSvQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
  ),
  (
    name: 'invalid-mac',
    key: 'cfcc9cf682dfb00b11357f65bdc45e29156b69db424d20b3596919074f5bf957',
    payload:
        'AmWxSwuUmqp9UsQX63U7OQ6K1thLI69L7G2b+j4DoIr0oRWQ8avl4OLqWZiTJ10vIgKrNqjoaX+fNhE9RqmR5g0f6BtUg1ijFMz71MO1D4lQLQfW7+UHva8PGYgQ1QpHlKgR',
  ),
  (
    name: 'invalid-padding-01',
    key: '5254827d29177622d40a7b67cad014fe7137700c3c523903ebbe3e1b74d40214',
    payload:
        'Anq2XbuLvCuONcr7V0UxTh8FAyWoZNEdBHXvdbNmDZHB573MI7R7rrTYftpqmvUpahmBC2sngmI14/L0HjOZ7lWGJlzdh6luiOnGPc46cGxf08MRC4CIuxx3i2Lm0KqgJ7vA',
  ),
  (
    name: 'invalid-padding-02',
    key: 'fea39aca9aa8340c3a78ae1f0902aa7e726946e4efcd7783379df8096029c496',
    payload:
        'An1Cg+O1TIhdav7ogfSOYvCj9dep4ctxzKtZSniCw5MwRrrPJFyAQYZh5VpjC2QYzny5LIQ9v9lhqmZR4WBYRNJ0ognHVNMwiFV1SHpvUFT8HHZN/m/QarflbvDHAtO6pY16',
  ),
  (
    name: 'invalid-padding-03',
    key: '0c4cffb7a6f7e706ec94b2e879f1fc54ff8de38d8db87e11787694d5392d5b3f',
    payload:
        'Am+f1yZnwnOs0jymZTcRpwhDRHTdnrFcPtsBzpqVdD6b2NZDaNm/TPkZGr75kbB6tCSoq7YRcbPiNfJXNch3Tf+o9+zZTMxwjgX/nm3yDKR2kHQMBhVleCB9uPuljl40AJ8kXRD0gjw+aYRJFUMK9gCETZAjjmrsCM+nGRZ1FfNsHr6Z',
  ),
  (
    name: 'empty-payload',
    key: '5cd2d13b9e355aeb2452afbd3786870dbeecb9d355b12cb0a3b6e9da5744cd35',
    payload: '',
  ),
  (
    name: 'four-base64-bytes',
    key: 'd61d3f09c7dfe1c0be91af7109b60a7d9d498920c90cbba1e137320fdd938853',
    payload: 'Ag==',
  ),
  (
    name: 'forty-eight-base64-bytes',
    key: '873bb0fc665eb950a8e7d5971965539f6ebd645c83c08cd6a85aafbad0f0bc47',
    payload: 'AqxgToSh3H7iLYRJjoWAM+vSv/Y1mgNlm6OWWjOYUClrFF8=',
  ),
  (
    name: 'ninety-two-base64-bytes',
    key: '9f2fef8f5401ac33f74641b568a7a30bb19409c76ffdc5eae2db6b39d2617fbe',
    payload:
        'Ap/2SEZCVFIhYk6qx7nqJxM6TMI1ZoKmAzrO7vBDVJhhuZXWiM20i/tIsbjT0KxkJs2MZjh1oXNYMO9ggfk7i47WQA==',
  ),
];

String buildNip44Fixture() {
  final lines = <String>[
    '# Roadstr NIP-44 v2 legacy-u16 profile fixture v1.',
    '# Curated from paulmillr/nip44 nip44.vectors.json and replayed through production Dart.',
  ];

  for (final vector in _conversationVectors) {
    final actual = _hex(Nip44.conversationKey(vector.secret, vector.publicKey));
    _expectEqual('conversation/${vector.name}', actual, vector.expected);
    lines.add([
      'conversation',
      vector.name,
      vector.secret,
      vector.publicKey,
      vector.expected,
    ].join('\t'));
  }

  for (final vector in _invalidConversationVectors) {
    _expectReject(
      'conversation_reject/${vector.name}',
      () => Nip44.conversationKey(vector.secret, vector.publicKey),
    );
    lines.add([
      'conversation_reject',
      vector.name,
      vector.secret,
      vector.publicKey,
    ].join('\t'));
  }

  for (var index = 0; index < _messageKeyVectors.length; index++) {
    final vector = _messageKeyVectors[index];
    final keys = Nip44.messageKeys(
      _hexBytes(_messageKeyConversation),
      _hexBytes(vector.nonce),
    );
    _expectEqual(
        'message_keys/$index/chacha', _hex(keys.chachaKey), vector.chachaKey);
    _expectEqual('message_keys/$index/nonce', _hex(keys.chachaNonce),
        vector.chachaNonce);
    _expectEqual(
        'message_keys/$index/hmac', _hex(keys.hmacKey), vector.hmacKey);
    lines.add([
      'message_keys',
      'official-${index + 1}',
      _messageKeyConversation,
      vector.nonce,
      vector.chachaKey,
      vector.chachaNonce,
      vector.hmacKey,
    ].join('\t'));
  }

  for (final (input, expected) in _paddingVectors) {
    _expectEqual('padded_len/$input', Nip44.paddedLength(input), expected);
    lines.add(['padded_len', 'bytes-$input', '$input', '$expected'].join('\t'));
  }

  for (final vector in _encryptionVectors) {
    final publicKey2 = KeyApi().getPublicKey(vector.secret2);
    final conversation = Nip44.conversationKey(vector.secret1, publicKey2);
    _expectEqual(
      'encrypt/${vector.name}/conversation',
      _hex(conversation),
      vector.conversationKey,
    );
    final payload = Nip44.encryptWithNonce(
      vector.secret1,
      publicKey2,
      vector.plaintext,
      _hexBytes(vector.nonce),
    );
    _expectEqual('encrypt/${vector.name}/payload', payload, vector.payload);
    _expectEqual(
      'encrypt/${vector.name}/decrypt',
      Nip44.decryptWithConversationKey(conversation, payload),
      vector.plaintext,
    );
    lines.add([
      'encrypt',
      vector.name,
      vector.secret1,
      publicKey2,
      vector.nonce,
      _text64(vector.plaintext),
      vector.payload,
    ].join('\t'));
  }

  for (final variant in <({String name, int vectorIndex})>[
    (name: 'url-safe-emoji', vectorIndex: 1),
    (name: 'url-safe-multilingual', vectorIndex: 2),
  ]) {
    final source = _encryptionVectors[variant.vectorIndex];
    final payload = source.payload.replaceAll('+', '-').replaceAll('/', '_');
    _expectEqual(
      'decrypt/${variant.name}',
      Nip44.decryptWithConversationKey(
        _hexBytes(source.conversationKey),
        payload,
      ),
      source.plaintext,
    );
    lines.add([
      'decrypt',
      variant.name,
      source.conversationKey,
      payload,
      _text64(source.plaintext),
    ].join('\t'));
  }

  final paddedSource = _encryptionVectors[2];
  final unpaddedPayload = paddedSource.payload.replaceFirst(RegExp(r'=+$'), '');
  _expectReject(
    'decrypt_reject/missing-base64-padding',
    () => Nip44.decryptWithConversationKey(
      _hexBytes(paddedSource.conversationKey),
      unpaddedPayload,
    ),
  );
  lines.add([
    'decrypt_reject',
    'missing-base64-padding',
    paddedSource.conversationKey,
    unpaddedPayload,
  ].join('\t'));

  for (final vector in _longVectors) {
    final plaintext = vector.pattern * vector.repeat;
    final plaintextHash = sha256.convert(utf8.encode(plaintext)).toString();
    _expectEqual(
        'long/${vector.name}/plaintext', plaintextHash, vector.plaintextHash);
    final payload = Nip44.encryptWithConversationKey(
      _hexBytes(vector.conversationKey),
      _hexBytes(vector.nonce),
      plaintext,
    );
    final payloadHash = sha256.convert(utf8.encode(payload)).toString();
    _expectEqual(
        'long/${vector.name}/payload', payloadHash, vector.payloadHash);
    _expectEqual(
      'long/${vector.name}/decrypt',
      Nip44.decryptWithConversationKey(
          _hexBytes(vector.conversationKey), payload),
      plaintext,
    );
    lines.add([
      'long',
      vector.name,
      vector.conversationKey,
      vector.nonce,
      _text64(vector.pattern),
      '${vector.repeat}',
      vector.plaintextHash,
      vector.payloadHash,
    ].join('\t'));
  }

  for (final vector in _invalidDecryptVectors) {
    _expectReject(
      'decrypt_reject/${vector.name}',
      () => Nip44.decryptWithConversationKey(
          _hexBytes(vector.key), vector.payload),
    );
    lines.add([
      'decrypt_reject',
      vector.name,
      vector.key,
      vector.payload.isEmpty ? '-' : vector.payload,
    ].join('\t'));
  }

  final fixedKey = _hexBytes(_messageKeyConversation);
  final fixedNonce = _hexBytes(_messageKeyVectors.first.nonce);
  for (final (name, plaintextLength) in <(String, int)>[
    ('empty', 0),
    ('u16-overflow', 65536),
  ]) {
    _expectReject(
      'encrypt_reject/$name',
      () => Nip44.encryptWithConversationKey(
        fixedKey,
        fixedNonce,
        'x' * plaintextLength,
      ),
    );
    lines.add([
      'encrypt_reject',
      name,
      _messageKeyConversation,
      _messageKeyVectors.first.nonce,
      '$plaintextLength',
    ].join('\t'));
  }

  for (final vector in <({String name, String key, String nonce})>[
    (
      name: 'short-conversation-key',
      key: '00' * 31,
      nonce: '00' * 32,
    ),
    (
      name: 'long-conversation-key',
      key: '00' * 33,
      nonce: '00' * 32,
    ),
    (
      name: 'short-nonce',
      key: '00' * 32,
      nonce: '00' * 31,
    ),
    (
      name: 'long-nonce',
      key: '00' * 32,
      nonce: '00' * 33,
    ),
  ]) {
    _expectReject(
      'message_keys_reject/${vector.name}',
      () => Nip44.messageKeys(_hexBytes(vector.key), _hexBytes(vector.nonce)),
    );
    lines.add([
      'message_keys_reject',
      vector.name,
      vector.key,
      vector.nonce,
    ].join('\t'));
  }

  return '${lines.join('\n')}\n';
}

void main(List<String> arguments) {
  final generated = buildNip44Fixture();
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
