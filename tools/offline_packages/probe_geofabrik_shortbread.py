#!/usr/bin/env python3
"""Lists the Geofabrik areas that have no Shortbread MBTiles extract.

Geofabrik's index-v1.json names every area but says nothing about Shortbread,
and the extracts are not published at one fixed level of the hierarchy: the
Czech Republic has one file for the whole country and none for its regions,
Poland has one per voivodeship and none for the country. The app needs to know
which areas really have a file, so this probes each one with a HEAD request
and writes the ids that answer 404.

Only missing ids are stored: an area Geofabrik adds later is assumed to have a
file, which is what the app did before this list existed, and the probe before
installation still catches it if not.

Usage: probe_geofabrik_shortbread.py [output]
"""

import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

INDEX_URL = "https://download.geofabrik.de/index-v1.json"
USER_AGENT = "Roadstr offline catalog probe (https://github.com/roadstrapp/roadstr-app)"
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parents[2]
    / "android/app/src/main/assets/offline_catalog/geofabrik-shortbread-missing.txt"
)
DELAY_SECONDS = 0.2


def request(url, method="GET"):
    req = urllib.request.Request(url, method=method, headers={"User-Agent": USER_AGENT})
    return urllib.request.urlopen(req, timeout=30)


def shortbread_url(pbf_url):
    # Same rule as OfflineMapProviderClient: ".../slovakia-latest.osm.pbf"
    # becomes ".../slovakia-shortbread-1.0.mbtiles".
    suffix = "-latest.osm.pbf"
    if not pbf_url.endswith(suffix):
        return None
    return pbf_url[: -len(suffix)] + "-shortbread-1.0.mbtiles"


def has_extract(url):
    try:
        with request(url, method="HEAD") as response:
            return response.status == 200
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return False
        raise


def main():
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUTPUT
    with request(INDEX_URL) as response:
        index = json.load(response)
    areas = []
    for feature in index["features"]:
        properties = feature["properties"]
        url = shortbread_url(properties.get("urls", {}).get("pbf", ""))
        if url:
            areas.append((properties["id"], url))

    missing = []
    for position, (area_id, url) in enumerate(sorted(areas), start=1):
        # Any answer other than 200/404 aborts: a partial list would hide real maps.
        if not has_extract(url):
            missing.append(area_id)
        print(f"\r{position}/{len(areas)} probed, {len(missing)} missing", end="", file=sys.stderr)
        time.sleep(DELAY_SECONDS)
    print(file=sys.stderr)

    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    lines = [
        "# Geofabrik areas without a Shortbread 1.0 MBTiles extract.",
        f"# Probed {stamp}: {len(areas)} areas, {len(missing)} missing.",
        "# Regenerate with tools/offline_packages/probe_geofabrik_shortbread.py",
        *missing,
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")


if __name__ == "__main__":
    main()
