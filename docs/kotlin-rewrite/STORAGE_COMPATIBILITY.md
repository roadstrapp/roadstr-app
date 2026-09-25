# Storage compatibility inventory

Status: forensic inventory, bounded transactional migration core, a
reproducible encrypted Hive 2.2.3 fixture, a strict read-only Dart collector
and the Dart-to-Kotlin envelope are implemented and tested. Reading protected
storage from controlled signed installations remains open.

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
`EncryptedSharedPreferences` option. The default plugin path is:

- Android `SharedPreferences` data name `FlutterSecureStorage`;
- default prefixed keys (the plugin's default prefix plus `_` and the logical
  key);
- AES-GCM value encryption with a random IV;
- an AES data key wrapped with RSA-OAEP/SHA-256 in an Android Keystore alias
  derived from the package name;
- plugin configuration/algorithm metadata and wrapped-key preferences managed
  by the plugin.

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
- Confirm the exact Android Keystore aliases/files on a signed installed build.
- Exercise every dynamic key and any plugin-owned files in real install fixtures.
- Decide the supported legacy release range after fixture coverage exists.
- Prove failure behavior for Keystore failure, corrupt Hive, process death and
  missing keys before any Flutter UI removal.
