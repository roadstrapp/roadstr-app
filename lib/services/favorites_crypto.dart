// Password-based encryption for the favorites export file.
// PBKDF2-HMAC-SHA256 (600k iterations, OWASP 2023 minimum) derives an
// AES-256 key from the user's password; AES-256-GCM then encrypts the
// favorites JSON with authentication (tamper/wrong-password detection).
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

class FavoritesDecryptException implements Exception {
  final String message;
  const FavoritesDecryptException(this.message);
  @override
  String toString() => 'FavoritesDecryptException: $message';
}

class FavoritesCrypto {
  static const _iterations = 600000;
  static const _saltLen = 16;
  static const _ivLen = 12;   // GCM standard nonce size
  static const _keyLen = 32;  // AES-256
  static const _macBits = 128;

  /// Encrypts [plaintext] with [password]. Returns a JSON-serialisable
  /// envelope: {v, iterations, salt, iv, ciphertext} (base64 fields).
  static Map<String, dynamic> encrypt(String plaintext, String password) {
    final salt = _randomBytes(_saltLen);
    final iv = _randomBytes(_ivLen);
    final key = _deriveKey(password, salt, _iterations);
    final cipher = GCMBlockCipher(AESEngine())
      ..init(true, AEADParameters(KeyParameter(key), _macBits, iv, Uint8List(0)));
    final ciphertext = cipher.process(Uint8List.fromList(utf8.encode(plaintext)));
    return {
      'v': 1,
      'iterations': _iterations,
      'salt': base64.encode(salt),
      'iv': base64.encode(iv),
      'ciphertext': base64.encode(ciphertext),
    };
  }

  /// Decrypts an [envelope] produced by [encrypt]. Throws
  /// [FavoritesDecryptException] on wrong password or a corrupted/tampered
  /// file — GCM's built-in authentication tag makes this detection reliable,
  /// never silently returning garbage plaintext.
  static String decrypt(Map<String, dynamic> envelope, String password) {
    try {
      final salt = base64.decode(envelope['salt'] as String);
      final iv = base64.decode(envelope['iv'] as String);
      final ciphertext = base64.decode(envelope['ciphertext'] as String);
      final iterations = envelope['iterations'] as int? ?? _iterations;
      final key = _deriveKey(password, Uint8List.fromList(salt), iterations);
      final cipher = GCMBlockCipher(AESEngine())
        ..init(false, AEADParameters(KeyParameter(key), _macBits,
            Uint8List.fromList(iv), Uint8List(0)));
      final plainBytes = cipher.process(Uint8List.fromList(ciphertext));
      return utf8.decode(plainBytes);
    } on FavoritesDecryptException {
      rethrow;
    } catch (_) {
      // Wrong password (GCM tag mismatch), truncated data, or malformed
      // envelope all land here — never distinguish them to the caller in a
      // way that would help an attacker brute-force the password.
      throw const FavoritesDecryptException('wrong password or corrupted file');
    }
  }

  static Uint8List _deriveKey(String password, Uint8List salt, int iterations) {
    final pbkdf2 = PBKDF2KeyDerivator(Mac('SHA-256/HMAC'))
      ..init(Pbkdf2Parameters(salt, iterations, _keyLen));
    return pbkdf2.process(Uint8List.fromList(utf8.encode(password)));
  }

  static Uint8List _randomBytes(int n) {
    final rnd = Random.secure();
    return Uint8List.fromList(List<int>.generate(n, (_) => rnd.nextInt(256)));
  }
}
