# Changelog

All notable Roadstr changes are documented here. Roadstr 1.0.0 is the first stable release of the Kotlin rewrite.

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

- Full offline map rendering from downloaded MBTiles remains a follow-up to this release.
- Public transport, some provider integrations, real bunker/NWC signing exercises and native-speaker translation review remain
  documented in the risk and feature-parity reports.

## 0.6.0 — Kotlin rewrite milestone

The 0.6.0 milestone introduced the production Kotlin launcher, migration bridge and device-tested native navigation. The complete
engineering record is preserved in `docs/kotlin-rewrite/`, including the parity matrix, migration plan, risks and release tooling.

## 0.5.x and earlier

The Flutter line remains available on the `flutter-maintenance` branch. Its historical tags and release notes are retained in Git
for users who need the rollback path.
