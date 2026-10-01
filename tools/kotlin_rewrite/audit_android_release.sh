#!/usr/bin/env bash
# Read-only Roadstr Android release contract and APK auditor.
# It never builds, signs, installs, tags or publishes an artifact.
set -euo pipefail

fail() {
    printf 'release-audit: %s\n' "$*" >&2
    exit 1
}

usage() {
    printf '%s\n' \
        'Usage:' \
        '  audit_android_release.sh --source-only [--require-upgrade]' \
        '  audit_android_release.sh --artifacts DIR --mode inspect|official|fdroid' \
        '      [--expected-cert-sha256 HEX] [--output FILE] [--require-upgrade]'
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SOURCE_ONLY=false
ARTIFACT_DIRECTORY=""
AUDIT_MODE="inspect"
EXPECTED_CERT_SHA256="${ROADSTR_EXPECTED_CERT_SHA256:-}"
OUTPUT_FILE=""
REQUIRE_UPGRADE=false
BASELINE_VERSION_CODE=2050

while [ "$#" -gt 0 ]; do
    case "$1" in
        --source-only)
            SOURCE_ONLY=true
            shift
            ;;
        --artifacts)
            [ "$#" -ge 2 ] || fail '--artifacts requires a directory'
            ARTIFACT_DIRECTORY="$2"
            shift 2
            ;;
        --mode)
            [ "$#" -ge 2 ] || fail '--mode requires a value'
            AUDIT_MODE="$2"
            shift 2
            ;;
        --expected-cert-sha256)
            [ "$#" -ge 2 ] || fail '--expected-cert-sha256 requires a value'
            EXPECTED_CERT_SHA256="$2"
            shift 2
            ;;
        --output)
            [ "$#" -ge 2 ] || fail '--output requires a file'
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --require-upgrade)
            REQUIRE_UPGRADE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "unknown argument: $1"
            ;;
    esac
done

case "$AUDIT_MODE" in
    inspect|official|fdroid) ;;
    *) fail "unsupported mode: $AUDIT_MODE" ;;
esac

if [ "$SOURCE_ONLY" = true ] && [ -n "$ARTIFACT_DIRECTORY" ]; then
    fail '--source-only and --artifacts are mutually exclusive'
fi
if [ "$SOURCE_ONLY" = false ] && [ -z "$ARTIFACT_DIRECTORY" ]; then
    fail 'choose --source-only or provide --artifacts DIR'
fi

GRADLE_FILE="$REPOSITORY_ROOT/android/app/build.gradle.kts"
PUBSPEC_FILE="$REPOSITORY_ROOT/pubspec.yaml"
FDROID_FILE="$REPOSITORY_ROOT/metadata/app.roadstr.yml"
MANIFEST_FILE="$REPOSITORY_ROOT/android/app/src/main/AndroidManifest.xml"
ZAPSTORE_FILE="$REPOSITORY_ROOT/zapstore.yaml"

for required_file in \
    "$GRADLE_FILE" "$PUBSPEC_FILE" "$FDROID_FILE" "$MANIFEST_FILE" "$ZAPSTORE_FILE"; do
    [ -f "$required_file" ] || fail "missing release source: $required_file"
done

extract_one() {
    local pattern="$1"
    local file="$2"
    local value
    value="$(sed -nE "$pattern" "$file")"
    [ -n "$value" ] || fail "could not read release value from $file"
    [ "$(printf '%s\n' "$value" | wc -l)" -eq 1 ] || fail "release value is ambiguous in $file"
    printf '%s' "$value"
}

APPLICATION_ID="$(extract_one 's/^[[:space:]]*applicationId = "([^"]+)".*/\1/p' "$GRADLE_FILE")"
NAMESPACE="$(extract_one 's/^[[:space:]]*namespace = "([^"]+)".*/\1/p' "$GRADLE_FILE")"
VERSION_CODE="$(extract_one 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE")"
VERSION_NAME="$(extract_one 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE")"
PUBSPEC_VERSION="$(extract_one 's/^version:[[:space:]]*([^+[:space:]]+)\+([0-9]+).*/\1+\2/p' "$PUBSPEC_FILE")"
FDROID_VERSION="$(extract_one 's/^CurrentVersion:[[:space:]]*(.+)/\1/p' "$FDROID_FILE")"
FDROID_VERSION_CODE="$(extract_one 's/^CurrentVersionCode:[[:space:]]*([0-9]+)/\1/p' "$FDROID_FILE")"

[ "$APPLICATION_ID" = 'app.roadstr' ] || fail "unexpected applicationId: $APPLICATION_ID"
[ "$NAMESPACE" = 'app.roadstr' ] || fail "unexpected namespace: $NAMESPACE"
[ "$PUBSPEC_VERSION" = "$VERSION_NAME+$VERSION_CODE" ] || fail 'pubspec and Gradle versions differ'
[ "$FDROID_VERSION" = "$VERSION_NAME" ] || fail 'F-Droid versionName differs from Gradle'
[ "$FDROID_VERSION_CODE" = "$VERSION_CODE" ] || fail 'F-Droid versionCode differs from Gradle'
[ "$VERSION_CODE" -ge "$BASELINE_VERSION_CODE" ] || fail 'versionCode is below the shipped baseline'
if [ "$REQUIRE_UPGRADE" = true ] && [ "$VERSION_CODE" -le "$BASELINE_VERSION_CODE" ]; then
    fail "candidate versionCode must be greater than $BASELINE_VERSION_CODE"
fi

grep -Fq 'android:label="Roadstr"' "$MANIFEST_FILE" || fail 'application label changed'
grep -Fq 'android:allowBackup="false"' "$MANIFEST_FILE" || fail 'backup protection changed'
grep -Fq 'android:usesCleartextTraffic="false"' "$MANIFEST_FILE" || fail 'cleartext default changed'
grep -Fq 'isMinifyEnabled   = true' "$GRADLE_FILE" || fail 'R8 minification is not enabled'
grep -Fq 'isShrinkResources = true' "$GRADLE_FILE" || fail 'resource shrinking is not enabled'
grep -Fq 'include("arm64-v8a", "armeabi-v7a", "x86_64")' "$GRADLE_FILE" || fail 'ABI split set changed'
grep -Fq 'isUniversalApk = true' "$GRADLE_FILE" || fail 'universal APK is disabled'
grep -Fq 'signingConfig = if (hasReleaseSigningConfig)' "$GRADLE_FILE" || \
    fail 'conditional release signing is missing'
grep -Eq '^[[:space:]]*null[[:space:]]*$' "$GRADLE_FILE" || \
    fail 'unsigned F-Droid release fallback is missing'
grep -Fq 'output: build/app/outputs/flutter-apk/app-release.apk' "$FDROID_FILE" || fail 'F-Droid output path changed'
grep -Fq 'repository: https://github.com/roadstrapp/roadstr-app/' "$ZAPSTORE_FILE" || fail 'ZapStore repository changed'

if [ "$SOURCE_ONLY" = true ]; then
    printf 'release source contract valid: %s %s+%s\n' "$APPLICATION_ID" "$VERSION_NAME" "$VERSION_CODE"
    exit 0
fi

command -v aapt >/dev/null 2>&1 || fail 'aapt is required for artifact inspection'
command -v apksigner >/dev/null 2>&1 || fail 'apksigner is required for signature inspection'
command -v sha256sum >/dev/null 2>&1 || fail 'sha256sum is required for artifact inspection'
[ -d "$ARTIFACT_DIRECTORY" ] || fail "artifact directory does not exist: $ARTIFACT_DIRECTORY"

EXPECTED_CERT_SHA256="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
if [ "$AUDIT_MODE" = official ] && ! printf '%s' "$EXPECTED_CERT_SHA256" | grep -Eq '^[0-9A-F]{64}$'; then
    fail 'official mode requires a 64-hex expected certificate SHA-256'
fi

MANIFEST_TEMP="$(mktemp /tmp/roadstr-release-manifest.XXXXXX)"
trap 'rm -f "$MANIFEST_TEMP"' EXIT
printf 'contract\t1\napplication_id\t%s\nversion_name\t%s\nversion_code\t%s\n' \
    "$APPLICATION_ID" "$VERSION_NAME" "$VERSION_CODE" > "$MANIFEST_TEMP"
printf 'artifact\tvariant\tfile\tsha256\tabis\tsigning\tcertificate_sha256\n' >> "$MANIFEST_TEMP"

ARTIFACT_SPECS=(
    'universal|app-release.apk|arm64-v8a,armeabi-v7a,x86_64'
    'arm64-v8a|app-arm64-v8a-release.apk|arm64-v8a'
    'armeabi-v7a|app-armeabi-v7a-release.apk|armeabi-v7a'
    'x86_64|app-x86_64-release.apk|x86_64'
)

for spec in "${ARTIFACT_SPECS[@]}"; do
    IFS='|' read -r variant file_name expected_abis <<< "$spec"
    apk="$ARTIFACT_DIRECTORY/$file_name"
    [ -f "$apk" ] || fail "missing $variant artifact: $file_name"

    badging="$(aapt dump badging "$apk")" || fail "aapt rejected $file_name"
    package_line="$(printf '%s\n' "$badging" | sed -n '/^package: /p')"
    label_line="$(printf '%s\n' "$badging" | sed -n '/^application-label:/p')"
    native_line="$(printf '%s\n' "$badging" | sed -n '/^native-code: /p')"
    printf '%s' "$package_line" | grep -Fq "name='$APPLICATION_ID'" || fail "package mismatch in $file_name"
    printf '%s' "$package_line" | grep -Fq "versionCode='$VERSION_CODE'" || fail "versionCode mismatch in $file_name"
    printf '%s' "$package_line" | grep -Fq "versionName='$VERSION_NAME'" || fail "versionName mismatch in $file_name"
    printf '%s' "$label_line" | grep -Fq "'Roadstr'" || fail "label mismatch in $file_name"
    [ -n "$native_line" ] || fail "no native ABI declaration in $file_name"

    actual_abis="$(printf '%s\n' "$native_line" | grep -oE "'[^']+'" | tr -d "'" | sort | paste -sd, -)"
    expected_sorted="$(printf '%s' "$expected_abis" | tr ',' '\n' | sort | paste -sd, -)"
    [ "$actual_abis" = "$expected_sorted" ] || fail "ABI mismatch in $file_name: $actual_abis"

    signing='unsigned'
    certificate='-'
    if apksigner verify "$apk" >/dev/null 2>&1; then
        signing='signed'
        certificate="$(apksigner verify --print-certs "$apk" \
            | sed -nE 's/^Signer #1 certificate SHA-256 digest:[[:space:]]*//p' \
            | head -n 1 \
            | tr -d ':[:space:]' \
            | tr '[:lower:]' '[:upper:]')"
        printf '%s' "$certificate" | grep -Eq '^[0-9A-F]{64}$' || fail "invalid certificate digest in $file_name"
    fi
    if [ "$AUDIT_MODE" = official ]; then
        [ "$signing" = signed ] || fail "official artifact is unsigned: $file_name"
        [ "$certificate" = "$EXPECTED_CERT_SHA256" ] || fail "certificate mismatch in $file_name"
    fi
    if [ "$AUDIT_MODE" = fdroid ] && [ "$signing" = signed ]; then
        fail "F-Droid source artifact must not use a repository release key: $file_name"
    fi

    digest="$(sha256sum "$apk" | awk '{print $1}')"
    printf 'artifact\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$variant" "$file_name" "$digest" "$actual_abis" "$signing" "$certificate" \
        >> "$MANIFEST_TEMP"
done

if [ -n "$OUTPUT_FILE" ]; then
    output_parent="$(dirname "$OUTPUT_FILE")"
    [ -d "$output_parent" ] || fail "output directory does not exist: $output_parent"
    output_temp="$(mktemp "$output_parent/.roadstr-release-manifest.XXXXXX")"
    cp "$MANIFEST_TEMP" "$output_temp"
    mv -f "$output_temp" "$OUTPUT_FILE"
    printf 'release artifact contract valid: %s\n' "$OUTPUT_FILE"
else
    cat "$MANIFEST_TEMP"
fi
