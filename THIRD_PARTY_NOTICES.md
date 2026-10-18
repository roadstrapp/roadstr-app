# Third-party notices

Roadstr's own source code is released under the [MIT License](LICENSE).
Everything below is third-party material that keeps **its own** licence.

## Bundled in the app and licensed differently

| Component | Where | Licence |
|---|---|---|
| [eSpeak NG](https://github.com/espeak-ng/espeak-ng) — phonemizer used by the on-device voice | `android/app/src/main/jniLibs/*/libespeak-ng.so`, `assets/espeak-ng-data.tar.gz`, built by `tools/build_espeak_android.sh` | **GPL-3.0-or-later** — full text in [third_party/licenses/GPL-3.0.txt](third_party/licenses/GPL-3.0.txt) |
| [GeoNames](https://www.geonames.org/) cities and administrative names | `android/app/src/main/assets/offline_catalog/geonames-cities15000.tsv.gz`, rebuilt by `tools/offline_packages/build_geonames_catalog.sh` | **CC BY 4.0** — © GeoNames contributors |

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
| [Valhalla](https://github.com/valhalla/valhalla) and [valhalla-mobile](https://github.com/Rallista/valhalla-mobile) | MIT |
| [Moshi](https://github.com/square/moshi) | Apache-2.0 |
| [Planetiler](https://github.com/onthegomap/planetiler) — build-time package generator, not shipped in the APK | Apache-2.0 |
| [VersaTiles](https://github.com/versatiles-org/versatiles) CLI — optional build-time Shortbread/PMTiles converter, not shipped in the APK | Unlicense |
| [OkHttp](https://github.com/square/okhttp) | Apache-2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 |
| [Bouncy Castle](https://www.bouncycastle.org/) | MIT |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | MIT |
| [transcribe.cpp](https://github.com/handy-computer/transcribe.cpp) and the ggml it contains — the offline speech engine, built as `libofflinestt_*.so` ([third_party/offline-stt-engine](third_party/offline-stt-engine/README.md)) | MIT |
| Rust crates compiled into that engine (`jni`, `android_logger`, `log`, `once_cell`, `thiserror`, `regex` and their dependencies) | MIT and/or Apache-2.0 |
| LLVM libc++ (`libc++_shared.so`, from the Android NDK) | Apache-2.0 with LLVM exception |
| AndroidX / Jetpack Compose | Apache-2.0 |
| Flutter and its plugins (production app) | BSD-3-Clause and the licences listed by `flutter pub deps` |

## Downloaded on request, not shipped in the APK

| Component | Where | Licence |
|---|---|---|
| NVIDIA Parakeet TDT 0.6B v3 speech model, converted and quantised to GGUF Q4_K_M by [handy-computer](https://huggingface.co/handy-computer/parakeet-tdt-0.6b-v3-gguf); Roadstr uses the converted file unmodified | `parakeet-tdt-0.6b-v3-Q4_K_M.gguf`, fetched once from `huggingface.co` when the user taps *Download the model* in Settings, size and SHA-256 checked first | **CC-BY-4.0** — © NVIDIA; <https://creativecommons.org/licenses/by/4.0/>. The conversion to GGUF/Q4_K_M is a change made by handy-computer. The attribution is also shown in Settings. |

Nothing the user says is sent anywhere: recognition runs on the phone. See
[docs/offline-voice-search.md](docs/offline-voice-search.md).

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
| [Geofabrik](https://download.geofabrik.de/) Shortbread MBTiles extracts and worldwide index | OSM data under ODbL; provider attribution retained in each installed package |
| [VersaTiles](https://docs.versatiles.org/) Shortbread schema used by optional visual PMTiles packages | Shortbread/tileset terms as published by the selected dataset; generated OSM data remains subject to ODbL attribution |
| [OpenMapTiles](https://openmaptiles.org/) schema used by optional PMTiles packages | BSD-3-Clause; generated map data remains subject to ODbL attribution |
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) voice model | Apache-2.0 |
| Piper voice "Thorsten-Voice" (German), see [Thorsten-Voice](https://www.thorsten-voice.de/) | MIT |

The Flutter and the native Kotlin builds share this list; the complete,
machine-readable inventory of each build is generated from its dependency lock.
