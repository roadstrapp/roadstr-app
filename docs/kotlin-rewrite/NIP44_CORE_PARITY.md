# NIP-44 v2 native crypto parity

This increment ports Roadstr's local NIP-44 v2 encryption primitive to Kotlin
without wiring it into Android startup, identity storage, favourites sync or
relay I/O. Flutter remains the production runtime. The native class is an
isolated candidate whose output is locked to the same committed fixture as the
production-used Dart implementation.

## Preserved Roadstr profile

The current app uses the original NIP-44 v2 payload profile:

- secp256k1 ECDH over the even-Y lift of a 32-byte x-only Nostr public key;
- the unhashed 32-byte shared x-coordinate;
- HKDF extract with HMAC-SHA256 and `nip44-v2`, followed by a 76-byte HKDF
  expand using the 32-byte message nonce as `info`;
- ChaCha20-IETF with counter zero, then HMAC-SHA256 over nonce plus ciphertext;
- version byte `0x02` and padded RFC 4648 Base64 output;
- a two-byte big-endian plaintext length, exact custom padding and a UTF-8
  plaintext range of 1 through 65,535 bytes.

The upstream NIP now documents an extended six-byte length prefix at 65,536
bytes. Roadstr's shipped Dart source and the historical official vectors used
by it reject that size. The native candidate deliberately preserves the
installed-app format; adopting the extended profile requires a separately
versioned compatibility decision rather than a silent rewrite change.

## Native implementation and dependency

`core.protocol.nostr.Nip44V2` provides random-nonce production encryption,
deterministic nonce injection for fixtures, decryption, conversation-key
derivation, message-key derivation and padded-length calculation.

The implementation pins `org.bouncycastle:bcprov-jdk18on:1.86`, published
under the permissive Bouncy Castle licence. It uses only Bouncy Castle's
lightweight secp256k1, SHA-256/HMAC and `ChaCha7539Engine` APIs. It does not
install or reorder a process-wide JCA provider, and the artifact has no
transitive dependencies. R8 can therefore retain only the reachable crypto
surface in a release build.

Private scalars are checked against the secp256k1 order. X-only public keys are
decoded and curve-validated before multiplication. Message and conversation
keys and nonces must be exactly 32 bytes. Decryption authenticates the complete
nonce/ciphertext pair before ChaCha20 output is interpreted, compares MACs in
constant time, requires exact padding, and uses a strict UTF-8 decoder.

## Shared fixture

`android/app/src/test/resources/parity/nip44_v2_v1.tsv` is generated and
checked by:

```text
dart run tools/kotlin_rewrite/generate_nip44_fixture.dart --check
```

Its 77 cases are curated from the upstream
[`nip44.vectors.json`](https://github.com/paulmillr/nip44/blob/main/nip44.vectors.json)
and replayed through Roadstr's production Dart primitive before being written.
They cover:

- seven official conversation keys, including scalar/order boundaries;
- all eight official invalid scalar, non-curve and twist-point cases;
- five official 76-byte HKDF expansions;
- 28 padding boundaries from one byte through 65,536 bytes;
- five exact deterministic payloads with ASCII and multilingual Unicode;
- the Base64 URL-safe compatibility accepted by Dart and rejection of missing
  required Base64 padding;
- all three official long-message hashes, including two 65,535-byte payloads;
- all official malformed version, Base64, MAC, padding and short-payload cases;
- empty/65,536-byte encryption rejection and wrong key/nonce lengths.

The native suite additionally proves that production encryption chooses fresh
nonces, round-trips through the public API and rejects a modified payload.
No fixture key is a user key or production secret.

## Compatibility and resource bounds

Kotlin accepts standard or URL-safe Base64 alphabet exactly as Dart does, but
requires the same four-character alignment and required `=` padding. It rejects
oversized encoded envelopes before allocating their decoded representation.
The ciphertext length is then bounded to the largest envelope that the shipped
u16 padding profile could ever accept.

The public Dart API was only factored into deterministic conversation-key and
nonce-aware boundaries. `Nip44.encrypt` still selects its nonce with
`Random.secure`, `Nip44.decrypt` still rejects malformed envelopes before key
work, and the NIP-78 caller and wire payload are unchanged.

## Explicitly outside this slice

- connecting `Nip44V2` to a native identity/key-isolation service;
- Amber/NIP-55 encryption intents and signer permission behavior;
- NIP-78 storage, passphrase encryption, relay sockets and wiring the
  separately fixture-locked BIP-340 core to signed events;
- NIP-47 encryption negotiation and native payment wiring; legacy NIP-04 is
  now separately fixture-locked in `NIP04_CORE_PARITY.md`;
- Android-device interoperability, heap/timing measurements and a clean
  minified release/F-Droid dependency audit.

No class in this increment is reachable from `MainActivity` or app startup.
Rollback removes the native class, dependency and fixture while leaving the
Flutter implementation and stored ciphertext unchanged.

## Verification

- the 77-row fixture is current under `--check`;
- `flutter analyze` reports no issues;
- all 588 Flutter tests and all 101 Kotlin tests pass;
- the debug Android APK passes D8, duplicate-class checks and packaging;
- Gradle dependency insight resolves only the direct pinned
  `bcprov-jdk18on:1.86` artifact.
