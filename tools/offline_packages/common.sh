#!/usr/bin/env bash
set -euo pipefail

OFFLINE_TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=versions.env
source "$OFFLINE_TOOLS_DIR/versions.env"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 2
}

need() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_file() {
  [[ -f "$1" ]] || die "file not found: $1"
}

require_sha256() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]] || die "SHA-256 must be 64 lowercase hex characters"
}

check_sha256() {
  local file="$1"
  local expected="$2"
  require_file "$file"
  require_sha256 "$expected"
  local actual
  actual="$(sha256sum "$file" | awk '{print $1}')"
  [[ "$actual" == "$expected" ]] || die "checksum mismatch for $file"
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
}
