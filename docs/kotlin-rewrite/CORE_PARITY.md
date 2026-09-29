# Pure Kotlin core parity evidence

This phase keeps the default Flutter startup and UI cutover unchanged. It
establishes deterministic behavior, bounded native service adapters and dormant
native persistence; the registered native GPS channel is now reachable from a
compile-time, disabled-by-default canary in both Flutter map renderers, with
runtime-state reconciliation, a value-only native-fix shadow stream and an
in-place native navigation-notification mirror after Activity or renderer
recreation. A separate non-exported Compose shell is packaged for native UI
integration work, while Flutter remains the sole launcher.

## Implemented slices

| Kotlin package | Flutter oracle | Covered behavior |
|---|---|---|
| `core.geo` | `geo.dart`, `polyline.dart`, `route_progress.dart` | local distance/projection, bearing, polygons, encoded geometry, ordered progress |
| `core.navigation` | `heading_filter.dart`, `off_route_detector.dart` | motion hysteresis, stale motion, reversal confirmation, route-snap veto, deviation trend |
| `core.map` | `camera_follow.dart`, `viewport_window.dart` | exponential easing, rotation cap, frame gate, navigation offset, marker culling window |
| `core.format` | `units.dart` | metric/imperial display, altitude, 27-language speech units, distance punctuation/spacing |
| `core.search` | `fuzzy_match.dart`, `search_ranking_protocol.dart`, `search_orchestration_protocol.dart`, `search_history_protocol.dart` | accent folding, bounded Levenshtein, address weighting, provider planning, proximity dedupe, city/brand/distance ranking, out-of-order provider completion, one partial, relaxed retry, POI-first final merge, history validation/recency/storage shape |
| `core.network` | `retry.dart`, `refetch_policy.dart`, `bounded_http.dart`, `network_config.dart`, `search_provider_protocol.dart`, `search_response_protocol.dart`, `routing_request_protocol.dart`, `routing_response_protocol.dart`, `routing_orchestration_protocol.dart`, `routing_avoidance_protocol.dart`, `routing_provider_config.dart`, GraphHopper validation | failure/status classes, bounded Retry-After, exponential schedule, movement/age refresh, HTTP deadlines/body ceilings/redirect and cleartext-loopback policy, exact search requests and normalized responses plus routing-provider requests/responses, one-retry bearing fallback, avoidance fallback, per-leg re-timing and persisted provider/key/server resolution |
| `service.network` | `bounded_http.dart` | shared OkHttp pool, exact GET/POST adaptation, whole-call deadline, declared/streamed body caps, redirect/retry refusal, value-free failures and physical coroutine cancellation |
| `service.routing` | `routing_service.dart` | resolved OSRM/ORS/GraphHopper dispatch, provider-specific deadlines, 32 MiB journey-route bound, normalized responses, value-free failures, cancellable one-retry bearing fallback, Valhalla hard/soft/track avoidance and best-effort OSRM re-timing |
| `service.location` | `gps_service.dart` | AOSP `LocationManager` source, 500 ms sampling boundary, safe fix normalization, last-known fix, 20/45-second dead-stream watchdog and cancellable lifecycle ownership; both Flutter renderers have a disabled-by-default foreground canary with init/resume reconciliation and an observed native-fix shadow stream, while full startup/UI cutover remains |
| `service.navigation` | map-screen lifecycle/navigation state | 30-second background grace, generation-safe pause/resume, GPS retention during navigation, detach cleanup, foreground-only wakelock policy, AOSP foreground-service adapter, opt-in `MainActivity` method/event bridges, process-local running-state query and fix fan-out, isolated Dart wrappers, serialized ownership coordinator and disabled-by-default start/stop/dispose/reconcile wiring in both renderers |
| `service.notifications` | `navigation_notification_service.dart` | 3-second distance-only throttle, immediate maneuver changes, reset semantics and private ongoing notification metadata; the disabled-by-default canary now mirrors bounded updates through the service `NotificationManager` adapter using the existing channel/ID, with Flutter completing last and remaining authoritative |
| `feature.map` | `maplibre_map_screen.dart`, `map_screen.dart`, map services/widgets | raster MapLibre style JSON, dark recoloring, tile URL admission/escaping, ZTL route-run segmentation and zoom/viewport marker culling; a lifecycle-safe MapLibre Native 13.5.2 AndroidView now installs bounded selected/completed/muted-alternative route layers and consumes revision-safe route-choice, camera-follow and projected user-cursor StateFlow sessions only in the private Compose shell, while product data/UI remain unwired |
| `core.ui` / `feature.home` | `app_theme.dart`, Flutter Android embedding | exact four-palette colors and stored ordinal aliases, edge-to-edge private Compose/MapLibre canary, non-exported/no-intent-filter shell contract; no production startup or storage access |
| `storage` | encrypted Hive search history | canonical encrypted history file, serialized prepend/clear/import, atomic recovery, Keystore boundary and migration-marker ciphertext binding |
| `core.time` | `sun_calc.dart`, `opening_hours.dart` | NOAA rise/set and conservative common OSM opening-hours subset |
| `core.protocol.lightning` | `bolt11_invoice.dart`, `lightning_protocol.dart`, `lnurl_protocol.dart`, `zap_service.dart` | BOLT-11 parsing, LNURL-pay source/metadata/callback/invoice binding, NIP-47 URI/info negotiation/request/response and NIP-57 draft/receipt bindings |
| `core.protocol.nostr` | `nostr_protocol_codec.dart`, `nostr_pending_report_queue.dart`, `nostr_relay_message.dart`, `nostr_relay_ingress.dart`, `nostr_nip19.dart`, `nostr_schnorr.dart`, `nip04.dart`, `nip44.dart`, `favorites_sync_protocol.dart`, Nostr/favourites/Lightning services | canonical JSON/ID, Roadstr 1315-1318/profile tags, geohash, outbound frames, offline FIFO/TTL/retry policy, bounded inbound envelopes, pre-verification routing/budgets, strict NIP-19 keys, x-only derivation/BIP-340, legacy NIP-04, NIP-44 v2 and deterministic NIP-78 policy |

## Shared vectors

`android/app/src/test/resources/parity/core_vectors.tsv` is the first fixture
read by both runtimes. `test/native_core_parity_vectors_test.dart` locks it to
the Flutter oracle; `SharedCoreVectorsTest.kt` locks the Kotlin implementation
to the same values.

The set currently covers three solar locations/dates, Unicode normalization,
opening/closed/overnight/unknown opening-hours states, the reference encoded
polyline and a fixed BOLT-11 invoice generated by Dart. The invoice contains
test-only deterministic bytes; it is not a real wallet invoice or secret.

`nostr_protocol_v1.tsv` is a second shared core fixture. It is generated by the
production-used Dart codec, cross-checked against `nostr_tools`, and consumed by
the Kotlin Nostr suite. See `NOSTR_CORE_PARITY.md` for its exact scope and the
remaining signing/network gates.

`nostr_pending_queue_v1.tsv` is the third shared core fixture. It locks exact
signed-entry storage JSON and six deterministic queue transcripts across Dart
and Kotlin, including expiration boundaries and ordered retry retention.

`nostr_relay_messages_v1.tsv` is the fourth shared core fixture. Its 25
generated transcripts lock NIP-01/NIP-42 message classification, malformed
shape rejection, UTF-16 frame limits, a 64-container nesting cap and JSON
escape handling. Kotlin uses an internal bounded parser with no new dependency.

`nostr_nip19_v1.tsv` is the fifth shared core fixture. Its 35 cases lock exact
`npub`/`nsec` Bech32 encodings, upper-case normalization and strict rejection
of invalid case, checksum, prefix, padding, payload length and hex. The valid
vectors are cross-checked against `nostr_tools`; Kotlin uses a dependency-free
codec and neither runtime exposes candidate secret input in decode errors.

`nostr_ingress_v1.tsv` is the sixth shared core fixture. Its 63 sequential
decisions lock subscription/kind routing, unknown-input rejection, independent
budgets, exact ceiling behavior and collision precedence before signature
verification. The Dart policy is used by the production relay service and its
favourites, Lightning-address, NWC info/response and zap-receipt one-shot
loops; the Kotlin state machine is still detached from native sockets.

`lightning_protocol_v1.tsv` is the seventh shared core fixture. Its 89 cases
lock NWC URI parsing and secret redaction, kind-13194 discovery/signature/
capability negotiation, NIP-44 preference and downgrade rejection, exact
NIP-47 command/draft/filter layouts, response binding and outcome
classification, NIP-57 zap drafts and receipt cardinality/invoice/preimage/
amount/recipient/event bindings. See `LIGHTNING_CORE_PARITY.md` for the exact
boundary.

`lnurl_protocol_v1.tsv` is the eighth shared core fixture. Its 77 cases lock
Lightning-address and `lud06` resolution, lexical HTTPS/SSRF admission,
metadata shape and amount bounds, callback query construction and BOLT-11
amount/expiry/description binding. The Dart core is now called by production
`ZapService`; Kotlin remains detached from native networking.

`favorites_sync_protocol_v1.tsv` is the ninth shared core fixture. Its 72
cases lock custom-relay normalization, per-user and legacy `d` tags, favourites
JSON and passphrase envelopes, byte padding and limits, timestamp ordering,
snapshot/cleanup drafts, exact fetch filters, lazy event admission, newest
selection and anti-rollback. The Dart core is called by production
`FavoritesSyncService`; Kotlin remains detached from crypto, storage and
networking. See `FAVORITES_SYNC_CORE_PARITY.md` for the exact boundary.

`nip44_v2_v1.tsv` is the tenth shared core fixture. Its 77 official/curated
cases lock secp256k1 ECDH validation, HKDF message keys, ChaCha20/HMAC payloads,
Unicode, Base64 acceptance, exact padding, maximum messages and malformed-input
rejection. Kotlin uses pinned Bouncy Castle lightweight primitives and remains
detached from native key storage and favourites-sync wiring. See
`NIP44_CORE_PARITY.md` for the exact profile and dependency boundary.

`nip04_v1.tsv` is the eleventh shared core fixture. Its 64 cases lock the
legacy NWC ECDH/AES-256-CBC format, random-IV interoperability with
`nostr_tools 1.0.9`, Dart-compatible Base64 forms, UTF-8/PKCS#7 handling,
maximum messages and hostile-input rejection. Kotlin reuses the pinned Bouncy
Castle dependency and remains detached from native NWC key storage, signing
and sockets. See `NIP04_CORE_PARITY.md`.

`nostr_schnorr_v1.tsv` is the twelfth shared core fixture. Its 43 cases include
all 19 official BIP-340 vectors, deterministic signatures over six Roadstr
event shapes and strict malformed/tampered Nostr inputs. Kotlin reuses the
pinned Bouncy Castle curve primitives; Dart cross-checks the implementation
already shipped by `nostr_tools`. See `SCHNORR_CORE_PARITY.md`.

`http_safety_policy_v1.tsv` is the thirteenth shared core fixture. Its 51 cases
lock the six timeout tiers, seven response limits, redirect refusal, declared
and streamed exact-byte ceilings, and the shipped GraphHopper host/cleartext
decision. The live Dart bounded client calls the extracted policy; the 32 MiB
`journey_route` tier preserves its measured full-route ceiling separately from
the 2 MiB small route/probe tier. See
`NETWORK_CORE_PARITY.md`.

`search_provider_requests_v1.tsv` is the fourteenth shared core fixture. Its 39
cases lock exact Nominatim forward/reverse, Photon and Overpass methods, URLs,
encodings, parameters, mirror order, headers and form bodies. The corresponding
Dart builder is used by all three live Flutter clients; Kotlin remains detached
from production startup, while the headless native search service now dispatches
the same forward-search values. See `SEARCH_NETWORK_CORE_PARITY.md`.

`routing_requests_v1.tsv` is the fifteenth shared core fixture. Its 89 cases
lock exact OSRM, OpenRouteService, GraphHopper and Valhalla methods, endpoints,
profiles, locales, coordinates, headers, bodies, API-key boundaries, waypoint/
alternative/bearing rules and avoidance policies. The Dart builder is used by
the live Flutter routing service; the headless Kotlin routing service now uses
the same values for OSRM/ORS/GraphHopper dispatch. See
`ROUTING_NETWORK_CORE_PARITY.md`.

`routing_responses_v1.tsv` is the sixteenth shared core fixture. Its 59 cases
lock normalized OSRM, OpenRouteService, GraphHopper and Valhalla routes,
maneuver mappings and decorations, all 27 localized OSRM instruction sets,
speed limits, polyline6 decoding, multi-leg joining, response validation,
provider errors and OSRM retiming. The extracted Dart parser is used by the
live Flutter routing service; the headless Kotlin routing service now parses
primary-provider responses but remains detached from production wiring.

`search_responses_v1.tsv` is the seventeenth shared core fixture. Its 34 cases
lock Nominatim forward/reverse, Photon GeoJSON and Overpass envelope/result
normalization, including hostile payloads, bounded text, coordinate validation,
category/label mapping, node/way centres and distance rounding. The extracted
Dart parser is used by the live Flutter search clients; the Kotlin parser
is now exercised after bounded headless Nominatim, Photon and Overpass dispatch
but remains detached from production wiring. See
`SEARCH_NETWORK_CORE_PARITY.md`.

`search_ranking_v1.tsv` is the eighteenth shared core fixture. Its 53 cases
lock query/phase planning, matching, rounded-distance deduplication, provider
merge precedence, city/brand/distance ranking, result caps and relaxed-retry
admission. The Dart policy is called by production `PlaceSearchService`; the
Kotlin policy is now driven by the headless concurrent search service but
remains detached from storage and UI. See `SEARCH_NETWORK_CORE_PARITY.md`.

`search_history_v1.tsv` is the nineteenth shared core fixture. Its 31 cases
lock tolerant persisted-row decoding, coordinate and UTF-16 label validation,
the 100-row load ceiling, coordinate-window deduplication, newest-first order,
the five-row storage ceiling and exact common-coordinate JSON spellings. Both
Flutter map implementations call the extracted Dart policy; Kotlin remains
detached from an Android persistence backend. See
`SEARCH_NETWORK_CORE_PARITY.md`.

`search_orchestration_v1.tsv` is the twentieth shared core fixture. Its 32
stateful transcripts lock enabled-provider sets, every settled three-provider
completion order, first-nonempty partial delivery, provider failure fallback,
one relaxed Nominatim/Photon retry, terminal merge ordering and rejection of
duplicate, disabled, premature or late completions. Production Dart
`PlaceSearchService` calls the extracted state machine; Kotlin remains detached
from UI request generations, while `NativeSearchService` now drives it with
structured coroutines and bounded provider calls. See
`SEARCH_NETWORK_CORE_PARITY.md`.

`routing_orchestration_v1.tsv` is the twenty-first shared core fixture. Its 28
stateful transcripts lock provider/course admission, the exact implausible
detour boundary, shortest-alternative selection, one unconstrained OSRM retry,
route-order retention, terminal failures and hostile premature/duplicate/late
outcomes. Both Flutter map implementations call the extracted Dart executor;
the Kotlin service now drives this state machine around bounded provider calls,
including cancellation-without-fallback, but remains detached from UI request
generations. See `ROUTING_NETWORK_CORE_PARITY.md`.

`routing_avoidance_v1.tsv` is the twenty-second shared core fixture. Its 36
outcomes lock hard-to-soft and direct-track attempt ordering plus OSRM sampling,
ferry handling, per-leg distance admission, proportional fallback time and the
exact 50% road-coverage gate. Production Dart avoidance calls use the
extracted state machine and re-timing policy; Kotlin remains detached from
coroutines, sockets and UI request generations. See
`ROUTING_NETWORK_CORE_PARITY.md`.

`routing_provider_config_v1.tsv` is the twenty-third shared core fixture. Its
34 outcomes lock exact provider-key matching, GraphHopper server and API-key
requirements, OpenRouteService key requirements, OSRM fallback warnings,
Unicode trimming, secure-key precedence and one-shot legacy-key migration.
Both Flutter map implementations call the extracted Dart resolver while the
Kotlin routing service consumes an already resolved configuration but remains
detached from secure storage and startup. See
`ROUTING_NETWORK_CORE_PARITY.md`.

## Current limits

- No Kotlin class in this phase is called by production startup or Flutter UI.
  The extracted Dart request builders, response parsers and search-ranking
  policy are called by the existing Flutter services. The private Compose
  canary can only be invoked explicitly inside the app and is not a production
  route.
- Stateful navigation tests cover policy decisions, not Android sensor timing.
- General retry scheduling remains pure policy. The native routing service now
  owns one sequential OSRM bearing fallback state machine, and the native
  search service owns concurrent provider jobs plus one relaxed retry; callers
  still own long-lived jobs and UI request generations.
- HTTP size, endpoint, search/routing request and response decisions are Kotlin
  policy and local OkHttp integration tests cover connection, total deadlines
  and cancellation. Android DNS, TLS, Network Security Config and cleartext
  integration evidence remains open.
- Mid-navigation OSRM bearing fallback, avoidance attempt ordering and
  per-slice re-timing admission are covered, including both one-retry ceilings.
  Persisted provider/key/server resolution and routing-time legacy migration
  are covered. Headless native OSRM/ORS/GraphHopper execution and cancellation
  are green, as are Valhalla hard/soft/track avoidance execution and OSRM
  per-leg re-timing; secure-store reads, startup ownership and UI request
  generations remain open.
- Search provider planning, normalized-result ranking/merge, out-of-order
  completion/failure handling, one-partial/one-retry orchestration and
  deterministic history value semantics are covered. Headless Nominatim,
  Photon and category-Overpass execution, mirror fallback and cancellation are
  green. The dormant Android AES-GCM history backend and transactional legacy
  extraction are green; encrypted Hive still owns the production Flutter path.
  History UI ownership, cache storage, UI request-generation handling, startup
  ownership, real device-Keystore and network evidence remain open.
- NIP-47/NIP-57, LNURL-pay, legacy NIP-04, NIP-44 v2 and NIP-78 favourites
  deterministic wire, crypto, parsing, padding, binding and rollback rules are
  covered, including NIP-47 encryption negotiation; native signer/key
  isolation, LNURL DNS/HTTP/redirect execution, wallet/relay sockets,
  persistence and orchestration remain open.
- Further cross-language coverage for the remaining signed/networked Nostr
  surface is required. Legacy storage now has a
  synthetic encrypted Hive source fixture and shared envelope, while
  installed-app secure-storage/Keystore evidence is still required before its
  gate can close.
- The Kotlin queue policy is not wired to a native store or socket. The current
  Flutter queue is unbounded and does not serialize concurrent flush calls;
  changing either behavior requires an explicit, fixture-backed decision.
- The inbound decoder, ingress policy and BIP-340 verifier are detached native
  pieces: signature-aware dispatch, socket lifecycle and native-service wiring
  remain open. Every shipped Dart relay loop uses the shared structural decoder
  and every event-consuming loop uses the shared admission boundary.
- X-only public-key derivation and BIP-340 vectors are green, but native
  local-key signing remains blocked on side-channel review, key isolation and
  storage. Amber intents and identity migration are also open.
- The headless native AOSP GPS source and watchdog are green, and controlled
  builds of either renderer can own and reconcile the foreground service and
  consume its normalized fixes through a shadow `EventChannel`. The shadow
  feed is never persisted or logged and does not drive map/navigation state.
  Android permission UX, full cutover and de-Googled physical-device evidence
  remain open.
- The native map foundation now matches the Flutter raster style and overlay
  decisions headlessly. The private Compose shell now owns a real MapLibre
  Native `13.5.2` raster host with deterministic initial camera, texture mode,
  idempotent lifecycle/disposal and low-memory forwarding. Its first real
  overlay renderer installs bounded GeoJSON sources for active/restricted and
  completed routes plus muted unselected alternatives, with the Flutter color,
  halo, width and layer-order contract and generation-safe style replacement.
  A revision-safe native StateFlow session now projects normalized route
  geometry, bounded route-choice previews, stale-safe selection/commit,
  monotonic progress, interpolated cursor and late ZTL classifications into
  those sources. A
  separate value-only camera session matches the 33 ms follow cadence, three-
  second dead reckoning cap, 40/55-degree pitch policy, heading/north-up modes,
  navigation forward shift, 90-degree/second turn cap and gesture detachment;
  its sequence-safe renderer emits MapLibre move/ease commands. A separate
  revision-safe cursor session and transparent Android overlay reproduce the
  default 48x76 violet arrow, 1.4 display scale, pitch flattening and moving
  contact shadow, and reproject the point on every MapLibre camera move. The
  shell supplies empty route, camera and cursor feeds and has no location component or
  production route feed; live fix binding, remaining overlays, screenshots and
  physical-device evidence remain open.
- Native navigation lifecycle and notification policies are now deterministic,
  and the Android location foreground-service adapter is registered with an
  opt-in `MainActivity` start/stop/state/notification channel and
  permission/error contract.
  The Dart wrapper and ownership coordinator preserve idempotency and cleanup
  ordering, adopt an already-running service, restart a missing active service
  and stop an orphan on init/resume. A separate value-only `EventChannel`
  dispatches normalized fixes to a typed, error-isolated Dart shadow feed in
  both renderers. During canary navigation, bounded notification updates and
  reset commands are also mirrored to a replacement-safe service dispatcher and
  the native `NotificationManager`; the existing channel and ID update the
  same slot, and deterministic ordering leaves the Flutter plugin authoritative.
  These paths exist only when the compile-time canary is enabled, while
  ordinary builds remain inert and process-death/device evidence remains open.

## Verification for this increment

- `flutter analyze`: no issues.
- `flutter test`: 680 Flutter tests passed, including the native shell,
  private MapLibre host, route-overlay, route-session, camera-session and
  user-cursor Gradle/source contracts, the Nostr/`nostr_tools` and
  official BIP-340 cross-check, pending-queue, bounded-inbound, ingress,
  NIP-19, Lightning and
  LNURL/NIP-04/NIP-44/NIP-78 transcripts, HTTP safety, search/routing-provider
  request/response/configuration policies, search/routing/avoidance
  orchestration and re-timing, search planning/ranking/history, both migration
  fixture oracles, the headless Dart handler, the opt-in native-navigation
  channel contract and state query, native-fix event decoding/feed isolation,
  compile-time rollout, best-effort retry, serialized ownership lifecycle and
  service reconciliation, plus the bounded notification bridge and
  native-before-Flutter update/cancel ordering.
- `./gradlew :app:testDebugUnitTest`: 286 Kotlin tests passed, including the
  exact native theme palette/stored-ordinal contract, MapLibre version/camera
  baseline, imperative lifecycle/disposal/low-memory ordering, bounded route
  GeoJSON, Flutter-compatible route metrics/colors, revision-safe route
  route-choice selection/commit, projection/progress, stale-style rejection
  and revision-safe camera follow and user-cursor fencing/visual policy,
  dead reckoning, recenter and gesture-detachment behavior,
  and the
  Nostr byte/queue/inbound/ingress/NIP-19/BIP-340/NIP-04/NIP-44/NIP-78,
  Lightning/LNURL parity, HTTP safety/search/routing requests, responses,
  configuration, orchestration and re-timing, search planning/ranking/
  orchestration/history, native persistence/migration and bounded headless
  transport reader, encrypted/atomic search-history persistence and migration
  binding, native bounded-HTTP integration and headless routing/search service
  suites, including Valhalla avoidance and OSRM re-timing execution, plus the
  headless AOSP GPS normalization, last-known, watchdog and cancellation suite,
  plus the native map style, tile safety, route segmentation, marker-culling,
  lifecycle/grace-period, navigation-notification policy, foreground-service
  runtime state, fix encoding/fan-out, notification command dispatcher,
  bounded update parser and opt-in bridge contract suites.
- `flutter build apk --debug --dart-define=ROADSTR_NATIVE_NAVIGATION=true`: D8,
  duplicate-class checks and canary Android APK packaging passed with pinned
  Bouncy Castle, OkHttp and coroutines dependencies; the opt-in ownership path
  for both Flutter renderers compiled through the full build pipeline.
- `flutter build apk --debug`: the default Flutter-launcher APK packages the
  dormant Compose/MapLibre shell successfully without enabling the navigation
  canary or adding a second launcher.
- `MainActivity` registers the opt-in native-navigation channel. Both map
  renderers own it only in builds compiled with
  `ROADSTR_NATIVE_NAVIGATION=true`; ordinary builds retain the existing
  Flutter startup, active GPS and notification wiring.
- The merged manifest retains `app.roadstr.MainActivity` as the sole launcher;
  `NativeCanaryActivity` is non-exported, excluded from recents and has no
  intent filter. MapLibre Native resolves once at the strictly pinned `13.5.2`;
  resolved runtime dependencies contain no Google Play Services.
