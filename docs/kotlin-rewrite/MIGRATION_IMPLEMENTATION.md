# Migration prototype status

The first native rewrite increment is intentionally a library-level prototype,
not a production startup change.

## Implemented

- `LegacyStorageContract.kt` records the audited Hive/secure-storage names and
  dynamic per-identity key prefixes.
- `LegacyStorageModels.kt` defines a bounded normalized snapshot boundary,
  identity and asset records, schema validation, key separation, safe relative
  paths and checksum shape checks.
- `TransactionalMigration.kt` defines the reader/writer/marker interfaces and
  enforces the order `read → validate → stage → commit → verify → mark complete`.
- No interface exposes a legacy-delete operation. Cleanup remains a separate,
  later policy decision.
- Unit tests cover success, idempotent completion, commit failure, verification
  failure, private/public key mismatch, unsafe asset paths and the audited key
  contract.
- `GeoMath.kt`, `EncodedPolyline.kt` and `RouteProgress.kt` port the first
  deterministic pure-core slice, with tests for projection, polygon/bearing,
  truncation behavior and ordered route progress.

## Deliberately not implemented yet

- No native code reads the real Android `SharedPreferences`, Keystore or Hive
  files.
- No Flutter headless bridge is bundled yet.
- No native marker/store is connected to `MainActivity`.
- No production Activity, manifest, application ID, permissions or Flutter
  runtime has been replaced.
- No real user data, key, NWC URI or voice asset is used by the tests.
- No cross-language serialized fixture harness exists yet; the geometry tests
  are native unit tests aligned to the Dart source contract.

The next migration increment must provide a controlled fixture producer and a
reader adapter. Only after those fixtures prove the exact legacy formats should
the coordinator be connected to native startup. Until then, the Flutter app
remains the only production path.
