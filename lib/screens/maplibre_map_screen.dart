// First real slice of the MapLibre rendering engine — reachable via the
// "mapEngine" setting (Impostazioni → Mappa), not merged in behind
// kDebugMode like the throwaway PoC it grew out of.
//
// Deliberately incomplete: this is the incremental migration from
// docs/rendering-engine-decision.md §7, not a replacement for MapScreen.
// Destination search, real routing, ZTL-aware route colouring, speed
// camera and parking markers are here; navigation itself (following a
// route, maneuver instructions, off-route detection, rerouting), POI
// search, favourites-as-markers and voice guidance are not — each is a
// later phase, migrated and tested on its own. Switching the setting to
// "maplibre" trades all of that away for tilt/rotate and native dark-mode
// styling; the settings copy says so.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';
import 'package:maplibre/maplibre.dart';
import 'package:provider/provider.dart';

import '../services/camera_follow.dart';
import '../services/gps_service.dart';
import '../services/place_search_service.dart';
import '../services/routing_service.dart';
import '../services/speed_camera_service.dart';
import '../services/ztl_service.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../widgets/cursor_painter.dart';
import '../widgets/map/map_markers.dart';
import '../widgets/search/search_panel.dart';

const _roadstrTileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

// Same recolouring as MapScreen._darkTileBuilder, done natively instead of
// through a Flutter ColorFilter — see the PoC commit for why the numbers
// are what they are (hue-rotate 180° to undo inversion's colour shift,
// brightness min/max flipped to invert lightness, saturation and contrast
// pulled back so area fills read as muted rather than neon).
String _style({required bool dark}) => '''
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["$_roadstrTileUrl"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {
      "id": "osm",
      "type": "raster",
      "source": "osm"
      ${dark ? ''',
      "paint": {
        "raster-hue-rotate": 180,
        "raster-brightness-min": 1,
        "raster-brightness-max": 0,
        "raster-saturation": -0.5,
        "raster-contrast": 0.1
      }''' : ''}
    }
  ]
}
''';

// Same colour split MapScreen's route rendering uses — the app's accent
// for an ordinary run, this red for one crossing a restricted zone. See
// map_screen.dart's _kZtlRed and _remainingRouteRuns; _splitByZtl below is
// a simplified version of the latter (no completed/remaining distinction —
// this screen has no navigation-progress concept yet, just a calculated
// route).
const _kZtlRed = Color(0xFFE53935);

/// Groups a polyline into contiguous same-classification runs, sharing the
/// boundary point between adjacent runs so the drawn segments stay visually
/// connected instead of leaving a gap at each colour change.
List<({List<LatLng> points, bool restricted})> _splitByZtl(
    List<LatLng> points, List<bool> restricted) {
  final runs = <({List<LatLng> points, bool restricted})>[];
  if (points.length < 2) return runs;
  var i = 0;
  while (i < points.length - 1) {
    final r = restricted[i];
    final run = <LatLng>[points[i]];
    var j = i;
    while (j + 1 < points.length && restricted[j + 1] == r) {
      run.add(points[j + 1]);
      j++;
    }
    runs.add((points: run, restricted: r));
    i = j;
  }
  return runs;
}

class MaplibreMapScreen extends StatefulWidget {
  const MaplibreMapScreen({super.key});

  @override
  State<MaplibreMapScreen> createState() => _MaplibreMapScreenState();
}

class _MaplibreMapScreenState extends State<MaplibreMapScreen> {
  final _gps = GpsService();
  StreamSubscription<GpsData>? _gpsSub;
  MapController? _controller;

  // Mirrors MapScreen's _followUser: true until the user pans/rotates/tilts
  // by hand, at which point their gesture must not be immediately fought by
  // the next GPS fix.
  bool _followUser = true;
  GpsData? _lastFix;
  bool? _stylingDark;

  // Camera easing — CameraFollowEasing is the same policy
  // MapScreen._startFollowTicker uses, driven here against MapController
  // instead of flutter_map's controller. _camState is this screen's own
  // tracked position, not read back from the controller every frame: while
  // following, nothing else is moving the camera, so our last computed
  // frame is already ground truth, and querying it every 16 ms would be
  // pure overhead.
  CameraFollowState? _camState;
  CameraFollowState? _targetState;
  Timer? _followTicker;
  int? _lastFollowFrameMs;

  // ── Destination search + routing ─────────────────────────────────────────
  // PlaceSearchService, RoutingService and ZtlService are all reused as-is:
  // none of the three ever touched flutter_map, so there is nothing here to
  // port, only to wire up.
  final _searchController = TextEditingController();
  final _placeSearch = PlaceSearchService();
  final _ztl = ZtlService.instance;
  bool _showSearch = false;
  bool _searching = false;
  List<NominatimResult> _searchResults = [];
  Timer? _searchDebounce;
  RouteResult? _route;
  List<({List<LatLng> points, bool restricted})> _routeRuns = [];

  // ── Static overlays ───────────────────────────────────────────────────────
  // SpeedCameraService is reused as-is, same as ZtlService — an OSM-backed
  // proximity cache neither of which ever depended on flutter_map. Parking is
  // simpler still: one saved LatLng read straight out of the settings box,
  // the same JSON shape MapScreen._loadParkingPosition reads. Favourites are
  // deliberately not drawn as markers here: MapScreen doesn't either — they
  // only ever surface as search-result rows, which SearchResultsList already
  // renders, so there is nothing to port for them on the map itself.
  final _speedCameraSvc = SpeedCameraService();
  LatLng? _parkingPosition;

  @override
  void initState() {
    super.initState();
    _loadParkingPosition();
    unawaited(_gps.start());
    _gpsSub = _gps.stream.listen(_onGps);
  }

  void _loadParkingPosition() {
    final raw = Hive.box('settings').get('parking_position') as String?;
    if (raw == null) return;
    try {
      final j = jsonDecode(raw) as Map<String, dynamic>;
      final lat = (j['lat'] as num).toDouble();
      final lon = (j['lon'] as num).toDouble();
      if (lat.isFinite && lon.isFinite && lat.abs() <= 90 && lon.abs() <= 180) {
        _parkingPosition = LatLng(lat, lon);
      }
    } catch (_) {
      // Corrupt or hand-edited entry: treat as absent rather than crash.
    }
  }

  @override
  void dispose() {
    _gpsSub?.cancel();
    _followTicker?.cancel();
    _searchDebounce?.cancel();
    _searchController.dispose();
    unawaited(_gps.dispose());
    super.dispose();
  }

  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    if (query.trim().isEmpty) {
      setState(() => _searchResults = []);
      return;
    }
    // Same 400ms debounce map_screen.dart's own search box uses — short
    // enough not to feel laggy, long enough that a fast typist doesn't fire
    // a request per keystroke.
    _searchDebounce = Timer(const Duration(milliseconds: 400), () async {
      setState(() => _searching = true);
      final near = _lastFix == null
          ? null
          : LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
      final results = await _placeSearch.search(query, near: near);
      if (!mounted) return;
      setState(() {
        _searchResults = results;
        _searching = false;
      });
    });
  }

  Future<void> _onSelectResult(NominatimResult result) async {
    final origin = _lastFix == null
        ? null
        : LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
    setState(() {
      _showSearch = false;
      _searchResults = [];
      _searchController.clear();
    });
    FocusManager.instance.primaryFocus?.unfocus();
    if (origin == null) return;
    final routes = await RoutingService.getRoutes(origin, result.position,
        lang: 'it', vehicle: 'driving');
    if (!mounted || routes.isEmpty) return;
    final route = routes.first;
    await _ztl.updateIfNeeded(origin);
    final restricted = _ztl.classifyPoints(route.polyline);
    setState(() {
      _route = route;
      _routeRuns = _splitByZtl(route.polyline, restricted);
    });
    final controller = _controller;
    if (controller == null || route.polyline.isEmpty) return;
    unawaited(controller.fitBounds(
      bounds: LngLatBounds.fromPoints([
        for (final p in route.polyline) Geographic(lon: p.longitude, lat: p.latitude),
      ]),
      padding: const EdgeInsets.all(48),
      pitch: 0,
    ));
  }

  void _clearRoute() => setState(() {
        _route = null;
        _routeRuns = [];
      });

  void _onGps(GpsData data) {
    _lastFix = data;
    if (!mounted) return;
    setState(() {}); // refresh the status readout
    unawaited(_speedCameraSvc.updateIfNeeded(data.position).then((_) {
      if (mounted) setState(() {});
    }));
    if (!_followUser) return;
    _targetState = CameraFollowState(
      lat: data.position.latitude,
      lng: data.position.longitude,
      zoom: _camState?.zoom ?? 17,
      rotDeg: data.heading ?? _camState?.rotDeg ?? 0,
    );
    _startFollowTicker();
  }

  void _startFollowTicker() {
    if (_followTicker != null) return;
    _lastFollowFrameMs = DateTime.now().millisecondsSinceEpoch;
    _followTicker = Timer.periodic(const Duration(milliseconds: 16), (timer) {
      final controller = _controller;
      final target = _targetState;
      final from = _camState;
      if (!mounted || !_followUser || controller == null || target == null) {
        timer.cancel();
        _followTicker = null;
        return;
      }
      if (from == null) {
        // First frame: nothing to ease from yet, snap the tracked state to
        // the target and let the next frame actually ease.
        _camState = target;
        return;
      }
      final nowMs = DateTime.now().millisecondsSinceEpoch;
      final dtMs = (nowMs - (_lastFollowFrameMs ?? nowMs)).clamp(1, 100);
      _lastFollowFrameMs = nowMs;
      final next = CameraFollowEasing.step(from: from, target: target, dtMs: dtMs);
      if (next == null) return;
      _camState = next;
      unawaited(controller.moveCamera(
        center: Geographic(lon: next.lng, lat: next.lat),
        zoom: next.zoom,
        bearing: next.rotDeg,
      ));
      if (CameraFollowEasing.hasCaughtUp(next, target)) {
        timer.cancel();
        _followTicker = null;
      }
    });
  }

  void _recenter() {
    setState(() => _followUser = true);
    final fix = _lastFix;
    final controller = _controller;
    if (fix == null || controller == null) return;
    // A tap for recenter is a request to snap back now, not to rejoin the
    // continuous ease mid-flight — same split MapScreen keeps between its
    // ticker and _animateCamera for an explicit action.
    _camState = CameraFollowState(
      lat: fix.position.latitude,
      lng: fix.position.longitude,
      zoom: 17,
      rotDeg: fix.heading ?? _camState?.rotDeg ?? 0,
    );
    unawaited(controller.animateCamera(
      center: Geographic(lon: fix.position.longitude, lat: fix.position.latitude),
      zoom: 17,
      pitch: 45,
      bearing: fix.heading,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.watch<ThemeProvider>().effective.isDark;
    final c = AppTheme.build(isDark ? AppThemeId.darkNostr : AppThemeId.lightNostr)
        .extension<RoadstrColors>()!;
    // setStyle only after onMapCreated hands us a controller — until then
    // the initial style picks the right one so there's no light-then-dark
    // flash on first frame.
    if (_stylingDark != null && _stylingDark != isDark) {
      _stylingDark = isDark;
      _controller?.setStyle(_style(dark: isDark));
    }
    _stylingDark ??= isDark;

    return Scaffold(
      body: Stack(children: [
        MapLibreMap(
          options: MapOptions(
            initStyle: _style(dark: isDark),
            initCenter: const Geographic(lon: 12.5, lat: 42.5),
            initZoom: 17,
            initPitch: 45,
            gestures: const MapGestures.all(),
          ),
          onMapCreated: (controller) => _controller = controller,
          onEvent: (event) {
            if (event is MapEventStartMoveCamera &&
                event.reason == CameraChangeReason.apiGesture) {
              setState(() => _followUser = false);
            }
          },
          // A wide, translucent glow pass under a narrower solid core per
          // run — an approximation of MapScreen's "laser" route rendering
          // (halo + glow + two coloured rails), collapsed to two passes
          // since PolylineLayer here colours a whole layer, not a single
          // Polyline the way flutter_map's does.
          layers: [
            for (final run in _routeRuns) ...[
              PolylineLayer(
                polylines: [
                  Feature(
                      geometry: LineString.from(run.points.map(
                          (p) => Geographic(lon: p.longitude, lat: p.latitude))))
                ],
                color: (run.restricted ? _kZtlRed : c.accent)
                    .withValues(alpha: 0.28),
                width: 18,
              ),
              PolylineLayer(
                polylines: [
                  Feature(
                      geometry: LineString.from(run.points.map(
                          (p) => Geographic(lon: p.longitude, lat: p.latitude))))
                ],
                color: run.restricted ? _kZtlRed : c.accent,
                width: 9,
              ),
            ],
          ],
          children: [
            if (_speedCameraSvc.cachedCameras.isNotEmpty ||
                _parkingPosition != null)
              WidgetLayer(markers: [
                for (final cam in _speedCameraSvc.cachedCameras)
                  Marker(
                    point: Geographic(
                        lon: cam.position.longitude, lat: cam.position.latitude),
                    size: const Size(30, 30),
                    child: const OsmCameraPin(),
                  ),
                if (_parkingPosition != null)
                  Marker(
                    point: Geographic(
                        lon: _parkingPosition!.longitude,
                        lat: _parkingPosition!.latitude),
                    size: const Size(38, 38),
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.blue.shade600,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                        boxShadow: const [
                          BoxShadow(color: Colors.black38, blurRadius: 5)
                        ],
                      ),
                      child: const Icon(Icons.local_parking_rounded,
                          color: Colors.white, size: 20),
                    ),
                  ),
              ]),
            if (_lastFix != null)
              WidgetLayer(markers: [
                Marker(
                  point: Geographic(
                      lon: _lastFix!.position.longitude,
                      lat: _lastFix!.position.latitude),
                  size: const Size(48, 48),
                  // Not rotate:true — the camera already turns to face the
                  // direction of travel each fix (bearing: data.heading in
                  // _onGps), the same heading-up convention MapScreen's own
                  // nav camera uses. With the map already doing that turning,
                  // the cursor's job is only to sit still pointing up, the
                  // way every nav app's own vehicle icon does; pointing it at
                  // the true heading too would rotate it twice.
                  child: UserMarker(
                    heading: 0,
                    accent: c.accent,
                    cursorStyle: CursorStyle.fromStorage(Hive.box('settings')
                        .get(CursorStyle.storageKey)),
                    cursorColor: CursorColor.fromStorage(Hive.box('settings')
                        .get(CursorColor.storageKey)),
                  ),
                ),
              ]),
          ],
        ),
        Positioned(
          top: MediaQuery.of(context).padding.top + 12,
          left: 12,
          right: 12,
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Material(
                color: c.surface2.withValues(alpha: 0.92),
                shape: const CircleBorder(),
                child: IconButton(
                  icon: Icon(Icons.arrow_back, color: c.textPrimary),
                  onPressed: () {
                    if (_showSearch) {
                      FocusManager.instance.primaryFocus?.unfocus();
                      setState(() => _showSearch = false);
                    } else {
                      Navigator.of(context).pop();
                    }
                  },
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: PlaceSearchBar(
                  controller: _searchController,
                  colors: c,
                  onFocus: () => setState(() => _showSearch = true),
                  onChanged: _onSearchChanged,
                  onSubmitted: (_) {},
                  onClear: () {
                    _searchController.clear();
                    setState(() => _searchResults = []);
                  },
                ),
              ),
            ]),
            if (_showSearch && (_searching || _searchResults.isNotEmpty)) ...[
              const SizedBox(height: 8),
              SearchResultsList(
                results: _searchResults,
                isLoading: _searching,
                colors: c,
                onSelect: _onSelectResult,
                onSelectFavorite: (_) {},
              ),
            ],
            if (!_showSearch && _route == null) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: c.surface2.withValues(alpha: 0.92),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: c.border, width: 0.5),
                ),
                child: Text(
                  'Motore mappa: MapLibre (sperimentale)',
                  style: TextStyle(color: c.textSecondary, fontSize: 11),
                ),
              ),
            ],
            if (!_showSearch && _route != null) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: c.surface2.withValues(alpha: 0.92),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: c.border, width: 0.5),
                ),
                child: Row(children: [
                  Icon(Icons.route_outlined, color: c.accent, size: 18),
                  const SizedBox(width: 8),
                  Text(
                    '${(_route!.totalDistanceM / 1000).toStringAsFixed(1)} km · '
                    '${(_route!.totalDurationS / 60).round()} min',
                    style: TextStyle(color: c.textPrimary, fontSize: 13),
                  ),
                  const Spacer(),
                  InkWell(
                    onTap: _clearRoute,
                    child: Icon(Icons.close, color: c.textSecondary, size: 18),
                  ),
                ]),
              ),
            ],
          ]),
        ),
        Positioned(
          right: 12,
          bottom: MediaQuery.of(context).padding.bottom + 16,
          child: Material(
            color: c.surface2.withValues(alpha: 0.92),
            shape: const CircleBorder(),
            child: IconButton(
              icon: Icon(
                _followUser ? Icons.gps_fixed : Icons.gps_not_fixed,
                color: c.accent,
              ),
              onPressed: _recenter,
            ),
          ),
        ),
      ]),
    );
  }
}
