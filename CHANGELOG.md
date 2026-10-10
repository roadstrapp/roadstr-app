# Changelog

All notable Roadstr changes are documented here. Roadstr 1.0.0 is the first stable release of the Kotlin rewrite.

## Unreleased

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
