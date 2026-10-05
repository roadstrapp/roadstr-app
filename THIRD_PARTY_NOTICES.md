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

## Optional build variant: in-app browser

Only the variant built with `native-android/gradlew-geckoview` contains these; the default build and
the Flutter app do not.

| Component | Where | Licence |
|---|---|---|
| [GeckoView](https://geckoview.dev/) 157 (Mozilla), unmodified binary from `https://maven.mozilla.org/maven2` | `org.mozilla.geckoview:geckoview-arm64-v8a:157.0.20260924084938`, source at the revision named in its POM (`hg.mozilla.org/releases/mozilla-release`) | **MPL-2.0** — file-level copyleft; Roadstr's own files stay MIT |
| kotlin-stdlib, AndroidX (core, lifecycle, annotation, collection), snakeyaml | pulled in by GeckoView | Apache-2.0 |

GeckoView's POM also depends on `com.google.android.gms:play-services-fido` (Google's proprietary
terms). The variant excludes it, and the APK defines no `com.google.android.gms` class; four
references to its types remain inside GeckoView's WebAuthn code, which never loads because WebAuthn
is switched off. `native-android/verify-geckoview-variant.sh` checks this. GeckoView's exported
clipboard provider is removed from the merged manifest.

The bundled page extension (`native-android/app/src/gecko/assets/web/place-extractor/`) is Roadstr's
own work, MIT.

## Services Roadstr can talk to

| Service | Use | Note |
|---|---|---|
| [SearXNG](https://docs.searxng.org/) (AGPL-3.0, server software) | optional web results, through an instance **the user chooses** | HTTP API only: no SearXNG code is linked, embedded or shipped, and no instance is built in |
| [Nominatim](https://nominatim.org/) and [Overpass](https://overpass-api.de/) | place search | ODbL data; the Nominatim usage policy is followed (1.1 s between calls, identifying user agent, no autocomplete) |

## Data and models downloaded at run time

| Asset | Licence |
|---|---|
| Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors | ODbL |
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) voice model | Apache-2.0 |
| Piper voice "Thorsten-Voice" (German), see [Thorsten-Voice](https://www.thorsten-voice.de/) | MIT |

The Flutter and the native Kotlin builds share this list; the complete,
machine-readable inventory of each build is generated from its dependency lock.
