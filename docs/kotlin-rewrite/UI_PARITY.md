# UI parity catalogue

Jetpack Compose is an implementation detail. The migration target is the
existing Roadstr product, including its screen hierarchy, labels, defaults,
layout intent and map overlays.

## Reference states to capture before native replacement

| State | Flutter reference | Required comparison |
|---|---|---|
| Home/map idle | `MapScreen` and `MapLibreMapScreen` | layout, map chrome, dashboard, FABs and cursor |
| Active search | search panel in both map screens | focus, keyboard, provider progress, history |
| Search results/place | search and place panels | ranking, cards, details, opening hours and actions |
| Route preview | route panels/planner | alternatives, stops, provider labels, avoidance controls |
| Active car navigation | map screens + nav widgets | camera, HUD, manoeuvre, route progress and voice state |
| Walking/cycling navigation | map screens + cursor painter | mode-specific cursor/phrases and controls |
| Public transport | transit service/widget | itinerary cards, legs and mode labels |
| Road event detail/report | road event sheets | privacy acknowledgment, fields, signing and zap action |
| Settings | `settings_screen.dart` | section order, names, defaults, all legacy settings |
| Profile | `profile_screen.dart` | Amber/nsec state, visibility, reports and balance |
| Inbox | `notifications_screen.dart` | persisted read state and counts |
| Onboarding | `onboarding_screen.dart` | disclosure, permissions, identity and voice steps |
| Themes | `app_theme.dart` | Violet/Bitcoin Orange, light/dark and auto-dark transition |
| Orientation | portrait and both allowed landscapes | proportions, edge-to-edge and map usability |
| Accessibility | semantics and compact devices | labels, touch targets, scaling and no clipped content |

The current repository has four widget golden assets for cursor/speedometer/
maneuver styles and 27 ARB locale sets. Native screenshot/golden coverage must
be added before claiming parity; the existing Flutter goldens are references,
not proof of Compose rendering.

## Native shell boundary

The packaged native shell is intentionally only an integration foundation. Its
activity is non-exported, excluded from recents and has no intent filter;
Flutter `MainActivity` remains the sole launcher. The shell establishes
edge-to-edge Compose hosting and exact light/dark Nostr Violet/Bitcoin Orange
color tokens, including legacy stored-theme aliases. It also embeds a real
MapLibre Native raster view with the Flutter initial camera and texture mode.
Only an explicit internal invocation can start its admitted HTTPS OSM tile
requests; it does not read legacy/native storage or request location. The shell
also installs the Flutter-compatible active/ZTL/completed route sources and
layers plus muted route-choice alternatives through a revision-safe StateFlow
route session with an intentionally empty snapshot. A dormant camera session can also issue sequence-safe
move/ease commands and detach follow on MapLibre gestures, but receives no GPS
fixes. A separate dormant cursor session can project the generic 48x76 Flutter
MapLibre arrow, including its pitch-aware shadow, but receives no position and
therefore draws nothing. This proves renderer integration, not visual route, cursor or camera parity: it
does not yet claim screen, navigation, locale, accessibility, live-overlay or
screenshot/device parity.

## Non-negotiable UX invariants

- No “Roadstr 2” information architecture or settings rename.
- Preserve the selectable legacy raster map-engine preference.
- Preserve all 27 locale contents, placeholders, plurals, line breaks and
  device/system locale fallback behavior.
- Preserve light/dark and Nostr Violet/Bitcoin Orange choices, auto-dark
  sunrise/sunset semantics, map camera easing, tilt, bearing and HUD behavior.
- Preserve onboarding/disclosure state across an update unless the disclosure
  version genuinely changes.
- Preserve Amber's external signer UX and never expose/copy an Amber private key.
- Keep navigation state outside the Activity so rotation, backgrounding and
  process recreation do not silently terminate an active trip.

## Parity evidence format

For every major state, store the Flutter reference conditions, device/display
configuration, locale/theme, input data and native result. Record intentional
pixel differences caused by platform rendering separately from behavioral
differences. A visual improvement is a later, separate change; it is not part
of the parity milestone.
