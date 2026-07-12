// Self-consistency tests for the from-scratch NIP-44 v2 implementation.
// These don't validate against the official nip44.vectors.json (not vendored
// here), but they do catch the classes of bugs most likely in a hand-rolled
// AEAD: asymmetric round-trip failures, padding boundary errors, and MAC/
// tamper-detection regressions.
import 'package:flutter_test/flutter_test.dart';
import 'package:kepler/kepler.dart';
import 'package:pointycastle/export.dart' show ECPrivateKey, ECPublicKey;
import 'package:roadstr/services/nip44.dart';

String _hexPriv(ECPrivateKey key) => key.d!.toRadixString(16).padLeft(64, '0');
String _hexPub(ECPublicKey key) =>
    Kepler.strinifyPublicKey(key).substring(2); // drop '02'/'03' prefix

void main() {
  final aliceKp = Kepler.generateKeyPair();
  final bobKp = Kepler.generateKeyPair();
  final alicePriv = _hexPriv(aliceKp.privateKey as ECPrivateKey);
  final bobPriv = _hexPriv(bobKp.privateKey as ECPrivateKey);
  final alicePub = _hexPub(aliceKp.publicKey as ECPublicKey);
  final bobPub = _hexPub(bobKp.publicKey as ECPublicKey);

  test('round-trips a normal message', () {
    const msg = 'Roadstr favorites sync payload — ciao mondo! 🚗';
    final enc = Nip44.encrypt(alicePriv, bobPub, msg);
    final dec = Nip44.decrypt(bobPriv, alicePub, enc);
    expect(dec, msg);
  });

  test('shared secret is symmetric regardless of key role', () {
    const msg = 'symmetry check';
    final enc = Nip44.encrypt(alicePriv, bobPub, msg);
    expect(Nip44.decrypt(bobPriv, alicePub, enc), msg);
  });

  test('self-encryption round-trips (own pubkey as recipient)', () {
    const msg = '{"favorites":[{"label":"Casa"}]}';
    final enc = Nip44.encrypt(alicePriv, alicePub, msg);
    expect(Nip44.decrypt(alicePriv, alicePub, enc), msg);
  });

  test('same plaintext encrypts to different ciphertext each time', () {
    const msg = 'nonce must be random';
    final a = Nip44.encrypt(alicePriv, bobPub, msg);
    final b = Nip44.encrypt(alicePriv, bobPub, msg);
    expect(a, isNot(equals(b)));
  });

  test('wrong recipient key fails to decrypt', () {
    const msg = 'for bob only';
    final enc = Nip44.encrypt(alicePriv, bobPub, msg);
    final eveKp = Kepler.generateKeyPair();
    final evePriv = _hexPriv(eveKp.privateKey as ECPrivateKey);
    expect(() => Nip44.decrypt(evePriv, alicePub, enc),
        throwsA(isA<Nip44DecryptException>()));
  });

  test('tampered payload fails MAC verification', () {
    const msg = 'integrity matters';
    final enc = Nip44.encrypt(alicePriv, bobPub, msg);
    final tampered = '${enc.substring(0, enc.length - 4)}AAAA';
    expect(() => Nip44.decrypt(bobPriv, alicePub, tampered),
        throwsA(isA<Nip44DecryptException>()));
  });

  test('padding boundaries round-trip (1, 32, 33, 256, 257 bytes)', () {
    for (final n in [1, 32, 33, 256, 257, 1000]) {
      final msg = 'x' * n;
      final enc = Nip44.encrypt(alicePriv, bobPub, msg);
      expect(Nip44.decrypt(bobPriv, alicePub, enc), msg, reason: 'n=$n');
    }
  });
}
