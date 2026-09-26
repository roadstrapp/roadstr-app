# Feature parity matrix

Legend: `FUNCTIONAL` describes the current Flutter product on the baseline;
`CORE_ONLY` means deterministic Kotlin policy/parser code and tests exist but
are not connected to a native runtime; `NOT_STARTED` means no native port yet.
No native production runtime exists, so no Kotlin row is marked functional.

| Area | Shipped behavior / oracle | Current source | Flutter state | Kotlin state | Release evidence required |
|---|---|---|---|---|---|
| App shell | First-launch gate, privacy disclosure, error/recovery screen | `lib/main.dart`, `lib/screens/onboarding_screen.dart` | FUNCTIONAL | NOT_STARTED | cold start and migration fixtures |
| Navigation | Home/map hierarchy, bottom bar, sheets and route panels | `lib/screens/map_screen.dart`, `maplibre_map_screen.dart` | FUNCTIONAL | NOT_STARTED | UI catalogue + navigation flows |
| Driving routes | OSRM, alternatives, progress, rerouting, traffic avoidance | `lib/services/routing_service.dart` | FUNCTIONAL | CORE_ONLY | response fixtures + provider matrix |
| Walking/cycling | Walking and cycling route modes and guidance | `routing_service.dart`, map screens | FUNCTIONAL | NOT_STARTED | route/guidance fixture parity |
| GraphHopper | Public/cloud and self-hosted URL, API key and loopback policy | `routing_service.dart`, settings | FUNCTIONAL | NOT_STARTED | key migration + HTTP policy tests |
| OpenRouteService | ORS route modes, API key and localized requests | `routing_service.dart` | FUNCTIONAL | NOT_STARTED | request/response fixtures |
| Public transport | Bus, tram, metro, rail, coach, ferry itineraries | `lib/services/transit_service.dart`, transit widgets | FUNCTIONAL | NOT_STARTED | Berlin fixture and live-provider manual test |
| MapLibre map | Vector style, tilt, rotation, camera easing, overlays | `maplibre_map_screen.dart`, `lib/theme/app_theme.dart` | FUNCTIONAL/default | CORE_ONLY | screenshot/golden + performance |
| Legacy map engine | Raster `flutter_map` renderer selectable in Settings | `map_screen.dart`, `settings_screen.dart` | FUNCTIONAL/selectable | NOT_STARTED | setting preserved and behavior verified |
| Map overlays | routes, current cursor, traffic lights, crossings, bumps, cameras, ZTL | `lib/services/*_service.dart`, map screens | FUNCTIONAL | CORE_ONLY | overlay fixture and camera tests |
| Navigation HUD | manoeuvre symbols, road name, speed, limit, altitude, distance | `lib/widgets/nav`, `speedometer_widget.dart` | FUNCTIONAL | NOT_STARTED | existing goldens + screen comparison |
| Vehicle cursor | styles, seven colours, walking animation and culling | `lib/widgets/cursor_painter.dart` | FUNCTIONAL | NOT_STARTED | existing cursor goldens |
| Search | Photon, Nominatim, category/brand Overpass, ranking/history | `place_search_service.dart`, `poi_search_service.dart` | FUNCTIONAL | CORE_ONLY | provider, ranking and history fixtures |
| Place details | OSM details, opening hours, contextual POI data | `place_info_panel.dart`, `opening_hours.dart` | FUNCTIONAL | CORE_ONLY | parser and screen parity |
| Wikipedia | restricted in-app Wikipedia article reader | `wikipedia_webview_screen.dart` | FUNCTIONAL | NOT_STARTED | URI allowlist tests |
| Favourites | local favourites, import/export, limits and validation | `favorite_place.dart`, map/settings screens | FUNCTIONAL | NOT_STARTED | storage migration and content comparison |
| Parking | saved local parking position | map screens | FUNCTIONAL | NOT_STARTED | storage migration |
| Nostr relay | NIP-01 sockets, relay rotation, retry/backoff and limits | `nostr_relay_service.dart`, `nostr_relay_message.dart`, `nostr_relay_ingress.dart` | FUNCTIONAL | CORE_ONLY | outbound, bounded inbound and admission-budget fixtures green; native sockets, signature dispatch and adversarial lifecycle tests required |
| Road events | kind 1315 reports, TTL, geohashes, confirmations/dismissals | `road_event.dart`, `nostr_relay_service.dart` | FUNCTIONAL | CORE_ONLY | shared golden events green; signatures/runtime still required |
| Corrections | kind 1317 owner edits and kind 1318 edit requests | `nostr_relay_service.dart`, profile/map screens | FUNCTIONAL | CORE_ONLY | exact tags/IDs green; signatures and conflict tests required |
| Offline queue | pending signed reports, TTL drop and retry | `nostr_relay_service.dart`, `nostr_pending_report_queue.dart` | FUNCTIONAL | CORE_ONLY | shared JSON/FIFO/TTL/retry fixture green; native store, concurrency and process-death tests required |
| Profile/identity | Amber NIP-55 and local nsec, profile metadata and visibility | `nostr_nip19.dart`, onboarding/profile screens | FUNCTIONAL | CORE_ONLY | npub/nsec representation fixture green; native key derivation/storage + manual Amber tests required |
| Activity inbox | persisted zaps/confirmations, cursors and dedupe | `activity_notification_service.dart` | FUNCTIONAL | NOT_STARTED | per-pubkey storage fixtures |
| Favourites sync | NIP-78 kind 30078, NIP-44, optional passphrase and relay policy | `favorites_sync_service.dart` | FUNCTIONAL | CORE_ONLY | bounded ingress green; payload wire and rollback fixtures required |
| Lightning | LNURL-pay, NIP-57 zaps, BOLT-11 validation and fallback link | `zap_service.dart`, `bolt11_invoice.dart` | FUNCTIONAL | CORE_ONLY | invoice plus relay admission green; LNURL/NIP-57 fixtures required |
| NWC | NIP-47 pay_invoice over NIP-04 | `zap_service.dart` | FUNCTIONAL | CORE_ONLY | bounded response admission green; URI/encrypted request fixtures required |
| GPS | LocationManager path, last-known, watchdog, assistance and lifecycle | `gps_service.dart`, map screens | FUNCTIONAL | NOT_STARTED | de-Googled manual test + lifecycle tests |
| Background navigation | foreground notification, wakelock, screen/brightness policy | navigation notification/map screens | FUNCTIONAL | NOT_STARTED | process/background manual test |
| Voice | Kokoro, Piper, eSpeak phonemization, language/voice/speed/audio policy | `lib/services/kokoro`, `piper` | FUNCTIONAL | NOT_STARTED | asset reuse, audio and language matrix |
| Settings | routing, voice, map, theme, units, sync, privacy and cursor settings | `settings_screen.dart` | FUNCTIONAL | NOT_STARTED | all persisted keys and defaults |
| Themes | Violet/Bitcoin Orange, light/dark, auto sunrise/sunset | `app_theme.dart`, `theme_provider.dart` | FUNCTIONAL | CORE_ONLY | theme tests + screenshots |
| Localization | 27 locales, fallback and route voice phrases | `lib/l10n`, `nav_phrases.dart` | FUNCTIONAL | NOT_STARTED | key/placeholder comparison |
| Privacy/security | no telemetry, backup off, cleartext bounds, input caps and secret hygiene | manifest, network config, services | FUNCTIONAL | NOT_STARTED | static and runtime security audit |
| Release identity | version checks, R8, ABI/native checksum and store metadata | Gradle, `build_release.sh`, metadata | FUNCTIONAL | NOT_STARTED | clean native/F-Droid/release build |

Any row that remains `PARTIAL` in the Kotlin column is a release blocker unless
the user explicitly approves an intentional behavior change and it is recorded
as `INTENTIONALLY_CHANGED_WITH_APPROVAL`.
