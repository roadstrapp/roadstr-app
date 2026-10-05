# Android release audit

The release auditor is a read-only gate. It never builds, signs, installs,
tags or publishes anything, and it does not read `android/key.properties`.

## Source gate

Run from the repository root:

```sh
tools/kotlin_rewrite/audit_android_release.sh --source-only
```

This checks the package and namespace, label, backup and cleartext policies,
literal Gradle/pubspec/F-Droid version agreement, R8/resource shrinking, the
three ABI splits plus universal output, and F-Droid/ZapStore metadata paths.

Adding `--require-upgrade` requires a code greater than the shipped baseline
2050. It intentionally fails while the checkout remains at `0.5.11+2050`; a
release candidate must bump all version declarations first.

## Artifact gates

The artifact directory must contain the four expected names used by the audit
contract:

- `app-release.apk` with all three native ABIs;
- `app-arm64-v8a-release.apk`;
- `app-armeabi-v7a-release.apk`;
- `app-x86_64-release.apk`.

For an official candidate:

```sh
tools/kotlin_rewrite/audit_android_release.sh \
  --artifacts build/app/outputs/flutter-apk \
  --mode official \
  --expected-cert-sha256 HEX_FINGERPRINT \
  --output build/release-manifest.tsv \
  --require-upgrade
```

`official` requires every APK to have the expected certificate. `fdroid`
requires repository-built APKs to be unsigned. `inspect` records signing state
without imposing either policy. All modes verify package, version, label,
variant ABI contents and SHA-256, then atomically write the optional manifest.

`build_release.sh` invokes both official gates. It deliberately omits Flutter's
`--split-per-abi` flag: the Gradle split block still emits all four APKs, while
omitting that flag prevents Flutter from adding ABI-specific version-code
offsets. The script remains unusable without the external official keystore.

## Evidence boundary

Five automated tests use synthetic APKs and fake read-only Android inspection
tools to prove command flow and fail-closed behavior. They do not prove a real
R8 build, F-Droid reproducibility, official certificate ownership or Android's
in-place update acceptance. Those remain release gates and require controlled
build/device evidence; no release command was run while adding this tooling.

## Update-path candidate (a device test, not a release)

`native-android` can build the Kotlin app under the production identity, to test Android's in-place update of the shipped app:

```sh
cd native-android
./gradlew :app:assembleRelease -Pcandidate=true
# build/native-roadtest/app/outputs/apk/release/app-release.apk
```

`-Pcandidate=true` sets `applicationId = app.roadstr`, code 2051 and name `0.5.12-kotlin-candidate`, and signs with the
keystore named in `android/key.properties`, read at build time (the key and its passwords are never in the repository). Without
the property nothing changes. It cannot be combined with `-Pgeckoview=true`.

Check before installing, with `apksigner verify --print-certs` and `aapt dump badging`: package `app.roadstr`, a code above the
installed one, the certificate fingerprint of the installed app, not debuggable.

**Safety net first.** Android refuses a lower version code without uninstalling, and uninstalling deletes the app's data. So,
before installing the candidate over a real installation, build a Flutter release of `main` with a **higher** code than the
candidate (the code is a literal in `android/app/build.gradle.kts`; do it in a separate `git worktree`, and delete the copy of
`key.properties` afterwards). Install with `adb install -r` only; never `adb uninstall` and never `pm clear`.

Run on 2026-10-05 on a Pixel 10, this proved acceptance in both directions and showed that the Flutter data survives the round trip;
see `docs/world-discovery/MEASUREMENTS.md`. The candidate does not migrate data: it starts as a first launch. After such a test the
device is at the higher code, so the next real release must use a code above it.

