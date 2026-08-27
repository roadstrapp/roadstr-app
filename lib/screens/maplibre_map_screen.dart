// First real slice of the MapLibre rendering engine — reachable via the
// "mapEngine" setting (Impostazioni → Mappa), not merged in behind
// kDebugMode like the throwaway PoC it grew out of.
//
// Deliberately incomplete: this is the incremental migration from
// docs/rendering-engine-decision.md §7, not a replacement for MapScreen.
// Destination search, real routing, ZTL-aware route colouring, speed
// camera and parking markers, navigation (step advancement, distance-to-
// maneuver, arrival), off-route detection, rerouting and voice guidance
// are here; POI search and favourites-as-markers are not — each remaining
// piece is a later phase, migrated and tested on its own. Switching the
// setting to "maplibre" trades all of that away for tilt/rotate and
// native dark-mode styling; the settings copy says so.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';
import 'package:maplibre/maplibre.dart';
import 'package:provider/provider.dart';

import '../services/camera_follow.dart';
import '../services/gps_service.dart';
import '../services/kokoro/kokoro_tts_service.dart';
import '../services/kokoro/kokoro_voices.dart';
import '../services/navigation_guidance.dart';
import '../services/place_search_service.dart';
import '../services/route_progress.dart';
import '../services/routing_service.dart';
import '../services/speed_camera_service.dart';
import '../services/ztl_service.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../utils/geo.dart';
import '../widgets/cursor_painter.dart';
import '../widgets/map/map_markers.dart';
import '../widgets/nav/maneuver_symbol.dart';
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
  LatLng? _destination;
  List<({List<LatLng> points, bool restricted})> _routeRuns = [];

  // ── Navigation ─────────────────────────────────────────────────────────────
  // RouteProgress (lib/services/route_progress.dart) is the pure geometry;
  // this is just the state machine driving it. No voice guidance, no
  // off-route detection, no rerouting yet — this proves step advancement and
  // a distance-to-maneuver number against a real route, each of those other
  // pieces is its own increment on top.
  bool _isNavigating = false;
  bool _arrived = false;
  int _currentStepIdx = 0;
  List<double> _cumDist = [];
  List<double> _stepCumDist = [];
  double _distToNextStepM = 0;

  /// Route position (not GPS wobble) close enough to a maneuver's own point
  /// to advance past it. Wider than typical GPS accuracy so a fix that lands
  /// slightly short or past the exact point doesn't stall the step index.
  static const _advanceToleranceM = 15.0;

  /// How close to the final step's own point counts as arrived.
  static const _arrivalRadiusM = 30.0;

  // ── Voice guidance ─────────────────────────────────────────────────────────
  // KokoroTtsService and NavigationGuidance are both reused as-is. No
  // chained "then in 300 metres take the off-ramp" tail the way MapScreen
  // does it — this is the two-stage far/near announcement only, per
  // maneuver, not the follow-up clause.
  final _tts = KokoroTtsService();
  bool _voiceMuted = false;
  int _ttsAnnouncedFarIdx = -1;
  int _ttsAnnouncedNearIdx = -1;

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
    // Same settings keys MapScreen reads at startup — muting or changing
    // voice/speed/volume in Settings applies here too, since it is the same
    // app-wide preference, not a copy of it.
    final settings = Hive.box('settings');
    _voiceMuted = !(settings.get('voiceEnabled', defaultValue: true) as bool);
    _tts.setGender(settings.get('kokoroVoiceGender',
        defaultValue: kKokoroDefaultGender) as String);
    _tts.setSpeed(kKokoroSpeedStages[
        settings.get('kokoroSpeedStage', defaultValue: kKokoroDefaultSpeedStage)
            as int]);
    _tts.setVolume(
        (settings.get('kokoroVolume', defaultValue: 1.0) as num).toDouble());
    unawaited(_tts.init('it'));
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
    unawaited(_tts.dispose());
    super.dispose();
  }

  /// Tears down everything that could still call into the native map view
  /// (the follow ticker, most directly) before asking Navigator to pop —
  /// not after. dispose() runs once the pop's own transition settles, which
  /// leaves a window where a stray 16 ms ticker frame can still fire a
  /// moveCamera against a platform view partway through being torn down.
  /// The reported freeze on the back button is consistent with exactly
  /// that: a method-channel call left waiting on a native view that isn't
  /// answering back. Whether this is that, or a lower-level bug in a
  /// pre-1.0 plugin (its 0.3.6 changelog lists more than one platform-view
  /// disposal fix already), this at least removes the one call site in this
  /// screen that could trigger it.
  void _exitScreen() {
    _followTicker?.cancel();
    _followTicker = null;
    _controller = null;
    Navigator.of(context).pop();
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
    setState(() {
      _showSearch = false;
      _searchResults = [];
      _searchController.clear();
    });
    FocusManager.instance.primaryFocus?.unfocus();
    await _calculateRouteTo(result.position);
  }

  bool _calculatingRoute = false;

  /// Shared by search-result selection and tapping a point on the map —
  /// both just name a destination, everything after that is identical.
  ///
  /// Every early return used to be silent: no GPS fix yet, the routing call
  /// throwing (a network error, a malformed response), an empty result —
  /// each looked from the outside like "I tapped a destination and nothing
  /// happened", because nothing told the driver why. Every path here now
  /// says something.
  Future<void> _calculateRouteTo(LatLng dest) async {
    final origin = _lastFix == null
        ? null
        : LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
    if (origin == null) {
      _showSnack('In attesa del GPS…');
      return;
    }
    setState(() => _calculatingRoute = true);
    _destination = dest;
    ({RouteResult route, List<({List<LatLng> points, bool restricted})> runs})?
        fetched;
    try {
      fetched = await _fetchRoute(origin, dest);
    } catch (_) {
      if (!mounted) return;
      setState(() => _calculatingRoute = false);
      _showSnack('Errore nel calcolo del percorso');
      return;
    }
    if (!mounted) return;
    if (fetched == null) {
      setState(() => _calculatingRoute = false);
      _showSnack('Percorso non trovato');
      return;
    }
    setState(() {
      _route = fetched!.route;
      _routeRuns = fetched.runs;
      _calculatingRoute = false;
    });
    final controller = _controller;
    if (controller == null || fetched.route.polyline.isEmpty) return;
    unawaited(controller.fitBounds(
      bounds: LngLatBounds.fromPoints([
        for (final p in fetched.route.polyline) Geographic(lon: p.longitude, lat: p.latitude),
      ]),
      padding: const EdgeInsets.all(48),
      pitch: 0,
    ));
  }

  void _showSnack(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  /// Fetches a route from [origin] to [dest] and classifies it against ZTL —
  /// the work shared between a fresh destination pick and a reroute, so
  /// there is one place that does it, not two that can drift apart.
  Future<({RouteResult route, List<({List<LatLng> points, bool restricted})> runs})?>
      _fetchRoute(LatLng origin, LatLng dest) async {
    final routes =
        await RoutingService.getRoutes(origin, dest, lang: 'it', vehicle: 'driving');
    if (!mounted || routes.isEmpty) return null;
    final route = routes.first;
    await _ztl.updateIfNeeded(origin);
    final restricted = _ztl.classifyPoints(route.polyline);
    return (route: route, runs: _splitByZtl(route.polyline, restricted));
  }

  void _clearRoute() {
    unawaited(_tts.stop());
    setState(() {
      _route = null;
      _destination = null;
      _routeRuns = [];
      _isNavigating = false;
      _arrived = false;
    });
  }

  /// Distance off the route polyline past which the driver is no longer
  /// plausibly following it. Same threshold MapScreen._checkOffRoute uses —
  /// retuned in 0.4.12 against a real driving trace, not derived here.
  static const _offRouteThresholdM = 55.0;

  bool _isRerouting = false;

  /// Off-route check, called from [_onGps] while navigating. Deliberately
  /// simpler than MapScreen's own _checkOffRoute: perpendicular distance to
  /// the nearest polyline segment only, no direction/bearing check against
  /// the next waypoint — that catches a driver on the correct road but going
  /// the wrong way on a bidirectional street, which this does not yet.
  void _checkOffRoute(GpsData data) {
    final route = _route;
    final dest = _destination;
    if (route == null ||
        dest == null ||
        !_isNavigating ||
        _isRerouting ||
        _arrived) {
      return;
    }
    if (data.speedKmh < 1) return; // stationary — a red light, not off-route
    final pos = LatLng(data.position.latitude, data.position.longitude);
    if (Geo.distanceToPolylineM(pos, route.polyline) > _offRouteThresholdM) {
      unawaited(_rerouteAndNavigate(pos, dest));
    }
  }

  Future<void> _rerouteAndNavigate(LatLng origin, LatLng dest) async {
    if (_isRerouting) return;
    setState(() => _isRerouting = true);
    final fetched = await _fetchRoute(origin, dest);
    if (!mounted) return;
    if (fetched == null) {
      setState(() => _isRerouting = false);
      return;
    }
    _cumDist = RouteProgress.cumulativeDistances(fetched.route.polyline);
    _stepCumDist = [
      for (final step in fetched.route.steps)
        _cumDist[RouteProgress.nearestIndex(fetched.route.polyline, step.location)],
    ];
    // A rerouted step list is a different array — yesterday's announced
    // indices mean nothing against it, and would silently block every
    // announcement on the new route until they happened to be overwritten.
    _ttsAnnouncedFarIdx = -1;
    _ttsAnnouncedNearIdx = -1;
    setState(() {
      _route = fetched.route;
      _routeRuns = fetched.runs;
      _currentStepIdx = 0;
      _isRerouting = false;
    });
  }

  void _startNavigation() {
    final route = _route;
    if (route == null || route.steps.isEmpty || route.polyline.isEmpty) return;
    _cumDist = RouteProgress.cumulativeDistances(route.polyline);
    _stepCumDist = [
      for (final step in route.steps)
        _cumDist[RouteProgress.nearestIndex(route.polyline, step.location)],
    ];
    _ttsAnnouncedFarIdx = -1;
    _ttsAnnouncedNearIdx = -1;
    setState(() {
      _isNavigating = true;
      _currentStepIdx = 0;
      _arrived = false;
    });
    if (!_voiceMuted) unawaited(_tts.announceStart());
  }

  void _stopNavigation() {
    unawaited(_tts.stop());
    setState(() {
      _isNavigating = false;
      _arrived = false;
    });
  }

  /// Advances [_currentStepIdx] and recomputes [_distToNextStepM] from
  /// [data]. Called from [_onGps] before its own setState, not after — the
  /// two share one rebuild instead of triggering back to back.
  void _updateNavigationProgress(GpsData data) {
    final route = _route;
    if (route == null || _cumDist.isEmpty || _arrived) return;
    final pos = LatLng(data.position.latitude, data.position.longitude);
    final idx = RouteProgress.nearestIndex(route.polyline, pos);
    final routeProgressM = _cumDist[idx];
    while (_currentStepIdx + 1 < route.steps.length &&
        _stepCumDist[_currentStepIdx + 1] <= routeProgressM + _advanceToleranceM) {
      _currentStepIdx++;
    }
    final isLast = _currentStepIdx >= route.steps.length - 1;
    _distToNextStepM = isLast
        ? 0
        : (_stepCumDist[_currentStepIdx + 1] - routeProgressM)
            .clamp(0, double.infinity);
    if (isLast && Geo.distanceM(pos, route.steps.last.location) < _arrivalRadiusM) {
      _arrived = true;
      if (!_voiceMuted) unawaited(_tts.announceArrival());
      return;
    }
    if (!_voiceMuted && !isLast) _announceUpcoming(data.speedKmh, route);
  }

  /// Two-stage far/near announcement for the maneuver after the current
  /// step, same distances NavigationGuidance already computes for
  /// MapScreen — speed-scaled, not a fixed number, so the near cue actually
  /// gives enough warning at motorway speed instead of flattening at 100 km/h.
  void _announceUpcoming(double speedKmh, RouteResult route) {
    final nextIdx = _currentStepIdx + 1;
    final t = NavigationGuidance.thresholds(speedKmh, 'driving');
    if (_distToNextStepM < t.far + 20 &&
        _distToNextStepM >= t.near + 20 &&
        _ttsAnnouncedFarIdx != nextIdx) {
      _ttsAnnouncedFarIdx = nextIdx;
      unawaited(_tts.announceManeuver(
          route.steps[nextIdx].instruction,
          NavigationGuidance.spokenDistanceM(_distToNextStepM,
              imminentBelowM: t.near)));
    } else if (_distToNextStepM < t.near + 30 && _ttsAnnouncedNearIdx != nextIdx) {
      _ttsAnnouncedNearIdx = nextIdx;
      unawaited(_tts.announceManeuver(route.steps[nextIdx].instruction, 0));
    }
  }

  void _onGps(GpsData data) {
    _lastFix = data;
    if (!mounted) return;
    if (_isNavigating) _updateNavigationProgress(data);
    _checkOffRoute(data);
    setState(() {}); // refresh the status readout + navigation progress
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
            } else if (event is MapEventClick &&
                !_isNavigating &&
                !_calculatingRoute) {
              // Tapping a point sets it as the destination directly — no
              // reverse-geocoded label, unlike MapScreen's long-press
              // context menu, since there's nowhere here yet to show one.
              unawaited(_calculateRouteTo(
                  LatLng(event.point.lat, event.point.lon)));
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
                  //
                  // flat: true, though — without it the marker stays a
                  // billboard facing the camera dead-on regardless of pitch,
                  // which is why tilting with two fingers didn't visibly tilt
                  // the cursor. flat makes it lie down with the tilted ground
                  // plane, the way a nav app's own puck looks like it's
                  // sitting on the road once the camera pitches.
                  flat: true,
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
            // Search hidden once navigating — same as MapScreen: mid-route
            // search would need to feed a waypoint-insert or a fresh
            // destination flow, neither of which exists here yet.
            if (!_isNavigating) ...[
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
                        _exitScreen();
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
                    TextButton.icon(
                      onPressed: _startNavigation,
                      icon: Icon(Icons.navigation_outlined,
                          color: c.accent, size: 16),
                      label: Text('Naviga',
                          style: TextStyle(color: c.accent, fontSize: 13)),
                    ),
                    InkWell(
                      onTap: _clearRoute,
                      child: Icon(Icons.close, color: c.textSecondary, size: 18),
                    ),
                  ]),
                ),
              ],
            ],
            if (_isRerouting || _calculatingRoute) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: c.surface2.withValues(alpha: 0.92),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: c.border, width: 0.5),
                ),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: c.accent)),
                  const SizedBox(width: 8),
                  Text(
                      _isRerouting
                          ? 'Ricalcolo il percorso…'
                          : 'Calcolo il percorso…',
                      style: TextStyle(color: c.textSecondary, fontSize: 12)),
                ]),
              ),
              const SizedBox(height: 8),
            ],
            if (_isNavigating && _route != null && !_arrived) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  gradient: c.panelGradient,
                  color: c.panelGradient == null ? c.surface2 : null,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: c.border, width: 0.5),
                  boxShadow: [
                    BoxShadow(
                        color: Colors.black.withValues(alpha: 0.2),
                        blurRadius: 10)
                  ],
                ),
                child: Row(children: [
                  ManeuverSymbol(
                    step: _route!.steps[_currentStepIdx],
                    size: 44,
                    colors: c,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          _distToNextStepM < 30
                              ? 'ora'
                              : _distToNextStepM < 1000
                                  ? '${_distToNextStepM.round()} m'
                                  : '${(_distToNextStepM / 1000).toStringAsFixed(1)} km',
                          style: TextStyle(
                              color: c.textPrimary,
                              fontSize: 20,
                              fontWeight: FontWeight.w800),
                        ),
                        Text(
                          _route!.steps[_currentStepIdx].instruction,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: c.textSecondary, fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: Icon(
                        _voiceMuted
                            ? Icons.volume_off_rounded
                            : Icons.volume_up_rounded,
                        color: c.textSecondary),
                    onPressed: () {
                      setState(() => _voiceMuted = !_voiceMuted);
                      // Same key MapScreen's own mute toggle writes — one
                      // app-wide voice preference, not a copy of it.
                      Hive.box('settings').put('voiceEnabled', !_voiceMuted);
                      if (_voiceMuted) unawaited(_tts.stop());
                    },
                  ),
                  IconButton(
                    icon: Icon(Icons.close, color: c.textSecondary),
                    onPressed: _stopNavigation,
                  ),
                ]),
              ),
            ],
            if (_isNavigating && _arrived) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: const Color(0xFF22C55E),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Row(children: [
                  const Icon(Icons.flag_rounded, color: Colors.white),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text('Sei arrivato',
                        style: TextStyle(
                            color: Colors.white, fontWeight: FontWeight.w700)),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white),
                    onPressed: _clearRoute,
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
