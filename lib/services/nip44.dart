// NIP-44 v2 encryption (versioned encryption for Nostr events).
// Spec: https://github.com/nostr-protocol/nips/blob/master/44.md
//
// Used for Roadstr's own private data (encrypted favorites sync) — NOT for
// NWC/zaps. NIP-47 (Nostr Wallet Connect) still specifies NIP-04 in its
// current spec and real-world wallets only understand that; converting
// zap_service.dart to NIP-44 would break wallet compatibility, so it stays
// on NIP-04. NIP-44 is the modern standard for everything Roadstr controls
// end-to-end, which is what this class is for.
//
// Algorithm: ECDH(secp256k1) -> HKDF-extract(salt="nip44-v2") -> conversation
// key -> per-message HKDF-expand(info=nonce) -> ChaCha20(IETF, 12-byte nonce)
// -> HMAC-SHA256 over (nonce || ciphertext). Payload = base64(0x02 || nonce
// || ciphertext || mac).
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:kepler/kepler.dart';
import 'package:pointycastle/export.dart';

class Nip44DecryptException implements Exception {
  final String message;
  const Nip44DecryptException(this.message);
  @override
  String toString() => 'Nip44DecryptException: $message';
}

class Nip44 {
  static final _saltBytes = Uint8List.fromList(utf8.encode('nip44-v2'));

  /// Encrypts [plaintext] from [privKeyHex] to [pubKeyHex] (both 64-char hex,
  /// x-only per Nostr convention — the even-Y point is assumed, same as NIP-04).
  static String encrypt(String privKeyHex, String pubKeyHex, String plaintext) {
    final convKey = _conversationKey(privKeyHex, pubKeyHex);
    final nonce = _randomBytes(32);
    final keys = _messageKeys(convKey, nonce);
    final padded = _pad(utf8.encode(plaintext));
    final ciphertext = _chacha20(keys.chachaKey, keys.chachaNonce, padded);
    final mac = _hmacSha256(keys.hmacKey, [...nonce, ...ciphertext]);
    return base64.encode([0x02, ...nonce, ...ciphertext, ...mac]);
  }

  /// Decrypts a NIP-44 v2 [payload]. Throws [Nip44DecryptException] on any
  /// integrity failure (wrong key, tampered ciphertext, malformed payload) —
  /// never returns partially-trusted plaintext.
  static String decrypt(String privKeyHex, String pubKeyHex, String payload) {
    final Uint8List raw;
    try {
      raw = base64.decode(payload);
    } catch (e) {
      throw Nip44DecryptException('invalid base64: $e');
    }
    if (raw.isEmpty || raw[0] != 0x02) {
      throw const Nip44DecryptException('unsupported version');
    }
    if (raw.length < 1 + 32 + 32 + 32) {
      throw const Nip44DecryptException('payload too short');
    }
    final nonce      = raw.sublist(1, 33);
    final mac        = raw.sublist(raw.length - 32);
    final ciphertext = raw.sublist(33, raw.length - 32);

    final convKey = _conversationKey(privKeyHex, pubKeyHex);
    final keys = _messageKeys(convKey, Uint8List.fromList(nonce));
    final expectedMac = _hmacSha256(keys.hmacKey, [...nonce, ...ciphertext]);
    if (!_constantTimeEquals(expectedMac, mac)) {
      throw const Nip44DecryptException('MAC verification failed');
    }
    final padded = _chacha20(keys.chachaKey, keys.chachaNonce, ciphertext);
    return _unpad(padded);
  }

  // ── key derivation ──────────────────────────────────────────────────────

  static Uint8List _conversationKey(String privKeyHex, String pubKeyHex) {
    // Kepler.byteSecret returns [x-coordinate(32B), y-coordinate(8B trunc)];
    // NIP-44, like NIP-04, only uses the unhashed 32-byte X coordinate of the
    // ECDH shared point. '02' prefix = assume the even-Y point for the
    // x-only Nostr pubkey, matching secp256k1 compressed-point convention.
    final sharedX = Uint8List.fromList(
        Kepler.byteSecret(privKeyHex, '02$pubKeyHex')[0]);
    // HKDF-extract(salt="nip44-v2", ikm=sharedX) == HMAC-SHA256(key=salt, msg=ikm).
    return Uint8List.fromList(_hmacSha256(_saltBytes, sharedX));
  }

  static _MessageKeys _messageKeys(Uint8List convKey, Uint8List nonce) {
    final expanded = _hkdfExpand(convKey, nonce, 76);
    return _MessageKeys(
      chachaKey:   Uint8List.fromList(expanded.sublist(0, 32)),
      chachaNonce: Uint8List.fromList(expanded.sublist(32, 44)),
      hmacKey:     Uint8List.fromList(expanded.sublist(44, 76)),
    );
  }

  /// RFC 5869 HKDF-expand using HMAC-SHA256.
  static List<int> _hkdfExpand(List<int> prk, List<int> info, int length) {
    final hmac = Hmac(sha256, prk);
    final out = <int>[];
    var t = <int>[];
    var i = 1;
    while (out.length < length) {
      final block = hmac.convert([...t, ...info, i]).bytes;
      t = block;
      out.addAll(block);
      i++;
    }
    return out.sublist(0, length);
  }

  static List<int> _hmacSha256(List<int> key, List<int> data) =>
      Hmac(sha256, key).convert(data).bytes;

  static Uint8List _chacha20(Uint8List key, Uint8List nonce, List<int> data) {
    final cipher = ChaCha7539Engine()
      ..init(true, ParametersWithIV(KeyParameter(key), nonce));
    return cipher.process(Uint8List.fromList(data));
  }

  // ── padding (NIP-44 custom scheme — obscures exact plaintext length) ────

  static Uint8List _pad(List<int> unpadded) {
    final n = unpadded.length;
    if (n == 0 || n > 65535) {
      throw ArgumentError('NIP-44 plaintext must be 1..65535 bytes, got $n');
    }
    final target = _paddedLen(n);
    final out = Uint8List(2 + target);
    out[0] = (n >> 8) & 0xff;
    out[1] = n & 0xff;
    out.setRange(2, 2 + n, unpadded);
    return out; // trailing bytes are already zero (Uint8List default-inits)
  }

  static String _unpad(Uint8List padded) {
    if (padded.length < 2) {
      throw const Nip44DecryptException('padded payload too short');
    }
    final n = (padded[0] << 8) | padded[1];
    // Reference implementation also requires the padded length to be EXACTLY
    // what _paddedLen(n) prescribes — a spec-conformance check that rejects
    // payloads other NIP-44 implementations would refuse too.
    if (n <= 0 || n > 65535 || padded.length != 2 + _paddedLen(n)) {
      throw const Nip44DecryptException('invalid length prefix');
    }
    return utf8.decode(padded.sublist(2, 2 + n));
  }

  /// next_power = 2^(floor(log2(n-1))+1); chunk = next_power<=256 ? 32 : next_power/8;
  /// padded_len = chunk * (floor((n-1)/chunk) + 1). Computed with exact integer
  /// bit-length arithmetic (not floating-point log2) to avoid rounding at
  /// power-of-two boundaries.
  static int _paddedLen(int n) {
    if (n <= 32) return 32;
    final m = n - 1;
    final nextPower = 1 << m.bitLength; // == 2^(floor(log2(m))+1)
    final chunk = nextPower <= 256 ? 32 : nextPower ~/ 8;
    return chunk * (m ~/ chunk + 1);
  }

  static Uint8List _randomBytes(int n) {
    final rnd = Random.secure();
    return Uint8List.fromList(List<int>.generate(n, (_) => rnd.nextInt(256)));
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

class _MessageKeys {
  final Uint8List chachaKey, chachaNonce, hmacKey;
  const _MessageKeys(
      {required this.chachaKey, required this.chachaNonce, required this.hmacKey});
}
