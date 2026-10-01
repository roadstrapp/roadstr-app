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
demonstrably yes**. The private native shell is packaged in the debug APK, but
no candidate has been signed with the official certificate or installed over a
supported production build. Signing material remains external to the checkout.

## Current release channels

- `build_release.sh` first rejects source drift and a candidate code at or below
  2050, then requires `android/key.properties`, builds the four obfuscated APKs
  without Flutter ABI version offsets, and runs the common artifact auditor.
- `tools/kotlin_rewrite/audit_android_release.sh` checks source identity,
  version agreement, hardening, ABI/store contracts and, for built artifacts,
  package/version/label, exact native ABI contents, signature certificate and
  SHA-256. It writes an atomic tab-separated release manifest.
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

The source and synthetic-artifact portions of this requirement are now green;
see `RELEASE_TOOLING.md`. The native Gradle build must continue to retain
literal version values readable by F-Droid, preserve release signing, keep
R8/resource shrinking and emit all required ABI artifacts. A clean unsigned
F-Droid-style build, an official signed build and the physical-device upgrade
matrix remain open. A debug-signed APK must never be presented as an official
upgrade.
