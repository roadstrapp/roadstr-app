# Offline visual maps — Phase 4 design

This phase adds persistent visual map packages alongside (and independently
from) offline routing packages.  It does not enable offline routing by merely
installing a map.

## Runtime contract

The package manager downloads a complete PMTiles or MBTiles archive into
`filesDir/offline-packages`. It resumes `.part` files, verifies the declared
SHA-256 or the provider entity validator plus exact size, computes and pins a
local SHA-256, validates the container header, then atomically renames the
archive and publishes a pointer. MapLibre reads active files with:

```text
pmtiles://file://<absolute path>
mbtiles://<absolute path>
```

MapLibre Android 13.5.2 is above the PMTiles support floor.  Its PMTiles
source is a random-access reader; it is not an OfflineManager pack, so Roadstr
must own persistence, quota, deletion and activation.

## Manifest additions

`datasetType: "pmtiles-map"` or `"mbtiles-map"` identifies a visual map. Optional `label` is the
human-facing area name.  `mapFormat` identifies the schema consumed by the
style:

* `shortbread-vector` (the default for old PMTiles entries), or
* `raster`.

`catalogAreaId` is optional but recommended for catalogued packages. It is a
stable geographic id (for example `it-emilia-romagna`), not a translated name.
The application decorates its local world hierarchy with these ids. A node can
resolve to one direct package or to a deduplicated set of complete child
packages; missing children are shown as partial/unavailable instead of getting
an invented download button.

The existing polygon/corridor coverage, level list, licence, attribution and
size remain mandatory. A SHA-256 remains mandatory for ordinary manifests;
the built-in Geofabrik adapter can instead pin the live ETag/Last-Modified
validator and replaces it with the computed SHA-256 at activation. A catalogue can therefore list Umbria,
Lombardia + Veneto, or a pre-composed multi-region archive without changing
the downloader.

Minimal visual entry (the enclosing manifest still has `schemaVersion` and
`generatedAt`):

```json
{
  "id": "umbria-map", "label": "Umbria", "catalogAreaId": "it-umbria",
  "version": 1, "datasetType": "pmtiles-map",
  "mapFormat": "shortbread-vector", "levels": [0, 6, 14],
  "sizeBytes": 123456789, "installedSizeBytes": 123456789,
  "sha256": "…64 lowercase hex characters…",
  "url": "https://packages.example.org/maps/umbria.pmtiles",
  "license": "ODbL-1.0", "attribution": "© OpenStreetMap contributors",
  "area": { "kind": "polygon", "coordinates": [[[12, 42], [13, 42], [13, 43], [12, 43], [12, 42]]] },
  "build": { "tool": "versatiles", "version": "…", "recipe": "…", "compatibilityId": "roadstr-shortbread-1" }
}
```

Multiple installed map archives are exposed as separate sources in one style,
so adjacent regions can coexist.  Package producers should still publish one
composite archive when regions overlap or share a style build; this avoids
duplicate features and gives the catalogue one compatibility/version boundary.

## VersaTiles/Shortbread boundary

VersaTiles OSM data is Shortbread vector data.  It is not interchangeable with
the OpenMapTiles source layers used by the old prototype style.  The native
style currently renders the core Shortbread polygon/line layers (`land`,
`water_polygons`, `buildings`, `streets`, `boundaries`) and keeps the visual
map usable without network access.  It deliberately does not claim offline
labels yet: symbol layers require a matching local glyph/sprite bundle and a
versioned Roadstr style asset.  The manifest format leaves room for that style
bundle in the next package revision.

Raster PMTiles use the same local source path and are rendered directly.
Installing a package never changes the selected energy/performance profile;
the profile still controls the map engine and frame cap.

## Geographic catalog UI and providers

The primary screen opens on five continents and keeps the manifest URL under an
Advanced section. It refreshes the official Geofabrik `index-v1.json`, retains
the last valid copy, and merges its 500+ extract areas with a bundled compressed
GeoNames index (administrative divisions and 34,000+ cities). Search remains
local after that refresh and returns the full breadcrumb path. City nodes map to
the smallest terminal Geofabrik extract whose published geometry contains the
city. Selecting multiple nodes creates one transfer plan keyed by artifact id,
so overlapping logical groupings never download the same package twice.

The provider row makes Geofabrik, VersaTiles, OpenMapTiles and Roadstr JSON
explicit. Geofabrik is directly installable. VersaTiles' public world archive
requires a regional CLI extraction before import, while OpenMapTiles is a
self-host/build pipeline rather than a free public regional package catalog;
the UI says this instead of presenting either source as a working fake download.
Forward and backward hierarchy changes use short, direction-aware horizontal
transitions.

## UI and policy

The Offline areas panel now shows both routing and visual map artifacts, with
the format and catalogue label visible.  Downloads remain explicit, Wi-Fi-only
by default, resumable and cancellable.  There is no startup catalogue polling
and no hidden background transfer.

The repository ships no large regional map archive. The built-in adapter uses
Geofabrik's HTTPS catalog and official Shortbread MBTiles extracts; custom
package hosts must provide HTTPS static files, integrity metadata, attribution
and a manifest. Do not bulk-download from the public `tile.openstreetmap.org`
server; use a provider that explicitly permits redistribution/offline use.

## Validation

The JVM tests cover provider/catalog parsing, city-to-extract mapping, manifest
parsing, PMTiles and MBTiles activation framing, entity-validator pinning and
style selection. Device testing uses a small official Geofabrik extract to
exercise download, activation and local rendering without touching the public
OSM tile server.
