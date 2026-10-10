#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: fetch_extract.sh --url HTTPS_URL --sha256 HASH --output FILE
                        --license SPDX --attribution TEXT [--verify-only]

Downloads one explicitly selected OSM extract and writes FILE.source.json.
The URL and checksum are mandatory; no default provider is contacted.
EOF
}

url= sha= output= license= attribution= verify_only=false
while (($#)); do
  case "$1" in
    --url) url="$2"; shift 2 ;;
    --sha256) sha="$2"; shift 2 ;;
    --output) output="$2"; shift 2 ;;
    --license) license="$2"; shift 2 ;;
    --attribution) attribution="$2"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$url" == https://* ]] || die "source URL must use HTTPS"
[[ -n "$output" && -n "$license" && -n "$attribution" ]] || die "missing required argument"
require_sha256 "$sha"
need curl
need python3
if [[ "$verify_only" == true ]]; then
  check_sha256 "$output" "$sha"
  require_file "$output.source.json"
  exit 0
fi

mkdir -p "$(dirname "$output")"
temporary="$output.part"
curl --fail --location --proto '=https' --tlsv1.2 --output "$temporary" "$url"
check_sha256 "$temporary" "$sha"
mv -f "$temporary" "$output"
python3 - "$output.source.json.tmp" "$url" "$sha" "$license" "$attribution" <<'PY'
import json, pathlib, sys
path, url, digest, license_id, attribution = sys.argv[1:]
pathlib.Path(path).write_text(json.dumps({
    "url": url,
    "sha256": digest,
    "license": license_id,
    "attribution": attribution,
}, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
PY
mv -f "$output.source.json.tmp" "$output.source.json"
