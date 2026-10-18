#!/usr/bin/env bash
# Rebuilds the offline speech engine from its pinned source and checks the result against what is
# committed in this repository (SHA256SUMS), byte for byte.
#
#   ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.1.12297006 \
#     third_party/offline-stt-engine/rebuild.sh [source-repo] [commit]
#
# source-repo defaults to the public fork; a local path works too. commit defaults to SOURCE_COMMIT.
# Needs Rust with the aarch64-linux-android target, cargo-ndk, CMake and the NDK named above.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
source_repo="${1:-https://github.com/roadstrapp/offline-speech-to-text.git}"
commit="${2:-$(tr -d '[:space:]' < "$here/SOURCE_COMMIT")}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the NDK directory}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git clone --quiet "$source_repo" "$work/src"
git -C "$work/src" checkout --quiet "$commit"
test "$(git -C "$work/src" rev-parse HEAD)" = "$commit"

"$work/src/engine/scripts/build-android.sh" "$work/out"

status=0
while read -r sum file; do
    case "$file" in
        jniLibs/*) built="$work/out/${file#jniLibs/arm64-v8a/}" ;;
        kotlin/*) built="$work/src/engine/${file}" ;;
        *) continue ;;
    esac
    actual="$(sha256sum "$built" | cut -d' ' -f1)"
    if [ "$actual" = "$sum" ]; then
        echo "ok        $file"
    else
        echo "DIFFERS   $file (built $actual, committed $sum)"
        status=1
    fi
done < "$here/SHA256SUMS"

# What is committed in the app must be exactly what SHA256SUMS lists.
while read -r sum file; do
    case "$file" in
        jniLibs/*) shipped="$repo/android/app/src/main/$file" ;;
        kotlin/*) shipped="$repo/android/app/src/main/$file" ;;
        *) continue ;;
    esac
    actual="$(sha256sum "$shipped" | cut -d' ' -f1)"
    [ "$actual" = "$sum" ] || { echo "SHIPPED FILE CHANGED: $file"; status=1; }
done < "$here/SHA256SUMS"

[ "$status" -eq 0 ] && echo "reproducible: every shipped file matches the pinned source"
exit "$status"
