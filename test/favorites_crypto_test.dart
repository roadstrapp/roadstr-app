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
}
