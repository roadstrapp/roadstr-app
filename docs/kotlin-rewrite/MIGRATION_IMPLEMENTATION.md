# Migration prototype status

The first native rewrite increment is intentionally a library-level prototype,
not a production startup change.

## Implemented

- `LegacyStorageContract.kt` records the audited Hive/secure-storage names and
  dynamic per-identity key prefixes.
- `LegacyStorageModels.kt` defines a bounded normalized snapshot boundary,
  identity and asset records, schema validation, key separation, safe relative
  paths and checksum shape checks. Unknown keys, oversized values, duplicate
  assets and impossible protected identity states fail closed.
- `LegacyProtectedStatePolicy.kt` and its Dart counterpart reject
  non-canonical Hive keys, incomplete nsec/Amber states and one-way identity
  bindings before native staging. Protected values never appear in policy
  diagnostics; native BIP-340 derivation remains the final private/public
  consistency check.
- `storage/NativeSnapshotStore.kt` defines the v1 native public record and its
  bounded codec. Ordinary values, public identity and asset metadata are
  persisted with per-key secret commitments; raw secure values are handed only
  to the `NativeSecretStore` adapter. Privacy-sensitive `searchHistory` is
  excluded from this public record and handed to its encrypted store.
  `CompositeNativeSnapshotWriter` makes public, protected and history
  staging/commit/verification retryable while leaving the migration marker as
  the final step.
- `storage/FileNativePersistence.kt` provides the concrete bounded public-file
  store. It fsyncs a validated stage, retains the previous active file during
  same-directory replacement, reopens and validates the replacement, and can
  restore a valid backup after an interrupted or corrupt activation. Its
  durable marker contains only a fixed header and a SHA-256 commitment. Its v2
  form binds the exact public record and validated encrypted history file;
  completion also requires the reopened protected store to match every secret
  commitment. A missing key/file, changed value or undecodable record therefore
  makes migration incomplete without copying protected values into the marker.
- `storage/NativeSecretPersistence.kt` adds the bounded canonical protected
  payload and an authenticated AES-256-GCM file envelope. The production
  `AndroidKeystoreNativeSecretStore` creates or reopens a non-exportable key
  under `AndroidKeyStore`, uses a fresh 96-bit nonce and fixed associated data,
  zeroes transient plaintext byte arrays, and reuses stage/active/backup
  recovery. It exposes verification and commitment matching, never a raw-value
  read API.
- `storage/NativeSearchHistoryStore.kt` adds a separate canonical history
  payload encrypted with AES-256-GCM under
  `app.roadstr.native.search-history.v1`. It imports the normalized legacy list
  without opening Hive, preserves the 100-row read window, applies the existing
  five-row/deduplication policy to later writes, serializes concurrent
  operations and uses the same recoverable stage/active/backup replacement.
  Normal loads degrade damaged optional history to empty; mutations fail closed
  so unreadable ciphertext is not silently overwritten. Explicit transactional
  migration can repair it from the retained legacy source.
- `storage/NativeStoragePaths.kt` fixes the future native root at
  `context.noBackupFilesDir/roadstr-native-v1`, preserving state across an
  in-place update while keeping it outside backup/restore. `migration/
  NativeMigrationRuntime.kt` composes that path, public store, Keystore secret
  and search-history stores, commitment-bound marker and transactional
  coordinator. It is an explicit worker-thread API and is not invoked by the
  current Flutter startup.
- `migration/NativeMigrationStartupRunner.kt` adds the asynchronous
  single-flight boundary above the runtime. It rejects duplicate concurrent
  requests, reports neutral worker/execution failures, returns to `Idle` after
  a failed attempt for retry, and becomes terminal only after a non-failed
  result. It does not choose a UI or alter the current Activity lifecycle.
- `LegacySnapshotFingerprint` provides a stable order-independent SHA-256 for
  comparing staged and reopened snapshots without logging their contents.
- `LegacySnapshotEnvelope.kt` implements the versioned, canonical and bounded
  Flutter-to-Kotlin hand-off plus a read-only `LegacySnapshotReader` adapter.
  Its trailing SHA-256 detects transport corruption but is not treated as
  authentication.
- `legacy_snapshot_collector.dart` reads an already-open `settings` box,
  canonicalizes only supported Hive value shapes, accepts an injectable secure
  snapshot and builds a bounded asset manifest without following symlinks.
- `legacy_settings_hive_v1.b64` is a reproducible, genuinely encrypted Hive
  2.2.3 box containing synthetic values. The Dart collector turns it into the
  exact committed envelope consumed by Kotlin without mutating the box.
- `legacy_migration_bridge_reader.dart` distinguishes absent from partial
  legacy state, validates the canonical 32-byte Hive key, opens only a stable
  bounded temporary copy and assembles the full envelope. Wrong keys and Hive's
  follow-up asynchronous cleanup error are contained without exposing values.
- `legacy_secure_storage_source.dart` wraps the exact resolved plugin with
  `resetOnError=false` and backup-protected algorithm migration. Its real
  Android/Keystore behavior remains a signed-install test gate.
- `legacy_migration_headless.dart` exposes a preserved secondary Dart
  entrypoint and a versioned one-shot MethodChannel handler. Kotlin launches
  it in an isolated `FlutterEngine`, registers only the two required app
  plugins, enforces worker-thread use and a bounded timeout, destroys the
  engine, then decodes the envelope.
- `legacy_bridge_protocol.tsv` is asserted by both runtimes so channel,
  methods, version, entrypoint and neutral error code cannot drift silently.
  `LEGACY_HEADLESS_BRIDGE.md` records its threading, lifecycle and evidence
  boundary.
- The explicit voice manifest tracks 18 Kokoro/Piper/eSpeak paths and is locked
  to the production voice catalogues by tests.
- `TransactionalMigration.kt` defines the reader/writer/marker interfaces and
  enforces the order `read → validate → stage → commit → verify → mark complete`.
- No interface exposes a legacy-delete operation. Cleanup remains a separate,
  later policy decision.
- Unit tests cover success, idempotent completion, commit failure, verification
  failure, private/public key mismatch, unknown keys, unsafe/duplicate assets,
  incomplete protected identity, non-canonical Hive keys, deterministic
  fingerprints, native commitment redaction, native-store retry/corruption,
  public-store reopen, interrupted replacement rollback, durable-marker
  binding, protected-store reopen, nonce randomization, authenticated tamper
  rejection, lost-key retry, envelope limits/corruption and the audited key
  contract. Runtime tests cover stable path resolution, composition,
  idempotent second start and failed protected commitment reopening. Runner
  tests cover single-flight behavior, retry after marker failure and executor
  rejection without sleeping or using a device.
- Dart generates `legacy_snapshot_v1.b64`; Dart and Kotlin both decode the
  exact bytes and assert coverage of all 42 fixed Hive keys, three dynamic
  per-identity keys, nine secure keys and six synthetic asset records.
- The pure Kotlin core now covers geometry/progress, heading/off-route,
  camera/viewport, formatting/TTS, fuzzy search, refetch/retry,
  sunrise/sunset, opening hours and strict BOLT-11 parsing.
- `core_vectors.tsv` is read by both test runtimes. It includes a BOLT-11
  invoice generated by Dart and fixed solar outputs, so those checks are
  cross-language rather than parallel hand-written expectations.

## Deliberately not implemented yet

- No Kotlin parser reads legacy Android `SharedPreferences`, historical
  Keystore entries or Hive files; the isolated legacy reader deliberately
  delegates those formats to the exact Flutter plugins and Hive runtime.
- The headless launcher is not invoked by `MainActivity`, `Application` or any
  production startup path. Its Android/Keystore execution still requires
  controlled signed-install testing.
- The file-backed native marker/store is not connected to `MainActivity` or
  used by the production runtime.
- `NativeMigrationRuntime` is not invoked by `MainActivity`, `Application` or
  any production startup callback; its real `Context` factory is only a
  prepared integration boundary.
- `NativeMigrationStartupRunner` is likewise not owned by the current Activity
  lifecycle; startup scheduling, neutral recovery UI and cutover policy remain
  open.
- The existing Flutter Activity and production Flutter runtime remain the
  active path. A dormant, non-exported foreground service and an opt-in native
  navigation `MethodChannel` are registered. The isolated Dart wrapper and its
  disabled-by-default ownership coordinator have contract tests, but no
  current Dart production path invokes them.
- No real user data, key, NWC URI or voice asset is used by the tests.
- The protected-state admission policy does not yet parse historical NWC URI
  variants during migration; that decision remains behind the native Lightning
  boundary until signed-install fixtures establish the supported range.
- No raw installed-app fixture exists yet. The synthetic raw Hive fixture
  proves the current Dart binary read path, but not historical installed-box,
  secure-storage or Keystore compatibility.
- The current production app still constructs `FlutterSecureStorage()` with
  `resetOnError=true`; changing its startup behavior is outside this isolated
  bridge increment and needs a separately reviewed compatibility fix.
- The Keystore-backed `NativeSecretStore` compiles but is not wired to startup
  and cannot be executed by host JVM tests. Real AndroidKeyStore reopen,
  invalidation, filesystem-directory durability and power-loss behavior remain
  signed-device gates before startup integration.

The next migration increment must run a controlled reader against supported
installed 0.5.x states, including encrypted Hive, current and historical
secure-storage formats, nsec and Amber. Only after those fixtures prove the
exact legacy formats should the coordinator be connected to native startup.
Until then, the Flutter app remains the only production path.
