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

`lib/services/routing_request_protocol.dart` is called by the live Dart routing
service for OSRM, OpenRouteService, GraphHopper and Valhalla. Its Kotlin
counterpart freezes request composition, including provider profiles/locales,
API-key destination, waypoints, alternatives, bearings, retiming and avoidance
options. See `ROUTING_NETWORK_CORE_PARITY.md`.

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

## Deliberately outside this slice

- no native HTTP engine, OkHttp dependency, DNS resolver or socket is wired;
- no native request is dispatched with coordinates, API keys or a Roadstr
  user-agent; Kotlin request values are inert fixture-backed data;
- total deadlines, cancellation and connection-pool ownership still need the
  native adapter;
- redirect/TLS/cleartext behavior needs Android integration tests in addition
  to this policy fixture;
- Nominatim, Photon and Overpass response fixtures remain; OSRM, ORS,
  GraphHopper and Valhalla response fixtures remain to be ported;
- DNS-aware SSRF checks for LNURL remain part of the later live-network gate.

The Kotlin policy has no Android, Activity, startup or persistence wiring.
Rollback is removal of the Kotlin boundary and fixture plus restoration of the
small Dart delegation; no stored data or endpoint defaults change.
