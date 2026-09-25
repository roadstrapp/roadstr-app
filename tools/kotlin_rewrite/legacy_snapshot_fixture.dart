import 'dart:convert';

import 'legacy_snapshot_envelope.dart';

final String fixturePrivateKeyHex = List.filled(32, '11').join();
final String fixturePublicKeyHex = List.filled(32, '22').join();

const Set<String> legacyFixedHiveKeys = {
  'autoDark',
  'autoCenterOnLaunch',
  'avoidUnpavedRoads',
  'fav_sync_custom_relay',
  'fav_sync_last_ts',
  'fav_sync_legacy_cleaned',
  'fav_sync_pass',
  'favorites',
  'favoritesSyncAutoEnabled',
  'favoritesSyncLastAt',
  'graphhopperApiKey',
  'graphhopperServer',
  'imperialUnits',
  'keepScreenOn',
  'keepScreenOnAlways',
  'kokoroSpeedStage',
  'kokoroVoiceGender',
  'kokoroVolume',
  'language',
  'mapEngine',
  'mapTileUrl',
  'minBrightness',
  'movementCursorColor',
  'movementCursorStyle',
  'nwcUri',
  'parking_position',
  'pending_road_reports',
  'privacy_disclosure_v2',
  'road_report_privacy_ack',
  'roadstr_profile_public',
  'routingProvider',
  'searchEngine',
  'searchHistory',
  'showAltitude',
  'showCrosswalks',
  'showTrafficLights',
  'speedometerStyle',
  'themeId',
  'voiceEnabled',
  'voice_unsupported_notice_shown',
};

const Set<String> legacySecureKeys = {
  'hive_settings_key',
  'nostr_priv_hex',
  'nostr_pub_hex',
  'nostr_flavor',
  'nostr_picture',
  'nostr_name',
  'routing_api_key',
  'nwc_uri',
  'favorites_sync_passphrase',
};

Set<String> get fixtureDynamicHiveKeys => {
      'activity_inbox_$fixturePublicKeyHex',
      'activity_zap_cursor_$fixturePublicKeyHex',
      'activity_confirmation_cursor_$fixturePublicKeyHex',
    };

LegacyEnvelopeSnapshot buildSyntheticLegacySnapshot() {
  final ordinaryValues = <String, String>{
    'autoDark': 'true',
    'autoCenterOnLaunch': 'false',
    'avoidUnpavedRoads': 'true',
    'fav_sync_custom_relay': 'wss://relay.fixture.invalid',
    'fav_sync_last_ts': '1700000000',
    'fav_sync_legacy_cleaned': 'true',
    'fav_sync_pass': 'fixture-legacy-passphrase-not-real',
    'favorites': jsonEncode([
      {
        'label': 'Fixture home',
        'address': '1 Test Road',
        'lat': 45.4642,
        'lon': 9.19,
      }
    ]),
    'favoritesSyncAutoEnabled': 'true',
    'favoritesSyncLastAt': '1700000000123',
    'graphhopperApiKey': 'fixture-legacy-routing-key-not-real',
    'graphhopperServer': 'https://routing.fixture.invalid',
    'imperialUnits': 'false',
    'keepScreenOn': 'true',
    'keepScreenOnAlways': 'false',
    'kokoroSpeedStage': '2',
    'kokoroVoiceGender': 'female',
    'kokoroVolume': '0.8',
    'language': 'it',
    'mapEngine': 'maplibre',
    'mapTileUrl': 'https://tiles.fixture.invalid/{z}/{x}/{y}.png',
    'minBrightness': '0.2',
    'movementCursorColor': 'bitcoin',
    'movementCursorStyle': 'arrow',
    'nwcUri': 'nostr+walletconnect://fixture-legacy-not-real',
    'parking_position': jsonEncode({
      'lat': 45.46,
      'lon': 9.18,
      'savedAt': 1700000000123,
    }),
    'pending_road_reports': jsonEncode([
      jsonEncode({
        'event': {'id': 'fixture-event', 'kind': 1315},
        'expiresAt': 1700003600,
      })
    ]),
    'privacy_disclosure_v2': 'true',
    'road_report_privacy_ack': 'true',
    'roadstr_profile_public': 'false',
    'routingProvider': 'graphhopper',
    'searchEngine': 'qwant',
    'searchHistory': jsonEncode([
      {'label': 'Fixture place', 'lat': 45.47, 'lon': 9.2}
    ]),
    'showAltitude': 'true',
    'showCrosswalks': 'true',
    'showTrafficLights': 'false',
    'speedometerStyle': 'compact',
    'themeId': '2',
    'voiceEnabled': 'true',
    'voice_unsupported_notice_shown': 'true',
    'activity_inbox_$fixturePublicKeyHex': jsonEncode([
      {'id': 'fixture-notification', 'createdAt': 1700000000}
    ]),
    'activity_zap_cursor_$fixturePublicKeyHex': '1700000001',
    'activity_confirmation_cursor_$fixturePublicKeyHex': '1700000002',
  };
  final secureValues = <String, String>{
    'hive_settings_key': base64Encode(List.generate(32, (index) => index)),
    'nostr_priv_hex': fixturePrivateKeyHex,
    'nostr_pub_hex': fixturePublicKeyHex,
    'nostr_flavor': 'nsec',
    'nostr_picture': 'https://profile.fixture.invalid/avatar.png',
    'nostr_name': 'Synthetic Fixture Rider',
    'routing_api_key': 'fixture-routing-key-not-real',
    'nwc_uri': 'nostr+walletconnect://fixture-secure-not-real',
    'favorites_sync_passphrase': 'fixture-sync-passphrase-not-real',
  };

  _expectExactKeys(ordinaryValues.keys.toSet(),
      legacyFixedHiveKeys.union(fixtureDynamicHiveKeys), 'Hive');
  _expectExactKeys(secureValues.keys.toSet(), legacySecureKeys, 'secure');

  return LegacyEnvelopeSnapshot(
    schemaVersion: legacySnapshotSchemaVersion,
    ordinaryValues: ordinaryValues,
    secureValues: secureValues,
    identity: LegacyEnvelopeIdentity(
      publicKeyHex: fixturePublicKeyHex,
      flavor: 'nsec',
      privateKeyHex: fixturePrivateKeyHex,
    ),
    assets: [
      LegacyEnvelopeAsset(
        relativePath: 'espeak-ng-data/.roadstr-ready',
        sizeBytes: 17,
        sha256: List.filled(32, 'cc').join(),
      ),
      LegacyEnvelopeAsset(
        relativePath: 'kokoro/model.onnx',
        sizeBytes: 123456,
        sha256: List.filled(32, 'aa').join(),
      ),
      LegacyEnvelopeAsset(
        relativePath: 'piper/model.onnx',
        sizeBytes: 654321,
        sha256: List.filled(32, 'bb').join(),
      ),
    ],
  );
}

void _expectExactKeys(Set<String> actual, Set<String> expected, String kind) {
  final missing = expected.difference(actual);
  final unexpected = actual.difference(expected);
  if (missing.isNotEmpty || unexpected.isNotEmpty) {
    throw StateError(
      '$kind fixture key mismatch; missing=$missing unexpected=$unexpected',
    );
  }
}
