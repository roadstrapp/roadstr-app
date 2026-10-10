import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_settings_strings.xml';

const _resourceKeys = <String, String>{
  'native_settings_title': 'settingsTitle',
  'native_settings_close': 'close',
  'native_settings_theme_section': 'sectionTheme',
  'native_settings_theme_light_nostr': 'themeLightNostr',
  'native_settings_theme_light_bitcoin': 'themeLightBitcoin',
  'native_settings_theme_dark_nostr': 'themeDarkNostr',
  'native_settings_theme_dark_bitcoin': 'themeDarkBitcoin',
  'native_settings_auto_dark': 'autoDarkMode',
  'native_settings_auto_dark_desc': 'autoDarkModeDesc',
  'native_settings_language_section': 'sectionLanguage',
  'native_settings_language_system': 'langSystem',
  'native_settings_visibility_section': 'profileVisibilityTitle',
  'native_settings_visibility_desc': 'profileVisibilityDesc',
  'native_settings_visibility_clear': 'profileVisibilityClear',
  'native_settings_visibility_pseudonymous': 'profileVisibilityPseudonymous',
  'native_settings_map_section': 'sectionMap',
  'native_settings_avoid_unpaved': 'avoidUnpavedRoads',
  'native_settings_avoid_unpaved_desc': 'avoidUnpavedRoadsDescription',
  'native_settings_keep_screen_on': 'keepScreenOn',
  'native_settings_keep_screen_on_desc': 'keepScreenOnDescription',
  'native_settings_keep_screen_always': 'keepScreenOnAlways',
  'native_settings_keep_screen_always_desc': 'keepScreenOnAlwaysDescription',
  'native_settings_min_brightness': 'minBrightness',
  'native_settings_min_brightness_desc': 'minBrightnessDescription',
  'native_settings_min_brightness_off': 'minBrightnessOff',
  'native_settings_show_altitude': 'showAltitude',
  'native_settings_show_altitude_desc': 'showAltitudeDescription',
  'native_settings_road_overlays': 'sectionRoadOverlays',
  'native_settings_crosswalks': 'showCrosswalks',
  'native_settings_crosswalks_desc': 'showCrosswalksDescription',
  'native_settings_traffic_lights': 'showTrafficLights',
  'native_settings_traffic_lights_desc': 'showTrafficLightsDescription',
  'native_settings_auto_center': 'autoCenterOnLaunch',
  'native_settings_auto_center_desc': 'autoCenterOnLaunchDesc',
  'native_settings_imperial': 'settingsImperialUnits',
  'native_settings_imperial_desc': 'settingsImperialUnitsDesc',
  'native_settings_tile_url': 'mapTileUrlLabel',
  'native_settings_routing_provider': 'routingProviderLabel',
  'native_settings_provider_osrm': 'osrmProvider',
  'native_settings_provider_gh_local': 'graphhopperLocalProvider',
  'native_settings_provider_gh_cloud': 'graphhopperCloudProvider',
  'native_settings_provider_openroute': 'openrouteProvider',
  'native_settings_gh_server_hint': 'graphhopperServerHint',
  'native_settings_api_key_hint': 'graphhopperApiKeyHint',
  'native_settings_verify': 'verify',
  'native_settings_appearance_section': 'sectionNavigationAppearance',
  'native_settings_speedometer': 'speedometerStyleLabel',
  'native_settings_speedometer_classic': 'speedometerClassic',
  'native_settings_speedometer_digital': 'speedometerDigital',
  'native_settings_speedometer_analog': 'speedometerAnalog',
  'native_settings_speedometer_sport': 'speedometerSport',
  'native_settings_speedometer_minimal': 'speedometerMinimal',
  'native_settings_cursor': 'movementCursorStyleLabel',
  'native_settings_cursor_vehicle': 'cursorVehicleLabel',
  'native_settings_cursor_color': 'cursorColorLabel',
  'native_settings_cursor_standard': 'cursorStandard',
  'native_settings_cursor_formula1': 'cursorFormula1',
  'native_settings_cursor_suv': 'cursorSuv',
  'native_settings_cursor_racing': 'cursorRacing',
  'native_settings_cursor_electric': 'cursorElectric',
  'native_settings_cursor_city': 'cursorCity',
  'native_settings_cursor_classic500': 'cursorClassic500',
  'native_settings_color_violet': 'cursorColorViolet',
  'native_settings_color_indigo': 'cursorColorIndigo',
  'native_settings_color_blue': 'cursorColorBlue',
  'native_settings_color_green': 'cursorColorGreen',
  'native_settings_color_yellow': 'cursorColorYellow',
  'native_settings_color_orange': 'cursorColorOrange',
  'native_settings_color_red': 'cursorColorRed',
  'native_settings_web_search_section': 'sectionWebSearch',
  'native_settings_search_qwant_desc': 'searchEngineQwantDesc',
  'native_settings_search_brave_desc': 'searchEngineBraveDesc',
  'native_settings_search_ddg_desc': 'searchEngineDdgDesc',
  'native_settings_search_startpage_desc': 'searchEngineStartpageDesc',
  'native_settings_search_google_desc': 'searchEngineGoogleDesc',
  'native_settings_lightning_section': 'sectionLightning',
  'native_settings_nwc': 'nwcLabel',
  'native_settings_nwc_desc': 'nwcDesc',
  'native_settings_favorites_section': 'sectionFavorites',
  'native_settings_add_favorite': 'addFavorite',
  'native_settings_export_favorites': 'exportFavoritesTitle',
  'native_settings_import_favorites': 'importFavoritesTitle',
  'native_settings_sync_section': 'syncFavoritesTitle',
  'native_settings_sync_desc': 'syncFavoritesDesc',
  'native_settings_sync_auto': 'favoritesAutoSyncTitle',
  'native_settings_sync_auto_desc': 'favoritesAutoSyncDesc',
  'native_settings_sync_push': 'syncNowButton',
  'native_settings_sync_pull': 'syncPullButton',
  'native_settings_sync_passphrase': 'syncPassphraseTitle',
  'native_settings_sync_relay': 'syncCustomRelayTitle',
  'native_settings_sync_no_identity': 'syncNoIdentity',
  'native_settings_voice_section': 'sectionNavigationVoice',
  'native_settings_voice_guidance': 'voiceGuidance',
  'native_settings_voice_guidance_desc': 'voiceGuidanceDesc',
  'native_settings_voice_model': 'kokoroModelTitle',
  'native_settings_voice_not_downloaded': 'kokoroModelStatusNotDownloaded',
  'native_settings_voice_downloading': 'kokoroModelStatusDownloading',
  'native_settings_voice_ready': 'kokoroModelStatusReady',
  'native_settings_voice_download': 'kokoroModelDownloadBtn',
  'native_settings_voice_languages': 'kokoroModelSupportedLangs',
  'native_settings_voice_gender': 'kokoroVoiceGenderTitle',
  'native_settings_voice_female': 'kokoroVoiceFemale',
  'native_settings_voice_male': 'kokoroVoiceMale',
  'native_settings_voice_gender_unavailable': 'kokoroVoiceGenderUnavailable',
  'native_settings_voice_speed': 'kokoroSpeedTitle',
  'native_settings_voice_volume': 'kokoroVolumeTitle',
  'native_settings_info_section': 'sectionInfo',
  'native_settings_info_version': 'infoVersion',
  'native_settings_info_protocol': 'infoProtocol',
  'native_settings_info_maps': 'infoMaps',
  'native_settings_info_routing': 'infoRouting',
  'native_settings_info_source': 'infoSource',
  'native_settings_info_osrm': 'providerOsrm',
  'native_settings_info_gh_local': 'providerGraphhopperSelfHosted',
  'native_settings_info_gh_cloud': 'providerGraphhopperCloud',
  'native_settings_info_openroute': 'providerOpenroute',
  'native_settings_support': 'supportRoadstr',
  'native_settings_loading': 'loadingLabel',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  if (RegExp(r'\{[^}]+\}').hasMatch(source)) {
    throw FormatException('Unsupported placeholder in $sourceKey: $source');
  }
  return source
      .replaceAll(r'\', r'\\')
      .replaceAll('%', '%%')
      .replaceAll("'", r"\'")
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll('\n', r'\n')
      .replaceAll('\r', r'\r')
      .replaceAll('\t', r'\t');
}

// The overlays card holds the map's own elements (crosswalks, traffic lights,
// altitude), not only road ones: Kotlin-only wording, the Flutter .arb keeps the old text.
const _mapOverlaysTitle = <String, String>{
  'bg': 'Елементи на картата',
  'cs': 'Prvky mapy',
  'da': 'Kortelementer',
  'de': 'Kartenelemente',
  'el': 'Στοιχεία χάρτη',
  'en': 'Map element overlays',
  'es': 'Elementos del mapa',
  'et': 'Kaardi elemendid',
  'fi': 'Kartan elementit',
  'fr': 'Éléments de la carte',
  'ga': 'Eilimintí léarscáile',
  'hr': 'Elementi karte',
  'hu': 'Térképelemek',
  'it': 'Overlay di elementi della mappa',
  'ja': '地図要素',
  'lt': 'Žemėlapio elementai',
  'lv': 'Kartes elementi',
  'mt': 'Elementi tal-mappa',
  'nl': 'Kaartelementen',
  'pl': 'Elementy mapy',
  'pt': 'Elementos do mapa',
  'ro': 'Elemente de hartă',
  'ru': 'Элементы карты',
  'sk': 'Prvky mapy',
  'sl': 'Elementi zemljevida',
  'sv': 'Kartelement',
  'zh': '地图元素',
};

String _buildResource(
  String language,
  Map<String, dynamic> arb,
  Map<String, dynamic> fallback,
) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
      '<!-- Generated from lib/l10n/app_$language.arb. Do not edit. -->',
    )
    ..writeln('<resources>');
  for (final entry in _resourceKeys.entries) {
    final source = entry.key == 'native_settings_road_overlays'
        ? (_mapOverlaysTitle[language] ?? fallback[entry.value])
        : (arb[entry.value] ?? fallback[entry.value]);
    if (source is! String || source.isEmpty) {
      throw FormatException('Missing ${entry.value} in app_$language.arb');
    }
    output.writeln(
      '    <string name="${entry.key}">${_androidText(source, entry.value)}</string>',
    );
  }
  output.writeln('</resources>');
  return output.toString();
}

Map<String, String> buildAndroidSettingsResources() {
  final sources = Directory(_sourceDirectory)
      .listSync()
      .whereType<File>()
      .where((file) => RegExp(r'app_[a-z]{2}\.arb$').hasMatch(file.path))
      .toList()
    ..sort((left, right) => left.path.compareTo(right.path));
  if (sources.length != 27) {
    throw StateError('Expected 27 ARB locale files, found ${sources.length}');
  }
  final fallbackDecoded = jsonDecode(
    File('$_sourceDirectory/app_en.arb').readAsStringSync(),
  );
  if (fallbackDecoded is! Map<String, dynamic>) {
    throw const FormatException('app_en.arb is not a JSON object');
  }

  final outputs = <String, String>{};
  for (final source in sources) {
    final match = RegExp(r'app_([a-z]{2})\.arb$').firstMatch(source.path)!;
    final language = match.group(1)!;
    final decoded = jsonDecode(source.readAsStringSync());
    if (decoded is! Map<String, dynamic>) {
      throw FormatException('${source.path} is not a JSON object');
    }
    final path =
        '$_resourceDirectory/${_resourceFolder(language)}/$_outputName';
    outputs[path] = _buildResource(language, decoded, fallbackDecoded);
  }
  return outputs;
}

void main(List<String> arguments) {
  final check = arguments.contains('--check');
  var stale = false;
  for (final entry in buildAndroidSettingsResources().entries) {
    final output = File(entry.key);
    if (check) {
      if (!output.existsSync() || output.readAsStringSync() != entry.value) {
        stderr.writeln('${entry.key} is stale; regenerate it.');
        stale = true;
      }
      continue;
    }
    output.parent.createSync(recursive: true);
    output.writeAsStringSync(entry.value);
  }
  if (stale) exitCode = 1;
}
