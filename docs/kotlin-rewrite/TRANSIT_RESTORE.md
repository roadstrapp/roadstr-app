# Public transport restoration and evolution

Status: implemented and JVM-tested on 2026-10-11; the public Transitous endpoint was checked once with an opt-in live probe. Production-wide use of that community endpoint still requires prior coordination with Transitous.

## Audit: Flutter 0.5.x versus Kotlin 1.0

The transit feature entered the Flutter tree in commit `8f5865e` and is present in tag `v0.5.11`. The comparison below is based on the tagged sources, not on the user report.

| Capability | Flutter 0.5.11 | Kotlin 1.0 before this restoration | Kotlin after this restoration |
|---|---|---|---|
| Planner mode | Transit in both Flutter map screens | Mode and Compose surface existed, but the journey coordinator rejected it | Live transit branch is connected to the shared planner |
| Provider | Transitous MOTIS `/api/v1/plan` | Bounded Transitous client existed but had no production UI owner | Transitous MOTIS stable `/api/v6/plan`; provider-neutral interface/router |
| Alternatives | Requested 3, then re-sorted by total duration | Parser retained alternatives; panel was detached | Provider order is preserved and identified as provider ranking |
| Walk + public-transport legs | Yes | Model/parser/overlay existed | Yes, including detailed sequential presentation |
| Modes | 21 wire values | 21 wire values | 28 current MOTIS values, including `FLEX`, `RIDE_SHARING`, `CABLE_CAR` and compatibility for `AREAL_LIFT` |
| Stops, line, direction, operator | Start/end names, line, headsign and agency | Parsed but not reachable from a live journey | Shown from provider data; provider-scoped route/trip/agency/stop IDs retained |
| Platforms and intermediate stops | No | No | Scheduled/expected platform and intermediate stops are parsed and presented when supplied |
| Timetable and realtime | Leg times and one realtime boolean | Same core fields | Scheduled/current times are distinct; delay, cancellation, alerts and realtime state are represented |
| Map rendering | Line geometry on both Flutter renderers; walking distinguished | MapLibre compiler/renderer existed but was detached | Selected itinerary plus boarding/alighting markers are live in the native MapLibre host |
| Active transit guidance | Not implemented | Not implemented | Not implemented; planned with explicit/manual boarding confirmation |
| Saved transit journey | Not implemented; road routes only | Road-only saved-route schema | Still deferred; must be a time-valid transit schema, not a frozen road route |
| Stop/departure search | General OSM search only | General native search | Still deferred; OSM infrastructure must not be presented as timetable data |

The legacy Flutter launcher remains buildable. Its Transitous URL and request headers were updated to the same v6/contact policy, but Kotlin is the maintained implementation.

## Implemented architecture

`TransitProtocol` is the provider-independent boundary. It retains origin, destination, requested and searched times, provider provenance, provider-scoped identifiers, alternatives, walking/wait totals, ordered legs, scheduled/current/actual timestamps, stops/platforms, accessibility, alerts, cancellation and geometry. Unknown values remain nullable or explicitly `Unknown`; no timetable value or vehicle position is synthesized.

`NativeTransitProvider` declares identity, priority, geographic coverage and capabilities. `NativeTransitProviderRouter` selects the highest-priority provider covering both endpoints and falls back only after a transient failure. It does not query every backend, merge legs from different graphs, or reinterpret a valid empty result as a provider failure.

`NativeTransitService` is the first adapter. It uses bounded HTTPS, physical coroutine cancellation, a 20-second/2-MiB budget, `Retry-After`-aware transient retries, a client/version/contact `User-Agent`, visible source attribution, `realtimeMode=REALTIME`, and detailed transfers. Coordinates exist only in the request and typed plan; raw URLs, bodies and causes are not retained in errors.

The planner accepts exactly one destination and no intermediate user waypoint for transit. The live plan is handed atomically to `NativeTransitJourneySession`, which projects both the selected card and MapLibre overlay. Cards expose provider alternatives without inventing a universal Roadstr score. Expanded detail includes direction, boarding/alighting stop, scheduled/current time and platform, operator, intermediate stops, delay, cancellation and alerts when available.

## Provider decision

| Provider/source | Decision | Reason and constraint |
|---|---|---|
| Transitous | Implemented and live-verified | Broad open-feed coverage and current MOTIS v6 contract. Public service is best-effort, FLOSS/non-commercial/light-use only; coordinate production rollout before routing load or many users. |
| Self-hosted MOTIS | Protocol-compatible and architecturally prepared | MIT engine; same versioned REST/OpenAPI family. Deployment, regional OSM/timetables/realtime feeds, monitoring and data licences remain operator responsibilities. |
| OpenTripPlanner 2 | Studied, not implemented | Useful independent regional engine, but current APIs are GraphQL. A real adapter needs schema/capability negotiation and an actual target deployment; a removed legacy REST adapter would be misleading. |
| Entur | Studied, not implemented | Strong Norway-specific GraphQL/realtime option with mandatory client identification. Add only with regional routing tests and current usage agreement. |
| transport.rest | Studied, not implemented | Regional community instances vary in upstream stability and limits; no instance is assumed free or unlimited. |
| MobilityDatabase, GTFS/GTFS-RT, NeTEx, GBFS | Dataset discovery/input only | These are catalogues or exchange formats, not universal journey planners. They belong behind MOTIS/OTP or a future offline router. |

Current official references: [Transitous API and usage policy](https://transitous.org/api/), [Transitous source attribution](https://transitous.org/sources/), [MOTIS OpenAPI](https://github.com/motis-project/motis/blob/master/openapi.yaml), [MOTIS engine and inputs](https://github.com/motis-project/motis), [OTP APIs](https://docs.opentripplanner.org/en/latest/apis/Apis/), [OTP data sources](https://docs.opentripplanner.org/en/latest/Data-Sources/), [Entur Journey Planner](https://developer.entur.org/pages-journeyplanner-journeyplanner/), [transport.rest](https://transport.rest/), [MobilityDatabase](https://mobilitydatabase.org/about), [GTFS overview](https://gtfs.org/documentation/overview/) and [GBFS](https://gbfs.org/).

## Verification and boundaries

Deterministic Kotlin fixtures cover urban walk/transit, interchange, rail metadata, realtime delay, cancellation/alerts, unavailable/fallback semantics, malformed bounds, geometry, marker projection, alternative selection and midnight/offset timestamps. Coordinator tests exercise the full provider-to-panel-to-map handoff without calling the road router. Dart contract tests protect the legacy and shared-source wiring.

The opt-in live test `ROADSTR_TRANSIT_LIVE_PROBE=1` queried central Milan once through the production adapter on 2026-10-11. Transitous returned three real alternatives; the first was WALK → subway line 3 toward Comasina → WALK, from DUOMO M3 to CENTRALE FS with intermediate stops and precision-6 geometry. This proves the current endpoint/parser path, not guaranteed coverage or service quality in every region.

The Kotlin performance profiles currently resolve to one MapLibre native host; the `osm` value is a compatibility preference, not a second native 2D renderer. The shared itinerary model therefore survives that preference change, but claiming two independently rendered native engines would be false. The legacy Flutter application still has separate raster and MapLibre adapters and uses the same provider contract.

Not yet implemented:

- active transit progress, voice prompts, missed-connection refresh and manual boarding/alighting confirmations;
- persisted transit itineraries and stale/original/updated comparison;
- stop/line/departure search and evidence-based OSM-to-schedule identity matching;
- a second production adapter, custom-backend settings UI, vehicle positions, GBFS and park-and-ride;
- on-device transit routing and physical-device accessibility/screenshot/performance evidence.

## Next phases

1. Coordinate expected load and release terms with Transitous before making its public endpoint the default for a broad production audience; expose the coordinate/logging disclosure in product privacy UI.
2. Add a configurable self-hosted MOTIS endpoint and health/capability negotiation, then a genuinely tested OTP GraphQL adapter for one maintained regional instance.
3. Build a transit navigation state machine around leg boundaries. GPS may estimate approach/progress, but boarding and alighting remain unknown until provider evidence or user confirmation says otherwise. Refresh only near relevant departure/transfer boundaries with lifecycle-aware throttling and backoff.
4. Add a versioned encrypted saved-transit schema storing query, provider IDs, schedule validity, selected legs/geometries and last verification. On reopen, label the saved snapshot as stale until a fresh provider result is compared.
5. Define signed regional offline manifests separate from map tiles: OSM extract, static GTFS/NeTEx artifact, licence/provenance, checksum, service-date range, timezone database version and router compatibility. Realtime remains network-dependent and must never be inferred from static data.
