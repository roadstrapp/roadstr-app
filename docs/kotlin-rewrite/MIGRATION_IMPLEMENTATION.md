# Migration prototype status

The native rewrite remains isolated from production startup. It now includes a
private Compose integration shell in addition to library-level prototypes, but
Flutter is still the only launcher and production UI of `app.roadstr`. A
separate `app.roadstr.roadtest` APK now launches that Compose shell directly
from a Flutter-free Gradle graph for side-by-side integration work.

This branch is a **pre-cutover implementation candidate**, not a Kotlin release
candidate. Repository-side migration/storage boundaries and deterministic
parity slices can be closed here; production cutover remains prohibited until
the signed-install, physical-device, UI, lifecycle, network and release gates
listed below are evidenced. This distinction prevents a high code-completion
estimate from being mistaken for release readiness.

## Implemented

- `native-android/` is an independent Android application module with its own
  settings/build graph, `ComponentActivity` launcher and `MAIN`/`LAUNCHER`
  manifest. It compiles the shared Kotlin `core/` and `feature/` trees plus only
  `service/location/` directly, applies no Flutter plugin and packages no Dart
  bundle, Flutter embedding, migration, storage or navigation-service source.
  Its distinct `app.roadstr.roadtest` identity
  prevents the incomplete harness from reading or replacing production data;
  the debug APK is roughly 60 MB and requests Internet plus foreground
  coarse/fine location (and AndroidX's package-scoped receiver permission), but
  never background location. It is not an update candidate.
- `NativeRoadTestLocationController.kt` owns one foreground-only AOSP
  `LocationManager` feed, permission/provider/retry states, a safe last-known
  seed and Activity start/stop cleanup. Its value-only snapshots drive the
  existing MapLibre cursor and follow-camera sessions without logging or
  persisting coordinates. Fused location and Google Play Services are absent.
- `feature/home/NativeCanaryActivity.kt` is a non-exported, recents-excluded
  activity with no intent filter. It hosts an edge-to-edge Compose shell and a
  MapLibre Native raster canary, opens no storage and does not invoke migration.
  The renderer can request only the admitted HTTPS OSM tiles if this private
  activity is explicitly invoked from inside the app; no production route
  reaches it.
  `core/ui/theme/RoadstrTheme.kt` reproduces the four current Flutter palettes
  and their historical stored-ordinal aliases. Manifest and Gradle contract
  tests lock the private boundary and `MainActivity` launcher ownership.
- `feature/map/NativeMapLibreHost.kt` embeds the strictly pinned BSD-2-Clause
  MapLibre Native OpenGL `13.5.2` artifact already selected by the Flutter
  plugin. It reuses the validated raster JSON and Flutter initial camera,
  retains texture mode for Compose overlays, and owns no location component.
  `NativeMapEngineCompatibility.kt` preserves the exact `maplibre`/`osm`
  values and implements the old renderer as a top-down zoom-6/2–19 profile
  over that same host, with an explicit admitted custom-tile input.
  `NativeMapLifecycle.kt` makes Activity callbacks, Compose disposal, restart
  and low-memory forwarding deterministic and idempotent.
  `NativeRouteOverlay.kt` and `NativeMapRouteRenderer.kt` add bounded,
  deterministic active/ZTL/completed GeoJSON and the matching MapLibre
  completed/halo/core layers plus muted route-choice candidates underneath and
  bright-red traffic runs above.
  `NativeTransitOverlay.kt`, `NativeTransitOverlaySession.kt` and
  `NativeMapTransitRenderer.kt` add a bounded revision-safe selected-itinerary
  source with Flutter's per-leg operator/accent/street colors, 7/4 logical
  widths and route-underlay ordering. `core/network/TransitProtocol.kt` and
  `service/transit/NativeTransitService.kt` now compose the exact Transitous
  request, parse the real Berlin fixture with bounded precision-aware geometry,
  and execute it through cancellable bounded OkHttp with classified retry. A
  parsed plan can be projected directly into the dormant transit session.
  `feature/transit/NativeTransitPresentation.kt` adds a revision-safe
  UI/map coordinator and Flutter-compatible card projection for duration,
  local clock, metric/imperial walking distance, boarding, line colors and
  timetable status. `NativeTransitItinerariesPanel.kt` packages the bounded
  Compose loading, ready, no-service and failure states with accessible
  selection and mode controls. Its 27 Android locale files are generated from
  the existing ARB values rather than maintained as a second translation source.
  `feature/search/NativeSearchPresentation.kt` adds the equivalent bounded,
  revision-safe UI boundary for ordinary and nearby place search, including
  partial/final outcomes, favorite/history rows and metric/imperial distances.
  `NativeSearchOverlay.kt` packages the Flutter search hierarchy with its exact
  11-category nearby order, GPS-disabled state, loading/empty feedback,
  keyboard action and explicit accessibility semantics. Nineteen search values
  are generated from every existing ARB file into a separate Android resource
  set with deterministic drift checks.
  `core/search/OsmPlaceDetailsProtocol.kt` ports the bounded OSM detail parser,
  including localized names/descriptions, safe contact values and contextual
  parking, charging, fuel, lodging and food fields.
  `feature/place/NativePlacePresentation.kt` adds a revision-fenced loading/
  ready/hidden session, bounded Wikipedia projection and localized opening-
  hours transitions. `NativePlaceDetailsPanel.kt` packages those values as an
  accessible bounded bottom sheet with typed side-effect-free callbacks. Its
  52 values are generated from all 27 ARB files with deterministic drift
  checks; no provider, image loader or intent launcher is owned by the sheet.
  `feature/navigation/NativeNavigationHudPresentation.kt` ports the bounded
  live/fallback navigation summary, all 21 manoeuvre families, five persisted
  speedometer styles and revision-safe monotonic step progression.
  `NativeManeuverSymbol.kt` draws those families as theme-aware vectors, while
  `NativeNavigationHud.kt` packages an adaptive portrait/landscape overlay
  with road/next-step cards, speed, limit, altitude, distance, duration, ETA,
  voice/settings/stop controls and explicit accessibility semantics. Its 13
  values are generated from all 27 ARB files; the slice owns no GPS, routing
  provider, settings store or external side effect.
  `feature/saved/NativeSavedPlacesPresentation.kt` adds bounded typed parsing
  for Flutter's favourite list-of-JSON-strings storage, direct import payloads,
  encrypted-envelope metadata and `parking_position`, together with exact
  label merge, the existing parking-marker projection and revision-safe add/
  edit/delete/import/parking state. `NativeSavedPlacesPanel.kt` packages the
  saved-place list plus add/import/export and parking navigate/remove callbacks
  with nine values generated from every ARB file. It owns no storage, picker,
  password crypto, sync, route or external intent adapter.
  `feature/wikipedia/NativeWikipediaPresentation.kt` mirrors the Flutter
  reader's exact HTTPS language-subdomain and `/wiki/` article admission plus
  main-frame-only navigation and revision-fenced progress/failure/retry state.
  `NativeWikipediaReader.kt` packages a dormant AOSP WebView with JavaScript,
  permissions, file/content access, mixed content and SSL continuation disabled,
  while clearing cookies/cache/history around its lifetime. Its four values are
  generated from all 27 ARB files; no place callback or browser intent is wired.
  `feature/report/NativeRoadEventPresentation.kt` adds bounded detail and report
  projection over the existing 14-category Nostr wire/MapLibre marker catalogue,
  including dual expiry, relative age, 500/200-character bounds, metric/imperial
  speed conversion, owner-only edit suggestions, bilingual privacy gating and
  revision-safe single-flight submission state. `NativeRoadEventPanels.kt`
  packages accessible detail/privacy/composer surfaces with typed confirmation,
  edit, profile, zap and submission callbacks. Its 39 resources cover all 27
  locales while preserving Flutter's English/Italian privacy-copy fallback; it
  owns no identity, privacy store, signer, relay, queue, GPS or wallet adapter.
  `feature/activity/NativeActivityInboxPresentation.kt` adds the bounded
  normalized-Hive list-of-maps codec, exact per-pubkey inbox/cursor names,
  newest-first 100-row dedupe/read-state behavior, monotonic cursor updates and
  revision-safe logged-out/empty/ready state. `NativeActivityInboxPanel.kt`
  packages the deliberately silent accessible inbox with 12 values generated
  from every ARB file; it owns no Hive, file, relay, socket, sound, banner or
  Android-notification adapter.
  `feature/route/NativeRoutePlanningPresentation.kt` adds revision-fenced
  planner/loading/alternatives/preview state, one-to-five stable ordered stops,
  four transport modes, Flutter-compatible duration/unit and avoidance badges,
  bounded route conditions and synchronized card/MapLibre selection.
  `NativeRoutePlanningPanel.kt` packages the dormant accessible planner and
  bottom sheets with 22 values generated from every ARB file; it owns no
  geocoder, router, GPS, weather, road-event, settings or navigation runtime.
  Style generations prevent stale reattachment;
  `NativeRouteOverlaySession.kt` adds a revision-safe StateFlow projector for
  normalized route geometry, bounded alternative selection/commit, monotonic
  progress, cursor interpolation, late ZTL refreshes and independently fenced
  traffic caches. `NativeRouteTrafficPolicy.kt` preserves Flutter's rounded-
  Vincenty 400 m segmentation and continuity boundaries with bounded work.
  `NativeMapCameraSession.kt` and
  `NativeMapCameraRenderer.kt` add a sequence-safe camera-follow boundary with
  the Flutter cadence, dead reckoning, navigation shift, bearing/pitch,
  recenter and gesture-detachment policies. `NativeMapCursorSession.kt` and
  `NativeMapCursorOverlayView.kt` add a monotonic user-position boundary and
  camera-reprojected painter matching the default Flutter MapLibre arrow.
  `NativeMapPointOverlaySession.kt` and `NativeMapPointOverlayView.kt` add a
  bounded revision-safe marker catalogue and projected billboard painter for
  road reports, cameras, parking, traffic lights, bumps and crossings.
  `NativeMapInteraction.kt` adds typed click/long-click values, projected
  road-event hit priority and Flutter's strict rounded-Vincenty 60 m route-
  alternative selection; product sheets and service caches remain detached. The
  private shell collects empty route, route-planning, transit, search, place, navigation,
  saved-place, Wikipedia, road-event, activity, camera, cursor and point-overlay state and packages all product panels in
  their hidden states. It deliberately does not invoke transit/search/place or
  navigation services, submit provider results, begin a query/place load or
  show/update the HUD or route/saved-place/activity panel, open Wikipedia or show/submit a road event; it has no product route, provider, storage,
  intent-launcher or GPS feed.
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
  to the `NativeSecretStore` adapter. Privacy-sensitive `searchHistory`,
  favorites/parking, dynamic activity inbox/cursor rows and signed pending
  reports are excluded from
  this public record and handed to their encrypted stores. The
  legacy Hive aliases `graphhopperApiKey`, `nwcUri` and `fav_sync_pass` are now
  likewise excluded and promoted to protected values only under Flutter's
  secure-value-absent/non-empty fallback rule.
  `CompositeNativeSnapshotWriter` makes public, protected, history,
  saved-place, activity and pending-report
  staging/commit/verification retryable while leaving the migration marker as
  the final step.
- `storage/FileNativePersistence.kt` provides the concrete bounded public-file
  store. It fsyncs a validated stage, retains the previous active file during
  same-directory replacement, reopens and validates the replacement, and can
  restore a valid backup after an interrupted or corrupt activation. Its
  durable marker contains only a fixed header and a SHA-256 commitment. Its
  v2/v3/v4/v5 forms bind the exact public record and validated encrypted
  history, saved-place, activity and pending-report files;
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
- `storage/NativeSavedPlacesStore.kt` extracts the normalized Flutter
  list-of-JSON-strings favorites and parking JSON from the public snapshot into
  a separately keyed AES-256-GCM file. It preserves the 1,000-row bounds,
  exact validation and label-merge policy, supports typed revision-fenced
  mutations and refuses runtime mutation before explicit initialization. Its
  validated ciphertext digest is part of migration marker v3, so missing,
  changed or wrong-key saved data prevents completion.
- `storage/NativeActivityStore.kt` extracts every per-identity inbox and relay
  cursor into one canonical integrity-framed AES-256-GCM file under the
  non-exportable `app.roadstr.native.activity.v1` alias. It preserves the exact
  100-row newest-first/dedupe/read policy, tolerant malformed-inbox migration
  and strict monotonic cursors, while typed runtime mutations require an
  existing migrated or explicitly initialized record. Its ciphertext digest
  extends migration marker v4, so missing, changed, corrupt or wrong-key
  activity state prevents completion.
- `storage/NativePendingReportStore.kt` extracts the Hive list-of-JSON-strings
  offline queue into a separately keyed AES-256-GCM atomic file. It preserves
  FIFO/TTL/verification/retry semantics, serializes concurrent enqueue/flush
  operations and writes the remaining retry set once per completed pass. Its
  validated ciphertext extends migration marker v5; wrong keys, corruption or
  mutation make completion false without exposing signed events publicly.
- `storage/NativePreferenceStore.kt` adds a deterministic, digest-bound and
  recoverable atomic file for the closed 35-key non-secret scalar catalogue.
  It strictly imports the public migration snapshot once, projects the exact
  Settings defaults/catalogues, accepts only typed Settings writes and fences
  stale callbacks per process. Unknown, compound, dynamic and protected keys
  cannot enter the file; every replacement is decoded and compared after
  reopen. It remains unconstructed by the launcher and private shell.
- `storage/NativeStoragePaths.kt` fixes the future native root at
  `context.noBackupFilesDir/roadstr-native-v1`, preserving state across an
  in-place update while keeping it outside backup/restore. `migration/
  NativeMigrationRuntime.kt` composes that path, public store, Keystore secret
  search-history, saved-place, activity and pending-report stores,
  commitment-bound marker and transactional
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
  to the production voice catalogues by tests. The dormant native catalogue
  additionally pins all 17 downloadable files by revision, exact byte length
  and SHA-256; its verifier reuses only exact regular files and its unowned
  downloader uses bounded `.part`/fsync/atomic replacement semantics.
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
  active path. The separately registered `NativeCanaryActivity` is private and
  dormant; it has no production navigation path. The separate Flutter-free
  road-test application now exercises an in-memory foreground GPS → route →
  HUD/overlay/camera path without changing that production boundary. A
  non-exported foreground
  service and an opt-in native
  navigation `MethodChannel` are registered. The channel exposes explicit
  start, stop and process-local running-state operations. The isolated Dart
  wrapper and its ownership coordinator have contract/reconciliation tests;
  both Flutter map renderers reconcile on init/resume, adopting an active
  service, restarting a missing one during navigation, or stopping an orphan.
  The service also publishes normalized fixes through a process-local,
  listener-isolated `EventChannel`; both renderers attach a typed shadow feed,
  but those fixes do not drive UI or navigation and are neither logged nor
  persisted. The same opt-in channel accepts bounded navigation-notification
  update/reset commands while the service is running. A replacement-safe
  process-local dispatcher applies the existing throttle/private metadata via
  Android `NotificationManager`, reusing `roadstr_navigation` and ID 42 so it
  updates the current slot rather than adding another notification. The Dart
  service waits for this shadow operation before invoking the established
  Flutter plugin, which therefore remains the final authority. This runs only
  in builds compiled with `ROADSTR_NATIVE_NAVIGATION=true`; the default is
  false, so ordinary builds keep the existing Flutter GPS and notification
  paths.
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
