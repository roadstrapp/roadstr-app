# Third-party notices

Roadstr's own source code is released under the [MIT License](LICENSE).
Everything below is third-party material that keeps **its own** licence.

## Bundled in the app and licensed differently

| Component | Where | Licence |
|---|---|---|
| [eSpeak NG](https://github.com/espeak-ng/espeak-ng) — phonemizer used by the on-device voice | `android/app/src/main/jniLibs/*/libespeak-ng.so`, `assets/espeak-ng-data.tar.gz`, built by `tools/build_espeak_android.sh` | **GPL-3.0-or-later** — full text in [third_party/licenses/GPL-3.0.txt](third_party/licenses/GPL-3.0.txt) |

The same library contains [Sonic](https://github.com/waywardgeek/sonic) (speech-rate
change, Apache-2.0).

eSpeak NG is loaded at run time (`dlopen`) by Roadstr's voice code and is never
copied into Roadstr's source files, which stay MIT. Because the app **binary** ships
this GPL component, a distributed APK has to honour the GPL for that component:
its complete corresponding source is the upstream project at the revision named in
`tools/build_espeak_android.sh`, together with that build script. Whoever
redistributes an APK is responsible for keeping that source available. Building a
variant without the on-device voice (or with another phonemizer) removes the
requirement.

## Libraries linked into the app

| Library | Licence |
|---|---|
| [MapLibre Native](https://github.com/maplibre/maplibre-native) | BSD-2-Clause |
| [OkHttp](https://github.com/square/okhttp) | Apache-2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 |
| [Bouncy Castle](https://www.bouncycastle.org/) | MIT |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | MIT |
| AndroidX / Jetpack Compose | Apache-2.0 |
| Flutter and its plugins (production app) | BSD-3-Clause and the licences listed by `flutter pub deps` |

## Data and models downloaded at run time

| Asset | Licence |
|---|---|
| Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors | ODbL |
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) voice model | Apache-2.0 |
| Piper voice "Thorsten-Voice" (German), see [Thorsten-Voice](https://www.thorsten-voice.de/) | MIT |

The Flutter and the native Kotlin builds share this list; the complete,
machine-readable inventory of each build is generated from its dependency lock.
