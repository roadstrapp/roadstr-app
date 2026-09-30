import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_profile_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/profile/'
    'NativeProfilePresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/profile/'
    'NativeProfilePanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 profile resource sets are current and complete', () {
    final generated = buildAndroidProfileResources();

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
      expect(strings, hasLength(30), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(30));
      expect(_value(strings, 'native_profile_reputation'), contains(r'%1$s'));
    }
  });

  test('native projection locks Flutter identity privacy and score behavior',
      () {
    final flutter = File('lib/screens/profile_screen.dart').readAsStringSync();
    final native = presentation.readAsStringSync();

    expect(flutter, contains('Future.wait(['));
    expect(flutter, contains('up / total'));
    expect(flutter, contains('balMsat ~/ 1000'));
    expect(flutter, contains("static const _kPriv = 'nostr_priv_hex'"));
    expect(native, contains('const val MAX_FETCHED_REPORTS = 500'));
    expect(native, contains('const val MAX_REPORTS = 100'));
    expect(native, contains('score >= 0.67'));
    expect(native, contains('score >= 0.34'));
    expect(native, contains('input.balanceMsat?.div(1_000)'));
    expect(native, contains('NostrNip19.encodePublicKey'));
    expect(native, isNot(contains('privateKey')));
    expect(native, isNot(contains('nsec: String')));
  });

  test('Compose profile is bounded accessible and owns no live integration',
      () {
    final source = panel.readAsStringSync();

    expect(source, contains('LazyColumn'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle ='));
    expect(source, contains('minHeight = 48.dp'));
    expect(source, contains('Role.Button'));
    expect(source, contains('LiveRegionMode.Polite'));
    expect(source, contains('onAmberLogin'));
    expect(source, contains('onNsecLogin'));
    expect(source, contains('onReportSelected'));
    expect(source, isNot(contains('FlutterSecureStorage')));
    expect(source, isNot(contains('Amberflutter')));
    expect(source, isNot(contains('NostrRelayService')));
    expect(source, isNot(contains('ZapService')));
    expect(source, isNot(contains('Image.network')));
    expect(source, isNot(contains('Clipboard')));
  });

  test('private shell packages profile state without opening or feeding it',
      () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeProfileSession()'));
    expect(source, contains('NativeProfilePanel('));
    expect(source, isNot(contains('profileSession.begin(')));
    expect(source, isNot(contains('profileSession.showProfile(')));
    expect(source, isNot(contains('profileSession.showLoggedOut(')));
  });
}

String? _value(List<RegExpMatch> values, String name) =>
    values.singleWhere((match) => match.group(1) == name).group(2);
