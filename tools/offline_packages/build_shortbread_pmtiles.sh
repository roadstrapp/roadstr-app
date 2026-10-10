#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: build_shortbread_pmtiles.sh --source FILE_OR_HTTPS_URL --output FILE
       [--bbox W,S,E,N] [--bbox-border N] [--minzoom Z] [--maxzoom Z]
       [--verify-only]

Converts a VersaTiles/Shortbread tileset into a PMTiles archive suitable for
Roadstr.  The source can be a local .versatiles file or an HTTPS source that
the versatiles CLI can range-read.  No default host is contacted.
EOF
}

source_path= output= bbox= bbox_border= minzoom= maxzoom= verify_only=false
while (($#)); do
  case "$1" in
    --source) source_path="$2"; shift 2 ;;
    --output) output="$2"; shift 2 ;;
    --bbox) bbox="$2"; shift 2 ;;
    --bbox-border) bbox_border="$2"; shift 2 ;;
    --minzoom) minzoom="$2"; shift 2 ;;
    --maxzoom) maxzoom="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$source_path" && -n "$output" ]] || die "source and output are required"
if [[ "$source_path" == http://* ]]; then
  die "source URL must use HTTPS"
fi
if [[ "$source_path" == https://* ]]; then
  :
else
  require_file "$source_path"
fi
need versatiles
if [[ "$verify_only" == true ]]; then
  "$OFFLINE_TOOLS_DIR/verify_package.sh" --type pmtiles --file "$output"
  exit 0
fi

mkdir -p "$(dirname "$output")"
temporary="$output.part"
rm -f "$temporary"
arguments=(convert)
[[ -n "$bbox" ]] && arguments+=(--bbox "$bbox")
[[ -n "$bbox_border" ]] && arguments+=(--bbox-border "$bbox_border")
[[ -n "$minzoom" ]] && arguments+=(--min-zoom "$minzoom")
[[ -n "$maxzoom" ]] && arguments+=(--max-zoom "$maxzoom")
arguments+=("$source_path" "$temporary")
versatiles "${arguments[@]}"
mv -f "$temporary" "$output"
digest="$(sha256sum "$output" | awk '{print $1}')"
printf '{"tool":"versatiles","format":"shortbread","sha256":"%s"}\n' "$digest" >"$output.recipe.json.tmp"
mv -f "$output.recipe.json.tmp" "$output.recipe.json"
"$OFFLINE_TOOLS_DIR/verify_package.sh" --type pmtiles --file "$output" --sha256 "$digest"
