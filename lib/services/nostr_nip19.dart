import 'package:bech32/bech32.dart';

enum NostrNip19Kind {
  publicKey('npub'),
  privateKey('nsec');

  const NostrNip19Kind(this.hrp);

  final String hrp;
}

class NostrNip19Key {
  const NostrNip19Key({required this.kind, required this.hex});

  final NostrNip19Kind kind;
  final String hex;
}

/// Strict NIP-19 boundary for the two fixed-size key formats Roadstr uses.
///
/// Exceptions deliberately omit the source value: an invalid `nsec` is still
/// secret material and must not leak through logs or crash reports.
abstract final class NostrNip19 {
  static const _keyBytes = 32;
  static const _maxBasicKeyLength = 90;
  static final _hexKey = RegExp(r'^[0-9a-fA-F]{64}$');

  static String encodePublicKey(String hex) =>
      _encode(NostrNip19Kind.publicKey, hex);

  static String encodePrivateKey(String hex) =>
      _encode(NostrNip19Kind.privateKey, hex);

  static NostrNip19Key decode(String encoded) {
    try {
      final decoded = const Bech32Codec().decode(
        encoded,
        _maxBasicKeyLength,
      );
      final kind = switch (decoded.hrp) {
        'npub' => NostrNip19Kind.publicKey,
        'nsec' => NostrNip19Kind.privateKey,
        _ => throw const FormatException('Unsupported NIP-19 key type'),
      };
      final bytes = _convertBits(decoded.data, 5, 8, pad: false);
      if (bytes.length != _keyBytes) {
        throw const FormatException('Invalid NIP-19 key length');
      }
      return NostrNip19Key(kind: kind, hex: _toHex(bytes));
    } catch (_) {
      throw const FormatException('Invalid NIP-19 key');
    }
  }

  static String decodePublicKey(String encoded) {
    final decoded = decode(encoded);
    if (decoded.kind != NostrNip19Kind.publicKey) {
      throw const FormatException('Expected npub');
    }
    return decoded.hex;
  }

  static String decodePrivateKey(String encoded) {
    final decoded = decode(encoded);
    if (decoded.kind != NostrNip19Kind.privateKey) {
      throw const FormatException('Expected nsec');
    }
    return decoded.hex;
  }

  static String _encode(NostrNip19Kind kind, String hex) {
    if (!_hexKey.hasMatch(hex)) {
      throw const FormatException('Invalid hexadecimal Nostr key');
    }
    final bytes = <int>[
      for (var offset = 0; offset < hex.length; offset += 2)
        int.parse(hex.substring(offset, offset + 2), radix: 16),
    ];
    final words = _convertBits(bytes, 8, 5, pad: true);
    return const Bech32Codec().encode(
      Bech32(kind.hrp, words),
      _maxBasicKeyLength,
    );
  }

  static List<int> _convertBits(
    List<int> data,
    int fromBits,
    int toBits, {
    required bool pad,
  }) {
    var accumulator = 0;
    var bitCount = 0;
    final output = <int>[];
    final outputMask = (1 << toBits) - 1;
    final maxAccumulator = (1 << (fromBits + toBits - 1)) - 1;
    for (final value in data) {
      if (value < 0 || value >= (1 << fromBits)) {
        throw const FormatException('Invalid NIP-19 word');
      }
      accumulator = ((accumulator << fromBits) | value) & maxAccumulator;
      bitCount += fromBits;
      while (bitCount >= toBits) {
        bitCount -= toBits;
        output.add((accumulator >> bitCount) & outputMask);
      }
    }
    if (pad) {
      if (bitCount > 0) {
        output.add((accumulator << (toBits - bitCount)) & outputMask);
      }
    } else if (bitCount >= fromBits ||
        ((accumulator << (toBits - bitCount)) & outputMask) != 0) {
      throw const FormatException('Invalid NIP-19 padding');
    }
    return output;
  }

  static String _toHex(Iterable<int> bytes) =>
      bytes.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();
}
