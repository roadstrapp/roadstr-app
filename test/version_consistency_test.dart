import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The Android version is stated literally in build.gradle.kts so F-Droid's
/// `checkupdates` can read it from a bare clone without running Gradle. That
/// buys automatic update detection at the cost of a second place to bump —
/// and a version that silently disagrees with pubspec.yaml would ship an APK
/// whose number means nothing. This test is the thing that stops that.
void main() {
  final pubspec = File('pubspec.yaml').readAsStringSync();
  final gradle = File('android/app/build.gradle.kts').readAsStringSync();

  // pubspec: "version: 0.4.13+22"
  final pubspecMatch =
      RegExp(r'^version:\s*([0-9]+\.[0-9]+\.[0-9]+)\+([0-9]+)\s*$',
              multiLine: true)
          .firstMatch(pubspec);
  // The same regexes F-Droid applies: a literal number and a literal string.
  final codeMatch =
      RegExp(r'''\bversionCode\s*=\s*([0-9]+)''').firstMatch(gradle);
  final nameMatch =
      RegExp(r'''\bversionName\s*=\s*"([^"]+)"''').firstMatch(gradle);

  test('pubspec states a version in the expected shape', () {
    expect(pubspecMatch, isNotNull,
        reason: 'pubspec.yaml must carry "version: X.Y.Z+build"');
  });

  test('the Android version is readable without running Gradle', () {
    // If these ever go back to flutter.versionCode / flutter.versionName,
    // F-Droid stops being able to detect releases and fails with
    // "Couldn't find any version information".
    expect(codeMatch, isNotNull,
        reason: 'versionCode must be a literal integer in build.gradle.kts');
    expect(nameMatch, isNotNull,
        reason: 'versionName must be a literal string in build.gradle.kts');
  });

  test('pubspec and the Android build agree on the version', () {
    final name = pubspecMatch!.group(1);
    final code = pubspecMatch.group(2);
    expect(nameMatch!.group(1), name,
        reason: 'versionName in build.gradle.kts != pubspec version');
    expect(codeMatch!.group(1), code,
        reason: 'versionCode in build.gradle.kts != pubspec build number');
  });

  test('the F-Droid metadata agrees with both', () {
    final metadata = File('metadata/app.roadstr.yml').readAsStringSync();
    final name = pubspecMatch!.group(1);
    final code = pubspecMatch.group(2);
    expect(RegExp('^CurrentVersion: $name\$', multiLine: true).hasMatch(metadata),
        isTrue,
        reason: 'CurrentVersion must match pubspec');
    expect(
        RegExp('^CurrentVersionCode: $code\$', multiLine: true)
            .hasMatch(metadata),
        isTrue,
        reason: 'CurrentVersionCode must match pubspec');
    // The build entry and the tag it points at.
    expect(RegExp('versionName: $name').hasMatch(metadata), isTrue);
    expect(RegExp('versionCode: $code').hasMatch(metadata), isTrue);
    expect(RegExp(r'commit: v\.' + name!.replaceAll('.', r'\.'))
            .hasMatch(metadata),
        isTrue,
        reason: 'commit: must point at the v.X.Y.Z tag for this version');
  });

  test('the metadata filename matches the applicationId', () {
    // F-Droid keys metadata by applicationId: metadata/<applicationId>.yml.
    // A mismatch here is why an earlier submission was processed as
    // "org.roadstr.app", an id this app has never used.
    final applicationId =
        RegExp(r'applicationId\s*=\s*"([^"]+)"').firstMatch(gradle)?.group(1);
    expect(applicationId, 'app.roadstr');
    expect(File('metadata/$applicationId.yml').existsSync(), isTrue,
        reason: 'metadata file must be named after the applicationId');
  });
}
