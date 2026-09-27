# Routing-provider request parity

This increment freezes Roadstr's outbound routing request contract without
giving Kotlin ownership of an HTTP engine. Flutter still dispatches every live
request and parses every provider response.

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

## Deliberately outside this slice

- no Kotlin HTTP engine, DNS resolver, connection pool or socket is wired;
- native timeout, cancellation, retry, redirect, TLS and cleartext enforcement
  still belongs to the future bounded-network adapter;
- OSRM, OpenRouteService, GraphHopper and Valhalla response parsing and provider
  fallback remain in Flutter;
- persisted provider/API-key migration and live-provider/device tests remain
  release gates.

Rollback is removal of the Kotlin boundary and fixture plus inlining the Dart
builders into `RoutingService`. No endpoint default, stored setting, API key or
live-network owner changed in this increment.
