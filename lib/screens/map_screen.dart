// Main map screen for Roadstr — the primary and only full-screen view.
//
// Responsibilities:
//   - Renders the tile map (flutter_map + OpenStreetMap tiles).
//   - Manages GPS follow mode and heading-up orientation.
//   - Hosts the routing workflow: search → alternative selection → preview → navigation.
//   - Subscribes to Nostr road events (kind-1315) via NostrRelayService and
//     renders them as markers; re-subscribes when the user moves > 2 km.
//   - Handles road-event reporting (kind-1315) and confirmation (kind-1316)
//     via either Amber (NIP-55) or local nsec signing.
//   - Orchestrates NIP-57 zaps via ZapService (NWC first, deep-link fallback).
//   - Shows a persistent navigation notification via NavigationNotificationService.
//
// Camera animation uses Timer.periodic (same as _northTimer) — frame-by-frame
// control over rotation, zoom, and centre with cubic ease-in-out easing.
// Timer is cancelled and recreated on each call; no ticker lifecycle issues.
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../services/gps_service.dart';
import '../services/location_service.dart';
import '../services/routing_service.dart';
import 'package:amberflutter/amberflutter.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:nostr_tools/nostr_tools.dart' show Nip19, Nip04, EventApi, Event;
import '../l10n/app_localizations.dart';
import '../models/favorite_place.dart';
import '../models/road_event.dart';
import '../services/navigation_notification_service.dart';
import '../services/kokoro/kokoro_tts_service.dart';
import '../services/nostr_relay_service.dart';
import 'package:sensors_plus/sensors_plus.dart';
import '../services/zap_service.dart';
import 'package:provider/provider.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../widgets/speedometer_widget.dart';
import 'settings_screen.dart';
import 'profile_screen.dart';

class MapScreen extends StatefulWidget {
  const MapScreen({super.key});
  @override
  State<MapScreen> createState() => _MapScreenState();
}

/// State for [MapScreen]. Uses [WidgetsBindingObserver] to react to app
/// [Ticker] to [_animateCamera] for smooth camera transitions.
class _MapScreenState extends State<MapScreen>
    with WidgetsBindingObserver {
  final _gps = GpsService();
  final _mapController = MapController();

  /// Current GPS position (updated by [_onGps]; falls back to Italy centre).
  LatLng _position = const LatLng(42.5, 12.5);
  double _initialZoom = 6.0;
  double _speed = 0;
  double _heading = 0;

  // ── Compass (magnetometer + accelerometer) ──────────────────────────────────
  /// Tilt-compensated compass bearing in degrees [0, 360), derived from the
  /// device magnetometer and accelerometer via the cross-product method.
  /// Computed entirely on-device; no data leaves the device.
  double _compassHeading = 0;
  AccelerometerEvent? _lastAccel;
  StreamSubscription<MagnetometerEvent>? _magnetSub;
  StreamSubscription<AccelerometerEvent>? _accelSub;
  int _lastCompassUiMs = 0;

  /// True once GPS permissions are granted AND the first valid position received.
  bool _gpsReady = false;
  /// True once the user has tapped the GPS button (permission flow in progress).
  bool _gpsRequested = false;
  /// When true, every GPS tick moves the camera to [_position].
  bool _followUser = false;
  /// When true, the map rotates to face the direction of travel (heading-up mode).
  bool _headingMode = false;

  // ── Camera animation state ──────────────────────────────────────────────────
  // These fields track the camera's current visual state independently of
  // flutter_map's internal state so the ticker can interpolate smoothly.

  double _camRotDeg = 0;
  double _camZoom = 6.0;
  LatLng _camCenter = const LatLng(42.5, 12.5);

  /// Active camera animation timer. Non-null while animating; cancelled when
  /// the animation finishes or a user gesture interrupts it.
  Timer? _camTicker;
  /// Default animation duration for user-triggered camera transitions (ms).
  static const _animMs = 550;
  /// Shorter duration used for GPS-tracking animation — keeps motion fluid.
  static const _gpsAnimMs = 380;

  RouteResult? _route;
  LatLng? _destination;
  int _currentStepIdx = 0;
  bool _isNavigating = false;
  bool _isCalculating = false;

  // ── Navigation live stats ──────────────────────────────────────────────────
  /// Real-time distance from current GPS position to the next maneuver point.
  double _distToNextManeuverM = 0;
  /// Remaining route distance in metres (decreases as the user advances).
  double _remainingDistM = 0;
  /// Remaining route time in seconds (estimated from remaining distance).
  double _remainingSecs = 0;
  /// Posted speed limit at the current route position (null = unknown / no data).
  int? _currentSpeedLimit;
  /// Previous GPS position used to compute movement-based heading when the
  /// device magnetometer / GPS bearing is unavailable.
  LatLng? _prevGpsPos;

  final _searchController = TextEditingController();
  List<NominatimResult> _searchResults = [];
  bool _isSearching = false;
  bool _showSearch = false;
  Timer? _searchDebounce;

  /// Alternative routes returned by OSRM. Shown in [_AlternativesPanel] when
  /// the route is > 5 km (shorter routes go directly to the preview panel).
  List<RouteResult> _alternatives = [];
  int _selectedAlt = 0;
  bool _showAlternatives = false;

  final _nostr    = NostrRelayService();
  final _navNotif = NavigationNotificationService();
  final _tts      = KokoroTtsService();
  bool _voiceMuted = false;

  // ── Voice guidance state ────────────────────────────────────────────────────
  /// Index of the last step for which a 200m announcement was made.
  int _ttsAnnounced200 = -1;
  /// Index of the last step for which a 50m announcement was made.
  int _ttsAnnounced50  = -1;

  bool _isRerouting = false;
  /// IDs of Nostr traffic events already shown as an in-navigation alert.
  final _alertedEventIds = <String>{};
  /// Live list of non-expired road events fed by [NostrRelayService.stream].
  List<RoadEvent> _roadEvents = [];
  StreamSubscription<List<RoadEvent>>? _roadSub;
  Timer? _eventCleanupTimer;

  /// Route displayed in the preview panel before the user confirms navigation.
  RouteResult? _previewRoute;
  bool _showPreview = false;

  List<_HistoryItem> _history = [];
  List<FavoritePlace> _favorites = [];
  /// Destination label derived from reverse geocode or the search result name.
  /// Stored here so it survives async gaps between geocoding and navigation start.
  String? _pendingLabel;

  // ── Transport mode ──────────────────────────────────────────────────────
  /// Active transport mode. 'driving' = car; 'walking' = pedestrian.
  /// Determines which OSRM profile (driving/foot) is used for route calculation.
  String _transportMode = 'driving';

  // ── Navigation cinematic transition ─────────────────────────────────────
  /// True while the zoom-in animation plays at navigation start.
  /// GPS camera updates are suppressed during this window so they don't fight
  /// the choreographed transition.
  bool _navTransitioning = false;

  // ── Search pin (dropped on Enter in search bar) ─────────────────────────
  NominatimResult? _pinResult;

  // ── North animation (Timer-based, independent of the ticker system) ─────
  Timer? _northTimer;

  // ── Place info (map tap) ─────────────────────────────────────────────────
  bool _showPlaceInfo   = false;
  LatLng? _placePoint;
  String? _placeAddress;
  /// Best Nominatim-derived term used for the Wikipedia geo-search fallback.
  String? _placeWikiQuery;
  WikiSummary? _placeWiki;
  bool _placeLoading    = false;

  // ── Route planner (A→B) ────────────────────────────────────────────────────
  bool _showPlanner  = false;
  /// Origin waypoint. `null` means "My Location" (use live GPS position).
  _WayPoint? _planFrom;
  _WayPoint? _planTo;
  /// 0 = "From" field active, 1 = "To" field active.
  int  _planActiveField = 0;
  final _planFromCtrl = TextEditingController();
  final _planToCtrl   = TextEditingController();
  List<NominatimResult> _planResults = [];
  bool _planSearching  = false;
  Timer? _planDebounce;

  StreamSubscription<GpsData>? _gpsSub;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _voiceMuted = !(Hive.box('settings')
        .get('voiceEnabled', defaultValue: true) as bool);
    // Warm up the TTS engine at app start so it is ready when navigation begins.
    final startLang = Hive.box('settings').get('language', defaultValue: '') as String;
    _tts.init(startLang.isNotEmpty ? startLang : 'it');
    _loadInitialPosition();
    _loadHistory();
    _loadFavorites();
    _nostr.connect();
    _roadSub = _nostr.stream.listen((events) {
      if (mounted) setState(() =>
          _roadEvents = events.where((e) => !e.isExpired).toList());
      if (mounted && _isNavigating && _route != null) {
        _checkNewTrafficOnRoute(events);
      }
    });
    _startCompass();
    // Option C: start GPS silently at launch so it's ready when the user navigates.
    WidgetsBinding.instance.addPostFrameCallback((_) => _requestGps(silent: true));
    // Periodically prune expired road events so they disappear from the map
    // even when no GPS ticks or Nostr stream updates are happening.
    _eventCleanupTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (!mounted) return;
      final pruned = _roadEvents.where((e) => !e.isExpired).toList();
      if (pruned.length != _roadEvents.length) {
        setState(() => _roadEvents = pruned);
      }
    });
  }

  /// Subscribes to the device magnetometer and accelerometer to compute a
  /// tilt-compensated compass bearing. All processing is on-device; no data
  /// is transmitted to any external server.
  void _startCompass() {
    _accelSub = accelerometerEventStream().listen((e) => _lastAccel = e);
    _magnetSub = magnetometerEventStream().listen((mag) {
      final acc = _lastAccel;
      if (acc == null) return;
      final az = _compassAzimuth(acc, mag);
      if (!az.isFinite) return;

      // Exponential low-pass filter with wrap-around.
      // α=0.15: slow enough to suppress sensor noise, fast enough to feel responsive.
      double diff = az - _compassHeading;
      while (diff > 180) diff -= 360;
      while (diff < -180) diff += 360;
      _compassHeading += diff * 0.15;
      _compassHeading = _compassHeading % 360;
      if (_compassHeading < 0) _compassHeading += 360;

      // Throttle all map + UI updates to ~10 Hz.
      // Calling _mapController.rotate() at the raw 50 Hz magnetometer rate
      // overwhelms flutter_map: frames pile up, causing East↔West oscillation.
      // At 10 Hz we smooth-animate each update with a 200 ms ease-in-out so
      // consecutive animations chain seamlessly — fluid compass following.
      final now = DateTime.now().millisecondsSinceEpoch;
      if (now - _lastCompassUiMs >= 100) {
        _lastCompassUiMs = now;
        // During navigation the GPS movement bearing drives _heading and the
        // camera — the compass must NOT overwrite it or the cursor will point
        // in the phone's physical orientation rather than the direction of travel.
        if (mounted && !_isNavigating) setState(() => _heading = _compassHeading);
      }
    });
  }

  /// Tilt-compensated compass azimuth (degrees, 0 = North, clockwise).
  ///
  /// Uses the device's magnetometer + accelerometer to compute the azimuth of
  /// the phone's −Z axis (the "back of the phone" = the direction of travel
  /// when holding the phone in portrait and looking at the screen).
  ///
  /// Formula derivation (verified for N/E/S/W in portrait):
  ///   East  = normalize(gravity × magnetic)   — perpendicular to both
  ///   nz    = (East × gravity) · ẑ            — z-component of horizontal North
  ///   az    = atan2(−ez, −nz)                 — project −Z (back of phone) onto East/North
  ///
  /// atan2(−mag.x, mag.y) was the previous formula; it works only for a flat
  /// phone (Y horizontal). In portrait (Y vertical), the mag.y component picks
  /// up the large downward inclination field (B_v ≈ 0.94·B in Europe), making
  /// mag.y negative when pointing North → formula returned 180° (South). Fixed.
  ///
  /// All computation is on-device; no sensor data is transmitted externally.
  double _compassAzimuth(AccelerometerEvent acc, MagnetometerEvent mag) {
    // Gravity vector: sensors_plus gives reaction force, negate to get gravity.
    double gx = -acc.x, gy = -acc.y, gz = -acc.z;
    final gN = math.sqrt(gx*gx + gy*gy + gz*gz);
    if (gN < 0.1) return _compassHeading; // free-fall / near-zero reading
    gx /= gN; gy /= gN; gz /= gN;

    // East = gravity × magnetic (cross product, then normalise).
    double ex = gy*mag.z - gz*mag.y;
    double ey = gz*mag.x - gx*mag.z;
    double ez = gx*mag.y - gy*mag.x;
    final eN = math.sqrt(ex*ex + ey*ey + ez*ez);
    if (eN < 0.1) return _compassHeading; // mag ≈ parallel to gravity (pole)
    ex /= eN; ey /= eN; ez /= eN;

    // North (horizontal) = East × gravity — only the z-component is needed.
    final nz = ex*gy - ey*gx;

    // Azimuth of the phone's −Z axis (back of the phone = direction of travel).
    // The user holds the phone with the screen toward them; the back faces forward.
    // East·(−ẑ) = −ez,  North·(−ẑ) = −nz
    // Verified for portrait: N→0°, E→90°, S→180°, W→270°.
    var az = math.atan2(-ez, -nz) * 180 / math.pi;
    if (az < 0) az += 360;
    return az;
  }

  Future<void> _loadInitialPosition() async {
    final result = await LocationService.getInitialPosition();
    if (!mounted) return;
    setState(() {
      _position = result.position;
      _camCenter = result.position;
      _initialZoom = result.zoom;
      _camZoom = result.zoom;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _mapController.move(_position, _initialZoom);
      // Subscribe to road events for the initial area even before GPS is active.
      _nostr.subscribeArea(_position);
    });
  }

  /// Starts GPS acquisition, with [silent] = true suppressing error dialogs.
  ///
  /// [silent] is used for the automatic launch-time acquisition (Option C) so
  /// no dialog is shown if the service is disabled or permission is denied;
  /// the user can always tap the GPS button to see the error later.
  ///
  /// [_gpsRequested] is set synchronously **before** the first await to prevent
  /// a race where a concurrent user tap passes the guard while the auto-start
  /// call is suspended, leading to two stream listeners on the same GPS stream.
  Future<void> _requestGps({bool silent = false}) async {
    if (_gpsRequested) {
      if (!silent && _gpsReady) _recenter();
      return;
    }
    // Lock before the first await — prevents concurrent auto-start + user-tap races.
    setState(() => _gpsRequested = true);
    final serviceEnabled = await _gps.isServiceEnabled();
    if (!mounted) return;
    if (!serviceEnabled) {
      setState(() => _gpsRequested = false);
      if (!silent) _showGpsDisabledDialog();
      return;
    }
    final ok = await _gps.start();
    if (!mounted) return;
    if (!ok) {
      setState(() => _gpsRequested = false);
      if (!silent) _showGpsDisabledDialog();
      return;
    }
    setState(() {
      _gpsReady   = true;
      _followUser = true;   // next GPS tick will move camera to the real position
      // _headingMode intentionally left as-is (false by default).
      // Navigation sets it to true explicitly; the heading button toggles it.
      // Auto-enabling it here would make the first heading-button tap a no-op
      // whenever the device is stationary (heading = 0 → delta = 0 → no animation).
      _camZoom    = 17.0;
    });
    _gpsSub = _gps.stream.listen(_onGps);
    // Do NOT call _recenter() here: _position is still the Italy fallback and
    // jumping there would be jarring. The first valid GPS sample in _onGps
    // will move the map to the actual location via the _followUser flag.
    // For the silent path, _nostr.subscribeArea is skipped intentionally:
    // _loadInitialPosition already subscribed to the area, and _onGps
    // re-subscribes with the real position on the first fix. Subscribing here
    // would send a REQ for the Italy fallback for every user outside Italy.
    if (!silent) _nostr.subscribeArea(_position);
  }

  void _showGpsDisabledDialog() {
    final c = RoadstrColors.of(context);
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          Icon(Icons.gps_off_rounded, color: c.accent, size: 22),
          const SizedBox(width: 8),
          Text(AppLocalizations.of(context)!.gpsNotActiveTitle,
              style: TextStyle(color: c.textPrimary, fontSize: 16,
              fontWeight: FontWeight.bold)),
        ]),
        content: Text(AppLocalizations.of(context)!.gpsDisabledMessage,
            style: TextStyle(color: c.textSecondary, fontSize: 13,
                height: 1.5)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(AppLocalizations.of(context)!.ok,
                style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () async {
              Navigator.pop(context);
              await _gps.openSettings();
            },
            child: Text(AppLocalizations.of(context)!.openSettings,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  /// Position at which [NostrRelayService.subscribeArea] was last called.
  /// Used to throttle re-subscriptions: a new subscription is only sent when
  /// the user has moved more than 2 km from this point. This prevents the relay
  /// from being spammed with REQ/CLOSE pairs on every GPS tick while navigating.
  LatLng? _lastSubscribedPos;

  /// Handles each GPS sample emitted by [GpsService.stream].
  ///
  /// Coordinate validity: GPS readings must be rechecked here (even though
  /// [GpsService._onPosition] already filters) because stream events can be
  /// replayed from [GpsData.lastData] after reconnect.
  ///
  /// Geohash re-subscription: the 2 km threshold was chosen so that a precision-4
  /// geohash cell (~40 km wide) always stays covered — moving 2 km never crosses
  /// more than one cell boundary, so stale road events near the edge are minimal.
  ///
  /// GPS follow mode: [_mapController.moveAndRotate] is called directly (not via
  /// [_animateCamera]) during navigation because an animated transition would
  /// introduce visible lag between the user's position and the map's camera.
  /// The ticker guard (`_camTicker == null`) ensures that an in-progress animated
  /// transition is not interrupted by a GPS update.
  void _onGps(GpsData data) {
    if (!mounted) return;
    final lat = data.position.latitude;
    final lng = data.position.longitude;
    if (!lat.isFinite || !lng.isFinite ||
        lat < -90 || lat > 90 || lng < -180 || lng > 180) return;

    context.read<ThemeProvider>().onPositionUpdate(lat, lng);

    // ── Heading resolution ───────────────────────────────────────────────────
    // Prefer the GPS movement bearing (two-position dead reckoning) when moving
    // — it always reflects the actual direction of travel, regardless of how
    // the phone is mounted or oriented.
    // Fall back to the GPS-provider heading (pos.heading) only when too slow
    // for a reliable bearing, and only if it carries a valid non-zero value.
    double effectiveHeading = _heading;
    if (_prevGpsPos != null && _speed > 3) {
      final movBearing = _bearingBetween(_prevGpsPos!, data.position);
      if (movBearing.isFinite) effectiveHeading = movBearing;
    } else if (data.heading != null && data.heading!.isFinite && data.heading! > 0) {
      effectiveHeading = data.heading!;
    }
    _prevGpsPos = data.position;

    // ── Speed-adaptive zoom ──────────────────────────────────────────────────
    // Zoom in when slow (pedestrian, city) and out at high speed (motorway)
    // so the driver always sees enough road ahead.
    double targetZoom = _camZoom;
    if (_isNavigating) {
      final spd = data.speedKmh.isFinite ? data.speedKmh : 0;
      targetZoom = spd < 10  ? 18.0
                 : spd < 30  ? 17.5
                 : spd < 60  ? 17.0
                 : spd < 100 ? 16.5
                 :              16.0;
      // Ease toward the target zoom — abrupt jumps are disorienting.
      targetZoom = _camZoom + (targetZoom - _camZoom) * 0.12;
    }

    setState(() {
      _position = data.position;
      _speed    = data.speedKmh.isFinite ? data.speedKmh : 0;
      // During navigation show direction of travel; outside navigation show the
      // compass bearing updated by the magnetometer stream (_compassHeading).
      if (_isNavigating) _heading = effectiveHeading;
    });

    // ── Geohash re-subscription ──────────────────────────────────────────────
    final prev = _lastSubscribedPos;
    if (prev == null ||
        const Distance().as(LengthUnit.Kilometer, data.position, prev) > 2) {
      _lastSubscribedPos = data.position;
      _nostr.subscribeArea(data.position);
    }

    // ── Navigation camera + step logic ───────────────────────────────────────
    if (_isNavigating && _gpsReady && !_navTransitioning) {
      if (_route != null) {
        _updateNavigationStep();
        _updateRemainingStats();
        _checkArrival();
      }
      if (Hive.box('settings').get('keepScreenOn', defaultValue: true) as bool) {
        WakelockPlus.enable();
      }

      if (_followUser) {
        // During navigation use the GPS movement bearing (effectiveHeading) so
        // the map rotates to face the direction of travel, not where the phone
        // physically points (which is _compassHeading from the magnetometer).
        final rot = _headingMode ? -effectiveHeading : _camRotDeg;
        _animateCamera(
            toCenter: _position, toZoom: targetZoom, toRot: rot,
            durationMs: _gpsAnimMs);
      }
    } else if (_followUser && _camTicker == null && _northTimer == null) {
      // Outside navigation: follow GPS position, keep whatever rotation the user
      // set (north button, manual gesture). Never override rotation with compass.
      _mapController.moveAndRotate(_position, _camZoom, _camRotDeg);
      _camCenter = _position;
    }
  }

  /// Computes the initial bearing (degrees, 0–360 clockwise from north) from
  /// [from] to [to] using the haversine formula.
  static double _bearingBetween(LatLng from, LatLng to) {
    final lat1 = from.latitude  * math.pi / 180;
    final lat2 = to.latitude    * math.pi / 180;
    final dLon = (to.longitude  - from.longitude) * math.pi / 180;
    final y = math.sin(dLon) * math.cos(lat2);
    final x = math.cos(lat1) * math.sin(lat2) -
              math.sin(lat1) * math.cos(lat2) * math.cos(dLon);
    return (math.atan2(y, x) * 180 / math.pi + 360) % 360;
  }

  /// Recalculates [_remainingDistM] and [_remainingSecs] from the current step
  /// index.  Called on every GPS tick during navigation.
  void _updateRemainingStats() {
    if (_route == null) return;
    double rem = 0;
    for (int i = _currentStepIdx; i < _route!.steps.length; i++) {
      rem += _route!.steps[i].distanceM;
    }
    // Subtract the portion of the current step already completed.
    if (_currentStepIdx < _route!.steps.length) {
      final stepDist = _route!.steps[_currentStepIdx].distanceM;
      if (stepDist > 0 && _distToNextManeuverM < stepDist) {
        rem -= (stepDist - _distToNextManeuverM);
      }
    }
    final totalDist = _route!.totalDistanceM;
    final elapsedM  = (totalDist - rem).clamp(0.0, totalDist);
    setState(() {
      _remainingDistM    = rem.clamp(0, totalDist);
      _remainingSecs     = totalDist > 0
          ? (_route!.totalDurationS * _remainingDistM / totalDist)
          : 0;
      _currentSpeedLimit = _route!.speedLimitAt(elapsedM);
    });
  }

  /// Checks whether the user has reached the destination.
  /// Triggers when GPS is within 40 m of the destination point, OR when the
  /// remaining route distance drops below 50 m (handles GPS drift near arrival).
  void _checkArrival() {
    if (_destination == null || !_isNavigating) return;
    final d = const Distance().as(LengthUnit.Meter, _position, _destination!);
    if (d < 40 || _remainingDistM < 50) _onArrival();
  }

  void _onArrival() {
    if (!_voiceMuted) _tts.announceArrival();
    _stopNavigation();
    _showArrivalDialog();
  }

  // App Nostr account for user feedback (DM via NIP-04 kind-4)
  static const _appNpub =
      'npub1cwft0dmtw5gtwhmu5r2f8fjpw0l5f006egs8fmltdc3jrxxcpe6q965mmc';

  void _showArrivalDialog() {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        title: Text(l.arrivedTitle,
            style: const TextStyle(fontSize: 22), textAlign: TextAlign.center),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(l.arrivedBody,
              textAlign: TextAlign.center,
              style: TextStyle(color: c.textSecondary, fontSize: 14)),
          const SizedBox(height: 16),
          Text(l.arrivedFeedbackPrompt,
              style: TextStyle(color: c.textPrimary, fontSize: 15,
                  fontWeight: FontWeight.bold)),
        ]),
        actionsAlignment: MainAxisAlignment.center,
        actions: [
          // Bad experience → send DM feedback
          OutlinedButton.icon(
            onPressed: () {
              Navigator.pop(ctx);
              _showFeedbackDialog();
            },
            style: OutlinedButton.styleFrom(
                side: BorderSide(color: c.border),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12))),
            icon: const Text('👎', style: TextStyle(fontSize: 16)),
            label: Text(l.feedbackBad, style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton.icon(
            onPressed: () => Navigator.pop(ctx),
            style: FilledButton.styleFrom(
                backgroundColor: c.accent,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12))),
            icon: const Text('👍', style: TextStyle(fontSize: 16)),
            label: Text(l.feedbackGood,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _showFeedbackDialog() {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context)!;
    final ctrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        title: Text(l.feedbackDialogTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 15,
                fontWeight: FontWeight.bold)),
        content: TextField(
          controller: ctrl,
          maxLines: 4,
          style: TextStyle(color: c.textPrimary, fontSize: 13),
          decoration: InputDecoration(
            hintText: l.feedbackHint,
            hintStyle: TextStyle(color: c.textSecondary),
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10)),
            focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: BorderSide(color: c.accent)),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx),
              child: Text(l.cancel,
                  style: TextStyle(color: c.textSecondary))),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () async {
              final msg = ctrl.text.trim();
              Navigator.pop(ctx);
              if (msg.isEmpty) return;
              await _sendFeedbackDm(msg);
              if (mounted) _snack(l.feedbackSent);
            },
            child: Text(l.feedbackSubmit, style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }


  /// Sends a NIP-04 encrypted direct message (kind-4) to the app's Nostr
  /// account with the user's feedback text.
  Future<void> _sendFeedbackDm(String text) async {
    final privKey = await _secStorage.read(key: 'nostr_priv_hex');
    final pubKey  = await _secStorage.read(key: 'nostr_pub_hex');
    if (privKey == null || pubKey == null) return;
    try {
      final appPubHex = Nip19().decode(_appNpub)['data'] as String;
      final encrypted = Nip04().encrypt(privKey, appPubHex, text);
      final event = EventApi().finishEvent(
        Event(
          pubkey:     pubKey,
          created_at: DateTime.now().millisecondsSinceEpoch ~/ 1000,
          kind:       4,
          tags:       [['p', appPubHex]],
          content:    encrypted,
        ),
        privKey,
      );
      _nostr.publishRawEvent(event.toJson());
    } catch (_) {}
  }

  void _updateNavigationStep() {
    if (_route == null) return;
    _checkOffRoute();
    if (_currentStepIdx >= _route!.steps.length - 1) {
      _distToNextManeuverM = 0;
      return;
    }
    final nextIdx  = _currentStepIdx + 1;
    final nextStep = _route!.steps[nextIdx];
    final dist = const Distance().as(LengthUnit.Meter, _position, nextStep.location);
    _distToNextManeuverM = dist;

    // ── Voice guidance announcements ─────────────────────────────────────────
    // Announce at ~200 m (prep) and ~50 m (immediate) before each maneuver,
    // then again when the step is actually entered.
    if (dist < 220 && dist >= 50 && _ttsAnnounced200 != nextIdx) {
      _ttsAnnounced200 = nextIdx;
      if (!_voiceMuted) _tts.announceManeuver(nextStep.instruction, 200);
    } else if (dist < 50 && _ttsAnnounced50 != nextIdx) {
      _ttsAnnounced50 = nextIdx;
      if (!_voiceMuted) _tts.announceManeuver(nextStep.instruction, 0); // imminent — no distance prefix
    }

    if (dist < 80) {
      setState(() => _currentStepIdx++);
      _updateNavNotification();
    }
  }

  // ── Auto-reroute ────────────────────────────────────────────────────────────

  /// Checks whether the user has drifted off the active route.
  ///
  /// Uses segment-distance (nearest point on each polyline segment) rather than
  /// point-distance so sparse polylines don't miss deviations between waypoints.
  /// Triggers reroute when the user is >60 m from the route and moving.
  void _checkOffRoute() {
    if (_route == null || !_isNavigating || !_gpsReady || _isRerouting) return;
    if (_speed < 1) return; // truly stationary (red light etc.) — skip
    final poly = _route!.polyline;
    if (poly.length < 2) return;

    double minDist = double.infinity;
    final lat = _position.latitude;
    final lon = _position.longitude;

    for (int i = 0; i < poly.length - 1; i++) {
      final d = _distToSegment(
          lat, lon,
          poly[i].latitude,  poly[i].longitude,
          poly[i+1].latitude, poly[i+1].longitude);
      if (d < minDist) minDist = d;
      if (minDist < 40) return; // early exit: clearly on route
    }
    if (minDist > 60) _rerouteAndNavigate();
  }

  /// Minimum distance (metres) from point (px,py) to the line segment (ax,ay)→(bx,by).
  /// Uses an equirectangular approximation — accurate enough within 100 m.
  static double _distToSegment(
      double px, double py, double ax, double ay, double bx, double by) {
    // Convert degrees to approximate metres (equirectangular)
    const degM = 111320.0;
    final cosLat = math.cos(px * math.pi / 180);
    final dx = (bx - ax) * degM * cosLat;
    final dy = (by - ay) * degM;
    final len2 = dx*dx + dy*dy;
    if (len2 == 0) {
      // Degenerate segment (A == B): just distance to point A
      final ex = (px - ax) * degM * cosLat;
      final ey = (py - ay) * degM;
      return math.sqrt(ex*ex + ey*ey);
    }
    // Parameter t: projection of P onto the segment, clamped to [0, 1]
    final ex = (px - ax) * degM * cosLat;
    final ey = (py - ay) * degM;
    final t = ((ex*dx + ey*dy) / len2).clamp(0.0, 1.0);
    final cx = ex - t * dx;
    final cy = ey - t * dy;
    return math.sqrt(cx*cx + cy*cy);
  }

  /// Silently recalculates the best route from the current GPS position and
  /// restarts navigation without showing the preview/alternatives panels.
  Future<void> _rerouteAndNavigate() async {
    if (_destination == null || _isRerouting) return;
    setState(() => _isRerouting = true);
    final (:provider, :apiKey, :ghServer) = _resolveProvider();
    final lang = Localizations.localeOf(context).languageCode;
    List<RouteResult> routes;
    try {
      routes = await RoutingService.getRoutes(_position, _destination!,
              provider: provider, apiKey: apiKey, graphhopperServer: ghServer,
              lang: lang, vehicle: _transportMode)
          .timeout(const Duration(seconds: 15));
    } catch (_) {
      if (mounted) setState(() => _isRerouting = false);
      return;
    }
    if (!mounted || routes.isEmpty) {
      if (mounted) setState(() => _isRerouting = false);
      return;
    }
    setState(() => _isRerouting = false);
    // Silent restart: no cinematic overview, camera stays on driver position.
    _startNavigation(routes.first, silent: true);
  }

  // ── In-navigation traffic alert ──────────────────────────────────────────

  /// Called whenever the Nostr event stream delivers a new batch of events.
  /// If any NEW trafficJam event appears on the active route, shows a dialog
  /// offering the user to recalculate with an alternative route.
  void _checkNewTrafficOnRoute(List<RoadEvent> events) {
    if (_route == null) return;
    final dist = const Distance();
    final newJams = events.where((e) =>
        e.category == RoadCategory.trafficJam &&
        !e.isExpired &&
        !_alertedEventIds.contains(e.id) &&
        _route!.polyline.any(
            (p) => dist.as(LengthUnit.Meter, p, e.position) < 400)).toList();
    if (newJams.isEmpty) return;
    for (final e in newJams) _alertedEventIds.add(e.id);
    _showTrafficAlert(newJams.first);
  }

  void _showTrafficAlert(RoadEvent jam) {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          const Text('🔴', style: TextStyle(fontSize: 20)),
          const SizedBox(width: 8),
          Text(l.trafficAlertTitle,
              style: TextStyle(color: c.textPrimary, fontSize: 15,
                  fontWeight: FontWeight.bold)),
        ]),
        content: Text(
          l.trafficAlertBody(jam.category.localizedLabel(l), jam.ageLabel(l)),
          style: TextStyle(color: c.textSecondary, fontSize: 13, height: 1.5),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l.trafficContinue, style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () {
              Navigator.pop(context);
              _rerouteAndNavigate();
            },
            child: Text(l.trafficRecalculate,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _updateNavNotification() {
    if (_route == null || !_isNavigating) return;
    // Show the next upcoming maneuver, consistent with the in-app nav card.
    final nextIdx = (_currentStepIdx + 1 < _route!.steps.length)
        ? _currentStepIdx + 1 : _currentStepIdx;
    final step = _route!.steps[nextIdx];
    final distM = step.distanceM;
    if (!mounted) return;
    final distLabel = distM < 50
        ? AppLocalizations.of(context)!.now
        : distM < 1000
            ? '${distM.round()} m'
            : '${(distM / 1000).toStringAsFixed(1)} km';
    _navNotif.show(step.instruction, distLabel);
  }

  ({RoutingProvider provider, String? apiKey, String? ghServer}) _resolveProvider() {
    final box = Hive.box('settings');
    final providerKey = box.get('routingProvider', defaultValue: 'osrm') as String;
    final String? apiKey = (box.get('graphhopperApiKey', defaultValue: '') as String).trim().nullIfEmpty;
    final String? ghServer = (box.get('graphhopperServer', defaultValue: '') as String).trim().nullIfEmpty;

    RoutingProvider provider;
    switch (providerKey) {
      case 'graphhopper':
        if (ghServer != null) {
          provider = RoutingProvider.graphHopper;
        } else {
          provider = RoutingProvider.osrm;
          _snack(AppLocalizations.of(context)!.graphhopperServerNotConfigured);
        }
      case 'graphhopper_public':
        if (apiKey != null) {
          provider = RoutingProvider.graphHopper;
        } else {
          provider = RoutingProvider.osrm;
          _snack(AppLocalizations.of(context)!.graphhopperApiKeyNotConfigured);
        }
      case 'openroute':
        if (apiKey != null) {
          provider = RoutingProvider.openRoute;
        } else {
          provider = RoutingProvider.osrm;
          _snack(AppLocalizations.of(context)!.openrouteApiKeyNotConfigured);
        }
      default:
        provider = RoutingProvider.osrm;
    }
    return (provider: provider, apiKey: apiKey, ghServer: ghServer);
  }

  /// Starts navigation on [route].
  ///
  /// [silent] = true is used for automatic reroutes during an active journey.
  /// In silent mode the cinematic overview is skipped so the camera stays on
  /// the user's current position and GPS updates are never blocked.
  void _startNavigation(RouteResult route, {bool silent = false}) {
    // Option A safety net: block user-initiated navigation until GPS is ready.
    if (!silent && !_gpsReady) {
      _snack(AppLocalizations.of(context)!.acquiringGps);
      if (!_gpsRequested) _requestGps();
      return;
    }
    // Only save to history on explicit user-initiated navigation, not reroutes.
    if (!silent && _destination != null) {
      final label = _pendingLabel ??
          AppLocalizations.of(context)!.selectedPosition;
      _saveToHistory(label, _destination!);
    }
    _ttsAnnounced200 = -1;
    _ttsAnnounced50  = -1;
    final lang = Localizations.localeOf(context).languageCode;
    // Set language immediately so bundled assets resolve correctly even before
    // the model finishes loading.  Then init the model in the background for
    // maneuver synthesis.
    _tts.setLanguage(lang);
    unawaited(_tts.init(lang));
    if (!silent && !_voiceMuted) unawaited(_tts.announceStart());
    setState(() {
      _route = route;
      _currentStepIdx = 0;
      _isNavigating = true;
      _showAlternatives = false;
      _alternatives = [];
      _followUser = _gpsReady;
      _headingMode = _gpsReady;
    });
    if (Hive.box('settings').get('keepScreenOn', defaultValue: true) as bool) {
      WakelockPlus.enable();
    }

    if (silent) {
      // Reroute during active navigation: no overview zoom, no transition lock.
      // Just animate the camera to the current GPS position at nav zoom level.
      _animateCamera(
          toCenter: _position, toZoom: 17.0, toRot: -_heading);
      _updateNavNotification();
      // Announce the first step of the new route immediately.
      if (route.steps.isNotEmpty) {
        if (!_voiceMuted) _tts.announceManeuver(route.steps.first.instruction, 0);
      }
      return;
    }

    // ── Cinematic transition (first-start only) ───────────────────────────
    setState(() => _navTransitioning = true);
    _fitRouteOnMap(route); // Phase 1 — full-route overview

    if (_gpsReady) {
      Future.delayed(const Duration(milliseconds: 1600), () {
        if (!mounted || !_isNavigating) return;
        _animateCamera(
            toCenter: _position, toZoom: 17.0, toRot: -_compassHeading);
      });
      Future.delayed(const Duration(milliseconds: 1600 + 620), () {
        if (!mounted) return;
        setState(() => _navTransitioning = false);
        if (_isNavigating) _updateNavNotification();
      });
    } else {
      setState(() => _navTransitioning = false);
    }
  }

  Future<void> _requestAlternatives(LatLng destination,
      {String? label, LatLng? fromPosition}) async {
    // Block navigation when GPS hasn't acquired a real fix yet.
    // Using the Italy fallback (42.5, 12.5) as origin would produce a useless
    // route from the middle of the country to the destination.
    if (!_gpsReady && fromPosition == null) {
      _snack(AppLocalizations.of(context)!.acquiringGps);
      _requestGps();   // prompt the user to enable GPS
      return;
    }

    _pendingLabel = label;
    // Start reverse geocode in parallel (only for map taps where no label is known yet).
    final geocodeFuture = label == null
        ? RoutingService.reverseGeocode(destination)
        : null;

    if (!mounted) return;
    setState(() {
      _isCalculating = true;
      _destination = destination;
      _showSearch = false;
      _searchResults = [];
      _showAlternatives = false;
      _alternatives = [];
    });
    _searchController.clear();
    FocusScope.of(context).unfocus();

    final origin = fromPosition ?? _position; // _gpsReady guaranteed above
    final (:provider, :apiKey, :ghServer) = _resolveProvider();

    final lang = Localizations.localeOf(context).languageCode;
    List<RouteResult> routes;
    try {
      routes = await RoutingService.getRoutes(origin, destination,
          provider: provider, apiKey: apiKey, graphhopperServer: ghServer,
          lang: lang, vehicle: _transportMode);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isCalculating = false);
      _routingError(e.toString());
      return;
    }

    // Await geocode result (usually already done while routing was running).
    if (geocodeFuture != null) {
      final geocoded = await geocodeFuture;
      if (geocoded != null) _pendingLabel = geocoded;
    }

    if (!mounted) return;
    if (routes.isEmpty) {
      setState(() => _isCalculating = false);
      _routingError(AppLocalizations.of(context)!.noRouteFound);
      return;
    }

    setState(() => _isCalculating = false);

    if (routes.length > 1 && routes.first.totalDistanceM > 5000) {
      // Long route with alternatives → show selection panel.
      setState(() {
        _alternatives = routes;
        _selectedAlt  = 0;
        _showAlternatives = true;
      });
      _fitRoutesOnMap(routes);
    } else {
      // Any distance → show preview before starting navigation.
      _showRoutePreview(routes.first);
    }
  }

  void _showRoutePreview(RouteResult route) {
    setState(() {
      _previewRoute     = route;
      _showPreview      = true;
      _showAlternatives = false;
      _followUser       = false; // prevent GPS ticks from overriding the route fit
    });
    _fitRouteOnMap(route);
  }

  void _cancelPreview() {
    setState(() {
      _showPreview  = false;
      _previewRoute = null;
      _destination  = null;
    });
  }

  void _confirmPreview() {
    if (_previewRoute == null) return;
    final r = _previewRoute!;
    setState(() { _showPreview = false; _previewRoute = null; });
    _startNavigation(r);
  }

  List<RoadEvent> _routeTrafficEvents(RouteResult route) {
    const maxM = 400.0;
    final dist = const Distance();
    return _roadEvents.where((ev) => !ev.isExpired && route.polyline.any(
        (p) => dist.as(LengthUnit.Meter, p, ev.position) < maxM)).toList();
  }

  /// Returns a traffic severity badge for [route] based on the number of active
  /// Nostr trafficJam events within 400 m of the polyline.
  ({String label, Color color}) _trafficStatus(RouteResult route) {
    final jams = _roadEvents.where((e) =>
        e.category == RoadCategory.trafficJam && !e.isExpired &&
        route.polyline.any((p) =>
            const Distance().as(LengthUnit.Meter, p, e.position) < 400)).length;
    if (jams == 0) return (label: '🟢 Normal traffic',   color: const Color(0xFF22C55E));
    if (jams <= 2) return (label: '🟡 Moderate traffic', color: const Color(0xFFF59E0B));
    return               (label: '🔴 Heavy traffic',      color: const Color(0xFFEF4444));
  }

  /// Finds the index of the alternative route whose polyline is closest to [tap].
  ///
  /// Iterates every point of every alternative and returns the index of the one
  /// with the minimum distance to [tap], provided it is within [thresholdM].
  /// Returns -1 if no alternative is within the threshold (tap was on empty map).
  ///
  /// This allows the user to select an alternative by tapping directly on the
  /// grey route line rather than having to use the bottom panel buttons.
  int _nearestAlternative(LatLng tap) {
    const thresholdM = 60.0;
    final dist = const Distance();
    int best = -1;
    double bestD = thresholdM;
    for (int i = 0; i < _alternatives.length; i++) {
      for (final p in _alternatives[i].polyline) {
        final d = dist.as(LengthUnit.Meter, tap, p);
        if (d < bestD) { bestD = d; best = i; }
      }
    }
    return best;
  }

  // ── Place info ─────────────────────────────────────────────────────────────

  Future<void> _showPlaceInfoFor(LatLng point) async {
    setState(() {
      _placePoint    = point;
      _placeAddress  = null;
      _placeWiki     = null;
      _placeLoading  = true;
      _showPlaceInfo = true;
    });

    // Single Nominatim call with addressdetails=1.
    final geo = await RoutingService.reverseGeocodeDetail(point);
    if (!mounted) return;

    // Display human-readable address (first 3 comma-separated components).
    final dispParts = (geo?.display ?? '').split(',')
        .map((s) => s.trim()).where((s) => s.isNotEmpty).take(3).join(', ');
    setState(() => _placeAddress = dispParts.isEmpty ? null : dispParts);

    // Geo-aware Wikipedia search: coordinates first, then fallback to the place name.
    final wikiQ = geo?.wikiQuery;
    setState(() => _placeWikiQuery = wikiQ);
    final lang = Localizations.localeOf(context).languageCode;
    final wiki = await WikiSearch.fetchWikiNearby(
      point.latitude, point.longitude,
      lang:          lang,
      fallbackQuery: wikiQ,
      radiusM:       300,
    );
    if (!mounted) return;
    setState(() { _placeWiki = wiki; _placeLoading = false; });
  }

  void _closePlaceInfo() => setState(() {
    _showPlaceInfo   = false;
    _placePoint      = null;
    _placeAddress    = null;
    _placeWikiQuery  = null;
    _placeWiki       = null;
  });

  // ── In-place mode recalculation ────────────────────────────────────────────

  /// Switches the transport mode and recalculates routes **without closing**
  /// whichever panel is currently open (alternatives or preview).
  /// The [_isCalculating] overlay appears briefly during the network request
  /// while the panel remains visible underneath, avoiding a jarring full reset.
  Future<void> _recalculateForMode(String vehicle) async {
    if (_destination == null) return;
    setState(() { _transportMode = vehicle; _isCalculating = true; });

    if (!_gpsReady) { setState(() => _isCalculating = false); return; }
    final origin = _position;
    final (:provider, :apiKey, :ghServer) = _resolveProvider();
    final lang = Localizations.localeOf(context).languageCode;

    List<RouteResult> routes;
    try {
      routes = await RoutingService.getRoutes(origin, _destination!,
          provider: provider, apiKey: apiKey, graphhopperServer: ghServer,
          lang: lang, vehicle: vehicle);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isCalculating = false);
      _routingError(e.toString());
      return;
    }
    if (!mounted) return;
    if (routes.isEmpty) {
      setState(() => _isCalculating = false);
      return;
    }

    final multiRoute = routes.length > 1 &&
        routes.first.totalDistanceM > 5000;

    setState(() {
      _isCalculating = false;
      if (multiRoute) {
        _alternatives    = routes;
        _selectedAlt     = 0;
        _showAlternatives = true;
        _showPreview     = false;
        _previewRoute    = null;
      } else {
        _previewRoute    = routes.first;
        _showPreview     = true;
        _showAlternatives = false;
        _alternatives    = [];
      }
    });

    if (multiRoute) {
      _fitRoutesOnMap(routes);
    } else {
      _fitRouteOnMap(routes.first);
    }
  }

  // ── Route planner logic ────────────────────────────────────────────────────

  void _openPlanner() => setState(() {
    _showPlanner   = true;
    _planFrom      = null;
    _planTo        = null;
    _planActiveField = 1; // start with the "To" field active
    _planFromCtrl.text = AppLocalizations.of(context)!.myLocation;
    _planToCtrl.clear();
    _planResults   = [];
  });

  void _closePlanner() {
    _planDebounce?.cancel();
    setState(() { _showPlanner = false; _planResults = []; });
    FocusScope.of(context).unfocus();
  }

  void _onPlanSearch(String query, int field) {
    _planDebounce?.cancel();
    setState(() { _planActiveField = field; });
    if (field == 0) {
      final myLoc = AppLocalizations.of(context)!.myLocation;
      if (query.isEmpty || query == myLoc) {
        setState(() { _planFrom = null; _planResults = []; });
        return;
      }
    }
    if (query.length < 3) { setState(() => _planResults = []); return; }
    _planDebounce = Timer(const Duration(milliseconds: 500), () async {
      setState(() => _planSearching = true);
      final r = await RoutingService.search(query);
      if (!mounted) return;
      setState(() { _planResults = r; _planSearching = false; });
    });
  }

  void _selectPlanResult(NominatimResult r) {
    if (_planActiveField == 0) {
      setState(() {
        _planFrom = _WayPoint(r.shortName, r.position);
        _planFromCtrl.text = r.shortName;
        _planResults = [];
        _planActiveField = 1;
      });
      FocusScope.of(context).requestFocus(); // focus su campo To
    } else {
      setState(() {
        _planTo = _WayPoint(r.shortName, r.position);
        _planToCtrl.text = r.shortName;
        _planResults = [];
      });
      FocusScope.of(context).unfocus();
    }
  }

  Future<void> _calculatePlan() async {
    if (_planTo == null) return;
    final from = _planFrom?.position ?? (_gpsReady ? _position : _camCenter);
    _pendingLabel = _planTo!.label;
    _closePlanner();
    await _requestAlternatives(
      _planTo!.position,
      label:        _planTo!.label,
      fromPosition: from,
    );
  }

  void _cancelAlternatives() {
    setState(() {
      _showAlternatives = false;
      _alternatives     = [];
      _destination      = null;
    });
  }

  /// Animates the camera to fit the entire [route] polyline on screen.
  ///
  /// The zoom level is chosen from a lookup table based on the maximum of the
  /// latitude and longitude extents (in degrees). This avoids the overhead of
  /// computing a proper bounding-box projection — for Western European routes
  /// the degree-based heuristic is accurate enough (1° lat ≈ 111 km).
  ///
  /// Zoom level table:
  ///   > 2.0° → z7,  > 1.0° → z8,  > 0.5° → z9,  > 0.2° → z10,
  ///   > 0.1° → z11, > 0.05° → z12, > 0.02° → z13, > 0.01° → z14, else z15
  void _fitRouteOnMap(RouteResult route) {
    if (route.polyline.isEmpty) return;
    final pts = route.polyline.where(
        (p) => p.latitude.isFinite && p.longitude.isFinite &&
               p.latitude >= -90 && p.latitude <= 90 &&
               p.longitude >= -180 && p.longitude <= 180).toList();
    if (pts.isEmpty) return;
    double minLat = pts.first.latitude;
    double maxLat = pts.first.latitude;
    double minLng = pts.first.longitude;
    double maxLng = pts.first.longitude;
    for (final p in pts) {
      if (p.latitude < minLat) minLat = p.latitude;
      if (p.latitude > maxLat) maxLat = p.latitude;
      if (p.longitude < minLng) minLng = p.longitude;
      if (p.longitude > maxLng) maxLng = p.longitude;
    }
    final center = LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);
    final maxDiff = math.max(maxLat - minLat, maxLng - minLng);
    double zoom;
    if      (maxDiff > 2.0)  zoom = 7;
    else if (maxDiff > 1.0)  zoom = 8;
    else if (maxDiff > 0.5)  zoom = 9;
    else if (maxDiff > 0.2)  zoom = 10;
    else if (maxDiff > 0.1)  zoom = 11;
    else if (maxDiff > 0.05) zoom = 12;
    else if (maxDiff > 0.02) zoom = 13;
    else if (maxDiff > 0.01) zoom = 14;
    else                     zoom = 15;
    _animateCamera(toCenter: center, toZoom: zoom, toRot: 0);
  }

  void _fitRoutesOnMap(List<RouteResult> routes) {
    if (routes.isEmpty) return;
    final pts = routes.expand((r) => r.polyline).where(
        (p) => p.latitude.isFinite && p.longitude.isFinite &&
               p.latitude >= -90 && p.latitude <= 90 &&
               p.longitude >= -180 && p.longitude <= 180).toList();
    if (pts.isEmpty) return;
    double minLat = pts.first.latitude;
    double maxLat = pts.first.latitude;
    double minLng = pts.first.longitude;
    double maxLng = pts.first.longitude;
    for (final p in pts) {
      if (p.latitude < minLat) minLat = p.latitude;
      if (p.latitude > maxLat) maxLat = p.latitude;
      if (p.longitude < minLng) minLng = p.longitude;
      if (p.longitude > maxLng) maxLng = p.longitude;
    }
    final center = LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);
    final maxDiff = math.max(maxLat - minLat, maxLng - minLng);
    double zoom;
    if      (maxDiff > 2.0)  zoom = 7;
    else if (maxDiff > 1.0)  zoom = 8;
    else if (maxDiff > 0.5)  zoom = 9;
    else if (maxDiff > 0.2)  zoom = 10;
    else if (maxDiff > 0.1)  zoom = 11;
    else if (maxDiff > 0.05) zoom = 12;
    else if (maxDiff > 0.02) zoom = 13;
    else if (maxDiff > 0.01) zoom = 14;
    else                     zoom = 15;
    _animateCamera(toCenter: center, toZoom: zoom, toRot: 0);
  }

  /// Returns the sub-segments of [polyline] that pass within [thresholdM] of an
  /// active traffic jam ([RoadCategory.trafficJam]) road event.
  ///
  /// The algorithm walks the polyline point by point, testing each point against
  /// all active jam events. When a point enters a jam zone a new segment is
  /// started; when it exits, the segment is closed and appended to the result.
  ///
  /// **Boundary inclusion:** the point immediately before a jam zone entry and
  /// the point immediately after the exit are included so that the red overlay
  /// visually connects to the blue route line without a visible gap.
  ///
  /// The returned segments are rendered as red [Polyline] overlays on the map,
  /// giving drivers a visual warning about congestion ahead on their route.
  List<List<LatLng>> _trafficSegments(List<LatLng> polyline) {
    const thresholdM = 400.0;
    final dist = const Distance();
    final jams = _roadEvents
        .where((e) => e.category == RoadCategory.trafficJam && !e.isExpired)
        .toList();
    if (jams.isEmpty || polyline.isEmpty) return [];

    final segments = <List<LatLng>>[];
    List<LatLng>? current;

    for (int i = 0; i < polyline.length; i++) {
      final p = polyline[i];
      final inJam = jams.any(
          (ev) => dist.as(LengthUnit.Meter, p, ev.position) < thresholdM);
      if (inJam) {
        // Include the previous point for visual continuity at the segment start.
        if (current == null && i > 0) current = [polyline[i - 1]];
        current ??= [];
        current.add(p);
      } else if (current != null) {
        current.add(p); // include the exit point for continuity at segment end
        if (current.length > 1) segments.add(List.from(current));
        current = null;
      }
    }
    if (current != null && current.length > 1) segments.add(current);
    return segments;
  }

  void _showExitNavigationDialog() {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          Icon(Icons.navigation_rounded, color: c.accent, size: 20),
          const SizedBox(width: 8),
          Text(l.navExitTitle,
              style: TextStyle(color: c.textPrimary, fontSize: 16,
                  fontWeight: FontWeight.bold)),
        ]),
        content: Text(l.navExitBody,
          style: TextStyle(color: c.textSecondary, fontSize: 13),
        ),
        actions: [
          OutlinedButton(
            onPressed: () => Navigator.pop(context),
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: c.accent),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: Text(l.navContinue,
                style: TextStyle(color: c.accent)),
          ),
          FilledButton(
            onPressed: () { Navigator.pop(context); _stopNavigation(); },
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: Text(l.navExit,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _stopNavigation() {
    setState(() {
      _route = null;
      _destination = null;
      _isNavigating = false;
      _isRerouting = false;
      _currentStepIdx = 0;
      _headingMode = false;
      _showAlternatives = false;
      _alternatives     = [];
      _showPreview      = false;
      _previewRoute     = null;
      // Clear alerted event IDs so fresh alerts can fire on the next journey.
      _alertedEventIds.clear();
    });
    _currentSpeedLimit = null;
    _animateCamera(toCenter: _position, toZoom: _camZoom, toRot: 0);
    WakelockPlus.disable();
    _navNotif.cancel();
    _tts.stop();
  }

  /// Favorites whose label or address contains [query] (case-insensitive).
  List<FavoritePlace> _matchingFavorites(String query) {
    if (query.isEmpty) return _favorites;
    final q = query.toLowerCase();
    return _favorites.where((f) =>
        f.label.toLowerCase().contains(q) ||
        f.address.toLowerCase().contains(q)).toList();
  }

  Future<void> _onSearchSubmit(String query) async {
    query = query.trim();
    if (query.isEmpty) return;
    _searchDebounce?.cancel();
    FocusScope.of(context).unfocus();
    // Use cached results if available; otherwise geocode the raw query.
    NominatimResult? result;
    final favMatch = _matchingFavorites(query);
    if (favMatch.isNotEmpty) {
      _searchController.clear();
      setState(() { _showSearch = false; _searchResults = []; _pinResult = null; });
      _requestAlternatives(favMatch.first.position, label: favMatch.first.label);
      return;
    }
    if (_searchResults.isNotEmpty) {
      result = _searchResults.first;
    } else {
      setState(() => _isSearching = true);
      final results = await RoutingService.search(query);
      setState(() => _isSearching = false);
      if (!mounted || results.isEmpty) return;
      result = results.first;
    }
    // Keep the search text so the user can refine if needed.
    setState(() {
      _showSearch    = false;
      _searchResults = [];
      _pinResult     = result;
      _followUser    = false;
    });
    // Wait for the keyboard-close layout pass before animating: unfocus() causes
    // Android to resize the window, which fires MapEventMoveStart (source ≠
    // controller) and cancels any ticker started too early.
    await Future<void>.delayed(const Duration(milliseconds: 200));
    if (!mounted) return;
    _animateCamera(
        toCenter: result!.position, toZoom: 15.0, toRot: _camRotDeg);
  }

  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    if (query.length < 3) {
      setState(() { _searchResults = []; _isSearching = false; });
      return;
    }
    _searchDebounce = Timer(const Duration(milliseconds: 500), () async {
      setState(() => _isSearching = true);
      final results = await RoutingService.search(query);
      if (!mounted) return;
      setState(() { _searchResults = results; _isSearching = false; });
    });
  }

  void _selectSearchResult(NominatimResult result) {
    // Save to history on every explicit selection, not just on navigation start.
    _saveToHistory(result.shortName, result.position);
    if (_isPoi(result)) {
      _showPlaceInfoForResult(result);
    } else {
      _requestAlternatives(result.position, label: result.shortName);
    }
  }

  /// Returns true when the Nominatim result is a point-of-interest rather than
  /// a plain postal address, road or administrative boundary.
  bool _isPoi(NominatimResult result) {
    const nonPoi = {'highway', 'boundary', 'landuse', 'postcode'};
    const placeAddressTypes = {
      'city', 'town', 'village', 'hamlet', 'suburb',
      'neighbourhood', 'quarter', 'municipality',
    };
    if (result.cls == null) return false;
    if (nonPoi.contains(result.cls)) return false;
    if (result.cls == 'place' && placeAddressTypes.contains(result.type)) return false;
    // Everything else (amenity, tourism, shop, historic, leisure, office…) is a POI
    return true;
  }

  /// Shows the place-info panel pre-populated with the Nominatim result,
  /// skipping the redundant reverse-geocode step since we already have the name.
  Future<void> _showPlaceInfoForResult(NominatimResult result) async {
    _pendingLabel = result.shortName;
    // Build a geo-disambiguated query: "Teodorico Ravenna" beats plain "Teodorico"
    // for both the Wikipedia fallback and the web-search button.
    final wikiQ = (result.city != null && result.city != result.shortName)
        ? '${result.shortName} ${result.city}'
        : result.shortName;

    setState(() {
      _placePoint     = result.position;
      _placeAddress   = result.displayName.split(',').take(3).join(', ');
      _placeWikiQuery = wikiQ;
      _placeWiki      = null;
      _placeLoading   = true;
      _showPlaceInfo  = true;
      _showSearch     = false;
      _searchResults  = [];
    });
    // Keep the search text so the user can refine without retyping.
    FocusScope.of(context).unfocus();

    final lang = Localizations.localeOf(context).languageCode;
    final wiki = await WikiSearch.fetchWikiNearby(
      result.position.latitude, result.position.longitude,
      lang:          lang,
      fallbackQuery: wikiQ,
      radiusM:       200,
    );
    if (!mounted) return;
    setState(() { _placeWiki = wiki; _placeLoading = false; });
  }

  void _recenter() {
    if (!_position.latitude.isFinite || !_position.longitude.isFinite) return;
    setState(() => _followUser = true);
    // Animate only the centre — zoom and rotation stay as they are so the user
    // doesn't lose their current view level or orientation just by recentering.
    _animateCamera(toCenter: _position, toZoom: _camZoom, toRot: _camRotDeg);
  }

  /// Rotates the map to north-up (0°) and recenters on the GPS position.
  /// Using _position (GPS) not _camCenter (current map pan) as target ensures
  /// the animation always has a visible delta even when _camRotDeg is already 0
  /// (e.g. second tap after GPS was following: center moves to latest GPS fix).
  /// Rotates the map to north (0°), recenters on GPS, and zooms to level 17.
  /// Uses a Timer instead of the custom ticker so it works independently of
  /// any stale animation state from previous _animateCamera calls.
  void _toggleHeadingMode() {
    setState(() { _headingMode = false; _followUser = true; });

    _northTimer?.cancel();
    _camTicker?.cancel();
    _camTicker = null;

    final startRot    = _mapController.camera.rotation;
    final startZoom   = _mapController.camera.zoom;
    final startCenter = _mapController.camera.center;
    final endCenter   = _gpsReady ? _position : startCenter;
    const dur = 550;
    final startMs = DateTime.now().millisecondsSinceEpoch;

    _northTimer = Timer.periodic(const Duration(milliseconds: 16), (timer) {
      if (!mounted) { timer.cancel(); _northTimer = null; return; }

      final elapsed = DateTime.now().millisecondsSinceEpoch - startMs;
      final p = (elapsed / dur).clamp(0.0, 1.0);
      // Cubic ease-in-out
      final e = p < 0.5 ? 4 * p * p * p : 1 - math.pow(-2 * p + 2, 3) / 2;

      // Shortest-arc rotation to 0°
      double rotDelta = 0.0 - startRot;
      while (rotDelta > 180) rotDelta -= 360;
      while (rotDelta < -180) rotDelta += 360;
      final rot  = startRot  + rotDelta * e;
      final zoom = startZoom + (17.0 - startZoom) * e;
      final lat  = startCenter.latitude  + (endCenter.latitude  - startCenter.latitude)  * e;
      final lng  = startCenter.longitude + (endCenter.longitude - startCenter.longitude) * e;

      _mapController.moveAndRotate(LatLng(lat, lng), zoom, rot);
      _camRotDeg = rot; _camZoom = zoom; _camCenter = LatLng(lat, lng);

      if (p >= 1.0) {
        timer.cancel();
        _northTimer = null;
        _camRotDeg = 0.0; _camZoom = 17.0; _camCenter = endCenter;
        _mapController.moveAndRotate(endCenter, 17.0, 0.0);
        if (mounted) setState(() {});
      }
    });
  }

  /// Smoothly animates the map camera to [toCenter] / [toZoom] / [toRot].
  ///
  /// **Why Ticker instead of AnimationController:**
  /// [AnimationController] animates a single `double` value. Here we need to
  /// simultaneously interpolate rotation, zoom, and a `LatLng` centre — three
  /// heterogeneous values — without creating three separate controllers that
  /// would need to be synchronised. A raw [Ticker] gives us a callback every
  /// frame where we manually apply the easing to all three values at once.
  ///
  /// **Easing curve:** cubic ease-in-out
  ///   - `t < 0.5` → `4t³`              (accelerates from rest)
  ///   - `t ≥ 0.5` → `1 − (−2t+2)³/2`  (decelerates to rest)
  ///
  /// **Rotation delta normalisation:** the shortest rotation arc is found by
  /// clamping the delta to [−180°, +180°]. This prevents the camera from
  /// spinning 350° clockwise when a 10° counter-clockwise rotation is intended.
  void _animateCamera({
    required LatLng toCenter, required double toZoom, required double toRot,
    int? durationMs,
  }) {
    if (!toCenter.latitude.isFinite || !toCenter.longitude.isFinite ||
        !toZoom.isFinite || !toRot.isFinite) return;
    final dur = durationMs ?? _animMs;
    _camTicker?.cancel();
    _camTicker = null;

    // Always read starting values from the actual flutter_map camera state.
    // _camRotDeg/_camCenter/_camZoom can be stale if MapEventMove arrived
    // out of order or a GPS tick updated flutter_map without updating our
    // internal copies — causing delta=0 and an invisible animation.
    final cam = _mapController.camera;
    final fromRot    = cam.rotation;
    final fromZoom   = cam.zoom;
    final fromCenter = cam.center;

    double delta = toRot - fromRot;
    while (delta > 180) delta -= 360;
    while (delta < -180) delta += 360;
    final endRot = fromRot + delta;

    final startMs = DateTime.now().millisecondsSinceEpoch;
    _camTicker = Timer.periodic(const Duration(milliseconds: 16), (timer) {
      if (!mounted) { timer.cancel(); _camTicker = null; return; }
      final t = ((DateTime.now().millisecondsSinceEpoch - startMs) / dur)
          .clamp(0.0, 1.0);
      // Cubic ease-in-out.
      final e = t < 0.5 ? 4*t*t*t : 1 - math.pow(-2*t+2, 3)/2;
      final rot  = fromRot  + (endRot  - fromRot)  * e;
      final zoom = (fromZoom + (toZoom  - fromZoom) * e).clamp(1.0, 22.0);
      final lat  = fromCenter.latitude  + (toCenter.latitude  - fromCenter.latitude)  * e;
      final lng  = fromCenter.longitude + (toCenter.longitude - fromCenter.longitude) * e;
      if (!lat.isFinite || lat < -90 || lat > 90 ||
          !lng.isFinite || lng < -180 || lng > 180 ||
          !zoom.isFinite || !rot.isFinite) return;
      _mapController.moveAndRotate(LatLng(lat, lng), zoom, rot);
      _camRotDeg = rot; _camZoom = zoom; _camCenter = LatLng(lat, lng);
      if (t >= 1.0) {
        _camRotDeg = endRot; _camZoom = toZoom; _camCenter = toCenter;
        timer.cancel();
        _camTicker = null;
        // Skip setState during active navigation: GPS ticks already trigger
        // frequent rebuilds via _updateRemainingStats.
        if (!_isNavigating) setState(() {});
      }
    });
  }

  void _loadHistory() {
    final raw = Hive.box('settings')
        .get('searchHistory', defaultValue: <dynamic>[]) as List<dynamic>;
    _history = raw.whereType<String>().map((s) {
      try { return _HistoryItem.fromJsonSafe(jsonDecode(s) as Map<String, dynamic>); }
      catch (_) { return null; }
    }).whereType<_HistoryItem>().toList();
  }

  void _loadFavorites() {
    final raw = Hive.box('settings')
        .get('favorites', defaultValue: <dynamic>[]) as List<dynamic>;
    _favorites = raw.whereType<String>().map((s) {
      try { return FavoritePlace.fromMapSafe(jsonDecode(s) as Map); }
      catch (_) { return null; }
    }).whereType<FavoritePlace>().toList();
  }

  void _saveToHistory(String label, LatLng pos) {
    final item = _HistoryItem(label, pos);
    final updated = [
      item,
      ..._history.where((h) =>
          (h.position.latitude - pos.latitude).abs() > 0.0001 ||
          (h.position.longitude - pos.longitude).abs() > 0.0001),
    ];
    if (updated.length > 5) updated.removeRange(5, updated.length);
    setState(() => _history = updated);
    Hive.box('settings').put('searchHistory',
        updated.map((h) => jsonEncode(h.toJson())).toList());
  }

  void _clearHistory() {
    setState(() => _history = []);
    Hive.box('settings').delete('searchHistory');
  }

  static const _secStorage = FlutterSecureStorage(
    aOptions: AndroidOptions(),
  );

  Future<void> _showRoadEventDetail(RoadEvent event) async {
    final c       = RoadstrColors.of(context);
    final privKey = await _secStorage.read(key: 'nostr_priv_hex');
    final pubKey  = await _secStorage.read(key: 'nostr_pub_hex');
    final flavor  = await _secStorage.read(key: 'nostr_flavor');
    if (!mounted) return;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => _RoadEventDetail(
        event: event,
        colors: c,
        isLoggedIn: pubKey != null,  // logged in = has pubkey, even Amber-only
        onConfirm: pubKey != null
            ? (stillThere) async {
                // Optimistically update local counters for immediate feedback.
                if (stillThere) event.confirmations++;
                else event.denials++;
                if (mounted) setState(() {});
                try {
                  if (flavor == 'amber') {
                    // Sign via Amber (NIP-55)
                    final unsigned = NostrRelayService.buildKind1316Map(
                      eventId:    event.id,
                      stillThere: stillThere,
                      pubKeyHex:  pubKey,
                    );
                    final result = await Amberflutter().signEvent(
                      currentUser: Nip19().npubEncode(pubKey),
                      eventJson:   jsonEncode(unsigned),
                    );
                    final signed = jsonDecode(result['event'] as String)
                        as Map<String, dynamic>;
                    _nostr.publishRawEvent(signed);
                  } else {
                    await _nostr.confirmRoadEvent(
                      eventId:    event.id,
                      stillThere: stillThere,
                      privKeyHex: privKey!,
                      pubKeyHex:  pubKey,
                    );
                  }
                  if (mounted) {
                    _snack(stillThere
                        ? '✓ ${AppLocalizations.of(context)!.confirmedLabel}'
                        : '✗ ${AppLocalizations.of(context)!.removedLabel}');
                  }
                } catch (e) {
                  // Roll back the optimistic counter update on failure.
                  if (stillThere) event.confirmations--;
                  else event.denials--;
                  if (mounted) { setState(() {}); _snack(e.toString()); }
                }
              }
            : null,
      ),
    );
  }

  Future<void> _showReportSheet({LatLng? position}) async {
    final privKey = await _secStorage.read(key: 'nostr_priv_hex');
    final pubKey  = await _secStorage.read(key: 'nostr_pub_hex');
    final flavor  = await _secStorage.read(key: 'nostr_flavor');
    if (!mounted) return;
    if (pubKey == null) {
      _snack(AppLocalizations.of(context)!.loginToReport);
      return;
    }
    final c   = RoadstrColors.of(context);
    final pos = position ?? (_gpsReady ? _position : _camCenter);
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => _ReportSheet(
        colors: c,
        position: pos,
        onSubmit: (category, comment) async {
          final now     = DateTime.now().millisecondsSinceEpoch ~/ 1000;
          final expires = now + 14 * 86400;
          RoadEvent event;
          if (flavor == 'amber') {
            // Sign via Amber (NIP-55) — private key never leaves the signer app.
            final unsigned = NostrRelayService.buildKind1315Map(
              position:  pos, category: category, comment: comment,
              pubKeyHex: pubKey, now: now, expires: expires,
            );
            final result = await Amberflutter().signEvent(
              currentUser: Nip19().npubEncode(pubKey),
              eventJson:   jsonEncode(unsigned),
            );
            final signed = jsonDecode(result['event'] as String)
                as Map<String, dynamic>;
            event = await _nostr.publishRawRoadEvent(
              eventJson: signed, category: category,
              position: pos, comment: comment, now: now, expires: expires,
            );
          } else {
            // Sign locally with the stored nsec private key.
            event = await _nostr.publishRoadEvent(
              position:   pos, category: category, comment: comment,
              privKeyHex: privKey!, pubKeyHex: pubKey,
            );
          }
          if (mounted) setState(() => _roadEvents = [..._roadEvents, event]);
        },
      ),
    );
  }

  void _showLocationContextMenu(LatLng point) {
    final c      = RoadstrColors.of(context);
    final navBar = MediaQuery.of(context).viewPadding.bottom;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        margin: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: c.surface2, borderRadius: BorderRadius.circular(20),
          border: Border.all(color: c.border, width: 0.5),
        ),
        padding: EdgeInsets.fromLTRB(20, 16, 20, 24 + navBar),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Center(child: Container(width: 40, height: 4,
              decoration: BoxDecoration(color: c.border,
                  borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 14),
          Text(
            '${point.latitude.toStringAsFixed(5)}, '
            '${point.longitude.toStringAsFixed(5)}',
            style: TextStyle(color: c.textSecondary, fontSize: 12,
                fontFamily: 'monospace'),
          ),
          const SizedBox(height: 16),
          // Navigate here
          SizedBox(width: double.infinity,
            child: FilledButton.icon(
              onPressed: () {
                Navigator.pop(context);
                _requestAlternatives(point);
              },
              style: FilledButton.styleFrom(
                backgroundColor: c.accent,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(vertical: 13),
              ),
              icon: const Icon(Icons.directions, color: Colors.white, size: 18),
              label: Text(AppLocalizations.of(context)!.navigateHere,
                  style: const TextStyle(color: Colors.white)),
            ),
          ),
          const SizedBox(height: 10),
          // Report event
          SizedBox(width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () {
                Navigator.pop(context);
                _showReportSheet(position: point);
              },
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: const Color(0xFFFFB800).withValues(alpha: 0.6)),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(vertical: 13),
              ),
              icon: const Icon(Icons.report_problem_outlined,
                  color: Color(0xFFFFB800), size: 18),
              label: Text(AppLocalizations.of(context)!.reportEventHere,
                  style: const TextStyle(color: Color(0xFFFFB800))),
            ),
          ),
        ]),
      ),
    );
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg, style: const TextStyle(color: Colors.white)),
      behavior: SnackBarBehavior.floating,
      backgroundColor: const Color(0xFF1A1A2E),
    ));
  }

  void _routingError(String msg) {
    if (!mounted) return;
    final c = RoadstrColors.of(context);
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Text(AppLocalizations.of(context)!.routeNotFoundTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 16,
                fontWeight: FontWeight.bold)),
        content: Text(msg,
            style: TextStyle(color: c.textSecondary, fontSize: 13)),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(AppLocalizations.of(context)!.ok, style: TextStyle(color: c.accent)),
          ),
        ],
      ),
    );
  }


  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final topInset = MediaQuery.of(context).viewPadding.top;
    final bottomInset = MediaQuery.of(context).viewPadding.bottom;
    // Show the NEXT upcoming maneuver (what to do next), not the current step
    // that was just executed.  The distance countdown (_distToNextManeuverM) is
    // already the distance to that next step, so instruction and distance match.
    final _nextStepIdx = (_route != null && _currentStepIdx + 1 < _route!.steps.length)
        ? _currentStepIdx + 1 : _currentStepIdx;
    final currentStep = (_route != null && _route!.steps.isNotEmpty)
        ? _route!.steps[_nextStepIdx] : null;

    return PopScope(
      canPop: !_showSearch && !_showPreview && !_showAlternatives && !_isNavigating,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_showSearch) {
          FocusScope.of(context).unfocus();
          _searchController.clear();
          setState(() { _showSearch = false; _searchResults = []; });
        } else if (_isNavigating) {
          _showExitNavigationDialog();
        } else if (_showPreview) {
          _cancelPreview();
        } else if (_showAlternatives) {
          _cancelAlternatives();
        }
      },
      child: Scaffold(
      resizeToAvoidBottomInset: false,
      body: Stack(children: [

        // ── MAP ─────────────────────────────────────────────────────────────
        Positioned.fill(
          child: FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _position,
              initialZoom: _initialZoom,
              // Clamp zoom to the tile layer's native range so the map never
              // shows blank grey tiles when the user over-zooms.
              minZoom: 2.0,
              maxZoom: 19.0,
              onMapEvent: (e) {
                if (e is MapEventMoveStart &&
                    e.source != MapEventSource.mapController) {
                  _camTicker?.cancel(); _camTicker = null;
                  // Any user pan or pinch-zoom disables follow mode so they
                  // can look ahead freely on the route. Follow resumes when
                  // the user taps the GPS FAB (_recenter) or the heading toggle.
                  if (_followUser) setState(() => _followUser = false);
                }
                if (e is MapEventMove) {
                  // Clamp zoom to valid flutter_map range to prevent LatLng
                  // exceptions when the user pinches rapidly beyond bounds.
                  final z = e.camera.zoom;
                  _camZoom   = z.isFinite ? z.clamp(1.0, 22.0) : _camZoom;
                  final ctr  = e.camera.center;
                  if (ctr.latitude.isFinite && ctr.longitude.isFinite) {
                    _camCenter = ctr;
                  }
                  // Only read rotation back from user gestures.
                  // Controller-driven moves (GPS tick, animations) already set
                  // _camRotDeg directly; letting MapEventMove overwrite it causes
                  // float drift that makes the heading-toggle delta appear < 0.5°
                  // and the animation guard prevents the button from responding.
                  if (e.source != MapEventSource.mapController) {
                    _camRotDeg = e.camera.rotation;
                  }
                }
              },
              onTap: (_, point) {
                if (_isNavigating || _isCalculating) return;

                if (_showAlternatives) {
                  final idx = _nearestAlternative(point);
                  if (idx >= 0) setState(() => _selectedAlt = idx);
                  return;
                }

                if (_showSearch) {
                  FocusScope.of(context).unfocus();
                  _searchController.clear();
                  setState(() { _showSearch = false; _searchResults = []; });
                  return;
                }

                if (_showPlaceInfo) { _closePlaceInfo(); return; }

                // First tap → show place info for the tapped point.
                _showPlaceInfoFor(point);
              },
              onLongPress: (_, point) {
                if (!_isNavigating && !_showSearch &&
                    !_showAlternatives && !_isCalculating) {
                  _showLocationContextMenu(point);
                }
              },
            ),
            children: [
              TileLayer(
                urlTemplate: Hive.box('settings').get('mapTileUrl', defaultValue: c.mapTile) as String,
                subdomains: c.mapTileSubs?.split('') ?? const [],
                userAgentPackageName: 'com.example.roadstr',
                maxZoom: 19,
                // In dark mode: boost contrast without hue shift so roads stand
                // out against the dark CartoDB background.
                // 2.0× multiplier, −20 offset: background (13)→6, roads (42)→64.
                // Contrast ratio major roads vs background: ~10×.
                tileBuilder: c.isDark
                    ? (_, tile, __) => ColorFiltered(
                          colorFilter: const ColorFilter.matrix([
                            2.0, 0,   0,   0, -20,
                            0,   2.0, 0,   0, -20,
                            0,   0,   2.0, 0, -20,
                            0,   0,   0,   1, 0,
                          ]),
                          child: tile,
                        )
                    : null,
              ),
              if (_showAlternatives)
                PolylineLayer(polylines: [
                  for (int i = 0; i < _alternatives.length; i++)
                    Polyline(
                      points: _alternatives[i].polyline,
                      strokeWidth: i == _selectedAlt ? 7 : 5,
                      color: i == _selectedAlt
                          ? c.accent
                          : Colors.grey.withValues(alpha: 0.55),
                      strokeCap: StrokeCap.round,
                    ),
                ]),
              if (_route != null && !_showAlternatives) ...[
                PolylineLayer(polylines: [
                  Polyline(points: _route!.polyline, strokeWidth: 8,
                      color: c.accent.withValues(alpha: 0.25)),
                  Polyline(points: _route!.polyline, strokeWidth: 5,
                      color: c.accent, strokeCap: StrokeCap.round),
                ]),
                // Heavy traffic overlay in red during navigation.
                if (_roadEvents.isNotEmpty)
                  PolylineLayer(polylines: [
                    for (final seg in _trafficSegments(_route!.polyline))
                      Polyline(points: seg, strokeWidth: 8,
                          color: const Color(0xFFEF4444).withValues(alpha: 0.85),
                          strokeCap: StrokeCap.round),
                  ]),
              ],
              if (_previewRoute != null && _showPreview) ...[
                PolylineLayer(polylines: [
                  Polyline(points: _previewRoute!.polyline, strokeWidth: 8,
                      color: c.accent.withValues(alpha: 0.20)),
                  Polyline(points: _previewRoute!.polyline, strokeWidth: 5,
                      color: c.accent, strokeCap: StrokeCap.round),
                ]),
                // Heavy traffic overlay in red during route preview.
                if (_roadEvents.isNotEmpty)
                  PolylineLayer(polylines: [
                    for (final seg in _trafficSegments(_previewRoute!.polyline))
                      Polyline(points: seg, strokeWidth: 8,
                          color: const Color(0xFFEF4444).withValues(alpha: 0.85),
                          strokeCap: StrokeCap.round),
                  ]),
              ],
              if (_destination != null)
                MarkerLayer(markers: [
                  Marker(point: _destination!, width: 40, height: 48,
                      child: _DestinationMarker(color: c.accent)),
                ]),
              // Road event markers: visible only at zoom ≥ 11
              // and filtered in real-time for expired TTL.
              if (_roadEvents.isNotEmpty && _camZoom >= 11)
                MarkerLayer(markers: [
                  for (final ev in _roadEvents.where((e) => !e.isExpired))
                    Marker(
                      point: ev.position,
                      width: 36, height: 36,
                      child: GestureDetector(
                        onTap: () => _showRoadEventDetail(ev),
                        child: _RoadEventPin(event: ev),
                      ),
                    ),
                ]),
              if (_showAlternatives)
                MarkerLayer(markers: [
                  for (int i = 0; i < _alternatives.length; i++)
                    if (_alternatives[i].polyline.length > 1)
                      Marker(
                        point: _alternatives[i]
                            .polyline[_alternatives[i].polyline.length ~/ 2],
                        width: 72, height: 30,
                        child: _TimeBubble(
                          label: _alternatives[i].durationLabel,
                          isSelected: i == _selectedAlt,
                          accent: c.accent,
                        ),
                      ),
                ]),
              if (_gpsReady)
                MarkerLayer(
                  // rotate: true keeps the marker upright in screen space so
                  // its heading angle is always relative to the screen, not the
                  // map. Without this, flutter_map rotates the marker widget
                  // together with the map, making the arrow appear perpendicular
                  // to the direction of travel when the map is tilted.
                  rotate: true,
                  markers: [
                    Marker(point: _position, width: 48, height: 48,
                        child: _UserMarker(
                            // In heading mode the map top = travel direction,
                            // so heading 0 (arrow up) is correct.
                            // Outside navigation the compass bearing is used.
                            heading: _headingMode ? 0 : _heading,
                            accent: c.accent)),
                  ]),
              // Purple dropped pin from search-bar Enter
              if (_pinResult != null)
                MarkerLayer(markers: [
                  Marker(
                    point: _pinResult!.position,
                    width: 36, height: 52,
                    alignment: Alignment.bottomCenter,
                    child: const _PinMarker(),
                  ),
                ]),
            ],
          ),
        ),

        // ── NAV INSTRUCTION / SEARCH BAR ───────────────────────────────────
        Positioned(
          top: topInset + 12, left: 16, right: 16,
          child: _isNavigating && currentStep != null
              ? _NavInstruction(step: currentStep, route: _route!,
                  stepIdx: _currentStepIdx, colors: c,
                  distToNextM: _distToNextManeuverM)
              : _SearchBarWidget(
                  controller: _searchController, colors: c,
                  onFocus: () { _loadFavorites(); setState(() => _showSearch = true); },
                  onChanged: _onSearchChanged,
                  onSubmitted: _onSearchSubmit,
                  onClear: () {
                    _searchController.clear();
                    setState(() { _searchResults = []; _showSearch = false; _pinResult = null; });
                    FocusScope.of(context).unfocus();
                  },
                ),
        ),

        // ── SEARCH RESULTS / HISTORY ─────────────────────────────────────────
        if (_showSearch)
          // Empty query → show history only (favourites appear ONLY on typed queries)
          if (_searchController.text.isEmpty && _history.isNotEmpty)
            Positioned(
              top: topInset + 76, left: 16, right: 16,
              child: _HistoryResults(
                history: _history, favorites: const [], colors: c,
                onSelect: (item) =>
                    _requestAlternatives(item.position, label: item.label),
                onSelectFavorite: (_) {},
                onClear: _clearHistory,
              ),
            )
          else if (_searchResults.isNotEmpty || _isSearching ||
                   _matchingFavorites(_searchController.text).isNotEmpty)
            Positioned(
              top: topInset + 76, left: 16, right: 16,
              child: _SearchResults(
                results: _searchResults, isLoading: _isSearching,
                favorites: _matchingFavorites(_searchController.text),
                colors: c,
                onSelect: _selectSearchResult,
                onSelectFavorite: (fav) {
                  _searchController.clear();
                  setState(() { _showSearch = false; _searchResults = []; });
                  FocusScope.of(context).unfocus();
                  _requestAlternatives(fav.position, label: fav.label);
                },
              ),
            ),

        // ── PIN PANEL (dropped via Enter on search bar) ──────────────────────
        if (_pinResult != null && !_isNavigating && !_showAlternatives &&
            !_showPreview)
          Positioned(
            bottom: bottomInset + 12, left: 16, right: 16,
            child: _PinPanel(
              result: _pinResult!,
              colors: c,
              onNavigate: () {
                final r = _pinResult!;
                setState(() => _pinResult = null);
                _requestAlternatives(r.position, label: r.shortName);
              },
              onClose: () => setState(() => _pinResult = null),
            ),
          ),

        // ── LEFT FABs: report event + A→B planner ─────────────────────────────
        if (!_showSearch && !_showAlternatives && !_isNavigating &&
            !_showPreview && !_showPlanner)
          Positioned(
            left: 12,
            bottom: 88 + bottomInset + 12,
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              if (_gpsReady)
                _MapFab(onTap: _showReportSheet, colors: c,
                  child: Icon(Icons.report_problem_outlined,
                      color: c.textPrimary, size: 22)),
              if (_gpsReady) const SizedBox(height: 8),
              _MapFab(onTap: _openPlanner, colors: c,
                child: Icon(Icons.alt_route_rounded,
                    color: c.accent, size: 28)),
            ]),
          ),

        // ── ROUTE PLANNER (A→B) ──────────────────────────────────────────────
        if (_showPlanner)
          Positioned(
            top: topInset + 12, left: 16, right: 16,
            child: _RoutePlannerBar(
              fromCtrl:     _planFromCtrl,
              toCtrl:       _planToCtrl,
              activeField:  _planActiveField,
              hasGps:       _gpsReady,
              canCalculate:  _planTo != null,
              transportMode: _transportMode,
              onModeChanged: (m) => setState(() => _transportMode = m),
              isSearching:  _planSearching,
              colors:       c,
              onFromTap:    () => setState(() => _planActiveField = 0),
              onToTap:      () => setState(() => _planActiveField = 1),
              onFromChanged: (q) => _onPlanSearch(q, 0),
              onToChanged:   (q) => _onPlanSearch(q, 1),
              onMyLocation: () {
                setState(() {
                  _planFrom = null;
                  _planFromCtrl.text = AppLocalizations.of(context)!.myLocation;
                  _planResults = [];
                  _planActiveField = 1;
                });
              },
              onCalculate: _calculatePlan,
              onClose:     _closePlanner,
            ),
          ),

        if (_showPlanner && (_planResults.isNotEmpty || _planSearching))
          Positioned(
            top: topInset + 140, left: 16, right: 16,
            child: _SearchResults(
              results:   _planResults,
              isLoading: _planSearching,
              colors:    c,
              onSelect:  _selectPlanResult,
              onSelectFavorite: (_) {},
            ),
          ),

        // ── RIGHT FABs ────────────────────────────────────────────────────────
        if (!_showSearch && !_showAlternatives && !_showPreview)
          Positioned(
            right: 12,
            bottom: (_isNavigating ? 160 : 88) + bottomInset + 12,
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              _CompassFab(
                rotDeg: _camRotDeg,
                // Active (purple) when map is at north-up (within 5°).
                // During navigation, active when heading mode is on.
                active: _isNavigating
                    ? _headingMode
                    : _camRotDeg.abs() < 5.0 || (_camRotDeg % 360).abs() < 5.0,
                onTap: _toggleHeadingMode,
              ),
              const SizedBox(height: 8),
              _MapFab(onTap: _requestGps, colors: c,
                child: _gpsRequested && !_gpsReady
                    ? SizedBox(width: 18, height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: c.accent))
                    : Icon(
                        _followUser ? Icons.gps_fixed : Icons.gps_not_fixed,
                        color: _gpsReady
                            ? (_followUser ? c.accent : c.textPrimary)
                            : c.textSecondary, size: 22),
              ),
              if (_isNavigating) ...[
                const SizedBox(height: 8),
                _MapFab(onTap: () {
                  setState(() => _voiceMuted = !_voiceMuted);
                  Hive.box('settings').put('voiceEnabled', !_voiceMuted);
                  if (_voiceMuted) _tts.stop();
                }, colors: c,
                  child: Icon(
                    _voiceMuted ? Icons.volume_off_rounded : Icons.volume_up_rounded,
                    color: _voiceMuted ? c.textSecondary : c.accent,
                    size: 22)),
                const SizedBox(height: 8),
                _MapFab(onTap: _showReportSheet, colors: c,
                  child: Icon(Icons.report_problem_outlined,
                      color: const Color(0xFFFFB800), size: 22)),
              ],
            ]),
          ),

        // ── SPEED LIMIT SIGN (navigation only) ───────────────────────────────
        if (_isNavigating && _currentSpeedLimit != null)
          Positioned(
            bottom: 120 + bottomInset,
            left: 16,
            child: _SpeedLimitSign(_currentSpeedLimit!),
          ),

        // ── NAV PANEL / BOTTOM BAR ───────────────────────────────────────────
        Positioned(
          bottom: 0, left: 0, right: 0,
          child: _isNavigating && _route != null
              ? _NavPanel(route: _route!, speed: _speed,
                  bottomInset: bottomInset, colors: c,
                  onStop: _showExitNavigationDialog,
                  remainingDistM: _remainingDistM,
                  remainingSecs: _remainingSecs)
              : _showPlaceInfo && _placePoint != null
                  ? _PlaceInfoPanel(
                      point:      _placePoint!,
                      address:    _placeAddress,
                      wiki:       _placeWiki,
                      wikiQuery:  _placeWikiQuery,
                      loading:    _placeLoading,
                      bottomInset: bottomInset,
                      colors:     c,
                      onNavigate: () {
                        // Capture values BEFORE _closePlaceInfo() nulls them out.
                        final dest  = _placePoint!;
                        final label = _placeAddress?.split(',').first
                                   ?? _pendingLabel;
                        _closePlaceInfo();
                        _requestAlternatives(dest, label: label);
                      },
                      onClose: _closePlaceInfo,
                    )
              : _showPreview && _previewRoute != null
                  ? _PreviewPanel(
                      route: _previewRoute!,
                      label: _pendingLabel,
                      trafficEvents: _routeTrafficEvents(_previewRoute!),
                      trafficStatus: _trafficStatus(_previewRoute!),
                      bottomInset: bottomInset,
                      colors: c,
                      transportMode: _transportMode,
                      onStart: _confirmPreview,
                      onCancel: _cancelPreview,
                      onModeChanged: _recalculateForMode,
                    )
              : _showAlternatives
                  ? _AlternativesPanel(
                      alternatives:  _alternatives,
                      selected:      _selectedAlt,
                      bottomInset:   bottomInset,
                      colors:        c,
                      transportMode: _transportMode,
                      onSelect:      (i) => setState(() => _selectedAlt = i),
                      // Skip the preview panel — go straight to navigation.
                      // The alternatives panel already shows duration, distance
                      // and traffic info, so a second "preview" is redundant.
                      onConfirm:     _startNavigation,
                      onCancel:      _cancelAlternatives,
                      onModeChanged: _recalculateForMode,
                    )
                  : _pinResult != null
                      ? const SizedBox.shrink()
                      : _BottomBar(bottomInset: bottomInset, colors: c),
        ),

        // ── ROUTE CALCULATION OVERLAY ────────────────────────────────────────
        if (_isCalculating)
          Positioned.fill(
            child: Container(
              color: Colors.black.withValues(alpha: 0.3),
              child: Center(
                child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(color: c.surface2,
                      borderRadius: BorderRadius.circular(16)),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    CircularProgressIndicator(color: c.accent),
                    const SizedBox(height: 16),
                    Text(AppLocalizations.of(context)!.calculatingRoute,
                        style: TextStyle(color: c.textPrimary, fontSize: 15)),
                  ]),
                ),
              ),
            ),
          ),

        // ── GPS ACQUIRING BADGE ──────────────────────────────────────────────
        if (_gpsRequested && !_gpsReady)
          Positioned(
            top: topInset + 76, left: 0, right: 0,
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: c.surface2, borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: const Color(0xFFFFB800).withValues(alpha: 0.6)),
                ),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  const SizedBox(width: 12, height: 12,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Color(0xFFFFB800))),
                  const SizedBox(width: 8),
                  Text(AppLocalizations.of(context)!.acquiringGps,
                      style: const TextStyle(color: Color(0xFFFFB800), fontSize: 13)),
                ]),
              ),
            ),
          ),
      ]),
    ), // Scaffold
    ); // PopScope
  }

  @override
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
        // Screen off / app backgrounded. Stop sensors to save battery.
        // GPS keeps running if navigation is active (foreground service handles it).
        _magnetSub?.cancel(); _magnetSub = null;
        _accelSub?.cancel();  _accelSub  = null;
        if (!_isNavigating) {
          _gps.stop();
          WakelockPlus.disable();
        }
      case AppLifecycleState.resumed:
        // Re-start sensors and clean up stale events.
        if (_magnetSub == null) _startCompass();
        if (!_gpsReady) _gps.start().then((_) {
          _gpsSub ??= _gps.stream.listen(_onGps);
        });
        if (mounted) {
          final pruned = _roadEvents.where((e) => !e.isExpired).toList();
          if (pruned.length != _roadEvents.length) {
            setState(() => _roadEvents = pruned);
          }
        }
      case AppLifecycleState.detached:
        // App fully closed — stop GPS immediately so the foreground
        // service (and its CPU wakelock) are released before the process exits.
        _gps.stop();
        WakelockPlus.disable();
      default:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _camTicker?.cancel();
    _gpsSub?.cancel();
    _roadSub?.cancel();
    _northTimer?.cancel();
    _eventCleanupTimer?.cancel();
    _magnetSub?.cancel();
    _accelSub?.cancel();
    _tts.dispose();
    _nostr.dispose();
    _gps.dispose();
    _searchController.dispose();
    _searchDebounce?.cancel();
    _planFromCtrl.dispose();
    _planToCtrl.dispose();
    _planDebounce?.cancel();
    WakelockPlus.disable();
    super.dispose();
  }
}

// ── Zap sheet ─────────────────────────────────────────────────────────────────

/// Bottom sheet that drives the full NIP-57 + LNURL-pay zap flow.
///
/// Payment strategy (tried in order):
///   1. **NWC (NIP-47)** — if the user has configured a `nostr+walletconnect://`
///      URI in Settings, payment is attempted silently in-app via [ZapService.payViaNwc].
///   2. **Deep link** — fallback; opens the `lightning:<invoice>` URI which
///      hands off to any installed Lightning wallet (e.g. Breez, Wallet of Satoshi).
///
/// NWC is tried first because it allows Roadstr to confirm the payment succeeded
/// (preimage received) and fire the [onZapSent] callback without leaving the app.
/// With a deep link we cannot know whether the user actually paid.
class _ZapSheet extends StatefulWidget {
  final RoadEvent event;
  final RoadstrColors colors;
  final void Function(int sats) onZapSent;
  const _ZapSheet({required this.event, required this.colors,
      required this.onZapSent});
  @override
  State<_ZapSheet> createState() => _ZapSheetState();
}

class _ZapSheetState extends State<_ZapSheet> {
  static const _presets = [21, 100, 500, 1000, 5000, 21000];
  static const _st = FlutterSecureStorage(
      aOptions: AndroidOptions());

  int? _selected;
  final _customCtrl = TextEditingController();
  bool _sending     = false;
  String? _status;

  @override
  void dispose() { _customCtrl.dispose(); super.dispose(); }

  int get _amount => _selected ?? (int.tryParse(_customCtrl.text) ?? 0);

  /// Executes the full zap payment flow.
  ///
  /// Follows the NIP-57 + LNURL-pay protocol in five sequential steps.
  /// Status messages are updated after each step so the UI reflects progress.
  Future<void> _send() async {
    if (_amount <= 0) return;
    final l = AppLocalizations.of(context)!;
    setState(() { _sending = true; _status = l.fetchingLightningAddress; });

    try {
      // Step 1: Fetch the recipient's Lightning address from their Nostr kind-0 profile.
      final lud16 = await ZapService.fetchLightningAddress(widget.event.pubkey);
      if (lud16 == null) {
        setState(() { _status = l.noLightningAddress; _sending = false; });
        return;
      }
      setState(() => _status = l.requestingInvoice);

      // Step 2: Resolve the LNURL-pay endpoint to get the invoice callback URL
      // and learn whether the server supports NIP-57 zap receipts (allowsNostr).
      final payInfo = await ZapService.fetchLnurlPayInfo(lud16);
      if (payInfo == null) {
        setState(() { _status = l.lnurlUnavailable; _sending = false; });
        return;
      }

      // Step 3: Build and sign a NIP-57 kind-9734 zap request (only when logged
      // in with nsec — Amber signing of zap requests is not yet supported).
      // The signed event JSON will be URL-encoded into the LNURL callback as the
      // `nostr` parameter, enabling the server to publish a kind-9735 receipt.
      Map<String, dynamic>? zapRequest;
      final privHex = await _st.read(key: 'nostr_priv_hex');
      final pubHex  = await _st.read(key: 'nostr_pub_hex');
      if (privHex != null && pubHex != null) {
        zapRequest = ZapService.buildZapRequest(
          senderPrivHex:    privHex,
          senderPubHex:     pubHex,
          recipientPubHex:  widget.event.pubkey,
          eventId:          widget.event.id,
          amountMsat:       _amount * 1000,
        );
      }

      // Step 4: Request a BOLT-11 invoice from the LNURL callback, optionally
      // attaching the NIP-57 zap request to trigger a kind-9735 receipt.
      final invoice = await ZapService.getInvoice(
          payInfo: payInfo, amountMsat: _amount * 1000,
          zapRequest: zapRequest);
      if (invoice == null) {
        setState(() { _status = l.invoiceFailed; _sending = false; });
        return;
      }
      setState(() => _status = l.openingWallet);

      // Step 5: Pay the invoice. NWC (NIP-47) is tried first because it stays
      // in-app and gives us the preimage proof of payment. Deep link is the
      // fallback for users without NWC configured.
      final nwcUri = Hive.box('settings')
          .get('nwcUri', defaultValue: '') as String;

      bool paid = false;
      if (nwcUri.trim().isNotEmpty) {
        setState(() => _status = l.payingViaNwc);
        final preimage = await ZapService.payViaNwc(
            invoice: invoice, nwcUri: nwcUri.trim());
        paid = preimage != null;
      }
      if (!paid) {
        paid = await ZapService.payViaDeepLink(invoice);
      }

      if (paid && mounted) {
        widget.onZapSent(_amount);
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(l.zapSent(_amount),
              style: const TextStyle(color: Colors.white)),
          backgroundColor: const Color(0xFFF7931A),
          behavior: SnackBarBehavior.floating,
        ));
      } else if (mounted) {
        setState(() { _status = l.noLightningWallet; _sending = false; });
      }
    } catch (e) {
      if (mounted) setState(() { _status = 'Error: $e'; _sending = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    return AlertDialog(
      backgroundColor: c.surface2,
      title: Row(children: [
        const Text('⚡', style: TextStyle(fontSize: 22)),
        const SizedBox(width: 8),
        Text(AppLocalizations.of(context)!.sendZap, style: TextStyle(
            color: c.textPrimary, fontSize: 16,
            fontWeight: FontWeight.bold)),
      ]),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        Text('Choose amount in satoshi (sats):',
            style: TextStyle(color: c.textSecondary, fontSize: 13)),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8,
          children: _presets.map((sats) {
            final sel = _selected == sats;
            return GestureDetector(
              onTap: () => setState(() {
                _selected = sel ? null : sats;
                if (!sel) _customCtrl.clear();
              }),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: sel ? const Color(0xFFF7931A).withValues(alpha: 0.15) : c.surface2,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: sel ? const Color(0xFFF7931A) : c.border,
                    width: sel ? 2 : 0.5,
                  ),
                ),
                child: Text('$sats ⚡', style: TextStyle(
                    color: sel ? const Color(0xFFF7931A) : c.textPrimary,
                    fontSize: 13, fontWeight: FontWeight.w600)),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _customCtrl,
          keyboardType: TextInputType.number,
          onTap: () => setState(() => _selected = null),
          onChanged: (_) => setState(() => _selected = null),
          style: TextStyle(color: c.textPrimary, fontSize: 13),
          decoration: InputDecoration(
            hintText: AppLocalizations.of(context)!.customAmount,
            hintStyle: TextStyle(color: c.textSecondary),
            suffixText: 'sat',
            suffixStyle: TextStyle(color: c.textSecondary, fontSize: 12),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: const BorderSide(color: Color(0xFFF7931A)),
            ),
            isDense: true,
            contentPadding: const EdgeInsets.all(10),
          ),
        ),
        if (_status != null) ...[
          const SizedBox(height: 10),
          Text(_status!, style: TextStyle(
              color: c.textSecondary, fontSize: 12)),
        ],
      ]),
      actions: [
        TextButton(
          onPressed: _sending ? null : () => Navigator.pop(context),
          child: Text('Cancel', style: TextStyle(color: c.textSecondary)),
        ),
        FilledButton.icon(
          onPressed: (_sending || _amount <= 0) ? null : _send,
          style: FilledButton.styleFrom(
            backgroundColor: _amount > 0 ? const Color(0xFFF7931A) : c.border,
          ),
          icon: _sending
              ? const SizedBox(width: 14, height: 14,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: Colors.white))
              : const Text('⚡', style: TextStyle(fontSize: 14)),
          label: Text(_sending
              ? AppLocalizations.of(context)!.zapSending
              : 'Zap $_amount sat',
              style: const TextStyle(color: Colors.white, fontSize: 13)),
        ),
      ],
    );
  }
}

// ── Road event widgets ────────────────────────────────────────────────────────

class _RoadEventPin extends StatelessWidget {
  final RoadEvent event;
  const _RoadEventPin({required this.event});
  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: event.category.color,
      shape: BoxShape.circle,
      border: Border.all(color: Colors.white, width: 1.5),
      boxShadow: const [BoxShadow(color: Colors.black38, blurRadius: 4)],
    ),
    child: Center(
      child: Text(event.category.emoji,
          style: const TextStyle(fontSize: 16, height: 1)),
    ),
  );
}

class _RoadEventDetail extends StatefulWidget {
  final RoadEvent event;
  final RoadstrColors colors;
  final bool isLoggedIn;
  final void Function(bool)? onConfirm;
  const _RoadEventDetail({required this.event, required this.colors,
      required this.isLoggedIn, this.onConfirm});
  @override
  State<_RoadEventDetail> createState() => _RoadEventDetailState();
}

class _RoadEventDetailState extends State<_RoadEventDetail> {
  int _zapSat = 0;

  @override
  void initState() {
    super.initState();
    ZapService.fetchZapTotal(widget.event.id).then((msats) {
      if (mounted) setState(() => _zapSat = msats ~/ 1000);
    });
  }

  void _openZapSheet() {
    final c = widget.colors;
    showDialog(
      context: context,
      builder: (_) => _ZapSheet(
        event:   widget.event,
        colors:  c,
        onZapSent: (sats) {
          if (mounted) setState(() => _zapSat += sats);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c      = widget.colors;
    final event  = widget.event;
    final navBar = MediaQuery.of(context).viewPadding.bottom;
    return Container(
      margin: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: c.surface2, borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.border, width: 0.5),
      ),
      padding: EdgeInsets.fromLTRB(20, 16, 20, 24 + navBar),
      child: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: c.border,
                borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 16),
        Row(children: [
          Container(
            width: 48, height: 48,
            decoration: BoxDecoration(
              color: event.category.color.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Center(child: Text(event.category.emoji,
                style: const TextStyle(fontSize: 24))),
          ),
          const SizedBox(width: 14),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
            Text(event.category.localizedLabel(AppLocalizations.of(context)!),
                style: TextStyle(color: c.textPrimary, fontSize: 16,
                fontWeight: FontWeight.bold)),
            Text(event.ageLabel(AppLocalizations.of(context)!),
                style: TextStyle(color: c.textSecondary, fontSize: 12)),
          ])),
          // Zap total badge
          if (_zapSat > 0)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: const Color(0xFFF7931A).withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                    color: const Color(0xFFF7931A).withValues(alpha: 0.4)),
              ),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                const Text('⚡', style: TextStyle(fontSize: 12)),
                const SizedBox(width: 3),
                Text('$_zapSat sat', style: const TextStyle(
                    color: Color(0xFFF7931A), fontSize: 11,
                    fontWeight: FontWeight.bold)),
              ]),
            ),
        ]),
        if (event.comment.isNotEmpty) ...[
          const SizedBox(height: 12),
          Text(event.comment,
              style: TextStyle(color: c.textSecondary, fontSize: 13)),
        ],
        const SizedBox(height: 14),
        Row(children: [
          const Icon(Icons.check_circle_outline,
              color: Color(0xFF22C55E), size: 16),
          const SizedBox(width: 4),
          Text('${event.confirmations}',
              style: TextStyle(color: c.textSecondary, fontSize: 12)),
          const SizedBox(width: 16),
          const Icon(Icons.cancel_outlined,
              color: Color(0xFFEF4444), size: 16),
          const SizedBox(width: 4),
          Text('${event.denials}',
              style: TextStyle(color: c.textSecondary, fontSize: 12)),
        ]),
        if (widget.isLoggedIn && widget.onConfirm != null) ...[
          const SizedBox(height: 16),
          Row(children: [
            Expanded(child: OutlinedButton.icon(
              onPressed: () { Navigator.pop(context); widget.onConfirm!(true); },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Color(0xFF22C55E)),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
              icon: const Icon(Icons.check_rounded,
                  color: Color(0xFF22C55E), size: 16),
              label: Text(AppLocalizations.of(context)!.stillThere,
                  style: const TextStyle(color: Color(0xFF22C55E), fontSize: 13)),
            )),
            const SizedBox(width: 10),
            Expanded(child: OutlinedButton.icon(
              onPressed: () { Navigator.pop(context); widget.onConfirm!(false); },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Color(0xFFEF4444)),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
              icon: const Icon(Icons.close_rounded,
                  color: Color(0xFFEF4444), size: 16),
              label: Text(AppLocalizations.of(context)!.notThereAnymore,
                  style: const TextStyle(color: Color(0xFFEF4444), fontSize: 13)),
            )),
          ]),
        ] else if (!widget.isLoggedIn) ...[
          const SizedBox(height: 10),
          Text(AppLocalizations.of(context)!.loginToConfirm,
              style: TextStyle(color: c.textSecondary, fontSize: 11)),
        ],
        // ── Zap button ─────────────────────────────────────────────────────
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: _openZapSheet,
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: Color(0xFFF7931A)),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              padding: const EdgeInsets.symmetric(vertical: 10),
            ),
            icon: const Text('⚡', style: TextStyle(fontSize: 16)),
            label: Text(AppLocalizations.of(context)!.zapSendSats,
                style: const TextStyle(color: Color(0xFFF7931A), fontSize: 13)),
          ),
        ),
      ]),
    );
  }
}

class _ReportSheet extends StatefulWidget {
  final RoadstrColors colors;
  final LatLng position;
  final Future<void> Function(RoadCategory, String) onSubmit;
  const _ReportSheet({required this.colors, required this.position,
      required this.onSubmit});
  @override
  State<_ReportSheet> createState() => _ReportSheetState();
}

class _ReportSheetState extends State<_ReportSheet> {
  RoadCategory? _selected;
  final _ctrl = TextEditingController();
  bool _sending = false;

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final bottomPad = MediaQuery.of(context).viewInsets.bottom
        + MediaQuery.of(context).viewPadding.bottom + 24;
    return Container(
      margin: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: c.surface2, borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.border, width: 0.5),
      ),
      child: SingleChildScrollView(
      padding: EdgeInsets.fromLTRB(20, 16, 20, bottomPad),
      child: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: c.border,
                borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 14),
        Text(AppLocalizations.of(context)!.reportAnEvent, style: TextStyle(
            color: c.textPrimary, fontSize: 17,
            fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        Text(
          '📍 ${widget.position.latitude.toStringAsFixed(4)}, '
          '${widget.position.longitude.toStringAsFixed(4)}',
          style: TextStyle(color: c.textSecondary, fontSize: 11),
        ),
        const SizedBox(height: 12),
        // Use MaxCrossAxisExtent so each cell is ≥ 64dp on any screen width.
        // crossAxisCount: 5 was breaking on phones narrower than ~360dp.
        GridView(
          shrinkWrap: true,
          gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 80,
              mainAxisSpacing: 8, crossAxisSpacing: 8,
              childAspectRatio: 0.85),
          physics: const NeverScrollableScrollPhysics(),
          children: RoadCategory.values.map((cat) {
            final sel = _selected == cat;
            return GestureDetector(
              onTap: () => setState(() => _selected = cat),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                decoration: BoxDecoration(
                  color: sel ? cat.color.withValues(alpha: 0.18) : c.surface3,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                      color: sel ? cat.color : c.border,
                      width: sel ? 2 : 0.5),
                ),
                child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                  Text(cat.emoji,
                      style: const TextStyle(fontSize: 20, height: 1.2)),
                  const SizedBox(height: 2),
                  Text(cat.label,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                          fontSize: 8, color: c.textSecondary, height: 1.1)),
                ]),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _ctrl,
          maxLines: 2, maxLength: 200,
          style: TextStyle(color: c.textPrimary, fontSize: 13),
          decoration: InputDecoration(
            hintText: AppLocalizations.of(context)!.optionalComment,
            hintStyle: TextStyle(color: c.textSecondary),
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12)),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: c.accent),
            ),
            counterStyle: TextStyle(color: c.textSecondary, fontSize: 11),
            contentPadding: const EdgeInsets.all(12),
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: _selected == null || _sending
                ? null
                : () async {
                    setState(() => _sending = true);
                    try {
                      await widget.onSubmit(_selected!, _ctrl.text.trim());
                      if (mounted) {
                        Navigator.pop(context);
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(AppLocalizations.of(context)!.reportPublished,
                                style: const TextStyle(color: Colors.white)),
                            backgroundColor: const Color(0xFF1A1A2E),
                            behavior: SnackBarBehavior.floating,
                          ),
                        );
                      }
                    } catch (e) {
                      if (mounted) {
                        setState(() => _sending = false);
                        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                          content: Text(e.toString(),
                              style: const TextStyle(color: Colors.white)),
                          backgroundColor: const Color(0xFFDC2626),
                          behavior: SnackBarBehavior.floating,
                        ));
                      }
                    }
                  },
            style: FilledButton.styleFrom(
              backgroundColor: _selected?.color ?? c.border,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              padding: const EdgeInsets.symmetric(vertical: 13),
            ),
            icon: _sending
                ? const SizedBox(width: 16, height: 16,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: Colors.white))
                : const Icon(Icons.send_rounded,
                    color: Colors.white, size: 18),
            label: Text(_sending
                ? AppLocalizations.of(context)!.publishing
                : AppLocalizations.of(context)!.publish,
                style: const TextStyle(color: Colors.white)),
          ),
        ),
      ]),
      ), // SingleChildScrollView
    );
  }
}

// ── Place info panel ──────────────────────────────────────────────────────────

class _PlaceInfoPanel extends StatefulWidget {
  final LatLng point;
  final String? address;
  final WikiSummary? wiki;
  final String? wikiQuery;
  final bool loading;
  final double bottomInset;
  final RoadstrColors colors;
  final VoidCallback onNavigate, onClose;
  const _PlaceInfoPanel({
    required this.point, required this.address, required this.wiki,
    this.wikiQuery,
    required this.loading, required this.bottomInset, required this.colors,
    required this.onNavigate, required this.onClose,
  });

  @override
  State<_PlaceInfoPanel> createState() => _PlaceInfoPanelState();
}

class _PlaceInfoPanelState extends State<_PlaceInfoPanel>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<Offset> _slide;
  double _dragDy = 0; // real-time drag offset (positive = downward)

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 340));
    _slide = Tween<Offset>(
            begin: const Offset(0, 1), end: Offset.zero)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _ctrl.forward(); // slide in when panel first appears
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _closeAnimated() async {
    await _ctrl.animateTo(0,
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeInCubic);
    widget.onClose();
  }

  /// Animated spring-back: smoothly returns the panel to its resting position
  /// so users can abort a downward drag without committing to close.
  void _springBack() {
    if (_dragDy <= 0) { setState(() => _dragDy = 0); return; }
    final start = _dragDy;
    final startMs = DateTime.now().millisecondsSinceEpoch;
    Timer.periodic(const Duration(milliseconds: 8), (t) {
      if (!mounted) { t.cancel(); return; }
      final p = ((DateTime.now().millisecondsSinceEpoch - startMs) / 220.0)
          .clamp(0.0, 1.0);
      final e = 1 - math.pow(1 - p, 3);
      setState(() => _dragDy = start * (1 - e));
      if (p >= 1.0) { t.cancel(); setState(() => _dragDy = 0); }
    });
  }

  /// Opens the web page using a Hero transition so the panel visually expands
  /// to fill the browser screen — no manual timer animation needed.
  void _openMore(BuildContext ctx) {
    String? url;
    String? title;
    if (widget.wiki?.pageUrl != null) {
      url   = widget.wiki!.pageUrl!;
      title = widget.wiki!.title;
    } else if (widget.wikiQuery != null) {
      url   = _searchUrl(widget.wikiQuery!);
      title = widget.wikiQuery;
    }
    if (url == null) return;
    Navigator.push(ctx, PageRouteBuilder(
      fullscreenDialog: true,
      transitionDuration: const Duration(milliseconds: 400),
      reverseTransitionDuration: const Duration(milliseconds: 350),
      pageBuilder: (_, __, ___) =>
          _WebViewScreen(url: url!, title: title, heroTag: _heroTag),
      transitionsBuilder: (_, anim, __, child) =>
          FadeTransition(opacity: anim, child: child),
    ));
  }

  static const _heroTag = 'roadstr_place_info_panel';

  static String _engineName() {
    final e = Hive.box('settings')
        .get('searchEngine', defaultValue: 'qwant') as String;
    return switch (e) {
      'brave'     => 'Brave',
      'ddg'       => 'DuckDuckGo',
      'startpage' => 'Startpage',
      'google'    => 'Google',
      _           => 'Qwant',
    };
  }

  static String _searchUrl(String query) {
    final q      = Uri.encodeComponent(query);
    final engine = Hive.box('settings')
        .get('searchEngine', defaultValue: 'qwant') as String;
    return switch (engine) {
      'brave'     => 'https://search.brave.com/search?q=$q',
      'ddg'       => 'https://duckduckgo.com/?q=$q',
      'startpage' => 'https://www.startpage.com/search?query=$q',
      'google'    => 'https://www.google.com/search?q=$q',
      _           => 'https://www.qwant.com/?q=$q',
    };
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    // Drag limit: 55% of available height so the panel stays partly visible
    // on short screens (480dp) while allowing full dismiss on tall ones.
    final maxDrag = MediaQuery.of(context).size.height * 0.55;
    return SlideTransition(
      position: _slide,
      child: Transform.translate(
        offset: Offset(0, _dragDy.clamp(0.0, maxDrag)),
        child: GestureDetector(
          // Only track downward drag — the panel must not physically move
          // upward during gesture; upward swipe is detected by velocity only.
          onVerticalDragUpdate: (d) {
            if (d.delta.dy > 0) setState(() => _dragDy += d.delta.dy);
          },
          onVerticalDragEnd: (d) {
            final v = d.primaryVelocity ?? 0;
            if (v > 400 || _dragDy > 120) {
              _closeAnimated();
            } else if (v < -500 && _dragDy < 10) {
              // Fast upward flick → Hero-expand into browser
              _openMore(context);
            } else {
              _springBack();
            }
          },
      // Hero tag: the panel background morphs into the WebView screen via
      // Flutter's Hero transition — this is the "expand to browser" effect.
      child: Hero(
        tag: _heroTag,
        flightShuttleBuilder: (_, anim, __, ___, ____) => Material(
          color: c.surface2,
          borderRadius: BorderRadius.lerp(
              const BorderRadius.vertical(top: Radius.circular(20)),
              BorderRadius.zero, anim.value),
        ),
        child: Container(
        decoration: BoxDecoration(
          color: c.surface2,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          border: Border(top: BorderSide(color: c.border, width: 0.5)),
          boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.12),
              blurRadius: 16, offset: const Offset(0, -4))],
        ),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          // Drag handle.
          const SizedBox(height: 10),
          Center(child: Container(width: 40, height: 4,
              decoration: BoxDecoration(color: c.border,
                  borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 12),

          // Title / address.
          Padding(padding: const EdgeInsets.symmetric(horizontal: 20),
            child: widget.loading && widget.address == null
              ? Row(children: [
                  SizedBox(width: 14, height: 14,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: c.accent)),
                  const SizedBox(width: 10),
                  Text(AppLocalizations.of(context)!.loadingInfo,
                      style: TextStyle(color: c.textSecondary, fontSize: 13)),
                ])
              : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  if (widget.wiki != null) ...[
                    Text(widget.wiki!.title, style: TextStyle(
                        color: c.textPrimary, fontSize: 17,
                        fontWeight: FontWeight.bold)),
                    const SizedBox(height: 2),
                    if (widget.address != null)
                      Text(widget.address!, maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: c.textSecondary, fontSize: 12)),
                  ] else
                    Text(widget.address ??
                        '${widget.point.latitude.toStringAsFixed(5)}, '
                        '${widget.point.longitude.toStringAsFixed(5)}',
                        style: TextStyle(color: c.textPrimary, fontSize: 15,
                            fontWeight: FontWeight.w600)),
                ]),
          ),

          // Wikipedia image + extract.
          if (widget.wiki != null) ...[
            const SizedBox(height: 10),
            if (widget.wiki!.imageUrl != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Image.network(widget.wiki!.imageUrl!,
                      height: 110, width: double.infinity,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const SizedBox.shrink()),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
              child: Text(widget.wiki!.extract,
                  maxLines: 3, overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: c.textSecondary, fontSize: 13,
                      height: 1.4)),
            ),
          ],

          // "Learn more" / search engine button (shown only when there is something to open).
          if (!widget.loading && (widget.wiki?.pageUrl != null || widget.wikiQuery != null)) ...[
            const SizedBox(height: 8),
            Padding(padding: const EdgeInsets.symmetric(horizontal: 20),
              child: GestureDetector(
                onTap: () => _openMore(context),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(widget.wiki?.pageUrl != null
                      ? Icons.open_in_browser_rounded
                      : Icons.search_rounded,
                      color: c.accent, size: 14),
                  const SizedBox(width: 4),
                  Text(
                    widget.wiki?.pageUrl != null
                        ? AppLocalizations.of(context)!.readOnWikipedia
                        : AppLocalizations.of(context)!.searchOnEngine(_engineName()),
                    style: TextStyle(color: c.accent, fontSize: 12,
                        decoration: TextDecoration.underline),
                  ),
                ]),
              ),
            ),
          ],

          const SizedBox(height: 12),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(children: [
              OutlinedButton(
                onPressed: _closeAnimated,
                style: OutlinedButton.styleFrom(
                  side: BorderSide(color: c.border),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                ),
                child: Text(AppLocalizations.of(context)!.cancel,
                    style: TextStyle(color: c.textSecondary)),
              ),
              const SizedBox(width: 12),
              Expanded(child: FilledButton.icon(
                onPressed: widget.onNavigate,
                style: FilledButton.styleFrom(
                  backgroundColor: c.accent,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                ),
                icon: const Icon(Icons.navigation_rounded,
                    color: Colors.white, size: 18),
                label: Text(AppLocalizations.of(context)!.startNavigation,
                    style: const TextStyle(color: Colors.white)),
              )),
            ]),
          ),
          SizedBox(height: widget.bottomInset > 0 ? widget.bottomInset : 14),
        ]),
        ), // Container
      ), // Hero
        ), // GestureDetector
      ), // Transform.translate
    ); // SlideTransition
  }
}

// ── WebView screen ────────────────────────────────────────────────────────────

class _WebViewScreen extends StatefulWidget {
  final String url;
  final String? title;
  final String? heroTag;
  const _WebViewScreen({required this.url, this.title, this.heroTag});
  @override
  State<_WebViewScreen> createState() => _WebViewScreenState();
}

class _WebViewScreenState extends State<_WebViewScreen> {
  late final WebViewController _ctrl;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _ctrl = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(NavigationDelegate(
        onPageStarted:  (_) { if (mounted) setState(() => _loading = true);  },
        onPageFinished: (_) { if (mounted) setState(() => _loading = false); },
      ))
      ..loadRequest(Uri.parse(widget.url));
  }

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    return Scaffold(
      appBar: AppBar(
        backgroundColor: c.surface2,
        leading: IconButton(
          icon: Icon(Icons.close_rounded, color: c.textPrimary),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(widget.title ?? '',
            maxLines: 1, overflow: TextOverflow.ellipsis,
            style: TextStyle(color: c.textPrimary, fontSize: 15)),
        actions: [
          // Open in external browser (optional).
          IconButton(
            icon: Icon(Icons.open_in_browser_rounded, color: c.textSecondary),
            onPressed: () => launchUrl(Uri.parse(widget.url),
                mode: LaunchMode.externalApplication),
          ),
        ],
        bottom: _loading
            ? PreferredSize(
                preferredSize: const Size.fromHeight(3),
                child: LinearProgressIndicator(
                    color: c.accent, backgroundColor: c.border))
            : PreferredSize(
                preferredSize: const Size.fromHeight(0.5),
                child: Divider(height: 0.5, color: c.border)),
      ),
      body: widget.heroTag != null
          ? Hero(tag: widget.heroTag!, child: WebViewWidget(controller: _ctrl))
          : WebViewWidget(controller: _ctrl),
    );
  }
}

// ── Waypoint ──────────────────────────────────────────────────────────────────

class _WayPoint {
  final String label;
  final LatLng position;
  const _WayPoint(this.label, this.position);
}

// ── Route planner bar ─────────────────────────────────────────────────────────

class _RoutePlannerBar extends StatelessWidget {
  final TextEditingController fromCtrl, toCtrl;
  final int activeField;
  final bool hasGps, canCalculate, isSearching;
  final String transportMode;
  final RoadstrColors colors;
  final VoidCallback onFromTap, onToTap, onMyLocation, onClose;
  final VoidCallback onCalculate;
  final ValueChanged<String> onFromChanged, onToChanged;
  final ValueChanged<String> onModeChanged;

  const _RoutePlannerBar({
    required this.fromCtrl, required this.toCtrl,
    required this.activeField, required this.hasGps,
    required this.canCalculate, required this.isSearching,
    required this.transportMode,
    required this.colors,
    required this.onFromTap, required this.onToTap,
    required this.onMyLocation, required this.onClose,
    required this.onCalculate,
    required this.onFromChanged, required this.onToChanged,
    required this.onModeChanged,
  });

  @override
  Widget build(BuildContext context) {
    final c = colors;
    return Container(
      decoration: BoxDecoration(
        color: c.surface2, borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.16),
            blurRadius: 12, offset: const Offset(0, 3))],
      ),
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        // ── Da ────────────────────────────────────────────────────────────
        Row(children: [
          Container(width: 10, height: 10,
              decoration: BoxDecoration(
                  color: c.accent, shape: BoxShape.circle)),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              controller: fromCtrl,
              onTap: () {
                onFromTap();
                // If showing "My location", select-all so a single backspace clears it.
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (fromCtrl.text.isNotEmpty) {
                    fromCtrl.selection = TextSelection(
                        baseOffset: 0,
                        extentOffset: fromCtrl.text.length);
                  }
                });
              },
              onChanged: onFromChanged,
              autofocus: false,
              style: TextStyle(color: c.textPrimary, fontSize: 14),
              decoration: InputDecoration(
                hintText: AppLocalizations.of(context)!.plannerFromHint,
                hintStyle: TextStyle(color: c.textSecondary, fontSize: 14),
                border: InputBorder.none, isDense: true,
              ),
            ),
          ),
          if (hasGps)
            GestureDetector(
              onTap: onMyLocation,
              child: Icon(Icons.my_location_rounded,
                  color: c.accent, size: 18),
            ),
        ]),
        Divider(height: 8, color: c.border),
        // ── A ─────────────────────────────────────────────────────────────
        Row(children: [
          Container(width: 10, height: 10,
              decoration: BoxDecoration(
                  border: Border.all(color: const Color(0xFFEA4335), width: 2),
                  shape: BoxShape.circle)),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              controller: toCtrl,
              onTap: onToTap,
              onChanged: onToChanged,
              autofocus: true,
              style: TextStyle(color: c.textPrimary, fontSize: 14),
              decoration: InputDecoration(
                hintText: AppLocalizations.of(context)!.plannerToHint,
                hintStyle: TextStyle(color: c.textSecondary, fontSize: 14),
                border: InputBorder.none, isDense: true,
              ),
            ),
          ),
          if (isSearching)
            SizedBox(width: 16, height: 16,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: c.accent)),
        ]),
        const SizedBox(height: 8),
        // ── Transport mode toggle ─────────────────────────────────────────
        Row(children: [
          _ModeChip(
            icon: Icons.directions_car_rounded,
            label: AppLocalizations.of(context)!.transportModeCar,
            selected: transportMode == 'driving',
            colors: c,
            onTap: () => onModeChanged('driving'),
          ),
          const SizedBox(width: 8),
          _ModeChip(
            icon: Icons.directions_walk_rounded,
            label: AppLocalizations.of(context)!.transportModeWalk,
            selected: transportMode == 'walking',
            colors: c,
            onTap: () => onModeChanged('walking'),
          ),
        ]),
        const SizedBox(height: 10),
        Row(children: [
          TextButton(
            onPressed: onClose,
            style: TextButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 12)),
            child: Text(AppLocalizations.of(context)!.cancel,
                style: TextStyle(color: c.textSecondary, fontSize: 13)),
          ),
          const Spacer(),
          FilledButton.icon(
            onPressed: canCalculate ? onCalculate : null,
            style: FilledButton.styleFrom(
              backgroundColor: canCalculate ? c.accent : c.border,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              padding: const EdgeInsets.symmetric(
                  horizontal: 16, vertical: 10),
            ),
            icon: Icon(
              transportMode == 'walking'
                  ? Icons.directions_walk_rounded
                  : Icons.navigation_rounded,
              color: Colors.white, size: 16),
            label: Text(AppLocalizations.of(context)!.calculateRoute,
                style: const TextStyle(color: Colors.white, fontSize: 13)),
          ),
        ]),
      ]),
    );
  }
}

/// Small selectable pill used in the route-planner transport-mode toggle.
class _ModeChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final RoadstrColors colors;
  final VoidCallback onTap;
  const _ModeChip({required this.icon, required this.label,
      required this.selected, required this.colors, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final c = colors;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color:  selected ? c.accent : c.surface3,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: selected ? c.accent : c.border, width: selected ? 2 : 0.5),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 14, color: selected ? Colors.white : c.textSecondary),
          const SizedBox(width: 5),
          Text(label, style: TextStyle(
              fontSize: 12,
              color: selected ? Colors.white : c.textSecondary,
              fontWeight: selected ? FontWeight.w600 : FontWeight.normal)),
        ]),
      ),
    );
  }
}

extension _StringX on String {
  String? get nullIfEmpty => isEmpty ? null : this;
}

// ── History ───────────────────────────────────────────────────────────────────

class _HistoryItem {
  final String label;
  final LatLng position;
  const _HistoryItem(this.label, this.position);

  Map<String, dynamic> toJson() => {
    'label': label,
    'lat': position.latitude,
    'lon': position.longitude,
  };

  static _HistoryItem? fromJsonSafe(Map<String, dynamic> j) {
    final lat = (j['lat'] as num?)?.toDouble() ?? double.nan;
    final lon = (j['lon'] as num?)?.toDouble() ?? double.nan;
    if (!lat.isFinite || !lon.isFinite ||
        lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
    return _HistoryItem(j['label'] as String, LatLng(lat, lon));
  }
}

class _HistoryResults extends StatelessWidget {
  final List<_HistoryItem> history;
  final List<FavoritePlace> favorites;
  final RoadstrColors colors;
  final ValueChanged<_HistoryItem> onSelect;
  final ValueChanged<FavoritePlace> onSelectFavorite;
  final VoidCallback onClear;
  const _HistoryResults({required this.history, required this.colors,
      required this.onSelect, required this.onSelectFavorite,
      required this.onClear, this.favorites = const []});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.15),
            blurRadius: 12, offset: const Offset(0, 4))],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [

        // ── Saved places section ─────────────────────────────────────────
        if (favorites.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Row(children: [
              Icon(Icons.favorite_rounded, color: colors.accent, size: 14),
              const SizedBox(width: 6),
              Text(l.sectionFavorites, style: TextStyle(
                  color: colors.textSecondary, fontSize: 12,
                  fontWeight: FontWeight.w600)),
            ]),
          ),
          Material(
            color: Colors.transparent,
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: EdgeInsets.zero,
              itemCount: favorites.length,
              separatorBuilder: (_, __) => Divider(height: 0.5, color: colors.border),
              itemBuilder: (_, i) {
                final fav = favorites[i];
                return ListTile(
                  tileColor: Colors.transparent,
                  dense: true,
                  leading: Container(
                    width: 30, height: 30,
                    decoration: BoxDecoration(
                        color: colors.accentSoft,
                        borderRadius: BorderRadius.circular(8)),
                    child: Icon(Icons.favorite_rounded, color: colors.accent, size: 14),
                  ),
                  title: Text(fav.label, maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: colors.textPrimary,
                          fontSize: 14, fontWeight: FontWeight.w500)),
                  subtitle: Text(fav.address, maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: colors.textSecondary, fontSize: 12)),
                  onTap: () => onSelectFavorite(fav),
                );
              },
            ),
          ),
        ],

        // ── History section ──────────────────────────────────────────────
        if (history.isNotEmpty) ...[
          if (favorites.isNotEmpty)
            Divider(height: 0.5, color: colors.border),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
            child: Row(children: [
              Icon(Icons.history_rounded, color: colors.textSecondary, size: 16),
              const SizedBox(width: 6),
              Text(l.history, style: TextStyle(
                  color: colors.textSecondary, fontSize: 12,
                  fontWeight: FontWeight.w600)),
              const Spacer(),
              TextButton(
                onPressed: onClear,
                style: TextButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 8)),
                child: Text(l.clearHistory, style: TextStyle(
                    color: colors.accent, fontSize: 12)),
              ),
            ]),
          ),
          Material(
            color: Colors.transparent,
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: EdgeInsets.zero,
              itemCount: history.length,
              separatorBuilder: (_, __) => Divider(height: 0.5, color: colors.border),
              itemBuilder: (_, i) {
                final h = history[i];
                return ListTile(
                  tileColor: Colors.transparent,
                  dense: true,
                  leading: Icon(Icons.location_on_outlined,
                      color: colors.accent, size: 20),
                  title: Text(h.label, maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: colors.textPrimary, fontSize: 14)),
                  onTap: () => onSelect(h),
                );
              },
            ),
          ),
        ],

        const SizedBox(height: 6),
      ]),
    );
  }
}

// ── Preview panel ─────────────────────────────────────────────────────────────

class _PreviewPanel extends StatelessWidget {
  final RouteResult route;
  final String? label;
  final List<RoadEvent> trafficEvents;
  final ({String label, Color color})? trafficStatus;
  final double bottomInset;
  final RoadstrColors colors;
  final String transportMode;
  final VoidCallback onStart;
  final VoidCallback onCancel;
  final ValueChanged<String> onModeChanged;
  const _PreviewPanel({
    required this.route, required this.label,
    required this.trafficEvents, this.trafficStatus,
    required this.bottomInset, required this.colors,
    required this.transportMode,
    required this.onStart, required this.onCancel,
    required this.onModeChanged,
  });

  @override
  Widget build(BuildContext context) {
    final c = colors;
    final now     = DateTime.now();
    final arrival = now.add(Duration(seconds: route.totalDurationS.round()));
    final depStr  = _fmtTime(now);
    final arrStr  = _fmtTime(arrival);

    return Container(
      decoration: BoxDecoration(
        color: c.surface2,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        border: Border(top: BorderSide(color: c.border, width: 0.5)),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 16, offset: const Offset(0, -4))],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(height: 10),
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: c.border,
                borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 14),

        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child:
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            // Destination label.
            if (label != null && label!.isNotEmpty) ...[
              Text(label!, style: TextStyle(color: c.textPrimary,
                  fontSize: 17, fontWeight: FontWeight.bold),
                  maxLines: 1, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 10),
            ],

            // Duration + distance.
            Row(children: [
              Icon(Icons.access_time_rounded, color: c.accent, size: 20),
              const SizedBox(width: 6),
              Text(route.durationLabel, style: TextStyle(
                  color: c.textPrimary, fontSize: 22,
                  fontWeight: FontWeight.bold)),
              const SizedBox(width: 16),
              Text(route.distanceLabel, style: TextStyle(
                  color: c.textSecondary, fontSize: 15)),
            ]),
            const SizedBox(height: 8),

            // Departure / arrival times.
            Row(children: [
              Icon(Icons.schedule_rounded,
                  color: c.textSecondary, size: 16),
              const SizedBox(width: 4),
              Text(AppLocalizations.of(context)!.departEta(depStr, arrStr),
                  style: TextStyle(color: c.textSecondary, fontSize: 13)),
            ]),

            // Traffic status badge (only for driving mode)
            if (transportMode != 'walking' && trafficStatus != null) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: trafficStatus!.color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                      color: trafficStatus!.color.withValues(alpha: 0.4)),
                ),
                child: Text(trafficStatus!.label,
                    style: TextStyle(
                        color: trafficStatus!.color, fontSize: 12,
                        fontWeight: FontWeight.w600)),
              ),
            ],

            // Traffic events along the route.
            if (trafficEvents.isNotEmpty) ...[
              const SizedBox(height: 12),
              Divider(height: 0.5, color: c.border),
              const SizedBox(height: 10),
              Text(AppLocalizations.of(context)!.conditionsOnRoute,
                  style: TextStyle(color: c.textSecondary, fontSize: 11,
                      fontWeight: FontWeight.bold, letterSpacing: 0.5)),
              const SizedBox(height: 6),
              ...trafficEvents.take(3).map((ev) => Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(children: [
                  Text(ev.category.emoji,
                      style: const TextStyle(fontSize: 14)),
                  const SizedBox(width: 6),
                  Text(ev.category.localizedLabel(AppLocalizations.of(context)!),
                      style: TextStyle(color: c.textPrimary,
                          fontSize: 13, fontWeight: FontWeight.w500)),
                  const SizedBox(width: 4),
                  if (ev.comment.isNotEmpty)
                    Expanded(child: Text('· ${ev.comment}',
                        maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                            color: c.textSecondary, fontSize: 12))),
                ]),
              )),
            ],
          ]),
        ),

        const SizedBox(height: 12),
        // Transport mode toggle — let the user switch mode before starting.
        Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(children: [
            _ModeChip(icon: Icons.directions_car_rounded, label: 'Auto',
                selected: transportMode == 'driving', colors: c,
                onTap: () => onModeChanged('driving')),
            const SizedBox(width: 8),
            _ModeChip(icon: Icons.directions_walk_rounded, label: 'A piedi',
                selected: transportMode == 'walking', colors: c,
                onTap: () => onModeChanged('walking')),
          ]),
        ),
        const SizedBox(height: 10),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(children: [
            Expanded(
              child: OutlinedButton(
                onPressed: onCancel,
                style: OutlinedButton.styleFrom(
                  side: BorderSide(color: c.border),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                child: Text(AppLocalizations.of(context)!.cancel,
                    style: TextStyle(color: c.textSecondary)),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(flex: 2,
              child: FilledButton.icon(
                onPressed: onStart,
                style: FilledButton.styleFrom(
                  backgroundColor: c.accent,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon: Icon(
                  transportMode == 'walking'
                      ? Icons.directions_walk_rounded
                      : Icons.navigation_rounded,
                  color: Colors.white, size: 18),
                label: Text(AppLocalizations.of(context)!.startNavigation,
                    style: const TextStyle(color: Colors.white)),
              ),
            ),
          ]),
        ),
        SizedBox(height: bottomInset > 0 ? bottomInset : 16),
      ]),
    );
  }

  static String _fmtTime(DateTime dt) =>
      '${dt.hour.toString().padLeft(2, '0')}:'
      '${dt.minute.toString().padLeft(2, '0')}';
}

// ── Alternatives panel ────────────────────────────────────────────────────────

class _AlternativesPanel extends StatelessWidget {
  final List<RouteResult> alternatives;
  final int selected;
  final double bottomInset;
  final RoadstrColors colors;
  final String transportMode;
  final ValueChanged<int> onSelect;
  final ValueChanged<RouteResult> onConfirm;
  final VoidCallback onCancel;
  final ValueChanged<String> onModeChanged;
  const _AlternativesPanel({
    required this.alternatives, required this.selected,
    required this.bottomInset, required this.colors,
    required this.transportMode,
    required this.onSelect, required this.onConfirm, required this.onCancel,
    required this.onModeChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        border: Border(top: BorderSide(color: colors.border, width: 0.5)),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 16, offset: const Offset(0, -4))],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(height: 10),
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: colors.border,
                borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 14),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(children: [
            Text(AppLocalizations.of(context)!.chooseRoute, style: TextStyle(
                color: colors.textPrimary, fontSize: 16,
                fontWeight: FontWeight.bold)),
            const Spacer(),
            // Transport mode toggle — switching recalculates in-place
            _ModeChip(
              icon: Icons.directions_car_rounded, label: 'Auto',
              selected: transportMode == 'driving', colors: colors,
              onTap: () => onModeChanged('driving')),
            const SizedBox(width: 6),
            _ModeChip(
              icon: Icons.directions_walk_rounded, label: 'A piedi',
              selected: transportMode == 'walking', colors: colors,
              onTap: () => onModeChanged('walking')),
          ]),
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 122,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: alternatives.length,
            itemBuilder: (_, i) => _RouteCard(
              route: alternatives[i], isSelected: i == selected, isBest: i == 0,
              colors: colors, onTap: () => onSelect(i),
            ),
          ),
        ),
        const SizedBox(height: 14),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(children: [
            Expanded(
              child: OutlinedButton(
                onPressed: onCancel,
                style: OutlinedButton.styleFrom(
                  side: BorderSide(color: colors.border),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                child: Text(AppLocalizations.of(context)!.cancel,
                    style: TextStyle(color: colors.textSecondary)),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(flex: 2,
              child: FilledButton.icon(
                onPressed: () => onConfirm(alternatives[selected]),
                style: FilledButton.styleFrom(
                  backgroundColor: colors.accent,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon: Icon(
                  transportMode == 'walking'
                      ? Icons.directions_walk_rounded
                      : Icons.navigation_rounded,
                  color: Colors.white, size: 18),
                label: Text(AppLocalizations.of(context)!.startNavigation,
                    style: const TextStyle(color: Colors.white)),
              ),
            ),
          ]),
        ),
        SizedBox(height: bottomInset > 0 ? bottomInset : 16),
      ]),
    );
  }
}

class _RouteCard extends StatelessWidget {
  final RouteResult route;
  final bool isSelected;
  final bool isBest;
  final RoadstrColors colors;
  final VoidCallback onTap;
  const _RouteCard({required this.route, required this.isSelected,
      required this.isBest, required this.colors, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        width: 128,
        margin: const EdgeInsets.only(right: 10),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: isSelected ? colors.accentSoft : colors.surface2,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected ? colors.accent : colors.border,
            width: isSelected ? 2 : 0.5,
          ),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min, children: [
          Text(route.durationLabel,
              maxLines: 1, overflow: TextOverflow.ellipsis,
              style: TextStyle(color: colors.textPrimary, fontSize: 19,
                  fontWeight: FontWeight.bold)),
          const SizedBox(height: 3),
          Text(route.distanceLabel,
              maxLines: 1, overflow: TextOverflow.ellipsis,
              style: TextStyle(color: colors.textSecondary, fontSize: 12)),
          if (isBest) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: colors.accent.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(AppLocalizations.of(context)!.fastestRoute, style: TextStyle(
                  color: colors.accent, fontSize: 10,
                  fontWeight: FontWeight.w600)),
            ),
          ],
        ]),
      ),
    );
  }
}

class _TimeBubble extends StatelessWidget {
  final String label;
  final bool isSelected;
  final Color accent;
  const _TimeBubble({required this.label, required this.isSelected,
      required this.accent});

  @override
  Widget build(BuildContext context) {
    return Container(
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: isSelected ? accent : Colors.grey[700],
        borderRadius: BorderRadius.circular(14),
        boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 4)],
      ),
      child: Text(label, style: const TextStyle(
          color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
    );
  }
}

// ── Widgets ───────────────────────────────────────────────────────────────────

class _SearchBarWidget extends StatelessWidget {
  final TextEditingController controller;
  final RoadstrColors colors;
  final VoidCallback onFocus;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;
  const _SearchBarWidget({
    required this.controller, required this.colors,
    required this.onFocus, required this.onChanged,
    required this.onSubmitted, required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      decoration: BoxDecoration(
        color: colors.surface2, borderRadius: BorderRadius.circular(28),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.18),
            blurRadius: 12, offset: const Offset(0, 3))],
      ),
      child: Row(children: [
        const SizedBox(width: 18),
        Icon(Icons.search, color: colors.textSecondary, size: 20),
        const SizedBox(width: 12),
        Expanded(
          child: TextField(
            controller: controller, onTap: onFocus, onChanged: onChanged,
            onSubmitted: onSubmitted,
            textInputAction: TextInputAction.search,
            style: TextStyle(color: colors.textPrimary, fontSize: 16),
            decoration: InputDecoration(
              hintText: AppLocalizations.of(context)!.searchHint,
              hintStyle: TextStyle(color: colors.textSecondary, fontSize: 16),
              border: InputBorder.none, isDense: true,
            ),
          ),
        ),
        if (controller.text.isNotEmpty)
          IconButton(
            icon: Icon(Icons.close, color: colors.textSecondary, size: 20),
            onPressed: onClear),
      ]),
    );
  }
}

// ── Purple dropped-pin marker ─────────────────────────────────────────────────

class _PinMarker extends StatelessWidget {
  const _PinMarker();
  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 32, height: 32,
          decoration: BoxDecoration(
            color: const Color(0xFF7C3AED),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2.5),
            boxShadow: [BoxShadow(
                color: const Color(0xFF7C3AED).withValues(alpha: 0.45),
                blurRadius: 8, offset: const Offset(0, 3))],
          ),
          child: const Icon(Icons.place_rounded, color: Colors.white, size: 18),
        ),
        CustomPaint(
          size: const Size(12, 10),
          painter: _PinStemPainter(),
        ),
      ],
    );
  }
}

class _PinStemPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = ui.Paint()
      ..color = const Color(0xFF7C3AED)
      ..style = ui.PaintingStyle.fill;
    final path = ui.Path()
      ..moveTo(size.width / 2 - 4, 0)
      ..lineTo(size.width / 2 + 4, 0)
      ..lineTo(size.width / 2, size.height)
      ..close();
    canvas.drawPath(path, paint);
  }
  @override
  bool shouldRepaint(_PinStemPainter _) => false;
}

// ── Pin destination panel ─────────────────────────────────────────────────────

class _PinPanel extends StatelessWidget {
  final NominatimResult result;
  final RoadstrColors colors;
  final VoidCallback onNavigate;
  final VoidCallback onClose;
  const _PinPanel({required this.result, required this.colors,
      required this.onNavigate, required this.onClose});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.18),
            blurRadius: 16, offset: const Offset(0, 4))],
      ),
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      child: Row(children: [
        Container(
          width: 40, height: 40,
          decoration: BoxDecoration(
              color: const Color(0xFF7C3AED).withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12)),
          child: const Icon(Icons.place_rounded,
              color: Color(0xFF7C3AED), size: 22),
        ),
        const SizedBox(width: 12),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(result.shortName, maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: colors.textPrimary,
                    fontSize: 14, fontWeight: FontWeight.w600)),
            if (result.categoryLabel.isNotEmpty)
              Text(result.categoryLabel, maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                      color: colors.textSecondary, fontSize: 12)),
          ],
        )),
        const SizedBox(width: 8),
        FilledButton.icon(
          style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF7C3AED),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10)),
          onPressed: onNavigate,
          icon: const Icon(Icons.navigation_rounded, size: 16),
          label: Text(l.startNavigation,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
        ),
        const SizedBox(width: 4),
        IconButton(
          icon: Icon(Icons.close_rounded, color: colors.textSecondary, size: 20),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
          onPressed: onClose,
        ),
      ]),
    );
  }
}

class _SearchResults extends StatelessWidget {
  final List<NominatimResult> results;
  final List<FavoritePlace> favorites;
  final bool isLoading;
  final RoadstrColors colors;
  final ValueChanged<NominatimResult> onSelect;
  final ValueChanged<FavoritePlace> onSelectFavorite;
  const _SearchResults({required this.results, required this.isLoading,
      required this.colors, required this.onSelect,
      required this.onSelectFavorite, this.favorites = const []});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2, borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.15),
            blurRadius: 12, offset: const Offset(0, 4))],
      ),
      child: isLoading && favorites.isEmpty
          ? Padding(padding: const EdgeInsets.all(16),
              child: Center(child: SizedBox(width: 20, height: 20,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: colors.accent))))
          : Material(
              color: Colors.transparent,
              child: Column(mainAxisSize: MainAxisSize.min, children: [

                // ── Saved places matching the query ──────────────────────
                if (favorites.isNotEmpty)
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    padding: EdgeInsets.zero,
                    itemCount: favorites.length,
                    separatorBuilder: (_, __) =>
                        Divider(height: 0.5, color: colors.border),
                    itemBuilder: (_, i) {
                      final fav = favorites[i];
                      return ListTile(
                        tileColor: Colors.transparent,
                        leading: Container(
                          width: 36, height: 36,
                          decoration: BoxDecoration(
                              color: colors.accentSoft,
                              borderRadius: BorderRadius.circular(10)),
                          child: Icon(Icons.favorite_rounded,
                              color: colors.accent, size: 18),
                        ),
                        title: Text(fav.label, style: TextStyle(
                            color: colors.textPrimary, fontSize: 14,
                            fontWeight: FontWeight.w600)),
                        subtitle: Text(fav.address, maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(color: colors.textSecondary, fontSize: 12)),
                        onTap: () => onSelectFavorite(fav),
                      );
                    },
                  ),

                // ── Nominatim results ────────────────────────────────────
                if (isLoading)
                  Padding(padding: const EdgeInsets.all(16),
                      child: Center(child: SizedBox(width: 20, height: 20,
                          child: CircularProgressIndicator(
                              strokeWidth: 2, color: colors.accent))))
                else if (results.isNotEmpty) ...[
                  if (favorites.isNotEmpty)
                    Divider(height: 0.5, color: colors.border),
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    padding: EdgeInsets.zero,
                    itemCount: results.length,
                    separatorBuilder: (_, __) =>
                        Divider(height: 0.5, color: colors.border),
                    itemBuilder: (_, i) {
                      final r = results[i];
                      final catLabel = r.categoryLabel;
                      return ListTile(
                        tileColor: Colors.transparent,
                        leading: Container(
                          width: 36, height: 36,
                          decoration: BoxDecoration(
                            color: colors.accentSoft,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Center(
                            child: Text(r.emoji,
                                style: const TextStyle(fontSize: 18, height: 1)),
                          ),
                        ),
                        title: Text(r.shortName, style: TextStyle(
                            color: colors.textPrimary, fontSize: 14,
                            fontWeight: FontWeight.w500)),
                        subtitle: Text(
                          catLabel.isNotEmpty ? catLabel : r.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              color: colors.textSecondary, fontSize: 12)),
                        onTap: () => onSelect(r),
                      );
                    },
                  ),
                ],
              ]),
            ), // Material
    );
  }
}

class _NavInstruction extends StatelessWidget {
  final RouteStep step;
  final RouteResult route;
  final int stepIdx;
  final RoadstrColors colors;
  /// Live GPS distance to the next maneuver point. Updated every GPS tick so
  /// the countdown reflects actual position, not the pre-calculated step length.
  final double distToNextM;
  const _NavInstruction({required this.step, required this.route,
      required this.stepIdx, required this.colors,
      this.distToNextM = 0});

  @override
  Widget build(BuildContext context) {
    final land = MediaQuery.of(context).orientation == Orientation.landscape;
    final iconSz = land ? 32.0 : 48.0;
    final boxSz  = land ? 56.0 : 88.0;
    final fsMain = land ? 16.0 : 22.0;
    final fsSub  = land ? 13.0 : 17.0;
    final vPad   = land ? 10.0 : 20.0;
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 16, vertical: vPad),
      decoration: BoxDecoration(
        color: colors.surface2, borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.18),
            blurRadius: 12, offset: const Offset(0, 3))],
      ),
      child: Row(children: [
        Container(
          width: boxSz, height: boxSz,
          decoration: BoxDecoration(color: colors.accentSoft,
              borderRadius: BorderRadius.circular(10)),
          child: Icon(_directionIcon(step.direction, step.modifier),
              color: colors.accent, size: iconSz),
        ),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min, children: [
          Text(step.instruction, style: TextStyle(color: colors.textPrimary,
              fontSize: fsMain, fontWeight: FontWeight.w600),
              maxLines: land ? 1 : 2, overflow: TextOverflow.ellipsis),
          const SizedBox(height: 2),
          // Show live distance to next maneuver when available (> 0);
          // fall back to the step's pre-calculated length otherwise.
          Text(_distLabel(distToNextM > 0 ? distToNextM : step.distanceM,
                AppLocalizations.of(context)!.now),
              style: TextStyle(color: colors.textSecondary, fontSize: fsSub)),
        ])),
      ]),
    );
  }

  String _distLabel(double m, String nowLabel) {
    if (m < 50) return nowLabel;
    if (m < 1000) return '${m.round()} m';
    return '${(m / 1000).toStringAsFixed(1)} km';
  }

  IconData _directionIcon(String direction, String modifier) {
    // Map direction+modifier to a meaningful icon.
    switch (direction) {
      case 'arrive':          return Icons.flag_rounded;
      case 'depart':          return Icons.play_arrow_rounded;
      case 'roundabout':
      case 'rotary':          return Icons.roundabout_right;
      case 'merge':           return Icons.merge;
      case 'fork':
        return modifier.contains('left')
            ? Icons.fork_left
            : Icons.fork_right;
      case 'on ramp':
      case 'off ramp':
        return modifier.contains('left')
            ? Icons.ramp_left
            : Icons.ramp_right;
      case 'end of road':
      case 'turn':
      case 'new name':
        return switch (modifier) {
          'left'         => Icons.turn_left,
          'right'        => Icons.turn_right,
          'slight left'  => Icons.turn_slight_left,
          'slight right' => Icons.turn_slight_right,
          'sharp left'   => Icons.turn_sharp_left,
          'sharp right'  => Icons.turn_sharp_right,
          'uturn'        => Icons.u_turn_left,
          _              => Icons.straight,
        };
      default: return Icons.straight;
    }
  }
}

class _NavPanel extends StatelessWidget {
  final RouteResult route;
  final double speed;
  final double bottomInset;
  final RoadstrColors colors;
  final VoidCallback onStop;
  /// Live remaining distance in metres (updated every GPS tick).
  final double remainingDistM;
  /// Live remaining seconds (estimated from remaining distance).
  final double remainingSecs;
  const _NavPanel({required this.route, required this.speed,
      required this.bottomInset, required this.colors, required this.onStop,
      this.remainingDistM = 0, this.remainingSecs = 0});

  String get _distLabel {
    final m = remainingDistM > 0 ? remainingDistM : route.totalDistanceM;
    if (m < 1000) return '${m.round()} m';
    return '${(m / 1000).toStringAsFixed(1)} km';
  }

  String get _timeLabel {
    final secs = remainingSecs > 0 ? remainingSecs : route.totalDurationS;
    final m = (secs / 60).round();
    if (m < 60) return '$m min';
    final h = m ~/ 60; final rem = m % 60;
    return '${h}h ${rem}min';
  }

  String get _etaLabel {
    final secs = remainingSecs > 0 ? remainingSecs : route.totalDurationS;
    final arr = DateTime.now().add(Duration(seconds: secs.round()));
    return '${arr.hour.toString().padLeft(2,'0')}:'
           '${arr.minute.toString().padLeft(2,'0')}';
  }

  @override
  Widget build(BuildContext context) {
    final land     = MediaQuery.of(context).orientation == Orientation.landscape;
    final speedoSz = land ? 70.0 : 110.0;
    final fsDist   = land ? 16.0 : 22.0;
    final fsSub    = land ? 11.0 : 13.0;
    final vTop     = land ?  6.0 : 14.0;
    final vBot     = land
        ? (bottomInset > 0 ? bottomInset + 4 : 8.0)
        : (bottomInset > 0 ? bottomInset     : 16.0);
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        border: Border(top: BorderSide(color: colors.border, width: 0.5)),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 12, offset: const Offset(0, -2))],
      ),
      padding: EdgeInsets.only(left: 16, right: 16, top: vTop, bottom: vBot),
      child: Row(children: [
        SpeedometerWidget(speedKmh: speed, size: speedoSz),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min, children: [
          // Remaining distance — updates every GPS tick
          Text(_distLabel, style: TextStyle(color: colors.textPrimary,
              fontSize: fsDist, fontWeight: FontWeight.bold)),
          Row(children: [
            // Remaining time
            Text(_timeLabel,
                style: TextStyle(color: colors.textSecondary, fontSize: fsSub)),
            if (!land) ...[
              Text('  ·  ', style: TextStyle(
                  color: colors.textSecondary, fontSize: fsSub)),
              // Estimated time of arrival
              Text(AppLocalizations.of(context)!.etaArrivalLabel(_etaLabel),
                  style: TextStyle(color: colors.textSecondary, fontSize: fsSub)),
            ],
          ]),
        ])),
        // Stop navigation
        GestureDetector(onTap: onStop,
          child: Container(width: 48, height: 48,
            decoration: BoxDecoration(
              color: const Color(0xFFFF4444).withValues(alpha: 0.12),
              shape: BoxShape.circle,
              border: Border.all(
                  color: const Color(0xFFFF4444).withValues(alpha: 0.4)),
            ),
            child: const Icon(Icons.close_rounded,
                color: Color(0xFFFF4444), size: 24)),
        ),
      ]),
    );
  }
}

class _BottomBar extends StatelessWidget {
  final double bottomInset; final RoadstrColors colors;
  const _BottomBar({required this.bottomInset, required this.colors});
  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: colors.surface2,
      border: Border(top: BorderSide(color: colors.border, width: 0.5)),
      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.12),
          blurRadius: 12, offset: const Offset(0, -2))],
    ),
    padding: EdgeInsets.only(top: 10,
        bottom: bottomInset > 0 ? bottomInset : 14),
    child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
      _BottomBarItem(icon: Icons.account_circle_outlined, label: AppLocalizations.of(context)!.bottomBarProfile,
          colors: colors, onTap: () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => const ProfileScreen()))),
      const SizedBox(width: 48),
      _BottomBarItem(icon: Icons.menu, label: AppLocalizations.of(context)!.bottomBarMenu, colors: colors,
          onTap: () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => const SettingsScreen()))),
    ]),
  );
}

class _BottomBarItem extends StatelessWidget {
  final IconData icon; final String label;
  final RoadstrColors colors; final VoidCallback onTap;
  const _BottomBarItem({required this.icon, required this.label,
      required this.colors, required this.onTap});
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap, borderRadius: BorderRadius.circular(12),
    child: Padding(padding: const EdgeInsets.symmetric(
        horizontal: 20, vertical: 6),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, color: colors.textSecondary, size: 26),
        const SizedBox(height: 2),
        Text(label, style: TextStyle(
            color: colors.textSecondary, fontSize: 11)),
      ]),
    ),
  );
}

class _MapFab extends StatelessWidget {
  final Widget child; final VoidCallback onTap; final RoadstrColors colors;
  const _MapFab({required this.child, required this.onTap,
      required this.colors});
  @override
  Widget build(BuildContext context) => Material(
    color: colors.surface2, shape: const CircleBorder(), elevation: 3,
    shadowColor: Colors.black.withValues(alpha: 0.25),
    child: InkWell(onTap: onTap, customBorder: const CircleBorder(),
        child: SizedBox(width: 44, height: 44,
            child: Center(child: child))),
  );
}

/// Compass / heading-mode FAB.
/// Shows a purple navigation cursor on a white circle; the cursor rotates to
/// reflect the current map orientation. A red "N" sits at the arrow tip so
/// the user can always see which way is north. When heading mode is active the
/// border glows purple.
// ── Speed limit sign ─────────────────────────────────────────────────────────

/// Circular road sign: red border, white fill, black number — exactly like a
/// real posted speed limit sign. Only rendered when a numeric limit is known.
class _SpeedLimitSign extends StatelessWidget {
  final int speedKmh;
  const _SpeedLimitSign(this.speedKmh);

  @override
  Widget build(BuildContext context) {
    final fontSize = speedKmh >= 100 ? 13.0 : 16.0;
    return Container(
      width: 46, height: 46,
      decoration: BoxDecoration(
        color: Colors.white,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.red, width: 4),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.25),
              blurRadius: 6, offset: const Offset(0, 2)),
        ],
      ),
      child: Center(
        child: Text(
          '$speedKmh',
          style: TextStyle(
            color: Colors.black,
            fontSize: fontSize,
            fontWeight: FontWeight.w900,
            height: 1,
            letterSpacing: -0.5,
          ),
        ),
      ),
    );
  }
}

class _CompassFab extends StatelessWidget {
  final double rotDeg;
  final bool active;
  final VoidCallback onTap;
  const _CompassFab({required this.rotDeg, required this.active,
      required this.onTap});

  static const _purple = Color(0xFF7C3AED);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 44, height: 44,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          border: active
              ? Border.all(color: _purple, width: 2)
              : null,
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.22),
                blurRadius: 8, offset: const Offset(0, 2)),
            if (active)
              BoxShadow(
                  color: _purple.withValues(alpha: 0.3),
                  blurRadius: 10, spreadRadius: 1),
          ],
        ),
        child: Transform.rotate(
          angle: -rotDeg * math.pi / 180,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Icon(Icons.navigation_rounded,
                  color: active ? _purple : const Color(0xFF9CA3AF), size: 26),
              // Red "N" at the arrow tip (top of the icon)
              Positioned(
                top: 5,
                child: Text('N',
                    style: const TextStyle(
                        color: Colors.red, fontSize: 8,
                        fontWeight: FontWeight.w900, height: 1.0)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UserMarker extends StatelessWidget {
  final double heading; final Color accent;
  const _UserMarker({required this.heading, required this.accent});
  @override
  Widget build(BuildContext context) => Transform.rotate(
    angle: heading * math.pi / 180,
    child: CustomPaint(size: const Size(48, 48),
        painter: _MarkerPainter(accent: accent)),
  );
}

class _MarkerPainter extends CustomPainter {
  final Color accent;
  const _MarkerPainter({required this.accent});
  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height / 2);
    final r = size.width / 2 - 4;
    canvas.drawCircle(c, r, Paint()..color = accent.withValues(alpha: 0.18));
    canvas.drawCircle(c, r, Paint()
      ..color = accent..style = PaintingStyle.stroke..strokeWidth = 2);
    final arrow = ui.Path()
      ..moveTo(c.dx, c.dy - r + 4)..lineTo(c.dx - 8, c.dy + 10)
      ..lineTo(c.dx, c.dy + 4)..lineTo(c.dx + 8, c.dy + 10)..close();
    canvas.drawPath(arrow, Paint()..color = accent);
  }
  @override
  bool shouldRepaint(_MarkerPainter old) => old.accent != accent;
}

class _DestinationMarker extends StatelessWidget {
  final Color color;
  const _DestinationMarker({required this.color});
  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min, children: [
    Container(width: 32, height: 32,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle,
          boxShadow: [BoxShadow(color: color.withValues(alpha: 0.4),
              blurRadius: 8, spreadRadius: 2)]),
      child: const Icon(Icons.flag_rounded, color: Colors.white, size: 18)),
    CustomPaint(size: const Size(2, 12),
        painter: _PinLinePainter(color: color)),
  ]);
}

class _PinLinePainter extends CustomPainter {
  final Color color;
  const _PinLinePainter({required this.color});
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawLine(Offset(size.width/2, 0), Offset(size.width/2, size.height),
        Paint()..color = color..strokeWidth = 2);
  }
  @override bool shouldRepaint(_) => false;
}
