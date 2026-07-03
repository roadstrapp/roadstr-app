/// Static catalogue of the Kokoro-82M model assets Roadstr needs.
///
/// Source: https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX
/// model_q8f16.onnx is the int8/fp16 quantized variant (~82 MB) — best
/// size/quality balance for a mobile download (full fp32 model is ~326 MB).
library;

const String kKokoroRepoBaseUrl =
    'https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main';

const String kKokoroModelFile = 'onnx/model_q8f16.onnx';
const int kKokoroModelSizeBytes = 86033585;

const String kKokoroTokenizerFile = 'tokenizer.json';

/// One curated voice per Roadstr-supported language.
/// Voice file naming follows Kokoro's convention: first letter = language/
/// accent, second letter = gender (f/m). All voice embeddings are a uniform
/// 522 240 bytes regardless of language.
const Map<String, String> kKokoroVoiceByLanguage = {
  'en': 'af_heart',   // American English, female
  'it': 'if_sara',    // Italian, female
  'es': 'ef_dora',    // Spanish, female
  'fr': 'ff_siwis',   // French, female
  'ja': 'jf_alpha',   // Japanese, female
  'zh': 'zf_xiaobei', // Mandarin Chinese, female
  'pt': 'pf_dora',    // Brazilian Portuguese, female
};

const int kKokoroVoiceSizeBytes = 522240;

/// Languages with on-device neural voice guidance. Kept in sync with
/// [kKokoroVoiceByLanguage]'s keys and with `kVoiceSupportedLanguages` in
/// main.dart (which gates the first-launch "voice unavailable" notice).
Set<String> get kokoroSupportedLanguages => kKokoroVoiceByLanguage.keys.toSet();

String kokoroVoiceFile(String languageCode) =>
    'voices/${kKokoroVoiceByLanguage[languageCode]}.bin';
