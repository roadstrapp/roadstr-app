import 'dart:convert';
import 'dart:typed_data';

import 'package:bech32/bech32.dart';
import 'package:crypto/crypto.dart' as crypto;

/// Minimal, strict BOLT-11 decoder for the fields Roadstr must verify before
/// handing an invoice to an automatic NWC wallet.
///
/// The Bech32 codec verifies the invoice checksum. This class additionally
/// extracts the exact millisatoshi amount, creation time, expiry and payment
/// hash. It deliberately rejects amountless and non-mainnet invoices: LNURL-pay
/// always requested a concrete mainnet amount, so accepting either would turn a
/// protocol mismatch into a potentially unsafe automatic payment.
class Bolt11Invoice {
  final int amountMsat;
  final int createdAt;
  final int expirySeconds;
  final Uint8List paymentHash;
  final Uint8List? descriptionHash;

  const Bolt11Invoice({
    required this.amountMsat,
    required this.createdAt,
    required this.expirySeconds,
    required this.paymentHash,
    required this.descriptionHash,
  });

  bool isExpiredAt(int unixSeconds) => unixSeconds >= createdAt + expirySeconds;

  bool preimageMatches(String preimageHex) {
    final preimage = _hexBytes(preimageHex);
    if (preimage == null || preimage.length != 32) return false;
    final actual = Uint8List.fromList(crypto.sha256.convert(preimage).bytes);
    return _constantTimeEquals(actual, paymentHash);
  }

  /// BOLT-11 binds an invoice to LNURL metadata (or to a NIP-57 zap request)
  /// through the tagged `h` field. Without this check a compromised callback
  /// could swap in an invoice with the right amount but unrelated semantics.
  bool descriptionMatches(String description) {
    final expected = descriptionHash;
    if (expected == null) return false;
    final actual = crypto.sha256.convert(utf8.encode(description)).bytes;
    return _constantTimeEquals(actual, expected);
  }

  static Bolt11Invoice? tryParse(String raw, {bool requireMainnet = true}) {
    try {
      final invoice = raw.trim().toLowerCase();
      if (invoice.length < 20 || invoice.length > 8192) return null;
      final decoded = const Bech32Codec().decode(invoice, invoice.length);
      final hrp = decoded.hrp.toLowerCase();
      if (!hrp.startsWith('ln')) return null;

      final networkPrefix = _networkPrefix(hrp);
      if (networkPrefix == null ||
          (requireMainnet && networkPrefix != 'lnbc')) {
        return null;
      }
      final amount = _parseAmountMsat(hrp.substring(networkPrefix.length));
      if (amount == null || amount <= 0) return null;

      // 7 timestamp words + at least one tagged field + 104-word signature.
      final words = decoded.data;
      if (words.length < 7 + 3 + 104) return null;
      var createdAt = 0;
      for (var i = 0; i < 7; i++) {
        createdAt = (createdAt << 5) | words[i];
      }

      Uint8List? paymentHash;
      Uint8List? descriptionHash;
      var expiry = 3600; // BOLT-11 default.
      var cursor = 7;
      final taggedEnd = words.length - 104;
      while (cursor + 3 <= taggedEnd) {
        final type = words[cursor++];
        final length = (words[cursor++] << 5) | words[cursor++];
        if (length < 0 || cursor + length > taggedEnd) return null;
        final field = words.sublist(cursor, cursor + length);
        cursor += length;

        // Bech32 charset index 1 is `p` (payment_hash), index 6 is `x`
        // (expiry). Duplicate critical fields are rejected as ambiguous.
        if (type == 1) {
          if (paymentHash != null) return null;
          final bytes = _convertBits(field, 5, 8, pad: false);
          if (bytes == null || bytes.length != 32) return null;
          paymentHash = Uint8List.fromList(bytes);
        } else if (type == 23) {
          if (descriptionHash != null) return null;
          final bytes = _convertBits(field, 5, 8, pad: false);
          if (bytes == null || bytes.length != 32) return null;
          descriptionHash = Uint8List.fromList(bytes);
        } else if (type == 6) {
          var value = 0;
          for (final word in field) {
            if (value > 0x7fffffff >> 5) return null;
            value = (value << 5) | word;
          }
          expiry = value;
        }
      }
      if (cursor != taggedEnd || paymentHash == null || expiry <= 0) {
        return null;
      }
      return Bolt11Invoice(
        amountMsat: amount,
        createdAt: createdAt,
        expirySeconds: expiry,
        paymentHash: paymentHash,
        descriptionHash: descriptionHash,
      );
    } catch (_) {
      return null;
    }
  }

  /// Converts Bech32 five-bit words to bytes. Exposed for decoding `lud06`
  /// LNURL values without maintaining a second subtly different converter.
  static List<int>? convertFiveBitWords(List<int> words) =>
      _convertBits(words, 5, 8, pad: false);

  static String? _networkPrefix(String hrp) {
    // Longest first: lnbcrt also starts with lnbc.
    for (final prefix in const ['lnbcrt', 'lntbs', 'lntb', 'lnsb', 'lnbc']) {
      if (hrp.startsWith(prefix)) return prefix;
    }
    return null;
  }

  static int? _parseAmountMsat(String raw) {
    if (raw.isEmpty) {
      return null; // amountless invoices are unsafe for LNURL-pay.
    }
    final match = RegExp(r'^(\d+)([munp]?)$').firstMatch(raw);
    if (match == null) return null;
    final n = BigInt.tryParse(match.group(1)!);
    if (n == null || n <= BigInt.zero) return null;
    final suffix = match.group(2)!;
    BigInt msat;
    switch (suffix) {
      case 'm':
        msat = n * BigInt.from(100000000);
      case 'u':
        msat = n * BigInt.from(100000);
      case 'n':
        msat = n * BigInt.from(100);
      case 'p':
        // One pico-BTC is 0.1 msat, so only multiples of 10 are payable.
        if (n % BigInt.from(10) != BigInt.zero) return null;
        msat = n ~/ BigInt.from(10);
      default:
        msat = n * BigInt.from(100000000000);
    }
    if (msat > BigInt.from(0x7fffffffffffffff)) return null;
    return msat.toInt();
  }

  static List<int>? _convertBits(List<int> input, int fromBits, int toBits,
      {required bool pad}) {
    var accumulator = 0;
    var bitCount = 0;
    final result = <int>[];
    final maxValue = (1 << toBits) - 1;
    final maxAccumulator = (1 << (fromBits + toBits - 1)) - 1;
    for (final value in input) {
      if (value < 0 || value >> fromBits != 0) return null;
      accumulator = ((accumulator << fromBits) | value) & maxAccumulator;
      bitCount += fromBits;
      while (bitCount >= toBits) {
        bitCount -= toBits;
        result.add((accumulator >> bitCount) & maxValue);
      }
    }
    if (pad) {
      if (bitCount > 0) {
        result.add((accumulator << (toBits - bitCount)) & maxValue);
      }
    } else if (bitCount >= fromBits ||
        ((accumulator << (toBits - bitCount)) & maxValue) != 0) {
      return null;
    }
    return result;
  }

  static Uint8List? _hexBytes(String hex) {
    final clean = hex.trim().toLowerCase();
    if (!RegExp(r'^[0-9a-f]+$').hasMatch(clean) || clean.length.isOdd) {
      return null;
    }
    final out = Uint8List(clean.length ~/ 2);
    for (var i = 0; i < out.length; i++) {
      out[i] = int.parse(clean.substring(i * 2, i * 2 + 2), radix: 16);
    }
    return out;
  }

  static bool _constantTimeEquals(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    var diff = 0;
    for (var i = 0; i < a.length; i++) {
      diff |= a[i] ^ b[i];
    }
    return diff == 0;
  }
}
