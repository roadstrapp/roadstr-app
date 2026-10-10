#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: build_valhalla.sh --pbf FILE --pbf-sha256 HASH --config-template JSON
       --output-dir DIR --default-speeds enabled|disabled
       --timezones enabled|disabled --elevation enabled|disabled [--verify-only]

Builds auto-only Valhalla tiles and tiles.tar with Valhalla 3.9.1.
More than 3.5 GiB RAM is required; the measured successful build had 9 GiB.
The three production-data choices are mandatory and recorded in recipe.json.
EOF
}

pbf= pbf_sha= config= output_dir= speeds= zones= elevation= verify_only=false
while (($#)); do
  case "$1" in
    --pbf) pbf="$2"; shift 2 ;;
    --pbf-sha256) pbf_sha="$2"; shift 2 ;;
    --config-template) config="$2"; shift 2 ;;
    --output-dir) output_dir="$2"; shift 2 ;;
    --default-speeds) speeds="$2"; shift 2 ;;
    --timezones) zones="$2"; shift 2 ;;
    --elevation) elevation="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$pbf" && -n "$config" && -n "$output_dir" ]] || die "missing required argument"
for choice in "$speeds" "$zones" "$elevation"; do
  [[ "$choice" =~ ^(enabled|disabled)$ ]] || die "production choices must be enabled or disabled"
done
need jq
need valhalla_build_tiles
need valhalla_build_extract
check_sha256 "$pbf" "$pbf_sha"
require_file "$config"
if [[ "$verify_only" == true ]]; then
  "$OFFLINE_TOOLS_DIR/verify_package.sh" --type valhalla --file "$output_dir/tiles.tar"
  require_file "$output_dir/recipe.json"
  exit 0
fi

mkdir -p "$output_dir/tiles"
absolute_output="$(cd "$output_dir" && pwd)"
jq --arg tiles "$absolute_output/tiles" --arg extract "$absolute_output/tiles.tar" \
  '.mjolnir.tile_dir=$tiles | .mjolnir.tile_extract=$extract |
   .mjolnir.include_bicycle=false | .mjolnir.include_pedestrian=false' \
  "$config" >"$output_dir/valhalla.json.tmp"
mv -f "$output_dir/valhalla.json.tmp" "$output_dir/valhalla.json"
if command -v valhalla_build_admins >/dev/null 2>&1; then
  valhalla_build_admins -c "$output_dir/valhalla.json" "$pbf"
fi
valhalla_build_tiles -c "$output_dir/valhalla.json" "$pbf"
valhalla_build_extract -c "$output_dir/valhalla.json"
digest="$(sha256sum "$output_dir/tiles.tar" | awk '{print $1}')"
printf '{"tool":"valhalla","version":"%s","sourceSha256":"%s","sha256":"%s","autoOnly":true,"defaultSpeeds":"%s","timezones":"%s","elevation":"%s"}\n' \
  "$VALHALLA_VERSION" "$pbf_sha" "$digest" "$speeds" "$zones" "$elevation" >"$output_dir/recipe.json.tmp"
mv -f "$output_dir/recipe.json.tmp" "$output_dir/recipe.json"
"$OFFLINE_TOOLS_DIR/verify_package.sh" --type valhalla --file "$output_dir/tiles.tar" --sha256 "$digest"
