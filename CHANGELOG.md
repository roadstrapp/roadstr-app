# Changelog

All notable Roadstr changes are documented here. Roadstr 1.0.0 is the first stable release of the Kotlin rewrite.

## [Unreleased]

### Changed

- **One road-event format, shared with Roadbeacon.** Reports and votes follow
  [nips/road-events.md](https://github.com/jooray/roadbeacon/blob/main/nips/road-events.md): six-decimal coordinates, no `alt`,
  two-tag votes, and an `expiration` of `created_at + max(14 days, lifetime)`. Roadstr's own reports already had that layout, so
  each report is now written once: the optional second copy in the *jooray/roadstr* layout and its settings are gone, and reports in
  that deprecated layout are no longer shown.
- **New MeshCore frame.** A report is 117 bytes plus its comment (at most 46 bytes are written, so a frame never exceeds the 163
  bytes a companion link carries; up to 48 are read), a vote is a self-contained 134 bytes. The codec runs the shared conformance
  vectors in `docs/interoperability/vectors/road-events-v1.json`. The old 110-byte frame is not accepted.
- Own reports and votes can go on the radio directly ("Send my reports by radio"); they no longer depend on the compatible copy.
- **Votes now decide how long every report stays on the map**, not only the ones heard over the radio. A "still there" keeps a
  report alive for its category's lifetime counted from the vote (a police report confirmed at hour five is still shown), a "no longer
  there" ends it at the time of the vote, only a key's latest vote counts, and a report is hidden while the last five minutes hold
  more denials than confirmations. Nothing is shown past the report's `expiration`. A report older than its category's lifetime is now
  kept while relays still serve it, so the votes that arrive afterwards can bring it back.

### Added

- **MeshCore Open as the radio link (opt-in).** Instead of taking the radio's only Bluetooth connection, Roadstr can use it through
  MeshCore Open's local API on the same phone (`ws://127.0.0.1:8787/api/v1`), so the chat app keeps working. The pairing key lives in
  the Android Keystore; Settings shows its fingerprint to compare with MeshCore Open's dialog. What the radio heard while Roadstr was
  closed is replayed from where it left off. Bluetooth stays the default, and a saved radio is never switched over.

### Fixed

- Votes on reports heard over the radio were always rejected, because the vote was expected to repeat the report's position.
- A 30-day report heard over the radio (speed camera, police station, surveillance camera) disappeared after 14 days.
- A vote heard on the radio before its report waits up to 15 minutes for it, from the radio or a relay, instead of being lost.
- A report sent again by a relay (relay rotation, area replay) no longer wipes its votes.
- Downloadable maps: Czechia could be selected but every one of its downloads failed, because Geofabrik publishes the Shortbread
  extract for the whole country and not for its regions. Of the 222 European areas offered, 89 had no file; the list of areas that
  really have one is now checked into the app (`tools/offline_packages/probe_geofabrik_shortbread.py`), a region without its own
  file downloads the area that contains it, and Czechia and Slovakia are listed under Eastern Europe.
- Downloadable maps: a city is placed by its area's outline, not its bounding box, so Northern Irish towns no longer point at England's file.
- The turn-by-turn card no longer covers the list of downloadable maps while navigating.
- The road-test build remembers the MeshCore switches across launches.

## [1.2.3] — 2026-10-25

New artwork for the Journey badges and a solid, turning coin.

### Changed

- All Journey badges have new artwork.
- A won coin is now drawn as a short gold cylinder: its body is built from depth slices and its side is reeded, so it keeps its
  thickness while it turns, down to the moment it is edge-on. This replaces the separate edge image used in 1.2.2.

## [1.2.2] — 2026-10-25

The speed-limit sign works again, the screen no longer defaults to full brightness, and road reports have proper artwork.

### Fixed

- **Speed-limit sign stuck on "–".** The limit came only from the public Overpass servers, and they were all unavailable at once
  (overpass-api.de at capacity, the openstreetmap.fr instance restricted to white-listed users, the others unreachable). A refusal was
  also read as "this road has no limit", so the app never moved on to another server. The limit is now read first from the Valhalla
  server at openstreetmap.de, the one used for routing (the posted `maxspeed` of the road under the fix, already in km/h), and Overpass is
  only a fallback. A refused or failed answer now rotates to the next server. Roads without a posted limit, roads with no limit at all and
  ways that disagree still show no limit rather than a guess, and service roads and parking aisles are ignored.
- The mail.ru Overpass instance is never asked for the speed limit: it would receive your position every 100 m while driving.

### Changed

- **Screen brightness defaults to 75%.** It used to be off, which left the screen to the system and let automatic brightness drive it to
  full in daylight. The slider moves in 5% steps so the default is one of its positions. Whatever you had already chosen is kept.
- Road reports are drawn with illustrations instead of emoji: map pins, report details, the category picker and the route panels. The
  Journey entry in Settings and Profile has new artwork, and a won coin now turns with a real edge instead of flattening into a line.

### Notes

- The speed limit is only looked up while a trip is under way. Not yet verified on the road: the lookup was checked against the live
  servers (Rome 50, London 32 for 20 mph, Florence 30) but not from a phone.
- Other features that rely on Overpass (OpenStreetMap speed cameras, ZTL, hazards, place search) still depend on those servers being up.

## [1.2.1] — 2026-10-22

Surveillance cameras can be reported, and the spoken start and arrival announcements now come in the right order.

### Reports

- Added a **Flock Camera** report category (📹; *Telecamere di sorveglianza* in Italian, translated into all 27 languages) for fixed
  surveillance cameras. Like speed cameras and police stations it stays on the map for 30 days. It is published to Nostr relays only:
  the open *jooray/roadstr* format has no equivalent type, so it is never sent over the MeshCore radio and never approximated as
  *other*.
- **Other** is no longer offered when you file a report. Reports that already carry it, from older versions or from other apps, are
  still read and drawn as before, and an older Roadstr shows the new category as *Other*.

### Voice

- Fixed the start of a trip being announced in the wrong order: the first instruction was spoken and only then *Let's go*. A departure
  that arrives while another phrase has barely begun now takes its place, and the displaced phrase is spoken right after it.
- Fixed *you have arrived* being heard twice. The last maneuver, which leads to the destination, was worded exactly like the arrival
  itself. It is now future tense in all 27 languages, the same wording the on-screen banner already used (*In 300 metres you will arrive at
  your destination*), and the arrival keeps the past tense. In Italian the arrival now reads *Sei giunto a destinazione* (the bundled
  audio was regenerated for both voices).
- **Not yet verified on the road:** both voice fixes are covered by unit tests of the speech queue and the phrase tables, but the
  regenerated Italian audio and the order of the announcements have not been heard on a phone.

### Notes

- The English label *Flock Camera* follows the name of a well-known manufacturer; the other 25 translations of the new category have
  not been reviewed by native speakers.

## [1.2.0] — 2026-10-18

Explore, opt-in Journey achievements, and an offline speech recogniser for voice search.

### Explore

- Added **Explore**: browse the places around an area you choose, by category (food and drink, culture, shopping, outdoors,
  services, transport, health, sport, lodging). Results from the different providers are de-duplicated, a failing provider falls
  back to the next one, and being offline or having every provider fail is reported instead of showing an empty list.

### Journey and achievements

- Added **Journey**, off by default: with tracking switched on, Roadstr records the distance you travel by each mode of transport,
  counts only distance it can defend (fixes with poor accuracy, jumps the chosen mode of transport could not make, gaps in the
  signal and standing still are left out), and keeps trips, monthly and yearly totals and the last session on the phone. Seventeen achievements unlock from the validated distance.
- Replaced the placeholder vector achievement marks with seventeen individually illustrated gold reward coins. Locked rewards stay
  muted; tapping a reward opens its details, while unlocked coins rotate in 3D with animated highlights.
- **Journey sync** is a second, separate opt-in. It stores your history as encrypted NIP-78 events (NIP-44, to your own key), one
  shard per device, with a signed manifest of exact chunk hashes so that one phone cannot overwrite another and a partial relay
  answer is never taken for a complete one. If the signer cannot do NIP-44, sync stops and keeps the local data; there is no
  cleartext fallback. Deleting local history cannot delete what relays already hold, and the screen says so.
- Added a **NIP-58 client**: strict parsing and building of badge definitions (30009), awards (8), profile badges (10008) and badge
  sets (30008). Roadstr does **not** issue official badges: no issuer key or badge host exists, so *Show on Nostr profile* stays
  unavailable until a build configures one, and local achievements work regardless. See [docs/journey-nostr.md](docs/journey-nostr.md).

### Offline voice search

- The microphone button in search can now recognise speech **on the phone** with NVIDIA Parakeet TDT 0.6B v3, run by an
  embedded engine (transcribe.cpp / ggml): 25 European languages including Italian, no internet and no Google or other speech
  service while you talk. It is off until you tap **Download the model** in *Settings → Voice search* (485 MB, from
  `huggingface.co`, resumable, size and SHA-256 checked before the file is accepted, deletable at any time).
- Until the model is installed, on devices that cannot run it (anything but 64-bit Arm with at least 3 GB of RAM), for languages it
  does not know, or if anything goes wrong while listening, the microphone button uses the phone's own recogniser exactly as before.
- **Addresses spoken aloud are repaired before they are searched.** Italian number words left in an address become digits
  (*via d'arsena diciassette* → *Via Darsena 17*), and street names spelled by sound are matched, without regard to spaces,
  apostrophes or doubled letters, against the streets around you and replaced by the map's spelling when one is clearly closest
  (*via chiavi caromea* → *Via Chiavica Romea*). Names that hold a number (*Piazza dei Tre Martiri*, *Via Venti Settembre*) are not
  touched. The street list comes from Overpass, once a month per area, asked for the centre of a cell about 5 km across and
  never for your position or your words; without it, only the numbers are fixed. Settings says so, in all 27 languages.
- Roadstr listens until you stop talking (or you tap *Done*), loads the model while you speak so there is no wait afterwards, and
  frees its memory (about a gigabyte) a minute after the last use or when Android runs short. The microphone permission is asked
  the first time, only for this, and the microphone is open only while the listening card is on screen.
- The engine lives in its own repository, [`roadstrapp/offline-speech-to-text`](https://github.com/roadstrapp/offline-speech-to-text)
  (a copy of *Offline Speech to Text* by Noah Mühl, MIT, with `engine/` added), and is vendored here as source plus binaries that
  can be rebuilt byte for byte (`third_party/offline-stt-engine/rebuild.sh`); the Gradle build refuses to start if any of them
  differs from its recorded SHA-256. The model is CC-BY-4.0 and its attribution is shown in Settings.
- **Not yet verified on a phone:** speed, memory and battery are unmeasured, and recognition quality was only checked on synthetic
  speech (it got street names such as *Via Giuseppe Garibaldi* and *Corso Vittorio Emanuele II* right, and slipped on common words:
  *benzina* came out as *benzena*, *portami a casa* as *porta-me a casa*). The voice-search texts are translated into all 27 languages
  (Irish and Maltese want a native speaker's review).
  See [docs/offline-voice-search.md](docs/offline-voice-search.md).

### Build

- Kotlin 2.2.20, and the plugins prepared for Android's built-in Kotlin.
- The 3 GB RAM / arm64 gate, the `RECORD_AUDIO` permission (optional hardware, declared by the app only) and the engine's checksums
  are guarded by `test/native_offline_speech_contract_test.dart`.
- Added the reusable engine fork as a pinned submodule. Its pristine upstream mirror is refreshed automatically, while engine
  updates remain reviewable pull requests and must pass the Rust test suite.

### Interface polish

- Restored notification overlays to the bottom safe area and kept Journey sheets clear of the system status bar.
- Made actions use the selected Nostr-purple or Bitcoin-orange theme accent consistently, including support, saved-place and Nostr
  actions, and gave the Journey launch action a distinct animated reward treatment.
- Clarified the two different search clear actions, added the microphone entry point, and replaced flat screen flashes with softer
  slide, scale and fade transitions.

## [1.1.0] — 2026-10-13

Public-transport guidance, road-report interoperability with the open *jooray/roadstr* format, and an experimental MeshCore
radio.

### Public transport

- Public transport is a choice in the route planner again: *Transit* now sits next to car, bike and walking. It had gone missing
  from the planner, so no public-transport itinerary could be requested from it.
- Added active public-transport guidance through walking, boarding, onboard, interchange and final-walk stages, with a dedicated
  HUD, de-duplicated voice cues, GPS assistance, manual boarded / alighted / missed-service actions, a foreground notification and
  encrypted recovery of the session if Android kills the process.
- Added provider-backed stop search, nearby stops, live departure boards, the lines serving a stop and trip details. A missed
  connection can be assessed, refreshed and replanned, and the active journey is replaced only after the user confirms.
- Added saved public-transport journeys in their own encrypted, versioned format, with a per-leg comparison of the original and
  the updated plan. A saved journey stays readable offline, labelled as a snapshot until it is verified online; offline transit
  routing itself is deliberately not part of Roadstr.
- Added Entur as a second, coverage-aware online provider for journeys wholly inside Norway (parameterised GraphQL requests,
  provider-scoped IDs, partial-error handling). It is not a worldwide fallback and offers neither stop search nor departure boards.
- Added readable names for the vehicle categories providers report (tram, metro, regional and high-speed trains, coach, ferry…).
- The onboarding disclosure and every live itinerary panel now say that transit searches, planning and live checks send the query,
  coordinates and travel time to Transitous or Entur, which also see the requesting IP address.

### Interoperability with the *jooray/roadstr* road-report format

- Reports in that layout (Nostr kinds 1315/1316) are now recognised by their tags instead of being read as Roadstr's own. They keep
  the lifetime that format gives each type (the 14-day `expiration` is only what relays keep), confirmations extend it and denials
  cut it short, and the details sheet says where a report came from. Roadstr's own reports and their lifetimes are unchanged.
  *Settings → Interoperability* can hide the compatible reports.
- Added a codec for the compact 110/134-byte form of those events. It rebuilds the exact signed event (same ID, key and signature)
  or refuses to encode: integer-only coordinates, no locale, golden vectors generated by an independent Python implementation
  (`tools/interop/generate_vectors.py`) and cross-checked with a third library.
- Reads, but never writes, the two defects found in the reference client's output: coordinates truncated by one unit and a decimal
  comma written by the device locale.
- Optional *Also publish in the compatible format*: each report is also sent as a second, separately signed event in that layout
  (account key or a one-time key) and shown as one marker with the native report. A vote on a report in that layout is written in
  that layout too. Off by default.

### MeshCore radio (experimental)

- Added MeshCore LoRa support, off by default: a companion-protocol client, an Android Bluetooth LE link, receive and send over a
  shared `#roadstr` channel with a development `data_type`, and optional bridges between the radio and the Nostr relays that never
  change a report's author. The bridges have a duplicate ledger, rate limits, an area limit and a bounded, encrypted, persistent
  queue; transmitting or bridging asks for explicit consent first.
- The Bluetooth permissions are requested only when the user pairs a radio, never at onboarding.
- Specification, test vectors and the hardware checklist: [docs/interoperability/MESHCORE_NOSTR.md](docs/interoperability/MESHCORE_NOSTR.md).

### Navigation fixes

- The report sheet opened from the *Report* button is no longer drawn underneath the speed and route panel, so a report can be
  published again while navigating.
- The speed figure is centred on the speedometer dial in every style (it sat above the centre); the unit now hangs below it.

### Known limits

- MeshCore has been verified against a simulated radio only. The Android Bluetooth layer has not run on a device and no real
  MeshCore radio has been tested.
- Anything sent over the radio can be read by anyone in range: position, time and the public key that signed it. It is not
  anonymous.
- Older Roadstr versions show two markers when *Also publish in the compatible format* is on, because they cannot tell that the two
  copies are one report.
- Publishing the compatible copy to public relays has been tested against a simulated relay only; reading it was checked against
  real relays.
- The newest texts (interoperability, MeshCore and the latest transit additions) exist in English and Italian; the other 25 languages
  fall back to English until they are translated.
- Production-wide use of the public Transitous endpoint still needs coordination with its operators, and the TalkBack, large-font,
  landscape, screen-off voice and process-recovery checks of the transit flows remain part of the device matrix.

## [1.0.1] — 2026-10-11

- Added a live offline-map download progress card with determinate byte/percentage progress, an indeterminate preparation state,
  throttled UI updates and a visible cancel action for every provider and manifest package.
- Reworked Roadstr JSON manifest packages into geographic groups derived from coverage geometry, with case-insensitive search across
  country, region, city, package name, id and URL; large catalogues are rendered lazily and accept up to 2,000 bounded artifacts.
- Fixed localized download-progress format placeholders across all 27 Android resource sets and kept the manifest generator aligned
  with the Android parser limit.
- Settings: the card of optional map elements is now called *Map element overlays* (27 languages), starts closed, and also holds
  the altitude readout switch; the altitude readout stays off by default.
- Added a destination pin on the map and lighter navigation rendering: the vehicle cursor animation is drawn at its 30 fps
  source cadence instead of waking the UI at a 90/120 Hz refresh rate.
- Restored native ZTL/ZAC/limited-traffic warnings from the 0.5.x line: bounded Overpass loading, restricted-route colouring,
  live inside/nearby notices, official local acronyms where established, dismissal and navigation-progress integration.
- Added client-initiated NIP-46 login with a generated `nostrconnect://` offer for Amber and compatible signers, alongside the
  existing pasted `bunker://` flow.
- Kept NIP-46 relay WebSockets warm and multiplexed across signer requests, while closing each logical Nostr subscription and
  retaining signature, recipient, request-id and one-time-secret validation.
- Documented the upstream GTFS/NeTEx/GTFS-RT contribution path that expands Roadstr coverage through Transitous without an APK
  release.
- Restored public-transport routing in the Kotlin planner through the stable Transitous/MOTIS v6 API.
- Added provider-scoped transit identifiers, scheduled versus realtime times, platforms, intermediate stops, accessibility,
  cancellations and service alerts without fabricating missing values.
- Added capability-aware provider routing with bounded retries, visible Transitous attribution and a live standalone Kotlin
  journey gateway.
- Added expanded transit itinerary cards and MapLibre boarding/alighting markers, keeping walking, bus, tram, metro, rail,
  ferry and other provider-supported modes in one multimodal model.
- Added deterministic Kotlin/Dart coverage for transit parsing, realtime metadata, midnight/timezone handling, provider fallback,
  planner handoff and map projection; verified one controlled live Milan journey against Transitous.

## [1.0.0] — 2026-10-10

🎉 **The Kotlin rewrite is stable!** 🎉

This release carries the work started during the 0.5.x to Kotlin migration and makes the native Android app the supported
Roadstr experience. It keeps the production package name `app.roadstr`, uses the owner's release certificate, and is designed
to update an existing 0.5.x installation in place.

### Kotlin app and migration

- Replaced the Flutter launcher with a Jetpack Compose and MapLibre Native application while retaining the Flutter reader needed
  to import data from 0.5.x.
- Added fail-closed, transactional migration for Amber identity/profile data, saved places, parking spot, settings, search
  history, pending reports, wallet/routing configuration and favourites-sync state.
- Preserved encrypted storage boundaries, secure-store aliases and recovery presentation; migration never deletes the legacy data.
- Added Amber (NIP-55) and bunker (NIP-46) signing flows without importing private keys into Roadstr.
- Delivered 27-language native resources, onboarding/disclaimer/recovery UX, themes, settings, voice model ownership and app
  version/about presentation.

### Navigation and live road experience

- Added the native map-first journey: GPS cursor/follow camera, search, place cards, route planner, alternatives, preview and
  active-trip HUD.
- Added OSRM routing, alternatives, direction-aware rerouting, route progress, arrival handling and background navigation.
- Kept trips alive through rotation, task removal and screen-off use with a foreground location service, notifications, wakelock
  and voice guidance.
- Added Nominatim, Photon and Overpass search, contextual place details, weather, OSM speed cameras and road-element overlays.
- Added Nostr road reports, confirmations, corrections, activity inbox, relay polling, Lightning/NWC integrations and sync flows.

### Saved routes and offline routing

- Added versioned saved-route models and encrypted persistence for route plans, destinations, waypoints and metadata.
- Added saved-route UI and offline package controls with bounded validation and transactional state updates.
- Added a verified offline package pipeline and opt-in on-device Valhalla routing engine, including package/catalogue checks and
  routing-data-only package selection.

### Offline maps

- Added a hierarchical worldwide map catalogue backed by the live [Geofabrik index](https://download.geofabrik.de/index-v1.json),
  with bundled GeoNames coverage for 34,156 cities and 3,865 administrative areas.
- Added provider presets for Geofabrik, VersaTiles, OpenMapTiles and Roadstr JSON, with the custom HTTPS manifest field moved into
  an explained advanced section for self-hosted catalogues.
- Added verified Shortbread MBTiles downloads with size, validator and checksum checks, persistent installation, attribution and
  local MapLibre rendering through `mbtiles://` sources.
- Added soft directional catalogue transitions: forward navigation slides right and backward navigation slides left.
- Fixed replacement of stale catalogue caches and Android asset packaging so the live catalogue and bundled geographic data work
  reliably in release builds.

### External destinations

- Added a strict JSON destination contract for Tankful and other trusted callers:
  `{"label":"…","lat":0.0,"lon":0.0}` via an explicit `ACTION_SEND` intent.
- Supports cold-start and single-top delivery, bounded payloads, finite coordinate validation, duplicate/unknown-field rejection,
  latest-wins handoff and one-time consumption diagnostics without logging destination contents.
- Tankful now targets the production package `app.roadstr`; the release launcher accepts the destination and opens route planning.

### Quality and release engineering

- Added extensive JVM, Dart contract and device-level checks for migration, storage, routing, navigation, intents and release
  compatibility.
- Added release-source and signed-artifact auditors covering package identity, version agreement, label, ABI outputs, hardening,
  checksum and release-certificate verification.
- Verified the signed update path, onboarding, JSON destination flow, cold/single-top launches and the production app label on a
  Pixel 10 running Android 17.

### Known follow-ups

- Public transport, some additional provider integrations, real bunker/NWC signing exercises and native-speaker translation review
  remain documented in the risk and feature-parity reports.

## 0.6.0 — Kotlin rewrite milestone

The 0.6.0 milestone introduced the production Kotlin launcher, migration bridge and device-tested native navigation. The complete
engineering record is preserved in `docs/kotlin-rewrite/`, including the parity matrix, migration plan, risks and release tooling.

## 0.5.x and earlier

The Flutter line remains available on the `flutter-maintenance` branch. Its historical tags and release notes are retained in Git
for users who need the rollback path.
