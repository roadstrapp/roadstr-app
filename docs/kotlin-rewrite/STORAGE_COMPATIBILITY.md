# Storage compatibility inventory

Status: forensic inventory, bounded transactional migration core, a
reproducible encrypted Hive 2.2.3 fixture, a strict read-only Dart collector
and a copy-before-open bridge plus isolated headless transport are implemented
and tested through the Dart-to-Kotlin envelope. Reading protected storage from
controlled signed installations remains open.

The existing app uses one Hive box named `settings` in the application
documents directory. `lib/main.dart` obtains a 32-byte key from
`FlutterSecureStorage` under `hive_settings_key`, base64-decodes it, and opens
Hive with `HiveAesCipher`. With Hive 2.2.3 this is a binary Hive frame format
using AES-CBC/PKCS7 at the box layer. The current startup also contains an
in-place plaintext-to-encrypted migration with a `.migration-backup` file.

The synthetic raw fixture confirms an important forensic boundary:
`HiveAesCipher` encrypts stored values, but logical key names remain visible in
the box bytes. The legacy file must therefore be treated as sensitive metadata
even when no value plaintext is recoverable without the key.

## Secure storage implementation observed

The resolved Android plugin is `flutter_secure_storage` 10.3.1. The app uses
the default `FlutterSecureStorage()` options, not the deprecated
`EncryptedSharedPreferences` option. Inspection of both its Dart options and
Android implementation found:

- Android `SharedPreferences` data name `FlutterSecureStorage`;
- default prefixed keys (the plugin's default prefix plus `_` and the logical
  key);
- wrapped AES keys in `FlutterSecureKeyStorage`;
- namespaced algorithm/config markers in
  `FlutterSecureStorageConfiguration:FlutterSecureStorage`, with fallback to
  the older global `FlutterSecureStorageConfiguration` preferences;
- AES-GCM value encryption with a random IV;
- an AES data key wrapped with RSA-OAEP/SHA-256 in an Android Keystore alias
  `app.roadstr.FlutterSecureStoragePluginKeyOAEP`; older custom-cipher data can
  use `app.roadstr.FlutterSecureStoragePluginKey` with RSA-PKCS1/AES-CBC;
- Dart's default `AndroidOptions` sends `resetOnError=true`,
  `migrateOnAlgorithmChange=true` and `migrateWithBackup=false`.

The last point is materially different from the Java config's fallback value:
on a decrypt/read failure, the default Dart configuration may delete protected
values and retry. The bridge candidate therefore sets `resetOnError=false`
explicitly and requests the plugin's backup-protected algorithm migration.
That candidate can still re-encrypt historical secure storage, so it is not
approved for startup until current and older signed-install fixtures prove its
rollback behavior.

Older installed releases or older plugin migrations may still contain the
plugin's prior EncryptedSharedPreferences/custom-cipher formats. This is why a
native parser must not be assumed safe from the current version alone.

## Required old locations and keys

| Old location / key | Old type / format | Sensitivity | Native target | Migration / verification |
|---|---|---|---|---|
| app documents `/settings.hive`, Hive box `settings` | Hive 2.2.3 frames; encrypted with 32-byte `HiveAesCipher` key | High: favourites, history, coordinates, preferences, queues | Versioned native store, with legacy reader/bridge retained | Read fixture, validate key/value types and critical counts; retain original until verified |
| secure storage `hive_settings_key` | base64 string containing 32 raw key bytes | Critical | Keystore-wrapped native key or compatibility access | Open old Hive with the exact key; never log or export |
| secure storage `nostr_priv_hex` | lowercase 64-char hex nsec | Critical | Keystore-backed encrypted secret | Derive pubkey and compare with old `nostr_pub_hex`; fail closed on mismatch |
| secure storage `nostr_pub_hex` | 64-char hex | Sensitive identity | Native identity record | Compare before/after migration |
| secure storage `nostr_flavor` | `amber` or `nsec` | Sensitive identity mode | Native identity record | Preserve mode; Amber must never gain a private key |
| secure storage `nostr_picture`, `nostr_name` | strings | Profile data | Native profile record | Exact value comparison |
| secure storage `routing_api_key` | string | Secret | Keystore-backed secret | Exact value comparison; never plain preferences |
| secure storage `nwc_uri` | `nostr+walletconnect://` string | Critical secret/config | Keystore-backed secret | Parse/validate and compare; do not print |
| secure storage `favorites_sync_passphrase` | string | Critical secret | Keystore-backed secret | Exact value comparison; fail closed |
| documents `kokoro/` | ONNX/tokenizer/voice files | Large asset, integrity-sensitive | Reuse in place | Verify current size/SHA-256 before reuse |
| documents `piper/` | ONNX/config files | Large asset, integrity-sensitive | Reuse in place | Verify current size/SHA-256 before reuse |
| documents `espeak-ng-data/` | extracted native voice data + sentinel | Runtime native asset | Reuse in place | Verify sentinel and expected contents |
| documents `kokoro_wav_cache/` and `kokoro_wav_session/` | generated WAV cache | Cache | Native cache directories | Reuse optional; never treat as source of truth |

## Hive logical keys

The following keys were found by inspecting all Hive reads/writes. Dynamic keys
are shown as templates.

```text
autoDark
autoCenterOnLaunch
avoidUnpavedRoads
disclaimer_accepted
fav_sync_custom_relay
fav_sync_last_ts
fav_sync_legacy_cleaned
fav_sync_pass                  # legacy secret, migrated to secure storage
favorites
favoritesSyncAutoEnabled
favoritesSyncLastAt
graphhopperApiKey              # legacy secret, migrated to secure storage
graphhopperServer
imperialUnits
keepScreenOn
keepScreenOnAlways
kokoroSpeedStage
kokoroVoiceGender
kokoroVolume
language
mapEngine
mapTileUrl
minBrightness
movementCursorColor
movementCursorStyle
nwcUri                          # legacy secret, migrated to secure storage
onboarding_v1
parking_position
pending_road_reports
privacy_disclosure_v2
road_report_privacy_ack
roadstr_profile_public
routingProvider
searchEngine
searchHistory
showAltitude
showCrosswalks
showTrafficLights
speedometerStyle
themeId
voiceEnabled
voice_unsupported_notice_shown
activity_inbox_<pubkey>
activity_zap_cursor_<pubkey>
activity_confirmation_cursor_<pubkey>
```

Values include Hive lists/maps, strings, booleans, integers and numbers.
`favorites`, `searchHistory` and `pending_road_reports` are Hive lists whose
items are JSON strings. Favourite JSON contains `label`, `address`, `lat` and
`lon`; search-history JSON contains `label`, `lat` and `lon`; pending-report
JSON contains a signed event and `expiresAt`. Activity inbox entries are Hive
maps rather than JSON strings.

## Migration policy

1. Detect an existing legacy Flutter installation and do not create a blank
   native profile when protected data is unavailable.
2. Prefer a minimal headless Flutter/Dart bridge that uses the exact legacy
   Hive and secure-storage code if deterministic native decoding is not proven.
3. Serialize a versioned, bounded migration envelope into native code. Do not
   pass secrets through logs or shell arguments.
4. Write native records transactionally to temporary/new storage, flush/fsync,
   reopen them, and compare critical values/counts.
5. Mark migration complete only after validation and one successful native
   startup. Keep a backup of legacy storage until that point.
6. Make reruns idempotent and crash-safe. On failure, show a neutral recovery
   screen and retain the old data.

The current Flutter migration code deletes the old plaintext Hive file only
after copying it to a backup and successfully writing the encrypted box. The
native migration must preserve that safety property and must also cover the
secure-storage formats, which the current startup code does not migrate to a
new implementation.

`legacy_migration_bridge_reader.dart` first rejects an interrupted
`settings.hive.migration-backup` state, validates that the source is a regular
file, and then opens only a bounded temporary copy. A formally valid but wrong
Hive key, corrupt Hive bytes, source symlink, unknown secure key, mismatched
Hive/secure presence or copy instability fails closed with a value-free error.
The source box and its asset files remain byte-for-byte unchanged in tests.

`legacyMigrationHeadlessMain` is a secondary, tree-shaker-preserved Dart
entrypoint. Its Kotlin launcher creates a separate engine on the Android main
thread, disables generated plugin registration, app-registers only
`path_provider` and `flutter_secure_storage`, performs one versioned read, then
destroys the engine before the worker-thread reader decodes the envelope. The
reader rejects main-thread invocation and bounds the request wait to 30 seconds
by default (120 seconds maximum), followed by at most five seconds waiting for
timeout disposal. Nothing invokes this launcher from current app startup.

The voice manifest contains the current 18 reusable paths: Kokoro's model,
tokenizer and every catalogued voice, both Piper files and the eSpeak sentinel.
The manifest is tested directly against the production Kokoro/Piper catalogues;
missing optional downloads are valid and are skipped.

The normalized v1 envelope, its bounds and its exact evidence boundary are
specified in `LEGACY_SNAPSHOT_ENVELOPE.md`. Its committed fixture covers every
fixed key above, one valid instance of every dynamic key prefix, all nine
secure keys and explicit Kokoro, Piper and eSpeak asset metadata. A second
committed fixture is an actual encrypted Hive 2.2.3 box with synthetic values;
the Dart collector reads that box and produces the exact envelope decoded by
Kotlin. This catches raw-shape and transport drift, but it does not replace
signed installed-app or secure-storage fixtures.

## Open verification items

- Produce fixtures from real supported 0.5.x installations, including current
  and historical secure-storage formats and encrypted Hive boxes.
- Verify that the candidate `resetOnError=false`, backup-protected plugin read
  preserves rollback on each supported historical format.
- Execute the isolated headless launcher end-to-end on each signed fixture and
  measure engine startup time and peak memory.
- Confirm the exact Android Keystore aliases/files on a signed installed build.
- Exercise every dynamic key and any plugin-owned files in real install fixtures.
- Decide the supported legacy release range after fixture coverage exists.
- Prove failure behavior on installed fixtures for Keystore failure, corrupt
  Hive, process death and missing keys before any Flutter UI removal.
