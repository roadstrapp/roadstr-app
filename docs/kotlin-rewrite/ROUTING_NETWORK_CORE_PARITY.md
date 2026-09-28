# Routing-provider request, response and orchestration parity

This increment freezes Roadstr's outbound routing request contract and inbound
response normalization and adds a headless Kotlin executor above the bounded
HTTP engine. Flutter still dispatches every production request.

## Production-used Dart boundary

`lib/services/routing_request_protocol.dart` now composes the requests used by
`RoutingService` for:

- OSRM driving, walking and cycling routes, including up to four intermediate
  stops, alternatives, reroute bearing hints and driving-route retiming;
- OpenRouteService driving, walking and cycling POSTs;
- GraphHopper public-cloud and self-hosted routes plus the settings probe;
- Valhalla hard motorway/toll exclusion, soft motorway/toll avoidance and
  unpaved-track avoidance.

The extraction preserves provider-specific behavior rather than normalizing it:

- OSRM requests use the three current public endpoints, GeoJSON steps, three
  alternatives only when there are no intermediate stops, and a normalized
  origin bearing with the shipped 45-degree tolerance;
- OpenRouteService keeps its JSON body, API-key header, vehicle profiles and
  language aliases such as Greek `gr` and Ukrainian `ua`;
- GraphHopper keeps unencoded points, `max_speed` details and provider vehicle
  names. The API key is sent only to the exact public endpoint and is omitted
  from self-hosted requests;
- Valhalla keeps its query-encoded JSON payload, locale mapping and distinct
  hard, soft and track-avoidance costing options;
- all four providers retain current coordinate rendering, query replacement,
  user-agent and fallback/default behavior.

`android/app/src/main/kotlin/app/roadstr/core/network/RoutingRequestProtocol.kt`
is the socket-free Kotlin counterpart. It also mirrors Dart's relevant string,
number, rounding, Unicode-trim and form-query behavior so fixture equality is
byte-exact rather than semantic-only.

`lib/services/routing_response_protocol.dart` is now the production-used Dart
boundary for OSRM, OpenRouteService, GraphHopper and Valhalla response parsing.
It owns the normalized route/step/speed-limit model, validation, passive-name
coalescing, maneuver decoration sanitization, Valhalla polyline6 decoding and
OSRM retiming-leg parsing. `RoutingService` retains live HTTP dispatch,
provider fallback and orchestration, but delegates every route response to this
boundary.

`RoutingResponseProtocol.kt` mirrors that normalization without sockets or
Android dependencies. It preserves provider-specific maneuver tables, OSRM
bearing correction and localized instructions, GraphHopper speed intervals,
Valhalla multi-leg endpoint de-duplication, route bounds and malformed-response
behavior. `NavigationPhrases.kt` is mechanically generated from the 27-language
Dart phrase table so translated route instructions do not become a second
hand-maintained source of truth.

`lib/services/routing_orchestration_protocol.dart` now owns the production
mid-navigation bearing fallback used by both Flutter map implementations. Only
a moving OSRM reroute with a supplied course starts with the shipped 45-degree
bearing constraint. A routing failure, empty constrained answer or shortest
route beyond `8 ×` straight-line distance plus 5 km admits exactly one
unconstrained retry; a plausible answer, second empty answer or second failure
is terminal. OpenRouteService, GraphHopper, stationary fixes and absent
bearings go directly to one unconstrained attempt. The asynchronous Dart
adapter preserves existing timeout/error behavior, while
`RoutingOrchestrationProtocol.kt` freezes the same state transitions without
coroutines or sockets.

`lib/services/routing_avoidance_protocol.dart` now owns the production
hard-exclusion to soft-preference transition, the direct unpaved-track attempt
and the deterministic part of OSRM re-timing. `RoutingService` uses its async
adapter for both avoidance entry points and delegates waypoint sampling and
per-leg admission to `RoutingRetimePolicy`; it still owns the Valhalla/OSRM
HTTP calls. The policy samples at roughly 5 km with a 120-waypoint ceiling,
keeps Valhalla time for ferry and diverged slices, accepts OSRM timing within
20% or 150 m, and requires at least half of the non-ferry distance to verify.
`RoutingAvoidanceProtocol.kt` and `RoutingRetimePolicy` mirror those decisions
without coroutines or sockets.

`lib/services/routing_provider_config.dart` now owns provider, API-key and
self-hosted GraphHopper-server resolution for both production map
implementations. It preserves their historical difference: MapLibre's exact
`osrm` fast path reads no credentials, while the raster map still performs the
existing routing-time legacy-key migration. A missing required key/server
falls back to OSRM with the same warning category, secure storage wins over
Hive, and migration writes the trimmed legacy key before deleting it.
`RoutingProviderConfigProtocol.kt` mirrors the pure decision without Android
storage or network dependencies.

## Headless native executor

`service.routing.NativeRoutingService` consumes an already resolved
`RoutingProviderConfiguration` and forms a complete headless path for the
current primary and avoidance providers:

- OSRM, OpenRouteService and GraphHopper request composition;
- the shipped 10-second OSRM/ORS and 12-second GraphHopper deadlines;
- the existing 32 MiB full-journey response ceiling, now named
  `journey_route` separately from the 2 MiB small route/probe tier;
- status and transport classification without retaining response bodies, URLs,
  coordinates or API keys in exceptions;
- normalized response parsing and immutable route lists;
- the exact constrained-to-unconstrained OSRM reroute state machine;
- Valhalla hard exclusion with one soft-preference fallback, direct track
  avoidance, accepted-route classification and best-effort OSRM per-leg
  re-timing with the shipped 25/30-second deadlines.

The orchestration loops catch only value-free routing failures. Best-effort
re-timing degrades on transport or response failure but explicitly rethrows a
caller `CancellationException`; cancellation therefore stops the active OkHttp
call and cannot trigger a fallback or stale result. Ten JVM tests cover all
three providers, limits, a real loopback GraphHopper exchange, redaction,
invalid endpoint admission, both fallback causes and cancellation.
Ten additional JVM tests cover exact Valhalla policies and bounds, hard-route
rejection, the single soft fallback, direct tracks, terminal failures,
best-effort re-timing, cancellation and a real Valhalla-to-OSRM loopback
exchange. Secure-store reads, startup/UI ownership and generation suppression
remain deliberately outside this slice.

## Shared fixture

`routing_requests_v1.tsv` contains 89 Dart-generated outcomes:

- 20 OpenRouteService language mappings;
- 25 Valhalla language mappings;
- 5 GraphHopper endpoint-selection cases;
- 5 OpenRouteService requests;
- 7 GraphHopper route requests and 4 probe requests;
- 14 OSRM route requests;
- 6 Valhalla requests;
- 3 OSRM retiming requests.

The matrix covers normal, unknown and upper-case locales; default, custom and
self-hosted endpoints; API-key inclusion and omission; all route modes;
negative-zero and tiny coordinates; Unicode and query encoding; the four-stop
cap; alternative suppression with stops; bearing rounding and wraparound;
existing-query replacement; and all shipped Valhalla avoidance policies. All
keys and hosts in the fixture are synthetic.

Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_routing_requests_fixture.dart
dart run tools/kotlin_rewrite/generate_routing_requests_fixture.dart --check
```

`routing_request_protocol_test.dart` locks the production-used Dart oracle.
`RoutingRequestProtocolParityTest.kt` reconstructs and compares all 89 outcomes
and checks the routing constants and boundary behavior independently.

`routing_responses_v1.tsv` contains 59 additional Dart-generated outcomes:

- 28 OSRM language cases covering all 27 shipped languages plus fallback;
- 8 OSRM maneuver, alternative, provider-error and malformed-route cases;
- 3 OpenRouteService maneuver/shape cases;
- 3 GraphHopper maneuver and speed-limit cases;
- 5 Valhalla maneuver, multi-leg, status and malformed-shape cases;
- 3 OSRM retiming responses;
- 7 localized/numeric exit-number cases;
- 2 signed and malformed polyline6 cases.

The canonical comparison includes geometry, maneuver instruction/direction/
modifier/location/decorations, road name/ref, distance, duration, avoidance
metadata, speed-limit offsets and provider errors. Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_routing_responses_fixture.dart
dart run tools/kotlin_rewrite/generate_routing_responses_fixture.dart --check
dart run tools/kotlin_rewrite/generate_kotlin_navigation_phrases.dart --check
```

`routing_response_protocol_test.dart` locks the extracted production Dart
oracle and its validation rules. `RoutingResponseProtocolParityTest.kt`
replays all 59 outcomes and separately checks native cleanup and limits.

`routing_orchestration_v1.tsv` contains 28 stateful Dart-generated reroute
transcripts. They cover all three providers, the exact 3 km/h course threshold,
missing/non-finite inputs, negative bearings, constrained success/empty/failure,
the exact implausible-detour boundary, shortest-alternative admission, one
unconstrained fallback, route-order retention, terminal failure and rejection
of premature, duplicate or late outcomes. Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_routing_orchestration_fixture.dart
dart run tools/kotlin_rewrite/generate_routing_orchestration_fixture.dart --check
```

`routing_orchestration_protocol_test.dart` and
`RoutingOrchestrationProtocolParityTest.kt` consume the shared transcripts.
Injected Dart executor tests additionally prove the exact bearing sequence,
single fallback, order retention and propagation of non-routing failures
without opening a socket.

`routing_avoidance_v1.tsv` contains 36 Dart-generated outcomes: 10 stateful
hard/soft/track transcripts and 26 sampling/re-timing cases. They cover direct
success/failure, the single soft fallback, premature/duplicate/late outcomes,
degenerate geometry, half-up sampling, the 120-waypoint cap, five-decimal
coordinates, exact ferry and road thresholds, wrong/null legs, zero arcs,
minimum distance slack, the 50% verification boundary and avoidance metadata.
Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_routing_avoidance_fixture.dart
dart run tools/kotlin_rewrite/generate_routing_avoidance_fixture.dart --check
```

`routing_avoidance_protocol_test.dart` and
`RoutingAvoidanceProtocolParityTest.kt` consume all 36 outcomes. Additional
Dart tests exercise the production async adapter and prove that non-routing
failures do not cause a hidden fallback; both runtimes independently verify
that accepted re-timing preserves geometry, maneuvers, speed limits, distance
and avoidance classification.

`routing_provider_config_v1.tsv` contains 34 Dart-generated outcomes covering
exact, unknown, case-mismatched and whitespace provider keys; both OSRM read
modes; GraphHopper server and cloud-key requirements; OpenRouteService keys;
null, empty and whitespace secure values; secure-key precedence; legacy
migration; stale server retention; and Dart Unicode/BOM trimming. Regenerate
or verify it with:

```text
dart run tools/kotlin_rewrite/generate_routing_provider_config_fixture.dart
dart run tools/kotlin_rewrite/generate_routing_provider_config_fixture.dart --check
```

`routing_provider_config_test.dart` and
`RoutingProviderConfigProtocolParityTest.kt` consume the shared contract.
Additional Dart adapter tests lock callback suppression and ordering, including
that a failed secure write never deletes the legacy value or continues to the
server read.

## Deliberately outside this slice

- the shared Kotlin HTTP engine now owns local-test sockets, connection pooling,
  total deadlines, redirect/retry refusal, response bounds and single-call
  cancellation; the routing service invokes it headlessly but not from
  production startup or UI;
- DNS, TLS and cleartext enforcement still need Android integration/device
  evidence;
- provider/key/server resolution and routing-time legacy-key migration are now
  fixture-locked and used by Flutter, but the native secure-store adapter,
  request-generation suppression and production native dispatch remain open;
- caller cancellation now stops routing orchestration and the physical request;
  long-lived job/ViewModel ownership and stale UI-generation suppression remain
  open;
- Valhalla avoidance execution and OSRM re-timing now have a cancellable
  headless coroutine owner; startup/ViewModel/UI ownership remains open;
- installed-app secure-storage/Keystore migration and live-provider/device
  tests remain release gates.

Rollback is removal of the headless service and its transport boundary while
retaining the Kotlin policies/fixtures and authoritative Flutter routing path.
No endpoint default, stored setting, API key or production network owner
changed in this increment.
