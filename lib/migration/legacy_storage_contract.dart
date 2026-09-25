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
