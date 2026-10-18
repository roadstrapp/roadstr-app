import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';

const _main = 'android/app/src/main';
const _kotlin = '$_main/kotlin/app/roadstr';
const _vendor = 'third_party/offline-stt-engine';

String _read(String path) => File(path).readAsStringSync();

String _sha256(String path) => sha256.convert(File(path).readAsBytesSync()).toString();

/// `sha256  path` lines of the vendoring record, as a map from path to digest.
Map<String, String> _recorded() {
  final result = <String, String>{};
  for (final line in _read('$_vendor/SHA256SUMS').trim().split('\n')) {
    final parts = line.split(RegExp(r'\s+'));
    expect(parts, hasLength(2), reason: line);
    result[parts[1]] = parts[0];
  }
  return result;
}

void main() {
  test('every vendored engine file is exactly what the record says', () {
    final recorded = _recorded();
    expect(recorded.keys.where((path) => path.endsWith('.so')), hasLength(4));
    expect(recorded.keys.where((path) => path.endsWith('.kt')), hasLength(3));
    recorded.forEach((path, digest) {
      expect(_sha256('$_main/$path'), digest, reason: path);
    });
  });

  test('no native file or Kotlin source of the engine escapes the record', () {
    final recorded = _recorded().keys.toSet();
    final libs = Directory('$_main/jniLibs/arm64-v8a')
        .listSync()
        .whereType<File>()
        .map((f) => 'jniLibs/arm64-v8a/${f.uri.pathSegments.last}')
        .where((p) => p.contains('offlinestt') || p.endsWith('libc++_shared.so'));
    final sources = Directory('$_kotlin/offlinestt')
        .listSync()
        .whereType<File>()
        .map((f) => 'kotlin/app/roadstr/offlinestt/${f.uri.pathSegments.last}');
    for (final path in [...libs, ...sources]) {
      expect(recorded, contains(path), reason: path);
    }
  });

  test('the Gradle build checks the same digests before it packages anything', () {
    final gradle = _read('android/app/build.gradle.kts');
    _recorded().forEach((path, digest) {
      if (!path.endsWith('.so')) return;
      expect(gradle, contains('src/main/$path'), reason: path);
      expect(gradle, contains(digest), reason: path);
    });
    expect(gradle, contains('verifyBundledNativeAssets'));
  });

  test('the engine ships for arm64 only, so no other ABI carries a half engine', () {
    for (final abi in ['armeabi-v7a', 'x86_64', 'x86']) {
      final dir = Directory('$_main/jniLibs/$abi');
      if (!dir.existsSync()) continue;
      final names = dir.listSync().map((e) => e.uri.pathSegments.last);
      expect(names.where((n) => n.contains('offlinestt')), isEmpty, reason: abi);
    }
  });

  test('the pinned source commit is a full hash that the README links to', () {
    final commit = _read('$_vendor/SOURCE_COMMIT').trim();
    expect(commit, matches(RegExp(r'^[0-9a-f]{40}$')));
    expect(_read('$_vendor/README.md'), contains(commit));
  });

  test('the model contract is the same in the code, the README and the notices', () {
    final catalog = _read('$_kotlin/core/speech/SpeechModelCatalog.kt');
    final readme = _read('$_vendor/README.md');
    final size = RegExp(r'byteSize = ([0-9_]+)L').firstMatch(catalog)!.group(1)!.replaceAll('_', '');
    final digest = RegExp(r'sha256 = "([0-9a-f]{64})"').firstMatch(catalog)!.group(1)!;
    // The URL is split over two string literals: the commit opens the second one.
    final commit = RegExp(r'"([0-9a-f]{40})/').firstMatch(catalog)!.group(1)!;
    expect(readme, contains(size));
    expect(readme, contains(digest));
    expect(readme, contains(commit));
    expect(catalog, contains('CC-BY-4.0'));
    final notices = _read('THIRD_PARTY_NOTICES.md');
    expect(notices, contains('Parakeet TDT 0.6B v3'));
    expect(notices, contains('CC-BY-4.0'));
    expect(notices, contains('transcribe.cpp'));
    expect(notices, contains('libofflinestt'));
  });

  test('the microphone permission is declared by the app and by nothing else', () {
    final app = _read('$_main/AndroidManifest.xml');
    expect(app, contains('android.permission.RECORD_AUDIO'));
    // Optional hardware: a phone without a microphone can still install Roadstr.
    expect(app, contains('android.hardware.microphone" android:required="false"'));
    expect(_read('native-android/app/src/main/AndroidManifest.xml'), isNot(contains('RECORD_AUDIO')));
  });

  test('the microphone is only opened by the speech source, never by feature code', () {
    final users = Directory('$_kotlin')
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.kt') && f.readAsStringSync().contains('AudioRecord('))
        .map((f) => f.uri.pathSegments.last)
        .toList();
    expect(users, ['AudioRecordPcmSource.kt']);
  });

  test('the speech model is fetched only from the pinned HTTPS address, with a size and digest', () {
    final store = _read('$_kotlin/service/speech/SpeechModelStore.kt');
    expect(store, contains('spec.sha256'));
    expect(store, contains('spec.byteSize'));
    final transport = _read('$_kotlin/service/speech/OkHttpSpeechModelTransport.kt');
    expect(transport, contains('followSslRedirects(false)'));
    expect(_read('$_kotlin/core/speech/SpeechModelCatalog.kt'), contains('startsWith("https://")'));
  });

  test('the licence text stays in English in every language and the user sees it', () {
    final strings = _read('$_main/res/values/native_speech_strings.xml');
    expect(strings, contains('name="native_speech_attribution" translatable="false"'));
    expect(strings, contains('CC-BY-4.0'));
    expect(_read('$_kotlin/feature/settings/NativeSettingsPanel.kt'), contains('R.string.native_speech_attribution'));
  });

  test('the privacy promise of the settings text matches what the code does', () {
    final it = _read('$_main/res/values-it/native_speech_strings.xml');
    final en = _read('$_main/res/values/native_speech_strings.xml');
    for (final text in [en, it]) {
      expect(text, contains('huggingface.co'));
    }
    // No audio ever reaches a network class: the recorder and the engine know nothing of OkHttp.
    for (final file in ['AudioRecordPcmSource.kt', 'OfflineSttSpeechEngine.kt', 'SpeechRecognition.kt']) {
      expect(_read('$_kotlin/service/speech/$file'), isNot(contains('okhttp3')), reason: file);
    }
    // The street-name request carries a place and nothing else: no text, no audio.
    final gazetteer = _read('$_kotlin/service/speech/StreetGazetteer.kt');
    expect(gazetteer, contains('suspend fun load(point: GeoPoint)'));
    expect(gazetteer, contains('cell.centre'));
    expect(_read('$_kotlin/service/speech/AddressSpeechRefiner.kt'), contains('gazetteer.load(point)'));
    for (final file in Directory('$_kotlin/offlinestt').listSync().whereType<File>()) {
      expect(file.readAsStringSync(), isNot(contains('java.net')), reason: file.path);
    }
  });
}
