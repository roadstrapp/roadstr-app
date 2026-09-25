# Isolated legacy headless bridge

Status: implemented as an unwired candidate. JVM and Flutter tests are green;
execution against Android Keystore data from signed installed releases remains
a blocking M2 gate.

## Purpose and boundary

The native migration coordinator cannot safely reimplement undocumented Hive
and historical `flutter_secure_storage` formats from assumptions. The bridge
therefore runs the exact pinned Dart/Hive/plugin code in a temporary Flutter
engine and returns only the bounded `RSTRMIG1` envelope.

The normal Flutter `main()` behavior is unchanged.
`legacyMigrationHeadlessMain` is a separate `@pragma('vm:entry-point')`
function retained in the app snapshot, but no Activity, Application, manifest
entry or startup code calls it today.

## Versioned flow

1. A worker thread creates `LegacyHeadlessSnapshotReader` with a 30-second
   timeout. Main-thread reads fail before an engine is created.
2. `FlutterLegacyEnvelopeTransport` posts engine creation to Android's main
   looper and disables generated plugin registration.
3. Only the app plugins required by the reader are registered:
   `path_provider` and `flutter_secure_storage`.
4. Dart installs its method handler before sending `bridgeReady` with protocol
   version `1`.
5. Kotlin validates that handshake and sends one `readLegacySnapshot` request
   with the same version.
6. Dart resolves the app documents directory, reads secure storage with
   `resetOnError=false`, copies `settings.hive` to a temporary directory,
   opens only that copy and returns `Uint8List` or `null`.
7. Any Dart/plugin/transport failure becomes the fixed
   `legacy_snapshot_unavailable` boundary; details and source values are never
   transported.
8. Kotlin destroys the engine, accepts only `byte[]`/null, and decodes the
   bounded envelope. Duplicate callbacks are ignored.

The exact constants live in `legacy_bridge_protocol.tsv` and are asserted by
both Dart and Kotlin tests.

## Failure and lifecycle policy

- no request on Android's main thread;
- one request per engine and one accepted terminal callback;
- 64-MiB envelope cap on both sides;
- 30-second default and 120-second maximum reader timeout;
- engine/channel disposal before normal completion and a bounded disposal
  attempt after timeout;
- neutral exceptions only, with no plugin errors, paths, keys or values;
- no deletion API and no source Hive open;
- no startup integration until signed-install fixtures pass.

## Evidence and remaining gate

Flutter tests prove readiness ordering, shared protocol constants, exact
fixture bytes, null-only-for-absent semantics, one-shot behavior and error
redaction. Kotlin tests prove protocol parity, exact envelope decode,
not-needed/failure handling, malformed data, timeout cleanup, duplicate reply
suppression and main-thread rejection. Android compilation proves the pinned
FlutterEngine and plugin APIs match the adapter, and the generated debug kernel
contains the secondary entrypoint.

This does not prove Android runtime plugin registration, Keystore aliases,
historical secure-storage migration, process-death behavior, or memory/time
budgets. Those require controlled signed upgrade fixtures and remain release
blockers.
