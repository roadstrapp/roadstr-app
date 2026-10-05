# World discovery — repository audit, architecture and task plan

Scope: natural place search over open data, optional SearXNG web discovery,
optional embedded GeckoView browser, and structured web data turned into
Roadstr destinations. This is the plan phase: it records what the Kotlin code
looks like today, what upstream documentation says today, and the ordered
tasks. Decisions taken while writing it are in [DECISIONS.md](DECISIONS.md).

Audit date: 2026-10-05, branch `kotlin-native`. Upstream facts were fetched on
that date (GeckoView 157.0.20260924084938 on maven.mozilla.org, SearXNG
`master`, taginfo, the OSMF Nominatim policy, the Overpass manual).

Classification used for every major conclusion:

- **CONFIRMED** — read in the code, in upstream source/documentation, or measured.
- **LIKELY** — follows from confirmed facts but was not exercised.
- **EXPERIMENTAL** — needs a spike or a device before it can be relied on.
- **BLOCKED** — cannot be done as asked without a decision or an external change.

Nothing here was measured on a device (no battery, RAM, CPU or cold-start
numbers exist yet). Those rows say `NOT MEASURED` and have a task.

---

## 0. Preconditions and ground rules

| # | Item | Status |
|---|---|---|
| 0.1 | The prompt asks to start only after the Flutter→Kotlin migration is at stable parity with a proven in-place update. The repo's own register ([RISKS.md](../kotlin-rewrite/RISKS.md)) still lists R-01 (release certificate), R-02/R-03 (Hive and secure-storage compatibility), R-04, R-05 and R-17 (device upgrade) as open. | **CONFIRMED not met** |
| 0.2 | `FEATURE_PARITY.md` still marks most rows `CORE_ONLY`; the road-test APK (`app.roadstr.roadtest`) has since wired search, routing, navigation, voice, Nostr, settings, favourites and parking. The matrix is stale, not the code. | **CONFIRMED** |
| 0.3 | Consequence taken: planning and Kotlin-only implementation proceed on the road-test composition root. Nothing in this plan changes the Flutter production app (`lib/`), the `app.roadstr` package, or any fixture-locked file. | decision D-01 |
| 0.4 | The prompt states the project is GPL-3.0. The repository is MIT since this branch; only bundled eSpeak NG stays GPL-3.0-or-later ([THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)). Current code wins. | **CONFIRMED** |
| 0.5 | Fixture-locked files (`SearchProviderProtocol`, `SearchResponseProtocol`, `SearchRankingProtocol`, `SearchOrchestrationProtocol`, `SearchHistoryProtocol`, `OsmPlaceDetailsProtocol`, `OpeningHours`) mirror Dart byte for byte and have Dart oracles. New behaviour goes in new files; the only edit allowed in a locked file is an additive, native-only field with a default (precedent: `NominatimReverseDetail.poiName`). | decision D-02 |

---

## 1. Current Kotlin module / package architecture

**CONFIRMED** (code read; line counts from `find … | xargs wc -l`).

Two Gradle roots compile the same Kotlin:

- `android/app` — the Flutter production host (`app.roadstr`); the Kotlin shell is behind a disabled canary.
- `native-android/app` — the Flutter-free road-test APK (`app.roadstr.roadtest`, minSdk 24, ABIs arm64-v8a/armeabi-v7a/x86_64, 116.5 MB release). Its `sourceSets` pull whole directories from `android/app/src/main/kotlin/app/roadstr`: `core`, `feature`, and the `service/{hazards,location,network,nostr,routing,search}` subsets. **A new `service/discovery` directory must be added to that list.**

| Package | Files / lines | Role |
|---|---|---|
| `core/{format,geo,map,navigation,time,ui}` | ~2,250 | pure policies, geometry, opening hours, shared Compose tokens |
| `core/network` | 12 / 3,153 | request/response protocols (search, routing, transit), `HttpSafetyPolicy`, `PublicAddressPolicy` |
| `core/search` | 5 / 959 | fuzzy match, ranking, orchestration state machine, history, OSM details |
| `core/protocol` | 14 / 3,398 | Nostr/Lightning codecs |
| `feature/*` | ~28,000 | Compose UI + revision-fenced sessions (home shell 4.8k lines, map 4.2k, settings 1.8k, place, search, wikipedia, …) |
| `service/*` | 32 / 5,389 | network executors (`NativeSearchService`, routing, transit, hazards, Nostr) |
| `storage`, `migration` | 9+9 / 4,900 | encrypted atomic stores, Flutter→native import |

Seam: `NativeShellJourneyGateway` (interface in `feature/home/NativeShellJourneyCoordinator.kt`) is the provider-neutral boundary the shell consumes; the concrete implementation is `NativeRoadTestJourneyGateway` in the road-test app. All new network capabilities enter through new gateway methods with `= null`/`= emptyList()` defaults, so the production canary keeps compiling.

`NativeRoadstrShell.kt` (~2,100 lines) is the single composition point for every sheet. It is large but it is the existing pattern; new UI adds small composables and a few state holders to it rather than a second shell.

## 2. Current search architecture

**CONFIRMED.** Flow: `NativeSearchOverlay` → `NativeSearchSession` (revision-fenced) → `NativeShellJourneyCoordinator.submitSearch` → gateway → `NativeSearchService.search`.

- `SearchRankingProtocol.executionPlan` → Nominatim (settled only), Photon (always), POI/Overpass (only with a GPS point).
- `SearchOrchestrationProtocol` accepts completions out of order, emits at most one partial, runs exactly one relaxed Nominatim+Photon retry for queries of ≥3 words, then `mergePoiFirst`.
- Ranking: fuzzy name/brand score, query-city detection, 30 m proximity dedupe, max 10 results.
- **Search is explicit-submit only** (IME "search"). `NativeSearchPhase.TYPE_AHEAD` exists in the service but the shell never uses it. Every call is `SETTLED`.
- The 11 "nearby" chips send a `wireQuery` (`fuel`, `restaurant`, …) through `NativePoiSearchProtocol.categoryFilters` → one Overpass `around:4000`, `out center 12`.
- A saved place that matches the typed text wins over a web search (shell `onSubmit`).
- A result selection opens the place sheet (`showSearchPlace`) or, during navigation, goes straight to route planning.

## 3. Photon — **CONFIRMED**

`https://photon.komoot.io/api/`, `limit=8`, language sent only for `en|de|fr`, location bias rounded to two decimals (~1.1 km) with `location_bias_scale=0.3&zoom=12`, 4 s / 2 MiB, query ≤200 chars. The parser keeps name/street/housenumber/city and `osm_key`/`osm_value`. It **drops** `osm_id`, `osm_type`, `extent`, postcode and country code. User-Agent `Roadstr/1.0`.

## 4. Nominatim — **CONFIRMED**

`search?…&limit=6&addressdetails=1&extratags=1&polygon_geojson=0` with a ±0.25° viewbox (`bounded=0`) when a point is known; `reverse` with `addressdetails=1&extratags=1`. 5 s / 2 MiB. The result parser keeps display name, class/type, city/road/house number and `extratags.brand`. It **drops** `osm_id`, `osm_type`, `boundingbox`, and the extratags `opening_hours`, `website`, `phone`, `cuisine`, `diet:*` (only the reverse path keeps `opening_hours`).

OSMF policy, read on the audit date: absolute maximum **1 request per second per application (the sum of all users)**; a User-Agent that identifies the application; attribution; **no client-side autocomplete**; no systematic or grid reverse queries; apps must be able to switch service on request; caching strongly encouraged; the same query repeated may get a client blocked. Consequences: new discovery code gets a shared pacer and a cache; route-corridor search must use Overpass, never reverse geocoding.

## 5. Overpass — **CONFIRMED**

Mirrors `overpass-api.de`, `lz4.`, `z.` (fixed list, parity-locked), POST form body, `[timeout:5]`, 5 s per mirror, 6 MiB, fall-through to the next mirror. One query shape exists: `node|way[tag](around:4000,lat,lon)`, `out center 12`, no tags returned beyond name/opening hours/class.

Overpass manual, same date: public instances expect roughly ≤10,000 requests/day and ≤1 GB/day per address, and say that "relying on the public instances as backend" for an app is the problematic case. LIKELY policy exposure for Roadstr at scale; mitigation is a user-configurable endpoint (WORLD-024) and strict result caps. A live syntax check of a polyline `around` query returned 504 (mirror busy), so polyline `around` is **LIKELY** (documented in Overpass QL, not exercised).

## 6. Current place model

**CONFIRMED.** `SearchResult(displayName, shortName, position, featureClass, type, city, openingHours, distanceM, brand)`. No OSM id/type, phone, website, cuisine, tags, source or confidence. `OsmPlaceDetails` (phone, https-only website, cuisine, wheelchair, parking/EV/fuel/lodging fields) and `NativePlaceUiSnapshot.details` exist and the place sheet renders an `OsmDetailsCard`, **but nothing in the Kotlin shell ever supplies `details`** — the card is dead UI (Flutter fetches them). Closing this gap is WORLD-026 and is a prerequisite for "phone / website / Call" on a discovery result.

## 7. Current MapLibre pin / result architecture

**CONFIRMED.** `NativeMapPointOverlayKind` is a fixed enum (road events, OSM speed cameras, parking, traffic lights, speed bumps, crosswalks); markers are views drawn by `NativeMapPointOverlayView` from a revision-fenced `NativeMapPointOverlaySnapshot`. **Search results are never pinned**: they are a list; selecting one flies there and opens the sheet. A new `DiscoveryResult` kind plus a bounded marker set (≤25) fits the existing session; tap routing needs a small extension next to `opensRoadEventDetail`.

## 8. Route-destination interface

**CONFIRMED.** `NativeShellJourneyCoordinator.selectDestination(label, point, gpsPoint, myLocationLabel)` and `selectDestinationAndCalculate(…)` ("Navigate here") seed the planner, which keeps destinations as **text** in `stops[].query`; `SelectedDestination(label, point)` maps back by label equality, otherwise `resolvePoint` re-searches the text. A resolved place therefore reaches routing as `(label, point)`; no second routing path is needed. Caveat: identical labels collide only while editing a stop; accepted.

## 9. Search history / storage

**CONFIRMED.** `SearchHistoryEntry(label, lat, lon)`, newest-first, coordinate dedupe, bounded. The road-test gateway stores it through `NativeRoadTestProtectedPreferences` (AES-GCM, Android Keystore); an atomic-file `FileNativeSearchHistoryStore` exists in `storage/`. A separate encrypted route history (Activity button) was added on this branch. Web-discovery queries must **not** enter place-search history (decision D-12).

## 10. Current web / Wikipedia implementation

**CONFIRMED.** Website taps call `onOpenExternal` → the system browser. The Wikipedia reader is a restricted **`android.webkit.WebView`**: JavaScript, DOM storage, file/content access off, permissions denied, mixed content blocked, Safe Browsing off (to avoid sending URL hash prefixes to Google), navigation limited to `https://<lang>.wikipedia.org/wiki/…` main frames (`NativeWikipediaUriPolicy`), cookies cleared. The article summary is fetched by **text query** (`wikipediaArticle`), never by coordinates. This reader is the natural first consumer of a browser abstraction.

## 11. Network and security utilities

**CONFIRMED.** `NativeBoundedHttpClient` (OkHttp): redirects off, retries off, per-call total deadline, streamed byte budget, request `toString` redacted. `NetworkTimeoutBudget`/`NetworkResponseLimit` presets. Cleartext refused app-wide; the network-security config allows only `localhost`, `127.0.0.1`, `10.0.2.2` and cannot express LAN CIDRs (precedent: `NativeLanRelayConnector` speaks RFC 6455 over a raw socket for `ws://` LAN relays). `PublicAddressPolicy` rejects private/loopback/link-local/NAT64-wrapped addresses but is used only for Lightning/NWC URLs. `RoutingEndpointPolicy` already validates a user-supplied server URL.

## 12. Localization system

**CONFIRMED.** 27 locales (`bg cs da de el en es et fi fr ga hr hu it ja lt lv mt nl pl pt ro ru sk sl sv zh`). Strings come from ARB (`lib/l10n`) or per-key explicit tables in `tools/kotlin_rewrite/generate_*_strings.dart`, generated into `android/app/src/main/res/values*/…xml`, with drift tests. **Important:** `FuzzyMatch.normalize` keeps only ASCII letters/digits and a Latin accent table and treats every other character as a separator, so for Greek, Cyrillic, Japanese and Chinese input it returns an empty string. Existing category matching (`NativePoiSearchProtocol`, 52 English/Italian keys) therefore cannot serve those locales. New parsing needs its own Unicode-aware normaliser (WORLD-010).

## 13. Battery-sensitive behaviour today

**CONFIRMED.** Search is user-submitted only. The map polls hazards every 4 s only while the overlay toggles are on and the map is visible. Location, wake lock and screen policy are owned by the navigation session. Voice models load lazily. No periodic network task exists for search. The new code must keep it that way: no type-ahead, no background refresh, no prefetch of result pages (decision D-13).

## 14. Gaps between current search and the vision

| Gap | Evidence |
|---|---|
| No intent model; the only "category" support is a 52-key en/it map | `NativePoiSearchProtocol` |
| No attributes (`diet:*`, outdoor seating, wheelchair, LPG …) | no code references them |
| No "in <place>" resolution; Nominatim `boundingbox`/`osm_id` discarded | parsers |
| No reference to destination or route as search context | coordinator takes only a GPS point |
| Results are not pinned | §7 |
| No place identity (OSM id, website, phone, tags) | §6 |
| OSM details never loaded | §6 |
| "Open now" cannot be asked or ranked; evaluator ignores the place's time zone | `OpeningHours.evaluate(raw, LocalDateTime)` |
| No web layer, no instance settings, no source policy | none |
| Website taps leave the app; no in-app browser | §10 |
| Hard-coded Overpass/Nominatim/Photon endpoints | `SearchProviderProtocol` |

Open-data coverage matters for what can be promised (taginfo, 2026-10-05, nodes+ways): `amenity=restaurant` 1,622,489; `diet:vegetarian` 117,990; `diet:vegan` 67,746; `diet:gluten_free` 15,090; `cuisine=steak_house` 11,618; `opening_hours` 4,859,118; `website` 4,811,296 (+874,538 `contact:website`); `phone` 3,708,902; `fuel:lpg` 38,518. A vegan filter reaches ~4 % of restaurants and gluten-free <1 %. **CONFIRMED**: structured-only answers to dietary queries will often be sparse, so the UI must say so instead of implying completeness, and offer web discovery where enabled.

---

## 15. NaturalPlaceQuery

**Proposed (new package `core/discovery`).**

```kotlin
data class NaturalPlaceQuery(
    val rawText: String,
    val locale: String,
    val intent: QueryIntent,              // FindPlace | NameOrAddress
    val categories: List<PlaceCategoryRef>,// one id or a group (eat = restaurant+fast_food+cafe)
    val attributes: Set<PlaceAttributeRef>,// Diet.Vegan, Diet.GlutenFree, OutdoorSeating, Fuel.Lpg …
    val cuisine: CuisineRef?,
    val nameOrBrand: String?,
    val location: LocationConstraint,     // CurrentLocation | MapCenter | Destination | NamedPlace(text) | RouteCorridor | None
    val reference: ReferencePlace?,       // "near the station" -> text + resolved later
    val openNow: Boolean,
    val residualTerms: List<String>,      // tokens no lexicon entry explained
    val webHint: Boolean,                 // residual terms look like dish/menu words
)
```

Guard rails:

- If no category, attribute or cuisine is recognised, `intent = NameOrAddress` and the **existing pipeline runs unchanged** (test queries 7 and 8; no new request, no delay).
- A location clause (`in <text>`, `near <text>`) is accepted only after the text resolves to an area; otherwise the words go back into the free text.
- The interpreter sits behind `interface QueryInterpreter` so an optional local model could replace the rules later. No cloud call, no dependency (D-14).

## 16. Locale / category / OSM-tag mapping strategy

**Proposed.** Three data layers, none of them a `when` switch:

1. `PlaceCatalog` — category id → list of OSM tag conjunctions, emoji, group (`eat`, `fuel`, `health`, …); attribute id → key, accepted values, applicable categories; cuisine id → `cuisine` values. One list per kind, validated by tests (every filter well-formed, every id unique).
2. Per-locale **lexicons** as TSV text held in Kotlin raw strings (`internal object LexiconIt { const val TSV = … }`), columns `kind⇥id⇥phrase`. Kinds: `category`, `attribute`, `cuisine`, `near_me`, `open_now`, `in_place`, `near_ref`, `near_destination`, `along_route`, `filler`. No Android asset plumbing, no runtime I/O, identical in both Gradle roots and in JVM unit tests.
3. A coverage test: all 27 locales parse, define the mandatory connector kinds (`near_me`, `open_now`, `in_place`), and cover at least the core category set; English is a secondary lexicon for every locale (people type English words).

Tokenisation: `java.text.Normalizer` NFD, strip combining marks, lowercase, keep letters/digits of every script; Japanese and Chinese (no spaces) are matched by longest-substring scan instead of whitespace tokens. **EXPERIMENTAL** for ja/zh/el/bg/ru until reviewed by a speaker; the file layout makes review a one-file diff per locale.

## 17. Search orchestration

**Proposed (`DiscoverySearchOrchestrator`, beside — not replacing — `NativeSearchService`).**

```
raw text ──▶ QueryInterpreter ──▶ NameOrAddress? ──yes──▶ existing NativeSearchService (unchanged)
                 │ FindPlace
                 ▼
        resolve location constraint ─▶ SearchArea (circle | area | bbox | corridor)
                 ▼
        Overpass discovery query (tags returned, ≤40 elements) ──▶ RoadstrPlace list
                 ▼  (optional, settled, setting-gated)
        web discovery (SearXNG) ──▶ PlaceEntityResolver ──▶ merge / enrich
                 ▼
        DiscoveryRanking (deterministic) ──▶ pins + list + notices
```

Rules: one settled request per user action; at most 1 Nominatim area lookup and 1–2 Overpass calls per search; Nominatim shared pacer ≥1.1 s; in-memory TTL cache (area 24 h, Overpass 5 min, never persisted); cancel on new query; single-flight identical keys. Radius: 4 km first, one widening to 10 km if fewer than 3 hits.

## 18. SearXNG provider

**Proposed.** `interface WebDiscoveryProvider { suspend fun discover(request): WebDiscoveryOutcome }` with outcomes `Results | Disabled | Incompatible(reason) | RateLimited(retryAfter) | Failed(kind)`. `SearxngProvider` builds `GET {base}/search?q=…&format=json&categories=general&language=…&safesearch=…&pageno=1` and, when an allowlist exists, `&engines=a,b,c`. No cookies, no Referer, `Accept: application/json`, `User-Agent: Roadstr/<version>`. Response ≤1 MiB, 8 s total, redirects **not** followed (a 3xx is a failure), ≤20 results kept, all text clamped and control-character-stripped.

## 19. Instance compatibility / capability detection

**CONFIRMED against SearXNG source (`searx/webapp.py`, `webadapter.py`, `webutils.py`, `docs/dev/search_api.rst`).**

- `/` and `/search`, GET and POST.
- `format=json` returns **403** when `json` is not in `search.formats` (default setting is `formats: [html]`); `format` is otherwise optional. Many public instances disable JSON.
- `/config` exists and returns `categories`, `engines[]` (`name`, `categories`, `shortcut`, `enabled`, `paging`, `language_support`, `safesearch`, `time_range_support`, `timeout`), `plugins`, `instance_name`, `version`, `limiter.enabled`, `public_instance`, `safe_search`, `default_locale`. It does **not** report whether JSON output is enabled, so the only reliable test is a probe search.
- The `engines` form parameter (comma list) is honoured by `parse_generic`; `categories` is a comma list of names from `/config`.
- JSON body: `query`, `results[]`, `answers`, `corrections`, `infoboxes`, `suggestions`, `unresponsive_engines`. There is **no `number_of_results`** in the current source.
- A result is an engine-defined object with the common fields `url`, `title`, `content`, `img_src`, `thumbnail`, `engine`, `engines`, `positions`, `score`, `category`, `template`, `parsed_url`, `publishedDate`, `priority`. Only map-template engines (`openstreetmap`, `photon`) add `latitude`, `longitude`, `boundingbox`, `geojson`, `osm`, `address`; general results carry **no coordinates**.
- Rate limiting is an optional limiter (valkey) and bot detection that returns **429**; probe-header checks on `Accept`, `Accept-Language`, `User-Agent`, etc. can reject unusual clients. **LIKELY**: Roadstr must send ordinary headers and treat 403 as "JSON disabled", 429 as "back off".

Capability probe (once at configuration, again on demand): validate URL → `GET /config` (≤1 MiB, optional) → `GET /search?q=roadstr&format=json` → classify `OK | JsonDisabled(403) | RateLimited(429) | NotSearxng(HTML/other) | Unreachable`. Never fall back to another instance. A SearXNG `map`-category query is **not** used: it would route a query Roadstr already sends directly to Nominatim/Photon through a third party.

## 20. Strict no-Google / source-policy analysis

**Proposed + CONFIRMED limits.**

- Roadstr never calls Google and never adds a Google engine to a request.
- `SearchSourcePolicy(allowedEngines, blockedEngines, allowUnknown)`; default blocks every engine whose name contains `google` and `startpage` (it proxies Google); the engine allowlist is built from `/config` (`enabled`, category `general`, minus blocked) and sent as `engines=`.
- If `/config` is unavailable the policy falls back to filtering results by their `engines` field. That removes Google-sourced rows but **cannot stop the instance from querying Google upstream**. Settings states this in plain words; a "strict" option refuses instances for which no allowlist can be built.
- The claim Roadstr may make: "Roadstr does not contact Google; the instance you choose decides which search engines see your query." The claim it must not make: "no upstream ever sees Google".

## 21. Web-search privacy model

**Proposed.**

- Default **Off**. Modes Off / Ask / On; Ask shows the instance host and the exact text to be sent.
- Only query text, interface language and safe-search level leave the device. No coordinates, ever, by default.
- "Near me" becomes a **coarse locality name**: Nominatim reverse at `zoom=10` using coordinates rounded to two decimals, cached per grid cell; the locality name is what is appended ("vegan restaurant <city>"). The reverse request goes to Nominatim like search bias already does.
- Settings text lists what is sent and who receives it, and that Roadstr runs no telemetry around it.
- Web queries are never added to place-search history.

## 22. Result normalisation

**Proposed.** `WebResult(title, url, host, snippet, engines, rank)` with scheme restricted to `http|https`, userinfo/port rejected, credentials-in-URL dropped, text clamped to 160/300 chars, control characters removed. A `WebResult` is data, never a place, until resolved.

## 23. Entity-resolution algorithm

**Proposed (`PlaceEntityResolver`).**

1. **Domain match (cheap, no new network).** Compare the result's registrable domain with the `website`/`contact:website` host of every structured candidate already fetched for the search area. A match is strong evidence and enriches that OSM place ("menu mention · website").
2. **Name + locality lookup (bounded).** For at most 3 unmatched top results, derive `(nameGuess, locality)` from title segments split on ` - | – : |`, query Photon/Nominatim through the shared pacer, accept only if name similarity ≥ threshold, distance to the search area ≤ radius, and (category agrees or website host agrees).
3. **Structured data (Phase 6).** `GeoCoordinates`, `PostalAddress`, `telephone` extracted by the bundled WebExtension add evidence.
4. Otherwise the row stays a web result: openable, no pin, never invented coordinates.

## 24. Confidence / provenance model

**Proposed.** Evidence items with fixed weights: `OSM_ID` 1.0, `WEBSITE_HOST` 0.85, `PHONE` 0.8, `ADDRESS_EXACT` 0.8, `STRUCTURED_COORDS` 0.7, `NAME_LOCALITY` 0.6, `PROXIMITY` 0.1–0.3, `CATEGORY` 0.1; combined as `1 − Π(1 − wᵢ)`, capped at 0.99 unless an OSM id is present. ≥0.8 → pin; 0.5–0.8 → "is it this one?" candidate list; <0.5 → web result. **A brand name alone never merges two places**: merging needs an OSM id, or a website host / phone / address match, or proximity <30 m with name similarity ≥0.8. Provenance is a set (`OpenStreetMap`, `Geocoder`, `Website`, `SearXNG`) shown on the card as "Address · OpenStreetMap", "Menu mention · Website", "Website result · via SearXNG".

## 25. Map / pin UX

**Proposed.** Results arrive as the existing list **and** pins (kind `DiscoveryResult`, emoji by category, selected state), ≤25; the camera fits the pins; tapping a pin or a row opens the place sheet. Card actions: Navigate (existing `selectDestinationAndCalculate`), Website (in-app browser if enabled, else system), Call (`ACTION_DIAL` after confirmation; never `ACTION_CALL`), Share (text + `geo:`), Details (the existing OSM details card, now fed). A notice line explains sparse results ("few places are tagged for this in OpenStreetMap here").

## 26. Route-context design

**Proposed, phased.** `SearchArea` is a sealed type (`Circle`, `AdminArea`, `BBox`, `Corridor`). A corridor is the **remaining route within an ahead-window** (default 30 km), simplified to ≤24 vertices, queried with one Overpass polyline `around:<buffer>,lat,lon,lat,lon,…` request (LIKELY syntax; verify in WORLD-070). Candidates are ranked by perpendicular distance to the route as a detour proxy (`GeoMath.projectOnSegment`), and an OSRM detour is computed for the top three only, on selection. Cache key = (route revision, 5 km progress bucket, category), TTL 10 min; never polled; only on explicit user action. "Near destination" = Circle around the planner destination (1.5 km). No dozens of requests, no reverse-geocoding grids (forbidden by the Nominatim policy).

---

## 27. GeckoView — current stable integration approach

**CONFIRMED from the 157.0.20260924084938 artifacts (AAR downloaded, `javap` run).**

- Coordinates: `org.mozilla.geckoview:geckoview-arm64-v8a|armeabi-v7a|x86_64:157.0.20260924084938` (+ an all-ABI `geckoview`/`geckoview-omni`, 230.5 MiB) on `https://maven.mozilla.org/maven2`. No `x86` artifact. Latest release in the metadata: `157.0.20260924084938`, last updated 2026-09-29.
- AAR sizes: arm64-v8a **86.2 MiB**, armeabi-v7a **83.7 MiB**, x86_64 **91.0 MiB**; inside, `libxul.so` is 152 MB uncompressed (64.6 MB compressed), `omni.ja` 15 MB. A 3-ABI universal APK would grow from 116.5 MB to roughly 370 MB; arm64-only to roughly 200 MB. **BLOCKED for the default universal APK.**
- **minSdk 26** (`<uses-sdk android:minSdkVersion="26">`); Roadstr's native module is 24. **BLOCKED** unless GeckoView lives in a variant that raises minSdk.
- Licence MPL-2.0 (POM). Source hg `releases/mozilla-release`.
- Manifest adds: permissions `ACCESS_NETWORK_STATE`, `INTERNET`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS`; required GL ES 2.0; **89 `<service>` child-process slots**; one **exported** `GeckoClipboardContentProvider` (`${applicationId}.clipboard`, `grantUriPermissions`); a `queries` entry for `VIEW */*`.
- **POM runtime dependencies include `com.google.android.gms:play-services-fido:21.3.1`**, and `WebAuthnTokenManager` links `com.google.android.gms.fido.*` classes directly. Roadstr's own policy (no `com.google.android.gms`/`play` classes in an APK, enforced by `test/no_google_dependencies_test.dart` and a Gradle exclude) is therefore violated by default. Mitigation (EXPERIMENTAL): exclude the artifact, ship minimal stub classes for the referenced `gms.fido` symbols or R8 `-dontwarn`, and disable WebAuthn through a `configFilePath` pref; verify with `apkanalyzer` that no gms package remains and that pages still load.
- The POM also pins `kotlin-stdlib 2.4.20`; the build uses Kotlin 2.2.10. A compiler reads metadata at most one minor version ahead, so **EXPERIMENTAL**: either force the stdlib to the project version (runtime risk in Kotlin classes) or move the Kotlin plugin forward.
- API surface present in 157 (checked with `javap`): `GeckoRuntime.create(Context, GeckoRuntimeSettings)`, `warmUp()`, `shutdown()`, `getStorageController()`, `getWebExtensionController()`, `notifyTelemetryPrefChanged(boolean)`; `GeckoRuntimeSettings.Builder` has `contentBlocking`, `crashHandler`, `configFilePath`, `globalPrivacyControlEnabled`, `remoteDebuggingEnabled(false)`, `aboutConfigEnabled`, `loginAutofillEnabled`, `lowMemoryDetection`, `isolatedProcessEnabled`, `appZygoteProcessEnabled`, `allowInsecureConnections`, `trustedRecursiveResolverMode/Uri`, `experimentDelegate`, `fissionEnabled`, `extensionsProcessEnabled`; `GeckoSessionSettings.Builder` has `usePrivateMode`, `contextId`, `useTrackingProtection`, `allowJavascript`, `userAgentOverride`; `ContentBlocking.Settings.Builder` has `antiTracking`, `cookieBehavior`, `cookieBehaviorPrivateMode`, `safeBrowsing`, `queryParameterStripping*`, `bounceTrackingProtectionMode`, `cookiePurging`; `GeckoSession` has `open/close/setActive/setPriorityHint/loadUri` and delegates for navigation (`onLoadRequest`, `onNewSession`, `onLoadError`), content (`onCrash`, `onKill`, `onExternalResponse`), progress (`SecurityInformation.isSecure/host/origin`), permission (`onContentPermissionRequest`, `onAndroidPermissionsRequest`, `onMediaPermissionRequest`, including `PERMISSION_LOCAL_NETWORK_ACCESS`), prompt; `WebExtensionController.ensureBuiltIn/installBuiltIn`; `WebExtension.SessionController.setMessageDelegate(extension, delegate, nativeApp)`; `WebExtension.MessageDelegate.onMessage/onConnect`; `StorageController.clearData(flags)`.
- Gecko telemetry: GeckoView exposes Glean entry points (`sendGleanBrokenSiteReport`, `notifyTelemetryPrefChanged`) but upload is the embedder's job. **LIKELY** that an embedder that never initialises Glean sends nothing; **EXPERIMENTAL** until verified by capturing traffic.
- F-Droid: the main repo builds from source and already objects to prebuilt binaries (eSpeak). A binary GeckoView from Mozilla's Maven is **LIKELY BLOCKED** for the F-Droid main repo; it fits the GitHub/ZapStore channels and an optional variant.

## 28. GeckoRuntime lifecycle

**Proposed.** One runtime per process, created lazily on the first browser open (never at app start, no prewarm unless measured worthwhile — D-11). One `GeckoSession` at a time, opened in private mode with a fresh `contextId`, closed when the browser sheet closes; `setActive(false)` on `onStop`; `GeckoRuntime.shutdown()` after an idle timeout (default 120 s) and on memory pressure (`onTrimMemory ≥ TRIM_MEMORY_RUNNING_LOW`). Navigation state lives in the Roadstr process and is never touched by a Gecko crash: `onCrash`/`onKill` close the session and show a retry card. A browser failure cannot stop an active navigation (separate session objects, no shared locks).

## 29. Browser privacy configuration

**Proposed.** Ephemeral by default: private-mode sessions, `StorageController.clearData(ALL)` on close, no history store, no autofill (`loginAutofillEnabled(false)`), `globalPrivacyControlEnabled(true)`, `remoteDebuggingEnabled(false)`, `aboutConfigEnabled(false)`, `allowInsecureConnections(HTTPS_ONLY)`, content blocking `antiTracking` strict category, third-party cookies blocked, `cookieBehaviorPrivateMode = ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS`, **Safe Browsing `NONE`** (it sends URL hash prefixes to a third party; same reason the WebView reader turned it off), DoH off (system DNS), no crash handler service (no crash upload), no experiment delegate, WebAuthn off. Nothing survives an app restart. The privacy claim in Settings is rewritten from what this configuration actually does and re-checked by a traffic capture (WORLD-054).

## 30. Browser permission policy

**Proposed.** Geolocation, camera, microphone, notifications, XR, persistent storage, local-network and local-device access: **deny**. No Android runtime permission is ever requested on a page's behalf. `onNewSession` (popups) is denied and the URL offered as "open here". A later consent dialog for approximate location is out of scope for v1 (D-13).

## 31. URL / external-intent policy

**Proposed (`WebNavigationPolicy`, pure and fully unit-tested).** `https` → allow; `http` → upgrade attempt, otherwise confirm; `tel:` → confirm, then `ACTION_DIAL`; `mailto:` → confirm; `geo:` → parse coordinates carefully and offer "use as destination" (no auto-start); `intent:`, `javascript:`, `data:`, `file:`, `content:`, `market:`, custom schemes → block. A page is never allowed to start an Activity directly. Redirect loops end after 10 hops; localhost/private hosts blocked for pages loaded from a web result.

## 32. WebExtension structured-data bridge

**Proposed.** A bundled MV2/MV3 extension (`assets/web/place-extractor/`) installed with `ensureBuiltIn`, a content script that reads `application/ld+json` blocks and OpenGraph tags and posts **one** message `{v:1, url, jsonLd:[…], og:{…}}` through `runtime.sendNativeMessage`/`connectNative`. Kotlin side: `setMessageDelegate` on the session only; strict schema, ≤64 KiB per message, ≤8 JSON-LD blocks, depth ≤8; the page cannot call any Roadstr API, and no key, token or location is ever sent into the page or the extension. Page content is untrusted input end to end. **EXPERIMENTAL** until a device run proves the messaging path with a built-in extension in a private session.

## 33. JSON-LD / schema.org extraction strategy

**Proposed (`JsonLdPlaceParser`, pure Kotlin, reuses `BoundedJsonParser`).** Accept `@type` in `LocalBusiness`, `Restaurant`, `FoodEstablishment`, `Store`, `Place` and their subtypes, including `@graph`. Read `name`, `address` (`PostalAddress` fields), `geo` (`GeoCoordinates`, range-checked), `telephone`, `url` (https only), `openingHours`/`openingHoursSpecification`, `servesCuisine`, `hasMenu` (https only). Reject anything oversized or malformed; never evaluate page-supplied code; never accept coordinates that disagree with the resolved area by more than the search radius.

---

## 34. Threat model (summary)

| Threat | Control | Test |
|---|---|---|
| Malicious SearXNG URL (userinfo, `file:`, huge port, redirect to LAN) | endpoint policy, redirects off, scheme allowlist | `SearxngEndpointPolicyTest` |
| HTTP instance | HTTPS required; loopback/LAN http only through the explicit "my own instance" path | policy tests |
| Oversized / malformed JSON, 403, 429, redirect loop | 1 MiB cap, 8 s, outcome classes, circuit breaker | provider tests with a fake transport |
| Hostile result URL (`javascript:`, `intent:`, userinfo, LAN) | `WebResult` normaliser + `WebNavigationPolicy` | policy tests |
| Result text as UI/markup injection | clamp + strip, rendered as plain text | normaliser tests |
| Location leaks to web search | text-only requests; coarse locality | request-builder tests assert no coordinates |
| Page asks camera/mic/location/popup | deny-by-default delegates | delegate tests |
| Oversized/malicious extension message | schema + caps | schema tests |
| Page reaches secrets | no JS bridge, extension has no host API | source contract test |
| Gecko crash during navigation | separate session; `onCrash` handled | lifecycle test |
| Exported clipboard provider added by the AAR | `tools:node="remove"` or restricted authority; audited | merged-manifest test |

## 35. Battery / memory / size impact

| Item | State |
|---|---|
| Structured discovery | 1 Nominatim + 1–2 Overpass requests per explicit search; no background work. **LIKELY** negligible. |
| Web discovery | one request per explicit, settled action, off by default. |
| GeckoView APK size | +84–91 MiB per ABI (CONFIRMED from the AAR); must be an opt-in variant. |
| GeckoView RAM / CPU / cold-open / background | **NOT MEASURED**; protocol in WORLD-081 (device run by the owner). |
| Navigation jank with the module present | **NOT MEASURED**; Gecko runs in separate processes (89 declared slots) and only while a session is open. |

## 36. Dependency / licence matrix

| Component | Use | Licence | Note |
|---|---|---|---|
| GeckoView 157 | optional variant | MPL-2.0 | file-level copyleft; unmodified binary + notice; compatible with MIT distribution |
| `play-services-fido` | pulled by GeckoView | Google proprietary SDK terms | **must not ship**; excluded/stubbed (WORLD-050) |
| kotlin-stdlib 2.4.20, androidx core/lifecycle/media3, snakeyaml | pulled by GeckoView | Apache-2.0 | media3 adds size; check R8 shrink |
| SearXNG | HTTP API only | AGPL-3.0 (server) | no code linked or embedded; never ship a server in the APK |
| OkHttp, kotlinx.coroutines | existing | Apache-2.0 | reused |
| Overpass / Nominatim / Photon data | existing | ODbL / service policies | attribution already shown |
| JSON-LD / HTML parsing | none new | — | extension reads the DOM; Kotlin reuses `BoundedJsonParser` |
| NLP | none | — | deterministic lexicons |

## 37. Localization impact

New strings (settings texts, notices, ask dialog, provenance labels, browser chrome, error cards) go through the existing explicit-table generator for all 27 locales. Lexicons are separate data (§16). Before release, the 14 least-common locales need a native-speaker pass over the lexicons; until then the English lexicon is the safety net and the query falls back to the classic search when nothing is recognised.

## 38. Test plan

Unit (JVM, no device): normaliser; lexicon coverage per locale; parser table of ≥120 phrases across ≥10 locales (all eight example queries in the prompt); catalog validity; Overpass query builder golden strings (circle, area, bbox, polyline); ranking determinism; open-now with unknown/complex rules and the time-zone guard; SearXNG request builder, capability classifier, 403/429/HTML/oversized/redirect cases, source policy, no-coordinates assertion; entity resolver evidence and chain non-merge cases; `WebNavigationPolicy` URL tables; JSON-LD parser corpus including malicious inputs; extension message schema. Dart contract tests read the Kotlin sources as text for guard rails (no `Log.` in discovery code, no `google` engine in defaults, no coordinates in web request code, GeckoView only in the variant source set). Device (owner): GeckoView memory/CPU/cold-open, permission prompts, extension messaging, 16 KB page-size alignment of the Gecko `.so` files, traffic capture for telemetry.

## 39. Implementation roadmap

Phases 0→8 as in the prompt, with two changes forced by §27: Phase 5 is an **opt-in build variant** (`-Pgeckoview=true`, minSdk 26, one ABI), and Phase 6 starts only after a device spike proves built-in extension messaging. Phases 1–4 and 7's pure primitives are device-independent and proceed first.

## 40. Files and modules

New: `core/discovery/*` (query model, normaliser, catalog, lexicons, parser, place model, ranking, Overpass builder, SearXNG protocol, resolver, navigation policy, JSON-LD parser), `service/discovery/*` (orchestrator, SearXNG provider, pacer, cache, LAN transport), `feature/discovery/*` (pins, notices, web rows), `feature/web/*` (browser host interface; Gecko implementation only in the variant source set), `assets/web/place-extractor/*`, `docs/world-discovery/*`, tests under the matching test packages, `test/native_world_discovery_contract_test.dart`.
Modified (additively): `native-android/app/build.gradle.kts` (source dir + Gecko property), `NativeShellJourneyCoordinator` (gateway methods with defaults), `NativeRoadstrShell` (wiring), `NativeSearchPresentation` (optional notice/provenance fields), `NativeMapPointOverlaySession` (one kind), settings presentation + preferences + strings generator, `THIRD_PARTY_NOTICES.md`.

---

## 41. Tasks

Format: **objective · current code affected · new code · dependencies · network/privacy · battery/memory · tests · acceptance · rollback.** "—" means none.

### Phase 0
**WORLD-001** Record audit, decisions, gate. · docs only · `docs/world-discovery/*` · — · none · none · markdown review · docs merged · delete the folder.

### Phase 1 — query model (no network)
**WORLD-010** Unicode normaliser/tokeniser. · — (does not touch `FuzzyMatch`) · `core/discovery/TextNormalizer.kt` · — · none · none · Latin, Greek, Cyrillic, ja/zh strings, combining marks, ß/œ · all scripts keep their letters; ja/zh scan works · delete file.
**WORLD-011** Catalog (categories, attributes, cuisines, groups). · `NativePoiSearchProtocol` left alone · `core/discovery/PlaceCatalog.kt` · 010 · none · none · every filter well-formed, ids unique, every chip category present · catalog test green · delete file.
**WORLD-012** 27 locale lexicons + coverage test. · — · `core/discovery/lexicon/Lexicon_<xx>.kt`, `LexiconRegistry` · 011 · none · small (parsed lazily, per locale) · parse, mandatory kinds, core-category coverage, no duplicate phrase→different id in one locale · all locales pass · revert files; parser falls back to English.
**WORLD-013** `NaturalPlaceQuery` + parser + guard rails. · — · `NaturalQueryParser.kt`, `NaturalPlaceQuery.kt`, `QueryInterpreter` · 010–012 · none · none · the eight prompt queries in it/en/de/fr/es + ≥10 other locales, address and brand inputs return `NameOrAddress` · acceptance table green · parser unused until 025.

### Phase 2 — structured search
**WORLD-020** `RoadstrPlace` + provenance + tag whitelist + adapters from `SearchResult`/Overpass elements. · — · `core/discovery/RoadstrPlace.kt` · 011 · none · bounded lists · adapters, clamps, whitelist · tests green · delete file.
**WORLD-021** Area resolution (Nominatim `jsonv2` area search; keep `osm_type`, `osm_id`, `boundingbox`, class/type) with pacer. · locked parsers untouched · `core/discovery/GeocodedArea.kt`, `service/discovery/HostPacer.kt` · 020 · place text → Nominatim (policy-compliant, ≥1.1 s apart, cached) · low · parser fixtures, pacer timing with a fake clock · ≥1.1 s spacing proven · remove call site.
**WORLD-022** Overpass discovery query builder (circle, admin area, bbox) + element parser returning tags. · — · `core/discovery/OverpassDiscoveryQuery.kt` · 011, 020 · query text reveals the search area only · bounded `out … 40` · golden strings, injection safety (tag values escaped) · goldens green · delete file.
**WORLD-023** Ranking + open-now. · `OpeningHours` untouched · `DiscoveryRanking.kt`, `OpenNowPolicy.kt` (evaluates only within 100 km of the device clock zone, else `Unknown`) · 020 · none · none · determinism, unknown stays unknown, explicit `diet:x=no` excluded, chain non-merge · tests green · delete file.
**WORLD-024** `NativeDiscoveryService` (orchestration, cache, notices, widening, configurable endpoints). · `NativeSearchService` untouched · `service/discovery/NativeDiscoveryService.kt` + sourceSet entry · 021–023 · Nominatim + Overpass only · ≤3 requests per action · fake-transport tests: cancellation, mirror fall-through, cache hit/expiry, widening once · green · gateway returns null → classic search.
**WORLD-025** Gateway/coordinator integration with classic passthrough. · `NativeShellJourneyGateway`, `NativeShellJourneyCoordinator.submitSearch`, road-test gateway · new default-implemented gateway methods · 013, 024 · — · — · coordinator tests: FindPlace → discovery, NameOrAddress → classic, failure → classic · behaviour for addresses byte-identical · feature flag off by default switch.
**WORLD-026** UI: pins, notice line, place sheet fed with OSM details and provenance, provenance labels, strings ×27. · overlay session (one kind), search presentation (optional fields), shell, generator · `feature/discovery/*` · 025 · one Overpass call per place opened (by OSM id, cached) · ≤25 views · presenter tests, contract tests · pins + notice + details visible (device check by owner) · revert UI commit; list-only remains.

### Phase 3 — web discovery
**WORLD-030** SearXNG protocol: request builder, response parser, endpoint policy, source policy. · — · `core/discovery/SearxngProtocol.kt` · — · text only · none · URL tables, engine policy, response corpus (JSON, malformed, huge, HTML) · green · delete file.
**WORLD-031** Capability probe classifier. · — · `SearxngCapability.kt` · 030 · one `/config` + one probe search on user action · — · classification table · green.
**WORLD-032** `NativeSearxngProvider` (+ LAN HTTP transport for the explicit own-instance case). · `NativeBoundedHttpClient` reused · `service/discovery/NativeSearxngProvider.kt`, `NativeLanHttpTransport.kt` · 030, 031 · text only to the chosen host · single-flight, circuit breaker · fake transport: 403, 429+Retry-After, redirect, timeout, cancellation, no cookies/Referer · green · provider returns `Disabled`.
**WORLD-033** Settings (Off/Ask/On, instance URL in protected storage, test button, coarse-location switch, engine policy text) + strings ×27. · settings presentation/preferences/generator/tests · — · 031 · — · — · key-count and contract tests updated · UI on device (owner) · hide section.
**WORLD-034** Coarse-locality service. · — · `CoarseLocality.kt` · 021 · rounded coordinates to Nominatim at zoom 10, per-cell cache · — · rounding and cache tests · green.
**WORLD-035** Web rows + ask dialog in the search overlay. · overlay/presentation · optional fields · 032–034 · — · — · presenter tests · green.

### Phase 4 — entity resolution
**WORLD-040** `PlaceEntityResolver` + evidence/confidence + merge. · — · `PlaceEntityResolver.kt` · 020, 030 · ≤3 lookups per search through the pacer · — · domain match, name+locality, chain non-merge, low confidence stays web · green.
**WORLD-041** Candidate selection + provenance UI. · overlay/place sheet · — · 040 · — · — · presenter tests · green.

### Phase 5 — GeckoView (opt-in variant)
**WORLD-050** Build variant `-Pgeckoview=true`: dependency, minSdk 26, single ABI, gms exclusion + stubs, Kotlin stdlib alignment, exported provider removal, size budget check. · `native-android/app/build.gradle.kts` only · `src/gecko/…` source set · — · build-time download only · +86 MiB per ABI · variant builds; `apkanalyzer` shows no `gms`; merged manifest audit · acceptance: default build byte-identical in behaviour · drop the property.
**WORLD-051** Runtime manager, session lifecycle, idle shutdown. · — · `feature/web/GeckoRuntimeManager.kt` (variant only) · 050 · — · lazy; shutdown on idle/memory · fake-session lifecycle tests · green.
**WORLD-052** Navigation/permission/prompt/download policies. · — · `WebNavigationPolicy.kt` (core, pure), `GeckoPolicyDelegates.kt` (variant) · 050 · — · — · URL/intent tables, permission delegate tests · green.
**WORLD-053** Browser screen (title/host, TLS indicator, back/forward/reload, open externally, share, copy, close) and `WebBrowserHost` interface with system-browser fallback in the default build; Wikipedia reader moves onto the host. · wikipedia reader, shell `onOpenWebsite` · `feature/web/*` · 051, 052 · — · session open only while visible · presenter tests · default build still opens the system browser · revert wiring.
**WORLD-054** Privacy profile + traffic-capture checklist. · — · `GeckoPrivacyProfile.kt` · 050 · none expected · — · settings-object unit test, capture by owner · profile matches §29.

### Phase 6 — structured web data
**WORLD-060** Bundled extension + native message schema. · — · `assets/web/place-extractor/*`, `WebPageMessageSchema.kt` · 051 · none · small · schema/caps tests, device spike (owner) · green.
**WORLD-061** `JsonLdPlaceParser`. · — · `core/discovery/JsonLdPlaceParser.kt` · — · none · — · corpus incl. malformed/malicious · green.
**WORLD-062** Resolver integration + "Navigate here" from a page. · — · — · 040, 060, 061 · — · — · evidence tests · green.

### Phase 7 — route-aware
**WORLD-070** `SearchArea.Corridor`, polyline simplification, polyline Overpass builder, batching/caching policy. · — · `core/discovery/RouteCorridor.kt` · 022 · one request per action · bounded · goldens, simplification error bound · green; verify polyline `around` on a live mirror.
**WORLD-071** Detour ranking, cache, UI hooks (deferred until Phase 2 is proven on device). · coordinator, planner · — · 070 · — · — · ranking tests · green.

### Phase 8 — hardening
**WORLD-080** Security tests across §34. **WORLD-081** Perf/battery/memory/size protocol and results (owner device). **WORLD-082** Localization completeness + native review list. **WORLD-083** `THIRD_PARTY_NOTICES.md`, Settings privacy text, README section. **WORLD-084** Dart contract tests for the guard rails in §38.
