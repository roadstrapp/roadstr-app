import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_onboarding_strings.dart';
import '../tools/kotlin_rewrite/native_recovery_translations.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/onboarding/'
    'NativeOnboardingPresentation.kt',
  );
  final flow = File(
    'android/app/src/main/kotlin/app/roadstr/feature/onboarding/'
    'NativeOnboardingFlow.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 onboarding resource sets are current and complete', () {
    final generated = buildAndroidOnboardingResources();

    expect(generated, hasLength(27));
    expect(
      generated.keys.where((path) => path.contains('/values/')),
      hasLength(1),
    );
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = RegExp(
        r'<string name="([^"]+)">([\s\S]*?)</string>',
      ).allMatches(entry.value).toList();
      expect(strings, hasLength(59), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(59));
    }
  });

  test(
      'the startup screens are translated, not left in English, in all 27 languages',
      () {
    final english = buildAndroidOnboardingResources()
        .entries
        .firstWhere((entry) => entry.key.contains('/values/'))
        .value;
    final englishStrings = RegExp(r'<string name="([^"]+)">([\s\S]*?)</string>')
        .allMatches(english)
        .toList();
    expect(_value(englishStrings, 'native_onboarding_recovery_title'),
        'Protected data unavailable');
    expect(_value(englishStrings, 'native_onboarding_recovery_body'),
        contains('did not open or erase your saved locations'));
    expect(_value(englishStrings, 'native_onboarding_migration_retry'),
        'Try again');
    expect(_value(englishStrings, 'native_onboarding_migration_skip'),
        'Continue without importing');

    for (final entry in buildAndroidOnboardingResources().entries) {
      if (entry.key.contains('/values/')) continue;
      final strings = RegExp(r'<string name="([^"]+)">([\s\S]*?)</string>')
          .allMatches(entry.value)
          .toList();
      for (final key in nativeRecoveryKeys) {
        expect(_value(strings, key), isNot(_value(englishStrings, key)),
            reason: '${entry.key} $key is still English');
      }
    }
  });

  test(
      'the recovery screen offers a retry and a way on, and the flow only shows them when asked',
      () {
    final source = flow.readAsStringSync();

    expect(source, contains('onRetryMigration: (() -> Unit)? = null'));
    expect(source, contains('onSkipMigration: (() -> Unit)? = null'));
    expect(source,
        contains('if (onRetryMigration != null && onSkipMigration != null)'));
    expect(source, contains('R.string.native_onboarding_migration_retry'));
    expect(source, contains('R.string.native_onboarding_migration_skip'));
    // Without the callbacks the original recovery message is unchanged.
    expect(source, contains('R.string.native_onboarding_recovery_title'));
    final activity = File(
      'android/app/src/main/kotlin/app/roadstr/startup/NativeAppActivity.kt',
    ).readAsStringSync();
    expect(activity, contains('onRetryMigration = startup::retry'));
    expect(activity, contains('onSkipMigration = startup::skip'));
  });

  test('native startup gate preserves the versioned Flutter disclosure oracle',
      () {
    final flutter = File('lib/main.dart').readAsStringSync();
    final native = presentation.readAsStringSync();

    expect(
      flutter,
      contains("box.get('privacy_disclosure_v2', defaultValue: false) as bool"),
    );
    expect(flutter, contains("'disclaimer_accepted': true"));
    expect(flutter, contains("'onboarding_v1': true"));
    expect(flutter, contains("'privacy_disclosure_v2': true"));
    expect(native, contains('input.privacyDisclosureV2 is Boolean'));
    expect(native, contains('&& input.privacyDisclosureV2'));
    expect(native, contains('!input.protectedStorageAvailable'));
    expect(native, contains('NativeMigrationReadiness.Failed'));
    expect(native, contains('LEGACY_DISCLAIMER_KEY to true'));
    expect(native, contains('LEGACY_ONBOARDING_KEY to true'));
    expect(native, contains('PRIVACY_DISCLOSURE_KEY to true'));
    expect(native, isNot(contains('legacyDisclosureAccepted')));
    expect(native, isNot(contains('legacyOnboardingComplete')));
  });

  test('Compose onboarding is swipeable accessible and cannot dismiss consent',
      () {
    final source = flow.readAsStringSync();

    expect(source, contains('HorizontalPager('));
    expect(source, contains('systemBarsPadding()'));
    expect(source, contains('paneTitle ='));
    expect(source, contains('liveRegion = LiveRegionMode.Assertive'));
    expect(source, contains('minHeight = 52.dp'));
    expect(source, contains('DialogProperties('));
    expect(source, contains('dismissOnBackPress = false'));
    expect(source, contains('dismissOnClickOutside = false'));
    expect(source, contains('onDismissRequest = {}'));
    expect(source, contains('onAmberLogin'));
    expect(source, contains('onNsecLogin'));
    expect(source, contains('onRequestLocation'));
    expect(source, contains('onDownloadVoice'));
    expect(source, isNot(contains('FlutterSecureStorage')));
    expect(source, isNot(contains('Amberflutter')));
    expect(source, isNot(contains('Geolocator')));
    expect(source, isNot(contains('KokoroModelManager')));
    expect(source, isNot(contains('NostrRelayService')));
    expect(source, isNot(contains('Hive.')));
  });

  test(
      'onboarding is drawn with the same cards, sections and buttons as the settings',
      () {
    final source = flow.readAsStringSync();

    expect(
        source, contains('import app.roadstr.feature.settings.SettingsCard'));
    expect(source,
        contains('import app.roadstr.feature.settings.SettingsSection'));
    expect(
        source, contains('import app.roadstr.feature.settings.ActionButton'));
    expect(
        source,
        contains(
            'SettingsSection(R.string.native_onboarding_profile_visibility_title)'));
    // The blocks the pages are made of are settings cards, not a second look.
    for (final block in ['InfoCard', 'ChoiceCard', 'SetupCard']) {
      final start = source.indexOf('private fun $block(');
      expect(start, greaterThan(0), reason: block);
      final body = source.substring(start, source.indexOf('\n}\n', start));
      expect(body, contains('SettingsCard'), reason: block);
      expect(body, isNot(contains('RoadstrGlassBox')), reason: block);
    }
    // The same edges as the settings sections: 12 dp around the cards.
    expect(source, contains('start = 12.dp'));
    expect(source, contains('end = 12.dp'));
  });

  test('private shell packages onboarding without owning user data', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeOnboardingSession()'));
    expect(source, contains('NativeOnboardingFlow('));
    expect(source, isNot(contains('Hive.')));
    expect(source, isNot(contains('FlutterSecureStorage')));
  });
}

String? _value(List<RegExpMatch> values, String name) =>
    values.singleWhere((match) => match.group(1) == name).group(2);
