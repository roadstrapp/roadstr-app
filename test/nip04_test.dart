import 'package:flutter_test/flutter_test.dart';
import 'package:nostr_tools/nostr_tools.dart' show Nip04;
import 'package:roadstr/services/nip04.dart';

const _aliceSecret =
    '0000000000000000000000000000000000000000000000000000000000000001';
const _alicePublic =
    '79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798';
const _bobSecret =
    '0000000000000000000000000000000000000000000000000000000000000002';
const _bobPublic =
    'c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5';

void main() {
  test('production encryption round-trips between NWC peers', () {
    const plaintext =
        '{"method":"pay_invoice","params":{"invoice":"lnbc1roadstr"}}';
    final payload = Nip04Cipher.encrypt(_aliceSecret, _bobPublic, plaintext);

    expect(
      Nip04Cipher.decrypt(_bobSecret, _alicePublic, payload),
      plaintext,
    );
    expect(Nip04().decrypt(_bobSecret, _alicePublic, payload), plaintext);
  });

  test('decrypts payloads emitted by the shipped nostr_tools adapter', () {
    const plaintext = 'legacy NWC response';
    final payload = Nip04().encrypt(_bobSecret, _alicePublic, plaintext);

    expect(
      Nip04Cipher.decrypt(_aliceSecret, _bobPublic, payload),
      plaintext,
    );
  });

  test('production encryption uses a fresh random IV', () {
    final first = Nip04Cipher.encrypt(_aliceSecret, _bobPublic, 'same');
    final second = Nip04Cipher.encrypt(_aliceSecret, _bobPublic, 'same');

    expect(first, isNot(second));
  });

  test('empty plaintext and UTF-8 round-trip', () {
    for (final plaintext in ['', 'Roadstr — ciao 🚗 Привет こんにちは']) {
      final payload = Nip04Cipher.encrypt(
        _aliceSecret,
        _bobPublic,
        plaintext,
      );
      expect(
        Nip04Cipher.decrypt(_bobSecret, _alicePublic, payload),
        plaintext,
      );
    }
  });

  test('malformed relay payloads are rejected', () {
    final valid = Nip04Cipher.encrypt(_aliceSecret, _bobPublic, 'message');
    final parts = valid.split('?iv=');
    final malformed = [
      '',
      parts[0],
      '?iv=${parts[1]}',
      '${parts[0]}?iv=',
      '$valid?iv=${parts[1]}',
      ' ${parts[0]}?iv=${parts[1]}',
      '${parts[0]}?iv=${parts[1].replaceAll('=', '')}',
    ];

    for (final payload in malformed) {
      expect(
        () => Nip04Cipher.decrypt(_bobSecret, _alicePublic, payload),
        throwsA(isA<Nip04DecryptException>()),
        reason: payload,
      );
    }
  });

  test('shipped Base64 URL-safe and percent-escaped forms stay accepted', () {
    final valid = Nip04Cipher.encrypt(_aliceSecret, _bobPublic, 'message');
    final parts = valid.split('?iv=');
    final compatible = [
      parts
          .map((part) => part.replaceAll('+', '-').replaceAll('/', '_'))
          .join('?iv='),
      parts.map((part) => part.replaceAll('=', '%3D')).join('?iv='),
    ];

    for (final payload in compatible) {
      expect(
        Nip04Cipher.decrypt(_bobSecret, _alicePublic, payload),
        'message',
      );
      expect(Nip04().decrypt(_bobSecret, _alicePublic, payload), 'message');
    }
  });

  test('oversized plaintext is rejected before encryption', () {
    expect(
      () => Nip04Cipher.encrypt(
        _aliceSecret,
        _bobPublic,
        'x' * (Nip04Cipher.maxPlaintextBytes + 1),
      ),
      throwsArgumentError,
    );
  });
}
