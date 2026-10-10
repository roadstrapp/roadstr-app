#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: build_pmtiles.sh --pbf FILE --pbf-sha256 HASH --planetiler-jar FILE
       --planetiler-sha256 HASH --output FILE [--maxzoom 12|13|14] [--verify-only]

Builds OpenMapTiles-compatible PMTiles with pinned Planetiler 0.10.2.
The measured national build needs at least 2.5 GiB of JVM heap.
EOF
}

pbf= pbf_sha= jar= jar_sha= output= maxzoom=14 verify_only=false
while (($#)); do
  case "$1" in
    --pbf) pbf="$2"; shift 2 ;;
    --pbf-sha256) pbf_sha="$2"; shift 2 ;;
    --planetiler-jar) jar="$2"; shift 2 ;;
    --planetiler-sha256) jar_sha="$2"; shift 2 ;;
    --output) output="$2"; shift 2 ;;
    --maxzoom) maxzoom="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$pbf" && -n "$jar" && -n "$output" ]] || die "missing required argument"
[[ "$maxzoom" =~ ^(12|13|14)$ ]] || die "maxzoom must be 12, 13 or 14"
need java
check_sha256 "$pbf" "$pbf_sha"
check_sha256 "$jar" "$jar_sha"
if [[ "$verify_only" == true ]]; then
  "$OFFLINE_TOOLS_DIR/verify_package.sh" --type pmtiles --file "$output"
  exit 0
fi

mkdir -p "$(dirname "$output")"
rm -f "$output.part"
SOURCE_DATE_EPOCH=0 java -Xmx2500m -jar "$jar" \
  --osm_path="$pbf" \
  --output="$output.part" \
  --maxzoom="$maxzoom" \
  --force
mv -f "$output.part" "$output"
digest="$(sha256sum "$output" | awk '{print $1}')"
printf '{"tool":"planetiler","version":"%s","profile":"openmaptiles","maxzoom":%s,"sourceSha256":"%s","sha256":"%s"}\n' \
  "$PLANETILER_VERSION" "$maxzoom" "$pbf_sha" "$digest" >"$output.recipe.json.tmp"
mv -f "$output.recipe.json.tmp" "$output.recipe.json"
"$OFFLINE_TOOLS_DIR/verify_package.sh" --type pmtiles --file "$output" --sha256 "$digest"
