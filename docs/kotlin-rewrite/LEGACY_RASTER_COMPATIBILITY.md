# Legacy raster map compatibility

The persisted `mapEngine=osm` choice is a shipped user preference and cannot
silently become the tilted MapLibre experience during the Kotlin migration.
The native compatibility path preserves its visible semantics through the
single MapLibre Native renderer rather than carrying a second Flutter map
runtime into the final application.

## Preserved contract

- `maplibre` remains the default and unknown stored values fail safely to it.
- `osm` selects the legacy raster profile without rewriting the stored value.
- Both modes use the same admitted OSM/custom raster URL, 256-pixel tiles,
  attribution, light style and dark recoloring already shared by the Flutter
  renderers.
- The MapLibre profile starts at zoom 17 and pitch 40, retains the SDK's
  zoom 0–25.5 envelope and permits tilt up to 60 degrees.
- The legacy profile starts at zoom 6, clamps zoom to 2–19, fixes pitch at zero
  and disables tilt gestures. Bearing remains available because the shipped
  raster renderer supports heading-up rotation.
- Route, transit, marker, cursor and interaction layers remain shared, avoiding
  divergent native implementations for the two persisted choices.
- A selected profile and custom tile URL are explicit inputs to
  `NativeMapLibreHost`; the host never reads Hive or native preferences.

## Evidence and boundary

Five JVM cases lock storage values, agreement with the settings catalogue,
both camera profiles, command clamping and shared custom/dark raster output.
Four Flutter contracts lock the production selector, exact legacy and modern
camera envelopes, native host application and dormant shell ownership.

The private shell deliberately supplies the safe `maplibre` default and OSM
tile template because persistence ownership is not connected yet. Before
cutover, device tests must load a migrated `osm` preference and custom tile
URL, compare light/dark rendering and overlays, exercise rotation and prove
that process recreation retains the selected mode.
