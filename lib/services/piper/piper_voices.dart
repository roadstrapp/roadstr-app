/// Piper TTS asset catalogue — a second, independent neural voice engine
/// used only for German, which Kokoro-82M does not support at all (its
/// shipped voice packs cover en/it/es/fr/ja/zh/pt only, see
/// ../kokoro/kokoro_voices.dart).
///
/// Piper (github.com/OHF-Voice/piper1-gpl, GPL-3.0) is a VITS-based
/// text-to-speech engine with a completely different ONNX input/output
/// contract than Kokoro (see PiperEngine), so it gets its own model file,
/// download manager and engine rather than being squeezed into Kokoro's.
///
/// Voice: Thorsten-Voice's "Martin" — a long-running, dedicated open-source
/// German TTS project (thorsten-voice.de). MIT-licensed, single male voice,
/// medium quality (22.05 kHz). No German female voice is bundled: unlike
/// Kokoro's per-language {f, m} catalogue, this is one voice, one gender —
/// closer to how French only ships a female Kokoro voice.
library;

/// Immutable upstream revision (huggingface.co/Thorsten-Voice/Piper).
const String kPiperRevision = '2c11851a427461f4e6a14b5c389386ffb0b01bc7';
const String kPiperRepoBaseUrl =
    'https://huggingface.co/Thorsten-Voice/Piper/resolve/$kPiperRevision';

const String kPiperModelFile = 'de_DE-thorsten-medium.onnx';
const int kPiperModelSizeBytes = 63201294;
const String kPiperModelSha256 =
    '7e64762d8e5118bb578f2eea6207e1a35a8e0c30595010b666f983fc87bb7819';

const String kPiperConfigFile = 'de_DE-thorsten-medium.onnx.json';
const int kPiperConfigSizeBytes = 4819;
const String kPiperConfigSha256 =
    '974adee790533adb273a1ac88f49027d2a1b8f0f2cf4905954a4791e79264e85';

/// Languages Piper covers in Roadstr today — just German.
const Set<String> kPiperSupportedLanguages = {'de'};

/// Piper's own inference defaults (from the voice's onnx.json `inference`
/// block) — how much the pitch/duration/energy vary. Fixed rather than
/// re-read from the config file at runtime: they never change without a
/// model swap, and hardcoding avoids parsing the config twice.
const double kPiperNoiseScale = 0.667;
const double kPiperNoiseW = 0.8;

/// Audio sample rate the model was trained/exported at.
const int kPiperSampleRate = 22050;
