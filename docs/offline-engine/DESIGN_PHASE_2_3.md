# Design: local routing contract and routing packages (Phases 2–3)

Status: Phases 2 and 3 implemented on the feature branch. Phone acceptance,
production package hosting and manifest signing remain release gates.

Implemented in Phase 2: the common selector and engine contract, the
`valhalla-mobile` adapter with one reusable instance per dataset, code-171
mapping, opt-in settings, explicit online fallback and the existing response
protocol. Implemented in Phase 3: bounded manifest parsing, public-HTTPS-only
transport, resumable foreground/UI-scoped downloads, SHA-256 and artifact-open
validation, atomic version activation, conservative coverage, package UI and
reproducible tools under `tools/offline_packages/`.

## Scope and invariants

Phase 2 introduces a common execution boundary for the existing online routing
service and one local Valhalla adapter. Phase 3 introduces explicit package
discovery, download, verification, installation, deletion and coverage queries.
Online OSRM, Valhalla, GraphHopper and ORS behaviour remains unchanged when the
new setting is off. Offline routing is off by default. Opening the app, planning
an online route or merely enabling location must not contact the package host.

No package starts downloading because routing failed. No update runs at startup.
No provider or privacy mode changes silently. Missing local coverage is a named
outcome, never a straight line.

## 1. Routing-engine boundary (Phase 2)

Add value-only types beside `NativeRoutingService`, not inside UI code:

```kotlin
interface RoutingEngine {
    val id: String
    suspend fun route(request: RoutingEngineRequest): RoutingEngineOutcome
}

data class RoutingEngineRequest(
    val origin: RoutingRequestPoint,
    val destination: RoutingRequestPoint,
    val via: List<RoutingRequestPoint>,
    val mode: NativeRouteTransportMode,
    val languageCode: String,
    val avoidance: RoutingRouteAvoidance,
)

sealed interface RoutingEngineOutcome {
    data class Success(val routes: List<RoutingParsedRoute>) : RoutingEngineOutcome
    data class Unavailable(val reason: UnavailableReason) : RoutingEngineOutcome
    data class Failed(val kind: FailureKind) : RoutingEngineOutcome
}
```

`OnlineRoutingEngine` delegates to the existing `NativeRoutingService`; it does
not duplicate request building or parsing. `ValhallaLocalRoutingEngine` builds
the same bounded Valhalla JSON request, calls `routeRaw`, then passes the raw
response to `RoutingResponseProtocol.parseValhalla`. The returned value is the
existing `RoutingParsedRoute`, so the planner, overlay, saved-route snapshot and
navigation session need no provider-specific model.

The adapter owns exactly one `Valhalla` instance per installed dataset id and
reuses it. Engine creation and close are serialized. Each instance receives a
dedicated configuration file, because the wrapper's default manager otherwise
writes the same `valhalla.json`. Replacing or deleting an active package first
closes its instance. The adapter never logs coordinates, package paths, raw
requests or raw responses.

Planned dependencies, added only in Phase 2 and then recorded in
`THIRD_PARTY_NOTICES.md`:

- `io.github.rallista:valhalla-mobile:0.6.4` (MIT);
- `io.github.rallista:valhalla-models-config:0.6.0` (MIT);
- `io.github.rallista:valhalla-models:0.6.0` (MIT).

Before merging, compile both Gradle roots and the release APK with the project's
Java target. The wrapper module was built with Java 21; this is an explicit gate,
not an assumption. Verify the APK contains only the intended ABI libraries and
keeps 16 KiB alignment.

### Selection policy

`RoutingEngineSelector` is a pure decision table with these inputs: offline
setting, requested mode, installed coverage result, connectivity, selected
online provider and whether the user requested online fallback.

| Offline setting | Coverage | Network | Result |
|---|---|---|---|
| Off (default) | any | any | Existing online provider, exactly as today |
| On | Covers complete request | any | Local Valhalla |
| On | Not covered | available and fallback disclosed/allowed | Selected online provider, with “outside downloaded area” status |
| On | Not covered | unavailable or fallback disallowed | `Unavailable(AreaNotDownloaded)` |
| On | Coverage unknown/corrupt | any | Do not open local engine; show package problem and apply the same explicit fallback rule |

Coverage must include origin, destination, all intermediate stops and a
continuous corridor appropriate to the package type. A set of points inside
disconnected polygons is insufficient.

Valhalla error code 171 maps to `Unavailable(AreaNotDownloaded)`. Other bounded
input errors map to `InvalidRequest`; corrupt data/configuration maps to
`DatasetInvalid`; cancellation remains cancellation; unexpected native failure
maps to `EngineFailure`. The UI text for 171 is “Area not downloaded”, never
“route unavailable” and never a geometric fallback.

### Settings and disclosure

Add an “Offline routing” switch, off by default, and a separate “Downloaded
areas” row. The switch explains:

- local routing processes coordinates on this device;
- routing data is separate from offline map data;
- package size is shown before every explicit download;
- stored routes can already be followed offline, but recalculation needs either
  installed coverage or a network provider;
- outside installed coverage, the app asks/indicates before using the selected
  online provider.

Opening Settings alone performs no package request. The manifest is fetched only
when “Downloaded areas” is opened. Default transfers are Wi-Fi only. “Use mobile
data this time” is a per-download confirmation, not a persistent automatic opt-in.

## 2. Package manager (Phase 3)

### Manifest

Use a bounded JSON document with a schema version and generation timestamp.
Every artifact has:

```json
{
  "id": "routing-region-example-v1",
  "version": 1,
  "datasetType": "valhalla-routing",
  "area": {"kind": "polygon", "coordinates": []},
  "levels": [0, 1, 2],
  "sizeBytes": 455000000,
  "sha256": "64 lowercase hex characters",
  "url": "https://host.example/path/file.tar",
  "license": "ODbL-1.0",
  "attribution": "© OpenStreetMap contributors",
  "build": {"tool": "valhalla", "version": "3.9.1", "recipe": "recipe id"}
}
```

For corridors, `area` contains the simplified centreline, margin and included
level-2 tile ids; for regions it contains a polygon/multipolygon; a national
skeleton declares levels 0–1 and its country boundary. URLs must be HTTPS,
credential-free and pass the same public-address/redirect rules as other bounded
HTTP clients. Limit manifest bytes, entry count, nesting, strings, coordinates
and polygon vertices before allocation. Unknown schema/type is rejected.

The first release may pin a manifest signing public key and verify a detached
Ed25519 signature. SHA-256 protects artifact integrity; the signature protects
the manifest's URL and metadata. If signing is deferred, HTTPS origin pinning and
the residual supply-chain risk must be accepted explicitly before release. The
current implementation is HTTPS plus artifact SHA-256 and is not release-ready
until the owner chooses one of those two authenticity policies.

### Download and atomic installation

1. The user selects an artifact. Show exact compressed/download size, estimated
   installed size, current free bytes, licence and attribution.
2. Require `freeBytes >= installedBytes + downloadBytes + safetyMargin`. Use a
   conservative safety margin of max(10%, 256 MiB), then recheck before install.
3. Create `<id>-<version>.part` in the same private-files filesystem as the final
   artifact. Persist a small journal containing id, version, expected length,
   ETag/Last-Modified if supplied, and received byte count.
4. Resume only when the server returns a valid `206` matching the requested
   `Range` and validators. A `200` restarts explicitly from byte zero. Reject
   compressed-transfer ambiguity and lengths beyond the manifest.
5. Cancellation closes the stream and retains `.part` only after the UI says it
   can be resumed. “Delete download” removes both journal and `.part`.
6. Stream SHA-256 over the complete file. On mismatch, quarantine/delete the
   `.part`, retain the old installed version and show a corruption error.
7. Validate artifact framing (`tiles.tar` index/config for Valhalla). Write and
   fsync an installed metadata record. Rename `.part` to the versioned final name
   on the same filesystem, then atomically replace the active pointer file.
8. Only after the new active pointer is durable and the engine can open it may
   the old inactive version be deleted.

Use a foreground, user-visible transfer while the chosen download runs. Do not
schedule periodic work. Process death resumes only when the user returns to the
package screen or explicitly resumes the transfer.

### Updates and deletion

An update is another explicit download. Show old/new versions and sizes; never
remove the working version first. Deleting an active package closes its local
engine, removes the active pointer, then deletes versioned data. Saved routes are
not deleted: their `offlineRecalculable` projection becomes false, while stored
geometry remains navigable.

### Overlapping and adjacent areas

Store artifacts independently by id/version and build a read-only coverage index
from installed metadata. Do not merge tar files on the phone. The integrated
wrapper opens one `tile_extract`; therefore the current selector requires one
installed artifact to cover the complete request. It deliberately does not form
an on-device union of adjacent or overlapping archives, even when their build ids
match. A catalogue may publish a prebuilt combined archive when that behavior is
required. This conservative rule prevents endpoints in two packages from being
reported as routable when no single Valhalla instance has the continuous graph.

Queries exposed to the selector:

- `coversPoint(point, datasetType): CoverageResult`;
- `coversRoute(points, marginMeters, datasetType): CoverageResult`;
- `coveringPackages(request): Set<PackageId>`;
- `missingSegments(route): List<RouteInterval>` remains a later UI refinement;
  the implemented boundary reports covered/not-covered/unknown and the ids of
  packages that individually cover the complete sampled request.

The conservative result is `Unknown/NotCovered`. False negatives can use the
online path after disclosure; false positives reach Valhalla 171 and must still
be mapped to “area not downloaded”.

## 3. Reproducible build tools

Scripts now live under `tools/offline_packages/`, with pinned versions/checksums,
`--help`, deterministic output paths, a machine-readable recipe and a verify-only
mode. Scripts never modify app source.

- `fetch_extract.sh`: explicit source URL, checksum and ODbL attribution record.
- `build_pmtiles.sh`: PBF → Planetiler 0.10.2, OpenMapTiles profile, selectable
  max zoom (12/13/14), deterministic metadata and SHA-256. Declare at least
  2.5 GiB Java heap for the measured Italy build.
- `build_valhalla.sh`: PBF → admins/time zones/default speeds/elevation choice →
  auto-only Valhalla tiles → `valhalla_build_extract`. Declare more than 3.5 GiB
  RAM; the successful measured setup used 9 GiB available.
- `build_routing_skeleton.sh`: select national levels 0–1 and produce the
  versioned skeleton (measured Italy size 375 MB).
- `build_routing_region.sh`: combine the matching skeleton with regional level-2
  tiles and emit polygon/tile coverage.
- `build_routing_corridor.sh`: route polyline + explicit margin → 0.25° level-2
  tile ids (`row * 1440 + column`) → matching skeleton + selected local tiles.
- `make_manifest.sh` and `verify_package.sh`: sizes, hashes, licence, build recipe,
  coverage and optional signature.

Production Valhalla recipes must record whether default speeds, time zones and
elevation are enabled. The research dataset omitted all three and is not a
production ETA reference. PMTiles notices must name OpenStreetMap contributors
(ODbL) and OpenMapTiles; Planetiler is Apache-2.0.

**Open owner decision:** where are manifest and static artifacts hosted? Record
provider, jurisdiction, access-log retention, bandwidth/cost limits, TLS/domain
ownership and availability target before Phase 3 implementation. This design
does not choose or imply a host.

## 4. Tests and phone measurements

### Phase 2 exit checks

Automated: selector decision table; no package-host request on startup/settings;
Valhalla raw JSON parsed through `RoutingResponseProtocol`; code 171 mapping;
one engine instance reused and closed; corrupted config; cancellation; online
regression suite; APK dependency/no-Google/16 KiB checks.

Owner's phone procedure (no adb required by this task):

1. Use a mid-range arm64 phone with 4–6 GB RAM, battery 80–100%, unplugged,
   battery saver off, screen brightness fixed and thermal state noted.
2. Install the Phase 2 probe/build and copy the same full and corridor packages
   used by `tools/routing_probe/` into app-private storage through its documented
   import action.
3. Force-stop, cold-open and time engine-ready plus first route. Run the fixed
   16-route set and six reroutes three times; record median/p95, failures and peak
   app memory from Android's developer memory screen.
4. Navigate a recorded 30–60 minute trip unplugged with at least three real
   deviations. Record start/end battery, elapsed time, thermal warnings and
   whether each deviation was local, online or explicitly unavailable.
5. Repeat with offline routing off and confirm behaviour/provider match the
   current build exactly.

### Phase 3 exit checks

Automated: `Range` resume (`206`, rejected mismatches, `200` restart); cancellation;
size/free-space boundary; SHA mismatch; process restart journal; atomic upgrade
rollback; deletion while engine is open; manifest bounds; overlapping/adjacent
coverage; point and route coverage; zero package-host traffic before package UI.

Owner's phone procedure:

1. On the same mid-range phone, open package management and confirm this is the
   first package-host request. Note displayed size and Android free space.
2. Start on Wi-Fi, cancel at roughly 25%, reopen and resume. Repeat once with the
   process killed from Android recents. Confirm received bytes continue rather
   than restart when validators match.
3. Attempt mobile data: verify the default blocks transfer and the one-time
   confirmation names bytes/cost risk. Cancel it; confirm no later background use.
4. Install a corridor, route inside it, then request a point outside it. Confirm
   the latter says “area not downloaded” and draws no straight line. Exercise an
   adjacent boundary and an overlapping regional package.
5. Upgrade with a deliberately bad checksum and with storage nearly full. The old
   package must remain usable. Then perform a valid update and deletion.
6. For maps, cold-start the real Roadstr style against PMTiles at zooms 5, 10,
   12, 13 and 14; record first-render/settle time, missing labels/icons, memory
   and battery. The earlier 65 ms style result used a minimal style and is not an
   acceptance result.

## 5. Risks and open questions

- **Java 21 compatibility:** compile both roots and release toolchain before
  accepting the wrapper; do not solve this by silently raising app requirements.
- **Single effective maintainer:** keep a reproducible fallback build of the MIT
  wrapper with NDK 29 and vcpkg. Exercise it before depending on an unmaintained
  binary, including 16 KiB page alignment and ABI checks.
- **Production Valhalla configuration:** owner must approve default speeds,
  time-zone database and elevation source/licence. ETA quality needs real traces.
- **National skeleton size:** 375 MB for Italy is a large fixed floor for every
  corridor/region. Measure whether regional skeletons remain connected and useful
  before promising small downloads.
- **Package hosting:** owner decision described above; no default host yet.
- **Manifest authenticity:** decide whether Phase 3 requires an Ed25519 signature
  at first release or records acceptance of HTTPS-only residual risk.
- **Package granularity:** measure regions, multiple corridors, mountains and
  islands before choosing the catalogue. One Florence–Perugia corridor is not a
  representative product catalogue.
- **Storage backend:** Phase 1's bounded encrypted SharedPreferences value should
  move to an atomic encrypted file if phone measurements show multi-megabyte
  writes block or duplicate excessive memory. The persisted schema remains valid.
