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
