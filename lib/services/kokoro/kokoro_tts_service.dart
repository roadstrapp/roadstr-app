import 'dart:async';
import 'dart:io';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:path_provider/path_provider.dart';
import '../../utils/units.dart';
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
  String _gender = kKokoroDefaultGender;
  double _speed = 1.0;
  Float32List? _voiceData;
  bool _ready = false;
  int _utteranceId = 0; // bumped on every speak() call to cancel in-flight synth
  bool _audioSessionConfigured = false;
  bool _audioFocusActive = false;
  bool _isSpeaking = false;
  /// At most one queued low-priority utterance (the newest wins — a stale
  /// hazard alert is worthless once a newer one arrives).
  String? _pendingText;

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
  bool get _langSupported => kKokoroVoicesByLanguage.containsKey(_lang);

  // ── Lifecycle ────────────────────────────────────────────────────────────────

  /// Initialise (or re-initialise after a language or gender change).
  Future<void> init(String languageCode) async {
    // Fast-path: already initialised for this language+gender — nothing to do.
    // Avoids redundant model checks + voice file reads on every navigation start.
    // (setGender() clears _ready when the gender actually changed, so this
    // guard alone is enough to also catch gender switches.)
    if (_ready && _lang == languageCode) {
      debugPrint('[KokoroTTS] init: already ready for $languageCode/$_gender');
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
    debugPrint('[KokoroTTS] init start: $languageCode/$_gender');

    try {
      await _phonemizer.init();
      debugPrint('[KokoroTTS]   phonemizer: ${ms()}ms');
      await _engine.init();
      debugPrint('[KokoroTTS]   engine: ${ms()}ms');
      _voiceData = await _loadVoiceEmbedding(languageCode, _gender);
      debugPrint('[KokoroTTS]   voice: ${ms()}ms');
      _ready = true;
      debugPrint('[KokoroTTS] init OK: ${ms()}ms total  voiceData=${_voiceData!.length} floats');
      unawaited(_configureAudioSession());
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

  /// Sets the preferred voice gender ('f' or 'm'). Call before [init] — if the
  /// gender actually changed, this clears the ready flag so the next [init]
  /// call reloads the corresponding voice embedding. Languages without a male
  /// voice (French) silently keep using the female voice regardless of this
  /// setting; see [kokoroVoiceName].
  void setGender(String gender) {
    if (_gender == gender) return;
    _gender = gender;
    _ready = false;
  }

  /// Sets the playback speed multiplier (e.g. 1.0 = normal). Takes effect on
  /// the next synthesis call — no model reload needed, unlike gender/language.
  void setSpeed(double speed) {
    _speed = speed;
  }

  Future<void> stop() async {
    _utteranceId++;
    _isSpeaking = false;
    _pendingText = null;
    try {
      await _player.stop();
    } catch (_) {}
    unawaited(_releaseFocus());
  }

  Future<void> dispose() async {
    await stop();
    await _player.dispose();
  }

  // ── Audio focus / ducking ────────────────────────────────────────────────────

  /// Configures the audio session once for navigation guidance.
  /// On Android: requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK so other apps
  /// (music, podcasts) lower their volume while the voice plays, then fade
  /// back to full volume within ~1 second when focus is released.
  /// On iOS: AVAudioSessionCategoryOptions.duckOthers achieves the same.
  Future<void> _configureAudioSession() async {
    if (_audioSessionConfigured) return;
    _audioSessionConfigured = true;
    try {
      final session = await AudioSession.instance;
      await session.configure(const AudioSessionConfiguration(
        avAudioSessionCategory: AVAudioSessionCategory.playback,
        avAudioSessionCategoryOptions: AVAudioSessionCategoryOptions.duckOthers,
        avAudioSessionMode: AVAudioSessionMode.voicePrompt,
        androidAudioAttributes: AndroidAudioAttributes(
          contentType: AndroidAudioContentType.speech,
          usage: AndroidAudioUsage.assistanceNavigationGuidance,
        ),
        androidAudioFocusGainType: AndroidAudioFocusGainType.gainTransientMayDuck,
        androidWillPauseWhenDucked: false,
      ));
      debugPrint('[KokoroTTS] audio session configured');
    } catch (e) {
      debugPrint('[KokoroTTS] audio session config failed: $e');
    }
  }

  Future<void> _acquireFocus() async {
    if (_audioFocusActive) return;
    try {
      final session = await AudioSession.instance;
      final granted = await session.setActive(true);
      _audioFocusActive = granted;
    } catch (_) {}
  }

  Future<void> _releaseFocus() async {
    if (!_audioFocusActive) return;
    _audioFocusActive = false;
    try {
      final session = await AudioSession.instance;
      await session.setActive(false);
    } catch (_) {}
  }

  // ── Public speak API ─────────────────────────────────────────────────────────

  /// Speaks [text]. Utterances never talk over each other:
  /// - [priority] utterances (turn instructions — time-critical) cut whatever
  ///   is playing and start immediately.
  /// - non-priority utterances (hazard/ZTL/ambient alerts) wait for the
  ///   current one to finish; only the newest pending alert is kept.
  Future<void> speak(String text, {bool priority = false}) async {
    if (_isSpeaking && !priority) {
      _pendingText = text;
      debugPrint('[KokoroTTS] queued: "$text"');
      return;
    }
    await _speakNow(text);
  }

  Future<void> _speakNow(String text) async {
    final id = ++_utteranceId;
    _isSpeaking = true;
    final started = await _startPlayback(text, id);
    if (!started) {
      _finishUtterance(id);
      return;
    }
    // Mark done + release focus + drain the queue when playback completes.
    unawaited(_player.playerStateStream
        .firstWhere((s) =>
            s.processingState == ProcessingState.completed ||
            s.processingState == ProcessingState.idle)
        .then((_) => _finishUtterance(id))
        .catchError((_) => _finishUtterance(id)));
  }

  /// Runs when utterance [id] finishes, fails, or is cut. The id guard makes
  /// stale completion listeners inert: when a priority utterance cuts this
  /// one, the old listener fires on the stop() transition but must NOT
  /// release the focus the new utterance is using, nor clear its state.
  void _finishUtterance(int id) {
    if (id != _utteranceId) return;
    _isSpeaking = false;
    unawaited(_releaseFocus());
    final next = _pendingText;
    _pendingText = null;
    if (next != null) unawaited(_speakNow(next));
  }

  /// Loads and starts playback of [text] (bundled WAV or on-device synthesis).
  /// Returns true if `play()` was reached, false when skipped or superseded.
  Future<bool> _startPlayback(String text, int id) async {
    // ── Bundled asset fast-path ──────────────────────────────────────────────
    // Pre-generated WAVs for fixed phrases play immediately — no model needed.
    // This runs even when !_ready so "Partenza" plays while the model loads.
    // Only at normal speed: bundled assets are pre-rendered at 1.0×, so a
    // non-default speed must fall through to on-device synthesis instead.
    final assetPath = _speed == 1.0 ? _bundledAsset(_lang, _gender, text) : null;
    if (assetPath != null) {
      try {
        await _acquireFocus();
        await _player.stop();
        await _player.setAudioSource(AudioSource.asset(assetPath));
        await _player.seek(Duration.zero);
        await _player.play();
        debugPrint('[KokoroTTS] bundled: "$text"');
        return true;
      } catch (e) {
        debugPrint('[KokoroTTS] bundled not found, synthesising: $e');
      }
    }

    // ── Synthesis path — requires model ──────────────────────────────────────
    if (!_ready) {
      debugPrint('[KokoroTTS] speak("$text") skipped — not ready');
      return false;
    }

    final t0 = DateTime.now();
    int ms() => DateTime.now().difference(t0).inMilliseconds;
    try {
      debugPrint('[KokoroTTS] speak start: "$text"');
      await _player.stop();
      debugPrint('[KokoroTTS]   stop: ${ms()}ms');

      final wavFile = await _diskCacheFile(_lang, _gender, _speed, text);

      if (_synthCompleter != null) {
        debugPrint('[KokoroTTS]   waiting for prewarm synth…');
        await _synthCompleter!.future;
        debugPrint('[KokoroTTS]   prewarm done: ${ms()}ms');
      }

      if (!await wavFile.exists()) {
        // Disk cache miss — check memory cache before synthesising.
        final cacheKey = '$_lang:$_gender:${_speed.toStringAsFixed(2)}:$text';
        Float32List audio;

        if (_synthCache.containsKey(cacheKey)) {
          audio = _synthCache[cacheKey]!;
          debugPrint('[KokoroTTS] mem cache: "$text"');
        } else {
          debugPrint('[KokoroTTS] speak: "$text"  lang=$_lang/$_gender speed=$_speed');
          final ipa = await _phonemizer.phonemize(text, _lang);
          debugPrint('[KokoroTTS] IPA: "$ipa"  (${DateTime.now().difference(t0).inMilliseconds}ms)');
          if (id != _utteranceId) return false;

          final completer = Completer<void>();
          _synthCompleter = completer;
          try {
            audio = await _engine.synthesize(ipa, _voiceData!, speed: _speed);
          } finally {
            _synthCompleter = null;
            completer.complete();
          }
          debugPrint('[KokoroTTS] synth done: ${audio.length} samples  (${DateTime.now().difference(t0).inMilliseconds}ms total)');
          if (id != _utteranceId) return false;
          _synthCache[cacheKey] = audio;
        }

        // Persist to disk so future speak() calls (even after restart) are instant.
        await _writeWav(wavFile, audio);
      } else {
        debugPrint('[KokoroTTS]   disk hit: ${ms()}ms');
      }

      if (id != _utteranceId) return false;
      await _acquireFocus();
      debugPrint('[KokoroTTS]   setAudioSource start: ${ms()}ms');
      await _player.setAudioSource(AudioSource.uri(Uri.file(wavFile.path)));
      debugPrint('[KokoroTTS]   setAudioSource done: ${ms()}ms');
      await _player.seek(Duration.zero);
      await _player.play();
      debugPrint('[KokoroTTS]   play() called: ${ms()}ms');
      return true;
    } catch (e) {
      debugPrint('[KokoroTTS] speak error: $e');
      return false;
    }
  }

  /// Pre-synthesise startup and arrival phrases and write them to the disk WAV
  /// cache.  Runs in the background after [init] — never awaited. Only warms
  /// normal speed (1.0×): bundled assets already cover that instantly, and
  /// non-default speeds are rare enough not to warrant pre-synthesis.
  Future<void> _prewarmCache() async {
    for (final phrase in [_letsGo(_lang), _arrived(_lang)]) {
      final wavFile = await _diskCacheFile(_lang, _gender, 1.0, phrase);
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
        _synthCache['$_lang:$_gender:1.00:$phrase'] = audio;
        await _writeWav(wavFile, audio);
        debugPrint('[KokoroTTS] prewarm saved: "$phrase"');
      } catch (e) {
        debugPrint('[KokoroTTS] prewarm failed for "$phrase": $e');
      }
    }
  }

  /// Pre-synthesise startup and arrival phrases for [languageCode]/[gender]
  /// right after model download so the very first navigation start is
  /// instant. Safe to call without a [KokoroTtsService] instance — uses the
  /// singletons.
  static Future<void> warmUpLanguage(String languageCode,
      {String gender = kKokoroDefaultGender}) async {
    if (!kKokoroVoicesByLanguage.containsKey(languageCode)) return;
    try {
      final phonemizer = EspeakPhonemizer.instance;
      final engine = KokoroEngine.instance;
      await phonemizer.init();
      await engine.init();
      final voiceFile = await KokoroModelManager.instance.voiceFile(languageCode, gender);
      final voiceData = (await voiceFile.readAsBytes()).buffer.asFloat32List();
      for (final phrase in [_letsGo(languageCode), _arrived(languageCode)]) {
        final wavFile = await _diskCacheFile(languageCode, gender, 1.0, phrase);
        if (await wavFile.exists()) continue;
        final ipa = await phonemizer.phonemize(phrase, languageCode);
        final audio = await engine.synthesize(ipa, voiceData);
        await _writeWav(wavFile, audio);
        debugPrint('[KokoroTTS] warmUpLanguage: saved "$phrase" ($languageCode/$gender)');
      }
    } catch (e) {
      debugPrint('[KokoroTTS] warmUpLanguage failed: $e');
    }
  }

  Future<void> announceStart() => speak(_letsGo(_lang), priority: true);
  Future<void> announceArrival() => speak(_arrived(_lang), priority: true);

  /// Speaks a fixed, language-neutral sample turn instruction using whatever
  /// gender/speed is currently set — lets the user judge a voice/speed choice
  /// before committing to it in Settings. Always synthesises fresh (bypasses
  /// the bundled-asset fast path) so it accurately reflects the live setting.
  Future<void> previewVoice() => speak(_previewPhrase(_lang), priority: true);

  /// Turn instructions are time-critical: they cut whatever is playing.
  /// Ambient alerts (hazards, ZTL…) go through [speak] without priority and
  /// wait their turn instead.
  Future<void> announceManeuver(String instruction, int distMeters) {
    final clean = _normalizeOrdinals(instruction, _lang);
    final prefix = Units.ttsDistPrefix(distMeters, _lang);
    return speak(prefix.isNotEmpty ? '$prefix$clean' : clean, priority: true);
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
  /// Asset files are generated by tools/generate_kokoro_phrases.py. Only
  /// bundled for genders that actually exist (French has no male voice —
  /// [kokoroVoiceName] would already have fallen back to 'f' upstream, but
  /// this stays defensive in case a caller passes 'm' directly).
  static String? _bundledAsset(String lang, String gender, String text) {
    final g = kokoroHasGenderChoice(lang) ? gender : kKokoroDefaultGender;
    if (text == _letsGo(lang)) return 'assets/kokoro_phrases/${lang}_${g}_letsgo.wav';
    if (text == _arrived(lang)) return 'assets/kokoro_phrases/${lang}_${g}_arrived.wav';
    return null;
  }

  /// Persistent WAV cache: docs/kokoro_wav_cache/{lang}_{gender}_{speed}_{hash}.wav
  /// Hash is derived from the text so filenames are safe and unique per phrase;
  /// gender/speed are part of the path so switching either never plays a
  /// stale cached clip recorded under different settings.
  static Future<File> _diskCacheFile(
      String lang, String gender, double speed, String text) async {
    final docs = await getApplicationDocumentsDirectory();
    final hash = text.hashCode.abs().toRadixString(16);
    final speedTag = speed.toStringAsFixed(2);
    return File('${docs.path}/kokoro_wav_cache/${lang}_${gender}_${speedTag}_$hash.wav');
  }

  Future<Float32List> _loadVoiceEmbedding(String lang, String gender) async {
    final file = await _manager.voiceFile(lang, gender);
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
        'it' => 'Partenza',
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

  /// Fixed sample turn instruction used by [previewVoice] — same structure
  /// (distance + direction) in every language so a voice/speed choice can be
  /// judged consistently regardless of the app's current language.
  static String _previewPhrase(String lang) => switch (lang) {
        'it' => 'Tra 200 metri, gira a destra',
        'es' => 'En 200 metros, gire a la derecha',
        'fr' => 'Dans 200 mètres, tournez à droite',
        'ja' => '200メートル先、右折です',
        'zh' => '前方200米，右转',
        'pt' => 'Em 200 metros, vire à direita',
        _ => 'In 200 meters, turn right',
      };

}

