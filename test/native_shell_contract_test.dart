import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final manifest = File('android/app/src/main/AndroidManifest.xml');
  final appGradle = File('android/app/build.gradle.kts');
  final settingsGradle = File('android/settings.gradle.kts');

  test('Compose shell keeps the existing package and SDK baseline', () {
    final appBuild = appGradle.readAsStringSync();
    final settings = settingsGradle.readAsStringSync();

    expect(appBuild, contains('applicationId = "app.roadstr"'));
    expect(appBuild, contains('namespace = "app.roadstr"'));
    expect(appBuild, contains('compileSdk = 36'));
    expect(appBuild, contains('compose = true'));
    expect(appBuild, contains('androidx.compose:compose-bom:2026.06.01'));
    expect(appBuild, contains('androidx.activity:activity-compose:1.13.0'));
    expect(appBuild, contains('androidx.compose.material3:material3'));
    expect(
      settings,
      contains('id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"'),
    );
  });

  test('Flutter stays the only launcher and native shell is private', () {
    final xml = manifest.readAsStringSync();
    final launcher = RegExp(
      r'<activity\s+android:name="\.MainActivity"[\s\S]*?</activity>',
    ).firstMatch(xml);
    final canary = RegExp(
      r'<activity\s+android:name="\.feature\.home\.NativeCanaryActivity"'
      r'[\s\S]*?/>',
    ).firstMatch(xml);

    expect(launcher, isNotNull);
    expect(launcher!.group(0), contains('android.intent.action.MAIN'));
    expect(launcher.group(0), contains('android.intent.category.LAUNCHER'));
    expect(
      RegExp(r'android.intent.action.MAIN').allMatches(xml),
      hasLength(1),
    );
    expect(canary, isNotNull);
    expect(canary!.group(0), contains('android:exported="false"'));
    expect(canary.group(0), contains('android:excludeFromRecents="true"'));
    expect(canary.group(0), isNot(contains('intent-filter')));
    expect(xml, contains('android:name="\${applicationName}"'));
    expect(xml, contains('android:allowBackup="false"'));
    expect(xml, contains('android:usesCleartextTraffic="false"'));
  });
}
