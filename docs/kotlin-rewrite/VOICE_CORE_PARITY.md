# Native voice core parity

The shared Kotlin voice core now has a live owner in the independent road-test
APK. The ordinary Flutter launcher and production speech owner are unchanged;
this is executable cutover evidence, not a production-package switch.

## Implemented boundary

- `NativeVoiceCatalog` mirrors the shipped Kokoro/Piper registry: seven
  Kokoro languages, German Piper, 13 Kokoro voices, gender fallback, six speed
  stages, sample rates, pinned revisions and the exact 17 downloadable assets.
  Together with the eSpeak extraction sentinel, all 18 legacy reusable paths
  remain recognized.
- `NativeVoiceAssetStore` verifies an existing regular file by exact byte
  length and SHA-256 before reuse. Paths are confined to app documents and
  symbolic links are rejected.
- `NativeVoiceAssetDownloader` uses 20-second connect, 30-second inactivity
  and 10-minute whole-call deadlines. It streams into a same-directory
  `.part` file, enforces the declared and observed size, verifies SHA-256,
  calls `fsync`, then atomically replaces the destination where supported.
  Valid legacy files are never downloaded or rewritten.
- `NativeVoiceInferencePolicy` builds bounded Kokoro and Piper tensor inputs,
  validates Kokoro voice shape, preserves Piper scale semantics, limits
  phonemizer input/output, applies the existing Italian IPA corrections and
  emits bounded mono PCM16 WAV data.
- `NativeVoiceGuidanceSession` is a side-effect-free scheduling state machine.
  It preserves ambient and manoeuvre deduplication windows, minimum audible
  time, imminent-instruction interruption, stale advance rejection, a
  two-item newest queue, completion fencing and shared focus leases for
  overlapping external cues.
- `NativeVoiceAudioFocusController` packages the Android navigation-guidance
  speech focus contract, including delayed gain and pre-Android 8 fallback.
- `NativeRoadTestVoiceGateway` verifies or downloads the existing files under
  the legacy-compatible `app_flutter` documents root, extracts the checksummed
  eSpeak data archive with tar-slip and size bounds, and invokes the pinned
  `libespeak-ng.so` through a small UTF-8 JNI bridge.
- Kokoro and Piper execute through pinned ONNX Runtime Android 1.23.0 sessions
  and the existing tensor policies. A bounded 24-entry memory-only cache avoids
  repeating inference without persisting spoken street or place names.
- Mono PCM is converted in memory and played through `AudioTrack` with native
  navigation-guidance attributes, transient/delayed audio focus, cancellation,
  mute, stop and Activity lifecycle cleanup.
- The standalone route owner emits speed/mode-aware far and point-of-action
  cues, plus localized start and arrival announcements. The settings and
  onboarding download actions expose verified model installation progress.
- The standalone build admits only arm64-v8a, armeabi-v7a and x86_64, verifies
  all committed eSpeak binaries/data before packaging and includes matching
  ONNX/eSpeak/JNI libraries for every admitted ABI.

## Evidence

- 31 JVM cases cover the catalogue, hashes/sizes, language and gender matrix,
  asset reuse and installation, partial cleanup, unsafe files, input bounds,
  WAV output, IPA correction, speed/mode cue thresholds, scheduling,
  interruption, deduplication, focus leases and reinitialization timing.
- Flutter contracts compare the registry/policies with production Dart and
  lock the standalone ONNX/JNI/AudioTrack owner, model download wiring,
  ABI/assets and absence of any Flutter runtime dependency.
- Kotlin, CMake and APK assembly are green. Direct ZIP inspection confirms all
  three copies of `libonnxruntime.so`, `libespeak-ng.so` and
  `libroadstr_voice_jni.so`, the packaged eSpeak data and no Flutter engine or
  bundle.

## Still open

- add fixed-phrase prewarm and privacy-bounded disk-cache ownership;
- connect speed-camera beeps/external-cue leases and persisted production
  settings; the road-test owner currently uses in-memory settings;
- verify downloads, focus transitions, interruption and output on physical
  devices, including process recreation and adverse networks;
- gather latency, memory, thermal, battery and accessibility evidence.

Until those gates are complete, Flutter remains the production-package voice
owner. The Kotlin implementation is active only in the side-by-side road-test
APK.
