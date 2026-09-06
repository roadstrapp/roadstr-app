import 'dart:async';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'piper_voices.dart';

/// Downloads and caches the Piper German voice model + its config on first
/// use. Same download/verify/atomic-rename mechanics as
/// [KokoroModelManager], kept as an independent copy rather than a shared
/// base class: the file set here is fixed (exactly one model, one config,
/// no per-language/gender fan-out), and duplicating this loop is a smaller
/// risk than reworking the already-shipped, harder-to-recheck Kokoro
/// manager to make room for a second, structurally different caller.
///
/// Stored in its own `piper` subdirectory — entirely separate from
/// `kokoro/` — so the two engines' downloads, readiness checks and
/// deletions never interact.
class PiperModelManager {
  PiperModelManager._();
  static final PiperModelManager instance = PiperModelManager._();

  Directory? _dir;

  bool _downloading = false;
  double _lastProgress = 0;

  final _progressCtrl = StreamController<double>.broadcast();
  final _errorCtrl = StreamController<String>.broadcast();

  bool get isDownloading => _downloading;
  double get lastProgress => _lastProgress;
  Stream<double> get progressStream => _progressCtrl.stream;
  Stream<String> get errorStream => _errorCtrl.stream;

  /// Combined size of both files — used by callers that blend this
  /// manager's progress with another download into one overall percentage.
  static const totalBytes = kPiperModelSizeBytes + kPiperConfigSizeBytes;

  Future<Directory> _modelDir() async {
    if (_dir != null) return _dir!;
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/piper');
    if (!await dir.exists()) await dir.create(recursive: true);
    _dir = dir;
    return dir;
  }

  Future<File> get modelFile async =>
      File('${(await _modelDir()).path}/$kPiperModelFile');
  Future<File> get configFile async =>
      File('${(await _modelDir()).path}/$kPiperConfigFile');

  Future<bool> isReady() async {
    final model = await modelFile;
    final config = await configFile;
    if (!await _matches(model, kPiperModelSizeBytes, kPiperModelSha256)) {
      return false;
    }
    return _matches(config, kPiperConfigSizeBytes, kPiperConfigSha256);
  }

  void startDownload() {
    if (_downloading) return;
    _ensureDownloaded();
  }

  Future<void> _ensureDownloaded() async {
    _downloading = true;
    _lastProgress = 0;
    try {
      await ensureDownloaded(onProgress: (p) {
        _lastProgress = p;
        _progressCtrl.add(p);
      });
    } catch (e) {
      _errorCtrl.add(e.toString());
    } finally {
      _downloading = false;
    }
  }

  Future<void> ensureDownloaded({void Function(double progress)? onProgress}) async {
    final tasks = [
      _DownloadTask(
        url: '$kPiperRepoBaseUrl/$kPiperModelFile',
        file: await modelFile,
        expectedSize: kPiperModelSizeBytes,
        sha256: kPiperModelSha256,
      ),
      _DownloadTask(
        url: '$kPiperRepoBaseUrl/$kPiperConfigFile',
        file: await configFile,
        expectedSize: kPiperConfigSizeBytes,
        sha256: kPiperConfigSha256,
      ),
    ];

    final pending = <_DownloadTask>[];
    for (final t in tasks) {
      if (await _matches(t.file, t.expectedSize, t.sha256)) continue;
      pending.add(t);
    }
    if (pending.isEmpty) {
      onProgress?.call(1.0);
      return;
    }

    final totalBytes = pending.fold<int>(0, (s, t) => s + t.expectedSize);
    var downloadedBytes = 0;

    for (final task in pending) {
      final client = http.Client();
      try {
        final request = http.Request('GET', Uri.parse(task.url));
        final response =
            await client.send(request).timeout(const Duration(seconds: 20));
        if (response.statusCode != 200) {
          throw Exception(
              'Piper download failed (${response.statusCode}): ${task.url}');
        }
        if (response.contentLength != null &&
            response.contentLength != task.expectedSize) {
          throw Exception('Piper asset has an unexpected size: ${task.url}');
        }
        final partial = File('${task.file.path}.part');
        if (await partial.exists()) await partial.delete();
        final sink = partial.openWrite();
        var taskBytes = 0;
        try {
          await response.stream
              .timeout(const Duration(seconds: 30))
              .forEach((chunk) {
            if (taskBytes + chunk.length > task.expectedSize) {
              throw Exception('Piper asset exceeded its declared size');
            }
            sink.add(chunk);
            taskBytes += chunk.length;
            if (totalBytes > 0) {
              onProgress?.call(
                  ((downloadedBytes + taskBytes) / totalBytes).clamp(0.0, 1.0));
            }
          });
        } finally {
          await sink.close();
        }
        if (taskBytes != task.expectedSize ||
            !await _matches(partial, task.expectedSize, task.sha256)) {
          await partial.delete();
          throw Exception('Piper asset integrity verification failed');
        }
        if (await task.file.exists()) await task.file.delete();
        await partial.rename(task.file.path);
        downloadedBytes += taskBytes;
      } finally {
        client.close();
      }
    }
    onProgress?.call(1.0);
  }

  static Future<bool> _matches(File file, int size, String expectedHash) async {
    if (!await file.exists() || await file.length() != size) return false;
    final digest = await sha256.bind(file.openRead()).first;
    return digest.toString() == expectedHash;
  }
}

class _DownloadTask {
  final String url;
  final File file;
  final int expectedSize;
  final String sha256;
  const _DownloadTask({
    required this.url,
    required this.file,
    required this.expectedSize,
    required this.sha256,
  });
}
