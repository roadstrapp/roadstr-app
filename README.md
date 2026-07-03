# Roadstr

**Roadstr** is an open-source, decentralised navigation app for Android, built on the [Nostr protocol](https://nostr.com/).  
It combines real-time GPS turn-by-turn navigation with community-sourced traffic alerts published as Nostr events, on-device AI voice guidance, Lightning Network tips for contributors, and privacy-first map data powered by OpenStreetMap.

> **Version 0.3.1** — Android only.

---

## Features

| Feature | Status |
|---|---|
| OpenStreetMap tile rendering (light & dark) | ✅ |
| Real-time GPS navigation — turn-by-turn | ✅ |
| OSRM driving + walking routes | ✅ |
| GraphHopper (self-hosted + cloud) routing | ✅ |
| OpenRouteService routing | ✅ |
| Multi-route alternatives with traffic preview | ✅ |
| Route planner (A → B with freeform waypoints) | ✅ |
| Speed-adaptive zoom | ✅ |
| Tilt-compensated compass heading | ✅ |
| On-device AI voice guidance (Kokoro-82M) | ✅ |
| Nostr road events — kind 1315 (reports) / 1316 (confirmations) | ✅ |
| Traffic alerts on-route with rerouting prompt | ✅ |
| Nostr identity login (Amber NIP-55 / nsec) | ✅ |
| Lightning Network zaps for road-event contributors | ✅ |
| Nostr Wallet Connect (NIP-47) | ✅ |
| Address & POI search (Nominatim) | ✅ |
| Place info with Wikipedia geo-aware lookup | ✅ |
| Saved favourite places | ✅ |
| Search history | ✅ |
| Speedometer HUD | ✅ |
| Light + Dark themes — Nostr Violet & Bitcoin Orange | ✅ |
| Auto dark mode (sunset / sunrise via GPS position) | ✅ |
| 27 languages (all EU official languages + RU, JA, ZH) | ✅ |
| Navigation notification in Android shade | ✅ |
| Offline maps | 🔜 Planned |
| Nostr DMs / collaborative alerts | 🔜 Planned |

---

## Architecture

```
lib/
├── main.dart                         # Entry point, provider tree, first-launch gate
├── l10n/                             # ARB files (27 locales) + generated Dart classes
├── models/
│   ├── favorite_place.dart           # Saved places data model (Hive-backed)
│   └── road_event.dart               # Nostr kind-1315/1316 model + TTLs
├── providers/
│   └── locale_provider.dart          # Language override (Hive-backed)
├── screens/
│   ├── map_screen.dart               # Main map, navigation, route planner, Nostr events
│   ├── profile_screen.dart           # Nostr identity, reports history, zap balance
│   └── settings_screen.dart          # Theme, routing, voice, language, NWC, favourites
├── services/
│   ├── gps_service.dart              # Android foreground GPS stream
│   ├── location_service.dart         # Nominatim geocoding + Wikipedia lookup
│   ├── navigation_notification_service.dart  # Android turn-by-turn notification
│   ├── nostr_relay_service.dart      # WebSocket relay, geohash area subscriptions
│   ├── routing_service.dart          # OSRM / GraphHopper / ORS routing
│   ├── sun_calc.dart                 # NOAA solar position — sunrise/sunset times
│   ├── zap_service.dart              # LNURL-pay + NIP-57 zaps + NWC (NIP-47)
│   └── kokoro/
│       ├── kokoro_engine.dart        # ONNX Runtime inference wrapper
│       ├── kokoro_model_manager.dart # Model download + file management
│       ├── kokoro_tts_service.dart   # TTS orchestration + audio playback
│       ├── kokoro_voices.dart        # Voice / language registry
│       └── espeak_phonemizer.dart    # Dart FFI bridge to eSpeak NG
├── theme/
│   ├── app_theme.dart                # Material 3 themes + RoadstrColors extension
│   └── theme_provider.dart           # Theme state, auto dark mode, Hive persistence
└── widgets/
    ├── hud_info_widget.dart          # Navigation HUD (distance, ETA, speed limit)
    └── speedometer_widget.dart       # Circular analogue speedometer
```

---

## Nostr protocols

| NIP | Purpose |
|---|---|
| NIP-01 | Base relay protocol (REQ / EVENT / EOSE / CLOSE) |
| NIP-04 | Symmetric encryption for NWC pay requests |
| NIP-19 | Bech32 key encoding (npub / nsec) |
| NIP-47 | Nostr Wallet Connect — pay Lightning invoices from any compatible wallet |
| NIP-55 | Android Signer Application — Amber integration |
| NIP-57 | Zap receipts — Lightning tips attached to kind-1315 road events |
| kind-1315 | Road event report (police, speed camera, traffic jam, accident, hazard…) |
| kind-1316 | Road event confirmation / dismissal |

---

## Prerequisites

1. **Flutter SDK** — install from <https://docs.flutter.dev/get-started/install>.  
   Select *Android* as the target platform. Android Studio is required for the toolchain.

2. **Android device** running Android 8.0+ (API 26+).  
   The emulator works for UI development; GPS behaviour is unreliable on emulators.

3. **VS Code** with the Flutter and Dart extensions (recommended), or Android Studio.

---

## Getting started

```bash
# 1. Clone the repository
git clone https://github.com/roadstrapp/roadstr-app.git
cd roadstr-app

# 2. Install dependencies
flutter pub get

# 3. Generate localisation classes
flutter gen-l10n

# 4. Connect an Android device via USB and enable USB debugging
#    Settings → About phone → tap "Build number" 7× → Developer options → USB debugging

# 5. Verify Flutter detects your device
flutter devices

# 6. Run (debug)
flutter run

# 7. Build a release APK
flutter build apk --release
```

---

## Routing providers

Selectable from **Settings → Routing provider**:

| Provider | API key required |
|---|---|
| OSRM (public) | No |
| GraphHopper (self-hosted) | No — provide your server URL |
| GraphHopper Cloud | Yes |
| OpenRouteService | Yes |

OSRM is the default and requires no configuration. All providers support car and walking profiles.

---

## Voice guidance — Kokoro AI

Roadstr ships with an optional on-device AI text-to-speech engine based on [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M):

- **Fully on-device** — the 82 MB ONNX model runs entirely on the device via [flutter_onnxruntime](https://pub.dev/packages/flutter_onnxruntime). No cloud API, no audio leaves the phone.
- **Phonemisation** via eSpeak NG through a Dart FFI bridge compiled as an Android native library.
- **Supported languages**: Italian, English, Spanish, French, Japanese, Chinese, Portuguese.
- The model is downloaded on demand from **Settings → Navigation voice → Voice model** (~82 MB).

For unsupported languages Roadstr falls back to the Android system TTS engine. If no TTS engine is installed, navigation instructions are shown as text on screen.

---

## Themes

Four built-in themes, selectable from **Settings → Theme**:

| Theme | Description |
|---|---|
| Light · Nostr Violet | Default light theme, purple (#8B5CF6) accent |
| Light · Bitcoin Orange | Light theme, Bitcoin orange (#F7931A) accent |
| Dark · Nostr Violet | Dark theme with CartoDB dark map tiles |
| Dark · Bitcoin Orange | Dark theme with CartoDB dark map tiles |

**Auto dark mode** (enabled by default) switches to the matching dark variant at local sunset and back at sunrise, calculated from the device's GPS position using the NOAA solar position algorithm. Toggleable from **Settings → Theme**.

---

## Lightning Network

Users can tip road-event reporters with Bitcoin over the Lightning Network:

1. Roadstr fetches the reporter's Lightning address (`lud16`) from their Nostr kind-0 profile.
2. It resolves the LNURL-pay endpoint and attaches a signed NIP-57 zap request (kind-9734) if the user is logged in with nsec.
3. Payment is sent via **NWC (NIP-47)** if a wallet URI is configured, or falls back to a `lightning:` deep link that opens any installed Lightning wallet.
4. The LNURL server publishes a kind-9735 zap receipt to Nostr relays once the invoice is settled.

To connect a wallet, paste a `nostr+walletconnect://…` URI from a compatible wallet (Alby Hub, Mutiny, Cashu NWC) in **Settings → Lightning → Nostr Wallet Connect**.

---

## Privacy

- **No accounts, no central servers** — the app communicates directly with public Nostr relays and OpenStreetMap tile servers.
- **GPS data never leaves the device** — position fixes are used locally for navigation and are not sent to any telemetry endpoint.
- **Road events are pseudonymous** — published under the user's Nostr public key with no additional personal metadata.
- **Routing requests** send only the origin and destination coordinates to the chosen routing provider. No account or identifier is attached.

---

## Localisation

Roadstr is fully localised in 27 languages covering all EU official languages plus Russian, Japanese, and Chinese:

`bg` `cs` `da` `de` `el` `en` `es` `et` `fi` `fr` `ga` `hr` `hu` `it` `ja` `lt` `lv` `mt` `nl` `pl` `pt` `ro` `ru` `sk` `sl` `sv` `zh`

Translations live in `lib/l10n/app_<locale>.arb`. To regenerate the Dart classes after editing an ARB file:

```bash
flutter gen-l10n
```

---

## Troubleshooting

**`flutter: command not found`**  
Flutter is not in your `PATH`. Add `flutter/bin` to your shell profile (`~/.bashrc`, `~/.zshrc`).

**`No devices found`**  
Enable USB debugging on the phone. Some devices require accepting a "Trust this computer" prompt after connecting.

**GPS unavailable**  
The app requests location permission on first launch. If denied, go to *Settings → Apps → Roadstr → Permissions → Location*.

**Map tiles don't load**  
Check your internet connection. Tiles are fetched at runtime from OpenStreetMap (light themes) or CartoDB (dark themes).

**Amber shows "invalid request"**  
Ensure your version of Amber supports NIP-55. Roadstr uses the `get_public_key` and `sign_event` methods via `startActivityForResult`.

**Voice guidance not working**  
Download the Kokoro model from *Settings → Navigation voice → Voice model* (~82 MB). If your language is not supported, enable a system TTS engine (e.g. RHVoice or eSpeak NG from F-Droid) and set it as default in *Android Settings → Accessibility → Text-to-speech output*.

---

## Contributing

Pull requests are welcome. For significant changes please open an issue first to discuss the proposal.

Code style:
- All `///` doc-comments and `// inline comments` in **English**.
- Complex algorithms should have numbered step comments.
- Nostr protocol references should cite the relevant NIP.

---

## Licence

[MIT](LICENSE) — free to use, modify and distribute. Attribution appreciated.

---

## Acknowledgements

- [OpenStreetMap](https://www.openstreetmap.org/) contributors for map data
- [OSRM](https://project-osrm.org/) for the open-source routing engine
- [Nominatim](https://nominatim.org/) for geocoding
- [CartoDB](https://carto.com/) for the dark map tile style
- [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) for the on-device TTS model
- [eSpeak NG](https://github.com/espeak-ng/espeak-ng) for phonemisation
- [Nostr protocol](https://nostr.com/) and all NIP authors
- [flutter_map](https://pub.dev/packages/flutter_map) for the Flutter map widget
- [Amber](https://github.com/greenart7c3/Amber) for the Android Nostr signer
