# Routing-provider request, response and reroute-orchestration parity

This increment freezes Roadstr's outbound routing request contract and inbound
response normalization without giving Kotlin ownership of an HTTP engine.
Flutter still dispatches every live request.

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

## Deliberately outside this slice

- no Kotlin HTTP engine, DNS resolver, connection pool or socket is wired;
- native timeout, cancellation, retry, redirect, TLS and cleartext enforcement
  still belongs to the future bounded-network adapter;
- persisted provider/key resolution, avoidance hard/soft execution, OSRM
  retiming, request-generation suppression and live dispatch remain in Flutter;
- physical cancellation and coroutine ownership remain open for the native
  adapter even though reroute fallback admission is now fixture-locked;
- persisted provider/API-key migration and live-provider/device tests remain
  release gates.

Rollback is removal of the Kotlin boundaries/fixtures and re-inlining the Dart
request builders, response parser and two screen-local reroute policies. No
endpoint default, stored setting, API key or live-network owner changed in this
increment.
