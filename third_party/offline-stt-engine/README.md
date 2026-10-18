# Offline speech engine

The engine behind Roadstr's offline voice search: one utterance of 16 kHz mono audio in, text out,
on the phone, with no network and no Google component. It lives in its own repository so other
apps can use it too, and is vendored here as source plus pinned binaries.

* **Source:** [`engine/`](https://github.com/roadstrapp/offline-speech-to-text/tree/2e68fe23db25cfe6e8d768f12a28c94ba75ceffa/engine)
  of `roadstrapp/offline-speech-to-text`, at the commit in [SOURCE_COMMIT](SOURCE_COMMIT). That
  repository is a copy of *Offline Speech to Text* by Noah Mühl (MIT) with `engine/` added;
  see its `FORK.md` for how it stays in sync with the original.
* **Licence:** MIT. Built on [transcribe.cpp](https://github.com/handy-computer/transcribe.cpp)
  (MIT, ggml inside), `jni`, `android_logger` and small Rust crates that are all MIT and/or
  Apache-2.0. `libc++_shared.so` is the NDK's LLVM libc++ (Apache-2.0 with LLVM exception).

## What is committed here

| Path | What |
|---|---|
| `android/app/src/main/jniLibs/arm64-v8a/libofflinestt_armv8.so` | Baseline Armv8-A build, runs on every arm64 phone |
| `…/libofflinestt_dotprod.so` | Armv8.2-A + dot-product + fp16 build, much faster where the CPU has them |
| `…/libofflinestt_cpu_probe.so` | Reads `AT_HWCAP` and tells the loader which of the two is safe |
| `…/libc++_shared.so` | The C++ runtime both builds link against |
| `android/app/src/main/kotlin/app/roadstr/offlinestt/` | The Kotlin API, copied unchanged from the source repository |

arm64-v8a only. On 32-bit Arm and x86 the engine is simply absent and the microphone button keeps
using the phone's own speech recogniser.

## Why binaries are committed, and how that is kept honest

Same approach as eSpeak NG ([`tools/build_espeak_android.sh`](../../tools/build_espeak_android.sh)):
normal builds stay offline, and the Gradle build refuses to start unless every file matches the
SHA-256 in `android/app/build.gradle.kts` (`bundledNativeChecksums`). [`SHA256SUMS`](SHA256SUMS) lists
the same values plus the Kotlin sources.

[`rebuild.sh`](rebuild.sh) clones the pinned source, builds it, and compares every file with
`SHA256SUMS`. The build is reproducible: building from a different directory gave byte-identical
libraries. Run it after changing anything here.

## The model

No model ships in the APK. It is downloaded once, on request, from Settings.

| | |
|---|---|
| Model | NVIDIA Parakeet TDT 0.6B v3, Q4_K_M GGUF conversion by handy-computer |
| File | `parakeet-tdt-0.6b-v3-Q4_K_M.gguf` |
| Size | 485425504 bytes |
| SHA-256 | `b68557be1e3c40207fd7c4bd9d63f1d3316b963f15325bfb0cc16a8bb0ffd181` |
| URL | `https://huggingface.co/handy-computer/parakeet-tdt-0.6b-v3-gguf/resolve/85ac09ea12fc4b1112fa76810059364bc6adc9de/parakeet-tdt-0.6b-v3-Q4_K_M.gguf` |
| Licence | **CC-BY-4.0**, attribution shown in Settings and in `THIRD_PARTY_NOTICES.md` |
| Languages | bg hr cs da nl en et fi fr de el hu it lv lt mt pl pt ro sk sl es sv ru uk |

The URL names an exact upstream commit, and the download is checked for size and SHA-256 before
the file is allowed to exist under its final name. `SpeechModelCatalog.kt` and this table are kept
equal by `test/native_offline_speech_contract_test.dart`.

## What was measured, and what was not

* The engine decodes real Italian speech with the real model on a desktop CPU; see
  `engine/README.md` in the source repository for the numbers.
* **Not measured: anything on a phone.** Decode time, memory use and battery cost on a real device
  are unknown. The memory thresholds in `SpeechModelCatalog` (3 GB total, 1.5 GB free) are
  conservative estimates, not measurements.
* **Not measured: a real microphone**, in a car or anywhere else. Recognition quality was checked
  on synthesised speech only.
