// Privacy invariants of the favorites sync layer. These properties are what
// keeps a relay operator from learning anything about the user's favorites:
// if one of them regresses, ciphertext size or event tags start leaking again.
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/favorites_sync_service.dart';

void main() {
  group('padToBucket', () {
    test('pads to 4096-byte multiples, counting UTF-8 bytes not chars', () {
      for (final s in [
        '[]',
        '[{"label":"Casa","address":"Via Roma 1","lat":44.4,"lon":12.2}]',
        // Non-ASCII: 'à' is 2 bytes in UTF-8 — padding must count bytes,
        // or the padded NIP-44 plaintext would land off-bucket.
        '[{"label":"Città àèìòù ${'x' * 500}"}]',
        'a' * 4096, // exactly one bucket already
        'b' * 4097, // one byte over → next bucket
      ]) {
        final padded = FavoritesSyncService.padToBucket(s);
        final byteLen = utf8.encode(padded).length;
        expect(byteLen % 4096, 0,
            reason: 'off-bucket for input len ${s.length}');
        expect(byteLen >= utf8.encode(s).length, true);
      }
    });

    test('snapshots with different favorite counts pad to identical size', () {
      String snapshot(int n) => jsonEncode(List.generate(
          n,
          (i) => {
                'label': 'Place $i',
                'address': 'Some Street $i, Some City',
                'lat': 44.0 + i,
                'lon': 12.0 + i,
              }));
      final a = FavoritesSyncService.padToBucket(snapshot(1));
      final b = FavoritesSyncService.padToBucket(snapshot(20));
      expect(utf8.encode(a).length, utf8.encode(b).length,
          reason: 'ciphertext length would reveal the favorites count');
    });

    test('padding survives a JSON decode round-trip', () {
      final original = [
        {'label': 'Home', 'address': 'x', 'lat': 1.0, 'lon': 2.0}
      ];
      final padded = FavoritesSyncService.padToBucket(jsonEncode(original));
      expect(jsonDecode(padded), original);
    });
  });

  group('hashedDTag', () {
    test('is deterministic and 64 hex chars', () {
      final pub = 'a' * 64;
      final d1 = FavoritesSyncService.hashedDTag(pub);
      final d2 = FavoritesSyncService.hashedDTag(pub);
      expect(d1, d2);
      expect(RegExp(r'^[0-9a-f]{64}$').hasMatch(d1), true);
    });

    test('differs per pubkey and never contains the app marker', () {
      final d1 = FavoritesSyncService.hashedDTag('a' * 64);
      final d2 = FavoritesSyncService.hashedDTag('b' * 64);
      expect(d1 == d2, false);
      expect(d1.contains('roadstr'), false,
          reason: 'a readable marker would re-enable app fingerprinting');
    });
  });
}
