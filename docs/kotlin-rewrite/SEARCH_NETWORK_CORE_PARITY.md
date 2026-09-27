# Search-provider request and response parity

This increment freezes the exact outbound request contract and deterministic
inbound normalization for Roadstr's three search data sources without giving
Kotlin ownership of a socket. Flutter still executes every live request.

## Production-used Dart boundary

`lib/services/search_provider_protocol.dart` now composes requests for:

- Nominatim forward search and reverse geocoding;
- Photon fuzzy/autocomplete search;
- Overpass interpreter POSTs on the two vetted worldwide mirrors.

`RoutingService`, `PhotonGeocoder` and `OverpassClient` call this boundary in
the shipped Dart path. The fixture generator therefore exercises the same
endpoint, header and encoding code that production uses rather than a parallel
test-only copy.

`lib/services/search_response_protocol.dart` is likewise called by
`RoutingService`, `PhotonGeocoder`, `OverpassClient` and `PoiSearchService`.
Its Kotlin counterpart, `core.network.SearchResponseProtocol.kt`, freezes the
same Nominatim forward/reverse, Photon GeoJSON and Overpass envelope/result
normalization while remaining detached from Android networking and UI.

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
- malformed Nominatim and Photon rows are skipped independently, while a
  malformed top-level payload keeps the existing empty/null outcome;
- remote labels, classes, types, brands and opening hours retain their current
  trimming, control-character and length limits;
- Overpass preserves node versus way-centre coordinates, name/brand/fallback
  precedence, category precedence and rounded Vincenty distance semantics.

## Shared fixtures

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

`search_responses_v1.tsv` adds 34 Dart-generated response outcomes:

- 7 Nominatim forward-search cases;
- 7 Nominatim reverse-geocode cases;
- 8 Photon cases;
- 8 normalized Overpass-result cases;
- 4 raw Overpass-envelope cases.

The cases cover malformed JSON and top-level shapes, independently malformed
rows, invalid coordinates and field types, bounded/control-character text,
node/way centres, name/brand/fallback precedence, distance rounding and a
53-pair Nominatim class/type category matrix. Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_search_responses_fixture.dart
dart run tools/kotlin_rewrite/generate_search_responses_fixture.dart --check
```

`search_response_protocol_test.dart` and
`SearchResponseProtocolParityTest.kt` consume the same fixture. Existing Dart
service tests remain the integration evidence that live clients call the
extracted production parser.

## Privacy and execution boundary

Kotlin can construct a value containing the same coarse coordinates and user
agent, but no Kotlin HTTP engine, DNS resolver, connection pool or socket is
wired. Request objects are inert. The future adapter must apply the separate
timeout, response-size, redirect, TLS and cleartext policy before dispatch.

No API key is represented in this fixture. Routing request composition and
response parsing are now covered separately by
`ROUTING_NETWORK_CORE_PARITY.md`; search provider fallback/cancellation,
ranking/history, Overpass backoff execution and Android network integration
remain later `KOTLIN-009` slices.

Rollback is removal of the Kotlin boundaries and fixtures plus inlining the
small Dart builders/parsers back into their callers. No persisted state,
endpoint, mirror order, request limit or live-network owner changed in this
increment.
