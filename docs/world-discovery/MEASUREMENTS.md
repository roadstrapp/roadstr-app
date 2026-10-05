# Measurements: what is known and how to measure the rest

Nothing here was run on a device: no `adb` was used for this work (D-17). The numbers below are from
the build outputs; the rest is a protocol for the owner.

## 1. Known from the builds (debug APKs, 2026-10-05)

| Build | Size | minSdk | ABIs |
|---|---|---|---|
| Default road-test app | 119.4 MiB | 24 | arm64-v8a, armeabi-v7a, x86_64 |
| GeckoView variant (`./gradlew-geckoview :app:assembleDebug`) | 248.1 MiB | 26 | arm64-v8a |

- Every native library of GeckoView's arm64 AAR has 16 KB aligned load segments (`0x4000`, checked with `readelf -lW`).
- `native-android/verify-geckoview-variant.sh` checks, for both builds, the package, minSdk, ABIs, the absence of the exported clipboard provider and of any `com.google.android.gms` class, the permissions, and that the default build has no GeckoView.
- Place search costs, per search the user starts: at most one Nominatim lookup for the area, one Overpass query and one widening; a web search adds one request to the chosen instance and at most three name lookups. No background work, no polling.

## 2. To measure on a device

Use the variant for the browser items and the default build as the baseline, on the same phone, battery above 80 %, screen brightness fixed, airplane mode off, the same Wi-Fi.

| What | How | Record |
|---|---|---|
| Memory of the app, idle on the map | `adb shell dumpsys meminfo app.roadstr.roadtest` after 2 min | PSS total, native, graphics |
| Memory with a page open | the same after opening a restaurant page from a web result, again 60 s later | PSS, number of `org.mozilla` processes (`adb shell ps -A \| grep roadtest`) |
| Memory after closing the page | the same 60 s after closing | how much is returned (the runtime stays, D-30) |
| Cold start of the browser | time from tapping a web result to the first paint, 5 runs, first and later | median, worst |
| CPU while a page is open and the screen is on | `adb shell top -H -n 5 -p <pid>` or Android Studio profiler | average % over 60 s |
| Battery | `adb shell dumpsys batterystats --reset`, 10 minutes of navigation with the browser never opened, then the same with a page open for 3 of the 10 minutes | mAh, per-process share |
| Navigation smoothness with the module present | 10 minutes of navigation in the variant versus the default build | dropped frames (`dumpsys gfxinfo`), any stutter noted |
| Install size on the device | `adb shell pm path` and `ls -l` | per build |
| Telemetry | capture the phone's traffic (a router or `mitmproxy` with the app on the same network, no certificate installed) while opening three pages | every host contacted, besides the page's own; Mozilla hosts and `telemetry` or `crash` names would be a failure |
| Engine preferences | the engine has no preference viewer (`about:config` and remote debugging are off by design), so judge the preferences by the traffic capture | that no request goes to the hosts the preferences switch off |
| Permissions | open a test page that asks for location, camera, notifications and a pop-up (`https://permission.site/`) | each must be refused without a prompt |
| Page extension | open a restaurant page whose markup has JSON-LD; the "navigate here" bar should appear within a few seconds | works or not; D-38 |
| WebAuthn off | open a page that offers a passkey sign-in | no crash, no prompt |

Write the results into this file, with the phone model, Android version and date, and revisit
D-11 (the variant is opt-in) and D-30 (the runtime stays alive) with them.
