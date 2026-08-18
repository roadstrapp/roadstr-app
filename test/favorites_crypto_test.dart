import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/favorites_crypto.dart';

void main() {
  test('round-trips with the correct password', () {
    const plaintext = '{"favorites":[{"label":"Casa","lat":45.46,"lon":9.19}]}';
    final env =
        FavoritesCrypto.encrypt(plaintext, 'correct horse battery staple');
    final out = FavoritesCrypto.decrypt(env, 'correct horse battery staple');
    expect(out, plaintext);
  });

  test('wrong password throws', () {
    final env = FavoritesCrypto.encrypt('secret data', 'right-password');
    expect(() => FavoritesCrypto.decrypt(env, 'wrong-password'),
        throwsA(isA<FavoritesDecryptException>()));
  });

  test('tampered ciphertext throws (GCM tag check)', () {
    final env = FavoritesCrypto.encrypt('secret data', 'pw');
    final tampered = Map<String, dynamic>.from(env);
    final bytes = (tampered['ciphertext'] as String).codeUnits;
    tampered['ciphertext'] = String.fromCharCodes(
        [...bytes.sublist(0, bytes.length - 4), 65, 65, 65, 65]);
    expect(() => FavoritesCrypto.decrypt(tampered, 'pw'),
        throwsA(isA<FavoritesDecryptException>()));
  });

  test('different salt/iv each call even for the same password+plaintext', () {
    final a = FavoritesCrypto.encrypt('same text', 'pw');
    final b = FavoritesCrypto.encrypt('same text', 'pw');
    expect(a['salt'], isNot(equals(b['salt'])));
    expect(a['ciphertext'], isNot(equals(b['ciphertext'])));
  });

  group('off the UI isolate', () {
    // The derivation is deliberately expensive (600k PBKDF2 iterations) and
    // synchronous, so running it inline freezes whatever isolate calls it.
    // Auto-push fires on every favourite edit and auto-pull at startup, which
    // made that freeze part of ordinary use rather than of an explicit export.
    test('encryptAsync/decryptAsync round-trip', () async {
      final envelope =
          await FavoritesCrypto.encryptAsync('favourite places', 'pw');
      expect(envelope['ciphertext'], isNotNull);
      expect(await FavoritesCrypto.decryptAsync(envelope, 'pw'),
          'favourite places');
    });

    test('the wrong password still throws across the isolate boundary',
        () async {
      // The failure has to survive being forwarded from the background
      // isolate, otherwise a bad passphrase would look like a success.
      final envelope = await FavoritesCrypto.encryptAsync('secret', 'right');
      await expectLater(FavoritesCrypto.decryptAsync(envelope, 'wrong'),
          throwsA(isA<FavoritesDecryptException>()));
    });

    test('async output is readable by the synchronous decrypt', () async {
      // Envelopes written by either path must stay interchangeable, so an
      // export from one app version imports into another.
      final envelope = await FavoritesCrypto.encryptAsync('data', 'pw');
      expect(FavoritesCrypto.decrypt(envelope, 'pw'), 'data');
      expect(await FavoritesCrypto.decryptAsync(
          FavoritesCrypto.encrypt('data', 'pw'), 'pw'), 'data');
    });
  });
}
