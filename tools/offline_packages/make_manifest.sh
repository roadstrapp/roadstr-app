#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"

usage() {
  cat <<'EOF'
Usage: make_manifest.sh --output FILE --generated-at ISO8601
       --artifact SPEC.json [--artifact SPEC.json ...] [--verify-only]

Each specification contains the app artifact fields plus a local "file" field.
Size and SHA-256 are derived from that file. Signing remains an owner decision.
EOF
}

output= generated= verify_only=false artifacts=()
while (($#)); do
  case "$1" in
    --output) output="$2"; shift 2 ;;
    --generated-at) generated="$2"; shift 2 ;;
    --artifact) artifacts+=(--artifact "$2"); shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$output" && -n "$generated" ]] || die "missing required argument"
need python3
if [[ "$verify_only" == true ]]; then
  python3 "$OFFLINE_TOOLS_DIR/manifest_tool.py" --output "$output" --generated-at "$generated" --verify-only
else
  ((${#artifacts[@]} > 0)) || die "at least one artifact is required"
  python3 "$OFFLINE_TOOLS_DIR/manifest_tool.py" --output "$output.tmp" --generated-at "$generated" "${artifacts[@]}"
  mv -f "$output.tmp" "$output"
fi
