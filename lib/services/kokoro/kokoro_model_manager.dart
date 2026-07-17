import 'dart:async';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'kokoro_voices.dart';

/// Downloads and caches the Kokoro-82M ONNX model, tokenizer and per-language
/// voice embeddings on first use, storing them under the app's documents
/// directory so they survive across app restarts.
///
/// Singleton so that an active download survives settings-screen dispose/rebuild.
/// Subscribe to [progressStream] / [errorStream] for live UI updates; read
/// [isDownloading] and [lastProgress] to restore state when re-entering the
/// settings screen mid-download.
class KokoroModelManager {
  KokoroModelManager._();
  static final KokoroModelManager instance = KokoroModelManager._();

  Directory? _dir;

  // ── Static download state (survives widget dispose) ──────────────────────────

  bool _downloading = false;
  double _lastProgress = 0;

  final _progressCtrl = StreamController<double>.broadcast();
  final _errorCtrl = StreamController<String>.broadcast();

  bool get isDownloading => _downloading;
  double get lastProgress => _lastProgress;
  Stream<double> get progressStream => _progressCtrl.stream;
  Stream<String> get errorStream => _errorCtrl.stream;

  // ── File paths ───────────────────────────────────────────────────────────────

  Future<Directory> _modelDir() async {
    if (_dir != null) return _dir!;
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/kokoro');
    if (!await dir.exists()) await dir.create(recursive: true);
    _dir = dir;
    return dir;
  }

  Future<File> get modelFile async => File('${(await _modelDir()).path}/model_q8f16.onnx');
  Future<File> get tokenizerFile async => File('${(await _modelDir()).path}/tokenizer.json');

  /// Local path for a voice embedding by its raw voice name (e.g. "if_sara").
  Future<File> voiceFileForName(String voiceName) async =>
      File('${(await _modelDir()).path}/$voiceName.bin');

  /// Local path for [languageCode]'s [gender] voice, falling back to the
  /// language's female voice if that gender isn't available.
  Future<File> voiceFile(String languageCode, [String gender = kKokoroDefaultGender]) async {
    final name = kokoroVoiceName(languageCode, gender);
    if (name == null) {
      throw ArgumentError('No Kokoro voice for language "$languageCode"');
    }
    return voiceFileForName(name);
  }

  // ── Status check ─────────────────────────────────────────────────────────────

  /// True once the model, tokenizer and every voice (both genders where
  /// available) for [languages] are present on disk with the expected size —
  /// downloading the whole catalogue upfront means switching gender in
  /// Settings never needs a fresh download.
  Future<bool> isReady(Iterable<String> languages) async {
    final model = await modelFile;
    final tok = await tokenizerFile;
    if (!await model.exists() || await model.length() != kKokoroModelSizeBytes) return false;
    if (!await tok.exists() || await tok.length() == 0) return false;
    for (final lang in languages) {
      for (final voiceName in kKokoroVoicesByLanguage[lang]?.values ?? const <String>[]) {
        final v = await voiceFileForName(voiceName);
        if (!await v.exists() || await v.length() != kKokoroVoiceSizeBytes) return false;
      }
    }
    return true;
  }

  // ── Download ─────────────────────────────────────────────────────────────────

  /// Start a background download for [languages].  Safe to call when already
  /// downloading — the call is a no-op.  Listen to [progressStream] for
  /// progress in [0,1] and [errorStream] for failure messages.
  void startDownload(Iterable<String> languages) {
    if (_downloading) return;
    _ensureDownloaded(languages); // fire-and-forget
  }

  Future<void> _ensureDownloaded(Iterable<String> languages) async {
    _downloading = true;
    _lastProgress = 0;
    try {
      await ensureDownloaded(
        languages,
        onProgress: (p) {
          _lastProgress = p;
          _progressCtrl.add(p);
        },
      );
    } catch (e) {
      _errorCtrl.add(e.toString());
    } finally {
      _downloading = false;
    }
  }

  /// Low-level download — prefer [startDownload] for UI flows.
  Future<void> ensureDownloaded(
    Iterable<String> languages, {
    void Function(double progress)? onProgress,
  }) async {
    final tasks = <_DownloadTask>[
      _DownloadTask(
        url: '$kKokoroRepoBaseUrl/$kKokoroModelFile',
        file: await modelFile,
        expectedSize: kKokoroModelSizeBytes,
      ),
      _DownloadTask(
        url: '$kKokoroRepoBaseUrl/$kKokoroTokenizerFile',
        file: await tokenizerFile,
        expectedSize: null,
      ),
      for (final voiceName in languages
          .expand((lang) => kKokoroVoicesByLanguage[lang]?.values ?? const <String>[])
          .toSet())
        _DownloadTask(
          url: '$kKokoroRepoBaseUrl/${kokoroVoiceFileForName(voiceName)}',
          file: await voiceFileForName(voiceName),
          expectedSize: kKokoroVoiceSizeBytes,
        ),
    ];

    final pending = <_DownloadTask>[];
    for (final t in tasks) {
      if (await t.file.exists() &&
          (t.expectedSize == null || await t.file.length() == t.expectedSize)) {
        continue;
      }
      pending.add(t);
    }
    if (pending.isEmpty) {
      onProgress?.call(1.0);
      return;
    }

    final totalBytes = pending.fold<int>(0, (s, t) => s + (t.expectedSize ?? 1024 * 1024));
    var downloadedBytes = 0;

    for (final task in pending) {
      final client = http.Client();
      try {
        final request = http.Request('GET', Uri.parse(task.url));
        final response = await client.send(request);
        if (response.statusCode != 200) {
          throw Exception('Kokoro download failed (${response.statusCode}): ${task.url}');
        }
        final sink = task.file.openWrite();
        var taskBytes = 0;
        try {
          await response.stream.forEach((chunk) {
            sink.add(chunk);
            taskBytes += chunk.length;
            if (totalBytes > 0) {
              onProgress?.call(((downloadedBytes + taskBytes) / totalBytes).clamp(0.0, 1.0));
            }
          });
        } finally {
          // Close even on mid-download failure or the file handle leaks; the
          // truncated file itself is harmless — the size check re-downloads it.
          await sink.close();
        }
        downloadedBytes += taskBytes;
      } finally {
        client.close();
      }
    }
    onProgress?.call(1.0);
  }
}

class _DownloadTask {
  final String url;
  final File file;
  final int? expectedSize;
  const _DownloadTask({required this.url, required this.file, required this.expectedSize});
}
