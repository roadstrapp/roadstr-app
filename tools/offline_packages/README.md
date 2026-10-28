# Offline package build tools

These scripts build and verify Roadstr's user-selected offline artifacts. They
never choose a source or package host and never modify application source.

Pinned toolchain: Planetiler 0.10.2 and Valhalla 3.9.1. Every source PBF and
Planetiler jar must be supplied with a SHA-256. Recipes record source/output
hashes and the Valhalla choices for default speeds, time zones and elevation.

Typical order:

1. `fetch_extract.sh` downloads one explicit HTTPS PBF and records attribution.
2. `build_shortbread_pmtiles.sh` converts a VersaTiles Shortbread extract to
   PMTiles (use `build_pmtiles.sh` only for the legacy OpenMapTiles experiment).
3. `build_valhalla.sh` makes the full auto-only graph and `tiles.tar`.
4. `build_routing_skeleton.sh`, `build_routing_region.sh` or
   `build_routing_corridor.sh` select a distributable routing package.
5. `make_manifest.sh` derives exact sizes and hashes from artifact specs.
6. `verify_package.sh` is the final build-pipeline framing/checksum gate.

All shell entry points support `--help`; build entry points also support
`--verify-only`. The measured national Planetiler build needs 2.5 GiB heap.
Valhalla needs more than 3.5 GiB RAM; the successful measured setup had 9 GiB.

The scripts require Bash, Python 3, `sha256sum`, and, depending on the command,
the `versatiles` CLI, Java, `curl`, `jq`, Planetiler, or Valhalla's command-line
programs.  `build_shortbread_pmtiles.sh` accepts the public VersaTiles source
only when the caller supplies its explicit HTTPS URL; it never chooses a
catalogue or package host on behalf of Roadstr.

`build_geonames_catalog.sh` refreshes the compact, bundled world place index
from the official GeoNames `cities15000` and `admin1CodesASCII` exports. It is
build-time catalog data only: the app does not contact the GeoNames web API.

`probe_geofabrik_shortbread.py` refreshes
`android/app/src/main/assets/offline_catalog/geofabrik-shortbread-missing.txt`,
the Geofabrik areas that have no Shortbread MBTiles extract. Geofabrik's index
does not list Shortbread, and the extracts are published at different levels
(one file for Czechia and none for its regions, one per Polish voivodeship and
none for Poland), so the script sends one HEAD request per area and keeps the
ids that answer 404. Rerun it before a release; an area missing from the list
is assumed to have a file, as before the list existed.

Manifest artifact specs may include `catalogAreaId` (a stable id such as
`it-emilia-romagna`) and `label`. The former connects a verified package to the
in-app geographic catalog; it is never inferred from a translated display name.
