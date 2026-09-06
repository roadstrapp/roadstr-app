import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'piper_model_manager.dart';
import 'piper_voices.dart';

/// Piper (VITS) ONNX inference engine — German only, see piper_voices.dart
/// for why this is a separate engine from [KokoroEngine] rather than another
/// entry in its language table.
///
/// Contract verified directly against Piper's own source
/// (github.com/OHF-Voice/piper1-gpl, `src/piper/phoneme_ids.py` and
/// `src/piper/voice.py`) and the Thorsten-Voice model's own `onnx.json` —
/// not guessed from the model card, which doesn't document it:
///   - id sequence: BOS, PAD, id(phoneme_1), PAD, id(phoneme_2), PAD, ...,
///     id(phoneme_n), PAD, EOS — a PAD id between every symbol, including
///     right after BOS, with none after the final EOS.
///   - inputs: `input` (int64 [1, N]), `input_lengths` (int64 [1] = N),
///     `scales` (float32 [3] = [noise_scale, length_scale, noise_w]).
///   - output: raw audio samples (float, [-1, 1]) — flattening the tensor
///     regardless of its nominal rank gives the sample sequence directly,
///     same approach [KokoroEngine] already uses for its own output.
///   - this specific voice has no multi-character phoneme entries (verified
///     against its shipped `phoneme_id_map`), so no diphthong-merging step
///     is needed before the per-character id lookup.
class PiperEngine {
  PiperEngine._();
  static final PiperEngine instance = PiperEngine._();

  final _manager = PiperModelManager.instance;
  final _ort = OnnxRuntime();

  OrtSession? _session;
  Map<String, List<int>>? _phonemeIdMap;
  Future<void>? _initFuture;
  Completer<void>? _inferenceLock;

  static const _pad = '_';
  static const _bos = '^';
  static const _eos = r'$';

  bool get isReady => _session != null && _phonemeIdMap != null;

  Future<void> init() async {
    if (isReady) return;
    _initFuture ??= _load().whenComplete(() => _initFuture = null);
    return _initFuture!;
  }

  Future<void> _load() async {
    if (isReady) return;

    final configFile = await _manager.configFile;
    if (!await configFile.exists()) {
      throw StateError('$kPiperConfigFile not found');
    }
    final modelFile = await _manager.modelFile;
    if (!await modelFile.exists()) {
      throw StateError('$kPiperModelFile not found');
    }

    final config =
        jsonDecode(await configFile.readAsString()) as Map<String, dynamic>;
    final rawMap = config['phoneme_id_map'] as Map<String, dynamic>;
    _phonemeIdMap = {
      for (final e in rawMap.entries)
        e.key: (e.value as List).map((v) => (v as num).toInt()).toList(),
    };

    _session = await _ort.createSession(
      modelFile.path,
      options: OrtSessionOptions(
        intraOpNumThreads: 4,
        providers: [OrtProvider.XNNPACK, OrtProvider.CPU],
      ),
    );
    debugPrint('[PiperEngine] loaded  inputs=${_session!.inputNames}');
  }

  List<int> _phonemesToIds(String ipa) =>
      phonemesToIds(ipa, _phonemeIdMap!, onMissing: (ch) {
        debugPrint('[PiperEngine] missing phoneme from id map: "$ch"');
      });

  /// Maps [ipa] to Piper's BOS/PAD/id.../PAD/EOS sequence. Unknown characters
  /// are skipped, same tolerance [KokoroEngine.tokenize] has for a
  /// phonemizer producing a symbol this specific voice's map doesn't cover.
  /// Pure and static so the exact sequencing (verified against Piper's own
  /// `phoneme_ids.py`) is unit-testable without an ONNX runtime.
  @visibleForTesting
  static List<int> phonemesToIds(
    String ipa,
    Map<String, List<int>> idMap, {
    void Function(String)? onMissing,
  }) {
    final ids = <int>[...idMap[_bos]!, ...idMap[_pad]!];
    for (final rune in ipa.runes) {
      final ch = String.fromCharCode(rune);
      final id = idMap[ch];
      if (id == null) {
        onMissing?.call(ch);
        continue;
      }
      ids.addAll(id);
      ids.addAll(idMap[_pad]!);
    }
    ids.addAll(idMap[_eos]!);
    return ids;
  }

  /// Run inference and return a 22.05 kHz float32 audio waveform.
  ///
  /// [speed]: Piper's `length_scale` runs the other way from a playback-speed
  /// multiplier (smaller = faster), hence the reciprocal below.
  Future<Float32List> synthesize(String ipa, {double speed = 1.0}) async {
    if (!isReady) await init();
    while (_inferenceLock != null) {
      await _inferenceLock!.future;
    }
    final lock = Completer<void>();
    _inferenceLock = lock;
    try {
      return await _synthesizeLocked(ipa, speed);
    } finally {
      if (identical(_inferenceLock, lock)) _inferenceLock = null;
      lock.complete();
    }
  }

  Future<Float32List> _synthesizeLocked(String ipa, double speed) async {
    if (!speed.isFinite || speed < 0.5 || speed > 2.0) {
      throw ArgumentError.value(speed, 'speed', 'must be between 0.5 and 2.0');
    }
    final ids = _phonemesToIds(ipa);
    if (ids.length <= 3) {
      // Just BOS/PAD/EOS — no recognised phoneme in the whole utterance.
      throw const FormatException('No supported phonemes to synthesize');
    }
    debugPrint('[PiperEngine] IPA: "$ipa"  ids(${ids.length})');

    final inputTensor =
        await OrtValue.fromList(Int64List.fromList(ids), [1, ids.length]);
    final lengthTensor =
        await OrtValue.fromList(Int64List.fromList([ids.length]), [1]);
    final scalesTensor = await OrtValue.fromList(
        Float32List.fromList([kPiperNoiseScale, 1.0 / speed, kPiperNoiseW]),
        [3]);

    Map<String, OrtValue>? outputs;
    try {
      outputs = await _session!.run({
        'input': inputTensor,
        'input_lengths': lengthTensor,
        'scales': scalesTensor,
      });

      final audioValue = outputs.values.first;
      final audioFlat = await audioValue.asFlattenedList();
      debugPrint('[PiperEngine] audio samples: ${audioFlat.length}  '
          '(${audioFlat.length / kPiperSampleRate}s @ ${kPiperSampleRate}Hz)');

      return Float32List.fromList(
        audioFlat.map((e) => (e as num).toDouble()).toList(),
      );
    } finally {
      if (outputs != null) {
        for (final value in outputs.values) {
          await value.dispose();
        }
      }
      await inputTensor.dispose();
      await lengthTensor.dispose();
      await scalesTensor.dispose();
    }
  }

  Future<void> dispose() async {
    await _session?.close();
    _session = null;
    _phonemeIdMap = null;
  }
}
