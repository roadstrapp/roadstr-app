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
Flutter `MainActivity` remains the sole launcher of the production package.
The separate `native-android/` road-test build gives the same shell a direct
Compose launcher under `app.roadstr.roadtest`, with no Flutter runtime in its
Gradle graph or APK. It owns no user data, but now owns a narrowly scoped
foreground AOSP GPS feed plus a credential-free, bounded live journey gateway:
Nominatim/Photon/Overpass search feeds destination selection, and OSRM driving,
cycling or walking alternatives feed the shared MapLibre route overlay. Queries
and coordinates remain memory-only. Starting a previewed route now activates a
foreground, value-only guidance owner that projects GPS progress into the HUD,
active/completed route geometry and navigation camera; stop/back returns to
free drive. It remains an integration harness rather than a production fork.
The shell establishes
edge-to-edge Compose hosting and exact light/dark Nostr Violet/Bitcoin Orange
color tokens, including legacy stored-theme aliases. It also embeds a real
MapLibre Native raster view with the Flutter initial camera and texture mode.
The host also packages the old `mapEngine=osm` behavior as a typed top-down
raster profile with its exact zoom-6 start and 2–19 limits; both choices share
the validated custom tile source and overlay stack. The shell still selects
`maplibre` explicitly and does not read the migrated preference.
Only an explicit road-test interaction can start its admitted HTTPS OSM tile,
search or routing requests. The shared shell does not select endpoints, read
legacy/native storage or instantiate location APIs; the separate road-test
Activity requests foreground coarse/fine permission, injects value-only fixes
and supplies the provider-neutral journey gateway. The shell
also installs the Flutter-compatible active/ZTL/completed route sources and
layers plus muted route-choice alternatives and a bright-red 400 m traffic
layer through a revision-safe StateFlow
route session with an intentionally empty snapshot. The camera session issues
sequence-safe move/ease commands, detaches follow on MapLibre gestures and, in
the road-test APK, follows injected live GPS with explicit recentering. The
cursor session projects those fixes as the generic 48x76 Flutter MapLibre arrow,
including its pitch-aware shadow. The production canary still injects no fix.
A bounded dormant point-overlay session also preserves
the Flutter marker catalogue, billboard sizes, ordering and zoom gates without
connecting service caches. Native click and long-click listeners now emit typed
map interactions, projected road-event hit tests match the Flutter marker
rectangles, and ordinary taps can select a dormant route preview with the same
strict 60 m rule. A first product-panel slice now packages the public-transport
itinerary sheet: bounded loading/ready/no-service/failure states, eight cards,
selection synchronized with the map session, operator colors, boarding/walking
details, mode choices, navigation-bar insets and explicit pane/button/selection
semantics. Its 15 visible values are generated byte-for-byte from every one of
the 27 Flutter ARB files. The next slice packages the active-search overlay:
query/loading/result states, matched favorites, recent history, metric/imperial
distance, GPS-disabled nearby controls and all 11 nearby categories in Flutter
order. Its 19 values are generated from the same 27 ARB files. A third slice
packages the place-detail sheet with bounded OSM content, opening-hours state,
safe website/Wikipedia values, contextual parking/charging/fuel/lodging/food
sections and fixed cancel/navigation actions. Its 52 values are generated from
the same 27 ARB files. A fourth slice packages the full-screen navigation HUD:
all 21 manoeuvre families are drawn as native vectors, while five speedometer
styles and bounded speed, limit, altitude, distance, duration and ETA summaries
adapt between portrait and landscape. In landscape the speed-limit sign, the
street label and the four map buttons (compass, my location, report, add stop; a
row instead of a column) are placed from the measured height of the summary panel,
so nothing overlaps the next-step card or the guidance buttons; this was checked
on a Pixel 10 in both orientations (2026-10-07). Its 15 values are generated from the
same 27 ARB files. A fifth slice packages saved places and parking: bounded
legacy/import parsing, exact label merge, revision-safe list mutations, the
existing blue parking-marker projection and a settings-like sheet with add,
edit, delete, import/export and parking actions; the settings screen also lists the
saved places (tap to edit, × to delete) as the Flutter settings do. Its nine values are generated
from the same 27 ARB files. A sixth slice packages the restricted Wikipedia
reader with exact article-host/path admission, main-frame-only navigation,
progress/back/error/retry state and ephemeral browser data. Its four values are
generated from the same 27 ARB files. A seventh slice packages road-event
detail, privacy disclosure and report composition with the existing 14-category
wire/marker vocabulary, TTLs, relative age, owner suggestions, confirmations,
speed limits and typed zap/publish actions. Its 39 values are generated for the
same 27 locale sets, preserving Flutter's English/Italian privacy fallback. The
eighth slice packages the silent activity inbox with logged-out, empty and
ready states, zap/confirmation/dispute cards, unread state and localized device
timestamps. Its 12 values are generated for all 27 locale sets. A ninth slice
packages route planning, loading, alternative choice and pre-navigation preview
states with five ordered stops, four modes, exact avoidance badges, bounded
conditions and card/map selection synchronization. Its 22 values are generated
for all 27 locale sets. A tenth slice packages profile loading, disconnected,
own and remote states with Amber/nsec choices, strict npub projection,
pseudonymous metadata suppression, visibility, reputation, zap balance and a
bounded newest-first report list. Its 30 values are generated for all 27 locale
sets. An eleventh slice packages the complete settings hierarchy: four themes,
system plus 27 language choices, map and routing controls, five speedometers,
seven cursor styles and colors, search, Lightning/NWC, saved-place/sync, voice
and information actions. Scalar mutations are typed and revision-safe, text and
sliders are bounded, and secrets are represented only by configured booleans.
Its 118 values are generated for all 27 locale sets with the same English
fallback used by Flutter for newer missing ARB entries. A twelfth slice packages
the four swipeable onboarding pages, optional identity state, location and voice
setup, an undismissable current privacy disclosure, migration-in-progress and
protected-data recovery states. Its strict gate ignores legacy completion flags,
requires the exact `privacy_disclosure_v2 == true` value and emits the same three
completion writes as Flutter. Its 55 values are generated for all 27 locale
sets; the two recovery strings preserve Flutter's existing English-only text.
The thirteenth slice packages Flutter's map-first idle dashboard and persistent
Notifications/Profile/Menu bar. It preserves every workflow replacement gate,
the collapsed/expanded hierarchy, the five-visible-favourite ceiling, unread
`99+` badge and typed quick actions. Its ten values are generated for all 27
locale sets. The production canary renders only empty value projections and
does not feed identity, favourites, activity, parking or route state. The
road-test composition supplies foreground GPS plus the bounded search/routing
gateway; its selected route is the only live navigation input. The shared shell
still has no transit, saved-place, Wikipedia, road-event, activity, profile,
settings or onboarding provider/history/identity/signer/wallet/preferences/
secure-storage/migration owner, so it cannot launch an external product intent
or read user data. The road-test owner now performs bounded direction-aware
rerouting and true-distance/closest-approach arrival cleanup. No spoken output,
building-footprint arrival, repeated-deviation alternative panel, background
navigation, remote image, drag gesture or long-press product handler is connected. This
proves bounded UI integration slices, not visual route, marker, cursor,
full-screen, live-overlay or screenshot/device parity.

## Native public-transport panel evidence

- `generate_android_transit_strings.dart` projects the existing ARB values into
  27 Android resource sets and supports a no-drift `--check` mode.
- `NativeTransitPresenter` preserves compact duration/clock formatting,
  metric/imperial thresholds, operator/accent colors and scheduled-time cues.
- `NativeTransitJourneySession` fences provider revisions and updates UI and
  selected MapLibre geometry together without retaining provider errors.
- `NativeTransitItinerariesPanel` retains the card, boarding, leg-chain,
  empty/error/retry and walking/transit mode hierarchy with bounded scrolling.

Compose screenshot/golden, font-scale, TalkBack, compact portrait/landscape and
physical-device rendering evidence remain required before this state is marked
visually equivalent.

## Native search-overlay evidence

- `generate_android_search_strings.dart` projects 19 existing ARB values into
  27 Android resource sets and supports deterministic no-drift checks.
- `NativeSearchPresenter` validates and bounds provider/favorite/history rows,
  preserves Flutter title/category fallback and reuses exact unit formatting.
- `NativeSearchSession` fences query generations and partial/final outcomes,
  distinguishes silent typed empties from explicit nearby empties, and owns no
  provider, persistence or location adapter.
- `NativeSearchOverlay` retains the search field, keyboard action, nearby GPS
  gate, category order, favorites, history clearing, result distances and
  polite loading/empty accessibility announcements.

Search screenshot/golden, IME/focus behavior, font-scale, TalkBack, compact
portrait/landscape and live provider/storage/device evidence remain required.

## Native place-detail evidence

- `generate_android_place_strings.dart` projects 52 existing ARB values into
  27 Android resource sets and supports deterministic no-drift checks.
- `OsmPlaceDetailsProtocol` preserves Flutter's category order, localized
  names/descriptions, text/count bounds, safe HTTPS contacts and contextual
  parking, charging, fuel, lodging and food fields.
- `NativePlaceSession` fences stale loading callbacks, bounds article/address/
  search values and reprojects opening-hours transitions without owning a
  provider, storage adapter or intent launcher.
- `NativePlaceDetailsPanel` retains the header, opening state, OSM detail card,
  contextual chips/sections, safe typed-link callbacks and fixed actions with
  bounded scrolling, system-bar insets and explicit accessibility semantics.

Place screenshot/golden, real drag/animation, remote image policy, font-scale,
TalkBack, compact portrait/landscape, intent and live provider/device evidence
remain required.

## Native navigation-HUD evidence

- `generate_android_navigation_strings.dart` projects 15 existing ARB values
  into 27 Android resource sets and supports deterministic no-drift checks.
- `NativeNavigationHudPresenter` preserves live/fallback distance selection,
  the long-straight gate, all 21 manoeuvre families, bounded roundabout
  topology, arrival side, metric/imperial speed, limit, altitude, remaining
  distance, duration and ETA, plus all five persisted speedometer styles.
- `NativeNavigationHudSession` fences stale revisions and backward step
  progression without owning location, route, settings or storage adapters.
- `NativeActiveNavigationSession` maps each increasing foreground GPS fix onto
  monotonic route/step progress and updates the HUD plus active/completed
  MapLibre geometry as one revision-fenced operation. It also reuses the core
  off-route trend policy, emits one bounded reroute request, atomically adopts
  its replacement and ends on true destination distance/closest approach.
- `NativeManeuverSymbol` and `NativeNavigationHud` retain vector manoeuvres,
  current/next instruction hierarchy, summary/stop/settings/voice controls,
  portrait/landscape adaptation, system insets and explicit accessibility
  semantics.

Navigation screenshot/golden, font-scale, TalkBack, compact portrait/landscape,
persisted settings integration, building-footprint arrival, repeated-reroute
alternative choice, interaction safety, performance and physical-device
rendering evidence remain required.

## Native saved-place and parking evidence

- `generate_android_saved_places_strings.dart` projects nine existing ARB
  values into 27 Android resource sets and supports deterministic no-drift
  checks.
- `NativeSavedPlacesProtocol` preserves Flutter's list-of-JSON-strings Hive
  shape, direct import shape, 1,000-item and 5-MiB bounds, encrypted-envelope
  admission, exact label merge, WGS84 validation and `parking_position` codec.
- Saved parking projects to the existing native blue `Parking` marker instead
  of creating a second visual vocabulary.
- `FileNativeSavedPlacesStore` now imports those exact legacy shapes into a
  separately keyed AES-GCM stage/active/backup file, reopens every mutation and
  binds its ciphertext into migration completion without exposing labels,
  addresses or coordinates in public bytes.
- `NativeSavedPlacesSession` fences stale callbacks across add, edit, delete,
  import and parking changes without owning persistence, picker, sync, route or
  crypto adapters.
- `NativeSavedPlacesPanel` retains the saved-place list and conditional export,
  import, parking navigate/remove and accessible bounded scrolling hierarchy.

Saved-place screenshot/golden, add/edit dialogs, font-scale, TalkBack, real
file picker plus PBKDF2 export interoperability, live persistence ownership/
sync/map/routing and physical-device migration evidence remain required.

## Native Wikipedia-reader evidence

- `generate_android_wikipedia_strings.dart` projects four existing ARB values
  into 27 Android resource sets with deterministic no-drift checks.
- `NativeWikipediaUriPolicy` mirrors the Flutter HTTPS, language-subdomain,
  no-user-info/no-port and non-empty `/wiki/` article boundary and admits only
  main-frame navigation inside that boundary.
- `NativeWikipediaSession` fences stale page, progress, failure, retry and hide
  callbacks without owning a WebView, network client or intent launcher.
- `NativeWikipediaReader` disables JavaScript, file/content access, DOM/database
  storage, geolocation, mixed content, media autoplay, WebView permissions and
  file selection; SSL errors are cancelled and browser state is cleared.
- The explicit external action can receive only the original validated article,
  while the dormant shell supplies no action and never opens the session.

Wikipedia screenshot/golden, font-scale, TalkBack, real place selection,
external-browser intent, redirect/TLS integration and physical-device WebView
evidence remain required.

## Native road-event evidence

- `generate_android_road_event_strings.dart` projects 36 existing ARB values
  plus the exact three English/Italian Flutter privacy strings into 27 Android
  resource sets with deterministic no-drift checks.
- `NativeRoadEventPresenter` validates lowercase event/pubkey identity, WGS84
  coordinates, five-minute future skew, category and relay expiry, bounded
  counters, zap totals, speed limits, 500-character inbound comments and up to
  100 owner suggestions.
- All 14 `RoadCategoryWire` entries reuse their existing
  `NativeMapPointOverlayKind`, symbol and color; category TTLs remain owned by
  the shared Nostr protocol instead of being duplicated in UI code.
- Relative age thresholds, 200-character report comments, optional 1–300 speed
  input and positive-number Flutter mph-to-km/h rounding are preserved.
- `NativeRoadEventSession` fences detail/privacy/composer/submission revisions,
  prevents duplicate submission and owns no identity, signer, relay, queue,
  wallet, GPS or persistence adapter.
- `NativeRoadEventPanels` retains bounded scroll, category radio semantics,
  owner/visitor speed actions, suggestions, reporter, confirmation/dispute, zap,
  privacy and publish states through typed callbacks only.

Road-event screenshot/golden, edit dialog, font-scale, TalkBack, map selection,
privacy storage migration, profile fetch/image, Amber/nsec signing, relay/offline
queue, zap payment and physical-device evidence remain required.

## Native profile evidence

- `generate_android_profile_strings.dart` projects 30 existing profile and
  identity ARB values, including the reputation placeholder, into all 27
  Android resource sets with deterministic no-drift checking.
- `NativeProfilePresenter` admits only strict lowercase public/event keys,
  bounded text, HTTPS avatar references, sane timestamps/counters and at most
  500 fetched reports; it sorts and exposes at most 100 rows, preserves
  Flutter's score thresholds and millisatoshi flooring, and suppresses remote
  identity/activity data when pseudonymous mode is active.
- `NativeProfileSession` fences identity generations and Amber-waiting,
  visibility and close mutations without retaining nsec material or owning a
  signer, relay, wallet, reverse geocoder, image loader, clipboard or storage.
- `NativeProfilePanel` retains loading, disconnected, own and remote profile
  states, Amber/nsec choices, visibility, npub copy, reputation, zap balance,
  reports and disconnect actions through typed callbacks only.

Profile screenshot/golden, font-scale, TalkBack, protected-storage migration,
live profile/image and report fetches, clipboard, Amber/nsec signer, relay,
wallet and physical-device evidence remain required.

## Native activity-inbox evidence

- `generate_android_activity_strings.dart` projects the 12 existing inbox ARB
  values and integer/string placeholders into all 27 Android resource sets.
- `NativeActivityInboxProtocol` decodes the normalized legacy Hive list-of-maps,
  accepts only bounded verified event identities/timestamps/payloads, preserves
  all three row types and the Flutter unknown-category fallback, sorts newest
  first, deduplicates new events and caps storage at 100 rows.
- `NativeActivityCursorProtocol` preserves exact zap/confirmation per-pubkey
  key names, seeds first activation at now and advances only monotonically.
- `NativeActivityInboxSession` fences identity/revision changes and returns
  typed persistence writes for record/mark-read mutations without owning Hive,
  files, relays, sockets or Android notifications.
- `FileNativeActivityStore` consumes those typed semantics in a separate
  Keystore-backed AES-GCM file, atomically preserves multiple identities plus
  both cursor families and binds its exact ciphertext into migration marker v4;
  the panel and shell still do not construct it.
- `NativeActivityInboxPanel` retains logged-out, empty and ready states, unread
  accents, localized category/body/time presentation and bounded accessible
  scrolling; opening it requests mark-all-read through a callback only.

Activity-inbox screenshot/golden, font-scale, TalkBack, live verified relay subscriptions and
physical-device evidence remain required.

## Native route-planning evidence

- `generate_android_route_planning_strings.dart` projects the 22 existing
  planner/preview ARB values and placeholders into all 27 Android resource sets.
- `NativeRoutePlanningPresenter` validates bounded normalized routes, preserves
  Flutter duration/unit formatting and the fastest, highway/toll, unavoidable
  and unpaved badge priority, and caps preview conditions at three rows.
- `NativeRoutePlanningSession` fences request generations, preserves one to five
  stable ordered stops and four transport modes, and synchronizes card and
  strict-60-metre map selection through `NativeRouteOverlaySession`.
- `NativeRoutePlanningPanel` retains planner, loading, alternatives and preview
  hierarchy, GPS affordance, stop reordering, avoidance state, accessible
  controls and bounded bottom-sheet layout through typed callbacks only.

Route-planning screenshot/golden, drag-to-dismiss/reorder behavior, IME and
focus comparison, live geocoder/router/weather/event ownership, font-scale,
TalkBack and physical-device evidence remain required.

## Native settings evidence

- `generate_android_settings_strings.dart` projects 118 settings values into
  all 27 Android resource sets, using the English ARB value only where Flutter
  locales do not yet define a newer key, with deterministic no-drift checking.
- `NativeSettingsPresenter` preserves exact scalar defaults and persisted wire
  catalogues, bounds URLs/relay summaries and voice/brightness values, and
  exposes only configured state for routing, wallet and sync secrets.
- `NativeSettingsSession` fences stale revisions and returns typed persistence
  writes without owning Hive, DataStore, Keystore, routing, sync, files, model
  downloads, intents or clipboard.
- `NativeSettingsPanel` retains the existing section order and controls with
  bounded scrolling, 48 dp targets, headings, pane/live-region semantics and
  typed callbacks only.

Settings screenshot/golden, font-scale, TalkBack, secure-value dialogs, native
persistence/migration writeback, provider verification, saved-place file/sync,
voice model/download, external-link and physical-device evidence remain
required.

## Native onboarding and startup-gate evidence

- `generate_android_onboarding_strings.dart` projects 53 Flutter ARB values
  plus the exact two English-only protected-storage recovery strings into all
  27 Android resource sets with deterministic no-drift checking.
- `NativeOnboardingPresenter` gives protected-storage and migration failure
  precedence over every other state, accepts only the exact current disclosure
  boolean and emits Flutter's three compatibility writes only after consent.
- `NativeOnboardingSession` fences pages, identity, visibility, permission,
  voice progress and disclosure acceptance by revision without retaining an
  nsec or owning persistence.
- `NativeOnboardingFlow` packages four swipeable bounded pages, system insets,
  accessibility semantics and a disclosure that cannot be dismissed by back or
  outside tap. Amber, nsec, location and model operations are typed callbacks.

Onboarding credential dialogs, production storage/migration ownership, Amber,
permission and download wiring, process recreation, screenshot/golden,
font-scale, TalkBack and physical-device evidence remain required.

## Native home-chrome evidence

- `generate_android_home_strings.dart` projects the ten existing dashboard and
  bottom-bar ARB values into all 27 Android resource sets with deterministic
  no-drift checking.
- `NativeHomePresenter` mirrors both Flutter renderers' idle replacement gates,
  sanitizes and caps the visible favourite row at five, and preserves the
  unread badge's `99+` ceiling.
- `NativeHomeSession` fences input, expansion, actions and favourite selection
  by revision and emits only typed values to an external owner.
- `NativeHomeChrome` packages the collapsed/expanded map-first dashboard,
  quick actions, safe-area bottom bar, bounded width and accessible touch
  targets without loading remote profile images or owning product services.

Home screenshot/golden, compact/landscape/font-scale/TalkBack comparison,
runtime panel coordination, profile image, migrated favourites/activity state,
GPS/parking actions and physical-device evidence remain required.

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
