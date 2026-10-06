import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _android = 'android/app/src/main/kotlin/app/roadstr';
const _native = 'native-android';

String _read(String path) => File(path).readAsStringSync();

String _code(String source) => source
    .replaceAll(RegExp(r'/\*[\s\S]*?\*/'), '')
    .split('\n')
    .where((line) => !line.trimLeft().startsWith('//'))
    .join('\n');

Iterable<File> _files(String dir, {String? extension}) => Directory(dir)
    .listSync(recursive: true)
    .whereType<File>()
    .where((file) => extension == null || file.path.endsWith(extension));

void main() {
  test('GeckoView exists only in the variant source set and the variant dependency', () {
    final sources = [
      ..._files('android/app/src', extension: '.kt'),
      ..._files('$_native/app/src/main', extension: '.kt'),
      ..._files('$_native/app/src/system', extension: '.kt'),
      // The manifest names Mozilla's clipboard provider on purpose, to remove it; see below.
      ..._files('$_native/app/src/main/res', extension: '.xml'),
    ];
    for (final file in sources) {
      expect(file.readAsStringSync(), isNot(contains('org.mozilla')), reason: file.path);
    }
    final gradle = _read('$_native/app/build.gradle.kts');
    final dependency = 'implementation("org.mozilla.geckoview:geckoview-arm64-v8a:\$geckoViewVersion")';
    expect(gradle, contains(dependency));
    // The only line that names the dependency sits inside `if (geckoView)`.
    final before = gradle.substring(0, gradle.indexOf(dependency));
    expect(before.lastIndexOf('if (geckoView) {'), greaterThan(before.lastIndexOf('}\n\ndependencies')));
    expect(gradle.split('org.mozilla.geckoview:').length - 1, 1);
  });

  test('no page can reach an app function: no script bridge, no script evaluation, one extension channel', () {
    for (final file in _files('$_native/app/src/gecko', extension: '.kt')) {
      final source = _code(file.readAsStringSync());
      for (final forbidden in ['evaluateJavascript', 'addJavascriptInterface', 'loadUri("javascript:', "loadUri('javascript:", 'executeJs', 'GeckoSession.NavigationDelegate.LoadRequest(']) {
        expect(source, isNot(contains(forbidden)), reason: '${file.path} $forbidden');
      }
    }
    final channels = _files('$_native/app/src/gecko', extension: '.kt')
        .where((file) => _code(file.readAsStringSync()).contains('setMessageDelegate'))
        .map((file) => file.path.split('/').last)
        .toList();
    expect(channels, ['GeckoPagePlaceExtractor.kt']);
  });

  test('the exported clipboard provider is removed from the merged manifest and checked by script', () {
    final manifest = _read('$_native/app/src/main/AndroidManifest.xml');
    expect(manifest, contains('android:name="org.mozilla.gecko.GeckoClipboardContentProvider"'));
    expect(manifest, contains('tools:node="remove"'));
    final script = _read('$_native/verify-geckoview-variant.sh');
    expect(script, contains('GeckoClipboardContentProvider'));
    expect(script, contains('no Google Play Services class defined'));
    for (final permission in ['CAMERA', 'RECORD_AUDIO']) {
      expect(manifest, isNot(contains('android.permission.$permission')), reason: permission);
    }
  });

  test('text from the web cannot disguise itself on screen', () {
    final policy = _read('$_android/core/discovery/RoadstrPlace.kt');
    // The Kotlin source holds these inside a regular expression string, so each backslash is doubled.
    expect(policy, contains(r'\\u202a-\\u202e'));
    expect(policy, contains(r'\\u2066-\\u2069'));
    expect(policy, contains('replace(invisible, "")'));
    final parser = _read('$_android/core/discovery/web/SearxngResponse.kt');
    expect(parser, contains('PlaceTagPolicy.clamp(plain.take(max))'));
  });

  test('the notices name what ships in the variant and what never ships', () {
    final notices = _read('THIRD_PARTY_NOTICES.md');
    expect(notices, contains('GeckoView'));
    expect(notices, contains('MPL-2.0'));
    expect(notices, contains('play-services-fido'));
    expect(notices, contains('no SearXNG code is linked, embedded or shipped'));
    expect(notices, contains('no instance is built in'));
    final readme = _read('README.md');
    for (final section in ['Place search in plain language', 'Web results are opt-in', 'gradlew-geckoview', 'verify-geckoview-variant.sh', 'SELF_HOSTING.md']) {
      expect(readme, contains(section), reason: section);
    }
  });

  test('the documents the README points to exist', () {
    for (final doc in ['PLAN.md', 'DECISIONS.md', 'LOCALIZATION_REVIEW.md', 'MEASUREMENTS.md', 'SELF_HOSTING.md']) {
      expect(File('docs/world-discovery/$doc').existsSync(), isTrue, reason: doc);
    }
    final decisions = _read('docs/world-discovery/DECISIONS.md');
    for (var n = 1; n <= 50; n++) {
      expect(decisions, contains('| D-${n.toString().padLeft(2, '0')} |'), reason: 'D-$n');
    }
  });

  test('the measurements keep the device results apart from what was only read off the builds', () {
    final measurements = _read('docs/world-discovery/MEASUREMENTS.md');
    for (final heading in ['## 1. Known from the builds', '## 3. Results on a device', '### In-place update, signed with the release key']) {
      expect(measurements, contains(heading), reason: heading);
    }
  });

  test('the update candidate is opt-in and leaves the default build identity alone', () {
    final gradle = _read('native-android/app/build.gradle.kts');
    expect(gradle, contains('providers.gradleProperty("candidate")'));
    expect(gradle, contains('The update candidate and the GeckoView variant are separate builds'));
    expect(gradle, contains('applicationId = "app.roadstr.roadtest"'));
  });

  test('the localisation review tells the truth about which languages have a full vocabulary', () {
    const full = ['en', 'it', 'de', 'fr', 'es', 'pt', 'nl'];
    const all = [
      'bg', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'fi', 'fr', 'ga', 'hr', 'hu', 'it', 'ja', 'lt', 'lv', 'mt', //
      'nl', 'pl', 'pt', 'ro', 'ru', 'sk', 'sl', 'sv', 'zh',
    ];
    for (final code in all) {
      final name = 'Lexicon${code[0].toUpperCase()}${code[1]}';
      final lines = _read('$_android/core/discovery/lexicon/$name.kt').split('\n').length;
      if (full.contains(code)) {
        expect(lines, greaterThan(80), reason: '$name is described as a full vocabulary');
      } else {
        expect(lines, lessThan(60), reason: '$name is described as a core vocabulary');
      }
    }
    final review = _read('docs/world-discovery/LOCALIZATION_REVIEW.md');
    expect(review, contains('| Full | en, it, de, fr, es, pt, nl |'));
    expect(review, contains('No language was reviewed by a native speaker.'));
  });
}
