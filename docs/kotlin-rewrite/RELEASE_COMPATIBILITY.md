# Release and in-place update compatibility

This is a release-blocking contract. The Kotlin implementation is not ready to
ship until every item below is verified with a signed APK.

## Identity contract

| Contract | Current baseline | Native candidate requirement |
|---|---|---|
| Package/application ID | `app.roadstr` | Exact match |
| Namespace | `app.roadstr` | Keep package-level identity stable |
| Label | `Roadstr` | Exact match |
| Version | `0.5.11`, code `2050` | Higher monotonically increasing code; no final value chosen yet |
| Signing certificate | Official key external to checkout | Must be the same certificate as current official APKs |
| Launcher assets | Existing mipmap/adaptive resources | Preserve unless explicitly approved |
| Backup/privacy policy | Backup disabled; cleartext narrowly scoped | Preserve or document an approved improvement |

The answer to the required question — whether a branch APK can install directly
over a current Flutter install and preserve all data — is currently **not yet
demonstrably yes**. The APK has not been built as native, and the official
certificate is not available for verification in this checkout.

## Current release channels

- `build_release.sh` requires `android/key.properties`, checks the keystore
  certificate, builds obfuscated release APKs, verifies signatures and prints
  SHA-256 checksums.
- Gradle enables ABI splits for `arm64-v8a`, `armeabi-v7a`, `x86_64` and a
  universal APK. The Flutter split-per-ABI command may apply version-code
  offsets; F-Droid intentionally builds the universal APK without that flag.
- `metadata/app.roadstr.yml` is F-Droid metadata for `app.roadstr`, GPL-3.0,
  version 0.5.11/code 2050, pinned to a full commit.
- `zapstore.yaml` points at the GitHub repository; Fastlane metadata/changelogs
  are present.
- eSpeak shared libraries and its data archive are checked against committed
  SHA-256 values. Any native rewrite must retain equivalent verification.

## Required signed-APK checklist

The user owns physical-device testing. Do not use `adb` from the rewrite work.
For each supported ABI and the universal fallback, the user should manually:

1. Install the current official Flutter APK and populate representative state.
2. Record package, version, signing certificate, npub, favourites, history,
   parking, settings, NWC, voice assets and onboarding state.
3. Install the Kotlin APK directly over it, without uninstalling or clearing
   data.
4. Confirm Android accepts the update and the package/signature remain stable.
5. Exercise the migration matrix in `MIGRATION_PLAN.md`.
6. Kill/restart during migration in a controlled fixture run and verify rollback.
7. Verify voice files are reused and no new permission/onboarding prompt appears.
8. Verify notification, background navigation, Amber, nsec, NWC, search,
   routing, maps and all user-visible settings.
9. Capture `apksigner` certificate output, APK version data and checksums for
   the release record.

No tag, GitHub release, ZapStore publish or F-Droid submission is allowed from
this branch without explicit approval.

## Native build requirements

The native Gradle build must retain literal version values readable by F-Droid,
port the metadata/output paths, preserve release signing configuration, keep
R8/resource shrinking, produce the required ABI artifacts, and include a
repeatable certificate/checksum verification step. A debug-signed APK must never
be presented as an official upgrade.

