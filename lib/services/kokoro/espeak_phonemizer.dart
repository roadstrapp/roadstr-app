import 'dart:ffi';
import 'dart:io';
import 'package:ffi/ffi.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path_provider/path_provider.dart';
import 'package:archive/archive.dart';

// ── Native function signatures ────────────────────────────────────────────────

typedef _EspeakInitNative = Int32 Function(Int32, Int32, Pointer<Utf8>, Int32);
typedef _EspeakInitDart = int Function(int, int, Pointer<Utf8>, int);

typedef _EspeakSetVoiceNative = Int32 Function(Pointer<Utf8>);
typedef _EspeakSetVoiceDart = int Function(Pointer<Utf8>);

typedef _EspeakTextToPhonemesNative = Pointer<Utf8> Function(
    Pointer<Pointer<Void>>, Int32, Int32);
typedef _EspeakTextToPhonemesDart = Pointer<Utf8> Function(
    Pointer<Pointer<Void>>, int, int);

// ── eSpeak NG constants (speak_lib.h) ────────────────────────────────────────

const int _kAudioOutputSynchronous = 2; // AUDIO_OUTPUT_SYNCHRONOUS
const int _kCharsUtf8 = 1;             // espeakCHARS_UTF8
const int _kPhonemesIpa = 2;           // espeakPHONEMES_IPA

// ── Public API ────────────────────────────────────────────────────────────────

/// Singleton G2P (grapheme-to-phoneme) wrapper around the bundled eSpeak NG
/// native library.  Converts navigation instruction text to IPA for subsequent
/// Kokoro neural synthesis.
///
/// Call [init] once (or lazily via [phonemize]) before using. The first call
/// extracts the bundled `espeak-ng-data.tar.gz` asset to the app documents
/// directory — takes ~200 ms; all subsequent calls are instant (sentinel check).
class EspeakPhonemizer {
  EspeakPhonemizer._();
  static final EspeakPhonemizer instance = EspeakPhonemizer._();

  bool _ready = false;
  late _EspeakSetVoiceDart _setVoice;
  late _EspeakTextToPhonemesDart _textToPhonemes;
  String? _currentVoice;

  Future<void> init() async {
    if (_ready) return;

    final dataParent = await _ensureDataExtracted();
    final lib = DynamicLibrary.open('libespeak-ng.so');

    final espeakInit =
        lib.lookupFunction<_EspeakInitNative, _EspeakInitDart>('espeak_Initialize');
    _setVoice =
        lib.lookupFunction<_EspeakSetVoiceNative, _EspeakSetVoiceDart>(
            'espeak_SetVoiceByName');
    _textToPhonemes =
        lib.lookupFunction<_EspeakTextToPhonemesNative, _EspeakTextToPhonemesDart>(
            'espeak_TextToPhonemes');

    // espeak_Initialize looks for <path>/espeak-ng-data/ automatically.
    final pathPtr = dataParent.toNativeUtf8();
    try {
      final sampleRate = espeakInit(_kAudioOutputSynchronous, 0, pathPtr, 0);
      if (sampleRate < 0) {
        throw StateError('eSpeak NG init failed (error code $sampleRate)');
      }
    } finally {
      malloc.free(pathPtr);
    }

    _ready = true;
  }

  /// Returns IPA phoneme string for [text] spoken in [languageCode].
  Future<String> phonemize(String text, String languageCode) async {
    if (!_ready) await init();

    final voice = _voiceForLang(languageCode);
    if (voice != _currentVoice) {
      final voicePtr = voice.toNativeUtf8();
      try {
        _setVoice(voicePtr);
      } finally {
        malloc.free(voicePtr);
      }
      _currentVoice = voice;
    }

    // espeak_TextToPhonemes advances *textptr word by word until NULL.
    final textNative = text.toNativeUtf8();
    final ptrHolder = malloc<Pointer<Void>>();
    try {
      ptrHolder.value = textNative.cast<Void>();
      final sb = StringBuffer();
      while (ptrHolder.value != nullptr) {
        final phonPtr = _textToPhonemes(ptrHolder, _kCharsUtf8, _kPhonemesIpa);
        if (phonPtr != nullptr) {
          sb.write(phonPtr.toDartString());
        }
      }
      return sb.toString().trim();
    } finally {
      malloc.free(textNative);
      malloc.free(ptrHolder);
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  static String _voiceForLang(String lang) =>
      const {
        'en': 'en-us',
        'it': 'it',
        'es': 'es',
        'fr': 'fr',
        'ja': 'ja',
        'zh': 'cmn',
        'pt': 'pt-BR',
      }[lang] ??
      'en';

  /// Extracts the bundled `assets/espeak-ng-data.tar.gz` to the app documents
  /// directory on first call, then returns the parent path.  A sentinel file
  /// prevents re-extraction on subsequent launches.
  static Future<String> _ensureDataExtracted() async {
    final docs = await getApplicationDocumentsDirectory();
    // Sentinel written only after a complete extraction — safe against mid-
    // extraction app kills (phontab itself appears early in the tar stream).
    final sentinel = File('${docs.path}/espeak-ng-data/.roadstr_extracted');
    if (await sentinel.exists()) return docs.path;

    final assetBytes =
        (await rootBundle.load('assets/espeak-ng-data.tar.gz')).buffer.asUint8List();
    final tarBytes = GZipDecoder().decodeBytes(assetBytes);
    final archive = TarDecoder().decodeBytes(tarBytes);

    for (final entry in archive.files) {
      // Tar-slip guard: reject entries whose name escapes the extraction root
      // ("../", absolute paths). The archive is a bundled asset, so this is
      // defence in depth, but archive contents must never dictate write paths
      // outside their target directory.
      final target = File('${docs.path}/${entry.name}');
      final normalized = target.uri.normalizePath().toFilePath();
      if (!normalized.startsWith('${docs.path}/')) continue;
      if (entry.isFile) {
        await target.create(recursive: true);
        await target.writeAsBytes(entry.content);
      } else {
        await Directory(normalized).create(recursive: true);
      }
    }

    // Write sentinel last, after all files are on disk.
    await sentinel.create(recursive: true);
    return docs.path;
  }
}
