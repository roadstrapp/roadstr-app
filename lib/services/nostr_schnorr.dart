import 'dart:math';

import 'package:nostr_tools/nostr_tools.dart' show Bip340Util;

/// Strict Nostr profile around the BIP-340 implementation already shipped by
/// `nostr_tools`.
///
/// NIP-01 signs a 32-byte event id, not an arbitrary message. Keeping that
/// boundary explicit prevents callers from accidentally signing unhashed or
/// ambiguously encoded data while the native rewrite is validated.
abstract final class NostrSchnorr {
  static final Random _secureRandom = Random.secure();
  static final BigInt _curveOrder = BigInt.parse(
    'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141',
    radix: 16,
  );
  static final RegExp _hex = RegExp(r'^[0-9a-fA-F]+$');

  /// Returns the 32-byte x-only public key for a valid secp256k1 scalar.
  static String publicKey(String privateKeyHex) {
    _requirePrivateKey(privateKeyHex);
    final publicKey = Bip340Util.getPublicKey(privateKeyHex).toLowerCase();
    if (!_isHexBytes(publicKey, 32)) {
      throw StateError('BIP-340 implementation returned an invalid public key');
    }
    return publicKey;
  }

  /// Signs a NIP-01 event id using fresh 32-byte auxiliary randomness.
  static String signHash(String privateKeyHex, String eventIdHex) {
    final auxiliaryRandom = List<int>.generate(
      32,
      (_) => _secureRandom.nextInt(256),
      growable: false,
    );
    return signHashWithAux(
      privateKeyHex,
      eventIdHex,
      _encodeHex(auxiliaryRandom),
    );
  }

  /// Deterministic BIP-340 signing entry point for shared test vectors.
  ///
  /// Production callers should use [signHash], never a fixed auxiliary value.
  static String signHashWithAux(
    String privateKeyHex,
    String eventIdHex,
    String auxiliaryRandomHex,
  ) {
    _requirePrivateKey(privateKeyHex);
    _requireHexBytes('event id', eventIdHex, 32);
    _requireHexBytes('auxiliary randomness', auxiliaryRandomHex, 32);
    final signature = Bip340Util.sign(
      privateKeyHex,
      eventIdHex,
      auxiliaryRandomHex,
    ).toLowerCase();
    if (!_isHexBytes(signature, 64)) {
      throw StateError('BIP-340 implementation returned an invalid signature');
    }
    return signature;
  }

  /// Verifies a BIP-340 signature over a NIP-01 event id.
  ///
  /// Untrusted malformed input is rejected as `false` and never escapes as an
  /// exception to relay consumers.
  static bool verifyHash(
    String publicKeyHex,
    String eventIdHex,
    String signatureHex,
  ) {
    if (!_isHexBytes(publicKeyHex, 32) ||
        !_isHexBytes(eventIdHex, 32) ||
        !_isHexBytes(signatureHex, 64)) {
      return false;
    }
    try {
      return Bip340Util.verify(publicKeyHex, eventIdHex, signatureHex);
    } catch (_) {
      return false;
    }
  }

  static void _requirePrivateKey(String value) {
    _requireHexBytes('private key', value, 32);
    final scalar = BigInt.parse(value, radix: 16);
    if (scalar < BigInt.one || scalar >= _curveOrder) {
      throw ArgumentError('private key must be a valid secp256k1 scalar');
    }
  }

  static void _requireHexBytes(String name, String value, int bytes) {
    if (!_isHexBytes(value, bytes)) {
      throw ArgumentError(
          '$name must contain exactly ${bytes * 2} hex characters');
    }
  }

  static bool _isHexBytes(String value, int bytes) =>
      value.length == bytes * 2 && _hex.hasMatch(value);

  static String _encodeHex(List<int> bytes) =>
      bytes.map((value) => value.toRadixString(16).padLeft(2, '0')).join();
}
