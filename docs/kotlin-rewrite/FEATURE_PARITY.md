# Feature parity matrix

Legend: `FUNCTIONAL` describes the current Flutter product on the baseline;
`CORE_ONLY` means deterministic Kotlin policy/parser code and tests exist but
are not connected to a native runtime; `NOT_STARTED` means no native port yet.
No native production runtime exists, so no Kotlin row is marked functional.

| Area | Shipped behavior / oracle | Current source | Flutter state | Kotlin state | Release evidence required |
|---|---|---|---|---|---|
| App shell | First-launch gate, privacy disclosure, error/recovery screen | `lib/main.dart`, `lib/screens/onboarding_screen.dart` | FUNCTIONAL | NOT_STARTED | cold start and migration fixtures |
| Navigation | Home/map hierarchy, bottom bar, sheets and route panels | `lib/screens/map_screen.dart`, `maplibre_map_screen.dart` | FUNCTIONAL | NOT_STARTED | UI catalogue + navigation flows |
| Driving routes | OSRM, alternatives, progress, rerouting, traffic avoidance | `lib/services/routing_service.dart`, `routing_orchestration_protocol.dart`, `routing_avoidance_protocol.dart`, `routing_provider_config.dart` | FUNCTIONAL | CORE_ONLY | headless native primary routing plus Valhalla hard/soft/track avoidance, best-effort OSRM re-timing and cancellation are green; secure reads, startup and UI wiring remain |
| Walking/cycling | Walking and cycling route modes and guidance | `routing_service.dart`, map screens | FUNCTIONAL | CORE_ONLY | headless OSRM/ORS/GraphHopper mode dispatch and response parsing green; live guidance remains |
| GraphHopper | Public/cloud and self-hosted URL, API key and loopback policy | `routing_service.dart`, `routing_provider_config.dart`, settings | FUNCTIONAL | CORE_ONLY | resolved public/self-hosted native execution and local OkHttp integration green; native secure-store/device migration and startup wiring remain |
| OpenRouteService | ORS route modes, API key and localized requests | `routing_service.dart`, `routing_provider_config.dart` | FUNCTIONAL | CORE_ONLY | resolved-key native POST and normalized response green; native secure-store/device migration and startup wiring remain |
| Public transport | Bus, tram, metro, rail, coach, ferry itineraries | `lib/services/transit_service.dart`, transit widgets | FUNCTIONAL | NOT_STARTED | Berlin fixture and live-provider manual test |
| MapLibre map | Vector style, tilt, rotation, camera easing, overlays | `maplibre_map_screen.dart`, `lib/theme/app_theme.dart` | FUNCTIONAL/default | CORE_ONLY | screenshot/golden + performance |
| Legacy map engine | Raster `flutter_map` renderer selectable in Settings | `map_screen.dart`, `settings_screen.dart` | FUNCTIONAL/selectable | NOT_STARTED | setting preserved and behavior verified |
| Map overlays | routes, current cursor, traffic lights, crossings, bumps, cameras, ZTL | `lib/services/*_service.dart`, map screens | FUNCTIONAL | CORE_ONLY | overlay fixture and camera tests |
| Navigation HUD | manoeuvre symbols, road name, speed, limit, altitude, distance | `lib/widgets/nav`, `speedometer_widget.dart` | FUNCTIONAL | NOT_STARTED | existing goldens + screen comparison |
| Vehicle cursor | styles, seven colours, walking animation and culling | `lib/widgets/cursor_painter.dart` | FUNCTIONAL | NOT_STARTED | existing cursor goldens |
| Search | Photon, Nominatim, category/brand Overpass, ranking/history | `place_search_service.dart`, `poi_search_service.dart`, `search_orchestration_protocol.dart`, `search_history_protocol.dart` | FUNCTIONAL | CORE_ONLY | headless provider execution/orchestration and an atomic Keystore-backed history store with transactional legacy extraction are green; cache, UI generations, startup, device-Keystore and Android network integration remain |
| Place details | OSM details, opening hours, contextual POI data | `place_info_panel.dart`, `opening_hours.dart` | FUNCTIONAL | CORE_ONLY | parser and screen parity |
| Wikipedia | restricted in-app Wikipedia article reader | `wikipedia_webview_screen.dart` | FUNCTIONAL | NOT_STARTED | URI allowlist tests |
| Favourites | local favourites, import/export, limits and validation | `favorite_place.dart`, map/settings screens | FUNCTIONAL | NOT_STARTED | storage migration and content comparison |
| Parking | saved local parking position | map screens | FUNCTIONAL | NOT_STARTED | storage migration |
| Nostr relay | NIP-01 sockets, relay rotation, retry/backoff and limits | `nostr_relay_service.dart`, `nostr_relay_message.dart`, `nostr_relay_ingress.dart` | FUNCTIONAL | CORE_ONLY | outbound, bounded inbound, admission-budget and 43-case BIP-340 fixtures green; native sockets, signature dispatch and adversarial lifecycle tests required |
| Road events | kind 1315 reports, TTL, geohashes, confirmations/dismissals | `road_event.dart`, `nostr_relay_service.dart` | FUNCTIONAL | CORE_ONLY | shared golden events and deterministic signatures green; signer isolation/runtime still required |
| Corrections | kind 1317 owner edits and kind 1318 edit requests | `nostr_relay_service.dart`, profile/map screens | FUNCTIONAL | CORE_ONLY | exact tags/IDs/signatures green; native signer wiring and conflict tests required |
| Offline queue | pending signed reports, TTL drop and retry | `nostr_relay_service.dart`, `nostr_pending_report_queue.dart` | FUNCTIONAL | CORE_ONLY | shared JSON/FIFO/TTL/retry fixture green; native store, concurrency and process-death tests required |
| Profile/identity | Amber NIP-55 and local nsec, profile metadata and visibility | `nostr_nip19.dart`, `nostr_schnorr.dart`, onboarding/profile screens | FUNCTIONAL | CORE_ONLY | npub/nsec plus x-only derivation/BIP-340 fixtures green; native key storage/side-channel approval + manual Amber tests required |
| Activity inbox | persisted zaps/confirmations, cursors and dedupe | `activity_notification_service.dart` | FUNCTIONAL | NOT_STARTED | per-pubkey storage fixtures |
| Favourites sync | NIP-78 kind 30078, NIP-44, optional passphrase and relay policy | `favorites_sync_service.dart`, `favorites_sync_protocol.dart`, `nip44.dart` | FUNCTIONAL | CORE_ONLY | NIP-78/NIP-44/BIP-340 fixtures green; signer/key isolation, passphrase storage and sockets required |
| Lightning | LNURL-pay, NIP-57 zaps, BOLT-11 validation and fallback link | `zap_service.dart`, `bolt11_invoice.dart`, `lightning_protocol.dart`, `lnurl_protocol.dart` | FUNCTIONAL | CORE_ONLY | LNURL source/metadata/callback/invoice and zap draft/receipt bindings green; native DNS/HTTP and crypto required |
| NWC | NIP-47 pay_invoice with verified NIP-44/NIP-04 negotiation | `zap_service.dart`, `lightning_protocol.dart`, `nip44.dart`, `nip04.dart` | FUNCTIONAL | CORE_ONLY | Flutter negotiation is live and 89 wire/downgrade cases plus crypto/BIP-340 fixtures are green; native signer/sockets/key storage and live-wallet evidence required |
| GPS | LocationManager path, last-known, watchdog, assistance and lifecycle | `gps_service.dart`, map screens | FUNCTIONAL | CORE_ONLY | headless AOSP LocationManager adapter, safe fix normalization, last-known, watchdog and cancellation are green; startup/UI, foreground notification, permission and device-lifecycle evidence remain |
| Background navigation | foreground notification, wakelock, screen/brightness policy | navigation notification/map screens | FUNCTIONAL | NOT_STARTED | process/background manual test |
| Voice | Kokoro, Piper, eSpeak phonemization, language/voice/speed/audio policy | `lib/services/kokoro`, `piper` | FUNCTIONAL | NOT_STARTED | asset reuse, audio and language matrix |
| Settings | routing, voice, map, theme, units, sync, privacy and cursor settings | `settings_screen.dart` | FUNCTIONAL | NOT_STARTED | all persisted keys and defaults |
| Themes | Violet/Bitcoin Orange, light/dark, auto sunrise/sunset | `app_theme.dart`, `theme_provider.dart` | FUNCTIONAL | CORE_ONLY | theme tests + screenshots |
| Localization | 27 locales, fallback and route voice phrases | `lib/l10n`, `nav_phrases.dart` | FUNCTIONAL | CORE_ONLY | generated 27-language route-instruction table green; UI keys/placeholders remain |
| Privacy/security | no telemetry, backup off, cleartext bounds, input caps and secret hygiene | manifest, network config, services | FUNCTIONAL | CORE_ONLY | HTTP cap/redirect/loopback plus search request/privacy-rounding fixtures green; static and runtime security audit remains |
| Release identity | version checks, R8, ABI/native checksum and store metadata | Gradle, `build_release.sh`, metadata | FUNCTIONAL | NOT_STARTED | clean native/F-Droid/release build |

Any row that remains `PARTIAL` in the Kotlin column is a release blocker unless
the user explicitly approves an intentional behavior change and it is recorded
as `INTENTIONALLY_CHANGED_WITH_APPROVAL`.
