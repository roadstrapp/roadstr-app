#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: build_routing_region.sh --full-tiles-dir DIR --tile-ids FILE
       --coverage-json FILE --config-template JSON --output-dir DIR [--verify-only]

Combines levels 0/1 with an explicit set of level-2 tile ids. Coverage metadata
must be supplied as GeoJSON and is copied next to the archive.
EOF
}

source_dir= ids= coverage= config= output_dir= verify_only=false
while (($#)); do
  case "$1" in
    --full-tiles-dir) source_dir="$2"; shift 2 ;;
    --tile-ids) ids="$2"; shift 2 ;;
    --coverage-json) coverage="$2"; shift 2 ;;
    --config-template) config="$2"; shift 2 ;;
    --output-dir) output_dir="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$source_dir" && -n "$ids" && -n "$coverage" && -n "$config" && -n "$output_dir" ]] || die "missing required argument"
if [[ "$verify_only" == true ]]; then
  "$OFFLINE_TOOLS_DIR/verify_package.sh" --type valhalla --file "$output_dir/tiles.tar"
  require_file "$output_dir/coverage.json"
  exit 0
fi
need jq
need python3
need valhalla_build_extract
require_file "$coverage"
rm -rf "$output_dir/staging"
mkdir -p "$output_dir/staging"
python3 "$OFFLINE_TOOLS_DIR/select_valhalla_tiles.py" \
  --source "$source_dir" --target "$output_dir/staging" --tile-ids "$ids"
absolute_output="$(cd "$output_dir" && pwd)"
jq --arg tiles "$absolute_output/staging" --arg extract "$absolute_output/tiles.tar" \
  '.mjolnir.tile_dir=$tiles | .mjolnir.tile_extract=$extract' "$config" >"$output_dir/valhalla.json"
valhalla_build_extract -c "$output_dir/valhalla.json"
cp "$coverage" "$output_dir/coverage.json"
digest="$(sha256sum "$output_dir/tiles.tar" | awk '{print $1}')"
printf '{"tool":"valhalla_build_extract","version":"%s","levels":[0,1,2],"sha256":"%s"}\n' \
  "$VALHALLA_VERSION" "$digest" >"$output_dir/recipe.json"
