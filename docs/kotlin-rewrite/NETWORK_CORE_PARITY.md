# Native HTTP safety and provider-request parity

This increment freezes the deterministic HTTP safety decisions needed by the
native networking rewrite without opening a socket or changing production
network ownership. Flutter remains the live client.

## Implemented boundary

`lib/services/http_safety_policy.dart` is now used by the shipped Dart
`BoundedHttp` helper and GraphHopper URL validation. Its Kotlin counterpart is
`core.network.HttpSafetyPolicy.kt`. The shared boundary covers:

- the six per-attempt timeout tiers from `NetworkTimeouts`;
- the six response caps from `NetworkLimits`;
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
provider errors. Network dispatch and fallback remain owned by Flutter.

`lib/services/routing_orchestration_protocol.dart` is called by both production
map implementations through `RoutingService`. Its Kotlin counterpart freezes
when a moving OSRM reroute may carry a bearing, constrained-result admission,
the `8 × + 5 km` implausible-detour ceiling, one unconstrained fallback and
terminal failure/ordering semantics. It does not dispatch or cancel requests.

`lib/services/routing_avoidance_protocol.dart` is called by both production
avoidance entry points through `RoutingService`. Its Kotlin counterpart freezes
hard-to-soft and direct-track attempt ordering, route-shape sampling, ferry
recognition, per-leg OSRM admission, proportional Valhalla fallback and the
minimum verified-road budget. It does not dispatch or cancel requests.

`lib/services/routing_provider_config.dart` is called by both production map
implementations. Its Kotlin counterpart freezes persisted provider/key/server
resolution, required-credential fallback, secure-key precedence and the
routing-time legacy-key migration decision. Kotlin still has no secure-storage
adapter and does not dispatch the resulting configuration.

## Compatibility detail

The shipped GraphHopper validator requires a host and refuses explicit
`http://` outside the three Android cleartext exceptions. It does not currently
reject user-info or a non-HTTP scheme at validation time; the downstream HTTP
client handles those inputs. The fixture records that behavior instead of
silently tightening it during the rewrite. Restricting it is a separate
security/product decision and should update both runtimes and the fixture
deliberately.

## Shared fixture

`http_safety_policy_v1.tsv` contains 48 Dart-generated cases:

- 6 timeout tiers and 6 response limits;
- redirect policy;
- 7 declared-length boundaries;
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
the redirect.

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

- no native HTTP engine, OkHttp dependency, DNS resolver or socket is wired;
- no native request is dispatched with coordinates, API keys or a Roadstr
  user-agent; Kotlin request values are inert fixture-backed data;
- total deadlines, cancellation and connection-pool ownership still need the
  native adapter;
- redirect/TLS/cleartext behavior needs Android integration tests in addition
  to this policy fixture;
- the native search-history store, physical request cancellation, UI
  request-generation suppression, Overpass backoff and live native execution
  remain; avoidance/re-timing policy is locked but has no coroutine/HTTP
  adapter;
- routing configuration decisions are locked, but native secure-storage/
  Keystore access and installed-app migration evidence remain open;
- DNS-aware SSRF checks for LNURL remain part of the later live-network gate.

The Kotlin policy has no Android, Activity, startup or persistence wiring.
Rollback is removal of the Kotlin boundary and fixture plus restoration of the
small Dart delegation; no stored data or endpoint defaults change.
