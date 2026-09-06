import 'kokoro/kokoro_model_manager.dart';
import 'kokoro/kokoro_voices.dart';
import 'piper/piper_model_manager.dart';

/// Shared helpers for screens (Settings, onboarding) that show ONE combined
/// download status/progress bar for two independent managers — Kokoro's
/// seven-language bundle and Piper's German voice.
///
/// Kept out of either screen so the byte-weighting and "are we actually done"
/// logic exists in exactly one place instead of drifting between two copies.
class VoiceModelDownload {
  VoiceModelDownload._();

  // Approximate on purpose — exact byte accounting isn't necessary for a
  // progress bar; only the ratio between the two downloads' sizes matters.
  static const _kokoroApproxTotalBytes = 93000000; // model+tokenizer+13 voices
  static const _piperApproxTotalBytes = PiperModelManager.totalBytes;
  static const kokoroWeight = _kokoroApproxTotalBytes /
      (_kokoroApproxTotalBytes + _piperApproxTotalBytes);
  static const piperWeight = 1 - kokoroWeight;

  static double combinedProgress(double kokoroFrac, double piperFrac) =>
      (kokoroFrac * kokoroWeight + piperFrac * piperWeight).clamp(0.0, 1.0);

  static Future<bool> isFullyReady() async =>
      await KokoroModelManager.instance.isReady(kokoroSupportedLanguages) &&
      await PiperModelManager.instance.isReady();

  static void startAll() {
    KokoroModelManager.instance.startDownload(kokoroSupportedLanguages);
    PiperModelManager.instance.startDownload();
  }
}
