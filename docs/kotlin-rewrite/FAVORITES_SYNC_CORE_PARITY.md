# NIP-78 favourites-sync deterministic core parity

This increment extracts Roadstr's deterministic favourites-sync policy around
the existing NIP-44, passphrase-encryption, signer, Hive and WebSocket adapters.
Flutter remains the production runtime, but `FavoritesSyncService` now calls
`lib/services/favorites_sync_protocol.dart`; Kotlin mirrors that boundary in
`core.protocol.nostr.FavoritesSyncProtocol` without startup wiring.

## Production-used boundary

The shared core covers:

- strict `wss` custom-relay normalization, including credential, query,
  fragment, length and built-in-relay rejection;
- the SHA-256 per-user `d` tag and legacy fixed tag used during migration;
- ordered favourites JSON, the optional passphrase envelope and finite
  coordinate values;
- UTF-8 padding to 4096-byte buckets and the NIP-44 65535-byte plaintext cap;
- hour-rounded, monotonically increasing `created_at` values;
- exact kind-30078 snapshot and legacy-wipe drafts plus the kind-5 NIP-09
  deletion draft;
- the exact one-result author/kind/`#d` fetch filter;
- content/author/kind/tag checks before invoking signature verification;
- newest-across-relays selection, first-event tie behavior and the persisted
  high-water anti-rollback decision.

The service still owns encryption, signing, relay I/O and Hive writes. The
extraction does not change its push serialization, hashed-tag-first/legacy-tag
fallback, one-time legacy cleanup, relay fan-out or pull decoding behavior.

## Shared fixture

`android/app/src/test/resources/parity/favorites_sync_protocol_v1.tsv` is
generated and checked by:

```text
dart run tools/kotlin_rewrite/generate_favorites_sync_protocol_fixture.dart --check
```

Its 72 cases cover 22 relay URLs, three per-user tags, nine byte-padding
boundaries, six timestamp cases, three snapshot/cleanup drafts, one fetch
request, twelve event-admission and lazy/failing-verifier cases, six
relay-selection cases, four rollback decisions, four favourites JSON payloads
and two passphrase envelopes. The admission cases include fractional-kind
rejection; the JSON cases include control/escape handling, an unpaired
surrogate, small-exponent coordinates and negative zero. Payloads and expected
results are base64url-packed JSON; all keys, locations and ciphertext-like
strings are fixed synthetic test data.

The Dart test regenerates the committed fixture through the production-used
core. The Kotlin test decodes every row with the bounded Nostr parser and
replays it through the native core, including exact JSON field order and event
IDs.

## Safety and compatibility properties

- Oversized plaintext is rejected before NIP-44 and oversized event content is
  rejected before canonical hashing or Schnorr verification.
- A signature callback is reached only after author, kind and `d` tag binding.
- A stale but validly signed snapshot cannot move a device below its persisted
  high-water timestamp.
- First-result tie behavior and the legacy fixed-tag cleanup are preserved
  rather than silently tightened during the port.
- URL checks are lexical only. Native socket wiring must still perform the
  reviewed DNS, timeout and lifecycle policy appropriate to WebSockets.
- The Kotlin JSON writer for favourite data accepts finite floating-point
  coordinates; the stricter NIP-01 canonical writer remains unchanged.

## Explicitly outside this slice

- wiring the separately fixture-locked native NIP-44 v2 core into identity and
  favourites-sync services; see `NIP44_CORE_PARITY.md`;
- PBKDF2/AES-GCM passphrase encryption and secure passphrase handling;
- wiring the separately fixture-locked BIP-340 core, key isolation and
  Amber/NIP-55 intents;
- WebSocket connection, relay ACK/EOSE, timeout and cancellation behavior;
- Hive high-water/cleanup state, local favourites storage and process-death
  behavior;
- end-to-end interoperability against live relays and signed Android devices.

No native class here is reachable from `MainActivity`, the manifest or app
startup. Flutter continues to own live favourites sync until these adapters and
the signed-upgrade matrix are complete.
