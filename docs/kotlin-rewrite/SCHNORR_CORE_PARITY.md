# Nostr BIP-340 native core parity

This increment ports x-only secp256k1 public-key derivation and BIP-340
signing/verification to the isolated Kotlin core. It also extracts a strict
Dart Nostr boundary around the same `bip340 0.2.0` implementation already
shipped through `nostr_tools 1.0.9`.

The production Dart inbound-event gate now calls this boundary after
recomputing the canonical NIP-01 event ID. Native relay sockets, key storage,
Amber and event dispatch remain unwired.

## Nostr profile

`NostrSchnorr` in both runtimes provides:

- validation of a 32-byte private scalar in the inclusive `1..n-1` range;
- derivation of the 32-byte x-only secp256k1 public key;
- BIP-340 tagged hashes for `aux`, `nonce` and `challenge`;
- signing of an exact 32-byte NIP-01 event ID with fresh 32-byte auxiliary
  randomness;
- deterministic auxiliary-randomness injection for fixture replay only;
- verification of a 64-byte signature against a 32-byte x-only key and event
  ID;
- complete-event signing that rejects a private key which does not match the
  draft pubkey, and verification that recomputes the canonical event ID first;
- non-throwing rejection of malformed untrusted verification input.

The Kotlin implementation also exposes byte-message methods solely to replay
the official BIP-340 vectors added after arbitrary message lengths were
standardized. Roadstr production callers must use the 32-byte Nostr methods.

## Native implementation and dependency

`core.protocol.nostr.NostrSchnorr` implements the BIP-340 composition over the
already pinned `org.bouncycastle:bcprov-jdk18on:1.86` secp256k1 point
primitives. It validates `r < p`, `s < n`, lifts x-only keys to their even-Y
point and rejects infinity, odd-Y nonce points and wrong X coordinates. It
does not register a process-wide JCA provider or add another artifact.

This is a detached parity candidate, not approval to expose native private
keys. Bouncy Castle/`BigInteger` execution has not been accepted as a
constant-time Android signing boundary. Before native local-nsec signing is
wired, the implementation needs a side-channel review or replacement with a
reviewed constant-time backend, plus Keystore/key-isolation and physical-device
evidence. Verification handles only public data and can be wired separately
after the relay lifecycle is implemented.

## Shared fixture

`android/app/src/test/resources/parity/nostr_schnorr_v1.tsv` is generated and
checked with:

```text
dart run tools/kotlin_rewrite/generate_nostr_schnorr_fixture.dart --check
```

Its 43 rows contain:

- all 19 official `bitcoin/bips` BIP-340 vectors, including invalid curve
  points, odd-Y/infinite nonce results, `r >= p`, `s >= n` and messages of
  0, 1, 17, 32 and 100 bytes;
- six deterministic signed NIP-01 events representative of Roadstr reports,
  votes, updates, metadata, NWC and NIP-78 favourites;
- five invalid private-scalar/encoding cases;
- four invalid strict signing-input cases;
- nine strict Nostr verification cases covering valid uppercase hex,
  tampering, malformed lengths and non-hex input.

Generation cross-checks every official vector and every Roadstr signature
against the Dart implementation shipped by `nostr_tools`. Fixture keys and
auxiliary values are synthetic and must never be reused as user keys.

The upstream BIP-340 vectors are offered by the Bitcoin BIPs project under
BSD-2-Clause, MIT or CC0-1.0; their source URL and retrieval date are recorded
beside the embedded generator data.

## Explicitly outside this slice

- native secure generation, storage, migration or use of identity/NWC keys;
- acceptance of the current JVM implementation as a constant-time signer;
- Amber/NIP-55 intents, permissions, cancellation and account selection;
- signature-aware relay dispatch, WebSocket lifecycle and publish ACKs;
- NIP-47/NIP-57/NIP-78 end-to-end signing orchestration;
- live-relay interoperability, device timing measurements and minified-release
  audit.

No Kotlin class in this increment is reachable from `MainActivity`, the
manifest or application startup. Removing the native class and fixture rolls
back the slice without changing stored state or wire formats.

## Verification

- the 43-row fixture is current under `--check`;
- `flutter analyze` reports no issues;
- all 588 Flutter tests and all 101 Kotlin tests pass;
- the debug APK passes D8, duplicate-class checks, native builds and packaging;
- no dependency, Activity, manifest, startup or stored-data format changed.
