import 'dart:convert';

/// Audited names from the supported Flutter storage layout.
const String legacySettingsBoxName = 'settings';
const String legacySettingsFileName = 'settings.hive';
const String legacySettingsMigrationBackupFileName =
    'settings.hive.migration-backup';

const Set<String> legacyFixedHiveKeys = {
  'autoDark',
  'autoCenterOnLaunch',
  'avoidUnpavedRoads',
  'disclaimer_accepted',
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
  'onboarding_v1',
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

const Set<String> legacyDynamicHiveKeyPrefixes = {
  'activity_inbox_',
  'activity_zap_cursor_',
  'activity_confirmation_cursor_',
};

/// Explicit app-document assets worth reusing after the native cutover.
/// Missing files are valid because users download only the languages they use.
const Set<String> legacyVoiceAssetRelativePaths = {
  'espeak-ng-data/.roadstr_extracted',
  'kokoro/model_q8f16.onnx',
  'kokoro/tokenizer.json',
  'kokoro/af_heart.bin',
  'kokoro/am_michael.bin',
  'kokoro/ef_dora.bin',
  'kokoro/em_alex.bin',
  'kokoro/ff_siwis.bin',
  'kokoro/if_sara.bin',
  'kokoro/im_nicola.bin',
  'kokoro/jf_alpha.bin',
  'kokoro/jm_kumo.bin',
  'kokoro/pf_dora.bin',
  'kokoro/pm_alex.bin',
  'kokoro/zf_xiaobei.bin',
  'kokoro/zm_yunxi.bin',
  'piper/de_DE-thorsten-medium.onnx',
  'piper/de_DE-thorsten-medium.onnx.json',
};

final RegExp _publicKeyHex = RegExp(r'^[0-9a-fA-F]{64}$');
final RegExp _canonicalHiveKey = RegExp(r'^[A-Za-z0-9+/]{43}=$');

bool isLegacyDynamicHiveKey(String key) {
  for (final prefix in legacyDynamicHiveKeyPrefixes) {
    if (key.startsWith(prefix) &&
        _publicKeyHex.hasMatch(key.substring(prefix.length))) {
      return true;
    }
  }
  return false;
}

bool isKnownLegacyHiveKey(String key) =>
    legacyFixedHiveKeys.contains(key) || isLegacyDynamicHiveKey(key);

/// Deterministic shape policy for values that must remain protected during
/// the Dart-to-native hand-off.
///
/// This deliberately does not derive a public key: cryptographic derivation
/// belongs to the native validation boundary, where the supplied
/// identity verifier can reject a private/public mismatch before any write.
/// The collector still rejects impossible modes and malformed encodings early.
abstract final class LegacyProtectedStatePolicy {
  static String? validateSecureValues(Map<String, String> values) {
    final hiveKey = values['hive_settings_key'];
    if (hiveKey != null && !isCanonicalLegacyHiveKey(hiveKey)) {
      return 'Legacy Hive encryption key is invalid';
    }

    final publicKey = values['nostr_pub_hex'];
    final privateKey = values['nostr_priv_hex'];
    final flavor = values['nostr_flavor'];
    if (publicKey != null && !_publicKeyHex.hasMatch(publicKey)) {
      return 'Stored public key has an invalid shape';
    }
    if (privateKey != null && !_publicKeyHex.hasMatch(privateKey)) {
      return 'Stored private key has an invalid shape';
    }
    if (flavor != null && flavor != 'amber' && flavor != 'nsec') {
      return 'Stored identity flavor is unsupported';
    }

    final hasIdentity =
        publicKey != null || privateKey != null || flavor != null;
    if (!hasIdentity) return null;
    if (flavor == null) return 'Stored identity flavor is missing';
    if (flavor == 'nsec' && (publicKey == null || privateKey == null)) {
      return 'nsec identity is incomplete';
    }
    if (flavor == 'amber' && (publicKey == null || privateKey != null)) {
      return 'Amber identity is incomplete';
    }
    return null;
  }

  static bool isCanonicalLegacyHiveKey(String encoded) {
    if (!_canonicalHiveKey.hasMatch(encoded)) return false;
    try {
      final decoded = base64Decode(encoded);
      return decoded.length == 32 && base64Encode(decoded) == encoded;
    } on FormatException {
      return false;
    }
  }
}
