# Lightning deterministic core parity

This increment extracts the deterministic NIP-47 and NIP-57 behavior around
Roadstr's existing crypto, signature, HTTP and WebSocket adapters. Flutter
remains the production runtime, but `ZapService` now delegates these decisions
to `lib/services/lightning_protocol.dart`; Kotlin mirrors them in
`core.protocol.lightning.LightningProtocol.kt`.

## Production-used boundary

The shared core covers:

- strict `nostr+walletconnect` URI parsing, duplicate `secret`/`relay`
  rejection, the shipped fallback relay and redacted diagnostics;
- exact `pay_invoice` command JSON;
- kind-23194 request fields and kind-23195 subscription filter order;
- wallet-author, request-`e` and client-`p` response bindings;
- the shipped distinction between a malformed response that is ignored and a
  valid protocol failure that completes payment with no preimage;
- payment-preimage binding to the BOLT-11 payment hash;
- exact kind-9734 zap-request tags and the 21-million-BTC millisatoshi ceiling;
- kind-9735 receipt tag cardinality, invoice description/preimage binding,
  signed zap-request kind, amount, recipient and optional road-event binding.

Signature checks remain injected gates. Receipt kind and expected signer are
checked before the receipt verifier is invoked; invoice bindings and request
kind are checked before the embedded request verifier is invoked. This keeps
hostile cheap failures from forcing unnecessary Schnorr work.

## Shared fixture

`android/app/src/test/resources/parity/lightning_protocol_v1.tsv` is generated
by:

```text
dart run tools/kotlin_rewrite/generate_lightning_protocol_fixture.dart --check
```

Its 69 cases cover accepted and rejected NWC URIs, JSON escaping and Unicode,
request/filter wire layouts, response-event binding, success/failure/ignore
response states, zap draft limits, profile and road-event receipts, duplicate
security tags, malformed tag types, bad invoices, description/preimage/amount/
recipient/event mismatches and lazy verifier call counts. The committed NWC
secret and preimage are deterministic synthetic test values, never user data.

## Explicitly outside this slice

- NIP-04 encryption/decryption vectors and a vetted native crypto adapter;
- native secp256k1/BIP-340 signing and verification;
- LNURL HTTP/DNS/redirect handling and live wallet interoperability;
- WebSocket lifecycle, timeout/cancellation and native payment orchestration;
- Amber/NIP-55 Activity Result behavior and Keystore-backed NWC persistence.

No native class here is wired to `MainActivity`, application startup or stored
state. Flutter keeps owning live payments until the remaining adapters and
signed-device matrix are complete.
