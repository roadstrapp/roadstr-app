/// Static catalogue of the Kokoro-82M model assets Roadstr needs.
///
/// Source: https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX
/// model_q8f16.onnx is the int8/fp16 quantized variant (~82 MB) — best
/// size/quality balance for a mobile download (full fp32 model is ~326 MB).
library;

/// Immutable upstream revision. Asset hashes below are the final authority;
/// pinning also prevents an upstream branch update from causing avoidable
/// re-download failures.
const String kKokoroRevision = 'a70f0e45c1cc0df9abdfbfa0f6dee9073579ee99';
const String kKokoroRepoBaseUrl =
    'https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/$kKokoroRevision';

const String kKokoroModelFile = 'onnx/model_q8f16.onnx';
const int kKokoroModelSizeBytes = 86033585;
const String kKokoroModelSha256 =
    '04c658aec1b6008857c2ad10f8c589d4180d0ec427e7e6118ceb487e215c3cd0';

const String kKokoroTokenizerFile = 'tokenizer.json';
const int kKokoroTokenizerSizeBytes = 3497;
const String kKokoroTokenizerSha256 =
    '77a02c8e164413299b4b4c403b14f8e0e1c1b727db4d46a09d6327b861060a34';

/// Voice catalogue: language -> {gender -> voice name}. Voice file naming
/// follows Kokoro's convention: first letter = language/accent, second =
/// gender (f/m). All voice embeddings are a uniform 522 240 bytes.
///
/// Not every language ships both genders in Kokoro-82M v1.0 — French only
/// has a female voice (`ff_siwis`); no `fm_*` exists in the model at all.
/// Use [kokoroVoiceName] rather than indexing this map directly so that
/// gap is handled with a graceful fallback instead of a null voice.
const Map<String, Map<String, String>> kKokoroVoicesByLanguage = {
  'en': {'f': 'af_heart', 'm': 'am_michael'},
  'it': {'f': 'if_sara', 'm': 'im_nicola'},
  'es': {'f': 'ef_dora', 'm': 'em_alex'},
  'fr': {'f': 'ff_siwis'}, // no male voice shipped for French in this model
  'ja': {'f': 'jf_alpha', 'm': 'jm_kumo'},
  'zh': {'f': 'zf_xiaobei', 'm': 'zm_yunxi'},
  'pt': {'f': 'pf_dora', 'm': 'pm_alex'},
};

const int kKokoroVoiceSizeBytes = 522240;
const Map<String, String> kKokoroVoiceSha256 = {
  'af_heart':
      'd583ccff3cdca2f7fae535cb998ac07e9fcb90f09737b9a41fa2734ec44a8f0b',
  'am_michael':
      '1d1f21dd8da39c30705cd4c75d039d265e9bc4a2a93ed09bc9e1b1225eb95ba1',
  'ef_dora': 'f66ec66bd295acb18372e37008533a9a3228483ccd294e7538d5d9294ac9a532',
  'em_alex': '27809e9eafdcbcfff90a3016c697568676531de2a2c39cee29c96c7bd6b83e95',
  'ff_siwis':
      'a35f5675ad08948e326ae75fd0ea16ba5d0042e4f76b5f3d1df77d0a48c54861',
  'if_sara': '409b69248798fcdc2542330c76953d230710f19b057e59cb82fdc3c4cf71265c',
  'im_nicola':
      'bc578e510d52a96d6940d46f12e96d7b3df00905dbea075113226d100e6e1ab0',
  'jf_alpha':
      '56b479360aad9f367aeb8cef908f9201cf48b4555e488c5f4590c9dfcd978bb6',
  'jm_kumo': '09e959d239724c734d65661f06f14cdabcddfd476bfaaad905a937099ae9e64f',
  'pf_dora': '3da7b5b2d91847ebf5646f57631af6ececae3c29a89cd300f06edf9aa6cfe9ee',
  'pm_alex': '0175c753f59c54e7fd5a995bedef0c5ff2fb67e0043dd3dcb2ae74ec2acbeb2a',
  'zf_xiaobei':
      '5dde6e1c9c4f12c8b327bc29c0cee361a23b52b952c04636858ba637ec66e640',
  'zm_yunxi':
      '7243892fb4e560d47014090ddf010f8b8b790f3c6b029ff82b2ac06aa4e27c8b',
};

/// Default voice gender when the user hasn't chosen one yet.
const String kKokoroDefaultGender = 'f';

/// Discrete speech-speed multipliers for the 6-stage settings slider.
/// Index 2 (1.0×) is the default/normal speed.
const List<double> kKokoroSpeedStages = [0.7, 0.85, 1.0, 1.15, 1.3, 1.5];
const int kKokoroDefaultSpeedStage = 2;

/// Languages with on-device neural voice guidance — derived directly from
/// [kKokoroVoicesByLanguage]. main.dart uses this to gate the first-launch
/// "voice unavailable" notice.
Set<String> get kokoroSupportedLanguages =>
    kKokoroVoicesByLanguage.keys.toSet();

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

String kokoroVoiceFileForName(String voiceName) => 'voices/$voiceName.bin';
