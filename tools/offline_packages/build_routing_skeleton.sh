#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: build_routing_skeleton.sh --full-tiles-dir DIR --config-template JSON
       --output-dir DIR [--verify-only]

Creates a Valhalla archive containing national hierarchy levels 0 and 1 only.
The measured national skeleton was about 375 MB.
EOF
}

source_dir= config= output_dir= verify_only=false
while (($#)); do
  case "$1" in
    --full-tiles-dir) source_dir="$2"; shift 2 ;;
    --config-template) config="$2"; shift 2 ;;
    --output-dir) output_dir="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$source_dir" && -n "$config" && -n "$output_dir" ]] || die "missing required argument"
if [[ "$verify_only" == true ]]; then
  "$OFFLINE_TOOLS_DIR/verify_package.sh" --type valhalla --file "$output_dir/tiles.tar"
  exit 0
fi
need jq
need python3
need valhalla_build_extract
rm -rf "$output_dir/staging"
mkdir -p "$output_dir/staging"
python3 "$OFFLINE_TOOLS_DIR/select_valhalla_tiles.py" --source "$source_dir" --target "$output_dir/staging"
absolute_output="$(cd "$output_dir" && pwd)"
jq --arg tiles "$absolute_output/staging" --arg extract "$absolute_output/tiles.tar" \
  '.mjolnir.tile_dir=$tiles | .mjolnir.tile_extract=$extract' "$config" >"$output_dir/valhalla.json"
valhalla_build_extract -c "$output_dir/valhalla.json"
printf '{"tool":"valhalla_build_extract","version":"%s","levels":[0,1]}\n' \
  "$VALHALLA_VERSION" >"$output_dir/recipe.json"
