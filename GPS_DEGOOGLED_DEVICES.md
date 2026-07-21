# GPS on de-Googled devices (GrapheneOS, /e/OS, LineageOS w/o gapps) — READ BEFORE TOUCHING LOCATION CODE

## The rule

**`AndroidSettings.forceLocationManager` MUST stay `true` everywhere the app asks
Android for a position.** Removing it silently breaks GPS for every user without
Google Play Services.

Currently set in `lib/services/gps_service.dart`:
- `_locationSettings` (the continuous position stream) → `forceLocationManager: true`
- `refresh()` one-shot `getCurrentPosition(...)` → `AndroidSettings(forceLocationManager: true)`
- `lastKnown()` `getLastKnownPosition(forceAndroidLocationManager: true)`

## Why

`geolocator` defaults to the **Google Play Services "fused" location provider**.
On a de-Googled device that provider does not exist, and geolocator does **not**
fall back on its own — `getPositionStream` / `getCurrentPosition` simply **never
emit a fix**. Symptom: the map stays pinned on the fallback position (for an
Italian user that fallback is the geographic centre of Italy, ~42.5, 12.5), and
**no amount of waiting (3+ min) or recenter tapping ever produces a lock**.

`forceLocationManager: true` makes geolocator use the raw AOSP
`android.location.LocationManager`, which reads the GNSS hardware directly and
works on **every** Android device — Google-fied or not.

## Trade-offs (all acceptable, deliberately accepted)

- Marginally higher battery and no cross-sensor fusion smoothing vs the fused
  provider. Negligible for active outdoor driving navigation, and the app already
  does its own GPS filtering/smoothing (heading gate, flip suppression, etc.).
- **Privacy win**: no location request ever reaches Google Play Services, for
  *all* users. This matches the app's audience (Nostr / Bitcoin / GrapheneOS).

## Also relevant: GPS must warm up at launch

De-Googled devices have no A-GPS / network-assisted location, so the **first raw
GNSS fix is slow** (tens of seconds to minutes on a cold start). The app therefore
warms up the stream at launch:
- `autoCenterOnLaunch` setting **defaults to `true`** (`map_screen._maybeAutoCenterAtStartup`
  + the Settings switch). It starts the stream at launch **only when location
  permission is already granted** (never opens a dialog). If this is flipped back
  to default-off, de-Googled users get no warm-up window and it feels broken again.

## History

Pre-rewrite the app auto-started GPS at launch, which gave the raw GNSS time to
lock, so it *appeared* to work on GrapheneOS. A later rewrite deferred GPS start
to the first recenter/nav tap AND never set `forceLocationManager`, which fully
broke de-Googled devices. Both are restored here. `geolocator` has been 14.0.3
throughout — this was never a package-version issue.
