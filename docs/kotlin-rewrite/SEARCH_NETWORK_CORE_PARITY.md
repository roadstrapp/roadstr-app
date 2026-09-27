# Search-provider request parity

This increment freezes the exact outbound request contract for Roadstr's three
search data sources without giving Kotlin ownership of a socket. Flutter still
executes every live request.

## Production-used Dart boundary

`lib/services/search_provider_protocol.dart` now composes requests for:

- Nominatim forward search and reverse geocoding;
- Photon fuzzy/autocomplete search;
- Overpass interpreter POSTs on the two vetted worldwide mirrors.

`RoutingService`, `PhotonGeocoder` and `OverpassClient` call this boundary in
the shipped Dart path. The fixture generator therefore exercises the same
endpoint, header and encoding code that production uses rather than a parallel
test-only copy.

The extraction preserves these provider-specific details:

- Nominatim query text uses RFC-2396 component encoding, sends six results,
  address details, extra tags and no polygon geometry;
- Nominatim's optional viewbox remains a soft `bounded=0` bias of ±0.25°;
- reverse geocoding preserves Dart's coordinate rendering and requests address
  details plus extra tags;
- Photon uses HTML-form query encoding, allows only `en`, `de` and `fr`, keeps
  the 200 UTF-16-unit query cap and rounds location bias to two decimals;
- Photon location bias remains soft (`location_bias_scale=0.3`, `zoom=12`);
- Overpass sends `application/x-www-form-urlencoded`, the navigation-app user
  agent and a `data=` body using form encoding;
- the Overpass mirror list excludes the Switzerland-only `overpass.osm.ch`.

## Shared fixture

`search_provider_requests_v1.tsv` contains 39 Dart-generated request outcomes:

- 11 Nominatim forward-search cases;
- 6 Nominatim reverse-geocode cases;
- 15 Photon cases;
- 7 Overpass POST cases.

The cases cover empty and maximum-length input, ASCII and Unicode whitespace,
UTF-8 text, the different punctuation/space encodings, normal/extreme/tiny and
negative-zero coordinates, viewboxes, Photon rounding, supported and omitted
languages, both mirrors, multiline Overpass QL and exact headers/body bytes.

Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_search_provider_requests_fixture.dart
dart run tools/kotlin_rewrite/generate_search_provider_requests_fixture.dart --check
```

`search_provider_protocol_test.dart` locks the Dart oracle and
`SearchProviderProtocolParityTest.kt` reconstructs and compares all 39 outcomes
in Kotlin. The existing local-server Overpass tests continue to prove that the
production client actually transmits the extracted form body and interprets
status/results as before.

## Privacy and execution boundary

Kotlin can construct a value containing the same coarse coordinates and user
agent, but no Kotlin HTTP engine, DNS resolver, connection pool or socket is
wired. Request objects are inert. The future adapter must apply the separate
timeout, response-size, redirect, TLS and cleartext policy before dispatch.

No API key is represented in this fixture. Routing request composition and
response parsing are now covered separately by
`ROUTING_NETWORK_CORE_PARITY.md`; search response parsing, provider fallback/
cancellation, Overpass backoff execution and Android network integration remain
later `KOTLIN-009` slices.

Rollback is removal of the Kotlin boundary and fixture plus inlining the small
Dart builders back into their three callers. No persisted state, endpoint,
mirror order, request limit or live-network owner changed in this increment.
