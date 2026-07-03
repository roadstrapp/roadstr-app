import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'kokoro_model_manager.dart';

/// Kokoro-82M ONNX inference engine.
///
/// Responsibilities:
///   - Parse the downloaded tokenizer.json into a character→ID vocab map.
///   - Tokenize IPA strings to int64 token sequences.
///   - Run the ONNX model to produce raw float32 audio at 24 kHz.
///
/// This class does NOT handle G2P (use [EspeakPhonemizer]) or playback
/// (use [KokoroTtsService]).  Keeping them separate makes each unit testable.
class KokoroEngine {
  KokoroEngine._();
  static final KokoroEngine instance = KokoroEngine._();

  final _manager = KokoroModelManager.instance;
  final _ort = OnnxRuntime();

  OrtSession? _session;
  Map<String, int>? _vocab;
  // Prevents concurrent createSession() calls (e.g. from line-216 and line-939 init).
  Future<void>? _initFuture;

  bool get isReady => _session != null && _vocab != null;

  /// Initialise the engine (idempotent, concurrent-safe).
  Future<void> init() async {
    if (isReady) return;
    _initFuture ??= _load().whenComplete(() => _initFuture = null);
    return _initFuture!;
  }

  Future<void> _load() async {
    if (isReady) return; // concurrent caller already finished

    final tokFile = await _manager.tokenizerFile;
    if (!await tokFile.exists()) throw StateError('tokenizer.json not found');
    final modelFile = await _manager.modelFile;
    if (!await modelFile.exists()) throw StateError('model_q8f16.onnx not found');

    final tokJson = jsonDecode(await tokFile.readAsString()) as Map<String, dynamic>;
    _vocab = _extractVocab(tokJson);

    // XNNPACK is a well-supported CPU-side accelerator (2-4× faster than plain
    // CPU for float/int8 ops). NNAPI is omitted — it can cause 5-10 s
    // compilation stalls on first use and varies wildly across devices.
    _session = await _ort.createSession(
      modelFile.path,
      options: OrtSessionOptions(
        intraOpNumThreads: 4,
        providers: [OrtProvider.XNNPACK, OrtProvider.CPU],
      ),
    );
    debugPrint('[KokoroEngine] loaded  inputs=${_session!.inputNames}');
  }

  /// Map each character of [ipa] to its vocab ID, skipping unknown characters.
  ///
  /// Does NOT add BOS/EOS tokens — [synthesize] handles that.
  List<int> tokenize(String ipa) {
    final vocab = _vocab!;
    final ids = <int>[];
    for (final rune in ipa.runes) {
      final ch = String.fromCharCode(rune);
      final id = vocab[ch];
      if (id != null) ids.add(id);
    }
    return ids;
  }

  /// Run inference and return a 24 kHz float32 audio waveform.
  ///
  /// [ipa] — IPA phoneme string (from [EspeakPhonemizer.phonemize]).
  /// [voiceData] — raw bytes from the voice .bin file read as [Float32List]
  ///   (shape: 510 × 256, i.e. 130 560 elements = 522 240 bytes).
  /// [speed] — playback speed multiplier (1.0 = normal).
  Future<Float32List> synthesize(
    String ipa,
    Float32List voiceData, {
    double speed = 1.0,
  }) async {
    if (!isReady) await init();

    final tokens = tokenize(ipa);
    debugPrint('[KokoroEngine] IPA: "$ipa"  tokens(${tokens.length}): $tokens');

    // Wrap with BOS/EOS token (ID 0) as expected by the Kokoro model.
    final seqTokens = Int64List(tokens.length + 2);
    seqTokens[0] = 0;
    for (int i = 0; i < tokens.length; i++) {
      seqTokens[i + 1] = tokens[i];
    }
    seqTokens[tokens.length + 1] = 0;

    // Style vector: row at index min(N, 509) of the 510×256 voice matrix,
    // then reshaped to [1, 1, 256] as the model expects.
    final styleIdx = tokens.length.clamp(0, 509);
    final styleSlice = Float32List.sublistView(voiceData, styleIdx * 256, styleIdx * 256 + 256);

    // Create input OrtValues.
    final tokTensor = await OrtValue.fromList(seqTokens, [1, seqTokens.length]);
    final styleTensor = await OrtValue.fromList(styleSlice, [1, 256]);
    final speedTensor = await OrtValue.fromList(Float32List.fromList([speed]), [1]);

    try {
      final outputs = await _session!.run({
        'input_ids': tokTensor,
        'style': styleTensor,
        'speed': speedTensor,
      });

      // First output is the audio waveform.
      final audioValue = outputs.values.first;
      final audioFlat = await audioValue.asFlattenedList();
      debugPrint('[KokoroEngine] audio samples: ${audioFlat.length}  (${audioFlat.length / 24000.0}s @ 24kHz)');

      // Release native output tensors before returning.
      for (final v in outputs.values) {
        await v.dispose();
      }

      return Float32List.fromList(
        audioFlat.map((e) => (e as num).toDouble()).toList(),
      );
    } finally {
      await tokTensor.dispose();
      await styleTensor.dispose();
      await speedTensor.dispose();
    }
  }

  Future<void> dispose() async {
    await _session?.close();
    _session = null;
    _vocab = null;
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  /// Extract a flat {character → tokenId} map from the tokenizer.json.
  ///
  /// Handles two formats:
  ///   - HuggingFace Tokenizers format: {"model": {"vocab": {char: id}}}
  ///   - Simple flat dict:              {"char": id, ...}
  static Map<String, int> _extractVocab(Map<String, dynamic> json) {
    final model = json['model'];
    if (model is Map) {
      final vocab = model['vocab'];
      if (vocab is Map) {
        return {
          for (final e in vocab.entries) e.key.toString(): (e.value as num).toInt(),
        };
      }
    }
    return {
      for (final e in json.entries)
        if (e.value is num) e.key: (e.value as num).toInt(),
    };
  }
}
