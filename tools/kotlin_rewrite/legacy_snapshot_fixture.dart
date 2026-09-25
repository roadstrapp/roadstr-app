import 'dart:convert';

import 'package:crypto/crypto.dart';

import 'legacy_snapshot_envelope.dart';
import 'legacy_storage_contract.dart';

final String fixturePrivateKeyHex = List.filled(32, '11').join();
final String fixturePublicKeyHex = List.filled(32, '22').join();
List<int> get fixtureHiveKeyBytes => List.generate(32, (index) => index);

Set<String> get fixtureDynamicHiveKeys => {
      'activity_inbox_$fixturePublicKeyHex',
      'activity_zap_cursor_$fixturePublicKeyHex',
      'activity_confirmation_cursor_$fixturePublicKeyHex',
    };

Map<String, List<int>> get syntheticLegacyAssetContents => {
      'espeak-ng-data/.roadstr_extracted': const [],
      'kokoro/if_sara.bin': utf8.encode('synthetic-kokoro-voice'),
      'kokoro/model_q8f16.onnx': utf8.encode('synthetic-kokoro-model'),
      'kokoro/tokenizer.json': utf8.encode('{"fixture":true}'),
      'piper/de_DE-thorsten-medium.onnx': utf8.encode('synthetic-piper-model'),
      'piper/de_DE-thorsten-medium.onnx.json':
          utf8.encode('{"fixture":"piper"}'),
    };

Map<String, dynamic> buildSyntheticLegacyHiveValues() {
  final favoriteJson = jsonEncode({
    'label': 'Fixture home',
    'address': '1 Test Road',
    'lat': 45.4642,
    'lon': 9.19,
  });
  final historyJson = jsonEncode({
    'label': 'Fixture place',
    'lat': 45.47,
    'lon': 9.2,
  });
  final pendingJson = jsonEncode({
    'event': {'id': 'fixture-event', 'kind': 1315},
    'expiresAt': 1700003600,
  });
  return <String, dynamic>{
    'autoDark': true,
    'autoCenterOnLaunch': false,
    'avoidUnpavedRoads': true,
    'disclaimer_accepted': true,
    'fav_sync_custom_relay': 'wss://relay.fixture.invalid',
    'fav_sync_last_ts': 1700000000,
    'fav_sync_legacy_cleaned': true,
    'fav_sync_pass': 'fixture-legacy-passphrase-not-real',
    'favorites': [favoriteJson],
    'favoritesSyncAutoEnabled': true,
    'favoritesSyncLastAt': 1700000000123,
    'graphhopperApiKey': 'fixture-legacy-routing-key-not-real',
    'graphhopperServer': 'https://routing.fixture.invalid',
    'imperialUnits': false,
    'keepScreenOn': true,
    'keepScreenOnAlways': false,
    'kokoroSpeedStage': 2,
    'kokoroVoiceGender': 'f',
    'kokoroVolume': 0.8,
    'language': 'it',
    'mapEngine': 'maplibre',
    'mapTileUrl': 'https://tiles.fixture.invalid/{z}/{x}/{y}.png',
    'minBrightness': 0.2,
    'movementCursorColor': 'bitcoin',
    'movementCursorStyle': 'arrow',
    'nwcUri': 'nostr+walletconnect://fixture-legacy-not-real',
    'onboarding_v1': true,
    'parking_position': jsonEncode({
      'lat': 45.46,
      'lon': 9.18,
      'ts': 1700000000123,
    }),
    'pending_road_reports': [pendingJson],
    'privacy_disclosure_v2': true,
    'road_report_privacy_ack': true,
    'roadstr_profile_public': false,
    'routingProvider': 'graphhopper',
    'searchEngine': 'qwant',
    'searchHistory': [historyJson],
    'showAltitude': true,
    'showCrosswalks': true,
    'showTrafficLights': false,
    'speedometerStyle': 'compact',
    'themeId': 2,
    'voiceEnabled': true,
    'voice_unsupported_notice_shown': true,
    'activity_inbox_$fixturePublicKeyHex': [
      {'createdAt': 1700000000, 'id': 'fixture-notification'}
    ],
    'activity_zap_cursor_$fixturePublicKeyHex': 1700000001,
    'activity_confirmation_cursor_$fixturePublicKeyHex': 1700000002,
  };
}

Map<String, String> buildSyntheticLegacySecureValues() => {
      'hive_settings_key': base64Encode(fixtureHiveKeyBytes),
      'nostr_priv_hex': fixturePrivateKeyHex,
      'nostr_pub_hex': fixturePublicKeyHex,
      'nostr_flavor': 'nsec',
      'nostr_picture': 'https://profile.fixture.invalid/avatar.png',
      'nostr_name': 'Synthetic Fixture Rider',
      'routing_api_key': 'fixture-routing-key-not-real',
      'nwc_uri': 'nostr+walletconnect://fixture-secure-not-real',
      'favorites_sync_passphrase': 'fixture-sync-passphrase-not-real',
    };

LegacyEnvelopeSnapshot buildSyntheticLegacySnapshot() {
  final rawValues = buildSyntheticLegacyHiveValues();
  final ordinaryValues = <String, String>{
    'autoDark': 'true',
    'autoCenterOnLaunch': 'false',
    'avoidUnpavedRoads': 'true',
    'disclaimer_accepted': 'true',
    'fav_sync_custom_relay': 'wss://relay.fixture.invalid',
    'fav_sync_last_ts': '1700000000',
    'fav_sync_legacy_cleaned': 'true',
    'fav_sync_pass': 'fixture-legacy-passphrase-not-real',
    'favorites': jsonEncode(rawValues['favorites']),
    'favoritesSyncAutoEnabled': 'true',
    'favoritesSyncLastAt': '1700000000123',
    'graphhopperApiKey': 'fixture-legacy-routing-key-not-real',
    'graphhopperServer': 'https://routing.fixture.invalid',
    'imperialUnits': 'false',
    'keepScreenOn': 'true',
    'keepScreenOnAlways': 'false',
    'kokoroSpeedStage': '2',
    'kokoroVoiceGender': 'f',
    'kokoroVolume': '0.8',
    'language': 'it',
    'mapEngine': 'maplibre',
    'mapTileUrl': 'https://tiles.fixture.invalid/{z}/{x}/{y}.png',
    'minBrightness': '0.2',
    'movementCursorColor': 'bitcoin',
    'movementCursorStyle': 'arrow',
    'nwcUri': 'nostr+walletconnect://fixture-legacy-not-real',
    'onboarding_v1': 'true',
    'parking_position': rawValues['parking_position']! as String,
    'pending_road_reports': jsonEncode(rawValues['pending_road_reports']),
    'privacy_disclosure_v2': 'true',
    'road_report_privacy_ack': 'true',
    'roadstr_profile_public': 'false',
    'routingProvider': 'graphhopper',
    'searchEngine': 'qwant',
    'searchHistory': jsonEncode(rawValues['searchHistory']),
    'showAltitude': 'true',
    'showCrosswalks': 'true',
    'showTrafficLights': 'false',
    'speedometerStyle': 'compact',
    'themeId': '2',
    'voiceEnabled': 'true',
    'voice_unsupported_notice_shown': 'true',
    'activity_inbox_$fixturePublicKeyHex':
        jsonEncode(rawValues['activity_inbox_$fixturePublicKeyHex']),
    'activity_zap_cursor_$fixturePublicKeyHex': '1700000001',
    'activity_confirmation_cursor_$fixturePublicKeyHex': '1700000002',
  };
  final secureValues = buildSyntheticLegacySecureValues();

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
      for (final entry in syntheticLegacyAssetContents.entries)
        LegacyEnvelopeAsset(
          relativePath: entry.key,
          sizeBytes: entry.value.length,
          sha256: sha256.convert(entry.value).toString(),
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
