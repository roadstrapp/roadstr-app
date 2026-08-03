import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/favorites_sync_service.dart';

/// This one string decides where an encrypted snapshot of the user's home
/// address is published, so the validator is the security boundary: anything
/// it lets through is a destination.
void main() {
  group('normaliseRelayUrl', () {
    test('accepts an ordinary wss relay', () {
      expect(FavoritesSyncService.normaliseRelayUrl('wss://relay.example.com'),
          'wss://relay.example.com');
      expect(
          FavoritesSyncService.normaliseRelayUrl('  wss://relay.example.com  '),
          'wss://relay.example.com',
          reason: 'a pasted URL usually brings whitespace with it');
    });

    test('a trailing slash is the same relay, not a second one', () {
      expect(FavoritesSyncService.normaliseRelayUrl('wss://relay.example.com/'),
          'wss://relay.example.com');
    });

    test('keeps a real path, which some relays use', () {
      expect(
          FavoritesSyncService.normaliseRelayUrl('wss://example.com/nostr'),
          'wss://example.com/nostr');
    });

    test('refuses anything that is not TLS WebSocket', () {
      for (final bad in [
        'ws://relay.example.com', // cleartext: leaks the traffic on the LAN
        'https://relay.example.com',
        'relay.example.com',
        'javascript:alert(1)',
        'file:///etc/passwd',
      ]) {
        expect(FavoritesSyncService.normaliseRelayUrl(bad), isNull,
            reason: bad);
      }
    });

    test('refuses credentials, queries and fragments', () {
      for (final bad in [
        'wss://user:pw@relay.example.com',
        'wss://relay.example.com?token=abc',
        'wss://relay.example.com#frag',
      ]) {
        expect(FavoritesSyncService.normaliseRelayUrl(bad), isNull,
            reason: bad);
      }
    });

    test('refuses a host that is not one', () {
      expect(FavoritesSyncService.normaliseRelayUrl('wss://'), isNull);
      expect(FavoritesSyncService.normaliseRelayUrl('wss://localhost'), isNull,
          reason: 'no dot: not a public relay name');
      expect(FavoritesSyncService.normaliseRelayUrl(''), isNull);
      expect(FavoritesSyncService.normaliseRelayUrl('   '), isNull);
      expect(
          FavoritesSyncService.normaliseRelayUrl('wss://${'a' * 300}.com'),
          isNull,
          reason: 'length cap');
    });

    test('a built-in relay is not a custom one', () {
      // Otherwise it would be published to twice and shown as if the user had
      // added something.
      expect(FavoritesSyncService.normaliseRelayUrl('wss://nos.lol'), isNull);
      expect(FavoritesSyncService.normaliseRelayUrl('wss://nos.lol/'), isNull);
    });
  });
}
