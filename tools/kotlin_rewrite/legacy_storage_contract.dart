const String legacySettingsBoxName = 'settings';
const String legacySettingsFileName = 'settings.hive';

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
