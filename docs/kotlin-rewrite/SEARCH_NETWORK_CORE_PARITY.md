# Search-provider request, response, ranking, orchestration and history parity

This increment freezes the exact outbound request contract and deterministic
inbound normalization/ranking for Roadstr's three search data sources. A
bounded Kotlin socket adapter now exists, but Flutter still executes every live
request and owns all provider orchestration.

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

`lib/services/search_ranking_protocol.dart` is called by the live
`PlaceSearchService` for query preparation, phase planning, geocoder deduping,
ranking, relaxed retry and POI-first merging. Its Kotlin counterpart,
`core.search.SearchRankingProtocol.kt`, consumes only normalized values and
does not schedule work or access storage/networking.

`lib/services/search_orchestration_protocol.dart` is now the production-used
state machine around those pure decisions. `PlaceSearchService` starts every
enabled initial provider concurrently, feeds completions in arrival order,
emits at most the first nonempty ranked partial, waits for the complete enabled
set, and either publishes one final merge or starts exactly one relaxed
Nominatim/Photon batch. A provider exception is represented as an empty
completion, and duplicate, disabled, premature and post-terminal completions
cannot alter the outcome. `core.search.SearchOrchestrationProtocol.kt` freezes
the same state transitions without owning futures, coroutines, sockets or UI.

`lib/services/search_history_protocol.dart` now owns tolerant history decoding,
newest-first coordinate deduplication, list limits and the persisted JSON value
shape used by both Flutter map implementations. Its Kotlin counterpart,
`core.search.SearchHistoryProtocol.kt`, is storage-free: it can read and write
the same logical values but is not wired to Hive or an Android store.

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

`search_ranking_v1.tsv` adds 53 Dart-generated policy outcomes:

- 7 provider/query execution plans;
- 6 relaxed-query and 6 direct match-score cases;
- 6 proximity-deduplication cases;
- 15 ranking and 3 cross-geocoder merge cases;
- 5 POI-first merge and 5 retry-admission cases.

The fixture locks the 200 UTF-16-unit query cap, typeahead versus settled
providers, optional location bias, 30 m rounded-Vincenty duplicate radius,
Nominatim-first duplicate retention, fuzzy confidence/brand/distance tiers,
trailing cities up to three words, the 10-result cap, one relaxed retry and
POI precedence. Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_search_ranking_fixture.dart
dart run tools/kotlin_rewrite/generate_search_ranking_fixture.dart --check
```

`search_ranking_protocol_test.dart` and
`SearchRankingProtocolParityTest.kt` consume the shared outcomes. Existing
`place_search_service_test.dart` cases prove that the production service and
its compatibility methods delegate without changing behavior.

`search_history_v1.tsv` adds 31 Dart-generated storage-policy outcomes:

- 14 tolerant decode and validation cases;
- 10 recency/deduplication cases;
- 7 serialization and storage-cap cases.

The fixture locks the existing `searchHistory` key, 300 UTF-16-unit label cap,
coordinate bounds, malformed-row skipping, 100 valid-row load ceiling,
`0.0001` per-axis duplicate window, newest-first ordering, five-row write cap,
Unicode/control escaping, negative zero and common decimal/exponent spellings.
Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_search_history_fixture.dart
dart run tools/kotlin_rewrite/generate_search_history_fixture.dart --check
```

`search_history_protocol_test.dart` and
`SearchHistoryProtocolParityTest.kt` consume the shared outcomes. Production
`MapScreen` and `MaplibreMapScreen` delegate their load/save/clear value policy
to the Dart boundary while retaining the same encrypted Hive box and key.

`search_orchestration_v1.tsv` adds 32 Dart-generated stateful transcripts:

- 19 phase, partial, ranking, merge and retry outcomes;
- all 6 settled three-provider initial-completion permutations, with both retry
  completion orders;
- 7 hostile duplicate, disabled, premature, invalid-batch and late-completion
  transcripts.

The fixture locks exact provider sets with and without location, one ranked
first-nonempty partial, waiting for all enabled providers, Nominatim-first
geocoder deduplication, POI-first final merge, retry admission/query/order and
terminal immutability. Regenerate or verify it with:

```text
dart run tools/kotlin_rewrite/generate_search_orchestration_fixture.dart
dart run tools/kotlin_rewrite/generate_search_orchestration_fixture.dart --check
```

`search_orchestration_protocol_test.dart` and
`SearchOrchestrationProtocolParityTest.kt` consume the shared transcripts.
Injected-provider `place_search_service_test.dart` cases additionally prove
that the live Dart owner starts providers concurrently, degrades synchronous
and asynchronous failures to empty results, launches one relaxed batch and
does not let a stale rendering callback fail the final search.

## Privacy and execution boundary

Kotlin can construct a value containing the same coarse coordinates and user
agent and can pass it to the shared bounded OkHttp adapter. Local tests prove
the timeout, response-size, redirect/retry and cancellation behavior, but no
production search service invokes it. Android DNS, TLS and cleartext behavior
still requires integration/device evidence.

No API key is represented in this fixture. Routing request composition and
response parsing are now covered separately by
`ROUTING_NETWORK_CORE_PARITY.md`; provider-job ownership, UI request-generation
suppression, the native history persistence adapter, Overpass backoff execution
and Android network integration remain later slices.

Rollback is removal of the Kotlin boundaries and fixtures plus inlining the
small Dart builders/parsers/policies back into their callers. The history JSON
shape, encrypted Hive owner/key, endpoints, mirror order, request limits and
live-network owner did not change in this increment.
