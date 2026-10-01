# Native voice core parity

This increment adds a dormant Kotlin voice core without changing the Flutter
launcher, navigation owner or current speech playback. It is a verified
cutover boundary, not a production voice-engine switch.

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
  No production component constructs it.

## Evidence

- 28 JVM cases cover the catalogue, hashes/sizes, language and gender matrix,
  asset reuse and installation, partial cleanup, unsafe files, input bounds,
  WAV output, IPA correction, scheduling, interruption, deduplication, focus
  leases and reinitialization timing.
- Four Flutter contracts compare the native registry and policies with the
  production Dart sources and prove that the downloader, scheduler and audio
  focus controller have no shell or startup owner.

## Still open

- create and lifecycle-manage real ONNX Runtime Kokoro/Piper sessions;
- connect the packaged eSpeak libraries through a bounded JNI phonemizer;
- add phrase cache/prewarm ownership and real audio playback/cancellation;
- connect route instructions, settings, beeps and Bluetooth behavior;
- verify downloads, focus transitions, interruption and output on physical
  devices, including process recreation and adverse networks;
- gather latency, memory, thermal, battery and accessibility evidence.

Until those gates are complete, Flutter remains the only production voice
owner and the native classes remain dormant.
