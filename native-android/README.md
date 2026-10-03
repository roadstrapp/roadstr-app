# Native road-test APK

This is an internal, side-by-side Android application used to turn the Kotlin
rewrite into a directly launchable runtime before the production cutover. It
has application ID `app.roadstr.roadtest`, so it cannot replace, migrate or
modify an installed `app.roadstr` application.

The module compiles the shared Kotlin `core/`, `feature/` and narrowly scoped
location/network/search/routing service sources plus Android resources
directly. It does not apply the Flutter Gradle plugin, package a Dart bundle or
reference Flutter embedding classes. The Activity owns foreground coarse/fine
location permission and an AOSP `LocationManager` feed that drives the Compose
map cursor and camera; it requests no background location and includes no
fused/Google location runtime.

The Navigate action now opens live Nominatim/Photon/Overpass search. Selecting
a destination opens the native planner and resolves driving, cycling or
walking routes through the credential-free public OSRM profiles; returned
alternatives are rendered by the shared MapLibre overlay. Starting the selected
route enters foreground guidance: new GPS fixes advance route progress
monotonically, update the active/completed geometry, manoeuvre HUD, speed,
limit, remaining distance/duration and the navigation camera. Stop or system
back clears that route and restores free-drive camera behavior. One cancellable,
deadline- and response-bounded OkHttp transport is reused by both services.
The runtime does not persist queries or coordinates. Automatic rerouting,
spoken TTS output, arrival automation, background navigation, transit and
production storage remain outside this harness.

Build from the repository root with this project's Gradle wrapper and an
Android SDK environment:

```sh
ANDROID_HOME=/path/to/android-sdk native-android/gradlew \
  -p native-android :app:assembleDebug
```

The APK is written to:

```text
build/native-roadtest/app/outputs/apk/debug/app-debug.apk
```

This artifact is not an official Roadstr update and must never be published as
one. The production package, signing identity, migration bridge and release
pipeline remain in `android/app` until their separate cutover gates pass.
