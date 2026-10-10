# Roadstr

**Roadstr** is an open-source, decentralised navigation app for Android, built on the [Nostr protocol](https://nostr.com/).  
It combines real-time GPS turn-by-turn navigation with community-sourced traffic alerts published as Nostr events, on-device AI voice guidance, Lightning Network tips for contributors, and privacy-first map data powered by OpenStreetMap.

> **Version 1.0.0** — Android only. The first stable Kotlin release (Jetpack Compose and MapLibre Native) is here.
> The Flutter app of the 0.5.x line lives on the branch [`flutter-maintenance`](https://github.com/roadstrapp/roadstr-app/tree/flutter-maintenance).

See the complete migration and release history in [CHANGELOG.md](CHANGELOG.md).

---

## What 1.0.0 is

1.0.0 replaces the Flutter app with a native Kotlin app that has the same package name (`app.roadstr`) and is signed with
the same key, so Android installs it **over** an existing 0.5.x without losing anything:

- **Your data comes over once, on the first start.** A short-lived reader opens the old app's storage and brings the identity
  (when it was an Amber login), saved places, parking spot, settings, search history, queued reports, the Lightning wallet
  string, routing keys and the favourites-sync settings. Nothing in the old files is changed or deleted. If something cannot be
  read, the app says so and offers *Try again* or *Continue without importing*; it never starts with a silently empty profile.
- **Sign-in is Amber or a bunker, never a private key.** The Android signer app Amber (NIP-55) and remote signers (NIP-46,
  a `bunker://` link) keep your key outside Roadstr. The login with a pasted `nsec` was removed on purpose: an app that holds
  the key can leak it, and the JVM can neither wipe it from memory nor sign in constant time. An install that was logged in with
  an `nsec` comes over logged out, with a one-time notice; everything else still imports. The Flutter branch keeps the old login.
- **Navigation goes on with the screen off.** The trip, the voice and the GPS live outside the screen, held by a foreground
  service, so locking the phone, rotating it or swiping the app away does not end a trip.
- **Update path and way back.** If a release ever has to be undone, Android cannot go to a lower version code, so a rollback
  build with a higher code is prepared first (see [docs/kotlin-rewrite/RELEASE_TOOLING.md](docs/kotlin-rewrite/RELEASE_TOOLING.md)).

### How far it has been checked

The Kotlin rewrite has been validated extensively on a Pixel 10 running Android 17, with the device session covering the
production update path, migration, navigation and the surrounding integrations:

- The in-place update from 0.5.11, profile-preserving migration and rollback were exercised end to end.
- Background and screen-off navigation, task removal, rotation, landscape, GPS, voice, search, place cards, OSRM routes,
  saved places, cross-device sync, settings, language switching, Amber login/logout and the voice model were exercised.
- The native routing stack, offline packages, saved routes, Tankful JSON destinations, road-event flows, Nostr integrations,
  release identity and signed update path were validated with device checks plus the automated suites.
- OpenStreetMap speed cameras and ZTL/ZAC/limited-traffic warnings are in the Kotlin app, including restricted route colouring,
  live inside/nearby notices and bounded Overpass caching (road events reported by people are there too).
- Known limits are product behavior rather than missing validation: a trip is held in memory, so if Android kills the process
  the trip is not restored; translations have not been reviewed by native speakers (Irish and Maltese are the least reliable).

The details, risk by risk, are in [docs/kotlin-rewrite/RISKS.md](docs/kotlin-rewrite/RISKS.md) and
[docs/kotlin-rewrite/FEATURE_PARITY.md](docs/kotlin-rewrite/FEATURE_PARITY.md).

---

## Features

| Feature | Status |
|---|---|
| OpenStreetMap rendering (light & dark) with MapLibre Native — 3D tilt, rotation, native styling | ✅ |
| Real-time GPS navigation — turn-by-turn, also with the screen off | ✅ |
| OSRM driving, walking and cycling routes | ✅ |
| GraphHopper (self-hosted + cloud) routing | ✅ |
| OpenRouteService routing | ✅ |
| Multi-route alternatives with traffic preview | ✅ |
| Route planner (A → B with freeform waypoints) | ✅ |
| Saved routes — encrypted plans, destinations and waypoints | ✅ |
| Offline routing — verified packages and on-device Valhalla engine | ✅ |
| External JSON destinations — Tankful handoff into route planning | ✅ |
| Speed-adaptive zoom | ✅ |
| Live speed-limit display (explicit OSM `maxspeed` data; unknown stays unknown) | ✅ |
| Speed cameras reported by the community (kind 1315) | ✅ |
| Speed cameras from OpenStreetMap data | ✅ Kotlin app (bounded Overpass cache; baseline, not a community report) |
| ZTL / ZAC / limited-traffic-zone warnings and restricted route colouring (OSM/Overpass) | ✅ |
| Map-element overlays — traffic lights, pedestrian crossings, speed bumps (OSM) and the altitude readout; the *Map element overlays* card in Settings is closed by default and the altitude readout is off by default | ✅ |
| Destination pin on the map while a route is planned or followed | ✅ |
| Lighter navigation rendering — the vehicle cursor animation is drawn at its 30 fps source cadence instead of the display's refresh rate, to save battery | ✅ |
| On-device AI voice guidance (Kokoro-82M; Piper / Thorsten-Voice for German) | ✅ |
| Nostr road events — kind 1315 (reports) / 1316 (confirmations) | ✅ |
| Nostr login — Amber (NIP-55) or a remote signer (NIP-46 via `nostrconnect://` or `bunker://`); no private key in the app | ✅ |
| Public-transport itineraries through Transitous (bus, tram, metro, rail, coach and ferry where open feeds exist) | ✅ |
| Lightning Network zaps for road-event contributors | ✅ |
| Nostr Wallet Connect (NIP-47) | ✅ |
| Address & POI search (Nominatim, position-biased) | ✅ |
| Category / brand POI search near current position (Overpass) | ✅ |
| Place search in plain language, along the route, optional web results | ✅ |
| Place info with native OSM details + restricted in-app Wikipedia reader | ✅ |
| Contextual POI details — Lightning/Bitcoin, parking, EV connectors, access and amenities | ✅ |
| Weather conditions along the route (Open-Meteo) | ✅ |
| Saved places — list, edit and delete in Settings, plus a saved parking spot (local-only) | ✅ |
| Encrypted favourites export (JSON, optional password) | ✅ |
| Encrypted cross-device favourites sync (Nostr, NIP-44) | ✅ |
| Search history | ✅ |
| Speedometer HUD with five styles | ✅ |
| Light + Dark themes — Nostr Violet & Bitcoin Orange, auto dark mode | ✅ |
| 27 languages (all EU official languages + RU, JA, ZH) | ✅ |
| Navigation notification in the Android shade | ✅ |
| Collaborative report corrections — owner-signed updates + third-party edit requests | ✅ |
| Activity inbox — zaps and confirmations received on your own reports | ✅ |
| Customisable vehicle cursor — styles, colours, animated walking mode | ✅ |
| Offline map rendering (MBTiles) | 🔜 Follow-up: routing packages are stable, full map rendering is next |

---

## Roadmap

### Next major change — complete offline maps

The 1.0.0 release already supports verified offline routing packages and saved routes. The next step is rendering downloaded
vector/MBTiles regions on-device with the same MapLibre experience as the online map; `main` stays shippable throughout.

### Public-transport coverage

Roadstr reads Transitous rather than maintaining a private timetable silo, so every compatible operator feed added upstream
becomes available to Roadstr without an app release. The practical contribution path — finding a stable GTFS/NeTEx URL,
adding the regional source, attaching realtime data and validating the pull request — is documented in
[Adding public-transport coverage](docs/TRANSIT_DATA.md).

---

## What is in this repository

```
android/app/src/main/kotlin/app/roadstr/   # the app (Kotlin, Jetpack Compose)
├── core/          # pure logic with no Android dependency: Nostr protocol (NIP-01/04/19/44/46/47/57/78, BIP-340),
│                  # routing and search protocols, navigation phrases, discovery (plain-language place search)
├── feature/       # one package per screen or flow: home, map, navigation (HUD, host), onboarding, place, profile,
│                  # report, route, saved, search, settings, transit, voice, web, wikipedia, activity
├── service/       # I/O: Nostr relays, remote signer (bunker), zaps and NWC, routing, search, location, hazards,
│                  # notifications and the navigation foreground service
├── storage/       # preferences, secrets, saved places, history, pending reports
├── migration/     # the reader of the old (Flutter) app's data, transactional and fail-closed
└── startup/       # the launcher activity, the one-time profile import, the guidance runtime holder
android/app/src/test/                      # JVM tests of all of the above (over 1,100 cases)
native-android/                            # the road-test app (same shell, own stores) and the optional GeckoView variant
lib/                                       # the Flutter code of 0.5.x, kept on this branch (see below)
test/                                      # Dart contract tests that pin the generated resources, the build and the docs
tools/kotlin_rewrite/                      # generators for the Android string resources and test fixtures, release audit
docs/                                      # plans, decisions, risks and release notes of the rewrite
```

**Why GitHub shows roughly half Dart and half Kotlin.** GitHub's language bar counts the bytes of every tracked source file
by language; it does not know which code ships. In 0.6.0 the app is Kotlin, but the repository still holds the whole Flutter
app (`lib/`, about 1.9 MB) because the old code is what reads the previous version's data on the first start, and it shares the
translation files; on top of that come 1.35 MB of generated Dart localisation classes, the Dart test suite and the Dart tools
that generate the Android resources and test fixtures. The Kotlin share is the app itself (about 2.2 MB) plus its tests (about 1
MB). Moving the Flutter app out of `main` would flip the bar; it stays for now because the update from 0.5.x needs it.

---

## Nostr protocols

| NIP | Purpose |
|---|---|
| NIP-01 | Base relay protocol (REQ / EVENT / EOSE / CLOSE) |
| NIP-04 | Legacy symmetric encryption for NWC pay requests, and for signers that only speak it |
| NIP-19 | Bech32 key encoding (npub) |
| NIP-44 | Versioned encryption (v2) — favourites sync, negotiated NWC and the remote-signer channel |
| NIP-46 | Nostr Connect — signer-initiated `bunker://` and client-initiated `nostrconnect://`, over persistent relay sockets |
| NIP-47 | Nostr Wallet Connect — pay Lightning invoices from any compatible wallet |
| NIP-55 | Android Signer Application — Amber integration |
| NIP-57 | Zap receipts — Lightning tips attached to kind-1315 road events |
| NIP-78 | Arbitrary app data (kind 30078) — encrypted favourites snapshot |
| kind-1315 | Road event report (police, speed camera, traffic jam, accident, hazard…) |
| kind-1316 | Road event confirmation / dismissal |
| kind-1317 | Correction to a report, signed by its original author — the only edit that is authoritative |
| kind-1318 | Correction *requested* by someone else; takes effect only once the author answers with a 1317 |

---

## Building

You need a JDK 17, the Android SDK (platform 36, build tools), the Android NDK `27.1.12297006` and the
[Flutter SDK](https://docs.flutter.dev/get-started/install): the build goes through Flutter because the APK carries the Flutter
engine that reads the old app's data once.

```bash
git clone https://github.com/roadstrapp/roadstr-app.git
cd roadstr-app
flutter pub get
flutter gen-l10n                 # the ARB files are the source of every translation

flutter build apk --release      # debug: flutter run
# or, only the Kotlin side and one ABI:
cd android && ./gradlew :app:assembleRelease -Ptarget-platform=android-arm64
```

The Kotlin launcher is the default. `-Pnative_launcher=false` builds the old Flutter launcher instead, to compare the two.

A signed release uses `android/key.properties` (copy `android/key.properties.template` and fill in the keystore); it is never
committed. `./build_release.sh` builds the four APKs (universal, arm64-v8a, armeabi-v7a, x86_64), checks package, version,
certificate, ABIs and 16 KB alignment, and writes the SHA-256 sums; it publishes nothing.

Tests:

```bash
flutter test                                   # Dart contract tests (resources, build, docs)
cd android && ./gradlew :app:testDebugUnitTest # Kotlin JVM tests
tools/kotlin_rewrite/audit_android_release.sh --source-only
```

After editing a translation, regenerate both the Dart classes and the Android resources:

```bash
flutter gen-l10n
for g in tools/kotlin_rewrite/generate_android_*_strings.dart; do dart run "$g"; done
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

OSRM is the default and requires no configuration. All providers support car, cycling
and walking profiles.

**Public transport** is routed separately, against a community-run service that
aggregates openly published timetables worldwide. It needs no account and no API key,
and it is queried only when the user explicitly asks for a public-transport journey.
Coverage depends on which operators publish open data for an area; where none is
published, the app reports that plainly rather than implying no service exists.

---

## Voice guidance — on-device AI

Roadstr ships optional on-device text-to-speech. Nothing is sent to a cloud API and no audio leaves the phone. Two engines are
used, because no single open model covers every language the app is translated into:

| Engine | Languages | Model |
|---|---|---|
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) | Italian, English, Spanish, French, Japanese, Chinese, Portuguese | ~82 MB, shared across all seven |
| [Piper](https://github.com/OHF-Voice/piper1-gpl) — Thorsten-Voice "Martin" | German | ~63 MB |

Both run with ONNX Runtime; phonemisation uses eSpeak NG through a JNI bridge compiled as an Android native library. The model
is downloaded on demand from **Settings → Navigation voice → Voice model**, checked against its size and SHA-256, and reused if
it is already on the phone (an update from 0.5.x keeps it). Kokoro offers a male or female voice and a 6-stage speed control;
French has only the female voice, German only Martin. Instructions are spoken as navigation guidance, so they are not drowned by
music, and a phrase in progress is finished when the trip ends. For languages neither engine covers, instructions stay as text.

The committed eSpeak libraries are reproducible from pinned upstream commits:

```bash
tools/build_espeak_android.sh
```

The script requires Android NDK `27.1.12297006`, removes host paths and debug symbols, and rebuilds all three supported ABIs plus
the compact language-data archive. Every Android build verifies their SHA-256 digests before `preBuild`.

---

## Themes

Four built-in themes, selectable from **Settings → Theme**:

| Theme | Description |
|---|---|
| Light · Nostr Violet | Default light theme, purple (#8B5CF6) accent |
| Light · Bitcoin Orange | Light theme, Bitcoin orange (#F7931A) accent |
| Dark · Nostr Violet | Dark theme, purple accent |
| Dark · Bitcoin Orange | Dark theme, Bitcoin orange accent |

The dark themes render the same OpenStreetMap tiles as the light ones, recoloured on
the device (lightness inverted, hue rotated back) rather than fetched from a separate
dark-tile service. CARTO's anonymous dark endpoint was used until 0.5.0, when it began
serving an "API KEY REQUIRED" watermark instead of tiles without warning — exactly the
kind of third-party dependency this app avoids elsewhere.

**Auto dark mode** (enabled by default) switches to the matching dark variant at local sunset and back at sunrise, calculated from the device's GPS position using the NOAA solar position algorithm. Toggleable from **Settings → Theme**.

---

## Lightning Network

Users can tip road-event reporters with Bitcoin over the Lightning Network:

1. Roadstr fetches the reporter's Lightning address (`lud16`) from their Nostr kind-0 profile.
2. It resolves the LNURL-pay endpoint and attaches a NIP-57 zap request (kind-9734) signed by the user's signer (Amber or bunker) if the user is logged in.
3. Payment is sent via **NWC (NIP-47)** if a wallet URI is configured: Roadstr verifies the wallet info event, prefers NIP-44 v2 and retains NIP-04 only for a verified legacy advertisement. Otherwise it falls back to a `lightning:` deep link that opens any installed Lightning wallet.
4. The LNURL server publishes a kind-9735 zap receipt to Nostr relays once the invoice is settled.

To connect a wallet, paste a `nostr+walletconnect://…` URI from a compatible wallet (Alby Hub, Mutiny, Cashu NWC) in **Settings → Lightning → Nostr Wallet Connect**.

## Place search in plain language

Roadstr understands searches such as "vegan restaurants near me", "pharmacy open now",
"petrol stations along the route" or "restaurants in <town>". The words are
read by a deterministic parser with one word list per language, with no model and no server of ours.
Seven languages (English, Italian, German, French, Spanish, Portuguese, Dutch) have a full word list;
the other twenty have a core of about nineteen categories, and English words work everywhere. None has
had a native-speaker review yet
([docs/world-discovery/LOCALIZATION_REVIEW.md](docs/world-discovery/LOCALIZATION_REVIEW.md)); when
nothing is recognised the ordinary search runs.

- **Where places come from.** OpenStreetMap, through Nominatim (the town, with a pause of 1.1 s between calls and no autocomplete) and Overpass (the places, one query plus at most one widening). Some searches find few places because few are tagged for them; the list says so.
- **Along the route.** "Along the route" searches the stretch still ahead, as one request. Places are ranked by the detour they cost and show how far ahead they are.
- **Web results (optional, off by default).** You can point Roadstr at a [SearXNG](https://docs.searxng.org/) instance you choose (*Settings → Search → Web results in search*); none is built in. It receives your search words, the name of your town for "near me" searches, your language and the safe-search level, never your coordinates, and it is asked only when you agree. Roadstr never asks for Google engines, but an instance you do not control may still use them behind the scenes. Results that match a place on the map are linked to it; a name that matches only by name is offered as "could this be…?" and never merged.
- **In-app browser (optional build variant).** By default a web result opens in your own browser. `native-android/gradlew-geckoview :app:assembleDebug` builds a variant that draws pages inside the app with [GeckoView](https://geckoview.dev/) in a private session that keeps nothing, denies every permission and pop-up, and can offer "navigate here" when a page states where a place is. It is about 130 MiB bigger (debug builds, one ABI), needs Android 8, has not been run on a device yet ([measurements](docs/world-discovery/MEASUREMENTS.md)), and `native-android/verify-geckoview-variant.sh` checks what must stay true of both builds.

The plan, the audit and every decision taken are in [docs/world-discovery/](docs/world-discovery/).

---

## Privacy

- **No accounts, no central servers, no telemetry/analytics SDKs** — the app talks directly to public Nostr relays, OSM tile/Overpass servers, and the chosen routing provider. Nothing is collected or sent to Roadstr itself.
- **GPS coordinates are read locally but are sent to third-party services as part of normal operation**: the routing provider (origin/destination), Overpass (periodic position pings during navigation, for live speed limits, speed cameras, traffic lights, pedestrian crossings, speed bumps, restricted zones and POI search), and Open-Meteo (for the weather row). None of these requests carry an account or persistent identifier — but if your threat model requires hiding your IP-linked location from those services, use a VPN (the onboarding flow suggests one). Saved favourites and the parking spot never leave the device unless you explicitly export or sync them.
- **Road events are pseudonymous** — published under the user's Nostr public key with no additional personal metadata, and every event received from a relay is signature-verified before being trusted (relays cannot forge reports under someone else's identity).
- **Favourites sync is end-to-end encrypted** (NIP-44) to the user's own key — relays storing the synced snapshot see only ciphertext.
- **The app never holds your Nostr private key.** You sign in with Amber or a remote signer (bunker), and signing happens there. The only secret Roadstr keeps for a bunker is its own pairing key, encrypted with the Android Keystore; a private key left by an earlier build is erased on the first start.
- **Web results are opt-in.** Off by default, with no instance built in. When you switch them on, the instance you chose receives your search words, the name of your town for "near me" searches, your language and the safe-search level, and the search engines it uses may see them too. Names found in the results may then be looked up on OpenStreetMap (a name and a town, at most three per search). The optional in-app browser runs pages in a private session, stores nothing after it closes, and asks for no permission on a page's behalf. To run your own instance, see [docs/world-discovery/SELF_HOSTING.md](docs/world-discovery/SELF_HOSTING.md).
- **Road reports are public and linkable** — publishing a report sends its exact coordinates, timestamp, content and Nostr public key to public relays. This is pseudonymous, not anonymous, and relay retention cannot be guaranteed.

---

## Localisation

Roadstr is fully localised in 27 languages covering all EU official languages plus Russian, Japanese, and Chinese:

`bg` `cs` `da` `de` `el` `en` `es` `et` `fi` `fr` `ga` `hr` `hu` `it` `ja` `lt` `lv` `mt` `nl` `pl` `pt` `ro` `ru` `sk` `sl` `sv` `zh`

Translations live in `lib/l10n/app_<locale>.arb` and feed both the Dart classes and the Android string resources (see [Building](#building) for the commands). A test fails if any language lacks a key of the English template or changes its placeholders. Texts that exist only in the Kotlin app (the start-up screens, the bunker login) live in `tools/kotlin_rewrite/native_recovery_translations.dart`.

---

## Troubleshooting

**The update says "protected data unavailable" or "could not bring your data over"**  
Nothing was erased. Restart the phone and choose *Try again*; *Continue without importing* starts empty and leaves the old
data on the phone untouched.

**GPS unavailable**  
The app requests location permission on first launch. If denied, go to *Settings → Apps → Roadstr → Permissions → Location* and
grant **Precise** location. Roadstr uses a foreground navigation service and does not need permanent background-location access.

**Navigation stops when the screen turns off**  
It should not. Allow notifications for Roadstr (the trip is held by a foreground service with a notification), and check that
battery optimisation is not set to restrict the app.

**Map tiles don't load**  
Check your internet connection. Tiles are fetched at runtime from OpenStreetMap; the dark themes recolour those same tiles on the device.

**Amber shows "invalid request"**  
Ensure your version of Amber supports NIP-55. Roadstr uses the `get_public_key`, `sign_event` and NIP-44 methods.

**A bunker login says the bunker did not answer**  
For a pasted link, check that it starts with `bunker://` and names at least one `wss://` relay. Alternatively choose
*Open signer app* and approve Roadstr's `nostrconnect://` offer. Ensure the signer is online and approve the request there;
public relays can still rate-limit or be temporarily unavailable.

**Voice guidance not working**  
Download the voice models from *Settings → Navigation voice → Voice model*. Voice is available for Italian, English, Spanish,
French, Japanese, Chinese, Portuguese and German; other languages keep text instructions on screen.

---

## Contributing

Pull requests are welcome. For significant changes please open an issue first to discuss the proposal.

Code style:
- All doc-comments and inline comments in **English**.
- Complex algorithms should have numbered step comments.
- Nostr protocol references should cite the relevant NIP.
- Fixes for the Flutter 0.5.x line go to `flutter-maintenance`.

---

## Licence

[MIT](LICENSE) — free to use, study, modify, redistribute and sell, as long as the
copyright and licence notice stay with the copies. Roadstr is fully open source.

Third-party components keep their own licences, listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). In particular the on-device voice
bundles eSpeak NG (GPL-3.0-or-later), so an APK that includes it has to honour the
GPL for that component.

---

## Acknowledgements

- [OpenStreetMap](https://www.openstreetmap.org/) contributors for map data
- [OSRM](https://project-osrm.org/) for the open-source routing engine
- [Nominatim](https://nominatim.org/) for geocoding
- [Overpass API](https://overpass-api.de/) and its public mirrors for speed limits, speed cameras and POI queries
- [Open-Meteo](https://open-meteo.com/) for weather data, free and API-key-free
- [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) for the on-device TTS model
- [Piper](https://github.com/OHF-Voice/piper1-gpl) and [Thorsten-Voice](https://www.thorsten-voice.de/) for the German on-device voice
- [MapLibre](https://maplibre.org/) for the vector rendering engine (MapLibre Native on Android)
- [Photon](https://photon.komoot.io/) for typo-tolerant geocoding
- [SearXNG](https://docs.searxng.org/) for the optional, self-chosen web results
- [GeckoView](https://geckoview.dev/) by Mozilla for the optional in-app browser
- [eSpeak NG](https://github.com/espeak-ng/espeak-ng) for phonemisation
- [Nostr protocol](https://nostr.com/) and all NIP authors
- [Jetpack Compose](https://developer.android.com/jetpack/compose), [OkHttp](https://square.github.io/okhttp/), [ONNX Runtime](https://onnxruntime.ai/) and [Bouncy Castle](https://www.bouncycastle.org/) for the native app
- [flutter_map](https://pub.dev/packages/flutter_map) and Flutter, for the 0.5.x line
- [Amber](https://github.com/greenart7c3/Amber) for the Android Nostr signer
