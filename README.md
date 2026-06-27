# Roadstr

**Roadstr** is an open-source, decentralised navigation app built on the [Nostr protocol](https://nostr.com/).  
It combines real-time GPS turn-by-turn navigation with community-sourced traffic alerts published as Nostr events, Lightning Network tips (zaps) for contributors, and privacy-first map data powered by OpenStreetMap and OSRM.

> **Version 0.1.0** — Android only. iOS support is planned for a future release.

---

## Features

| Feature | Status |
|---|---|
| OpenStreetMap tile rendering | ✅ |
| Real-time GPS navigation (turn-by-turn) | ✅ |
| OSRM driving + walking routes | ✅ |
| Multi-route alternatives with traffic preview | ✅ |
| Nostr road events — kind 1315 (reports) / 1316 (confirmations) | ✅ |
| Traffic overlay from community Nostr events | ✅ |
| Nostr identity login (Amber NIP-55 / nsec) | ✅ |
| Lightning Network zaps for road-event contributors | ✅ |
| Address & POI search with category emojis | ✅ |
| Place info with Wikipedia geo-aware lookup | ✅ |
| Search history | ✅ |
| Cinematic navigation start transition | ✅ |
| Navigation notification in Android shade | ✅ |
| Localisation (Italian + English) | ✅ |
| Offline maps | 🔜 Planned |
| iOS support | 🔜 Planned |
| Nostr DMs / collaborative alerts | 🔜 Planned |

---

## Architecture overview

```
lib/
├── main.dart                    # App entry point, provider setup
├── l10n/                        # ARB localisation files (en / it)
├── models/
│   └── road_event.dart          # Nostr kind-1315/1316 data model + TTLs
├── providers/
│   └── locale_provider.dart     # Language override (Hive-backed)
├── screens/
│   ├── map_screen.dart          # Main map + navigation + route planner
│   ├── profile_screen.dart      # Nostr identity, reports history, zap balance
│   └── settings_screen.dart     # Routing provider, theme, language, NWC
├── services/
│   ├── gps_service.dart         # Android foreground GPS stream
│   ├── location_service.dart    # Default fallback position
│   ├── nostr_relay_service.dart # WebSocket relay, geohash subscriptions
│   ├── routing_service.dart     # OSRM / GraphHopper / ORS + Nominatim + Wikipedia
│   ├── zap_service.dart         # LNURL-pay + NIP-57 zaps + NWC (NIP-47)
│   └── navigation_notification_service.dart  # Android turn-by-turn notification
├── theme/
│   └── app_theme.dart           # Material 3 theme + RoadstrColors extension
└── widgets/
    └── speedometer_widget.dart  # Circular speedometer
```

---

## Nostr protocols used

| NIP | Purpose |
|---|---|
| NIP-01 | Base relay protocol (REQ / EVENT / EOSE / CLOSE) |
| NIP-04 | Symmetric encryption for NWC pay requests |
| NIP-19 | Bech32 key encoding (npub / nsec) |
| NIP-47 | Nostr Wallet Connect — pay Lightning invoices from any NWC-compatible wallet |
| NIP-55 | Android Signer Application — Amber integration (`startActivityForResult`) |
| NIP-57 | Zap receipts — Lightning tips attached to kind-1315 road events |
| kind-1315 | Road event report (police, traffic jam, accident, pothole…) |
| kind-1316 | Road event confirmation / dismissal |

---

## Prerequisites

1. **Flutter SDK** — install from <https://flutter.dev/docs/get-started/install>  
   Select *Android* as the target platform and follow the official guide (Android Studio is required).

2. **Android device** running Android 8.0+ (API 26+).  
   The emulator works for UI development, but GPS behaviour is unreliable there.

3. **VS Code** (recommended) with the Flutter and Dart extensions.

---

## Getting started

```bash
# 1. Clone the repository
git clone https://github.com/<your-org>/roadstr.git
cd roadstr

# 2. Install dependencies
flutter pub get

# 3. Generate localisation files
flutter gen-l10n

# 4. Generate app icons (requires the source PNG at assets/icons/app_icon.png)
flutter pub run flutter_launcher_icons

# 5. Connect your Android phone via USB and enable USB debugging
#    Settings → About phone → tap "Build number" 7 times → Developer options → USB debugging

# 6. Verify Flutter detects your device
flutter devices

# 7. Run the app
flutter run
```

---

## Routing providers

Roadstr supports multiple routing backends, selectable in **Settings → Routing provider**:

| Provider | Mode | API key required |
|---|---|---|
| OSRM (public) | Car + Walking | No |
| GraphHopper (self-hosted) | Car + Walking | No (needs server URL) |
| GraphHopper Cloud | Car + Walking | Yes |
| OpenRouteService | Car + Walking | Yes |

OSRM is the default and requires no configuration.  
Walking routes use the OSRM `foot` profile or GraphHopper `vehicle=foot`.

---

## Lightning Network (Zaps)

Users can tip road-event reporters with Bitcoin via the Lightning Network:

1. Roadstr fetches the reporter's Lightning address (`lud16`) from their Nostr kind-0 profile.
2. It resolves the LNURL-pay endpoint and, if the user is logged in with nsec, attaches a signed NIP-57 zap request (kind-9734).
3. Payment is sent via **NWC (NIP-47)** if a wallet URI is configured in Settings → Lightning, or falls back to a `lightning:` deep link that opens any installed Lightning wallet.
4. The LNURL server publishes a kind-9735 zap receipt to Nostr relays once the invoice is settled.

To connect your wallet, paste a `nostr+walletconnect://…` URI from a compatible wallet (Alby Hub, Mutiny, Cashu NWC) in **Settings → Lightning → Nostr Wallet Connect**.

---

## Voice input

Voice search uses the Android system's built-in on-device speech recogniser via a native `SpeechRecognizer` API call — no Google dependency, no network request. The microphone button in the search bar activates listening; results are filled directly into the search field.

---

## Privacy

- **No accounts, no servers** — the app communicates directly with public Nostr relays and OpenStreetMap tile servers.
- **GPS data never leaves the device** — position fixes are used locally for navigation; they are not sent to any telemetry endpoint.
- **Road events are pseudonymous** — published under the user's Nostr public key with no additional personal information.
- **IP geolocation was intentionally removed** — the app starts at a default map centre and jumps to the GPS position when available.
- Privacy mode (Settings → Privacy → Privacy mode) disables anonymous telemetry if any is ever added.

---

## Troubleshooting

**`flutter: command not found`**  
Flutter is not in your `PATH`. Add the `flutter/bin` directory to your shell profile (`~/.bashrc`, `~/.zshrc`, etc.).

**`No devices found`**  
Enable USB debugging on the phone. Some devices also require accepting a "Trust this computer" prompt.

**GPS unavailable**  
The app requests location permission on first launch. If denied, go to *Settings → Apps → Roadstr → Permissions → Location* and grant it.

**Map tiles don't load**  
Check your internet connection. OSM tiles are fetched online at runtime.

**Amber shows "invalid request"**  
Make sure your version of Amber supports NIP-55. The app sends a `startActivityForResult` intent using the NIP-55 `get_public_key` method — no `nostrsigner:` URL scheme is involved.

**Voice input error (code 10)**  
Error 10 is `ERROR_TOO_MANY_REQUESTS` from the Android speech recogniser. Wait a moment and try again. On Samsung devices the on-device recogniser may take a few seconds to release a previous session.

---

## Contributing

Pull requests are welcome. For major changes please open an issue first to discuss what you would like to change.

Please follow the existing code style:  
- All `///` doc-comments and `// inline comments` must be in **English**.  
- Complex algorithms must have numbered step comments.  
- Nostr protocol references should cite the relevant NIP number.

---

## Licence

[MIT](LICENSE) — free to use, modify and distribute. Attribution appreciated.

---

## Acknowledgements

- [OpenStreetMap](https://www.openstreetmap.org/) contributors for the map data
- [OSRM](https://project-osrm.org/) for the open-source routing engine
- [Nominatim](https://nominatim.org/) for geocoding
- [Nostr protocol](https://nostr.com/) and all NIP authors
- [flutter_map](https://pub.dev/packages/flutter_map) for the Flutter map widget
- [Amber](https://github.com/greenart7c3/Amber) for the Android Nostr signing app
