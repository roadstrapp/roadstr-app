# Nostr deterministic core parity

This increment ports the byte-deterministic part of Roadstr's Nostr protocol,
its pure offline-report flush policy, its bounded relay-envelope decoder, its
stateful relay-ingress policy, its fixed-size NIP-19 key representation and
its deterministic NIP-78 favourites policy. It now also contains isolated
native NIP-04/NIP-44 and BIP-340 candidates, without introducing a native
WebSocket, private-key storage path, storage adapter or startup wiring. Flutter
remains the production runtime; its extracted Schnorr boundary still delegates
to the BIP-340 implementation shipped by `nostr_tools`.

## Implemented boundary

`lib/services/nostr_protocol_codec.dart` is now the shared Dart construction
boundary for:

- the NIP-01 canonical array `[0, pubkey, created_at, kind, tags, content]`;
- UTF-8 SHA-256 event IDs;
- exact kind-1315 report tags, including six-decimal coordinates, g4/g5/g6,
  category, expiration and optional `maxspeed`;
- kind-1316 votes, kind-1317 owner updates and kind-1318 edit requests;
- kind-30078 profile visibility;
- Roadstr's area and confirmation `REQ`, publish `EVENT` and `CLOSE` frames.

The production Dart service uses this boundary for local-nsec drafts,
Amber unsigned maps, event-ID verification and the area/confirmation wire
messages. Local signing itself still goes through `nostr_tools`.

`core.protocol.nostr.NostrProtocol.kt` implements the same deterministic
surface with only JDK/Kotlin primitives. Its JSON writer intentionally accepts
only the value types used by NIP-01 here; in particular it rejects floating
point JSON values and non-string object keys instead of silently choosing a
different representation.

`lib/services/nostr_pending_report_queue.dart` now isolates the production
Hive representation and one queue-flush pass from sockets/storage. The
production service delegates to it without changing behavior. Kotlin's
`NostrPendingReportQueue.kt` mirrors the same FIFO rules: capture one clock,
drop entries whose expiration is less than or equal to it, verify before
publish, retain transport failures in order and commit the resulting queue
only after the whole pass.

`lib/services/nostr_relay_message.dart` is now the production structural
boundary for inbound relay frames. `NostrRelayService` uses it for its live
stream, publication ACKs and all one-shot report/profile fetches. It rejects
non-string frames, frames above 256 Ki UTF-16 code units, malformed envelopes
and JSON nesting deeper than 64 containers before any signature work. It
classifies NIP-01 `EVENT`, `EOSE`, `OK`, `NOTICE`, `CLOSED` and NIP-42 `AUTH`;
subscription/kind authorization and Schnorr verification remain in the
service and still happen before event effects.

`NostrRelayMessage.kt` implements the same boundary with an internal bounded
JSON parser, so the pure Kotlin core gains no serialization dependency.

`lib/services/nostr_relay_ingress.dart` is now the production admission gate
between structural relay decoding and signature verification. Live road,
confirmation and zap streams plus one-shot user-history, edit-request,
visibility, profile, favourites, Lightning-address, NWC-response and
zap-receipt fetches use it to reject unknown subscription/kind pairs and
enforce the shipped independent event budgets before paying the canonical-
hash/Schnorr cost. Favourites publication ACKs also use the shared structural
decoder. A `verify` verdict only permits cryptographic verification; it never
marks relay data trusted. Ordered rules preserve the shipped precedence even
if opaque random subscription ids collide.

`NostrRelayIngress.kt` mirrors the same state machine and accepts integral JSON
`Long` kinds emitted by the native bounded parser. It remains detached from a
native socket and must be owned by one serialized stream when that adapter is
implemented.

`lib/services/nostr_nip19.dart` is now the production boundary for the
fixed-size NIP-19 values Roadstr actually uses: 32-byte `npub` public keys and
32-byte `nsec` private keys. Profile, onboarding, Amber signer calls and road
event UI all use its typed encode/decode methods. It accepts canonical lower-
case or whole-string upper-case Bech32, rejects mixed case, invalid checksums,
non-canonical padding, unsupported human-readable prefixes and payloads that
are not exactly 32 bytes. Decode errors never include the source value, because
even an invalid `nsec` must be treated as secret material.

`NostrNip19.kt` implements the same strict boundary without a Bech32
dependency. The codec itself remains representation-only; derivation and
signatures are handled separately and neither component stores identity data.

`lib/services/favorites_sync_protocol.dart` is now the production-used pure
boundary for NIP-78 relay normalization, per-user/legacy tags, favourites JSON,
passphrase envelopes, byte padding, monotonic timestamps, snapshot/cleanup
drafts, fetch filters, event binding, newest selection and anti-rollback.
`FavoritesSyncProtocol.kt` mirrors those decisions without crypto, storage or
socket dependencies. See `FAVORITES_SYNC_CORE_PARITY.md` for the detailed
scope.

`lib/services/nip44.dart` now exposes deterministic nonce and
conversation-key boundaries while preserving its production random-nonce API.
`Nip44V2.kt` mirrors the shipped u16 NIP-44 v2 profile with Bouncy Castle's
lightweight secp256k1 and ChaCha7539 primitives. It validates private scalars
and x-only curve points, authenticates before decrypting, strictly decodes
UTF-8 and remains detached from key storage and NIP-78 orchestration. See
`NIP44_CORE_PARITY.md` for the crypto/dependency boundary.

`lib/services/nip04.dart` is now the production legacy NWC encryption boundary
used by `ZapService` after negotiation selects NIP-04, preserving
interoperability with `nostr_tools 1.0.9` while adding explicit size, IV,
block, PKCS#7 and UTF-8 checks. `Nip04Cipher.kt`
mirrors its x-only secp256k1 ECDH and AES-256-CBC wire format with Bouncy
Castle lightweight primitives. It remains detached from NWC keys, signing and
sockets. See `NIP04_CORE_PARITY.md` for the legacy-security limitations.

`lib/services/nostr_schnorr.dart` is now the strict production Dart boundary
for x-only derivation and NIP-01 hash signing/verification. The inbound event
gate recomputes the canonical ID before calling it and malformed untrusted
verification input returns false. `NostrSchnorr.kt` reproduces BIP-340 over the
already pinned Bouncy Castle secp256k1 primitives, including all official
vectors. The Kotlin implementation is detached and is not approved for
private-key use until side-channel and key-isolation gates are complete. See
`SCHNORR_CORE_PARITY.md`.

## Shared fixture

`android/app/src/test/resources/parity/nostr_protocol_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nostr_protocol_fixture.dart`. Generation fails
if the extracted Dart codec disagrees with `nostr_tools` about any event ID.
Use:

```text
dart run tools/kotlin_rewrite/generate_nostr_protocol_fixture.dart --check
```

The 43-line fixture covers:

- all 14 category wire keys and TTL values;
- one kind-1315 event per category, across positive/negative coordinates,
  negative zero, six-decimal rounding and geographic edges;
- both kind-1316 statuses;
- kind-1317 with and without a linked request;
- kind-1318 and private/public kind-30078 values;
- quotes, backslashes, control characters, Unicode, emoji and U+2028/U+2029;
- exact event IDs and canonical preimages;
- complete publish, area request, confirmation request and close frames.

Both runtimes consume that committed file. Dart additionally proves that the
public `NostrRelayService` Amber builders produce the fixture values, so the
vectors are not detached test-only examples.

`android/app/src/test/resources/parity/nostr_pending_queue_v1.tsv` is generated
by `tools/kotlin_rewrite/generate_nostr_queue_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nostr_queue_fixture.dart --check
```

Its six flush transcripts cover empty/mixed queues, the TTL boundary, invalid
events, successful publication, false/exceptional transport outcomes, FIFO
retention and a post-2038 clock. A storage row locks the exact Hive JSON string
for a complete signed kind-1315 event. Both Dart and Kotlin consume the same
file.

`nostr_relay_messages_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nostr_relay_message_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nostr_relay_message_fixture.dart --check
```

Its 25 cases cover a complete signed kind-1315 `EVENT`, every supported relay
envelope, missing/extra/wrongly typed fields, unsupported and malformed input,
JSON escapes, Unicode/emoji, non-string input, exact and over-limit 256 Ki
frames, and nesting exactly at/over the 64-container ceiling. Large cases are
stored as deterministic construction recipes rather than bloating the fixture.

`nostr_nip19_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nostr_nip19_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nostr_nip19_fixture.dart --check
```

Its 35 cases cover exact encodings for four synthetic 32-byte values in both
`npub` and `nsec`, upper-case input normalization, typed decoding, wrong type,
case/checksum/prefix/length/padding failures and malformed hex. Valid vectors
are cross-checked against `nostr_tools`; all private values are test-only.

`nostr_ingress_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nostr_ingress_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nostr_ingress_fixture.dart --check
```

Its 63 stateful steps cover every live and one-shot route, unknown
subscriptions, missing or wrongly typed kinds, exact/over-budget behavior,
independent report/vote/favourites/Lightning/NWC-info/receipt budgets, the
intentionally unbounded shipped NWC response stream, limit-before-kind
precedence, defensive configuration and deliberate subscription-id
collisions. Expected outcomes
are declared independently in the generator before both runtimes replay the
transcript.

`favorites_sync_protocol_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_favorites_sync_protocol_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_favorites_sync_protocol_fixture.dart --check
```

Its 72 cases lock relay admission, hashed and legacy tags, favourites and
passphrase JSON, padding/size limits, hour-rounded timestamps, kind-30078 and
kind-5 drafts, the exact fetch filter, lazy signature admission, relay winner
selection and anti-rollback in both runtimes.

`nip44_v2_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nip44_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nip44_fixture.dart --check
```

Its 77 cases replay a curated official NIP-44 set through production Dart and
Kotlin: valid/invalid secp256k1 ECDH, HKDF keys, exact payloads, Unicode,
Base64 forms, padding boundaries, maximum-size hashes, MAC corruption and
malformed envelopes.

`nip04_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nip04_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nip04_fixture.dart --check
```

Its 64 cases replay production Dart and `nostr_tools 1.0.9` through Kotlin:
valid/invalid ECDH, exact AES-CBC payloads, NWC JSON, Unicode, Base64 variants,
size boundaries, malformed envelopes, padding corruption and invalid UTF-8.

`nostr_schnorr_v1.tsv` is generated by
`tools/kotlin_rewrite/generate_nostr_schnorr_fixture.dart`. Use:

```text
dart run tools/kotlin_rewrite/generate_nostr_schnorr_fixture.dart --check
```

Its 43 cases replay all 19 official BIP-340 vectors plus six representative
Roadstr events and strict scalar/hash/auxiliary/signature rejection cases.
Generation cross-checks Dart `nostr_tools`; Kotlin reproduces derivation,
deterministic signatures and all valid/hostile verification outcomes.

## Safety properties

- Drafts and filter builders copy caller-owned tag/id/geohash collections.
- Kotlin hashes UTF-8 bytes and emits lowercase two-digit SHA-256 hex.
- Kotlin preserves Dart's negative-zero coordinate spelling.
- Update/edit speed limits retain the current inclusive 5..300 boundary.
- Dart verification still performs both canonical-ID comparison and BIP-340
  signature verification through the extracted strict boundary, and rejects
  content, ID or signature tampering.
- Expiration is evaluated before verification/publication, failed publishes
  retain their original order and malformed persisted strings are isolated.
- A crash before the one final queue write can republish the same immutable
  Nostr event ID, but cannot lose an unpublished entry through a partial write.
- Frame size/depth and envelope shape are rejected before event-ID/Schnorr
  verification; the new depth cap hardens hostile input without excluding any
  shipped Nostr event shape.
- NIP-19 decoding is type-checked at call sites and rejects malformed key
  lengths before identity or signer state is changed; errors do not echo input.
- Ingress budgets count every event on a recognized subscription before kind
  or signature work; unknown subscriptions consume no budget, and reaching a
  ceiling cannot authorize an event effect.
- NIP-78 snapshots are author/kind/`d`-tag bound before signature work,
  plaintext/event limits are enforced before expensive crypto, and stale valid
  snapshots are rejected against the persisted timestamp high-water mark.
- NIP-44 verifies HMAC in constant time before interpreting plaintext, rejects
  malformed curve points and bounds envelopes to the shipped u16 profile.
- NIP-04 validates every PKCS#7 byte and strict UTF-8 after event admission,
  but remains unauthenticated by protocol design; signature and NIP-47 event
  bindings are still mandatory trust gates.

## Explicitly not implemented

This is not completion of KOTLIN-006. The following remain blocked behind
separate review and fixtures:

- side-channel review or a reviewed constant-time backend before native
  BIP-340 signing is allowed to handle real private keys;
- Amber/NIP-55 Intent behavior and key-isolation evidence;
- WebSocket lifecycle, relay rotation, reconnect jitter and publish ACKs;
- native signature-aware dispatch/socket wiring, broader parser fuzzing,
  and complete filter/transcript coverage beyond admission decisions;
- native parsing/storage for the persisted report list, process-death tests,
  queue serialization/capping and activity cursors;
- native NIP-47/NIP-57 crypto/socket/signature orchestration, and NIP-78
  NIP-44 key-storage/encryption wiring plus
  storage/network adapters. Deterministic Lightning, NIP-04, NIP-44 and
  favourites rules are covered separately in `LIGHTNING_CORE_PARITY.md`,
  `NIP04_CORE_PARITY.md`, `NIP44_CORE_PARITY.md`,
  `SCHNORR_CORE_PARITY.md` and `FAVORITES_SYNC_CORE_PARITY.md`.

The current Flutter product has neither a queue-size cap nor single-flight
protection around overlapping flushes. This increment records that inherited
risk rather than silently changing user-visible retry behavior.

The profile-metadata and Lightning-address fetches historically rely on their
kind-0 REQs and locally check author/signature but not kind. Their ingress
rules therefore use explicit fallback routes to preserve behavior; tightening
them to kind 0 remains a separately reviewed hardening change rather than an
undocumented parity drift.

No native class in this increment is reachable from `MainActivity`, the
manifest or app startup. Rollback is removal of the native package/fixtures and
restoring the small Dart service delegations; no stored data format changed.
