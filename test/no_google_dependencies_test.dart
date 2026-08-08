import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Roadstr ships no proprietary Google libraries. That is not a style
/// preference: the app is built to work on devices with no Google services at
/// all, and shipping Play Services code it can never execute made the APK both
/// heavier and dishonest about what it depends on.
///
/// The dependency came back once already — pulled in transitively by a plugin
/// nobody thought to check — so it is checked here rather than remembered.
void main() {
  final gms = RegExp(r'com\.google\.android\.(gms|play)');

  test('the vendored geolocator has no Play Services left in it', () {
    final root = Directory('third_party/geolocator_android');
    expect(root.existsSync(), isTrue,
        reason: 'the de-Googled geolocator fork must be present');

    final offenders = <String>[];
    for (final entity in root.listSync(recursive: true)) {
      if (entity is! File) continue;
      final path = entity.path;
      // Tests and changelogs may name the library; only shipped code counts.
      if (path.contains('/test/') ||
          path.endsWith('CHANGELOG.md') ||
          path.endsWith('ROADSTR_FORK.md')) {
        continue;
      }
      if (!path.endsWith('.java') &&
          !path.endsWith('.kt') &&
          !path.endsWith('.gradle')) {
        continue;
      }
      if (gms.hasMatch(entity.readAsStringSync())) offenders.add(path);
    }
    expect(offenders, isEmpty,
        reason: 'Play Services reappeared in the vendored plugin');
  });

  test('the fused client is gone, the LocationManager one remains', () {
    const dir =
        'third_party/geolocator_android/android/src/main/java/com/baseflow/geolocator/location';
    expect(File('$dir/FusedLocationClient.java').existsSync(), isFalse,
        reason: 'the Play Services client must not come back');
    expect(File('$dir/LocationManagerClient.java').existsSync(), isTrue,
        reason: 'the AOSP client is the only one Roadstr uses');
  });

  test('the app builds against the vendored fork, not pub.dev', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    expect(
      RegExp(r'dependency_overrides:[\s\S]*geolocator_android:\s*\n\s*path:\s*'
              r'third_party/geolocator_android')
          .hasMatch(pubspec),
      isTrue,
      reason: 'without the override, pub silently restores the upstream plugin',
    );
  });

  test('the build excludes the Google groups outright', () {
    // Belt and braces: even if some future dependency asks for them, Gradle
    // refuses rather than quietly packaging them.
    final gradle = File('android/build.gradle.kts').readAsStringSync();
    expect(gradle.contains('exclude(group = "com.google.android.gms")'), isTrue);
    expect(
        gradle.contains('exclude(group = "com.google.android.play")'), isTrue);
  });

  test('every location call still forces the AOSP provider', () {
    // The fork makes this the only possible behaviour, but the app should keep
    // saying so: it documents the intent, and it keeps working if the fork is
    // ever swapped back for the upstream plugin.
    final gps = File('lib/services/gps_service.dart').readAsStringSync();
    expect(RegExp(r'forceLocationManager:\s*true').allMatches(gps).length,
        greaterThanOrEqualTo(2));
    expect(gps.contains('forceAndroidLocationManager: true'), isTrue);
  });
}
