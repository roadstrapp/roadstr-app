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

## 3. Results on a device (2026-10-05)

**Phone:** Pixel 10, Android 17, arm64-v8a, powered over USB (battery 84–97 %, charging), debug builds of the default
road-test app and of the GeckoView variant, Wi-Fi. Memory is PSS summed over every process of the package (`dumpsys
meminfo`), CPU is `top` in percent of one core, six samples two seconds apart. Everything was driven with `adb` and screenshots
by the agent, with a stand-in SearXNG on the computer reached through `adb reverse` (it logs every request).

| What | Default build | GeckoView variant |
|---|---|---|
| Installed APK | 119 MiB | 248 MiB (the Flutter production app, arm64 split: 63 MiB) |
| Idle on the map, more than 2 minutes | 359 MiB, 1 process, CPU 2.5–3.5 % (115–165 % during the first minute, which is start-up) | 341–356 MiB, 1 process, CPU 2–3.5 %: **the engine costs nothing until a page is opened** |
| A static page open (`example.org`, 45 s after load) | – | **1262 MiB**, 5 processes: app 468, GPU 277, two page processes 199 and 180, crash helper 139; CPU about 6 % in total |
| A heavy page open (Wikipedia mobile, 1.5–2 min after load) | – | **1454–1482 MiB**, 5 processes: app 570–587, GPU 339, page 233 and 178, crash helper 135; CPU app 37–47 %, **GPU 90–120 %**, page 6–10 %: about 1.5–1.8 cores for a page that is not moving |
| After closing the page | – | 3 s later 1005–1085 MiB, and **80 s later still 1008 MiB** (nothing is given back with time): 4 processes, the app process keeps 485–569 MiB, the GPU and one page process stay, the crash helper stays; CPU back to 4–10 % |
| First content after tapping a result | – | cold engine: 4.4–4.9 s (Wikipedia), 3.6–4.1 s (`example.org`, fresh process); engine already running: 2.8–3.6 s (`permission.site`). Frames every 0.4–0.5 s, so ±0.5 s |

Readings of these numbers:
- Opening any page costs about 0.9–1.1 GiB of memory, almost whatever the page, and about 0.66–0.74 GiB of it is **not given back** when the page is closed (the engine stays for the life of the process, D-30). On a phone with little memory the system will kill the app instead.
- CPU depends on the page, not on the engine: a static page is quiet, Wikipedia's mobile page keeps the GPU process busy. It is the figure to look at for the battery, which could not be measured (below).
- The "crash helper" process is started by the engine and holds about 135–139 MiB without doing anything.

**16 KB pages.** The phone shows, at every start of a debug build, "this app is not compatible with 16 kB page sizes".
Checked on the APKs (stored uncompressed, data offset, ELF `LOAD` alignment): every library of GeckoView, MapLibre and ONNX Runtime is fine
(stored, offset multiple of 16 KiB, `p_align` 0x4000). Two are not, in both builds: `libespeak-ng.so` and `libroadstr_voice_jni.so`
(`p_align` 0x1000). The Flutter production app 0.5.11 has the same problem with `libespeak-ng.so`, so it is not new. See D-46.

**Permissions.** On `permission.site`, requests for notifications, location, camera and microphone showed no system permission
dialog and the app kept the focus. With location temporarily granted to the test package, the page's request produced no location
request in `dumpsys location`. (The grant was revoked afterwards.)

**WebAuthn.** `webauthn.io` says "WebAuthn isn't supported", and the app did not crash: the engine preference file is read and the
preference works.

**What was sent.** To the stand-in instance, the test connection and the search carried exactly: `User-Agent: Roadstr/1.0 (+project
URL)`, `Accept: application/json, text/html;q=0.9, */*;q=0.8`, `Accept-Language` (`it`), and the query `q`, `format=json`,
`engines` (the list read from `/config`) or `categories`, `language`, `safesearch`, `pageno`. No coordinates, no `Referer`, no cookie,
no other header. (No GPS was available in this run, so the town name for "near me" was not exercised.)

**Traffic, by socket sampling.** Not a packet capture: the sockets of the app's user id were listed from the kernel while a page was
open. The app alone talks to two hosts at idle (a map-tile CDN and one more); a search adds the search provider; a page adds its own
hosts (Wikimedia for Wikipedia). The engine adds **`content-signature-2.cdn.mozilla.net`** (34.149.226.178), also with `example.org`
open: a Mozilla service the engine contacts on its own. No telemetry host (`incoming.telemetry.mozilla.org`), no Safe Browsing or other
Google host was seen at the moments sampled, but short connections would be missed. See D-47.

**Not measured.** Battery: the phone was charging over USB, so milliamp-hours would mean nothing. Navigation smoothness with the module
present: the phone was not moving. A real traffic capture. The page extension on a page with place data (a page within the search
area was not available without using the owner's location).

### In-place update, signed with the release key

Not part of the protocol above; done the same day on the same phone. The release key in the checkout has the same certificate
(SHA-256 `9d6113c5…4281`) as the installed production app 0.5.11 (code 2050, installed by ZapStore).
- A Kotlin build under the production identity (`app.roadstr`, code 2051, `-Pcandidate=true`, signed with that key) **installed over
  the Flutter app** with `adb install -r`: accepted, same signature, install date and permissions kept, started in 0.9 s without a crash.
- It started as a first launch (onboarding, light default theme, no identity): **the data migration is not wired in this build**, as
  expected. The voice model downloaded by the Flutter app was recognised as ready without downloading it again (R-10).
- A Flutter build of `main` with code 2052 (the only way back, Android does not accept a lower code without uninstalling) installed over
  it: accepted, and the Flutter app came back with its data (onboarding done, dark theme, profile, notifications). The phone is now at
  code 2052: **a real release must use 2053 or more**.

