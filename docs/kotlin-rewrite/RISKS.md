# Kotlin rewrite risks and blockers

| ID | Risk | Impact | Likelihood | Mitigation / gate | Status |
|---|---|---:|---:|---|---|
| R-01 | Official release certificate is not available in the checkout | Critical | High | Obtain controlled certificate fingerprint and verify a signed APK before cutover | Open blocker |
| R-02 | Hive 2.2.3 compatibility with boxes from supported installed releases is unproven | Critical | High | Synthetic encrypted Hive read is green; add signed-install fixtures and retain the Dart bridge until native decoding is proven | Open (synthetic green) |
| R-03 | `flutter_secure_storage` has multiple historical Android formats | Critical | High | Candidate uses the exact plugin with backup-protected migration; test v10.3.1 plus supported older 0.5.x signed fixtures | Open (adapter isolated) |
| R-04 | A failed migration creates a blank identity/profile | Critical | Medium | Fail closed, retain legacy files, compare derived pubkey, show recovery UI | Design gate |
| R-05 | Version code or signing mismatch blocks normal Android update | Critical | Medium | Exact identity checks, higher code than 2050, signed APK upgrade test | Open |
| R-06 | Nostr event bytes/tags change during port | Critical | Medium | Shared Dart/Kotlin fixture now locks canonical JSON, IDs, all Roadstr 1315 categories, 1316-1318 and profile visibility; native signature vectors remain required | In progress (deterministic core green) |
| R-07 | Legacy raster map setting is silently removed | High | Medium | Keep the setting and implement equivalent raster behavior through native MapLibre or transitional path | Not started |
| R-08 | Map/navigation lifecycle regresses on rotation/background/process death | High | Medium | Service/ViewModel ownership, lifecycle tests and manual navigation sessions | Not started |
| R-09 | GPS battery behavior regresses on de-Googled devices | High | Medium | Port existing LocationManager/watchdog policy and benchmark against Flutter | Not started |
| R-10 | Voice models are unnecessarily re-downloaded | High | Medium | Reuse `kokoro/`, `piper/`, eSpeak files after size/hash validation | Not started |
| R-11 | MapLibre Native dependency introduces proprietary/Google transitive code | High | Medium | Dependency/license table, Gradle dependency audit and no-Google test | Not started |
| R-12 | F-Droid recipe no longer builds or detects versions | High | Medium | Keep literal version values, port recipe/output/NDK steps, validate from bare clone | Not started |
| R-13 | 27 localization sets lose keys/placeholders/fallback behavior | High | Medium | Key-coverage and placeholder comparison before UI completion | Not started |
| R-14 | Amber/NIP-55 signing semantics change | High | Medium | NIP-19 key normalization is fixture-locked; add Intent fixtures/manual signer tests and never copy private keys for Amber | In progress (key representation green) |
| R-15 | Native release build cannot be reproduced offline/F-Droid-style | High | Medium | Pin dependencies, retain eSpeak reproducibility and build in clean environment | Not started |
| R-16 | Protocol/network limits are approximated instead of ported | High | Medium | Outbound bytes, inbound envelope bounds and relay-service subscription/kind/budget admission are fixture-locked; port signature-aware dispatch, remaining Zap/favourites admission and socket policy from source | In progress (ingress core green) |
| R-17 | Physical-device upgrade path is untested | Critical | High | Manual signed-APK checklist owned by user; no release candidate without evidence | Open |
| R-18 | Parallel Flutter/main changes expand parity gap | Medium | Medium | Periodically audit main, update matrix intentionally, never silently drop features | Process |
| R-19 | Default Dart `AndroidOptions` sets `resetOnError=true`, allowing a read/decrypt error to erase protected values | Critical | Medium | Bridge explicitly sets false; prove historical reads and address current startup in a separate reviewed change | Open blocker |
| R-20 | A headless Flutter engine can deadlock startup, leak plugins or outlive migration | Critical | Medium | Worker-thread-only bounded wait, one-shot protocol, minimal plugin registration and engine destruction before decode; prove on signed devices before wiring | Open (adapter green) |
| R-21 | The shipped pending-report queue is unbounded and overlapping flushes can race the final Hive rewrite | High | Medium | Preserve current behavior in parity fixtures; before native wiring, approve a bounded policy and single-flight/transactional adapter with crash/concurrency tests | Open (source gap recorded) |

## Current stop condition

No destructive framework-removal work may begin until R-01 through R-04 have
evidence and the migration prototype can preserve identity, protected storage,
favourites and model assets in fixtures.
