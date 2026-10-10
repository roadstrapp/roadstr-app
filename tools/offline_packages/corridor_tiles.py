#!/usr/bin/env python3
import argparse
import json
import math
import pathlib


def tile_id(latitude: float, longitude: float) -> int:
    row = min(719, max(0, math.floor((latitude + 90.0) * 4.0)))
    column = min(1439, max(0, math.floor((longitude + 180.0) * 4.0)))
    return row * 1440 + column


def main() -> None:
    parser = argparse.ArgumentParser(description="Create conservative 0.25 degree corridor coverage")
    parser.add_argument("--polyline", required=True, type=pathlib.Path,
                        help="UTF-8 text with one longitude,latitude pair per line")
    parser.add_argument("--margin-meters", required=True, type=float)
    parser.add_argument("--tile-ids", required=True, type=pathlib.Path)
    parser.add_argument("--coverage", required=True, type=pathlib.Path)
    args = parser.parse_args()
    if not 1 <= args.margin_meters <= 250_000:
        raise ValueError("margin must be between 1 and 250000 metres")
    points = []
    for line in args.polyline.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        longitude, latitude = (float(value.strip()) for value in line.split(",", 1))
        if not (-180 <= longitude <= 180 and -90 <= latitude <= 90):
            raise ValueError("coordinate out of bounds")
        points.append((longitude, latitude))
    if len(points) < 2:
        raise ValueError("polyline needs at least two points")
    selected = set()
    margin_lat = args.margin_meters / 111_320.0
    for first, second in zip(points, points[1:]):
        latitude = (first[1] + second[1]) / 2
        longitude_scale = max(0.05, math.cos(math.radians(latitude)))
        margin_lon = args.margin_meters / (111_320.0 * longitude_scale)
        min_lon, max_lon = min(first[0], second[0]) - margin_lon, max(first[0], second[0]) + margin_lon
        min_lat, max_lat = min(first[1], second[1]) - margin_lat, max(first[1], second[1]) + margin_lat
        row_start = max(0, math.floor((min_lat + 90) * 4))
        row_end = min(719, math.floor((max_lat + 90) * 4))
        col_start = max(0, math.floor((min_lon + 180) * 4))
        col_end = min(1439, math.floor((max_lon + 180) * 4))
        for row in range(row_start, row_end + 1):
            for column in range(col_start, col_end + 1):
                selected.add(row * 1440 + column)
    args.tile_ids.write_text("\n".join(map(str, sorted(selected))) + "\n", encoding="utf-8")
    coverage = {
        "kind": "corridor",
        "centreline": [[lon, lat] for lon, lat in points],
        "marginMeters": args.margin_meters,
        "level2TileIds": sorted(selected),
    }
    args.coverage.write_text(json.dumps(coverage, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
