import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:path_provider/path_provider.dart';
import 'espeak_phonemizer.dart';
import 'kokoro_engine.dart';
import 'kokoro_model_manager.dart';
import 'kokoro_voices.dart';

/// Drop-in replacement for [TtsService] using the on-device Kokoro-82M neural
/// TTS model.  Only the seven languages supported by Kokoro receive voice
/// guidance; all other languages degrade silently to text-only navigation.
///
/// Initialisation flow:
///   1. Call [init] whenever the active language changes (e.g. at nav start).
///   2. If the model files are not yet on disk, [init] returns silently — the
///      [KokoroModelManager] download UI (settings screen) must be used first.
///   3. Once ready, [speak], [announceStart], [announceManeuver] and
///      [announceArrival] produce synthesised speech.
class KokoroTtsService {
  final _phonemizer = EspeakPhonemizer.instance;
  final _engine = KokoroEngine.instance;
  final _manager = KokoroModelManager.instance;
  final _player = AudioPlayer();

  String _lang = 'en';
  Float32List? _voiceData;
  bool _ready = false;
  int _utteranceId = 0; // bumped on every speak() call to cancel in-flight synth

  // Synthesis cache: "$lang:$text" → float32 waveform.
  // Keyed by language so entries survive language changes without collision.
  // Pre-warmed phrases ("Partiamo!", arrival text) are stored here at init time
  // so repeated announcements and the very first navigation start are near-instant.
  final Map<String, Float32List> _synthCache = {};

  // Serialises ONNX inference between prewarm and speak().
  // Non-null while a synthesis call is in progress; complete()d when done.
  // speak() awaits this so that if prewarm is already synthesising "Partiamo!"
  // it can reuse the disk file prewarm just wrote, instead of a redundant 2nd call.
  Completer<void>? _synthCompleter;


  bool get isReady => _ready;
  bool get _langSupported => kKokoroVoiceByLanguage.containsKey(_lang);

  // ── Lifecycle ────────────────────────────────────────────────────────────────

  /// Initialise (or re-initialise after a language change).
  Future<void> init(String languageCode) async {
    // Fast-path: already initialised for this language — nothing to do.
    // Avoids redundant model checks + voice file reads on every navigation start.
    if (_ready && _lang == languageCode) {
      debugPrint('[KokoroTTS] init: already ready for $languageCode');
      return;
    }

    _lang = languageCode;
    _ready = false;

    if (!_langSupported) {
      debugPrint('[KokoroTTS] init: $languageCode not supported');
      return;
    }

    final t0 = DateTime.now();
    int ms() => DateTime.now().difference(t0).inMilliseconds;
    debugPrint('[KokoroTTS] init start: $languageCode');

    try {
      await _phonemizer.init();
      debugPrint('[KokoroTTS]   phonemizer: ${ms()}ms');
      await _engine.init();
      debugPrint('[KokoroTTS]   engine: ${ms()}ms');
      _voiceData = await _loadVoiceEmbedding(languageCode);
      debugPrint('[KokoroTTS]   voice: ${ms()}ms');
      _ready = true;
      debugPrint('[KokoroTTS] init OK: ${ms()}ms total  voiceData=${_voiceData!.length} floats');
      unawaited(_prewarmCache());
    } catch (e) {
      debugPrint('[KokoroTTS] init failed: $e');
    }
  }

  Future<void> updateLanguage(String languageCode) => init(languageCode);

  /// Set the active language immediately, without loading the model.
  /// Call this before [init] so that [announceStart] can resolve the correct
  /// bundled asset path even while the model is still loading in the background.
  void setLanguage(String languageCode) {
    _lang = languageCode;
  }

  Future<void> stop() async {
    _utteranceId++;
    try {
      await _player.stop();
    } catch (_) {}
  }

  Future<void> dispose() async {
    await stop();
    await _player.dispose();
  }

  // ── Public speak API ─────────────────────────────────────────────────────────

  Future<void> speak(String text) async {
    // ── Bundled asset fast-path ──────────────────────────────────────────────
    // Pre-generated WAVs for fixed phrases play immediately — no model needed.
    // This runs even when !_ready so "Partiamo!" plays while the model loads.
    final assetPath = _bundledAsset(_lang, text);
    if (assetPath != null) {
      try {
        ++_utteranceId;
        await _player.stop();
        await _player.setAudioSource(AudioSource.asset(assetPath));
        await _player.seek(Duration.zero);
        await _player.play();
        debugPrint('[KokoroTTS] bundled: "$text"');
        return;
      } catch (e) {
        debugPrint('[KokoroTTS] bundled not found, synthesising: $e');
      }
    }

    // ── Synthesis path — requires model ──────────────────────────────────────
    if (!_ready) {
      debugPrint('[KokoroTTS] speak("$text") skipped — not ready');
      return;
    }

    final id = ++_utteranceId;
    final t0 = DateTime.now();
    int ms() => DateTime.now().difference(t0).inMilliseconds;
    try {
      debugPrint('[KokoroTTS] speak start: "$text"');
      await _player.stop();
      debugPrint('[KokoroTTS]   stop: ${ms()}ms');

      final wavFile = await _diskCacheFile(_lang, text);

      if (_synthCompleter != null) {
        debugPrint('[KokoroTTS]   waiting for prewarm synth…');
        await _synthCompleter!.future;
        debugPrint('[KokoroTTS]   prewarm done: ${ms()}ms');
      }

      if (!await wavFile.exists()) {
        // Disk cache miss — check memory cache before synthesising.
        final cacheKey = '$_lang:$text';
        Float32List audio;

        if (_synthCache.containsKey(cacheKey)) {
          audio = _synthCache[cacheKey]!;
          debugPrint('[KokoroTTS] mem cache: "$text"');
        } else {
          debugPrint('[KokoroTTS] speak: "$text"  lang=$_lang');
          final ipa = await _phonemizer.phonemize(text, _lang);
          debugPrint('[KokoroTTS] IPA: "$ipa"  (${DateTime.now().difference(t0).inMilliseconds}ms)');
          if (id != _utteranceId) return;

          final completer = Completer<void>();
          _synthCompleter = completer;
          try {
            audio = await _engine.synthesize(ipa, _voiceData!);
          } finally {
            _synthCompleter = null;
            completer.complete();
          }
          debugPrint('[KokoroTTS] synth done: ${audio.length} samples  (${DateTime.now().difference(t0).inMilliseconds}ms total)');
          if (id != _utteranceId) return;
          _synthCache[cacheKey] = audio;
        }

        // Persist to disk so future speak() calls (even after restart) are instant.
        await _writeWav(wavFile, audio);
      } else {
        debugPrint('[KokoroTTS]   disk hit: ${ms()}ms');
      }

      if (id != _utteranceId) return;
      debugPrint('[KokoroTTS]   setAudioSource start: ${ms()}ms');
      await _player.setAudioSource(AudioSource.uri(Uri.file(wavFile.path)));
      debugPrint('[KokoroTTS]   setAudioSource done: ${ms()}ms');
      await _player.seek(Duration.zero);
      await _player.play();
      debugPrint('[KokoroTTS]   play() called: ${ms()}ms');
    } catch (e) {
      debugPrint('[KokoroTTS] speak error: $e');
    }
  }

  /// Pre-synthesise startup and arrival phrases and write them to the disk WAV
  /// cache.  Runs in the background after [init] — never awaited.
  Future<void> _prewarmCache() async {
    for (final phrase in [_letsGo(_lang), _arrived(_lang)]) {
      final wavFile = await _diskCacheFile(_lang, phrase);
      if (await wavFile.exists()) continue; // already on disk from a previous session
      if (_synthCompleter != null) {
        // speak() is synthesising — wait for it before proceeding
        await _synthCompleter!.future;
        if (await wavFile.exists()) continue; // speak() already wrote the file
      }
      try {
        final ipa = await _phonemizer.phonemize(phrase, _lang);
        if (_synthCompleter != null) continue; // speak() started after phonemize
        final completer = Completer<void>();
        _synthCompleter = completer;
        final Float32List audio;
        try {
          audio = await _engine.synthesize(ipa, _voiceData!);
        } finally {
          _synthCompleter = null;
          completer.complete();
        }
        _synthCache['$_lang:$phrase'] = audio;
        await _writeWav(wavFile, audio);
        debugPrint('[KokoroTTS] prewarm saved: "$phrase"');
      } catch (e) {
        debugPrint('[KokoroTTS] prewarm failed for "$phrase": $e');
      }
    }
  }

  /// Pre-synthesise startup and arrival phrases for [languageCode] right after
  /// model download so the very first navigation start is instant.
  /// Safe to call without a [KokoroTtsService] instance — uses the singletons.
  static Future<void> warmUpLanguage(String languageCode) async {
    if (!kKokoroVoiceByLanguage.containsKey(languageCode)) return;
    try {
      final phonemizer = EspeakPhonemizer.instance;
      final engine = KokoroEngine.instance;
      await phonemizer.init();
      await engine.init();
      final voiceFile = await KokoroModelManager.instance.voiceFile(languageCode);
      final voiceData = (await voiceFile.readAsBytes()).buffer.asFloat32List();
      for (final phrase in [_letsGo(languageCode), _arrived(languageCode)]) {
        final wavFile = await _diskCacheFile(languageCode, phrase);
        if (await wavFile.exists()) continue;
        final ipa = await phonemizer.phonemize(phrase, languageCode);
        final audio = await engine.synthesize(ipa, voiceData);
        await _writeWav(wavFile, audio);
        debugPrint('[KokoroTTS] warmUpLanguage: saved "$phrase" ($languageCode)');
      }
    } catch (e) {
      debugPrint('[KokoroTTS] warmUpLanguage failed: $e');
    }
  }

  Future<void> announceStart() => speak(_letsGo(_lang));
  Future<void> announceArrival() => speak(_arrived(_lang));

  Future<void> announceManeuver(String instruction, int distMeters) {
    final clean = _normalizeOrdinals(instruction, _lang);
    return speak(distMeters > 0 ? '${_inMeters(distMeters, _lang)}$clean' : clean);
  }

  /// Replaces ordinal symbols (e.g. "1°", "2°") with spoken words so the TTS
  /// engine does not misread "°" as a temperature unit.
  static String _normalizeOrdinals(String text, String lang) =>
      text.replaceAllMapped(RegExp(r'(\d+)°'), (m) =>
          _ordinalWord(int.tryParse(m[1]!) ?? 1, lang));

  static String _ordinalWord(int n, String lang) {
    const it = {1:'prima',2:'seconda',3:'terza',4:'quarta',5:'quinta',6:'sesta'};
    const es = {1:'primera',2:'segunda',3:'tercera',4:'cuarta',5:'quinta',6:'sexta'};
    const fr = {1:'première',2:'deuxième',3:'troisième',4:'quatrième',5:'cinquième',6:'sixième'};
    const pt = {1:'primeira',2:'segunda',3:'terceira',4:'quarta',5:'quinta',6:'sexta'};
    const en = {1:'first',2:'second',3:'third',4:'fourth',5:'fifth',6:'sixth'};
    final map = switch (lang) {
      'it' => it, 'es' => es, 'fr' => fr, 'pt' => pt, _ => en,
    };
    return map[n] ?? '$n';
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  /// Returns the Flutter asset path for a pre-generated WAV if [text] is one of
  /// the fixed navigation phrases, otherwise null (→ synthesise at runtime).
  /// Asset files are generated by tools/generate_kokoro_phrases.py.
  static String? _bundledAsset(String lang, String text) {
    if (text == _letsGo(lang)) return 'assets/kokoro_phrases/${lang}_letsgo.wav';
    if (text == _arrived(lang)) return 'assets/kokoro_phrases/${lang}_arrived.wav';
    return null;
  }

  /// Persistent WAV cache: docs/kokoro_wav_cache/{lang}_{hash}.wav
  /// Hash is derived from the text so filenames are safe and unique per phrase.
  static Future<File> _diskCacheFile(String lang, String text) async {
    final docs = await getApplicationDocumentsDirectory();
    final hash = text.hashCode.abs().toRadixString(16);
    return File('${docs.path}/kokoro_wav_cache/${lang}_$hash.wav');
  }

  Future<Float32List> _loadVoiceEmbedding(String lang) async {
    final file = await _manager.voiceFile(lang);
    final bytes = await file.readAsBytes();
    return bytes.buffer.asFloat32List();
  }

  static Future<void> _writeWav(File file, Float32List audio) async {
    final wav = _float32ToWav(audio, 24000);
    await file.create(recursive: true);
    await file.writeAsBytes(wav);
  }

  /// Encode a float32 PCM waveform as a standard WAV (16-bit mono).
  static Uint8List _float32ToWav(Float32List samples, int sampleRate) {
    const numChannels = 1;
    const bitsPerSample = 16;
    final byteRate = sampleRate * numChannels * bitsPerSample ~/ 8;
    const blockAlign = numChannels * bitsPerSample ~/ 8;
    final dataSize = samples.length * blockAlign;

    final buf = ByteData(44 + dataSize);
    int p = 0;

    void ascii(String s) {
      for (final c in s.codeUnits) {
        buf.setUint8(p++, c);
      }
    }

    void u32(int v) {
      buf.setUint32(p, v, Endian.little);
      p += 4;
    }

    void u16(int v) {
      buf.setUint16(p, v, Endian.little);
      p += 2;
    }

    ascii('RIFF');
    u32(36 + dataSize);
    ascii('WAVE');
    ascii('fmt ');
    u32(16);           // subchunk1 size
    u16(1);            // PCM
    u16(numChannels);
    u32(sampleRate);
    u32(byteRate);
    u16(blockAlign);
    u16(bitsPerSample);
    ascii('data');
    u32(dataSize);

    for (final s in samples) {
      final v = (s * 32767).clamp(-32768, 32767).round();
      buf.setInt16(p, v, Endian.little);
      p += 2;
    }

    return buf.buffer.asUint8List();
  }

  // ── Localised phrases (Kokoro-supported languages only) ──────────────────────

  static String _letsGo(String lang) => switch (lang) {
        'it' => 'Partiamo!',
        'es' => '¡Vamos!',
        'fr' => "C'est parti !",
        'ja' => '出発します！',
        'zh' => '出发！',
        'pt' => 'Vamos lá!',
        _ => "Let's go!",
      };

  static String _arrived(String lang) => switch (lang) {
        'it' => 'Sei arrivato a destinazione.',
        'es' => 'Has llegado a tu destino.',
        'fr' => 'Vous êtes arrivé à destination.',
        'ja' => '目的地に到着しました。',
        'zh' => '您已到达目的地。',
        'pt' => 'Chegou ao seu destino.',
        _ => 'You have arrived at your destination.',
      };

  static String _inMeters(int m, String lang) => switch (lang) {
        'it' => 'Tra $m metri, ',
        'es' => 'En $m metros, ',
        'fr' => 'Dans $m mètres, ',
        'ja' => '$mメートル先で、',
        'zh' => '在$m米后，',
        'pt' => 'Em $m metros, ',
        _ => 'In $m meters, ',
      };
}

