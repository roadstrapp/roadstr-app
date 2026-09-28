# Native HTTP safety and provider execution parity

This increment freezes the deterministic HTTP safety decisions and adds a
real, cancellable native transport without changing production network
ownership. Flutter remains the live client; Kotlin sockets are exercised only
against local test servers.

## Implemented boundary

`lib/services/http_safety_policy.dart` is now used by the shipped Dart
`BoundedHttp` helper and GraphHopper URL validation. Its Kotlin counterpart is
`core.network.HttpSafetyPolicy.kt`. The shared boundary covers:

- the six per-attempt timeout tiers from `NetworkTimeouts`;
- the seven response caps from `NetworkLimits`, including the separate 32 MiB
  full-journey route ceiling used by the live routing service;
- redirect forwarding disabled before dispatch;
- declared `Content-Length` rejection before buffering;
- cumulative per-chunk accounting with the exact byte ceiling accepted;
- fail-closed invalid/negative lengths and overflow-safe Kotlin arithmetic;
- the current GraphHopper host and cleartext-loopback decision, including
  `localhost`, `127.0.0.1` and the Android emulator alias `10.0.2.2`.

`BoundedHttp` still owns the real Dart stream and total-body deadline. It now
delegates the deterministic size decisions to the fixture-backed policy, so the
oracle exercised by production and the oracle exported to Kotlin are the same
code.

`service.network.NativeBoundedHttpClient` is the first socket-owning Kotlin
adapter. It accepts both native search and routing request values, reuses one
OkHttp dispatcher/connection pool, applies an explicit whole-call deadline,
rejects oversized declared bodies before reading, accounts every streamed
chunk before retaining it, disables HTTP and HTTPS redirects, disables hidden
connection retries and cancels the physical OkHttp `Call` when its coroutine is
cancelled. Its value-free failure type never includes a URI, header or body.

`service.routing.NativeRoutingService` is the first headless native provider
executor. Given an already resolved configuration, it composes and dispatches
OSRM, OpenRouteService or GraphHopper requests, keeps the shipped 10/12-second
provider deadlines and 32 MiB full-route ceiling, normalizes the response and
drives the one-retry OSRM bearing state machine. Caller cancellation propagates
through the service and physically cancels the active call without starting the
fallback. The service has no startup, UI or secure-store owner yet.

The app pins OkHttp 4.12.0 and kotlinx-coroutines 1.10.2, matching the versions
already selected by MapLibre/AndroidX instead of upgrading the existing Flutter
runtime transitively. Both are permissively licensed; MockWebServer 4.12.0 is
test-only.

`lib/services/search_provider_protocol.dart` is also called by the live Dart
Nominatim, Photon and Overpass paths. Its socket-free Kotlin counterpart locks
the exact method, endpoint, encoding, parameters, headers and form body. See
`SEARCH_NETWORK_CORE_PARITY.md` for the provider matrix and privacy boundary.

`lib/services/search_response_protocol.dart` is called by those live Dart
clients and the POI result mapper. Its socket-free Kotlin counterpart freezes
Nominatim forward/reverse, Photon GeoJSON and Overpass envelope/result parsing,
including validation, bounded remote text, labels/categories and distance
rounding.

`lib/services/search_ranking_protocol.dart` is called by production
`PlaceSearchService` after normalization. Its Kotlin counterpart freezes query
caps, phase/provider planning, fuzzy/city/brand/distance ranking, proximity
dedupe, relaxed retry and POI-first merge without scheduling a request.

`lib/services/search_orchestration_protocol.dart` is also called by production
`PlaceSearchService`. Its Kotlin counterpart freezes enabled-provider tracking,
out-of-order completion, first-nonempty partial delivery, failure-as-empty
fallback, one relaxed retry batch and one immutable final result without
opening a socket or choosing a coroutine implementation.

`lib/services/routing_request_protocol.dart` is called by the live Dart routing
service for OSRM, OpenRouteService, GraphHopper and Valhalla. Its Kotlin
counterpart freezes request composition, including provider profiles/locales,
API-key destination, waypoints, alternatives, bearings, retiming and avoidance
options. See `ROUTING_NETWORK_CORE_PARITY.md`.

`lib/services/routing_response_protocol.dart` is also called by every live Dart
routing path. Its Kotlin counterpart freezes normalized route geometry,
maneuvers, localized instructions, speed limits, response validation and
provider errors. The headless Kotlin routing service now exercises the same
parser after bounded dispatch, while production dispatch and UI fallback remain
owned by Flutter.

`lib/services/routing_orchestration_protocol.dart` is called by both production
map implementations through `RoutingService`. Its Kotlin counterpart freezes
when a moving OSRM reroute may carry a bearing, constrained-result admission,
the `8 × + 5 km` implausible-detour ceiling, one unconstrained fallback and
terminal failure/ordering semantics. `NativeRoutingService` now drives it
sequentially and deliberately catches only value-free routing failures, leaving
caller cancellation terminal.

`lib/services/routing_avoidance_protocol.dart` is called by both production
avoidance entry points through `RoutingService`. Its Kotlin counterpart freezes
hard-to-soft and direct-track attempt ordering, route-shape sampling, ferry
recognition, per-leg OSRM admission, proportional Valhalla fallback and the
minimum verified-road budget. It does not dispatch or cancel requests.

`lib/services/routing_provider_config.dart` is called by both production map
implementations. Its Kotlin counterpart freezes persisted provider/key/server
resolution, required-credential fallback, secure-key precedence and the
routing-time legacy-key migration decision. Kotlin still has no secure-storage
adapter; the headless routing service accepts and dispatches only an already
resolved configuration.

## Compatibility detail

The shipped GraphHopper validator requires a host and refuses explicit
`http://` outside the three Android cleartext exceptions. It does not currently
reject user-info or a non-HTTP scheme at validation time; the downstream HTTP
client handles those inputs. The fixture records that behavior instead of
silently tightening it during the rewrite. Restricting it is a separate
security/product decision and should update both runtimes and the fixture
deliberately.

## Shared fixture

`http_safety_policy_v1.tsv` contains 51 Dart-generated cases:

- 6 timeout tiers and 7 response limits;
- redirect policy;
- 9 declared-length boundaries;
- 9 chunk transcripts, including exact, cumulative and oversized cases;
- 19 GraphHopper endpoint cases covering HTTPS, ports, loopback aliases, LAN
  cleartext, IPv6, subdomains, malformed/hostless input and current scheme/
  user-info behavior.

Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_http_safety_fixture.dart
dart run tools/kotlin_rewrite/generate_http_safety_fixture.dart --check
```

Both `http_safety_policy_test.dart` and `HttpSafetyPolicyParityTest.kt` consume
the same generated contract. Existing Dart integration tests still prove that
an oversized body is rejected and a 3xx response is returned without following
the redirect. Ten `NativeBoundedHttpClientTest` local-server cases additionally
prove exact routing GET and search POST dispatch, repeated response headers,
redirect refusal, declared and chunked overflow, exact-limit acceptance, total
slow-body deadline, physical coroutine cancellation, value-free invalid-request
failure and no hidden transport retry.

Ten `NativeRoutingServiceTest` cases prove OSRM/ORS/GraphHopper composition and
normalization above that adapter, provider-specific deadlines, the full-route
cap, a real self-hosted GraphHopper loopback exchange, value-free status/parser
failures, invalid endpoint rejection, exactly one bearing fallback and caller
cancellation without a hidden retry.

`search_provider_requests_v1.tsv` adds 39 Dart-generated Nominatim, Photon and
Overpass request outcomes. It is consumed by both runtimes and includes their
intentionally different text encodings, Nominatim viewboxes, Photon location
rounding/language allowlist and exact Overpass mirror/header/body contract.

`routing_requests_v1.tsv` adds 89 Dart-generated OSRM, OpenRouteService,
GraphHopper and Valhalla request/language outcomes. It is consumed by both
runtimes and includes mode/locale mapping, API-key isolation, coordinate/query
encoding, waypoint and bearing boundaries, retiming and avoidance policies.

`routing_responses_v1.tsv` adds 59 Dart-generated outcomes for those four
providers. Both runtimes compare the complete normalized route, including all
27 localized OSRM instruction tables, provider maneuver mappings, decorations,
speed limits, polyline6/multi-leg handling, retiming and malformed inputs.

`routing_orchestration_v1.tsv` adds 28 Dart-generated stateful reroute
transcripts. Both runtimes compare provider/course admission, constrained and
unconstrained outcomes, exact detour boundaries, shortest-route decisions,
single fallback, terminal errors and duplicate/late completion rejection.

`routing_avoidance_v1.tsv` adds 36 Dart-generated outcomes. Both runtimes
compare 10 hard/soft/track state transcripts and 26 re-timing cases spanning
sampling/count caps, rounding, ferry detection, malformed legs, distance slack,
coverage thresholds, proportional fallback and metadata retention.

`routing_provider_config_v1.tsv` adds 34 Dart-generated outcomes. Both runtimes
compare provider-key matching, OSRM credential-read modes, required keys and
servers, fallback issues, secure/legacy precedence, migration intent and Dart
Unicode trimming. Dart adapter tests additionally lock storage callback order
and failed-write safety.

`search_responses_v1.tsv` adds 34 Dart-generated Nominatim forward/reverse,
Photon and Overpass outcomes. Both runtimes compare normalized fields,
coordinates, categories, labels, text bounds, node/way centres, distances and
malformed-payload behavior; a category matrix covers 53 class/type pairs.

`search_ranking_v1.tsv` adds 53 Dart-generated planning/ranking outcomes. Both
runtimes compare provider-phase flags, prepared/relaxed queries, match scores,
ordered normalized results, 30 m duplicate decisions, Nominatim/POI precedence
and the 10-result ceiling.

`search_history_v1.tsv` adds 31 Dart-generated history-value outcomes. Both
runtimes compare tolerant row admission, coordinate/label limits, newest-first
dedupe, 100-load/5-store ceilings and persisted JSON strings. Flutter keeps
encrypted Hive ownership; Kotlin has no persistence adapter yet.

`search_orchestration_v1.tsv` adds 32 Dart-generated provider-completion
transcripts. Both runtimes compare phase/location provider sets, all settled
completion orders, one partial, failure/empty fallback, retry admission and
ordering, final merge precedence, duplicate suppression and terminal behavior.

## Deliberately outside this slice

- no native search provider service or production owner is wired. The native
  routing service is headless and only synthetic loopback tests dispatch
  coordinates, test keys and Roadstr user agents;
- DNS, TLS, Android Network Security Config and cleartext-loopback behavior
  still need Android integration/device tests in addition to local JVM tests;
- the native search-history store, search-provider job cancellation, UI
  request-generation suppression, Overpass backoff and live search execution
  remain above the single-call adapter; routing avoidance/re-timing still has
  no coroutine service owner;
- routing configuration decisions are locked, but native secure-storage/
  Keystore access and installed-app migration evidence remain open;
- DNS-aware SSRF checks for LNURL remain part of the later live-network gate.

The Kotlin adapter and routing service have no Activity, startup or persistence
wiring. Rollback is removal of the service, adapter and direct dependency
declarations while leaving the pure boundaries and Flutter client in place; no
stored data or endpoint defaults change.
