import 'kokoro/kokoro_voices.dart';
import 'piper/piper_voices.dart';

/// Every language with on-device neural voice guidance, regardless of which
/// engine (Kokoro or Piper) actually produces it. UI code that only needs to
/// know "does this language get a voice" — the first-launch notice, the
/// settings/onboarding preview fallback — should use this rather than
/// reaching into one engine's own language set and forgetting the other.
Set<String> get voiceGuidanceLanguages =>
    {...kokoroSupportedLanguages, ...kPiperSupportedLanguages};
