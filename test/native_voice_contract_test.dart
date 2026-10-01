import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/migration/legacy_storage_contract.dart';
import 'package:roadstr/services/kokoro/kokoro_voices.dart';
import 'package:roadstr/services/piper/piper_voices.dart';
import 'package:roadstr/services/voice_engine_languages.dart';

void main() {
  final catalog = File(
    'android/app/src/main/kotlin/app/roadstr/feature/voice/'
    'NativeVoiceCatalog.kt',
  ).readAsStringSync();
  final inference = File(
    'android/app/src/main/kotlin/app/roadstr/feature/voice/'
    'NativeVoiceInferencePolicy.kt',
  ).readAsStringSync();
  final guidance = File(
    'android/app/src/main/kotlin/app/roadstr/feature/voice/'
    'NativeVoiceGuidanceSession.kt',
  ).readAsStringSync();
  final focus = File(
    'android/app/src/main/kotlin/app/roadstr/feature/voice/'
    'NativeVoiceAudioFocusController.kt',
  ).readAsStringSync();

  test('native voice catalogue matches every reusable Flutter asset', () {
    final nativePaths = RegExp(r'relativePath = "([^"]+)"')
        .allMatches(catalog)
        .map((match) => match.group(1)!)
        .toSet()
      ..remove(r'kokoro/$voice.bin')
      ..addAll(kKokoroVoiceSha256.keys.map((voice) => 'kokoro/$voice.bin'))
      ..add('espeak-ng-data/.roadstr_extracted');

    expect(nativePaths, legacyVoiceAssetRelativePaths);
    expect(nativePaths, hasLength(18));
    expect(catalog, contains(kKokoroRevision));
    expect(catalog, contains(kPiperRevision));
    final numericCatalog = catalog.replaceAll('_', '');
    expect(numericCatalog, contains('$kKokoroModelSizeBytes'));
    expect(numericCatalog, contains('$kPiperModelSizeBytes'));
    expect(catalog, contains(kKokoroModelSha256));
    expect(catalog, contains(kPiperModelSha256));
    for (final entry in kKokoroVoiceSha256.entries) {
      expect(catalog, contains(entry.key));
      expect(catalog, contains(entry.value));
    }
  });

  test('language gender speed and engine policies stay wire-compatible', () {
    for (final language in voiceGuidanceLanguages) {
      expect(catalog, contains('"$language"'));
    }
    expect(voiceGuidanceLanguages, hasLength(8));
    expect(catalog, contains('listOf(0.7, 0.85, 1.0, 1.15, 1.3, 1.5)'));
    expect(catalog, contains('const val DEFAULT_SPEED_STAGE = 4'));
    expect(catalog, contains('PIPER_SAMPLE_RATE_HZ = 22_050'));
    expect(catalog, contains('KOKORO_SAMPLE_RATE_HZ = 24_000'));
    expect(catalog, contains('requestedGender.takeIf(voices::containsKey)'));
  });

  test('inference and guidance retain Flutter safety bounds', () {
    expect(inference, contains('KOKORO_MAX_PHONEMES = 510'));
    expect(inference, contains('MAX_PHONEMIZER_TEXT_CHARS = 1_000'));
    expect(inference, contains('MAX_PHONEMIZER_WORD_CHARS = 60'));
    expect(inference, contains('MAX_PHONEME_OUTPUT_CHARS = 16_000'));
    expect(inference, contains('PIPER_NOISE_SCALE = 0.667f'));
    expect(guidance, contains('MIN_AUDIBLE_MILLIS = 1_300L'));
    expect(guidance, contains('MAX_UTTERANCE_WAIT_MILLIS = 25_000L'));
    expect(guidance, contains('MANEUVER_DEDUP_MILLIS = 12_000L'));
    expect(guidance, contains('AMBIENT_DEDUP_MILLIS = 8_000L'));
    expect(guidance, contains('MAX_PENDING = 2'));
  });

  test('audio focus is native navigation guidance and remains dormant', () {
    final shell = File(
      'android/app/src/main/kotlin/app/roadstr/feature/home/'
      'NativeRoadstrShell.kt',
    ).readAsStringSync();

    expect(focus, contains('USAGE_ASSISTANCE_NAVIGATION_GUIDANCE'));
    expect(focus, contains('CONTENT_TYPE_SPEECH'));
    expect(focus, contains('AUDIOFOCUS_GAIN_TRANSIENT'));
    expect(focus, contains('setAcceptsDelayedFocusGain(true)'));
    expect(focus, contains('setWillPauseWhenDucked(false)'));
    expect(shell, isNot(contains('NativeVoiceAudioFocusController')));
    expect(shell, isNot(contains('NativeVoiceAssetDownloader')));
    expect(shell, isNot(contains('NativeVoiceGuidanceSession')));
  });
}
