# Kotlin rewrite baseline

Audit date: 2026-09-25 (Europe/Rome)

This document records the repository state before any native Kotlin cutover. The
Flutter/Dart implementation remains the behavioral oracle and has not been
removed or moved.

## Git baseline

| Item | Value |
|---|---|
| Working branch | `rewrite/kotlin-native` |
| Baseline commit | `ad5476e35358ea3f519d2ae71bea1bd28d4763a8` |
| `main` at audit | same SHA |
| `origin/main` at audit | same SHA after `git fetch origin main` |
| Initial working tree | clean |
| Main modified | no |
| Tags at baseline | `v0.5.11` points to `c1eb02f`; baseline includes later commits through `ad5476e` |

All rewrite work must remain on this branch. No tag, release, force-push or
merge is authorized by this audit.

## Product and build identity

| Property | Current value / evidence |
|---|---|
| Product | Roadstr, Android-only, Flutter/Dart |
| Label | `Roadstr` |
| `applicationId` | `app.roadstr` (`android/app/build.gradle.kts`) |
| Namespace | `app.roadstr` |
| Version | `0.5.11+2050` in `pubspec.yaml` |
| Android version | `versionName "0.5.11"`, `versionCode 2050` |
| `compileSdk` | 36 |
| `targetSdk` | 36, supplied by the installed Flutter Gradle extension |
| `minSdk` | 24, supplied by the installed Flutter Gradle extension; launcher config says 21 but the effective app value is 24 |
| Java source/target | 17 |
| Kotlin JVM target | 17 |
| Android Gradle Plugin | 9.0.1 |
| Gradle wrapper | 9.1.0 |
| NDK | `27.1.12297006` is forced for subprojects; Flutter's default is overridden |
| Flutter/Dart lock floor | Flutter >=3.44.0, Dart >=3.12.0 |
| License | GPL-3.0-only on `main`; MIT from the `kotlin-native` branch on |
| Locales | 27 ARB sets and 27 generated localization classes |

The Android module currently applies the Flutter Gradle plugin. A future native
module must keep the identity values, and must choose a higher versionCode than
2050 for any installable candidate.

## Current architecture

- `lib/` contains 113 Dart sources. The production UI is Flutter.
- There are two user-selectable map implementations: MapLibre (`maplibre`) and
  the legacy raster `flutter_map` renderer. MapLibre is the default.
- Android contains a small Kotlin `MainActivity`/Flutter embedding layer and
  committed eSpeak NG libraries for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- Location uses the vendored de-Googled `geolocator_android` fork and Android
  `LocationManager`; Gradle excludes `com.google.android.gms` and
  `com.google.android.play` groups.
- Persistence is currently concentrated in an encrypted Hive `settings` box,
  with Android Keystore-backed Flutter secure storage for secrets.

The README and source identify shipped areas including routing, search/POI,
MapLibre/raster maps, Nostr road events, Amber/nsec identity, Lightning/NWC,
GPS lifecycle, navigation notifications, Kokoro/Piper voice, profile/inbox,
favourites, parking, settings, themes, 27 languages and privacy hardening.

## Dependencies relevant to the rewrite

Resolved versions from `pubspec.lock`:

| Component | Version |
|---|---:|
| `flutter_secure_storage` | 10.3.1 |
| `hive` / `hive_flutter` | 2.2.3 / 1.1.0 |
| `maplibre` | 0.3.6 |
| `nostr_tools` | 1.0.9 |
| `amberflutter` | 0.0.9 |
| `flutter_onnxruntime` | 1.8.3 |
| `web_socket_channel` | 2.4.0 |
| `pointycastle` | 3.9.1 |
| `geolocator_android` | 5.0.3, local path override |

## Signing and release evidence

The release path is `build_release.sh`. It requires external
`android/key.properties`, resolves the configured keystore, reads the expected
certificate fingerprint with `keytool`, builds obfuscated split APKs, and
verifies every APK with `apksigner`. The repository deliberately does not carry
`android/key.properties`; two ignored local `.jks` files are present but their
official status and certificate fingerprint were not inferred or exposed.

Therefore signing compatibility is **not proven** by this audit. The official
Roadstr signing certificate/key owner must provide a controlled verification
fixture before an upgrade APK can be accepted.

F-Droid metadata is in `metadata/app.roadstr.yml`, pinned to commit
`c1eb02f0c26cf6e8e391bcaccfae55a739483dfb` for 0.5.11 and building the Flutter
universal APK. ZapStore metadata is in `zapstore.yaml`. Fastlane metadata and
changelogs are present.

## Test baseline

Commands run on this branch:

```text
flutter analyze  -> No issues found
flutter test     -> 519 tests passed
```

There are 59 Dart test files, plus fixtures/goldens. No `adb` command was run.
No signed APK upgrade test was run because no official signing configuration
was available in the checkout. Physical-device installation remains a manual
user-controlled step.

## Security baseline to preserve

- `android:allowBackup="false"`.
- Global cleartext traffic disabled, with only `localhost`, `127.0.0.1` and
  `10.0.2.2` allowed for self-hosted GraphHopper.
- No Firebase, analytics or Google Play Services dependency.
- Release `debugPrint` is silenced and production R8/resource shrinking is on.
- Sensitive identity, NWC and sync data are not stored in ordinary Hive
  preferences in the current code path.
- HTTP and relay inputs have explicit size, timeout, redirect and retry bounds.
- eSpeak native assets have build-time SHA-256 verification.

These are observed properties, not permission to weaken them during the port.

