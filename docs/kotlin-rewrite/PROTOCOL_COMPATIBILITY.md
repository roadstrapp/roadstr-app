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
| NIP-44 v2 | encrypted favourites payloads and self-encryption | `lib/services/nip44.dart`, `favorites_sync_service.dart` |
| NIP-47 | NWC `pay_invoice` request/response using NIP-04 | `lightning_protocol.dart`, `zap_service.dart` |
| NIP-55 | Amber signer Intent flow, no private key for Amber | `amberflutter` calls in screens/services |
| NIP-57 | kind 9734 zap request and kind 9735 receipt validation | `lightning_protocol.dart`, `zap_service.dart` |
| NIP-78 | kind 30078 replaceable encrypted favourites snapshot | `favorites_sync_service.dart` |
| Roadstr 1315 | road report, category, coordinates, geohash, expiration/TTL | `nostr_relay_service.dart`, `road_event.dart` |
| Roadstr 1316 | confirmation/dismissal referencing a 1315 | `nostr_relay_service.dart` |
| Roadstr 1317 | owner-signed correction/update | `nostr_relay_service.dart` |
| Roadstr 1318 | third-party correction request | `nostr_relay_service.dart` |

NIP-04 must remain in the NWC path because wallet compatibility depends on it;
it must not be casually replaced by NIP-44. NIP-44's current padding and
favourites-sync envelope are also compatibility boundaries.

## Required golden fixtures

The following fixtures must be exported from the current Dart implementation
with fixed clocks, keys and randomness where applicable, then consumed by Kotlin
tests:

- canonical event serialization, event IDs and signature inputs;
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
- NWC/NIP-47 request, NIP-04 ciphertext and response validation;
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
The 69-case Lightning fixture additionally locks NWC URI/command/wire/response
semantics and NIP-57 draft/receipt bindings, including lazy verifier ordering.
The 77-case LNURL fixture locks `lud16`/`lud06` resolution, HTTPS admission,
metadata validation, callback construction and invoice amount/expiry/
description binding. Native NIP-04, Schnorr/key derivation, queue storage,
DNS/HTTP execution, socket lifecycle and signature-aware dispatch wiring are
still outstanding; see
`NOSTR_CORE_PARITY.md` and `LIGHTNING_CORE_PARITY.md`.

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

## Acceptance criteria

For deterministic inputs, Kotlin must match required protocol bytes/fields and
must reject the same invalid or hostile inputs. If a bug is intentionally fixed,
the old fixture, reason and explicit approval must be recorded before the user-
visible change is accepted. Randomized encryption is compared by successful
cross-implementation decrypt/validation and protocol constraints, not by
expecting equal ciphertext.
