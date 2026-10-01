import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final preferenceStore = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativePreferenceStore.kt',
  );
  final snapshotStore = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativeSnapshotStore.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native secret alias promotion matches Flutter precedence', () {
    final flutter = File('lib/screens/settings_screen.dart').readAsStringSync();
    final native = snapshotStore.readAsStringSync();

    for (final oracle in [
      "if (apiKey == null)",
      "_box.get('graphhopperApiKey', defaultValue: '')",
      "if (nwcUri == null)",
      "_box.get('nwcUri', defaultValue: '')",
      "_syncPassphrase == null && legacyPassphrase?.isNotEmpty == true",
      "_box.get('fav_sync_pass')",
    ]) {
      expect(flutter, contains(oracle));
    }
    for (final mapping in [
      '"graphhopperApiKey" to "routing_api_key"',
      '"nwcUri" to "nwc_uri"',
      '"fav_sync_pass" to "favorites_sync_passphrase"',
    ]) {
      expect(native, contains(mapping));
    }
    expect(native, contains('protectedKey !in protected'));
    expect(native, contains('takeIf(String::isNotEmpty)'));
    expect(native, contains('it !in NativeLegacySecretAliases.legacyKeys'));
  });

  test('native preference schema is scalar closed and excludes secrets', () {
    final source = preferenceStore.readAsStringSync();
    final settings = File(
      'android/app/src/main/kotlin/app/roadstr/feature/settings/'
      'NativeSettingsPresentation.kt',
    ).readAsStringSync();

    expect(source, contains('NativeSettingsBooleanKey.entries'));
    expect(settings, contains('AutoDark("autoDark", false)'));
    expect(settings, contains('KeepScreenOn("keepScreenOn", true)'));
    expect(settings, contains('VoiceEnabled("voiceEnabled", true)'));
    for (final key in [
      'disclaimer_accepted',
      'fav_sync_last_ts',
      'favoritesSyncLastAt',
      'graphhopperServer',
      'kokoroSpeedStage',
      'kokoroVolume',
      'language',
      'mapEngine',
      'mapTileUrl',
      'minBrightness',
      'routingProvider',
      'searchEngine',
      'themeId',
    ]) {
      expect(source, contains('"$key"'), reason: key);
    }
    for (final excluded in [
      'graphhopperApiKey',
      'nwcUri',
      'fav_sync_pass',
      'routing_api_key',
      'nwc_uri',
      'favorites_sync_passphrase',
      'favorites',
      'searchHistory',
      'parking_position',
      'pending_road_reports',
      'activity_inbox_',
    ]) {
      expect(source, isNot(contains('"$excluded"')), reason: excluded);
    }
  });

  test('native preference bytes are integrity checked and atomically replaced',
      () {
    final source = preferenceStore.readAsStringSync();

    expect(source, contains('"RSTRPRF1"'));
    expect(source, contains('MessageDigest.getInstance("SHA-256")'));
    expect(source, contains('MessageDigest.isEqual(expected, actual)'));
    expect(source, contains('RecoverableAtomicFile('));
    expect(source, contains('file.stage(encoded)'));
    expect(source, contains('file.commit()'));
    expect(source, contains('if (reopened != record)'));
    expect(source, contains('NativePreferenceSchema.validate('));
    expect(source, isNot(contains('SharedPreferences')));
    expect(source, isNot(contains('Hive')));
    expect(source, isNot(contains('NativeSecretStore')));
  });

  test('native preference ownership remains dormant before cutover', () {
    final shellSource = shell.readAsStringSync();
    final activitySource = File(
      'android/app/src/main/kotlin/app/roadstr/MainActivity.kt',
    ).readAsStringSync();

    expect(shellSource, isNot(contains('FileNativePreferenceStore')));
    expect(activitySource, isNot(contains('FileNativePreferenceStore')));
    expect(activitySource, isNot(contains('NATIVE_PREFERENCE_FILE')));
  });
}
