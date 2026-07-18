#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Roadstr — Release Build Script
# Produces signed, optimised APKs ready for ZapStore and F-Droid sideloading.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}→${NC} $*"; }
warning() { echo -e "${YELLOW}⚠${NC}  $*"; }
error()   { echo -e "${RED}✗${NC}  $*"; exit 1; }

# ── Prerequisites ─────────────────────────────────────────────────────────────
info "Checking prerequisites..."
command -v flutter >/dev/null 2>&1 || error "flutter not found in PATH"
command -v apksigner >/dev/null 2>&1 || error "apksigner not found — install Android SDK Build Tools"

# ── Keystore setup ────────────────────────────────────────────────────────────
KEY_PROPS="android/key.properties"
if [ -f "$KEY_PROPS" ]; then
    STORE_FILE=$(grep "^storeFile=" "$KEY_PROPS" | cut -d= -f2- | tr -d '[:space:]')
    if [ -f "$STORE_FILE" ]; then
        info "Keystore found at $STORE_FILE — will sign with release key."
    else
        error "key.properties points to a missing keystore: $STORE_FILE"
    fi
else
    warning "android/key.properties not found."
    warning "A public release must use the official release key."
    echo ""
    echo "  To set up release signing:"
    echo "  1. Copy android/key.properties.template → android/key.properties"
    echo "  2. Fill in your keystore path and passwords"
    echo "  3. Re-run this script"
    echo ""
    error "Release keystore unavailable; refusing to continue."
fi

# ── Version ───────────────────────────────────────────────────────────────────
VERSION=$(grep '^version:' pubspec.yaml | awk '{print $2}')
VERSION_NAME="${VERSION%+*}"
echo ""
info "Building signed Roadstr v${VERSION_NAME}"
echo ""

# ── Clean ─────────────────────────────────────────────────────────────────────
info "flutter clean..."
flutter clean 2>/dev/null

# ── Dependencies ──────────────────────────────────────────────────────────────
info "flutter pub get..."
flutter pub get 2>/dev/null

# ── Build ─────────────────────────────────────────────────────────────────────
info "Building split APKs (arm64-v8a · armeabi-v7a · x86_64 · universal)..."
flutter build apk --release \
    --split-per-abi \
    --obfuscate \
    --split-debug-info="build/debug-symbols"

# A successful Gradle build does not guarantee that an APK was signed. Refuse
# to publish unless every split passes Android's cryptographic verifier.
for apk in build/app/outputs/flutter-apk/app-*-release.apk; do
    [ -f "$apk" ] || error "No release APK was produced"
    apksigner verify "$apk" || error "Unsigned or invalid APK: $apk"
done

echo ""
echo "─────────────────────────────────────────────────────────────────────────"
echo -e "${GREEN}✓ Build complete${NC}"
echo "─────────────────────────────────────────────────────────────────────────"
ls -lh build/app/outputs/flutter-apk/app-*-release.apk 2>/dev/null || true

# ── SHA-256 ───────────────────────────────────────────────────────────────────
echo ""
info "SHA-256 checksums:"
for apk in build/app/outputs/flutter-apk/app-*-release.apk; do
    [ -f "$apk" ] || continue
    sha256sum "$apk"
done

echo ""
echo "─────────────────────────────────────────────────────────────────────────"
echo "NEXT STEPS"
echo "─────────────────────────────────────────────────────────────────────────"
echo ""
echo "1. TEST on a real device:"
echo "   adb install build/app/outputs/flutter-apk/app-arm64-v8a-release.apk"
echo ""
echo "2. GITHUB RELEASE:"
echo "   git tag v${VERSION_NAME} && git push origin v${VERSION_NAME}"
echo "   gh release create v${VERSION_NAME} \\"
echo "     build/app/outputs/flutter-apk/app-arm64-v8a-release.apk \\"
echo "     build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk \\"
echo "     build/app/outputs/flutter-apk/app-x86_64-release.apk \\"
echo "     --title 'Roadstr v${VERSION_NAME}'"
echo ""
echo "3. ZAPSTORE:"
echo "   export SIGN_WITH=nsec1..."
echo "   zsp publish --wizard"
echo ""
echo "4. F-DROID: submit metadata/app.roadstr.yml via MR to gitlab.com/fdroid/fdroiddata"
echo "─────────────────────────────────────────────────────────────────────────"
