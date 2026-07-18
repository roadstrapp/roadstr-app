import 'dart:convert';

import 'package:bech32/bech32.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/bolt11_invoice.dart';

void main() {
  const description = '{"kind":9734,"content":"road zap"}';
  final preimage = List<int>.generate(32, (i) => i);
  final preimageHex =
      preimage.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();
  final createdAt = DateTime.now().millisecondsSinceEpoch ~/ 1000;

  test('decodes and binds amount, expiry, preimage and description', () {
    final raw = _invoice(
      createdAt: createdAt,
      preimage: preimage,
      description: description,
    );
    final invoice = Bolt11Invoice.tryParse(raw);

    expect(invoice, isNotNull);
    expect(invoice!.amountMsat, 1000);
    expect(invoice.createdAt, createdAt);
    expect(invoice.expirySeconds, 600);
    expect(invoice.preimageMatches(preimageHex), isTrue);
    expect(invoice.preimageMatches(List.filled(32, '00').join()), isFalse);
    expect(invoice.descriptionMatches(description), isTrue);
    expect(invoice.descriptionMatches('$description '), isFalse);
    expect(invoice.isExpiredAt(createdAt + 599), isFalse);
    expect(invoice.isExpiredAt(createdAt + 600), isTrue);
  });

  test('rejects a modified checksum', () {
    final raw = _invoice(
      createdAt: createdAt,
      preimage: preimage,
      description: description,
    );
    final replacement = raw.endsWith('q') ? 'p' : 'q';
    expect(
        Bolt11Invoice.tryParse(
            '${raw.substring(0, raw.length - 1)}$replacement'),
        isNull);
  });

  test('rejects testnet and amountless invoices for automatic payment', () {
    expect(
      Bolt11Invoice.tryParse(_invoice(
        hrp: 'lntb10n',
        createdAt: createdAt,
        preimage: preimage,
        description: description,
      )),
      isNull,
    );
    expect(
      Bolt11Invoice.tryParse(_invoice(
        hrp: 'lnbc',
        createdAt: createdAt,
        preimage: preimage,
        description: description,
      )),
      isNull,
    );
  });

  test('rejects invoices without a payment hash', () {
    expect(
      Bolt11Invoice.tryParse(_invoice(
        createdAt: createdAt,
        preimage: preimage,
        description: description,
        includePaymentHash: false,
      )),
      isNull,
    );
  });
}

String _invoice({
  String hrp = 'lnbc10n',
  required int createdAt,
  required List<int> preimage,
  required String description,
  bool includePaymentHash = true,
}) {
  final words = <int>[
    for (var shift = 30; shift >= 0; shift -= 5) (createdAt >> shift) & 31,
  ];
  if (includePaymentHash) {
    words.addAll(_tag(1, _toFiveBits(sha256.convert(preimage).bytes)));
  }
  words.addAll(
    _tag(23, _toFiveBits(sha256.convert(utf8.encode(description)).bytes)),
  );
  words.addAll(_tag(6, [18, 24])); // 18*32 + 24 = 600 seconds.
  words.addAll(List<int>.filled(104, 0)); // Compact signature payload.
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
