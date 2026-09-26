# NIP-04 native crypto parity

This increment ports Roadstr's legacy NIP-04 encryption path to Kotlin without
wiring native sockets, signing, key storage or payment orchestration. Flutter
still owns live NWC payments, but `ZapService` now calls the extracted
production boundary in `lib/services/nip04.dart` instead of constructing
`nostr_tools.Nip04` directly.

NIP-04 is deprecated and unauthenticated. It remains necessary for NIP-47
wallets that do not advertise an `encryption` mode; future NWC negotiation
should prefer NIP-44 v2. This slice preserves the currently shipped NIP-04
path and does not claim to implement that negotiation.

## Preserved wire profile

Both runtimes implement:

- secp256k1 ECDH using the even-Y lift of a 32-byte x-only Nostr public key;
- the unhashed 32-byte X coordinate as the AES-256 key;
- AES-256-CBC with a fresh 16-byte IV and PKCS#7 padding;
- UTF-8 plaintext and the exact
  `<base64 ciphertext>?iv=<base64 IV>` envelope;
- the shipped Dart decoder's standard/URL-safe Base64 alphabets, `%3D`
  padding escapes and required four-character alignment;
- empty plaintext and a bounded maximum of 65,535 plaintext bytes.

The 65,535-byte ceiling bounds direct calls as well as relay-driven work. The
largest accepted ciphertext is 65,536 bytes and oversized encoded envelopes
are rejected before Base64 allocation. Private scalars are checked against the
secp256k1 order, x-only public keys are curve-validated, IVs must be exactly 16
bytes and ciphertext must be a non-empty multiple of the AES block size.

Decryption validates every PKCS#7 byte and uses strict UTF-8. This deliberately
rejects malformed input that the old adapter could sometimes truncate based
only on its final byte. It does not make NIP-04 authenticated: a modified or
wrong-key ciphertext can still be indistinguishable if it happens to produce
valid padding and UTF-8. Callers must continue to verify the signed Nostr event
and its NIP-47 bindings before decrypting.

## Native implementation and dependency

`core.protocol.nostr.Nip04Cipher` exposes random-IV production encryption,
deterministic IV injection, shared-secret derivation and decryption. It uses
the already pinned `org.bouncycastle:bcprov-jdk18on:1.86` lightweight
secp256k1 and AES/CBC primitives. It does not install or reorder a process-wide
JCA provider and introduces no additional artifact.

## Shared fixture

`android/app/src/test/resources/parity/nip04_v1.tsv` is generated and checked
with:

```text
dart run tools/kotlin_rewrite/generate_nip04_fixture.dart --check
```

Its 64 cases cover:

- eight valid ECDH shared secrets and ten invalid scalar/point encodings;
- twelve deterministic encryptions, including empty, block boundaries,
  Unicode and representative NWC request/response JSON;
- standard, URL-safe and `%3D`-escaped Base64 compatibility;
- malformed delimiters/Base64, missing padding, wrong IV/ciphertext lengths,
  corrupt PKCS#7, invalid UTF-8 and early oversized-envelope rejection;
- 1 KiB and two maximum-size payload hashes;
- short keys/IVs and ASCII/multibyte plaintext limit rejection.

Every valid deterministic payload is decrypted by the shipped
`nostr_tools 1.0.9` adapter during fixture generation. A random-IV payload
produced by that adapter is also decrypted by the new Roadstr Dart boundary
without contributing random bytes to the committed fixture. No fixture key is
a user key or production secret.

## Explicitly outside this slice

- NIP-47 info-event fetching and NIP-44/NIP-04 encryption negotiation;
- native BIP-340 signing/verification and Keystore-backed NWC secret storage;
- relay lifecycle, timeout/cancellation and complete native payment flow;
- live-wallet/device interoperability and downgrade-policy review.

No native class here is reachable from `MainActivity` or application startup.
Removing the native class and fixture rolls back this increment; the valid
wire format and stored NWC URI remain unchanged.
