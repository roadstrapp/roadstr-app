# Protocol compatibility contract

The Kotlin port must preserve protocol bytes and semantics wherever the current
product is already interoperating with other Roadstr clients, Nostr relays,
signers and wallets. The Dart implementation remains the oracle until fixtures
are exported.

## Current protocol surface

| Protocol / kind | Current behavior | Oracle |
|---|---|---|
| NIP-01 | REQ/EVENT/EOSE/CLOSE, verified relay events, bounded frames, publish redundancy | `lib/services/nostr_relay_service.dart` |
| NIP-19 | strict 32-byte npub/nsec encoding and Amber key normalization | `nostr_nip19.dart`, profile/onboarding screens |
| NIP-04 | legacy NWC ECDH/AES-256-CBC payloads | `lib/services/nip04.dart`, `zap_service.dart` |
| NIP-44 v2 | encrypted favourites payloads, self-encryption and negotiated NWC | `lib/services/nip44.dart`, `favorites_sync_service.dart`, `zap_service.dart` |
| NIP-47 | verified info-event negotiation and NWC `pay_invoice` over NIP-44 v2 or legacy NIP-04 | `lightning_protocol.dart`, `zap_service.dart` |
| NIP-55 | Amber signer Intent flow, no private key for Amber | `amberflutter` calls in screens/services |
| NIP-57 | kind 9734 zap request and kind 9735 receipt validation | `lightning_protocol.dart`, `zap_service.dart` |
| NIP-78 | kind 30078 replaceable encrypted favourites snapshot | `favorites_sync_service.dart` |
| Roadstr 1315 | road report, category, coordinates, geohash, expiration/TTL | `nostr_relay_service.dart`, `road_event.dart` |
| Roadstr 1316 | confirmation/dismissal referencing a 1315 | `nostr_relay_service.dart` |
| Roadstr 1317 | owner-signed correction/update | `nostr_relay_service.dart` |
| Roadstr 1318 | third-party correction request | `nostr_relay_service.dart` |

NIP-04 must remain as the backward-compatible NWC path because an absent
NIP-47 `encryption` tag in a verified info event means NIP-04. Current
negotiation prefers NIP-44 v2 when advertised, rejects an authentic unsupported
mode, and fails closed when info discovery is missing or forged so relay
suppression cannot silently force a downgrade.
NIP-44's current padding and favourites-sync envelope are also compatibility
boundaries.

## Required golden fixtures

The following fixtures must be exported from the current Dart implementation
with fixed clocks, keys and randomness where applicable, then consumed by Kotlin
tests:

- canonical event serialization, event IDs and signature inputs;
- x-only public-key derivation and BIP-340 signing/verification (43-case
  official/Roadstr shared fixture green);
- kind 1315 tags/content for every road-event category, including expiration
  and geohash levels;
- kind 1316 confirmation/dismissal;
- kind 1317 owner correction and kind 1318 request/response semantics;
- profile-visibility replaceable event;
- kind 30078 favourites payload, hashed and legacy `d` tags, timestamp bump and
  optional passphrase envelope;
- NIP-44 official vector, self-encryption and padding boundaries;
- NIP-19 npub/nsec encodings (shared Dart/Kotlin fixture green);
- NIP-57 kind 9734 request and receipt binding rules;
- NWC/NIP-47 info negotiation, request and response validation (89-case
  fixture green);
- NIP-04 ECDH, AES-CBC ciphertext, Base64 and hostile envelopes (64-case
  shared fixture green against `nostr_tools 1.0.9`);
- offline queue JSON representation, expiration and retry behavior;
- geohash subscriptions, relay filters and relay-limit rejection cases.

Current evidence includes an official NIP-44 vector test in
`test/nip44_test.dart`, road-event validation tests, favourites privacy tests
and pending-report tests. BOLT-11 has a native strict parser and shared fixture.
Nostr now has a first shared deterministic-core fixture covering canonical
serialization/IDs, all Roadstr 1315 categories, kinds 1316-1318, profile
visibility and the main area/confirmation relay frames. A second shared fixture
locks the pending-report Hive JSON plus FIFO, TTL, validation and retry
transcripts in both runtimes. A third fixture locks bounded inbound decoding
for NIP-01/NIP-42 envelope types, structural failures, UTF-16 size limits and
nesting limits. A fourth fixture locks strict fixed-size NIP-19 `npub`/`nsec`
encoding, decoding and malformed-input rejection against `nostr_tools`. A
fifth Nostr fixture locks stateful subscription/kind routing, collision
precedence and pre-verification event budgets for every production relay loop,
including favourites, Lightning-address, NWC-response and receipt queries.
The 89-case Lightning fixture additionally locks NWC URI/command/info-event/
negotiation/wire/response semantics and NIP-57 draft/receipt bindings,
including lazy verifier ordering and downgrade rejection.
The 43-case Schnorr fixture locks every official BIP-340 vector, six signed
Roadstr event shapes and strict malformed/tampered Nostr inputs. The 77-case
LNURL fixture locks `lud16`/`lud06` resolution, HTTPS admission,
metadata validation, callback construction and invoice amount/expiry/
description binding. The 72-case NIP-78 fixture locks relay normalization,
hashed/legacy tags, favourites/passphrase JSON, padding and limits, timestamps,
snapshot/cleanup drafts, filters, event admission, relay selection and
anti-rollback. The 77-case NIP-44 fixture locks native ECDH validation, HKDF,
ChaCha20/HMAC payloads, Unicode, Base64, exact padding, maximum sizes and all
official hostile vectors against production Dart. The 64-case NIP-04 fixture
locks the legacy NWC ECDH/AES-CBC boundary, valid Base64 compatibility and
bounded malformed-input rejection against the shipped Dart adapter.
Native signer side-channel approval/key isolation, NIP-47/NIP-44 service
wiring, queue/favourites storage, DNS/HTTP execution, socket lifecycle and
signature-aware dispatch remain outstanding;
see `NOSTR_CORE_PARITY.md`, `SCHNORR_CORE_PARITY.md`,
`NIP04_CORE_PARITY.md`, `NIP44_CORE_PARITY.md`, `LIGHTNING_CORE_PARITY.md` and
`FAVORITES_SYNC_CORE_PARITY.md`.

## Network semantics that must not drift

Port semantically, not approximately:

- relay list, publish redundancy, WebSocket handshake timeout, reconnect
  backoff, subscription filters and duplicate/dedupe behavior;
- inbound message/event count and size caps, TTL pruning, and an explicitly
  reviewed pending-queue cap (the current Dart queue is unbounded);
- exact HTTP endpoints, headers/user-agent, bounded bodies, redirects, timeout,
  cancellation, retry and cleartext loopback policy;
- geohash precision and filter composition;
- Nostr signature verification before trusting remote metadata, zaps or route
  event corrections;
- Nominatim etiquette, Overpass mirror rotation/backoff and route provider
  fallback behavior.

The deterministic subset of that HTTP contract is now locked by the 48-case
`http_safety_policy_v1.tsv`: timeout and response tiers, redirect refusal,
declared/streamed size ceilings and current GraphHopper loopback admission.
The 39-case `search_provider_requests_v1.tsv` additionally locks exact
Nominatim forward/reverse, Photon and Overpass methods, URLs, encodings,
coordinates, headers and form bodies. `NETWORK_CORE_PARITY.md` and
`SEARCH_NETWORK_CORE_PARITY.md` record the remaining live-network gates.
The 89-case `routing_requests_v1.tsv` locks OSRM, OpenRouteService,
GraphHopper and Valhalla methods, URLs, profiles/locales, coordinates,
headers/bodies, API-key boundaries, waypoint/alternative/bearing rules,
retiming and avoidance policies. The 59-case `routing_responses_v1.tsv` locks
their normalized geometry, localized instructions, maneuver/decorative fields,
speed limits, multi-leg/polyline6 behavior, validation/errors and retiming.
`ROUTING_NETWORK_CORE_PARITY.md` records the remaining orchestration and native-
execution gates.

## Acceptance criteria

For deterministic inputs, Kotlin must match required protocol bytes/fields and
must reject the same invalid or hostile inputs. If a bug is intentionally fixed,
the old fixture, reason and explicit approval must be recorded before the user-
visible change is accepted. Randomized encryption is compared by successful
cross-implementation decrypt/validation and protocol constraints, not by
expecting equal ciphertext.
