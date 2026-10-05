import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_settings_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/settings/'
    'NativeSettingsPresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/settings/'
    'NativeSettingsPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 settings resource sets are current and complete', () {
    final generated = buildAndroidSettingsResources();

    expect(generated, hasLength(27));
    expect(
      generated.keys.where((path) => path.contains('/values/')),
      hasLength(1),
    );
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final names = RegExp(r'<string name="([^"]+)">')
          .allMatches(entry.value)
          .map((match) => match.group(1))
          .toList();
      expect(names, hasLength(118), reason: entry.key);
      expect(names.toSet(), hasLength(118), reason: entry.key);
    }
  });

  test('native settings lock Flutter keys defaults and choice catalogues', () {
    final flutter = File('lib/screens/settings_screen.dart').readAsStringSync();
    final native = presentation.readAsStringSync();

    for (final oracle in [
      "_getBool('avoidUnpavedRoads', false)",
      "_getBool('keepScreenOn', true)",
      "_getBool('keepScreenOnAlways', false)",
      "_getBool('showAltitude', false)",
      "_getBool('showCrosswalks', true)",
      "_getBool('showTrafficLights', true)",
      "_getBool('autoCenterOnLaunch', true)",
      "_getBool('imperialUnits', false)",
      "_getBool('favoritesSyncAutoEnabled', false)",
      "_getBool('voiceEnabled', true)",
    ]) {
      expect(flutter, contains(oracle));
    }
    expect(native, contains('const val MAX_FAVORITES = 1_000'));
    expect(native, contains('"graphhopper_public"'));
    expect(native, contains('"movementCursorStyle"'));
    expect(native, contains('"kokoroSpeedStage"'));
    expect(native, contains('listOf(0.7, 0.85, 1.0, 1.15, 1.3, 1.5)'));
    expect(native, isNot(contains('nwcUri: String')));
    expect(native, isNot(contains('apiKey: String')));
    expect(native, isNot(contains('passphrase: String')));
  });

  test('Compose settings catalogue is bounded accessible and callback-only',
      () {
    final source = panel.readAsStringSync();

    expect(source, contains('LazyColumn'));
    // An expanded section stacks its rows: AnimatedVisibility alone would draw
    // the crosswalk and traffic-light switches on top of each other.
    expect(source, contains('Column { content() }'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle ='));
    expect(source, contains('minHeight = 48.dp'));
    expect(source, contains('Role.Button'));
    expect(source, contains('LiveRegionMode.Polite'));
    expect(source, contains('NativeSettingsUiAction.ConfigureNwc'));
    expect(source, contains('NativeSettingsUiAction.DownloadVoiceModel'));
    expect(source, isNot(contains('Hive')));
    expect(source, isNot(contains('FlutterSecureStorage')));
    expect(source, isNot(contains('RoutingService')));
    expect(source, isNot(contains('FavoritesSyncService')));
    expect(source, isNot(contains('KokoroModelManager')));
    expect(source, isNot(contains('launchUrl')));
    expect(source, isNot(contains('Clipboard')));
  });

  test('private shell feeds only the road-test voice settings owner', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeSettingsSession('));
    expect(source, contains('NativeSettingsPanel('));
    expect(source, contains('settingsSession.reopen('));
    expect(source, contains('settingsSession.refresh('));
    expect(source, contains('voiceGateway?.downloadAssets()'));
    expect(source, contains('voiceModelStatus = when'));
    expect(source, isNot(contains('FlutterSecureStorage')));
    expect(source, isNot(contains("Hive.box('settings')")));
  });
}
