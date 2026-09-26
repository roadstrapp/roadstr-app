// NIP-04 encryption retained only for legacy Nostr Wallet Connect peers.
// Spec: https://github.com/nostr-protocol/nips/blob/master/04.md
//
// NIP-04 is AES-256-CBC without authentication. New NWC peers should use
// NIP-44 v2, but an absent NIP-47 `encryption` tag still means NIP-04. This
// boundary therefore preserves the shipped wire format and Dart Base64
// compatibility while rejecting malformed and oversized envelopes early.
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:kepler/kepler.dart';
import 'package:pointycastle/export.dart';

class Nip04DecryptException implements Exception {
  final String message;
  const Nip04DecryptException(this.message);

  @override
  String toString() => 'Nip04DecryptException: $message';
}

abstract final class Nip04Cipher {
  static const maxPlaintextBytes = 65535;
  static const _blockSize = 16;
  static const _keySize = 32;
  static const _ivSize = 16;
  static const _maxCiphertextBytes = 65536;
  // Canonical maximum (87,412) plus four `%3D` padding escapes (+8 chars).
  static const maxPayloadCharacters = 87420;
  static final _secretOrder = BigInt.parse(
    'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141',
    radix: 16,
  );

  /// Encrypts to the NIP-04 `<base64 ciphertext>?iv=<base64 IV>` format.
  static String encrypt(
    String privateKeyHex,
    String publicKeyHex,
    String plaintext,
  ) =>
      encryptWithIv(
        privateKeyHex,
        publicKeyHex,
        plaintext,
        _randomBytes(_ivSize),
      );

  /// Deterministic encryption boundary used only by parity fixtures.
  static String encryptWithIv(
    String privateKeyHex,
    String publicKeyHex,
    String plaintext,
    Uint8List iv,
  ) =>
      encryptWithSharedSecret(
        sharedSecret(privateKeyHex, publicKeyHex),
        plaintext,
        iv,
      );

  static String encryptWithSharedSecret(
    Uint8List secret,
    String plaintext,
    Uint8List iv,
  ) {
    _requireLength('shared secret', secret, _keySize);
    _requireLength('IV', iv, _ivSize);
    final plainBytes = Uint8List.fromList(utf8.encode(plaintext));
    if (plainBytes.length > maxPlaintextBytes) {
      throw ArgumentError(
        'NIP-04 plaintext must be 0..$maxPlaintextBytes bytes, '
        'got ${plainBytes.length}',
      );
    }
    final ciphertext = _aesCbcEncrypt(secret, iv, plainBytes);
    return '${base64.encode(ciphertext)}?iv=${base64.encode(iv)}';
  }

  static String decrypt(
    String privateKeyHex,
    String publicKeyHex,
    String payload,
  ) {
    // Parse hostile relay input before doing any elliptic-curve work.
    final envelope = _decodeEnvelope(payload);
    return _decryptEnvelope(
      sharedSecret(privateKeyHex, publicKeyHex),
      envelope,
    );
  }

  static String decryptWithSharedSecret(Uint8List secret, String payload) {
    _requireLength('shared secret', secret, _keySize);
    return _decryptEnvelope(secret, _decodeEnvelope(payload));
  }

  /// Returns the unhashed 32-byte X coordinate required by NIP-04.
  static Uint8List sharedSecret(String privateKeyHex, String publicKeyHex) {
    _requireHex32('private key', privateKeyHex);
    _requireHex32('public key', publicKeyHex);
    final privateScalar = BigInt.parse(privateKeyHex, radix: 16);
    if (privateScalar <= BigInt.zero || privateScalar >= _secretOrder) {
      throw ArgumentError('private key must be a valid secp256k1 scalar');
    }
    try {
      final shared = Kepler.byteSecret(privateKeyHex, '02$publicKeyHex')[0];
      if (shared.length != _keySize) {
        throw ArgumentError('ECDH x-coordinate must be $_keySize bytes');
      }
      return Uint8List.fromList(shared);
    } catch (error) {
      if (error is ArgumentError) rethrow;
      throw ArgumentError('public key is not a valid secp256k1 point: $error');
    }
  }

  static String _decryptEnvelope(
    Uint8List secret,
    ({Uint8List ciphertext, Uint8List iv}) envelope,
  ) {
    try {
      final plaintext = _aesCbcDecrypt(
        secret,
        envelope.iv,
        envelope.ciphertext,
      );
      return utf8.decode(plaintext);
    } catch (error) {
      if (error is Nip04DecryptException) rethrow;
      throw const Nip04DecryptException(
        'invalid padding, key or UTF-8 plaintext',
      );
    }
  }

  static ({Uint8List ciphertext, Uint8List iv}) _decodeEnvelope(
    String payload,
  ) {
    if (payload.length > maxPayloadCharacters) {
      throw const Nip04DecryptException('payload too long');
    }
    const delimiter = '?iv=';
    final separator = payload.indexOf(delimiter);
    if (separator <= 0 || separator != payload.lastIndexOf(delimiter)) {
      throw const Nip04DecryptException('invalid payload format');
    }
    final ciphertext = _decodeCompatibleBase64(
      payload.substring(0, separator),
      'ciphertext',
    );
    final iv = _decodeCompatibleBase64(
      payload.substring(separator + delimiter.length),
      'IV',
    );
    if (iv.length != _ivSize) {
      throw const Nip04DecryptException('IV must be 16 bytes');
    }
    if (ciphertext.isEmpty ||
        ciphertext.length > _maxCiphertextBytes ||
        ciphertext.length % _blockSize != 0) {
      throw const Nip04DecryptException('invalid ciphertext length');
    }
    return (
      ciphertext: Uint8List.fromList(ciphertext),
      iv: Uint8List.fromList(iv),
    );
  }

  static List<int> _decodeCompatibleBase64(String value, String name) {
    if (value.isEmpty) throw Nip04DecryptException('empty $name base64');
    try {
      // Dart's Base64 codec, and therefore the shipped nostr_tools adapter,
      // accepts standard/URL-safe alphabets and `%3D` padding escapes while
      // still requiring four-character alignment. Keep that interoperability
      // profile explicit.
      return base64.decode(value);
    } catch (_) {
      throw Nip04DecryptException('invalid $name base64');
    }
  }

  static Uint8List _aesCbcEncrypt(
    Uint8List key,
    Uint8List iv,
    Uint8List plaintext,
  ) {
    final padding = _blockSize - (plaintext.length % _blockSize);
    final padded = Uint8List(plaintext.length + padding)
      ..setRange(0, plaintext.length, plaintext)
      ..fillRange(plaintext.length, plaintext.length + padding, padding);
    return _aesCbc(key, iv, padded, encrypting: true);
  }

  static Uint8List _aesCbcDecrypt(
    Uint8List key,
    Uint8List iv,
    Uint8List ciphertext,
  ) {
    final padded = _aesCbc(key, iv, ciphertext, encrypting: false);
    final padding = padded.last;
    if (padding == 0 || padding > _blockSize || padding > padded.length) {
      throw const Nip04DecryptException('invalid PKCS#7 padding');
    }
    var mismatch = 0;
    for (var index = padded.length - padding; index < padded.length; index++) {
      mismatch |= padded[index] ^ padding;
    }
    if (mismatch != 0) {
      throw const Nip04DecryptException('invalid PKCS#7 padding');
    }
    return Uint8List.fromList(padded.sublist(0, padded.length - padding));
  }

  static Uint8List _aesCbc(
    Uint8List key,
    Uint8List iv,
    Uint8List input, {
    required bool encrypting,
  }) {
    final cipher = CBCBlockCipher(AESEngine())
      ..init(encrypting, ParametersWithIV(KeyParameter(key), iv));
    final output = Uint8List(input.length);
    for (var offset = 0; offset < input.length; offset += _blockSize) {
      cipher.processBlock(input, offset, output, offset);
    }
    return output;
  }

  static Uint8List _randomBytes(int length) {
    final random = Random.secure();
    return Uint8List.fromList(
      List<int>.generate(length, (_) => random.nextInt(256)),
    );
  }

  static void _requireHex32(String name, String value) {
    if (value.length != 64 || !RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(value)) {
      throw ArgumentError('$name must contain 64 hexadecimal characters');
    }
  }

  static void _requireLength(String name, List<int> value, int expected) {
    if (value.length != expected) {
      throw ArgumentError('$name must be $expected bytes, got ${value.length}');
    }
  }
}
