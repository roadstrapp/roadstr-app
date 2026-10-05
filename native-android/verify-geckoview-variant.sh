#!/bin/sh
# Builds the default road-test app and the optional GeckoView variant and checks what must be true
# of each. It needs the Android SDK's apkanalyzer on the PATH. The default build is built last, so
# the output folder is left with the default APK.
set -eu
cd "$(dirname "$0")"
APK=../build/native-roadtest/app/outputs/apk/debug/app-debug.apk

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "ok:   $*"; }
command -v apkanalyzer >/dev/null 2>&1 || fail "apkanalyzer is required (Android SDK cmdline-tools)"

manifest() { apkanalyzer manifest print "$APK"; }
files() { apkanalyzer files list "$APK"; }
min_sdk() { manifest | sed -n 's/.*android:minSdkVersion="\([0-9]*\)".*/\1/p' | head -1; }
package() { manifest | sed -n 's/.*package="\([^"]*\)".*/\1/p' | head -1; }
# Classes defined by the APK whose package is Google Play Services (references to its types do not count).
gms_defined() { apkanalyzer dex packages --defined-only "$APK" | awk '$1 == "P" && $0 ~ /com\.google\.android\.gms/' | wc -l; }
mozilla_defined() { apkanalyzer dex packages --defined-only "$APK" | awk '$1 == "P" && $0 ~ /org\.mozilla/' | wc -l; }

echo "== variant (GeckoView)"
./gradlew-geckoview :app:assembleDebug >/dev/null
[ "$(package)" = "app.roadstr.roadtest.gecko" ] || fail "variant package is $(package)"; ok "package app.roadstr.roadtest.gecko"
[ "$(min_sdk)" = "26" ] || fail "variant minSdk is $(min_sdk)"; ok "minSdk 26"
files | grep -q "^/lib/arm64-v8a/libxul.so$" || fail "libxul.so missing for arm64-v8a"; ok "libxul.so for arm64-v8a"
files | grep -qE "^/lib/(armeabi-v7a|x86_64|x86)/" && fail "the variant carries another ABI"; ok "arm64-v8a only"
manifest | grep -q "GeckoClipboardContentProvider" && fail "the exported clipboard provider is in the manifest"; ok "no exported clipboard provider"
[ "$(gms_defined)" = "0" ] || fail "the variant defines Google Play Services classes"; ok "no Google Play Services class defined"
[ "$(mozilla_defined)" -gt 0 ] || fail "GeckoView classes are missing"; ok "GeckoView classes present"
files | grep -q "^/assets/web/place-extractor/manifest.json$" || fail "the page extension is not bundled"; ok "page extension bundled"
for permission in CAMERA RECORD_AUDIO READ_CONTACTS READ_EXTERNAL_STORAGE ACCESS_BACKGROUND_LOCATION READ_PHONE_STATE; do
    manifest | grep -q "android.permission.$permission" && fail "unexpected permission $permission"
done
ok "no camera, microphone, contacts, storage, background location or phone permission"

echo "== default"
./gradlew :app:assembleDebug >/dev/null
[ "$(package)" = "app.roadstr.roadtest" ] || fail "default package is $(package)"; ok "package app.roadstr.roadtest"
[ "$(min_sdk)" = "24" ] || fail "default minSdk is $(min_sdk)"; ok "minSdk 24"
files | grep -q "libxul" && fail "the default build carries GeckoView"; ok "no GeckoView library"
files | grep -q "place-extractor" && fail "the default build carries the page extension"; ok "no page extension"
[ "$(mozilla_defined)" = "0" ] || fail "the default build defines GeckoView classes"; ok "no GeckoView class"
[ "$(gms_defined)" = "0" ] || fail "the default build defines Google Play Services classes"; ok "no Google Play Services class"
for abi in arm64-v8a armeabi-v7a x86_64; do
    files | grep -q "^/lib/$abi/" || fail "the default build lost ABI $abi"
done
ok "three ABIs"
echo "all checks passed"
