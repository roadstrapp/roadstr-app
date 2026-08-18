# Rendering engine — architecture decision

**Status:** decision proposed, not yet accepted
**Date:** 2026-08-18
**Scope:** phases 1–2 (audit and decision). No implementation has been started.

---

## Summary

**Recommendation: do not replace the rendering engine. Ship offline maps on the current
stack instead, and revisit vector rendering once a blocking dependency is resolved.**

The investigation started from "build from scratch or fork OsmAnd". Both were examined
seriously. Both, and the two obvious middle paths, turn out to be blocked by concrete,
verifiable obstacles — while the feature that motivated the whole exercise, **offline
maps, does not actually require a new rendering engine at all**.

That reframing is the substance of this document.

---

## 1. Current state

### 1.1 What Roadstr renders today

`flutter_map ^8.3.0` (resolved 8.3.1), with `latlong2 ^0.10.1`. All map composition
lives in `lib/screens/map_screen.dart`, with marker widgets in `lib/widgets/map/`.

| Layer | Count | Contents |
|---|---|---|
| `TileLayer` | 1 | OSM raster tiles, with a dark-mode tile builder |
| `PolylineLayer` | 6 | route alternatives, active route, progress split (done/remaining), transit legs per mode |
| `MarkerLayer` | 7 | user cursor, destination, road events, speed cameras, POI results, favourites, parking |

There is **no native map code**: no C++, no FFI, no platform views. The only native code
in the project is the eSpeak NG phonemizer, unrelated to rendering.

### 1.2 What the current stack cannot do

These are the real limitations, in the order they matter:

1. **No offline maps.** Raster tiles are fetched per session. This is the headline gap.
2. **Styling stops at the UI.** Themes (light/dark, Nostr Violet / Bitcoin Orange) apply
   to the chrome; the map itself is recoloured with a tile-level filter, which is a
   post-process, not real styling.
3. **Rotation resamples.** Heading-up navigation rotates raster tiles, which softens
   labels and rasterised text.
4. **Bandwidth.** Every journey re-fetches tiles.

### 1.3 What was NOT measured

**No device benchmarks were run.** Phase 1.3 asked for FPS, memory and battery figures
on Pixel 6 / Pixel 3a / Mi 9, and comparative measurements against the OsmAnd app.

That work has not been done, and no numbers are invented here. Frame timings and battery
drain cannot be obtained from a static audit, and quoting plausible-looking figures in a
decision document is worse than admitting the gap — they would be treated as evidence.

This does not block the recommendation below, because the recommendation does not rest
on performance: every rejected option is rejected on **licensing, dependency or
compatibility grounds that are decidable without a benchmark**. If the decision were
between two otherwise viable engines, benchmarking would be mandatory first.

---

## 2. Options examined

### Option A — keep flutter_map, add capability incrementally

Status quo plus targeted additions. Covered in §3 because it is the recommendation.

### Option B — fork OsmAnd's rendering engine

The engine is **not** in `osmandapp/OsmAnd` (1.4 GB, mostly Java). It is a separate
repository, `osmandapp/OsmAnd-core` (~24 MB, C++), actively maintained — most recent
commit 2026-08-14.

**Licensing — two different answers, and the second is the problem.**

| Component | Licence | Compatible with Roadstr (GPL-3.0-only)? |
|---|---|---|
| `OsmAnd-core` (C++ engine) | GPL-3.0-**or-later** | ✅ Yes |
| OsmAnd artwork, incl. map styles and icons | **CC-BY-NC-ND 4.0** | ❌ No |
| Some artwork | "proprietary" (their own wording) | ❌ No |

The engine code is cleanly usable. **The styles and icons are not.** `NC` forbids
commercial use and `ND` forbids derivatives — both incompatible with GPL-3.0, and both
disqualified from F-Droid as non-free.

This matters more than it first appears: **a rendering engine without a style renders a
blank screen.** The value of forking OsmAnd is largely its mature cartography, and that
is precisely the part that cannot be taken. What is usable is the geometry pipeline,
after which the entire style and icon set would have to be authored from scratch.

> Correction: an earlier note in `README.md` stated that OsmAnd is GPL-3.0 and "the reuse
> is clean". That was right about the code and wrong about the artwork. The README has
> been corrected.

**Dependencies — the practical killer.** `OsmAnd-core/externals` contains 21 external
C++ projects:

```
boost      expat      freetype   gdal       geographiclib  giflib
glew       glm        harfbuzz   icu4c      jpeg           libarchive
libpng     proj       protobuf   qtbase-android            qtbase-desktop
qtbase-ios skia       sqlite     zlib
```

That list includes **Qt**, **Skia**, **GDAL** and **ICU** — four of the heaviest native
dependencies in existence. Consequences:

- **F-Droid would very likely be unable to build it.** F-Droid builds from source on
  their infrastructure. Compiling Qt + Skia + GDAL for three ABIs is a multi-hour build.
  The project already needed a `prebuild:` script and `scanignore` entries for a single
  eSpeak NG library; this is a different order of magnitude.
- **A second graphics stack.** Flutter already ships its own renderer. Embedding
  OsmAnd-core means running Skia, freetype and harfbuzz *alongside* Flutter's own
  equivalents, composited through a texture bridge — two GPU pipelines, two text
  shapers, in one process.
- **APK size.** The universal APK is already 134 MB. Qt plus Skia plus GDAL would add
  substantially to that.

**Verdict: rejected.** Not on quality — OsmAnd's renderer is excellent — but because the
reusable half is the half that needs the most work anyway, and the dependency tree is
incompatible with how this app is built and distributed.

### Option C — pure-Dart vector tiles (`vector_map_tiles`)

The natural middle path: vector rendering on Flutter's own canvas, no native code, works
as a `flutter_map` layer.

**Blocked by version incompatibility.**

| | Requires | Roadstr has |
|---|---|---|
| `vector_map_tiles` 8.0.0 | `flutter_map: ^7.0.2` | `^8.3.0` |
| | `latlong2: ^0.9.0` | `^0.10.1` |

Adopting it means downgrading `flutter_map` a major version and downgrading `latlong2` —
whose `LatLng` type appears throughout the codebase, including routing, Nostr events and
the new transit models.

The package's last release was **2024-08-16**, two years ago, while `flutter_map` has
moved to 8.3.1. This is not a temporary lag; it is an unmaintained dependency at the
centre of the map.

**Verdict: rejected for now, but this is the one worth watching.** If it gains
`flutter_map` 8.x support, it becomes the strongest option, because it needs no native
code, no new licence surface and no second graphics stack.

### Option D — `maplibre_gl`

Actively maintained (v0.26.2, 2026-06-19), mature vector engine, BSD-licensed, genuine
offline support.

**Blocked by a hard project constraint.** Its Android module declares:

```gradle
implementation 'com.google.android.gms:play-services-base:18.10.0'
implementation 'com.google.android.gms:play-services-location:21.3.0'
```

Roadstr ships **zero Google classes**, verified with `apkanalyzer` on every release. That
constraint is why `geolocator` is vendored into `third_party/`. Adopting MapLibre means
either carrying those dependencies — which breaks the project's central promise and its
F-Droid anti-feature status — or vendoring and patching MapLibre the way geolocator was,
and maintaining that fork.

Additionally it replaces the whole map widget, so every custom layer in §1.1 would be
rewritten against a different API, and platform views bring their own composition costs.

**Verdict: rejected on constraint grounds.** Technically the most capable option; the
Play Services dependency disqualifies it unless someone commits to maintaining a
de-Googled fork.

### Option E — write a renderer from scratch

Full control, no licence surface, no native dependencies. Also: matching a mature
renderer's label placement, collision detection, and performance is years of work, not
weeks. Every credible vector renderer in this space represents a decade of accumulated
cartographic detail.

**Verdict: rejected.** Not because it is impossible, but because the motivating feature
(offline maps) can be delivered in a fraction of the time by other means, and nothing
else about the current renderer is actually broken.

---

## 3. Recommendation

**Do not replace the rendering engine now. Deliver offline maps on the existing stack.**

The goal was never "a new renderer" — it was offline maps, better styling, and less
bandwidth. Offline maps are the valuable part, and they are achievable today:

| Package | Version | `flutter_map` constraint | Compatible with 8.3.1 |
|---|---|---|---|
| `flutter_map_mbtiles` | 1.0.4 | `>=6.0.0 <9.0.0` | ✅ |
| `flutter_map_cache` | 2.1.0 | `>=6.0.0 <9.0.0` | ✅ |
| `flutter_map_tile_caching` | 10.1.1 | `^8.1.1` | ✅ |

All three are compatible with the current stack, need no native code, add no Google
dependency, and raise no licensing question.

**Proposed sequence:**

1. **Offline raster maps** via MBTiles, with region download and management UI. Delivers
   the headline feature. Weeks, not months.
2. **Tile caching** for online use, which reduces bandwidth on repeat journeys as a side
   effect of the same work.
3. **Revisit vector rendering** when `vector_map_tiles` supports `flutter_map` 8.x — or
   when someone is prepared to maintain a de-Googled `maplibre_gl` fork. Re-open this
   document at that point.

**Accepted trade-offs:** raster offline packs are larger than vector for the same area,
and map styling stays baked into the tiles. Both are real costs. Neither is worth a
multi-month native-engine project, a second graphics stack, or breaking the F-Droid
build.

---

## 4. Risk assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Offline raster packs too large to be useful | Medium | High | Measure early on one city before building the UI. If unusable, this is the trigger to reopen the vector question, not to push on. |
| `flutter_map_mbtiles` becomes unmaintained | Medium | Medium | It is a thin tile provider; the format is a documented SQLite schema. Replaceable in-house if abandoned, unlike a whole renderer. |
| Storage permission / SAF friction on modern Android | Medium | Medium | Prototype the download-and-store path first, before the UI. |
| `vector_map_tiles` never updates | Medium | Low | Only delays a future option; does not affect what ships. |

**Rollback:** trivial, and this is a large part of the argument. Offline tiles are an
additional `TileProvider` behind a setting. Removing them is deleting a dependency and a
code path — as opposed to unpicking an FFI boundary and a native build.

---

## 5. What would change this decision

State the falsifiers up front, so this is revisited on evidence rather than on mood:

- `vector_map_tiles` (or an equivalent) gains `flutter_map` 8.x support → re-evaluate
  Option C, which then likely wins.
- MapLibre's Flutter plugin drops its Play Services dependency, or a maintained de-Googled
  fork appears → re-evaluate Option D.
- Measured raster offline packs turn out to be impractically large for real regions →
  vector becomes necessary rather than preferable, and the cost calculus changes.
- Someone commits to owning a native build long-term → Option B becomes discussable, but
  the artwork licensing still has to be solved separately.

---

## 6. Status of the phased plan

Phases 3–5 of the original brief (prototype, core rendering, styling, benchmarking,
merge) are **not started**, and should not be until this decision is accepted. The
`feature/rendering-engine` branch currently contains this document only.
