// First real slice of the MapLibre rendering engine — reachable via the
// "mapEngine" setting (Impostazioni → Mappa), not merged in behind
// kDebugMode like the throwaway PoC it grew out of.
//
// Deliberately incomplete: this is the incremental migration from
// docs/rendering-engine-decision.md §7, not a replacement for MapScreen.
// Destination search (POI categories + matching favourites), real
// routing with a proper preview panel before committing, ZTL-aware route
// colouring, speed camera/parking markers, hazard reporting, north-up/
// heading-up toggle, navigation with off-route detection, rerouting and
// voice guidance are all here now. Favourites-as-map-markers is not —
// matching MapScreen, which doesn't draw them either, only as search
// rows. What's left is polish, not missing features: chained voice
// instructions, direction-aware off-route, tap-to-manage parking beyond
// save/clear, multi-stop route planning. Each noted at its own commit.
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:amberflutter/amberflutter.dart';
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';
import 'package:maplibre/maplibre.dart' hide Box;
import 'package:nostr_tools/nostr_tools.dart' show Nip19;
import 'package:provider/provider.dart';
import 'package:screen_brightness/screen_brightness.dart';
import 'package:sensors_plus/sensors_plus.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../l10n/app_localizations.dart';
import '../models/activity_notification.dart';
import '../models/favorite_place.dart';
import '../models/search_history_item.dart';
import '../services/activity_notification_service.dart';
import '../services/camera_follow.dart';
import '../services/favorites_sync_service.dart';
import '../services/gps_service.dart';
import '../services/kokoro/kokoro_tts_service.dart';
import '../services/kokoro/kokoro_voices.dart';
import '../services/navigation_guidance.dart';
import '../services/navigation_notification_service.dart';
import '../services/nostr_relay_service.dart';
import '../services/place_search_service.dart';
import '../services/poi_search_service.dart';
import '../services/route_progress.dart';
import '../services/routing_service.dart';
import '../services/speed_camera_service.dart';
import '../services/speed_limit_service.dart';
import '../services/ztl_service.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../utils/geo.dart';
import '../utils/heading_filter.dart';
import '../utils/settings_listenable.dart';
import '../utils/units.dart';
import '../widgets/cursor_painter.dart';
import '../widgets/map/map_chrome.dart';
import '../widgets/map/map_markers.dart';
import '../widgets/nav/nav_hud.dart';
import '../widgets/nav/speed_limit_sign.dart';
import '../widgets/route/route_panels.dart';
import '../widgets/search/search_panel.dart';
import '../widgets/sheets/road_event_sheets.dart';
import '../widgets/speedometer_widget.dart';

const _roadstrTileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

// Same recolouring as MapScreen._darkTileBuilder, done natively instead of
// through a Flutter ColorFilter — see the PoC commit for why the numbers
// are what they are (hue-rotate 180° to undo inversion's colour shift,
// brightness min/max flipped to invert lightness, saturation and contrast
// pulled back so area fills read as muted rather than neon). Unlike
// MapScreen, dark mode here never needs a separate CARTO dark-tile source —
// the same native paint transform recolours whatever raster source is
// configured, [tileUrl] included.
String _style({required bool dark, required String tileUrl}) => '''
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["$tileUrl"],
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

/// The MapLibre engine's own navigation cursor — a from-scratch replacement
/// for [UserMarker], built after reusing it here produced a doubled shadow:
/// the arrow.svg asset UserMarker draws already bakes in its own static
/// shadow ellipse, and stacking the new pitch-driven parallax shadow on top
/// of that duplicated it. Painting the arrow ourselves means there is
/// exactly one shadow, sized by [pitch] from the start, and nothing here
/// touches assets/cursors/ or the CursorStyle/CursorWidget asset pipeline —
/// those stay exactly as they are for MapScreen ("roadstr light"). Same
/// silhouette and colour treatment as arrow.svg (the default driving
/// cursor), tinted by the same movement-cursor colour setting instead of a
/// fixed blue. Vehicle-skin selection (formula1, suv, the ostrich walking
/// sprite, …) isn't ported — this screen shows one pointer shape, matching
/// how much of the rest of the cursor system it already skips (no
/// ostrich/bicycle swap for the walking/cycling profiles either).
class _MaplibreCursor extends StatelessWidget {
  final double pitch;
  final Color color;

  const _MaplibreCursor({required this.pitch, required this.color});

  @override
  Widget build(BuildContext context) {
    // 0 at top-down, 1 at maxPitch (60°, set in MapOptions below).
    final t = (pitch / 60.0).clamp(0.0, 1.0);
    return SizedBox(
      width: 48,
      height: 76,
      child: CustomPaint(
        painter: _CursorPainter(color: color, t: t),
      ),
    );
  }
}

class _CursorPainter extends CustomPainter {
  final Color color;
  final double t;

  const _CursorPainter({required this.color, required this.t});

  @override
  void paint(Canvas canvas, Size size) {
    // The arrow itself sits in the top 48×48 of the box, same as arrow.svg's
    // own 48×48 viewBox — the extra height below is headroom for the shadow
    // to stretch into as it drifts down with tilt.
    final shadowPaint = Paint()
      ..shader = RadialGradient(colors: [
        Colors.black.withValues(alpha: 0.30 - t * 0.08),
        Colors.black.withValues(alpha: 0),
      ]).createShader(Rect.fromCircle(center: Offset.zero, radius: 16))
      ..style = PaintingStyle.fill;
    canvas.save();
    // Starts tucked just under the arrow's own base at t=0, drifts toward
    // the bottom of the box as tilt increases — the parallax cue that sells
    // "the cursor is a 3D object standing on the tilted road".
    canvas.translate(24, 50 + t * 22);
    canvas.scale(1 - t * 0.15, 0.3 + t * 0.35);
    canvas.drawCircle(Offset.zero, 16, shadowPaint);
    canvas.restore();

    final arrowPath = ui.Path()
      ..moveTo(24, 5.5)
      ..lineTo(36, 33)
      ..cubicTo(36.3, 33.7, 35.6, 34.4, 34.9, 34.1)
      ..lineTo(24, 29.6)
      ..lineTo(13.1, 34.1)
      ..cubicTo(12.4, 34.4, 11.7, 33.7, 12, 33)
      ..close();
    final hsl = HSLColor.fromColor(color);
    final fillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          hsl.withLightness((hsl.lightness + 0.18).clamp(0.0, 1.0)).toColor(),
          hsl.withLightness((hsl.lightness - 0.10).clamp(0.0, 1.0)).toColor(),
        ],
      ).createShader(const Rect.fromLTWH(0, 5.5, 48, 28.6));
    canvas.drawPath(arrowPath, fillPaint);
    canvas.drawPath(
      arrowPath,
      Paint()
        ..color =
            hsl.withLightness((hsl.lightness - 0.30).clamp(0.0, 1.0)).toColor()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..strokeJoin = StrokeJoin.round,
    );
    canvas.drawLine(
      const Offset(24, 10),
      const Offset(24, 25.5),
      Paint()
        ..color = Colors.white.withValues(alpha: 0.5)
        ..strokeWidth = 1.4
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(_CursorPainter old) => old.color != color || old.t != t;
}

class MaplibreMapScreen extends StatefulWidget {
  const MaplibreMapScreen({super.key});

  @override
  State<MaplibreMapScreen> createState() => _MaplibreMapScreenState();
}

class _MaplibreMapScreenState extends State<MaplibreMapScreen>
    with WidgetsBindingObserver {
  final _gps = GpsService();
  StreamSubscription<GpsData>? _gpsSub;
  MapController? _controller;

  // Mirrors MapScreen's _followUser: true until the user pans/rotates/tilts
  // by hand, at which point their gesture must not be immediately fought by
  // the next GPS fix. Actual initial value comes from the 'autoCenterOnLaunch'
  // setting in initState, not this default.
  bool _followUser = true;
  GpsData? _lastFix;

  /// Screen-wake and minimum-brightness policy — same
  /// MapScreen._applyScreenPolicy, reacting to the same three settings.
  late final ValueListenable<Box> _screenPolicyListenable;

  /// Smoothed GPS altitude, same exponential smoothing MapScreen applies —
  /// raw altitude jitters by several metres fix to fix even standing still.
  double? _altitudeM;
  bool? _stylingDark;
  String? _stylingTileUrl;

  /// True: the camera turns to face the direction of travel (what every
  /// GPS fix has been doing all along). False: north stays up, the same
  /// toggle MapScreen's CompassFab drives — the map simply stops being
  /// re-aimed at heading each fix; the driver's own rotate gesture still
  /// works either way.
  bool _headingMode = true;

  /// Live camera pitch and bearing, read back from MapEventMoveCamera — the
  /// two-finger tilt/rotate gestures are entirely native, nothing here
  /// drives them, so this is the only way to know the current values.
  /// _pitch sizes the cursor's shadow (below); _bearing points the compass
  /// needle, which _camState alone can't do once the user has taken manual
  /// control and the follow ticker (the only other thing that updates
  /// _camState) has stopped running.
  double _pitch = 45.0;
  double _bearing = 0;

  /// Same dead-reckoning heading filter MapScreen feeds every GPS tick —
  /// without it the raw course-over-ground (data.heading) is what drove the
  /// camera's bearing here, and that value is genuinely noisy at low speed,
  /// which is what made the cursor look "ballerino": the cursor itself never
  /// moves (it stays screen-up), so a shaky *camera* bearing reads as a
  /// shaky cursor. HeadingFilter has no flutter_map dependency — it only
  /// ever touches LatLng/Geo — so it ports directly. routeLocalBearingAt is
  /// [_routeLocalBearingAt] below, the same progress-along-route
  /// disambiguation MapScreen runs, ported alongside its supporting state.
  final _headingFilter = HeadingFilter();
  LatLng? _prevGpsPos;

  // ── Compass (magnetometer + accelerometer) ────────────────────────────────
  // Same tilt-compensated compass MapScreen runs — ported unchanged (pure
  // sensor math, no flutter_map dependency). Owns the heading only while
  // stationary and not navigating; in motion the GPS/route bearing above
  // takes over, same split as MapScreen's own _startCompass.
  double _compassHeading = 0;
  AccelerometerEvent? _lastAccel;
  StreamSubscription<MagnetometerEvent>? _magnetSub;
  StreamSubscription<AccelerometerEvent>? _accelSub;
  int _lastCompassUiMs = 0;

  // ── Route-local bearing (for HeadingFilter's route-snap easing) ──────────
  // Same windowed nearest-segment search + progress-based disambiguation as
  // MapScreen's _nearestActiveRouteSegment/_segmentNearestInProgress —
  // needed so a two-fix GPS bearing at a roundabout snaps toward the route's
  // own direction instead of the nearest (possibly wrong-way) arm.
  int _nearestRouteSegmentIdx = 0;
  double _routeProgressM = 0;

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
  final _poiSvc = PoiSearchService();
  final _ztl = ZtlService.instance;

  /// In-zone/near-restricted-street banner state — same
  /// MapScreen fields, driven by the same ZtlService the route colouring
  /// already warms in the background.
  ZtlWay? _ztlPassingBy;
  bool _ztlNoticeDismissed = false;
  bool _inZtl = false;
  String? _ztlName;
  bool _showSearch = false;
  bool _searching = false;
  List<NominatimResult> _searchResults = [];
  NearbyCategory? _nearbyCategory;
  Timer? _searchDebounce;
  RouteResult? _route;
  LatLng? _destination;

  /// A destination picked before the first GPS fix arrived — replayed once
  /// one does, in _onGps. Same MapScreen._awaitingFixDestination.
  LatLng? _awaitingFixDestination;
  List<({List<LatLng> points, bool restricted})> _routeRuns = [];

  /// 'driving' / 'cycling' / 'walking' — same three profiles
  /// RoutingService.getRoutes accepts. Switching modes on the preview panel
  /// recalculates the route for the new profile via [_calculateRouteTo],
  /// mirroring MapScreen._recalculateForMode.
  String _transportMode = 'driving';

  // ── Navigation ─────────────────────────────────────────────────────────────
  // RouteProgress (lib/services/route_progress.dart) is the pure geometry;
  // this is just the state machine driving it. No voice guidance, no
  // off-route detection, no rerouting yet — this proves step advancement and
  // a distance-to-maneuver number against a real route, each of those other
  // pieces is its own increment on top.
  bool _isNavigating = false;

  /// True once _onArrival has fired for the current trip — purely a
  /// reentry guard at that point (arrival itself ends navigation
  /// immediately, same as MapScreen), reset at the next _startNavigation.
  bool _arrived = false;

  /// Drives the transient "sei arrivato" banner — independent of
  /// _isNavigating (which is already false by the time this shows), same
  /// split MapScreen keeps between _showArrivalBanner and the trip itself
  /// having ended.
  bool _showArrivalBanner = false;
  Timer? _arrivalBannerTimer;

  // ── GPS signal loss (tunnels, underground) ────────────────────────────
  // Same watchdog MapScreen runs: a tunnel or underground car park stops
  // the fix stream entirely with no error, so this is the only way to
  // notice. Any fresh fix clears it in _onGps.
  bool _gpsSignalLost = false;
  Timer? _gpsLossTimer;
  int _lastFixEpochMs = 0;
  static const _gpsLossThresholdMs = 8000;
  int _currentStepIdx = 0;
  List<double> _cumDist = [];
  List<double> _stepCumDist = [];
  double _distToNextStepM = 0;

  /// Live remaining distance/time for the whole route, same figures
  /// MapScreen._updateRemainingStats feeds its NavPanel — recomputed from
  /// route progress on every GPS tick during navigation.
  double _remainingDistM = 0;
  double _remainingSecs = 0;

  /// Extra stops appended mid-journey via the "add stop" FAB — same idea as
  /// MapScreen._activeVia, simplified to append-only (no reorder/remove UI).
  List<LatLng> _activeVia = const [];
  bool _pickingWaypoint = false;

  /// Route position (not GPS wobble) close enough to a maneuver's own point
  /// to advance past it. Wider than typical GPS accuracy so a fix that lands
  /// slightly short or past the exact point doesn't stall the step index.
  static const _advanceToleranceM = 15.0;

  /// Horizontal accuracy (metres) of the most recent GPS fix — drives the
  /// dynamic arrival radius in [_checkArrival], same as MapScreen.
  double _lastGpsAccuracy = 20;

  /// Smallest GPS-to-destination distance observed so far this leg. Reset on
  /// every navigation start. Drives the closest-approach fallback in
  /// [_checkArrival]: once the user got reasonably close and is now moving
  /// away again, that closest point WAS the arrival.
  double _minDistToDestM = double.infinity;

  /// Destination's OSM building footprint, fetched best-effort at
  /// navigation start via PoiSearchService (already reused elsewhere in
  /// this screen). Stepping into it counts as arrival regardless of how far
  /// the router's arrive-point (on the road) is.
  List<LatLng>? _destBuilding;

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
  List<FavoritePlace> _favorites = [];

  /// Recent destinations, same MapScreen._history/SearchHistoryItem model —
  /// shown alongside favourites when the search box is focused but empty.
  List<SearchHistoryItem> _history = [];

  /// Pulls the encrypted favourites snapshot from Nostr at startup, same
  /// MapScreen._favSyncSvc/_autoRestoreFavorites — reused as-is, no
  /// flutter_map dependency.
  final _favSyncSvc = FavoritesSyncService();

  /// OSM-tagged speed limit, same source MapScreen's sign uses: the route's
  /// own annotation while navigating (route.speedLimitAt), Overpass proximity
  /// cache otherwise.
  final _speedLimitSvc = SpeedLimitService();
  int? _currentSpeedLimit;

  // ── Hazard reporting ─────────────────────────────────────────────────────
  // NostrRelayService reused as-is (never touched flutter_map). No
  // subscription to other people's reports here, so no own-report
  // suppression state is needed the way MapScreen keeps _myPubkey/
  // _roadEvents — this only publishes, it doesn't display anyone's pins yet.
  // connect() *is* required though, unlike the rest of this list — every
  // publish call throws Exception('Not connected to Nostr relay') without
  // it (NostrRelayService._requireConnected has no auto-connect fallback),
  // so hazard reporting was silently broken until initState calls it.
  final _nostr = NostrRelayService();
  static const _secStorage = FlutterSecureStorage();

  /// Persistent Android notification during nav (next manoeuvre + distance)
  /// — same NavigationNotificationService MapScreen uses.
  final _navNotif = NavigationNotificationService();

  /// Silently records the logged-in user's own activity (zaps received,
  /// confirm/deny on their own reports) for the Notifications screen — same
  /// ActivityNotificationService MapScreen uses, fed by the same
  /// _nostr.activityStream enableActivityNotifications (already called from
  /// _refreshHomeIdentity) populates.
  final _activityNotif = ActivityNotificationService();
  StreamSubscription<ActivityNotification>? _activitySub;

  // ── Home identity (bottom bar) ────────────────────────────────────────────
  // Same three SecureStorage values MapScreen._refreshHomeIdentity reads —
  // needed for MapBottomBar's profile/notifications entries.
  String? _myPubkey;
  String? _profilePicture;
  String? _nostrFlavor;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadParkingPosition();
    _loadFavorites();
    _loadHistory();
    unawaited(_autoRestoreFavorites());
    _startCompass();
    // Same settings keys MapScreen reads at startup — muting or changing
    // voice/speed/volume in Settings applies here too, since it is the same
    // app-wide preference, not a copy of it.
    final settings = Hive.box('settings');
    _voiceMuted = !(settings.get('voiceEnabled', defaultValue: true) as bool);
    _tts.setGender(settings.get('kokoroVoiceGender',
        defaultValue: kKokoroDefaultGender) as String);
    _tts.setSpeed(kKokoroSpeedStages[settings.get('kokoroSpeedStage',
        defaultValue: kKokoroDefaultSpeedStage) as int]);
    _tts.setVolume(
        (settings.get('kokoroVolume', defaultValue: 1.0) as num).toDouble());
    // Warms the TTS engine in the stored voice-language preference rather
    // than a hardcoded 'it', same fallback MapScreen's own startup warm-up
    // uses.
    final startLang = settings.get('language', defaultValue: '') as String;
    unawaited(_tts.init(startLang.isNotEmpty ? startLang : 'it'));
    unawaited(_refreshHomeIdentity());
    _activitySub = _nostr.activityStream.listen(_recordActivityNotification);
    unawaited(_nostr.connect());

    _screenPolicyListenable = SettingsListenable.forKeys(
        const ['keepScreenOn', 'keepScreenOnAlways', 'minBrightness']);
    _screenPolicyListenable.addListener(_applyScreenPolicy);
    _applyScreenPolicy();

    // Default ON, same as MapScreen: a navigation app should warm up GPS at
    // launch. Turning this off in Settings leaves the map at its neutral
    // view instead of jumping to the driver until they tap the GPS FAB —
    // the GPS stream itself still starts (this screen's speed-camera/ZTL/
    // speed-limit caches all depend on a live fix regardless of whether the
    // camera follows it), which is the one place this diverges from
    // MapScreen's own stricter "don't even acquire a fix" reading of the
    // setting.
    _followUser =
        settings.get('autoCenterOnLaunch', defaultValue: true) == true;
    unawaited(_gps.start());
    _gpsSub = _gps.stream.listen(_onGps);
  }

  /// Same read as MapScreen._refreshHomeIdentity, ported directly — the
  /// three SecureStorage values MapBottomBar needs (login state, pubkey,
  /// avatar), plus the same activity-notification subscription toggle.
  /// Records social activity silently for the Notifications screen — same
  /// MapScreen._recordActivityNotification, ported directly.
  void _recordActivityNotification(ActivityNotification notification) {
    final pubkey = _myPubkey;
    if (pubkey == null) return;
    unawaited(_activityNotif.record(pubkey, notification));
  }

  Future<void> _refreshHomeIdentity() async {
    if (!mounted) return;
    final values = await Future.wait([
      _secStorage.read(key: 'nostr_pub_hex'),
      _secStorage.read(key: 'nostr_flavor'),
      _secStorage.read(key: 'nostr_picture'),
    ]);
    var pub = values[0];
    final flavor = values[1];
    var picture = values[2];
    final loggedIn = pub != null && (flavor == 'amber' || flavor == 'nsec');
    if (!loggedIn) {
      pub = null;
      picture = null;
    }
    if (!mounted) return;
    final identityChanged = pub != _myPubkey;
    setState(() {
      _myPubkey = pub;
      _nostrFlavor = loggedIn ? flavor : null;
      _profilePicture = picture;
    });
    if (pub != null && identityChanged) {
      unawaited(_nostr.enableActivityNotifications(pub));
    } else if (pub == null && identityChanged) {
      _nostr.disableActivityNotifications();
    }
    if (pub != null && (picture == null || picture.isEmpty)) {
      final requestedPub = pub;
      final profile = await NostrRelayService.fetchProfile(requestedPub);
      final fetchedPicture = profile?.picture;
      if (fetchedPicture == null || fetchedPicture.isEmpty) return;
      final currentPub = await _secStorage.read(key: 'nostr_pub_hex');
      if (currentPub != requestedPub) return;
      await _secStorage.write(key: 'nostr_picture', value: fetchedPicture);
      if (mounted && _myPubkey == requestedPub) {
        setState(() => _profilePicture = fetchedPicture);
      }
    }
  }

  /// Subscribes to the device magnetometer and accelerometer to compute a
  /// tilt-compensated compass bearing — ported unchanged from
  /// MapScreen._startCompass. All processing is on-device; no data is
  /// transmitted to any external server.
  void _startCompass() {
    _accelSub = accelerometerEventStream().listen((e) => _lastAccel = e);
    _magnetSub = magnetometerEventStream().listen((mag) {
      final acc = _lastAccel;
      if (acc == null) return;
      final az = _compassAzimuth(acc, mag);
      if (!az.isFinite) return;

      // Exponential low-pass filter with wrap-around. α=0.15: slow enough to
      // suppress sensor noise, fast enough to feel responsive.
      double diff = az - _compassHeading;
      while (diff > 180) {
        diff -= 360;
      }
      while (diff < -180) {
        diff += 360;
      }
      _compassHeading += diff * 0.15;
      _compassHeading = _compassHeading % 360;
      if (_compassHeading < 0) _compassHeading += 360;

      // Throttled to ~10 Hz, same reason as MapScreen: the raw ~50 Hz stream
      // overwhelms the camera controller with more moveCamera calls than it
      // can settle, which shows up as oscillation rather than smoothness.
      final now = DateTime.now().millisecondsSinceEpoch;
      if (now - _lastCompassUiMs >= 100) {
        _lastCompassUiMs = now;
        // The compass owns the heading only while standing still and not
        // navigating — in motion the magnetometer reports where the phone
        // points, which in a cradle or a pocket is not where the car points.
        if (mounted &&
            !_isNavigating &&
            !_headingFilter.isMoving &&
            _headingMode &&
            _followUser) {
          final base = _camState;
          if (base != null) {
            _targetState = CameraFollowState(
                lat: base.lat,
                lng: base.lng,
                zoom: base.zoom,
                rotDeg: _compassHeading);
            _startFollowTicker();
          }
        }
      }
    });
  }

  /// Tilt-compensated compass azimuth (degrees, 0 = North, clockwise) —
  /// ported unchanged from MapScreen._compassAzimuth. See that method's own
  /// doc comment for the full derivation; all computation is on-device.
  double _compassAzimuth(AccelerometerEvent acc, MagnetometerEvent mag) {
    double gx = -acc.x, gy = -acc.y, gz = -acc.z;
    final gN = math.sqrt(gx * gx + gy * gy + gz * gz);
    if (gN < 0.1) return _compassHeading;
    gx /= gN;
    gy /= gN;
    gz /= gN;

    double ex = gy * mag.z - gz * mag.y;
    double ey = gz * mag.x - gx * mag.z;
    double ez = gx * mag.y - gy * mag.x;
    final eN = math.sqrt(ex * ex + ey * ey + ez * ez);
    if (eN < 0.1) return _compassHeading;
    ex /= eN;
    ey /= eN;
    ez /= eN;

    final nz = ex * gy - ey * gx;

    var az = math.atan2(-ez, -nz) * 180 / math.pi;
    if (az < 0) az += 360;
    return az;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Same battery-saving split MapScreen's own lifecycle handler uses for
    // the compass specifically: stop the sensor streams while backgrounded,
    // restart on return. GPS itself is left to GpsService/the OS foreground
    // service — this screen doesn't yet mirror MapScreen's separate
    // idle-stop-with-grace-period timer for the location stream, only the
    // compass half of that lifecycle handling.
    switch (state) {
      case AppLifecycleState.paused:
        _magnetSub?.cancel();
        _magnetSub = null;
        _accelSub?.cancel();
        _accelSub = null;
      case AppLifecycleState.resumed:
        if (_magnetSub == null) _startCompass();
      default:
        break;
    }
  }

  /// Same storage shape MapScreen._loadFavorites reads — one JSON string per
  /// favourite in the 'favorites' list.
  void _loadFavorites() {
    final raw = Hive.box('settings').get('favorites', defaultValue: <dynamic>[])
        as List;
    _favorites = raw
        .whereType<String>()
        .map((s) {
          try {
            return FavoritePlace.fromMapSafe(jsonDecode(s) as Map);
          } catch (_) {
            return null;
          }
        })
        .whereType<FavoritePlace>()
        .take(FavoritePlace.maxStoredItems)
        .toList();
  }

  List<FavoritePlace> _matchingFavorites(String query) {
    if (query.isEmpty) return _favorites;
    final q = query.toLowerCase();
    return _favorites
        .where((f) =>
            f.label.toLowerCase().contains(q) ||
            f.address.toLowerCase().contains(q))
        .toList();
  }

  /// Same storage shape MapScreen._loadHistory reads.
  void _loadHistory() {
    final raw = Hive.box('settings')
        .get('searchHistory', defaultValue: <dynamic>[]) as List<dynamic>;
    _history = raw
        .whereType<String>()
        .map((s) {
          try {
            return SearchHistoryItem.fromJsonSafe(
                jsonDecode(s) as Map<String, dynamic>);
          } catch (_) {
            return null;
          }
        })
        .whereType<SearchHistoryItem>()
        .take(100)
        .toList();
  }

  /// Records a confirmed destination — same MapScreen._saveToHistory,
  /// deduping by position (a re-searched place moves to the top rather than
  /// appearing twice) and capping at 5 recent entries.
  void _saveToHistory(String label, LatLng pos) {
    final item = SearchHistoryItem(label, pos);
    final updated = [
      item,
      ..._history.where((h) =>
          (h.position.latitude - pos.latitude).abs() > 0.0001 ||
          (h.position.longitude - pos.longitude).abs() > 0.0001),
    ];
    if (updated.length > 5) updated.removeRange(5, updated.length);
    setState(() => _history = updated);
    Hive.box('settings').put(
        'searchHistory', updated.map((h) => jsonEncode(h.toJson())).toList());
  }

  void _clearHistory() {
    setState(() => _history = []);
    Hive.box('settings').delete('searchHistory');
  }

  /// On startup, pull the encrypted favourites snapshot from Nostr and merge
  /// it in — same MapScreen._autoRestoreFavorites, ported directly.
  /// Best-effort and silent: no network keys, nothing synced, or a
  /// passphrase-locked snapshot all just skip. Never removes a local
  /// favourite, only adds/updates.
  Future<void> _autoRestoreFavorites() async {
    try {
      final pub = await _secStorage.read(key: 'nostr_pub_hex');
      if (pub == null) return;
      final priv = await _secStorage.read(key: 'nostr_priv_hex');
      final pass = await _secStorage.read(key: 'favorites_sync_passphrase') ??
          Hive.box('settings').get('fav_sync_pass') as String?;
      final result = await _favSyncSvc.pull(
          pubKeyHex: pub, privKeyHex: priv, passphrase: pass);
      final fetched = result.favorites;
      if (fetched == null || fetched.isEmpty || !mounted) return;
      var changed = false;
      for (final f in fetched) {
        final i = _favorites.indexWhere((e) => e.label == f.label);
        if (i >= 0) {
          _favorites[i] = f;
        } else {
          _favorites.add(f);
        }
        changed = true;
      }
      if (_favorites.length > FavoritePlace.maxStoredItems) {
        _favorites.removeRange(FavoritePlace.maxStoredItems, _favorites.length);
      }
      if (!changed) return;
      Hive.box('settings').put(
          'favorites', _favorites.map((f) => jsonEncode(f.toMap())).toList());
      if (mounted) setState(() {});
    } catch (_) {
      // Auto-restore is best-effort; manual pull (Settings) remains available.
    }
  }

  /// Screen-wake and minimum-brightness policy — same MapScreen._applyScreenPolicy.
  void _applyScreenPolicy() {
    final box = Hive.box('settings');
    final navWantsAwake =
        _isNavigating && (box.get('keepScreenOn', defaultValue: true) as bool);
    final alwaysAwake =
        box.get('keepScreenOnAlways', defaultValue: false) as bool;
    if (navWantsAwake || alwaysAwake) {
      WakelockPlus.enable();
    } else {
      WakelockPlus.disable();
    }
    final floor =
        (box.get('minBrightness', defaultValue: 0.0) as num).toDouble();
    if (floor > 0) {
      unawaited(ScreenBrightness().setApplicationScreenBrightness(floor));
    } else {
      unawaited(ScreenBrightness().resetApplicationScreenBrightness());
    }
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

  void _saveParkingPosition(LatLng pos) {
    Hive.box('settings').put(
        'parking_position',
        jsonEncode({
          'lat': pos.latitude,
          'lon': pos.longitude,
          'ts': DateTime.now().millisecondsSinceEpoch,
        }));
    setState(() => _parkingPosition = pos);
    _showSnack(AppLocalizations.of(context).parkingSavedSnack);
  }

  void _clearParkingPosition() {
    Hive.box('settings').delete('parking_position');
    setState(() => _parkingPosition = null);
    _showSnack(AppLocalizations.of(context).parkingRemovedSnack);
  }

  /// Same content as MapScreen._showParkingSheet, ported directly — no
  /// flutter_map dependency in the original either, just AppLocalizations
  /// strings and the two methods above. "Navigate here" routes through
  /// _calculateRouteTo instead of MapScreen's _requestAlternatives, since
  /// there is no alternatives flow here.
  void _showParkingSheet() {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context);
    final navBar = MediaQuery.of(context).viewPadding.bottom;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        margin: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: c.surface2,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: c.border, width: 0.5),
        ),
        padding: EdgeInsets.fromLTRB(20, 16, 20, 24 + navBar),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Center(
              child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                      color: c.border,
                      borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 14),
          Row(children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                  color: Colors.blue.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(12)),
              child: Icon(Icons.local_parking_rounded,
                  color: Colors.blue.shade400, size: 22),
            ),
            const SizedBox(width: 12),
            Expanded(
                child: Text(
                    _parkingPosition != null
                        ? l.parkingMarkerTitle
                        : l.parkingSaveHere,
                    style: TextStyle(
                        color: c.textPrimary,
                        fontSize: 15,
                        fontWeight: FontWeight.w600))),
          ]),
          const SizedBox(height: 16),
          if (_parkingPosition != null) ...[
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  unawaited(_calculateRouteTo(_parkingPosition!));
                },
                style: FilledButton.styleFrom(
                  backgroundColor: c.accent,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon:
                    const Icon(Icons.directions, color: Colors.white, size: 18),
                label: Text(l.parkingNavigateHere,
                    style: const TextStyle(color: Colors.white)),
              ),
            ),
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  _clearParkingPosition();
                },
                style: OutlinedButton.styleFrom(
                  side: BorderSide(color: Colors.red.withValues(alpha: 0.6)),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon: const Icon(Icons.delete_outline_rounded,
                    color: Colors.red, size: 18),
                label: Text(l.parkingRemove,
                    style: const TextStyle(color: Colors.red)),
              ),
            ),
          ] else
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: _lastFix == null
                    ? null
                    : () {
                        Navigator.pop(context);
                        _saveParkingPosition(_lastFix!.position);
                      },
                style: FilledButton.styleFrom(
                  backgroundColor: Colors.blue.shade400,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon: const Icon(Icons.local_parking_rounded,
                    color: Colors.white, size: 18),
                label: Text(l.parkingSaveHere,
                    style: const TextStyle(color: Colors.white)),
              ),
            ),
        ]),
      ),
    );
  }

  /// Same dual-signing path as MapScreen._showReportSheet (Amber/NIP-55 vs
  /// a locally stored nsec), ported directly — none of it touches
  /// flutter_map. Simplified in one way: no _roadEvents list to append the
  /// published event to, since this screen doesn't subscribe to or draw
  /// anyone's reports yet, its own included.
  Future<void> _showReportSheet() async {
    final privKey = await _secStorage.read(key: 'nostr_priv_hex');
    final pubKey = await _secStorage.read(key: 'nostr_pub_hex');
    final flavor = await _secStorage.read(key: 'nostr_flavor');
    if (!mounted) return;
    if (pubKey == null) {
      _showSnack(AppLocalizations.of(context).loginToReport);
      return;
    }
    final settings = Hive.box('settings');
    if (settings.get('road_report_privacy_ack', defaultValue: false) != true) {
      final italian = Localizations.localeOf(context).languageCode == 'it';
      final accepted = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(italian ? 'Report pubblico' : 'Public report'),
          content: Text(italian
              ? 'Il report pubblicherà sui relay Nostr posizione esatta, '
                  'orario, contenuto e chiave pubblica. È pseudonimo, non '
                  'anonimo, può essere collegato agli altri tuoi report e la '
                  'cancellazione dai relay non può essere garantita.'
              : 'This report publishes its exact position, time, content and '
                  'your public key to Nostr relays. It is pseudonymous, not '
                  'anonymous, can be linked to your other reports, and relay '
                  'deletion cannot be guaranteed.'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: Text(AppLocalizations.of(ctx).cancel)),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: Text(italian ? 'Ho capito' : 'I understand')),
          ],
        ),
      );
      if (accepted != true || !mounted) return;
      await settings.put('road_report_privacy_ack', true);
      if (!mounted) return;
    }
    final c = RoadstrColors.of(context);
    final pos = _lastFix == null
        ? null
        : LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
    if (pos == null) return;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => ReportSheet(
        colors: c,
        position: pos,
        onSubmit: (category, comment, speedLimit) async {
          final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
          final expires = now + category.ttlSeconds;
          if (flavor == 'amber') {
            final unsigned = NostrRelayService.buildKind1315Map(
              position: pos,
              category: category,
              comment: comment,
              pubKeyHex: pubKey,
              now: now,
              expires: expires,
              speedLimit: speedLimit,
            );
            final result = await Amberflutter().signEvent(
              currentUser: Nip19().npubEncode(pubKey),
              eventJson: jsonEncode(unsigned),
            );
            final signed =
                jsonDecode(result['event'] as String) as Map<String, dynamic>;
            await _nostr.publishRawRoadEvent(
              eventJson: signed,
              category: category,
              position: pos,
              comment: comment,
              now: now,
              expires: expires,
              expectedPubKeyHex: pubKey,
            );
          } else {
            await _nostr.publishRoadEvent(
              position: pos,
              category: category,
              comment: comment,
              privKeyHex: privKey!,
              pubKeyHex: pubKey,
              speedLimit: speedLimit,
            );
          }
        },
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _screenPolicyListenable.removeListener(_applyScreenPolicy);
    unawaited(ScreenBrightness().resetApplicationScreenBrightness());
    _magnetSub?.cancel();
    _accelSub?.cancel();
    _gpsSub?.cancel();
    _followTicker?.cancel();
    _searchDebounce?.cancel();
    _arrivalBannerTimer?.cancel();
    _gpsLossTimer?.cancel();
    _activitySub?.cancel();
    _searchController.dispose();
    unawaited(_gps.dispose());
    unawaited(_tts.dispose());
    _nostr.dispose();
    super.dispose();
  }

  Future<void> _searchNearby(NearbyCategory category) async {
    if (_lastFix == null) return;
    final pos =
        LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _nearbyCategory = category;
      _searchResults = [];
      _searching = true;
    });
    final results = await _poiSvc.nearby(category, pos,
        unnamedLabel:
            nearbyCategoryLabel(category, AppLocalizations.of(context)));
    if (!mounted) return;
    setState(() {
      _searchResults = results;
      _searching = false;
    });
  }

  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    // Typing takes over from a tapped category — same as MapScreen.
    if (_nearbyCategory != null) setState(() => _nearbyCategory = null);
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
    await _onDestinationPicked(result.position, label: result.shortName);
  }

  /// Pressing Enter/search on the keyboard instead of tapping a row — same
  /// MapScreen._onSearchSubmit: a matching favourite wins first, then the
  /// already-fetched top result, then (nothing cached yet) a fresh search
  /// for its top result.
  Future<void> _onSearchSubmit(String query) async {
    query = query.trim();
    if (query.isEmpty) return;
    _searchDebounce?.cancel();
    FocusManager.instance.primaryFocus?.unfocus();
    final favMatch = _matchingFavorites(query);
    if (favMatch.isNotEmpty) {
      _searchController.clear();
      setState(() {
        _showSearch = false;
        _searchResults = [];
      });
      await _onDestinationPicked(favMatch.first.position, label: favMatch.first.label);
      return;
    }
    NominatimResult result;
    if (_searchResults.isNotEmpty) {
      result = _searchResults.first;
    } else {
      setState(() => _searching = true);
      final near = _lastFix == null
          ? null
          : LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
      final results = await _placeSearch.search(query, near: near);
      if (!mounted) return;
      setState(() => _searching = false);
      if (results.isEmpty) return;
      result = results.first;
    }
    await _onSelectResult(result);
  }

  /// Routes a picked position to either a fresh destination search or a
  /// mid-journey stop, depending on which search flow is open. Shared by
  /// search-result selection, favourite selection and nearby-category
  /// results, so all three ways of picking a place go through one place.
  /// [label], when given, is recorded to search history — only for an
  /// actual destination, same as MapScreen: a mid-journey stop isn't one.
  Future<void> _onDestinationPicked(LatLng pos, {String? label}) async {
    if (_pickingWaypoint) {
      await _addWaypoint(pos);
      return;
    }
    if (label != null && label.isNotEmpty) _saveToHistory(label, pos);
    await _calculateRouteTo(pos);
  }

  /// Appends [pos] to the active journey as an extra stop and reroutes
  /// through it — same idea as MapScreen's waypoint insertion, simplified to
  /// append-only (no reorder/remove UI). Reuses [_rerouteAndNavigate]'s
  /// cumDist/step-index refresh: adding a stop changes the step list exactly
  /// like an automatic reroute does, so there is nothing extra to redo here.
  Future<void> _addWaypoint(LatLng pos) async {
    setState(() => _pickingWaypoint = false);
    final dest = _destination;
    final origin = _lastFix == null
        ? null
        : LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
    if (dest == null || origin == null) return;
    if (_activeVia.length >= RoutingService.maxWaypoints) {
      _showSnack(AppLocalizations.of(context).plannerStopsFull);
      return;
    }
    _activeVia = [..._activeVia, pos];
    await _rerouteAndNavigate(origin, dest);
  }

  /// Opens the search panel mid-journey to pick a stop — the FAB on the
  /// right column, visible only while navigating.
  void _openWaypointSearch() {
    if (_activeVia.length >= RoutingService.maxWaypoints) {
      _showSnack(AppLocalizations.of(context).plannerStopsFull);
      return;
    }
    setState(() {
      _pickingWaypoint = true;
      _showSearch = true;
    });
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
      // Routing from nowhere would be useless, but throwing the destination
      // away makes the user find it again — the part that was actually
      // wrong. Hold it and run the moment the first real fix lands (see the
      // replay at the top of _onGps), same as MapScreen's
      // _awaitingFixDestination.
      _awaitingFixDestination = dest;
      _showSnack(AppLocalizations.of(context).acquiringGps);
      return;
    }
    _awaitingFixDestination = null;
    setState(() => _calculatingRoute = true);
    _destination = dest;
    // A fresh destination starts a new journey — any stop added to a
    // previous one no longer applies.
    _activeVia = const [];
    ({
      RouteResult route,
      List<({List<LatLng> points, bool restricted})> runs
    })? fetched;
    try {
      fetched = await _fetchRoute(origin, dest);
    } catch (_) {
      if (!mounted) return;
      setState(() => _calculatingRoute = false);
      _showSnack(AppLocalizations.of(context).noRouteFound);
      return;
    }
    if (!mounted) return;
    if (fetched == null) {
      setState(() => _calculatingRoute = false);
      _showSnack(AppLocalizations.of(context).noRouteFound);
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
        for (final p in fetched.route.polyline)
          Geographic(lon: p.longitude, lat: p.latitude),
      ]),
      padding: const EdgeInsets.all(48),
      pitch: 0,
    ));
  }

  /// Recalculates the current route for a different transport profile —
  /// same idea as MapScreen._recalculateForMode, simplified: there is no
  /// alternatives list here to refresh, just the one route this screen ever
  /// shows, so switching modes is just a fresh [_calculateRouteTo] call.
  Future<void> _onModeChanged(String mode) async {
    if (_transportMode == mode) return;
    setState(() => _transportMode = mode);
    final dest = _destination;
    if (dest != null) await _calculateRouteTo(dest);
  }

  void _showSnack(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  /// Same provider-resolution logic as MapScreen._resolveProvider, ported
  /// directly: reads the configured provider/API key/self-hosted GraphHopper
  /// server, migrating a legacy Hive-stored key to SecureStorage the same
  /// way. Without this the screen always fell through to the public OSRM
  /// demo server regardless of what the driver configured in Settings.
  ///
  /// The SecureStorage read (and its one-time legacy-key migration) only
  /// runs when the configured provider actually needs an API key — on the
  /// default 'osrm' setting, resolution is synchronous. Confirmed on a real
  /// device that OSRM (the default, untouched) was still the configured
  /// provider when routing felt slow, so that SecureStorage round-trip
  /// wasn't the cause — but it is pure overhead on every single-shot and
  /// automatic-reroute request for the common case, so it's worth avoiding
  /// regardless of whether it explains the report.
  Future<({RoutingProvider provider, String? apiKey, String? ghServer})>
      _resolveProvider() async {
    final box = Hive.box('settings');
    final providerKey =
        box.get('routingProvider', defaultValue: 'osrm') as String;
    if (providerKey == 'osrm') {
      return (provider: RoutingProvider.osrm, apiKey: null, ghServer: null);
    }
    var rawKey = await _secStorage.read(key: 'routing_api_key') ?? '';
    if (rawKey.isEmpty) {
      final legacy =
          (box.get('graphhopperApiKey', defaultValue: '') as String).trim();
      if (legacy.isNotEmpty) {
        await _secStorage.write(key: 'routing_api_key', value: legacy);
        await box.delete('graphhopperApiKey');
        rawKey = legacy;
      }
    }
    final apiKey = rawKey.trim().isEmpty ? null : rawKey.trim();
    final rawGhServer =
        (box.get('graphhopperServer', defaultValue: '') as String).trim();
    final ghServer = rawGhServer.isEmpty ? null : rawGhServer;

    final l10n = mounted ? AppLocalizations.of(context) : null;
    RoutingProvider provider;
    switch (providerKey) {
      case 'graphhopper':
        if (ghServer != null) {
          provider = RoutingProvider.graphHopper;
        } else {
          provider = RoutingProvider.osrm;
          if (l10n != null) _showSnack(l10n.graphhopperServerNotConfigured);
        }
      case 'graphhopper_public':
        if (apiKey != null) {
          provider = RoutingProvider.graphHopper;
        } else {
          provider = RoutingProvider.osrm;
          if (l10n != null) _showSnack(l10n.graphhopperApiKeyNotConfigured);
        }
      case 'openroute':
        if (apiKey != null) {
          provider = RoutingProvider.openRoute;
        } else {
          provider = RoutingProvider.osrm;
          if (l10n != null) _showSnack(l10n.openrouteApiKeyNotConfigured);
        }
      default:
        provider = RoutingProvider.osrm;
    }
    return (provider: provider, apiKey: apiKey, ghServer: ghServer);
  }

  /// Fetches a route from [origin] to [dest] and classifies it against ZTL —
  /// the work shared between a fresh destination pick and a reroute, so
  /// there is one place that does it, not two that can drift apart.
  Future<
      ({
        RouteResult route,
        List<({List<LatLng> points, bool restricted})> runs
      })?> _fetchRoute(LatLng origin, LatLng dest, {List<LatLng>? via}) async {
    final (:provider, :apiKey, :ghServer) = await _resolveProvider();
    if (!mounted) return null;
    final routes = await RoutingService.getRoutes(origin, dest,
        provider: provider,
        apiKey: apiKey,
        graphhopperServer: ghServer,
        lang: 'it',
        vehicle: _transportMode,
        via: via ?? _activeVia);
    if (!mounted || routes.isEmpty) return null;
    final route = routes.first;
    // Not awaited: this used to be the whole reason route calculation felt
    // slow. ZtlService's cache is kept warm in the background by _onGps
    // (same as MapScreen, which never awaits it in the route path either) —
    // classifyPoints reads whatever is cached right now rather than blocking
    // the route on a live Overpass round-trip, which carries a 25 s timeout
    // and was the actual 15 s the driver was seeing on a cold cache.
    final restricted = _ztl.classifyPoints(route.polyline);
    return (route: route, runs: _splitByZtl(route.polyline, restricted));
  }

  /// Cancels a route preview/plan before navigation ever starts. Same reset
  /// as actually stopping mid-trip — there's nothing left to distinguish
  /// once _stopNavigation clears the route itself, so this just is that.
  void _clearRoute() => _stopNavigation();

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
    final nearest = _nearestActiveRouteSegment(pos);
    if (nearest == null || nearest.distM < 30) return;
    if (nearest.distM > _offRouteThresholdM) {
      unawaited(_rerouteAndNavigate(pos, dest));
      return;
    }
    // Direction check: on a bidirectional road the fix can sit right on the
    // route polyline while the driver is actually facing the wrong way (a
    // U-turn, or joining from the far carriageway) — distance alone can't
    // catch that. Only at meaningful speed and with a strict angle, same as
    // MapScreen, to avoid false triggers on curves or a bend just ahead.
    final steps = route.steps;
    if (data.speedKmh > 20 && _currentStepIdx + 1 < steps.length) {
      final nextWaypoint = steps[_currentStepIdx + 1].location;
      final bearingToNext = Geo.bearingBetween(pos, nextWaypoint);
      var diff = (_bearing - bearingToNext).abs();
      if (diff > 180) diff = 360 - diff;
      if (diff > 135) unawaited(_rerouteAndNavigate(pos, dest));
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
        _cumDist[
            RouteProgress.nearestIndex(fetched.route.polyline, step.location)],
    ];
    // A rerouted step list is a different array — yesterday's announced
    // indices mean nothing against it, and would silently block every
    // announcement on the new route until they happened to be overwritten.
    _ttsAnnouncedFarIdx = -1;
    _ttsAnnouncedNearIdx = -1;
    // A rerouted polyline invalidates the windowed segment search's cached
    // index just as much as the step-index arrays above.
    _nearestRouteSegmentIdx = 0;
    _routeProgressM = 0;
    setState(() {
      _route = fetched.route;
      _routeRuns = fetched.runs;
      _currentStepIdx = 0;
      _isRerouting = false;
      _remainingDistM = fetched.route.totalDistanceM;
      _remainingSecs = fetched.route.totalDurationS;
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
    _headingFilter.reset();
    _nearestRouteSegmentIdx = 0;
    _routeProgressM = 0;
    _minDistToDestM = double.infinity;
    // Fetch the destination's building footprint (best-effort, one Overpass
    // round-trip) — used by _checkArrival below.
    final dest = _destination;
    _destBuilding = null;
    if (dest != null) {
      unawaited(_poiSvc.buildingPolygonAt(dest).then((poly) {
        if (!mounted || _destination != dest) return;
        _destBuilding = poly;
      }));
    }
    setState(() {
      _isNavigating = true;
      _followUser = true;
      _currentStepIdx = 0;
      _arrived = false;
      _remainingDistM = route.totalDistanceM;
      _remainingSecs = route.totalDurationS;
    });
    // The camera was left wherever the route-preview fitBounds put it
    // (zoomed out, top-down, centred on the whole route) — nothing snapped
    // it back onto the driver when the trip actually started, so navigation
    // began on a wide overview instead of the driving view.
    _snapCameraToFix(navShift: _headingMode, pitch: 60.0);
    _applyScreenPolicy();
    if (!_voiceMuted) unawaited(_tts.announceStart());

    // GPS-loss watchdog: a tunnel or underground car park stops the fix
    // stream with no error, so polling is the only way to notice. After
    // _gpsLossThresholdMs of silence, show the banner and speak a one-shot
    // warning; a fresh fix clears both in _onGps.
    _gpsLossTimer?.cancel();
    _lastFixEpochMs = DateTime.now().millisecondsSinceEpoch;
    _gpsLossTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (!mounted || !_isNavigating) return;
      final silentMs = DateTime.now().millisecondsSinceEpoch - _lastFixEpochMs;
      if (silentMs > _gpsLossThresholdMs && !_gpsSignalLost) {
        setState(() => _gpsSignalLost = true);
        if (!_voiceMuted) {
          unawaited(_tts.speak(AppLocalizations.of(context).gpsSignalLost));
        }
      }
    });
  }

  void _stopNavigation({bool stopVoice = true}) {
    if (stopVoice) unawaited(_tts.stop());
    _headingFilter.reset();
    _arrivalBannerTimer?.cancel();
    _gpsLossTimer?.cancel();
    _gpsLossTimer = null;
    unawaited(_navNotif.cancel());
    setState(() {
      _route = null;
      _destination = null;
      _routeRuns = [];
      _isNavigating = false;
      _arrived = false;
      _gpsSignalLost = false;
      _activeVia = const [];
    });
    _applyScreenPolicy();
  }

  /// Same confirmation dialog MapScreen shows before actually stopping —
  /// ported directly, same l10n strings. Reachable from the NavPanel's stop
  /// button and from the system back gesture while navigating (PopScope,
  /// in build()).
  void _showExitNavigationDialog() {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          Icon(Icons.navigation_rounded, color: c.accent, size: 20),
          const SizedBox(width: 8),
          Text(l.navExitTitle,
              style: TextStyle(
                  color: c.textPrimary,
                  fontSize: 16,
                  fontWeight: FontWeight.bold)),
        ]),
        content: Text(l.navExitBody,
            style: TextStyle(color: c.textSecondary, fontSize: 13)),
        actions: [
          OutlinedButton(
            onPressed: () => Navigator.pop(context),
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: c.accent),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: Text(l.navContinue, style: TextStyle(color: c.accent)),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              _stopNavigation();
            },
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: Text(l.navExit, style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  /// Advances [_currentStepIdx] and recomputes [_distToNextStepM] from
  /// [data]. Called from [_onGps] before its own setState, not after — the
  /// two share one rebuild instead of triggering back to back.
  /// The route polyline split at the live progress cursor — same
  /// MapScreen._routeProgressPolyline, adapted to this screen's own
  /// _cumDist/_routeProgressM (no separate "render polyline" concept here,
  /// just the route's own polyline).
  List<LatLng> _routeProgressPolyline({required bool completed}) {
    final route = _route;
    if (route == null || _cumDist.length != route.polyline.length) {
      return completed ? const [] : (route?.polyline ?? const []);
    }
    final render = route.polyline;
    var i = 0;
    while (i + 1 < render.length && _cumDist[i + 1] < _routeProgressM) {
      i++;
    }
    final a = render[i];
    final b = i + 1 < render.length ? render[i + 1] : render[i];
    final span = i + 1 < render.length ? _cumDist[i + 1] - _cumDist[i] : 0.0;
    final t = span <= 0
        ? 0.0
        : ((_routeProgressM - _cumDist[i]) / span).clamp(0.0, 1.0);
    final cursor = LatLng(a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t);
    if (completed) {
      if (_routeProgressM <= 0) return const [];
      return [...render.take(i + 1), cursor];
    }
    return [cursor, ...render.skip(i + 1)];
  }

  /// ZTL-coloured runs for the road still ahead only — same
  /// MapScreen._remainingRouteRuns. Classification is a cheap local
  /// proximity check against the already-cached restricted-ways list (no
  /// network), so recomputing it every GPS tick is the same thing
  /// MapScreen's own build() already does.
  List<({List<LatLng> points, bool restricted})> _remainingRouteRuns() {
    final pts = _routeProgressPolyline(completed: false);
    if (pts.length < 2) return const [];
    return _splitByZtl(pts, _ztl.classifyPoints(pts));
  }

  void _updateNavigationProgress(GpsData data) {
    final route = _route;
    if (route == null || _cumDist.isEmpty || _arrived) return;
    final pos = LatLng(data.position.latitude, data.position.longitude);
    final idx = RouteProgress.nearestIndex(route.polyline, pos);
    final routeProgressM = _cumDist[idx];
    _routeProgressM = routeProgressM;
    final totalDist = route.totalDistanceM;
    final rem = (totalDist - routeProgressM).clamp(0.0, totalDist);
    _remainingDistM = rem;
    _remainingSecs = totalDist > 0 ? route.totalDurationS * rem / totalDist : 0;
    // Route-embedded limit (OSRM/GH annotation) first, Overpass cache as
    // fallback — same preference MapScreen._updateRemainingStats uses.
    final routeLimit = route.speedLimitAt(routeProgressM);
    _currentSpeedLimit = routeLimit ?? _speedLimitSvc.cachedLimit;
    if (routeLimit == null) {
      unawaited(_speedLimitSvc.updateIfNeeded(pos));
    }
    while (_currentStepIdx + 1 < route.steps.length &&
        _stepCumDist[_currentStepIdx + 1] <=
            routeProgressM + _advanceToleranceM) {
      _currentStepIdx++;
    }
    final isLast = _currentStepIdx >= route.steps.length - 1;
    _distToNextStepM = isLast
        ? 0
        : (_stepCumDist[_currentStepIdx + 1] - routeProgressM)
            .clamp(0, double.infinity);
    _checkArrival(pos, isLast);
    if (_arrived) return;
    if (!_voiceMuted && !isLast) {
      _announceUpcoming(data.speedKmh, route);
    }
    _updateNavNotification(route);
  }

  /// Persistent Android notification with the next manoeuvre + distance —
  /// same MapScreen._updateNavNotification, using the same "next upcoming
  /// manoeuvre" convention as the in-app NavInstruction card.
  void _updateNavNotification(RouteResult route) {
    if (route.steps.isEmpty || !mounted) return;
    final nextIdx =
        _currentStepIdx + 1 < route.steps.length ? _currentStepIdx + 1 : _currentStepIdx;
    final step = route.steps[nextIdx];
    final distM = _distToNextStepM > 0 ? _distToNextStepM : step.distanceM;
    final l = AppLocalizations.of(context);
    final distLabel = Units.fmtDist(distM, nowLabel: l.now);
    final instruction = step.direction == 'arrive'
        ? switch (step.modifier) {
            'left' => l.arrivalAheadLeft,
            'right' => l.arrivalAheadRight,
            _ => l.arrivalAhead,
          }
        : step.instruction;
    _navNotif.show(instruction, distLabel);
  }

  /// Whether the driver has arrived — same multi-signal logic as
  /// MapScreen._checkArrival, ported directly: never on route-progress alone
  /// (only true GPS distance to the destination, which is what a router's
  /// arrive-point sitting hundreds of metres short/past the actual door
  /// can't fool), widened by GPS accuracy on the last step, then a
  /// building-footprint check (OSM traces building outlines — stepping into
  /// the destination's footprint IS arriving regardless of how far the
  /// road-based arrive-point is), then a closest-approach fallback for a fix
  /// that's simply never accurate enough to dip under any fixed radius.
  void _checkArrival(LatLng pos, bool onLastStep) {
    final dest = _destination;
    if (dest == null) return;
    final d = Geo.distanceM(pos, dest);
    if (d < _minDistToDestM) _minDistToDestM = d;
    final arrivalRadius = _transportMode == 'walking' ? 15.0 : 40.0;
    final effectiveRadius =
        onLastStep ? (_lastGpsAccuracy * 1.5).clamp(10.0, 40.0) : arrivalRadius;
    if (d < effectiveRadius) {
      _onArrival();
      return;
    }
    final building = _destBuilding;
    if (onLastStep &&
        building != null &&
        (Geo.pointInPolygon(pos, building) ||
            Geo.distanceToPolylineM(pos, building) <= 10)) {
      _onArrival();
      return;
    }
    const closeEnoughM = 60.0;
    const movingAwayMarginM = 20.0;
    if (onLastStep &&
        _minDistToDestM <= closeEnoughM &&
        d > _minDistToDestM + movingAwayMarginM) {
      _onArrival();
    }
  }

  void _onArrival() {
    if (_arrived) return;
    _arrived = true;
    if (!_voiceMuted) unawaited(_tts.announceArrival());
    // Let the arrival announcement finish — stopping the TTS player in the
    // same stack frame used to cut the clip and could leave the shared
    // audio session interrupted, same reason MapScreen keeps stopVoice:
    // false here. Navigation fully ends now (route/destination cleared,
    // search and the bottom bar come back) — same as MapScreen: arrival
    // isn't a special in-between state, it's the trip actually finishing,
    // with a transient banner layered on top of the now-idle map.
    _stopNavigation(stopVoice: false);
    _arrivalBannerTimer?.cancel();
    setState(() => _showArrivalBanner = true);
    _arrivalBannerTimer = Timer(const Duration(seconds: 6), () {
      if (mounted) setState(() => _showArrivalBanner = false);
    });
  }

  /// Two-stage far/near announcement for the maneuver after the current
  /// step, same distances NavigationGuidance already computes for
  /// MapScreen — speed-scaled, not a fixed number, so the near cue actually
  /// gives enough warning at motorway speed instead of flattening at 100 km/h.
  void _announceUpcoming(double speedKmh, RouteResult route) {
    final nextIdx = _currentStepIdx + 1;
    final t = NavigationGuidance.thresholds(speedKmh, _transportMode);
    if (_distToNextStepM < t.far + 20 &&
        _distToNextStepM >= t.near + 20 &&
        _ttsAnnouncedFarIdx != nextIdx) {
      _ttsAnnouncedFarIdx = nextIdx;
      unawaited(_tts.announceManeuver(
          route.steps[nextIdx].instruction,
          NavigationGuidance.spokenDistanceM(_distToNextStepM,
              imminentBelowM: t.near)));
    } else if (_distToNextStepM < t.near + 30 &&
        _ttsAnnouncedNearIdx != nextIdx) {
      _ttsAnnouncedNearIdx = nextIdx;
      unawaited(_tts.announceManeuver(route.steps[nextIdx].instruction, 0));
    }
  }

  /// Finds the closest active-route segment in a moving window around the
  /// previous match, same as MapScreen's own — a full scan of a long route on
  /// every GPS/heading update can monopolise the UI thread; normal motion
  /// only advances a handful of polyline points. A wide mismatch (>100 m)
  /// falls back to a full scan, which recovers correctness after a GPS jump
  /// or on a self-crossing route.
  ({double distM, int segmentIdx})? _nearestActiveRouteSegment(LatLng pos) {
    final poly = _route?.polyline;
    if (poly == null || poly.length < 2) return null;
    final lastSegment = poly.length - 2;
    final start = math.max(0, _nearestRouteSegmentIdx - 100);
    final end = math.min(lastSegment, _nearestRouteSegmentIdx + 500);
    double bestDist = double.infinity;
    var bestIdx = start;

    void scan(int from, int through) {
      for (var i = from; i <= through; i++) {
        final d = Geo.distanceToSegmentM(pos, poly[i], poly[i + 1]);
        if (d < bestDist) {
          bestDist = d;
          bestIdx = i;
        }
      }
    }

    scan(start, end);
    if (bestDist > 100 && (start > 0 || end < lastSegment)) {
      bestDist = double.infinity;
      scan(0, lastSegment);
    }
    _nearestRouteSegmentIdx = bestIdx;
    return (distM: bestDist, segmentIdx: bestIdx);
  }

  /// Among segments about as close as the nearest, the one whose position
  /// along the route is closest to where the driver actually is — same
  /// disambiguation MapScreen runs. At a roundabout the exit arm passes
  /// within a few metres of the entry arm while pointing the opposite way;
  /// "nearest in space" happily returns it, while progress along the route
  /// cannot make that mistake (it advances monotonically).
  int _segmentNearestInProgress(
      LatLng pos, ({double distM, int segmentIdx}) nearest) {
    final poly = _route?.polyline;
    if (poly == null || _cumDist.length != poly.length) {
      return nearest.segmentIdx;
    }
    final tolerance = math.max(nearest.distM * 1.6, 20.0);
    final from = math.max(0, nearest.segmentIdx - 120);
    final to = math.min(poly.length - 2, nearest.segmentIdx + 120);

    var bestIdx = nearest.segmentIdx;
    var bestProgressGap = double.infinity;
    for (var i = from; i <= to; i++) {
      if (Geo.distanceToSegmentM(pos, poly[i], poly[i + 1]) > tolerance) {
        continue;
      }
      final gap = (_cumDist[i] - _routeProgressM).abs();
      if (gap < bestProgressGap) {
        bestProgressGap = gap;
        bestIdx = i;
      }
    }
    return bestIdx;
  }

  /// The route's own local direction near [pos], for HeadingFilter's
  /// route-snap easing — same MapScreen._routeLocalBearingAt, using the two
  /// helpers above.
  ({double distM, double bearing})? _routeLocalBearingAt(LatLng pos) {
    final nearest = _nearestActiveRouteSegment(pos);
    if (nearest == null) return null;
    final poly = _route!.polyline;
    final idx = _segmentNearestInProgress(pos, nearest);
    return (
      distM: nearest.distM,
      bearing: Geo.bearingBetween(poly[idx], poly[idx + 1])
    );
  }

  /// In-zone / passing-a-restricted-street banner state — same MapScreen
  /// logic: route-ahead ZTL warnings were deliberately dropped there (too
  /// noisy, misleading when the route only brushes a boundary), so this is
  /// purely about where the driver actually is right now.
  void _updateZtlBannerState(LatLng pos) {
    final nowInZtl = _ztl.isInsideZtl(pos);
    // Advisory about a restricted street being driven past. Suppressed
    // while inside one: the red banner is already the accurate message,
    // and two warnings at once teaches the driver to read neither.
    final passing = nowInZtl ? null : _ztl.nearestRestrictedWay(pos);
    // Compared by identity, not name: restricted ways are frequently
    // unnamed, and comparing names would make leaving an unnamed one — null
    // before, null after — register as "no change", leaving the notice
    // stuck over the cursor.
    if (!identical(passing, _ztlPassingBy)) {
      setState(() {
        _ztlPassingBy = passing;
        if (passing == null) _ztlNoticeDismissed = false;
      });
    }
    if (nowInZtl != _inZtl) {
      _ztlNoticeDismissed = false;
      setState(() {
        _inZtl = nowInZtl;
        _ztlName = nowInZtl ? _ztl.ztlNameAt(pos) : null;
      });
    }
  }

  void _onGps(GpsData data) {
    final wasWaitingForFix = _lastFix == null;
    _lastFix = data;
    if (!mounted) return;
    final queued = _awaitingFixDestination;
    if (wasWaitingForFix && queued != null) {
      _awaitingFixDestination = null;
      // Deferred to the next frame so it routes from the position this fix
      // is about to commit, same as MapScreen.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _lastFix == null) return;
        unawaited(_calculateRouteTo(queued));
      });
    }
    _lastFixEpochMs = DateTime.now().millisecondsSinceEpoch;
    if (_gpsSignalLost) setState(() => _gpsSignalLost = false);
    if (_isNavigating) _updateNavigationProgress(data);
    _checkOffRoute(data);

    // ── Heading resolution ───────────────────────────────────────────────
    // Same filter MapScreen runs on every fix: raw course-over-ground
    // (data.heading) is noisy enough at low speed that using it directly as
    // the camera bearing is what made the cursor look "ballerino".
    final sampleSpeed =
        data.speedKmh.isFinite && data.speedKmh > 0 ? data.speedKmh : 0.0;
    final moving = _headingFilter.updateMotion(sampleSpeed);
    final sampleAccuracy =
        data.accuracy.isFinite && data.accuracy > 0 ? data.accuracy : 20.0;
    if (data.accuracy.isFinite && data.accuracy > 0) {
      _lastGpsAccuracy = data.accuracy;
    }
    final headingOrigin = _prevGpsPos;
    final effectiveHeading = _headingFilter.resolve(
      current: _bearing,
      from: headingOrigin,
      to: data.position,
      speedKmh: sampleSpeed,
      accuracyM: sampleAccuracy,
      providerHeading: data.heading,
      navigating: _isNavigating,
      routeLocalBearingAt: _route == null ? null : _routeLocalBearingAt,
    );
    if (!moving) {
      _prevGpsPos = null;
    } else if (headingOrigin == null ||
        HeadingFilter.hasReliableMovement(
            headingOrigin, data.position, sampleAccuracy)) {
      _prevGpsPos = data.position;
    }

    if (data.altitude.isFinite) {
      final prev = _altitudeM;
      _altitudeM =
          prev == null ? data.altitude : prev + (data.altitude - prev) * 0.15;
    }

    // ── Free-drive speed limit ───────────────────────────────────────────
    // During navigation the limit comes from route annotations, resolved
    // inside _updateNavigationProgress; outside it, query Overpass directly,
    // same as MapScreen's free-drive path.
    if (!_isNavigating) {
      if (sampleSpeed > 10) {
        unawaited(_speedLimitSvc.updateIfNeeded(data.position));
      }
      _currentSpeedLimit = _speedLimitSvc.cachedLimit;
    }

    setState(() {}); // refresh the status readout + navigation progress
    unawaited(_speedCameraSvc.updateIfNeeded(data.position).then((_) {
      if (mounted) setState(() {});
    }));
    // Same background warm-up MapScreen runs on every fix — by the time a
    // route is actually requested, ZtlService's cache already covers the
    // area (self-throttled: it only re-fetches every 2 km/on failure
    // back-off, see updateIfNeeded), so _fetchRoute's classifyPoints call
    // never has to wait on a live Overpass round-trip.
    unawaited(_ztl.updateIfNeeded(data.position));
    _updateZtlBannerState(data.position);
    if (!_followUser) return;
    final rotDeg = _headingMode
        ? ((_isNavigating || moving)
            ? effectiveHeading
            : (_camState?.rotDeg ?? 0))
        : 0.0;
    final zoom = _camState?.zoom ?? 17;
    // In heading-up navigation shift the camera ahead so the cursor sits
    // lower in frame (more road visible ahead, clear of the NavPanel/
    // speedometer) — same MapScreen._navCameraCenter formula, ported to
    // camera_follow.dart, using the real screen height rather than
    // MapScreen's own hardcoded 800px assumption.
    final (targetLat, targetLng) = (_isNavigating && _headingMode)
        ? CameraFollowEasing.navCameraCenter(
            data.position.latitude, data.position.longitude, rotDeg, zoom,
            screenHeightPx: MediaQuery.of(context).size.height)
        : (data.position.latitude, data.position.longitude);
    _targetState = CameraFollowState(
      lat: targetLat,
      lng: targetLng,
      zoom: zoom,
      rotDeg: rotDeg,
    );
    _startFollowTicker();
  }

  void _toggleHeadingMode() => setState(() => _headingMode = !_headingMode);

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
      final next =
          CameraFollowEasing.step(from: from, target: target, dtMs: dtMs);
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
    _snapCameraToFix(
        navShift: _isNavigating && _headingMode,
        pitch: _isNavigating ? 60.0 : 45.0);
  }

  /// Snaps the camera onto the current GPS fix immediately — a request to
  /// jump there now, not to rejoin the continuous ease mid-flight, same
  /// split MapScreen keeps between its ticker and _animateCamera for an
  /// explicit action. [navShift] applies the same forward offset the
  /// follow ticker uses in heading-up navigation, so recentring while
  /// driving doesn't undo it.
  void _snapCameraToFix({required bool navShift, required double pitch}) {
    final fix = _lastFix;
    final controller = _controller;
    if (fix == null || controller == null) return;
    // Without this, a follow-ticker frame landing mid-flight (either a
    // stale ticker still easing toward wherever the camera was before this
    // snap, or a fresh one started by the very next GPS tick) calls
    // moveCamera and cuts the animateCamera below off wherever it happened
    // to be — which is what made the camera land in an unpredictable
    // "random" spot instead of behind the cursor. Cancelling the ticker and
    // pointing _targetState at the same place _camState is about to become
    // means even a GPS tick that lands mid-animation only asks the ticker
    // to ease a negligible remaining distance, not fight the snap.
    _followTicker?.cancel();
    _followTicker = null;
    final rotDeg = _headingMode ? (fix.heading ?? _bearing) : 0.0;
    const zoom = 17.0;
    final screenHeightPx = MediaQuery.of(context).size.height;
    final (lat, lng) = navShift
        ? CameraFollowEasing.navCameraCenter(
            fix.position.latitude, fix.position.longitude, rotDeg, zoom,
            screenHeightPx: screenHeightPx)
        : (fix.position.latitude, fix.position.longitude);
    _camState =
        CameraFollowState(lat: lat, lng: lng, zoom: zoom, rotDeg: rotDeg);
    _targetState = _camState;
    unawaited(controller.animateCamera(
      center: Geographic(lon: lng, lat: lat),
      zoom: zoom,
      pitch: pitch,
      bearing: rotDeg,
    ));
  }

  /// Name of the town street being driven right now, or null — same
  /// MapScreen._currentStreetName. Comes straight from the route step
  /// already in hand (the step in progress carries the name of the road it
  /// leads onto), no reverse-geocoding call needed. Numbered roads
  /// deliberately return null: the code is already on every sign and in the
  /// manoeuvre panel.
  String? get _currentStreetName {
    final route = _route;
    if (!_isNavigating || route == null || route.steps.isEmpty) return null;
    final idx = _currentStepIdx.clamp(0, route.steps.length - 1);
    final step = route.steps[idx];
    return step.isUrbanStreet ? step.roadName : null;
  }

  @override
  Widget build(BuildContext context) {
    // Reads the theme actually applied to the app (MaterialApp.theme, driven
    // by ThemeProvider.effectiveThemeData in main.dart) instead of building
    // a fresh one from a hardcoded AppThemeId — the previous version always
    // rendered the plain Nostr light/dark pair regardless of which of the
    // 8 themes (Bitcoin accent, "modern" gradient variants, auto-dark) the
    // user actually has selected in Settings, since it never consulted
    // ThemeProvider.current at all, only .effective.isDark for raster
    // recolouring. context.watch keeps this screen rebuilding on live theme
    // changes (auto-dark sunset/sunrise, a Settings edit) same as MapScreen.
    context.watch<ThemeProvider>();
    final c = RoadstrColors.of(context);
    final isDark = c.isDark;
    // Same custom-server override MapScreen's tile layer reads — a
    // self-hosted or alternate raster source, same default as MapScreen's
    // own RoadstrColors.mapTile.
    final tileUrl = Hive.box('settings')
        .get('mapTileUrl', defaultValue: _roadstrTileUrl) as String;
    // setStyle only after onMapCreated hands us a controller — until then
    // the initial style picks the right one so there's no light-then-dark
    // flash on first frame. Also re-applied if the custom tile URL changes
    // mid-session (edited in Settings while this screen is open).
    if (_stylingDark != null &&
        (_stylingDark != isDark || _stylingTileUrl != tileUrl)) {
      _stylingDark = isDark;
      _stylingTileUrl = tileUrl;
      _controller?.setStyle(_style(dark: isDark, tileUrl: tileUrl));
    }
    _stylingDark ??= isDark;
    _stylingTileUrl ??= tileUrl;

    // Same walking/cycling override MapScreen's cursor uses — the ostrich
    // and bicycle sprites only appear while actually navigating on foot or
    // by bike, never leaking into the stored driving skin otherwise.
    final cursorStyle = CursorStyle.resolve(
      isNavigating: _isNavigating,
      transportMode: _transportMode,
      storedDrivingStyle: Hive.box('settings').get(CursorStyle.storageKey),
    );
    // Only the 'arrow' style gets the from-scratch painter: it's the one
    // asset confirmed to bake its own static shadow (arrow.svg's <ellipse>),
    // which is what the parallax shadow duplicated. Every other driving skin
    // (formula1/suv/racing/electric/city/classic500) is a plain top-view PNG
    // with no such baked shadow to duplicate, so it goes through the real
    // UserMarker/CursorWidget system unmodified — same as ostrich/bicycle
    // already did. Collapsing every skin to the generic arrow (an earlier
    // version of this file did) silently dropped the user's actual vehicle
    // choice and made the cursor read as smaller/thinner than the skin they
    // picked.
    final usesRealCursorAsset = cursorStyle != CursorStyle.arrow;

    // MapLibre's line-width paints in physical pixels; MapScreen's
    // flutter_map equivalent (and the _kRouteGlowW/_kRouteStrokeW constants
    // these numbers are copied from) paints in logical pixels. Left as-is,
    // the exact same numeric width renders devicePixelRatio× fatter here
    // than the light engine — on a typical ~2.5–3× phone that's a very
    // visibly thicker "laser". Dividing by the ratio corrects it back to
    // the same on-screen thickness.
    final dpr = MediaQuery.of(context).devicePixelRatio;
    int routeWidthPx(double logicalPx) =>
        (logicalPx / dpr).round().clamp(1, 999);
    // Once under way, only colour the road still ahead — the driven leg
    // goes flat grey, same distinction MapScreen's laser vs. completed-grey
    // split makes. Recomputed every rebuild (every GPS tick): classifyPoints
    // is a local proximity check against the already-cached restricted-ways
    // list, no network, same cost MapScreen's own _remainingRouteRuns pays
    // on every build.
    final showRouteProgress = _isNavigating && _routeProgressM > 0;
    final activeRouteRuns = showRouteProgress ? _remainingRouteRuns() : _routeRuns;
    final completedRoutePts =
        showRouteProgress ? _routeProgressPolyline(completed: true) : const <LatLng>[];

    return PopScope(
      // Same split MapScreen's own PopScope uses: search and navigation both
      // claim the system back gesture instead of letting it exit — search
      // closes (there's no on-screen back button any more to do it), and
      // navigation asks for confirmation instead of just stopping.
      canPop: !_showSearch && !_isNavigating,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_showSearch) {
          FocusManager.instance.primaryFocus?.unfocus();
          _searchController.clear();
          setState(() {
            _showSearch = false;
            _searchResults = [];
            _nearbyCategory = null;
            _pickingWaypoint = false;
          });
        } else if (_isNavigating) {
          _showExitNavigationDialog();
        }
      },
      child: Scaffold(
        body: Stack(children: [
          MapLibreMap(
            options: MapOptions(
              initStyle: _style(dark: isDark, tileUrl: tileUrl),
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
              } else if (event is MapEventMoveCamera &&
                  ((event.camera.pitch - _pitch).abs() > 0.5 ||
                      (event.camera.bearing - _bearing).abs() > 0.5)) {
                // >0.5° gate: both gestures fire this on every native frame,
                // and neither the shadow nor the compass needle needs finer
                // resolution than that to look continuous.
                setState(() {
                  _pitch = event.camera.pitch;
                  _bearing = event.camera.bearing;
                });
              }
            },
            // A wide, translucent glow pass under a narrower solid core per
            // run — an approximation of MapScreen's "laser" route rendering
            // (halo + glow + two coloured rails), collapsed to two passes
            // since PolylineLayer here colours a whole layer, not a single
            // Polyline the way flutter_map's does.
            layers: [
              if (completedRoutePts.length >= 2)
                PolylineLayer(
                  polylines: [
                    Feature(
                        geometry: LineString.from(completedRoutePts.map((p) =>
                            Geographic(lon: p.longitude, lat: p.latitude))))
                  ],
                  color: Colors.grey.shade500,
                  width: routeWidthPx(9),
                ),
              for (final run in activeRouteRuns) ...[
                PolylineLayer(
                  polylines: [
                    Feature(
                        geometry: LineString.from(run.points.map((p) =>
                            Geographic(lon: p.longitude, lat: p.latitude))))
                  ],
                  color: (run.restricted ? _kZtlRed : c.accent)
                      .withValues(alpha: 0.28),
                  width: routeWidthPx(18),
                ),
                PolylineLayer(
                  polylines: [
                    Feature(
                        geometry: LineString.from(run.points.map((p) =>
                            Geographic(lon: p.longitude, lat: p.latitude))))
                  ],
                  color: run.restricted ? _kZtlRed : c.accent,
                  width: routeWidthPx(9),
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
                          lon: cam.position.longitude,
                          lat: cam.position.latitude),
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
                    // Taller than the cursor itself: the shadow needs room to
                    // stretch below it without being clipped by the marker's
                    // own bounding box.
                    size: const Size(48, 76),
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
                    child: usesRealCursorAsset
                        // Every skin but the default arrow: the real
                        // UserMarker/CursorWidget system, unmodified — no
                        // baked shadow to duplicate the way arrow.svg had,
                        // so there's nothing to rebuild.
                        ? UserMarker(
                            heading: 0,
                            accent: c.accent,
                            cursorStyle: cursorStyle,
                            cursorColor: CursorColor.fromStorage(
                                Hive.box('settings')
                                    .get(CursorColor.storageKey)),
                            ostrichIsMoving: (_lastFix?.speedKmh ?? 0) >= 1.0,
                            ostrichSpeedKmh: _lastFix?.speedKmh ?? 0,
                          )
                        : _MaplibreCursor(
                            pitch: _pitch,
                            color: CursorColor.fromStorage(Hive.box('settings')
                                    .get(CursorColor.storageKey))
                                .value,
                          ),
                  ),
                ]),
            ],
          ),
          Positioned(
            top: MediaQuery.of(context).padding.top + 12,
            left: 12,
            right: 12,
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              // Search hidden once navigating, except while picking a mid-route
              // stop — same as MapScreen: adding a waypoint reuses the same
              // search UI a fresh destination search does.
              if (!_isNavigating || _pickingWaypoint) ...[
                // No back arrow here — MapScreen's home screen never had one
                // either: PlaceSearchBar's own clear (×) button closes search
                // once there's text to clear, and the system back gesture
                // (PopScope, below) closes it the rest of the way.
                PlaceSearchBar(
                  controller: _searchController,
                  colors: c,
                  onFocus: () => setState(() => _showSearch = true),
                  onChanged: _onSearchChanged,
                  onSubmitted: (q) => unawaited(_onSearchSubmit(q)),
                  onClear: () {
                    _searchController.clear();
                    FocusManager.instance.primaryFocus?.unfocus();
                    setState(() {
                      _showSearch = false;
                      _searchResults = [];
                      _nearbyCategory = null;
                      _pickingWaypoint = false;
                    });
                  },
                ),
                // Nothing typed yet: offer the one-tap nearby categories —
                // same PoiSearchService MapScreen uses, reused as-is.
                if (_showSearch && _searchController.text.isEmpty) ...[
                  const SizedBox(height: 8),
                  NearbyBar(
                    colors: c,
                    enabled: _lastFix != null,
                    selected: _nearbyCategory,
                    onSelect: _searchNearby,
                  ),
                ],
                if (_showSearch &&
                    (_searching ||
                        _searchResults.isNotEmpty ||
                        _nearbyCategory != null ||
                        _matchingFavorites(_searchController.text)
                            .isNotEmpty)) ...[
                  const SizedBox(height: 8),
                  SearchResultsList(
                    results: _searchResults,
                    isLoading: _searching,
                    favorites: _matchingFavorites(_searchController.text),
                    colors: c,
                    emptyMessage: _nearbyCategory == null
                        ? null
                        : AppLocalizations.of(context).nearbyNothingFound,
                    onSelect: (r) {
                      setState(() => _nearbyCategory = null);
                      _onSelectResult(r);
                    },
                    onSelectFavorite: (fav) {
                      _searchController.clear();
                      setState(() {
                        _showSearch = false;
                        _searchResults = [];
                        _nearbyCategory = null;
                      });
                      FocusManager.instance.primaryFocus?.unfocus();
                      unawaited(
                          _onDestinationPicked(fav.position, label: fav.label));
                    },
                  ),
                ],
                // Independent condition, not folded into the block above: with
                // an empty query _matchingFavorites('') returns every saved
                // favourite, so almost any user with one saved place would
                // permanently hide history behind that panel. They're two
                // different "nothing typed yet" panels and belong on screen
                // together, same as MapScreen.
                if (_showSearch &&
                    _searchController.text.isEmpty &&
                    _history.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  SearchHistoryList(
                    history: _history,
                    favorites: const [],
                    colors: c,
                    onSelect: (item) {
                      _searchController.clear();
                      setState(() {
                        _showSearch = false;
                        _searchResults = [];
                        _nearbyCategory = null;
                      });
                      FocusManager.instance.primaryFocus?.unfocus();
                      unawaited(_onDestinationPicked(item.position,
                          label: item.label));
                    },
                    onSelectFavorite: (_) {},
                    onClear: _clearHistory,
                  ),
                ],
                if (!_showSearch && _route == null) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
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
              ],
              if (_isRerouting || _calculatingRoute) ...[
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
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
                        AppLocalizations.of(context).calculatingRoute,
                        style: TextStyle(color: c.textSecondary, fontSize: 12)),
                  ]),
                ),
                const SizedBox(height: 8),
              ],
              if (_showArrivalBanner) ...[
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF22C55E),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Row(children: [
                    const Icon(Icons.flag_rounded, color: Colors.white),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(AppLocalizations.of(context).arrivedTitle,
                          style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w700)),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close, color: Colors.white),
                      onPressed: () {
                        _arrivalBannerTimer?.cancel();
                        setState(() => _showArrivalBanner = false);
                      },
                    ),
                  ]),
                ),
              ],
            ]),
          ),
          // ── NAV INSTRUCTION (full-width, top edge-to-edge) ─────────────────
          // The real widget MapScreen uses, not an ad-hoc lookalike — same
          // display convention too: the maneuver shown is the one AFTER
          // _currentStepIdx (the step whose point hasn't been reached yet),
          // matching NavInstruction's own "step = upcoming manoeuvre" contract.
          if (_isNavigating && _route != null && !_showSearch)
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: Builder(builder: (context) {
                final steps = _route!.steps;
                final nextIdx = _currentStepIdx + 1 < steps.length
                    ? _currentStepIdx + 1
                    : _currentStepIdx;
                final step = steps[nextIdx];
                final nextStep =
                    nextIdx + 1 < steps.length ? steps[nextIdx + 1] : null;
                return NavInstruction(
                  step: step,
                  nextStep: nextStep,
                  distToNextStepM: step.distanceM,
                  route: _route!,
                  stepIdx: _currentStepIdx,
                  colors: c,
                  topInset: MediaQuery.of(context).padding.top,
                  distToNextM: _distToNextStepM,
                  voiceMuted: _voiceMuted,
                  onToggleVoice: () {
                    setState(() => _voiceMuted = !_voiceMuted);
                    // Same key MapScreen's own mute toggle writes — one
                    // app-wide voice preference, not a copy of it.
                    Hive.box('settings').put('voiceEnabled', !_voiceMuted);
                    if (_voiceMuted) unawaited(_tts.stop());
                  },
                );
              }),
            ),
          // Real preview panel, not the summary chip this replaced — same
          // widget MapScreen shows (RoutePreviewPanel), reused as-is. Traffic
          // events/status passed empty/null: that's Nostr road-event
          // subscription, a separate piece not ported here, so the panel
          // simply doesn't show a traffic banner rather than a fake one.
          if (_route != null && !_isNavigating && !_showSearch)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: RoutePreviewPanel(
                route: _route!,
                label: null,
                trafficEvents: const [],
                bottomInset: MediaQuery.of(context).padding.bottom,
                colors: c,
                transportMode: _transportMode,
                onStart: _startNavigation,
                onCancel: _clearRoute,
                onModeChanged: (m) => unawaited(_onModeChanged(m)),
              ),
            ),
          // ── NAV PANEL (speedometer, ETA, remaining distance) ────────────────
          // Same bottom bar MapScreen shows during navigation — reused as-is.
          if (_isNavigating && _route != null)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: NavPanel(
                route: _route!,
                speed: _lastFix?.speedKmh ?? 0,
                bottomInset: MediaQuery.of(context).padding.bottom,
                colors: c,
                onStop: _showExitNavigationDialog,
                remainingDistM: _remainingDistM,
                remainingSecs: _remainingSecs,
                speedLimit: _currentSpeedLimit,
                speedometerStyle: SpeedometerStyle.fromStorage(
                    Hive.box('settings').get(SpeedometerStyle.storageKey)),
              ),
            ),
          // ── ZTL WARNING BANNER ───────────────────────────────────────────────
          // Sits just above the bottom panel, same as MapScreen. Swipe in
          // any direction to dismiss — someone with a permit drives their
          // own restricted street every day, and a warning that can't be
          // dismissed is one they stop reading.
          if (!_ztlNoticeDismissed &&
              (_inZtl || _ztlPassingBy != null) &&
              _lastFix != null)
            Positioned(
              bottom: (_isNavigating ? 176 : 104) +
                  MediaQuery.of(context).padding.bottom,
              left: 16,
              right: 16,
              child: Dismissible(
                key: ValueKey(
                    'ztl-${_inZtl ? 'in' : 'near'}-${_ztlName ?? _ztlPassingBy?.name ?? ''}'),
                direction: DismissDirection.horizontal,
                onDismissed: (_) => setState(() => _ztlNoticeDismissed = true),
                child: GestureDetector(
                  onVerticalDragEnd: (d) {
                    if ((d.primaryVelocity ?? 0) > 200) {
                      setState(() => _ztlNoticeDismissed = true);
                    }
                  },
                  child: _inZtl
                      ? ZtlBanner(
                          name: _ztlName,
                          pos: LatLng(_lastFix!.position.latitude,
                              _lastFix!.position.longitude))
                      : ZtlNearbyNotice(
                          name: _ztlPassingBy!.name,
                          pos: LatLng(_lastFix!.position.latitude,
                              _lastFix!.position.longitude)),
                ),
              ),
            ),
          // ── GPS SIGNAL LOST (tunnel/underground) ─────────────────────────────
          // Discreet pill, informative not alarming — navigation continues on
          // the last known position. Same copy/style as MapScreen's.
          if (_gpsSignalLost && _isNavigating)
            Positioned(
              top: MediaQuery.of(context).padding.top + 104,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: const Color(0xCC202030),
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: [
                      BoxShadow(
                          color: Colors.black.withValues(alpha: 0.25),
                          blurRadius: 6,
                          offset: const Offset(0, 2))
                    ],
                  ),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    const Icon(Icons.gps_off_rounded,
                        color: Colors.white70, size: 16),
                    const SizedBox(width: 7),
                    Text(AppLocalizations.of(context).gpsSignalLost,
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 12.5,
                            fontWeight: FontWeight.w500)),
                  ]),
                ),
              ),
            ),
          // ── CURRENT STREET ────────────────────────────────────────────────
          // Positioned on screen rather than attached to the marker layer —
          // a label inside that layer rotates with the map in heading-up
          // mode, and an upside-down street name is worse than none. Same
          // offset MapScreen uses: clears the NavPanel's top edge by 12px.
          if (_isNavigating && _currentStreetName != null)
            Positioned(
              bottom: 136 + MediaQuery.of(context).padding.bottom,
              left: 0,
              right: 0,
              child: Center(
                child: CurrentStreetLabel(name: _currentStreetName!, colors: c),
              ),
            ),
          // ── SPEED LIMIT SIGN (navigation + free drive) ──────────────────────
          // Same widget and offset MapScreen uses — 150 + bottomInset clears
          // the NavPanel's top edge while navigating and sits well above
          // MapBottomBar otherwise.
          if (_currentSpeedLimit != null && !_showSearch)
            Positioned(
              bottom: 150 + MediaQuery.of(context).padding.bottom,
              left: 16,
              child: SpeedLimitSign(_currentSpeedLimit!),
            ),
          // ── RIGHT FABs ────────────────────────────────────────────────────
          // Compass, recenter, report (always available — a hazard can be
          // reported from a standstill too), altitude underneath when enabled,
          // and — only while navigating — add-stop. Same order and same
          // pre-trip/en-route split as MapScreen's own right column.
          Positioned(
            right: 12,
            bottom: (_route != null && !_isNavigating && !_showSearch
                ? 220.0
                : _isNavigating
                    ? 190.0
                    : MediaQuery.of(context).padding.bottom + 16),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              CompassFab(
                rotDeg: _bearing,
                active: _headingMode,
                onTap: _toggleHeadingMode,
              ),
              const SizedBox(height: 8),
              MapFab(
                onTap: _recenter,
                colors: c,
                child: Icon(
                  _followUser ? Icons.gps_fixed : Icons.gps_not_fixed,
                  color: c.onAccent.withValues(alpha: _followUser ? 1.0 : 0.62),
                  size: 22,
                ),
              ),
              if (_lastFix != null) ...[
                const SizedBox(height: 8),
                MapFab(
                  onTap: _showReportSheet,
                  colors: c,
                  child: Icon(Icons.report_problem_outlined,
                      color: c.onAccent, size: 22),
                ),
                if (_altitudeM != null &&
                    (Hive.box('settings')
                        .get('showAltitude', defaultValue: false) as bool)) ...[
                  const SizedBox(height: 8),
                  AltitudeBadge(altitudeM: _altitudeM!, colors: c),
                ],
              ],
              if (_isNavigating) ...[
                const SizedBox(height: 8),
                MapFab(
                  onTap: _openWaypointSearch,
                  colors: c,
                  child: Icon(Icons.add_location_alt_outlined,
                      color: c.onAccent, size: 22),
                ),
              ],
            ]),
          ),
          // ── LEFT FABs — route search shortcut + parking. Pre-trip only, same
          // as MapScreen: re-planning and checking a saved spot are both
          // pre-trip actions, reporting is not (it stays on the right, in
          // both states). "Tragitto" is a simplified stand-in for MapScreen's
          // full A→B planner (RoutePlannerBar) — that multi-stop form isn't
          // ported here yet, so this just opens the same search panel the top
          // search bar does.
          if (!_isNavigating && !_showSearch && _route == null)
            Positioned(
              left: 12,
              bottom: MediaQuery.of(context).padding.bottom + 16,
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                MapFab(
                  onTap: () => setState(() => _showSearch = true),
                  colors: c,
                  child: Icon(Icons.alt_route_rounded,
                      color: c.onAccent, size: 28),
                ),
                if (_lastFix != null || _parkingPosition != null) ...[
                  const SizedBox(height: 8),
                  MapFab(
                    onTap: _showParkingSheet,
                    colors: c,
                    child: Icon(Icons.local_parking_rounded,
                        color: _parkingPosition != null
                            ? Colors.lightBlueAccent.shade100
                            : c.onAccent,
                        size: 24),
                  ),
                ],
              ]),
            ),
          // ── BOTTOM BAR (notifications / profile / settings) ─────────────────
          // Same widget MapScreen shows when nothing else claims the bottom —
          // idle, no search, no route.
          if (!_isNavigating && !_showSearch && _route == null)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: MapBottomBar(
                bottomInset: MediaQuery.of(context).padding.bottom,
                colors: c,
                pubkey: _myPubkey,
                profilePicture: _profilePicture,
                hasNostrLogin:
                    _nostrFlavor == 'amber' || _nostrFlavor == 'nsec',
                onProfileReturn: () => unawaited(_refreshHomeIdentity()),
              ),
            ),
        ]),
      ),
    );
  }
}
