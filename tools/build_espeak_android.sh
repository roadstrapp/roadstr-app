#!/usr/bin/env bash
set -euo pipefail

# Rebuild Roadstr's eSpeak NG library and compact data asset from audited,
# immutable upstream revisions. Nothing downloaded by this script is executed
# as root. Set ESPEAK_SOURCE_DIR / SONIC_SOURCE_DIR to pre-fetched sources for
# offline or F-Droid-style builds.

readonly ESPEAK_COMMIT="4870adfa25b1a32b4361592f1be8a40337c58d6c"
readonly SONIC_COMMIT="fbf75c3d6d846bad3bb3d456cbc5d07d9fd8c104"
readonly NDK_VERSION="27.1.12297006"
readonly ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"
work_dir="$(mktemp -d -t roadstr-espeak.XXXXXXXX)"
trap 'rm -rf -- "$work_dir"' EXIT

sdk_root="${ANDROID_SDK_ROOT:-}"
if [[ -z "$sdk_root" && -f "$repo_dir/android/local.properties" ]]; then
  sdk_root="$(sed -n 's/^sdk\.dir=//p' "$repo_dir/android/local.properties" | head -n 1)"
fi
ndk_root="${ROADSTR_ANDROID_NDK:-${sdk_root:+$sdk_root/ndk/$NDK_VERSION}}"
if [[ -z "$ndk_root" || ! -f "$ndk_root/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK $NDK_VERSION not found. Set ROADSTR_ANDROID_NDK." >&2
  exit 1
fi

espeak_source="${ESPEAK_SOURCE_DIR:-$work_dir/espeak-ng}"
sonic_source="${SONIC_SOURCE_DIR:-$work_dir/sonic}"
if [[ ! -d "$espeak_source/.git" ]]; then
  git clone --quiet https://github.com/espeak-ng/espeak-ng.git "$espeak_source"
fi
if [[ ! -d "$sonic_source/.git" ]]; then
  git clone --quiet https://github.com/waywardgeek/sonic.git "$sonic_source"
fi
git -C "$espeak_source" checkout --quiet --detach "$ESPEAK_COMMIT"
git -C "$sonic_source" checkout --quiet --detach "$SONIC_COMMIT"
[[ "$(git -C "$espeak_source" rev-parse HEAD)" == "$ESPEAK_COMMIT" ]]
[[ "$(git -C "$sonic_source" rev-parse HEAD)" == "$SONIC_COMMIT" ]]

common_options=(
  "-DFETCHCONTENT_SOURCE_DIR_SONIC-GIT=$sonic_source"
  "-DBUILD_TESTING=OFF"
  "-DUSE_ASYNC=OFF"
  "-DUSE_MBROLA=OFF"
  "-DUSE_LIBSONIC=OFF"
  "-DUSE_LIBPCAUDIO=OFF"
  "-DUSE_SPEECHPLAYER=OFF"
  "-DCMAKE_BUILD_TYPE=Release"
)

# Generate the data files with a host binary, then package only the seven
# languages Roadstr's Kokoro integration supports. Source voice/language files
# are included because eSpeak resolves regional variants through them.
host_build="$work_dir/host-build"
cmake -S "$espeak_source" -B "$host_build" "${common_options[@]}"
cmake --build "$host_build" --target data --parallel
data_stage="$work_dir/data-stage/espeak-ng-data"
mkdir -p "$data_stage"
for file in phondata phontab phonindex phondata-manifest intonations \
  en_dict cmn_dict fr_dict it_dict es_dict ja_dict pt_dict; do
  install -m 0644 "$host_build/espeak-ng-data/$file" "$data_stage/$file"
done
cp -R "$espeak_source/espeak-ng-data/lang" "$data_stage/lang"
cp -R "$espeak_source/espeak-ng-data/voices" "$data_stage/voices"
rm -rf -- "$data_stage/voices/mb"
tar --sort=name --mtime='@0' --owner=0 --group=0 --numeric-owner \
  -C "$work_dir/data-stage" -czf "$work_dir/espeak-ng-data.tar.gz" \
  espeak-ng-data
install -m 0644 "$work_dir/espeak-ng-data.tar.gz" \
  "$repo_dir/assets/espeak-ng-data.tar.gz"

strip_tool="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
for abi in "${ABIS[@]}"; do
  build_dir="$work_dir/android-$abi"
  prefix_flags="-O2 -DNDEBUG -g0 -ffile-prefix-map=$work_dir=/usr/src/roadstr-native -fdebug-prefix-map=$work_dir=/usr/src/roadstr-native -ffile-prefix-map=$ndk_root=/opt/android-ndk -fdebug-prefix-map=$ndk_root=/opt/android-ndk"
  cmake -S "$espeak_source" -B "$build_dir" \
    "${common_options[@]}" \
    "-DCMAKE_TOOLCHAIN_FILE=$ndk_root/build/cmake/android.toolchain.cmake" \
    "-DANDROID_ABI=$abi" \
    "-DANDROID_PLATFORM=android-21" \
    "-DBUILD_SHARED_LIBS=ON" \
    "-DCMAKE_C_FLAGS_RELEASE=$prefix_flags"
  cmake --build "$build_dir" --target espeak-ng --parallel
  library="$build_dir/src/libespeak-ng/libespeak-ng.so"
  "$strip_tool" --strip-unneeded "$library"
  install -m 0644 "$library" \
    "$repo_dir/android/app/src/main/jniLibs/$abi/libespeak-ng.so"
done

sha256sum \
  "$repo_dir"/android/app/src/main/jniLibs/*/libespeak-ng.so \
  "$repo_dir/assets/espeak-ng-data.tar.gz"
