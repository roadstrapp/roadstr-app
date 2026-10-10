#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: verify_package.sh --type valhalla|pmtiles --file FILE [--sha256 HASH]

Checks optional SHA-256 plus basic framing. App-side validation still opens a
Valhalla archive before activation; this script is a build-pipeline gate.
EOF
}

type= file= sha=
while (($#)); do
  case "$1" in
    --type) type="$2"; shift 2 ;;
    --file) file="$2"; shift 2 ;;
    --sha256) sha="$2"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$type" && -n "$file" ]] || die "missing required argument"
require_file "$file"
[[ -z "$sha" ]] || check_sha256 "$file" "$sha"
case "$type" in
  pmtiles)
    [[ "$(head -c 7 "$file")" == PMTiles ]] || die "invalid PMTiles header"
    ;;
  valhalla)
    (( $(stat -c %s "$file") >= 512 )) || die "Valhalla archive is too small"
    (( $(stat -c %s "$file") % 512 == 0 )) || die "Valhalla archive is not tar-block aligned"
    tar -tf "$file" | awk '/\.gph$/ { found=1; exit } END { exit !found }' || die "Valhalla archive contains no graph tiles"
    ;;
  *) die "unsupported package type: $type" ;;
esac
