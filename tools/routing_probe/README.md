# Routing probe

Throwaway Android app used to measure offline routing and PMTiles rendering on a real phone
(`docs/offline-engine/RESEARCH_2026-10-08.md`, section 1.6). Not part of Roadstr: separate
`applicationId`, no INTERNET permission.

- `mkpairs.py` writes `app/src/main/assets/pairs.json` (snapped test points) using `pyvalhalla`.
- Needs `app/libs/brouter-all.jar` (BRouter fat jar, built from `brouter-server`) and a Gradle wrapper.
- Data copied to the app's private storage with `adb push` + `run-as`: `car_full.tar`, `corridor.tar`
  (Valhalla extracts), `rd5/` + `brouter_profiles/` (BRouter), `italy.pmtiles`.
- Suites: `--es suite valhalla_full|valhalla_corridor|brouter` on `ProbeActivity`, and `MapProbeActivity`.
- Results are written to `files/results_<suite>.txt` and logged under the tag `PROBE`.
