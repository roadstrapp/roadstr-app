# geolocator_android — Roadstr fork

Vendored from [`geolocator_android` 5.0.3](https://pub.dev/packages/geolocator_android)
(MIT, Copyright (c) 2018 Baseflow — see `LICENSE`, unchanged).

## Why

Upstream depends on `com.google.android.gms:play-services-location`, a
proprietary Google library. Roadstr never uses it: every call into geolocator
sets `forceAndroidLocationManager`, so position always comes from Android's own
`LocationManager` — which is what makes the app work on GrapheneOS, /e/OS and
other devices with no Google services installed.

The library still shipped inside the APK, because the plugin's
`GeolocationManager` chooses between the two clients at runtime and both stay
reachable to the compiler. That put fifteen `com.google.android.gms.*` classes
in a release nobody could execute them in, and made an app built to avoid
Google look like it depended on it.

## What changed

Three edits, kept as small as possible so upstream changes stay easy to merge:

1. `android/src/main/java/.../location/FusedLocationClient.java` — deleted.
2. `android/src/main/java/.../location/GeolocationManager.java` —
   `createLocationClient` always returns `LocationManagerClient`; the two
   `com.google.android.gms.common` imports and the now-unused
   `isGooglePlayServicesAvailable` probe are gone.
3. `android/build.gradle` — the `play-services-location` dependency is removed.

Nothing else is touched. The Dart API is identical, including
`forceAndroidLocationManager`, which is now simply always in effect.

## Updating

Copy a newer `geolocator_android` over this directory, re-apply the three edits
above, and rebuild. `test/no_google_dependencies_test.dart` in the app repo
fails if any `com.google.android.gms` reference reappears.
