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
import 'dart:ui' as ui;

import 'package:amberflutter/amberflutter.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';
import 'package:maplibre/maplibre.dart';
import 'package:nostr_tools/nostr_tools.dart' show Nip19;
import 'package:provider/provider.dart';

import '../l10n/app_localizations.dart';
import '../models/favorite_place.dart';
import '../services/camera_follow.dart';
import '../services/gps_service.dart';
import '../services/kokoro/kokoro_tts_service.dart';
import '../services/kokoro/kokoro_voices.dart';
import '../services/navigation_guidance.dart';
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
        ..color = hsl.withLightness((hsl.lightness - 0.30).clamp(0.0, 1.0)).toColor()
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

class _MaplibreMapScreenState extends State<MaplibreMapScreen> {
  final _gps = GpsService();
  StreamSubscription<GpsData>? _gpsSub;
  MapController? _controller;

  // Mirrors MapScreen's _followUser: true until the user pans/rotates/tilts
  // by hand, at which point their gesture must not be immediately fought by
  // the next GPS fix.
  bool _followUser = true;
  GpsData? _lastFix;

  /// Smoothed GPS altitude, same exponential smoothing MapScreen applies —
  /// raw altitude jitters by several metres fix to fix even standing still.
  double? _altitudeM;
  bool? _stylingDark;

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
  /// passed null (see route_progress.dart's own doc comment): the
  /// roundabout-disambiguation half of MapScreen's filter needs progress-
  /// along-route bookkeeping this screen doesn't keep, so this gets the
  /// dead-reckoning/hysteresis/reversal-rejection behaviour without the
  /// route-snap easing on top.
  final _headingFilter = HeadingFilter();
  LatLng? _prevGpsPos;

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
  bool _showSearch = false;
  bool _searching = false;
  List<NominatimResult> _searchResults = [];
  NearbyCategory? _nearbyCategory;
  Timer? _searchDebounce;
  RouteResult? _route;
  LatLng? _destination;
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
  bool _arrived = false;
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
  List<FavoritePlace> _favorites = [];

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
  final _nostr = NostrRelayService();
  static const _secStorage = FlutterSecureStorage();

  // ── Home identity (bottom bar) ────────────────────────────────────────────
  // Same three SecureStorage values MapScreen._refreshHomeIdentity reads —
  // needed for MapBottomBar's profile/notifications entries.
  String? _myPubkey;
  String? _profilePicture;
  String? _nostrFlavor;

  @override
  void initState() {
    super.initState();
    _loadParkingPosition();
    _loadFavorites();
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
    unawaited(_refreshHomeIdentity());
    unawaited(_gps.start());
    _gpsSub = _gps.stream.listen(_onGps);
  }

  /// Same read as MapScreen._refreshHomeIdentity, ported directly — the
  /// three SecureStorage values MapBottomBar needs (login state, pubkey,
  /// avatar), plus the same activity-notification subscription toggle.
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

  /// Same storage shape MapScreen._loadFavorites reads — one JSON string per
  /// favourite in the 'favorites' list.
  void _loadFavorites() {
    final raw =
        Hive.box('settings').get('favorites', defaultValue: <dynamic>[]) as List;
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
                      color: c.border, borderRadius: BorderRadius.circular(2)))),
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
                  shape:
                      RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon: const Icon(Icons.directions, color: Colors.white, size: 18),
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
                  shape:
                      RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 13),
                ),
                icon: const Icon(Icons.delete_outline_rounded,
                    color: Colors.red, size: 18),
                label:
                    Text(l.parkingRemove, style: const TextStyle(color: Colors.red)),
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
                  shape:
                      RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
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
    _gpsSub?.cancel();
    _followTicker?.cancel();
    _searchDebounce?.cancel();
    _searchController.dispose();
    unawaited(_gps.dispose());
    unawaited(_tts.dispose());
    _nostr.dispose();
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

  Future<void> _searchNearby(NearbyCategory category) async {
    if (_lastFix == null) return;
    final pos = LatLng(_lastFix!.position.latitude, _lastFix!.position.longitude);
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _nearbyCategory = category;
      _searchResults = [];
      _searching = true;
    });
    final results = await _poiSvc.nearby(category, pos,
        unnamedLabel: nearbyCategoryLabel(category, AppLocalizations.of(context)));
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
    await _onDestinationPicked(result.position);
  }

  /// Routes a picked position to either a fresh destination search or a
  /// mid-journey stop, depending on which search flow is open. Shared by
  /// search-result selection, favourite selection and nearby-category
  /// results, so all three ways of picking a place go through one place.
  Future<void> _onDestinationPicked(LatLng pos) async {
    if (_pickingWaypoint) {
      await _addWaypoint(pos);
      return;
    }
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
      _showSnack('Numero massimo di tappe raggiunto');
      return;
    }
    _activeVia = [..._activeVia, pos];
    await _rerouteAndNavigate(origin, dest);
  }

  /// Opens the search panel mid-journey to pick a stop — the FAB on the
  /// right column, visible only while navigating.
  void _openWaypointSearch() {
    if (_activeVia.length >= RoutingService.maxWaypoints) {
      _showSnack('Numero massimo di tappe raggiunto');
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
      _showSnack('In attesa del GPS…');
      return;
    }
    setState(() => _calculatingRoute = true);
    _destination = dest;
    // A fresh destination starts a new journey — any stop added to a
    // previous one no longer applies.
    _activeVia = const [];
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
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  /// Same provider-resolution logic as MapScreen._resolveProvider, ported
  /// directly: reads the configured provider/API key/self-hosted GraphHopper
  /// server, migrating a legacy Hive-stored key to SecureStorage the same
  /// way. Without this the screen always fell through to the public OSRM
  /// demo server regardless of what the driver configured in Settings,
  /// which is also why routing felt oddly slow — the demo server is shared,
  /// rate-limited public infrastructure, not whatever faster provider was
  /// actually set up.
  Future<({RoutingProvider provider, String? apiKey, String? ghServer})>
      _resolveProvider() async {
    final box = Hive.box('settings');
    final providerKey = box.get('routingProvider', defaultValue: 'osrm') as String;
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
  Future<({RouteResult route, List<({List<LatLng> points, bool restricted})> runs})?>
      _fetchRoute(LatLng origin, LatLng dest, {List<LatLng>? via}) async {
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
      _activeVia = const [];
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
    setState(() {
      _isNavigating = true;
      _currentStepIdx = 0;
      _arrived = false;
      _remainingDistM = route.totalDistanceM;
      _remainingSecs = route.totalDurationS;
    });
    if (!_voiceMuted) unawaited(_tts.announceStart());
  }

  void _stopNavigation() {
    unawaited(_tts.stop());
    _headingFilter.reset();
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
    final totalDist = route.totalDistanceM;
    final rem = (totalDist - routeProgressM).clamp(0.0, totalDist);
    _remainingDistM = rem;
    _remainingSecs =
        totalDist > 0 ? route.totalDurationS * rem / totalDist : 0;
    // Route-embedded limit (OSRM/GH annotation) first, Overpass cache as
    // fallback — same preference MapScreen._updateRemainingStats uses.
    final routeLimit = route.speedLimitAt(routeProgressM);
    _currentSpeedLimit = routeLimit ?? _speedLimitSvc.cachedLimit;
    if (routeLimit == null) {
      unawaited(_speedLimitSvc.updateIfNeeded(pos));
    }
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
    final t = NavigationGuidance.thresholds(speedKmh, _transportMode);
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

    // ── Heading resolution ───────────────────────────────────────────────
    // Same filter MapScreen runs on every fix: raw course-over-ground
    // (data.heading) is noisy enough at low speed that using it directly as
    // the camera bearing is what made the cursor look "ballerino".
    final sampleSpeed =
        data.speedKmh.isFinite && data.speedKmh > 0 ? data.speedKmh : 0.0;
    final moving = _headingFilter.updateMotion(sampleSpeed);
    final sampleAccuracy =
        data.accuracy.isFinite && data.accuracy > 0 ? data.accuracy : 20.0;
    final headingOrigin = _prevGpsPos;
    final effectiveHeading = _headingFilter.resolve(
      current: _bearing,
      from: headingOrigin,
      to: data.position,
      speedKmh: sampleSpeed,
      accuracyM: sampleAccuracy,
      providerHeading: data.heading,
      navigating: _isNavigating,
      routeLocalBearingAt: null,
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
    if (!_followUser) return;
    _targetState = CameraFollowState(
      lat: data.position.latitude,
      lng: data.position.longitude,
      zoom: _camState?.zoom ?? 17,
      rotDeg: _headingMode
          ? ((_isNavigating || moving) ? effectiveHeading : (_camState?.rotDeg ?? 0))
          : 0,
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
                  child: _MaplibreCursor(
                    pitch: _pitch,
                    color: CursorColor.fromStorage(
                            Hive.box('settings').get(CursorColor.storageKey))
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
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            // Search hidden once navigating, except while picking a mid-route
            // stop — same as MapScreen: adding a waypoint reuses the same
            // search UI a fresh destination search does.
            if (!_isNavigating || _pickingWaypoint) ...[
              Row(children: [
                Material(
                  color: c.surface2.withValues(alpha: 0.92),
                  shape: const CircleBorder(),
                  child: IconButton(
                    icon: Icon(Icons.arrow_back, color: c.textPrimary),
                    onPressed: () {
                      if (_showSearch) {
                        FocusManager.instance.primaryFocus?.unfocus();
                        setState(() {
                          _showSearch = false;
                          _pickingWaypoint = false;
                        });
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
                      setState(() {
                        _searchResults = [];
                        _nearbyCategory = null;
                      });
                    },
                  ),
                ),
              ]),
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
                      _matchingFavorites(_searchController.text).isNotEmpty)) ...[
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
                    unawaited(_onDestinationPicked(fav.position));
                  },
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
        // ── NAV INSTRUCTION (full-width, top edge-to-edge) ─────────────────
        // The real widget MapScreen uses, not an ad-hoc lookalike — same
        // display convention too: the maneuver shown is the one AFTER
        // _currentStepIdx (the step whose point hasn't been reached yet),
        // matching NavInstruction's own "step = upcoming manoeuvre" contract.
        if (_isNavigating && _route != null && !_arrived && !_showSearch)
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
        // speedLimit is always null here: this screen has no SpeedLimitService
        // wired up yet (only the speed-camera proximity cache), so the panel
        // simply never shows a limit rather than showing a wrong one.
        if (_isNavigating && _route != null && !_arrived)
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: NavPanel(
              route: _route!,
              speed: _lastFix?.speedKmh ?? 0,
              bottomInset: MediaQuery.of(context).padding.bottom,
              colors: c,
              onStop: _stopNavigation,
              remainingDistM: _remainingDistM,
              remainingSecs: _remainingSecs,
              speedometerStyle: SpeedometerStyle.fromStorage(
                  Hive.box('settings').get(SpeedometerStyle.storageKey)),
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
                  : _isNavigating && !_arrived
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
            if (_isNavigating && !_arrived) ...[
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
                child: Icon(Icons.alt_route_rounded, color: c.onAccent, size: 28),
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
    );
  }
}
