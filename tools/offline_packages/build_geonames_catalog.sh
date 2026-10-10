#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_dir="$project_root/android/app/src/main/assets/offline_catalog"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

curl --fail --location --silent --show-error \
  https://download.geonames.org/export/dump/cities15000.zip \
  --output "$work_dir/cities15000.zip"
curl --fail --location --silent --show-error \
  https://download.geonames.org/export/dump/admin1CodesASCII.txt \
  --output "$work_dir/admin1CodesASCII.txt"
unzip -p "$work_dir/cities15000.zip" cities15000.txt > "$work_dir/cities15000.txt"
mkdir -p "$output_dir"
awk -F '\t' 'BEGIN { OFS="\t" } { split($1, code, "."); print "A", code[1], code[2], $2 }' \
  "$work_dir/admin1CodesASCII.txt" > "$work_dir/catalog.tsv"
awk -F '\t' 'BEGIN { OFS="\t" } { print "C", $1, $2, $3, $5, $6, $9, $11, $15 }' \
  "$work_dir/cities15000.txt" >> "$work_dir/catalog.tsv"
gzip -9 -n -c "$work_dir/catalog.tsv" > "$output_dir/geonames-cities15000.tsv.gz"
echo "Wrote $output_dir/geonames-cities15000.tsv.gz"
