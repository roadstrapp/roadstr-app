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

/// Voice catalogue: language -> {gender -> voice name}. Voice file naming
/// follows Kokoro's convention: first letter = language/accent, second =
/// gender (f/m). All voice embeddings are a uniform 522 240 bytes.
///
/// Not every language ships both genders in Kokoro-82M v1.0 — French only
/// has a female voice (`ff_siwis`); no `fm_*` exists in the model at all.
/// Use [kokoroVoiceName] rather than indexing this map directly so that
/// gap is handled with a graceful fallback instead of a null voice.
const Map<String, Map<String, String>> kKokoroVoicesByLanguage = {
  'en': {'f': 'af_heart',   'm': 'am_michael'},
  'it': {'f': 'if_sara',    'm': 'im_nicola'},
  'es': {'f': 'ef_dora',    'm': 'em_alex'},
  'fr': {'f': 'ff_siwis'},  // no male voice shipped for French in this model
  'ja': {'f': 'jf_alpha',   'm': 'jm_kumo'},
  'zh': {'f': 'zf_xiaobei', 'm': 'zm_yunxi'},
  'pt': {'f': 'pf_dora',    'm': 'pm_alex'},
};

const int kKokoroVoiceSizeBytes = 522240;

/// Default voice gender when the user hasn't chosen one yet.
const String kKokoroDefaultGender = 'f';

/// Discrete speech-speed multipliers for the 6-stage settings slider.
/// Index 2 (1.0×) is the default/normal speed.
const List<double> kKokoroSpeedStages = [0.7, 0.85, 1.0, 1.15, 1.3, 1.5];
const int kKokoroDefaultSpeedStage = 2;

/// Languages with on-device neural voice guidance — derived directly from
/// [kKokoroVoicesByLanguage]. main.dart uses this to gate the first-launch
/// "voice unavailable" notice.
Set<String> get kokoroSupportedLanguages => kKokoroVoicesByLanguage.keys.toSet();

/// True if [lang] has a male voice available (used to show/hide the gender
/// toggle in Settings — French does not).
bool kokoroHasGenderChoice(String lang) =>
    kKokoroVoicesByLanguage[lang]?.containsKey('m') ?? false;

/// Resolves the voice name for [lang]/[gender]. Falls back to the female
/// voice (always present) when the requested gender doesn't exist for that
/// language, and to null only if the language itself isn't supported.
String? kokoroVoiceName(String lang, String gender) {
  final voices = kKokoroVoicesByLanguage[lang];
  if (voices == null) return null;
  return voices[gender] ?? voices['f'];
}

/// Every distinct voice name across all supported languages/genders — used
/// to download the full catalogue upfront so switching gender in Settings
/// never needs a fresh download.
Iterable<String> get kokoroAllVoiceNames =>
    kKokoroVoicesByLanguage.values.expand((m) => m.values).toSet();

String kokoroVoiceFileForName(String voiceName) => 'voices/$voiceName.bin';
