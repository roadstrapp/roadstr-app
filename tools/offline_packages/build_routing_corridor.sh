#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: build_routing_corridor.sh --full-tiles-dir DIR --polyline FILE
       --margin-meters N --config-template JSON --output-dir DIR [--verify-only]

Builds a conservative corridor: all levels 0/1 plus every 0.25 degree level-2
tile touched by the expanded segment bounding boxes.
EOF
}

source_dir= polyline= margin= config= output_dir= verify_only=false
while (($#)); do
  case "$1" in
    --full-tiles-dir) source_dir="$2"; shift 2 ;;
    --polyline) polyline="$2"; shift 2 ;;
    --margin-meters) margin="$2"; shift 2 ;;
    --config-template) config="$2"; shift 2 ;;
    --output-dir) output_dir="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$source_dir" && -n "$polyline" && -n "$margin" && -n "$config" && -n "$output_dir" ]] || die "missing required argument"
if [[ "$verify_only" == true ]]; then
  "$OFFLINE_TOOLS_DIR/build_routing_region.sh" --full-tiles-dir "$source_dir" \
    --tile-ids "$output_dir/level2-tiles.txt" --coverage-json "$output_dir/coverage.json" \
    --config-template "$config" --output-dir "$output_dir" --verify-only
  exit 0
fi
need python3
mkdir -p "$output_dir"
python3 "$OFFLINE_TOOLS_DIR/corridor_tiles.py" --polyline "$polyline" \
  --margin-meters "$margin" --tile-ids "$output_dir/level2-tiles.txt" \
  --coverage "$output_dir/coverage.input.json"
"$OFFLINE_TOOLS_DIR/build_routing_region.sh" --full-tiles-dir "$source_dir" \
  --tile-ids "$output_dir/level2-tiles.txt" --coverage-json "$output_dir/coverage.input.json" \
  --config-template "$config" --output-dir "$output_dir"
rm -f "$output_dir/coverage.input.json"
