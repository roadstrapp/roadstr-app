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
//   - Provides an offline speech recognition entry point via a native MethodChannel.
//
// Camera animation uses a Ticker (via SingleTickerProviderStateMixin)
// rather than AnimationController because we need direct, frame-by-frame
// control over rotation, zoom, and centre simultaneously without building a
// full animation tree. The easing curve is a cubic ease-in-out applied manually.
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
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
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:nostr_tools/nostr_tools.dart' show Nip19;
import '../l10n/app_localizations.dart';
import '../models/road_event.dart';
import '../services/navigation_notification_service.dart';
import '../services/nostr_relay_service.dart';
import '../services/zap_service.dart';
import '../theme/app_theme.dart';
import '../widgets/speedometer_widget.dart';
import 'settings_screen.dart';
import 'profile_screen.dart';

class MapScreen extends StatefulWidget {
  const MapScreen({super.key});
  @override
  State<MapScreen> createState() => _MapScreenState();
}

/// State for [MapScreen]. Uses [SingleTickerProviderStateMixin] to supply a
/// [Ticker] to [_animateCamera] for smooth camera transitions.
class _MapScreenState extends State<MapScreen>
    with SingleTickerProviderStateMixin {
  final _gps = GpsService();
  final _mapController = MapController();

  /// Current GPS position (updated by [_onGps]; falls back to Italy centre).
  LatLng _position = const LatLng(42.5, 12.5);
  double _initialZoom = 6.0;
  double _speed = 0;
  double _heading = 0;
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

  /// The active camera animation ticker. Non-null during an animation; disposed
  /// and set to null when the animation completes or is interrupted by a user
  /// gesture ([MapEventMoveStart] with a non-controller source).
  Ticker? _camTicker;
  double _fromRot = 0, _toRot = 0;
  double _fromZoom = 6, _toZoom = 6;
  LatLng _fromCenter = const LatLng(42.5, 12.5);
  LatLng _toCenter = const LatLng(42.5, 12.5);
  int _animStartMs = 0;
  /// Total animation duration in milliseconds.
  static const _animMs = 550;

  RouteResult? _route;
  LatLng? _destination;
  int _currentStepIdx = 0;
  bool _isNavigating = false;
  bool _isCalculating = false;

  final _searchController = TextEditingController();
  List<NominatimResult> _searchResults = [];
  bool _isSearching = false;
  bool _showSearch = false;
  Timer? _searchDebounce;

  /// MethodChannel for native offline speech recognition (Android).
  /// The Kotlin side launches `ACTION_RECOGNIZE_SPEECH` and returns the
  /// recognised text string. No internet required — uses the on-device model.
  static const _speechCh = MethodChannel('roadstr/speech');
  bool _listening = false;

  /// Alternative routes returned by OSRM. Shown in [_AlternativesPanel] when
  /// the route is > 5 km (shorter routes go directly to the preview panel).
  List<RouteResult> _alternatives = [];
  int _selectedAlt = 0;
  bool _showAlternatives = false;

  final _nostr   = NostrRelayService();
  final _navNotif = NavigationNotificationService();
  /// Live list of non-expired road events fed by [NostrRelayService.stream].
  List<RoadEvent> _roadEvents = [];
  StreamSubscription<List<RoadEvent>>? _roadSub;

  /// Route displayed in the preview panel before the user confirms navigation.
  RouteResult? _previewRoute;
  bool _showPreview = false;

  List<_HistoryItem> _history = [];
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
    _loadInitialPosition();
    _loadHistory();
    _nostr.connect();
    _roadSub = _nostr.stream.listen((events) {
      if (mounted) setState(() => _roadEvents = events);
    });
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
      // Sottoscrive subito gli eventi nell'area, anche senza GPS attivo
      _nostr.subscribeArea(_position);
    });
  }

  Future<void> _requestGps() async {
    if (_gpsRequested) {
      if (_gpsReady) _recenter();
      return;
    }
    // Controlla se il servizio GPS è attivo PRIMA di richiedere il permesso
    final serviceEnabled = await _gps.isServiceEnabled();
    if (!mounted) return;
    if (!serviceEnabled) {
      _showGpsDisabledDialog();
      return;
    }
    setState(() => _gpsRequested = true);
    final ok = await _gps.start();
    if (!mounted) return;
    if (!ok) {
      setState(() => _gpsRequested = false);
      _showGpsDisabledDialog();
      return;
    }
    setState(() => _gpsReady = true);
    _gpsSub = _gps.stream.listen(_onGps);
    _recenter();
    _nostr.subscribeArea(_position);
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
    setState(() {
      _position = data.position;
      _speed = data.speedKmh.isFinite ? data.speedKmh : 0;
      if (data.heading != null && data.heading!.isFinite) {
        _heading = data.heading!;
      }
    });
    // Re-subscribe only when more than 2 km from the last subscription centre.
    final prev = _lastSubscribedPos;
    if (prev == null ||
        const Distance().as(LengthUnit.Kilometer, data.position, prev) > 2) {
      _lastSubscribedPos = data.position;
      _nostr.subscribeArea(data.position);
    }
    if (_isNavigating && _gpsReady && !_navTransitioning) {
      // Step updates always run regardless of _followUser so that turn
      // instructions advance even when the user has scrolled the map.
      if (_route != null) _updateNavigationStep();
      WakelockPlus.enable();

      // Camera only follows when _followUser is true.  The user can freely
      // pan/zoom to look ahead on the route; tapping the GPS FAB re-enables
      // follow mode (see _recenter).
      if (_followUser && _camTicker == null) {
        final rot = _headingMode ? -_heading : _camRotDeg;
        _mapController.moveAndRotate(_position, _camZoom, rot);
        _camCenter = _position;
        _camRotDeg = rot;
      }
    } else if (_followUser && _camTicker == null) {
      final targetRot = _headingMode ? -_heading : _camRotDeg;
      _mapController.moveAndRotate(_position, _camZoom, targetRot);
      _camCenter = _position;
      _camRotDeg = targetRot;
    }
  }

  void _updateNavigationStep() {
    if (_route == null || _currentStepIdx >= _route!.steps.length - 1) return;
    final nextStep = _route!.steps[_currentStepIdx + 1];
    final dist = const Distance().as(LengthUnit.Meter, _position, nextStep.location);
    if (dist < 30) {
      setState(() => _currentStepIdx++);
      _updateNavNotification();
    }
  }

  void _updateNavNotification() {
    if (_route == null || !_isNavigating) return;
    final step = _route!.steps[_currentStepIdx];
    final distM = step.distanceM;
    final distLabel = distM < 50
        ? 'Ora'
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

  void _startNavigation(RouteResult route) {
    if (_destination != null) {
      final label = _pendingLabel ??
          AppLocalizations.of(context)!.selectedPosition;
      _saveToHistory(label, _destination!);
    }
    setState(() {
      _route = route;
      _currentStepIdx = 0;
      _isNavigating = true;
      _showAlternatives = false;
      _alternatives = [];
      _followUser = _gpsReady;
      _headingMode = _gpsReady;
    });
    // ── Cinematic transition ──────────────────────────────────────────────
    // Phase 1 (immediate): show the entire route in a bird's-eye overview so
    //   the user can see where they're going before zooming in.
    // Phase 2 (after 1.6 s): smooth animated zoom-in to street level on the
    //   user's GPS position, rotated to face the direction of travel.
    //
    // _navTransitioning blocks GPS camera updates during the transition so the
    // two-phase animation isn't interrupted by the location stream.
    setState(() => _navTransitioning = true);
    WakelockPlus.enable();
    _fitRouteOnMap(route); // Phase 1 — overview

    if (_gpsReady) {
      // Phase 2: after the overview anim (~550 ms) plus a 1 s reading pause,
      // animate down to the street-level navigation view.
      Future.delayed(const Duration(milliseconds: 1600), () {
        if (!mounted || !_isNavigating) return;
        _animateCamera(
          toCenter: _position,
          toZoom:   17.0,
          toRot:    -_heading,
        );
      });
      // Release the transition lock after the zoom-in animation finishes.
      Future.delayed(const Duration(milliseconds: 1600 + 620), () {
        if (!mounted) return;
        setState(() => _navTransitioning = false);
        _updateNavNotification();
      });
    } else {
      // No GPS: stay at the overview, release lock immediately.
      setState(() => _navTransitioning = false);
    }
  }

  Future<void> _requestAlternatives(LatLng destination,
      {String? label, LatLng? fromPosition}) async {
    _pendingLabel = label;
    // Avvia reverse geocode in parallelo (solo per i tap sulla mappa)
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

    final origin = fromPosition ?? (_gpsReady ? _position : _camCenter);
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

    // Aspetta il geocode (di solito è già finito mentre il routing calcolava)
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
      // Percorso lungo con alternative → pannello selezione
      setState(() {
        _alternatives = routes;
        _selectedAlt  = 0;
        _showAlternatives = true;
      });
      _fitRoutesOnMap(routes);
    } else {
      // Qualunque distanza → preview prima di avviare
      _showRoutePreview(routes.first);
    }
  }

  void _showRoutePreview(RouteResult route) {
    setState(() {
      _previewRoute     = route;
      _showPreview      = true;
      _showAlternatives = false;
      _followUser       = false; // evita che il GPS tick sovrascriva il fit
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
    return _roadEvents.where((ev) => route.polyline.any(
        (p) => dist.as(LengthUnit.Meter, p, ev.position) < maxM)).toList();
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

    // Una sola chiamata Nominatim con addressdetails=1
    final geo = await RoutingService.reverseGeocodeDetail(point);
    if (!mounted) return;

    // Mostra indirizzo human-readable (prime 3 componenti)
    final dispParts = (geo?.display ?? '').split(',')
        .map((s) => s.trim()).where((s) => s.isNotEmpty).take(3).join(', ');
    setState(() => _placeAddress = dispParts.isEmpty ? null : dispParts);

    // Ricerca Wikipedia geo-aware: priorità alle coordinate, poi fallback sul nome
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

    final origin = _gpsReady ? _position : _camCenter;
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
    _planActiveField = 1; // parte dal campo "A"
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
      // campo "Da": la mia posizione oppure ricerca
      if (query.isEmpty ||
          query.toLowerCase().contains('posizione') ||
          query.toLowerCase().contains('gps')) {
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
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          Icon(Icons.navigation_rounded, color: c.accent, size: 20),
          const SizedBox(width: 8),
          Text('Uscire dalla navigazione?',
              style: TextStyle(color: c.textPrimary, fontSize: 16,
                  fontWeight: FontWeight.bold)),
        ]),
        content: Text(
          'Vuoi interrompere la navigazione e tornare alla mappa?',
          style: TextStyle(color: c.textSecondary, fontSize: 13),
        ),
        actions: [
          // "Continua" — non fa nulla, il dialog si chiude
          OutlinedButton(
            onPressed: () => Navigator.pop(context),
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: c.accent),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: Text('Continua navigazione',
                style: TextStyle(color: c.accent)),
          ),
          FilledButton(
            onPressed: () { Navigator.pop(context); _stopNavigation(); },
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFEF4444),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: const Text('Sì, esci',
                style: TextStyle(color: Colors.white)),
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
      _currentStepIdx = 0;
      _headingMode = false;
      _showAlternatives = false;
      _alternatives     = [];
      _showPreview      = false;
      _previewRoute     = null;
    });
    _animateCamera(toCenter: _position, toZoom: _camZoom, toRot: 0);
    WakelockPlus.disable();
    _navNotif.cancel();
  }

  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    if (query.length < 3) {
      setState(() => _searchResults = []);
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
    _requestAlternatives(result.position, label: result.shortName);
  }

  void _recenter() {
    if (!_position.latitude.isFinite || !_position.longitude.isFinite) return;
    // Cancella qualsiasi animazione in corso (ticker GPS potrebbe sovrascrivere)
    _camTicker?.dispose();
    _camTicker = null;
    const zoom = 17.0;
    final rot  = _headingMode ? -_heading : 0.0;
    setState(() => _followUser = true);
    // Chiamata diretta come fa _loadInitialPosition — garantisce lo zoom
    _mapController.moveAndRotate(_position, zoom, rot);
    _camCenter = _position;
    _camZoom   = zoom;
    _camRotDeg = rot;
  }

  void _toggleHeadingMode() {
    setState(() { _headingMode = !_headingMode; _followUser = true; });
    _animateCamera(
      toCenter: _position, toZoom: _camZoom,
      toRot: _headingMode ? -_heading : 0.0,
    );
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
  }) {
    // Guard: discard non-finite targets — LatLng constructor would assert/throw.
    if (!toCenter.latitude.isFinite || !toCenter.longitude.isFinite ||
        !toZoom.isFinite) return;
    _camTicker?.dispose();
    // Normalise rotation delta to shortest arc (avoids spinning > 180°).
    double delta = toRot - _camRotDeg;
    while (delta > 180) delta -= 360;
    while (delta < -180) delta += 360;
    _fromRot = _camRotDeg; _toRot = _camRotDeg + delta;
    _fromZoom = _camZoom; _toZoom = toZoom;
    _fromCenter = _camCenter; _toCenter = toCenter;
    _animStartMs = DateTime.now().millisecondsSinceEpoch;

    _camTicker = createTicker((_) {
      if (!mounted) return;
      // Normalised time [0, 1].
      final t = ((DateTime.now().millisecondsSinceEpoch - _animStartMs) /
          _animMs).clamp(0.0, 1.0);
      // Cubic ease-in-out applied to t.
      final e = t < 0.5 ? 4*t*t*t : 1 - math.pow(-2*t+2, 3)/2;
      final rot  = _fromRot  + (_toRot  - _fromRot)  * e;
      final zoom = _fromZoom + (_toZoom - _fromZoom) * e;
      final lat  = _fromCenter.latitude  + (_toCenter.latitude  - _fromCenter.latitude)  * e;
      final lng  = _fromCenter.longitude + (_toCenter.longitude - _fromCenter.longitude) * e;
      if (!lat.isFinite || !lng.isFinite || !zoom.isFinite) return;
      _mapController.moveAndRotate(LatLng(lat, lng), zoom, rot);
      _camRotDeg = rot; _camZoom = zoom; _camCenter = LatLng(lat, lng);
      if (t >= 1.0) {
        // Snap to exact target values to eliminate floating-point drift.
        _camRotDeg = _toRot; _camZoom = _toZoom; _camCenter = _toCenter;
        setState(() {});
        _camTicker?.dispose(); _camTicker = null;
      }
    });
    _camTicker!.start();
  }

  void _loadHistory() {
    final raw = Hive.box('settings')
        .get('searchHistory', defaultValue: <dynamic>[]) as List<dynamic>;
    _history = raw.whereType<String>().map((s) {
      try { return _HistoryItem.fromJsonSafe(jsonDecode(s) as Map<String, dynamic>); }
      catch (_) { return null; }
    }).whereType<_HistoryItem>().toList();
  }

  void _saveToHistory(String label, LatLng pos) {
    final item = _HistoryItem(label, pos);
    final updated = [
      item,
      ..._history.where((h) =>
          (h.position.latitude - pos.latitude).abs() > 0.0001 ||
          (h.position.longitude - pos.longitude).abs() > 0.0001),
    ];
    if (updated.length > 12) updated.removeRange(12, updated.length);
    setState(() => _history = updated);
    Hive.box('settings').put('searchHistory',
        updated.map((h) => jsonEncode(h.toJson())).toList());
  }

  void _clearHistory() {
    setState(() => _history = []);
    Hive.box('settings').delete('searchHistory');
  }

  static const _secStorage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
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
        isLoggedIn: pubKey != null,  // loggato = ha pubkey, anche solo Amber
        onConfirm: pubKey != null
            ? (stillThere) async {
                // Aggiorna immediatamente il counter locale
                if (stillThere) event.confirmations++;
                else event.denials++;
                if (mounted) setState(() {});
                try {
                  if (flavor == 'amber') {
                    // Firma tramite Amber
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
                  // Rollback del counter in caso di errore
                  if (stillThere) event.confirmations--;
                  else event.denials++;
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
            // Firma tramite Amber (NIP-55)
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
            // Firma locale con nsec
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
          // Naviga qui
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
          // Segnala evento
          SizedBox(width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () {
                Navigator.pop(context);
                _showReportSheet(position: point);
              },
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: const Color(0xFFFFB800).withOpacity(0.6)),
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

  /// Lancia ACTION_RECOGNIZE_SPEECH via MethodChannel — usa il riconoscitore
  /// nativo del dispositivo (Google, Samsung, ecc.) senza binding al service.
  Future<void> _startListening() async {
    if (_listening) return;
    setState(() { _listening = true; _showSearch = true; });
    try {
      final lang = Localizations.localeOf(context).languageCode == 'it'
          ? 'it-IT' : 'en-US';
      final text = await _speechCh.invokeMethod<String>('listen', {'lang': lang});
      if (mounted && text != null && text.isNotEmpty) {
        _searchController.text = text;
        _onSearchChanged(text);
      }
    } on PlatformException catch (e) {
      if (mounted) _snack('Voce non disponibile: ${e.message}');
    } catch (e) {
      if (mounted) _snack('Errore voce: $e');
    } finally {
      if (mounted) setState(() => _listening = false);
    }
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
    final currentStep = (_route != null && _route!.steps.isNotEmpty)
        ? _route!.steps[_currentStepIdx] : null;

    return PopScope(
      canPop: !_showSearch && !_showPreview && !_showAlternatives && !_isNavigating,
      onPopInvoked: (didPop) {
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

        // ── MAPPA ──────────────────────────────────────────────────────────
        Positioned.fill(
          child: FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _position,
              initialZoom: _initialZoom,
              onMapEvent: (e) {
                if (e is MapEventMoveStart &&
                    e.source != MapEventSource.mapController) {
                  _camTicker?.dispose(); _camTicker = null;
                  // Any user pan or pinch-zoom disables follow mode so they
                  // can look ahead freely on the route. Follow resumes when
                  // the user taps the GPS FAB (_recenter) or the heading toggle.
                  if (_followUser) setState(() => _followUser = false);
                }
                if (e is MapEventMove) {
                  _camZoom = e.camera.zoom;
                  _camCenter = e.camera.center;
                  _camRotDeg = e.camera.rotation;
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

                // Primo tap → mostra info sul luogo tappato
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
                urlTemplate: c.mapTile,
                userAgentPackageName: 'com.example.roadstr',
                maxZoom: 19,
              ),
              if (_showAlternatives)
                PolylineLayer(polylines: [
                  for (int i = 0; i < _alternatives.length; i++)
                    Polyline(
                      points: _alternatives[i].polyline,
                      strokeWidth: i == _selectedAlt ? 7 : 5,
                      color: i == _selectedAlt
                          ? c.accent
                          : Colors.grey.withOpacity(0.55),
                      strokeCap: StrokeCap.round,
                    ),
                ]),
              if (_route != null && !_showAlternatives) ...[
                PolylineLayer(polylines: [
                  Polyline(points: _route!.polyline, strokeWidth: 8,
                      color: c.accent.withOpacity(0.25)),
                  Polyline(points: _route!.polyline, strokeWidth: 5,
                      color: c.accent, strokeCap: StrokeCap.round),
                ]),
                // Overlay traffico pesante in rosso durante la navigazione
                if (_roadEvents.isNotEmpty)
                  PolylineLayer(polylines: [
                    for (final seg in _trafficSegments(_route!.polyline))
                      Polyline(points: seg, strokeWidth: 8,
                          color: const Color(0xFFEF4444).withOpacity(0.85),
                          strokeCap: StrokeCap.round),
                  ]),
              ],
              if (_previewRoute != null && _showPreview) ...[
                PolylineLayer(polylines: [
                  Polyline(points: _previewRoute!.polyline, strokeWidth: 8,
                      color: c.accent.withOpacity(0.20)),
                  Polyline(points: _previewRoute!.polyline, strokeWidth: 5,
                      color: c.accent, strokeCap: StrokeCap.round),
                ]),
                // Traffico pesante segnalato (rosso) in anteprima
                if (_roadEvents.isNotEmpty)
                  PolylineLayer(polylines: [
                    for (final seg in _trafficSegments(_previewRoute!.polyline))
                      Polyline(points: seg, strokeWidth: 8,
                          color: const Color(0xFFEF4444).withOpacity(0.85),
                          strokeCap: StrokeCap.round),
                  ]),
              ],
              if (_destination != null)
                MarkerLayer(markers: [
                  Marker(point: _destination!, width: 40, height: 48,
                      child: _DestinationMarker(color: c.accent)),
                ]),
              // Marker eventi stradali: visibili solo a zoom ≥ 11
              // e filtrati anche in tempo reale per TTL scaduto
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
                MarkerLayer(markers: [
                  Marker(point: _position, width: 48, height: 48,
                      child: _UserMarker(
                          heading: _headingMode ? 0 : _heading,
                          accent: c.accent)),
                ]),
            ],
          ),
        ),

        // ── ISTRUZIONE / RICERCA IN ALTO ────────────────────────────────────
        Positioned(
          top: topInset + 12, left: 16, right: 16,
          child: _isNavigating && currentStep != null
              ? _NavInstruction(step: currentStep, route: _route!,
                  stepIdx: _currentStepIdx, colors: c)
              : _SearchBarWidget(
                  controller: _searchController, colors: c,
                  onFocus: () => setState(() => _showSearch = true),
                  onChanged: _onSearchChanged,
                  onClear: () {
                    _searchController.clear();
                    setState(() { _searchResults = []; _showSearch = false; });
                    FocusScope.of(context).unfocus();
                  },
                  onVoiceTap: _listening ? null : _startListening,
                  isListening: _listening,
                ),
        ),

        // ── RISULTATI RICERCA / CRONOLOGIA ───────────────────────────────────
        if (_showSearch)
          if (_searchController.text.isEmpty && _history.isNotEmpty)
            Positioned(
              top: topInset + 76, left: 16, right: 16,
              child: _HistoryResults(
                history: _history, colors: c,
                onSelect: (item) =>
                    _requestAlternatives(item.position, label: item.label),
                onClear: _clearHistory,
              ),
            )
          else if (_searchResults.isNotEmpty || _isSearching)
            Positioned(
              top: topInset + 76, left: 16, right: 16,
              child: _SearchResults(
                results: _searchResults, isLoading: _isSearching,
                colors: c, onSelect: _selectSearchResult,
              ),
            ),

        // ── FAB SINISTRA: segnala evento + percorso A→B ──────────────────────
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
            ),
          ),

        // ── FAB DESTRA ───────────────────────────────────────────────────────
        if (!_showSearch && !_showAlternatives && !_showPreview)
          Positioned(
            right: 12,
            bottom: (_isNavigating ? 160 : 88) + bottomInset + 12,
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              _MapFab(onTap: _toggleHeadingMode, colors: c,
                child: Transform.rotate(
                  angle: -_camRotDeg * math.pi / 180,
                  child: Icon(Icons.navigation_rounded,
                    color: _headingMode ? c.accent : c.textSecondary, size: 22),
                ),
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
              const SizedBox(height: 8),
              _MapFab(onTap: () {
                setState(() => _followUser = false);
                _mapController.move(_camCenter, _camZoom + 1);
                _camZoom += 1;
              }, colors: c,
                child: Icon(Icons.add, color: c.textPrimary, size: 22)),
              const SizedBox(height: 8),
              _MapFab(onTap: () {
                setState(() => _followUser = false);
                _mapController.move(_camCenter, _camZoom - 1);
                _camZoom -= 1;
              }, colors: c,
                child: Icon(Icons.remove, color: c.textPrimary, size: 22)),
            ]),
          ),

        // ── PANNELLO NAV / BOTTOM BAR ────────────────────────────────────────
        Positioned(
          bottom: 0, left: 0, right: 0,
          child: _isNavigating && _route != null
              ? _NavPanel(route: _route!, speed: _speed,
                  bottomInset: bottomInset, colors: c, onStop: _stopNavigation)
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
                        _closePlaceInfo();
                        _requestAlternatives(_placePoint!,
                            label: _placeAddress?.split(',').first);
                      },
                      onClose: _closePlaceInfo,
                    )
              : _showPreview && _previewRoute != null
                  ? _PreviewPanel(
                      route: _previewRoute!,
                      label: _pendingLabel,
                      trafficEvents: _routeTrafficEvents(_previewRoute!),
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
                      onConfirm:     _showRoutePreview,
                      onCancel:      _cancelAlternatives,
                      onModeChanged: _recalculateForMode,
                    )
                  : _BottomBar(bottomInset: bottomInset, colors: c),
        ),

        // ── OVERLAY CALCOLO ──────────────────────────────────────────────────
        if (_isCalculating)
          Positioned.fill(
            child: Container(
              color: Colors.black.withOpacity(0.3),
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

        // ── BADGE GPS ────────────────────────────────────────────────────────
        if (_gpsRequested && !_gpsReady)
          Positioned(
            top: topInset + 76, left: 0, right: 0,
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: c.surface2, borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: const Color(0xFFFFB800).withOpacity(0.6)),
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
  void dispose() {
    _camTicker?.dispose();
    _gpsSub?.cancel();
    _roadSub?.cancel();
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
      aOptions: AndroidOptions(encryptedSharedPreferences: true));

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
      if (mounted) setState(() { _status = 'Errore: $e'; _sending = false; });
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
        Text('Scegli l\'importo in satoshi (sats):',
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
                  color: sel ? const Color(0xFFF7931A).withOpacity(0.15) : c.surface2,
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
          child: Text('Annulla', style: TextStyle(color: c.textSecondary)),
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
              color: event.category.color.withOpacity(0.15),
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
                color: const Color(0xFFF7931A).withOpacity(0.12),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                    color: const Color(0xFFF7931A).withOpacity(0.4)),
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
        // ── Pulsante Zap ────────────────────────────────────────────────────
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
        GridView.count(
          shrinkWrap: true,
          crossAxisCount: 5,
          mainAxisSpacing: 8, crossAxisSpacing: 8,
          childAspectRatio: 0.85,
          physics: const NeverScrollableScrollPhysics(),
          children: RoadCategory.values.map((cat) {
            final sel = _selected == cat;
            return GestureDetector(
              onTap: () => setState(() => _selected = cat),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                decoration: BoxDecoration(
                  color: sel ? cat.color.withOpacity(0.18) : c.surface3,
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

class _PlaceInfoPanel extends StatelessWidget {
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

  void _openMore(BuildContext ctx) {
    // Apri Wikipedia (pagina mobile) se trovata, altrimenti motore scelto
    String? url;
    String? title;
    if (wiki?.pageUrl != null) {
      url   = wiki!.pageUrl!;
      title = wiki!.title;
    } else if (wikiQuery != null) {
      url   = _searchUrl(wikiQuery!);
      title = wikiQuery;
    }
    if (url == null) return;
    // WebView interno all'app — niente browser esterno
    Navigator.push(ctx, MaterialPageRoute(
      fullscreenDialog: true,
      builder: (_) => _WebViewScreen(url: url!, title: title),
    ));
  }

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
    final c = colors;
    return GestureDetector(
      // Swipe verso il basso → chiude
      onVerticalDragEnd: (d) {
        if ((d.primaryVelocity ?? 0) > 300) onClose();
      },
      child: Container(
        decoration: BoxDecoration(
          color: c.surface2,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          border: Border(top: BorderSide(color: c.border, width: 0.5)),
          boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.12),
              blurRadius: 16, offset: const Offset(0, -4))],
        ),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          // Handle di trascinamento
          const SizedBox(height: 10),
          Center(child: Container(width: 40, height: 4,
              decoration: BoxDecoration(color: c.border,
                  borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 12),

          // Titolo / indirizzo
          Padding(padding: const EdgeInsets.symmetric(horizontal: 20),
            child: loading && address == null
              ? Row(children: [
                  SizedBox(width: 14, height: 14,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: c.accent)),
                  const SizedBox(width: 10),
                  Text('Ricerca informazioni…',
                      style: TextStyle(color: c.textSecondary, fontSize: 13)),
                ])
              : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  if (wiki != null) ...[
                    Text(wiki!.title, style: TextStyle(
                        color: c.textPrimary, fontSize: 17,
                        fontWeight: FontWeight.bold)),
                    const SizedBox(height: 2),
                    if (address != null)
                      Text(address!, maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: c.textSecondary, fontSize: 12)),
                  ] else
                    Text(address ??
                        '${point.latitude.toStringAsFixed(5)}, '
                        '${point.longitude.toStringAsFixed(5)}',
                        style: TextStyle(color: c.textPrimary, fontSize: 15,
                            fontWeight: FontWeight.w600)),
                ]),
          ),

          // Immagine + estratto
          if (wiki != null) ...[
            const SizedBox(height: 10),
            if (wiki!.imageUrl != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: Image.network(wiki!.imageUrl!,
                      height: 110, width: double.infinity,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const SizedBox.shrink()),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
              child: Text(wiki!.extract,
                  maxLines: 3, overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: c.textSecondary, fontSize: 13,
                      height: 1.4)),
            ),
          ],

          // Pulsante "Scopri di più" / Qwant (se c'è qualcosa da aprire)
          if (!loading && (wiki?.pageUrl != null || wikiQuery != null)) ...[
            const SizedBox(height: 8),
            Padding(padding: const EdgeInsets.symmetric(horizontal: 20),
              child: GestureDetector(
                onTap: () => _openMore(context),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(wiki?.pageUrl != null
                      ? Icons.open_in_browser_rounded
                      : Icons.search_rounded,
                      color: c.accent, size: 14),
                  const SizedBox(width: 4),
                  Text(
                    wiki?.pageUrl != null
                        ? 'Leggi su Wikipedia'
                        : 'Cerca su ${_engineName()}',
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
                onPressed: onClose,
                style: OutlinedButton.styleFrom(
                  side: BorderSide(color: c.border),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                ),
                child: Text('Chiudi',
                    style: TextStyle(color: c.textSecondary)),
              ),
              const SizedBox(width: 12),
              Expanded(child: FilledButton.icon(
                onPressed: onNavigate,
                style: FilledButton.styleFrom(
                  backgroundColor: c.accent,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                ),
                icon: const Icon(Icons.navigation_rounded,
                    color: Colors.white, size: 18),
                label: const Text('Naviga qui',
                    style: TextStyle(color: Colors.white)),
              )),
            ]),
          ),
          SizedBox(height: bottomInset > 0 ? bottomInset : 14),
        ]),
      ),
    );
  }
}

// ── WebView screen ────────────────────────────────────────────────────────────

class _WebViewScreen extends StatefulWidget {
  final String url;
  final String? title;
  const _WebViewScreen({required this.url, this.title});
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
          // Apri nel browser esterno (opzionale)
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
      body: WebViewWidget(controller: _ctrl),
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
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.16),
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
                // Se mostra "La mia posizione", seleziona tutto in blocco
                // così il tasto backspace lo cancella in un colpo solo
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
                hintText: 'Partenza…',
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
                hintText: 'Destinazione…',
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
            label: 'Auto',
            selected: transportMode == 'driving',
            colors: c,
            onTap: () => onModeChanged('driving'),
          ),
          const SizedBox(width: 8),
          _ModeChip(
            icon: Icons.directions_walk_rounded,
            label: 'A piedi',
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
            child: Text('Annulla',
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
            label: const Text('Calcola percorso',
                style: TextStyle(color: Colors.white, fontSize: 13)),
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
  final RoadstrColors colors;
  final ValueChanged<_HistoryItem> onSelect;
  final VoidCallback onClear;
  const _HistoryResults({required this.history, required this.colors,
      required this.onSelect, required this.onClear});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.15),
            blurRadius: 12, offset: const Offset(0, 4))],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
          child: Row(children: [
            Icon(Icons.history_rounded, color: colors.textSecondary, size: 16),
            const SizedBox(width: 6),
            Text(AppLocalizations.of(context)!.history, style: TextStyle(
                color: colors.textSecondary, fontSize: 12,
                fontWeight: FontWeight.w600)),
            const Spacer(),
            TextButton(
              onPressed: onClear,
              style: TextButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 8)),
              child: Text(AppLocalizations.of(context)!.clearHistory, style: TextStyle(
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
          ),  // ListView.separated
        ), // Material
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
  final double bottomInset;
  final RoadstrColors colors;
  final String transportMode;
  final VoidCallback onStart;
  final VoidCallback onCancel;
  final ValueChanged<String> onModeChanged;
  const _PreviewPanel({
    required this.route, required this.label,
    required this.trafficEvents, required this.bottomInset,
    required this.colors,
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
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.12),
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
            // Destinazione
            if (label != null && label!.isNotEmpty) ...[
              Text(label!, style: TextStyle(color: c.textPrimary,
                  fontSize: 17, fontWeight: FontWeight.bold),
                  maxLines: 1, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 10),
            ],

            // Durata + distanza
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

            // Partenza / Arrivo
            Row(children: [
              Icon(Icons.schedule_rounded,
                  color: c.textSecondary, size: 16),
              const SizedBox(width: 4),
              Text('Partenza $depStr  →  Arrivo stimato $arrStr',
                  style: TextStyle(color: c.textSecondary, fontSize: 13)),
            ]),

            // Segnalazioni sul percorso
            if (trafficEvents.isNotEmpty) ...[
              const SizedBox(height: 12),
              Divider(height: 0.5, color: c.border),
              const SizedBox(height: 10),
              Text('Condizioni sul percorso',
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
                child: Text('Annulla',
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
                label: const Text('Avvia navigazione',
                    style: TextStyle(color: Colors.white)),
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
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.12),
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
                color: colors.accent.withOpacity(0.15),
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
  final VoidCallback onClear;
  final VoidCallback? onVoiceTap;
  final bool isListening;
  const _SearchBarWidget({
    required this.controller, required this.colors,
    required this.onFocus, required this.onChanged, required this.onClear,
    this.onVoiceTap, this.isListening = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      decoration: BoxDecoration(
        color: colors.surface2, borderRadius: BorderRadius.circular(28),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.18),
            blurRadius: 12, offset: const Offset(0, 3))],
      ),
      child: Row(children: [
        const SizedBox(width: 18),
        Icon(Icons.search, color: colors.textSecondary, size: 20),
        const SizedBox(width: 12),
        Expanded(
          child: TextField(
            controller: controller, onTap: onFocus, onChanged: onChanged,
            style: TextStyle(color: colors.textPrimary, fontSize: 16),
            decoration: InputDecoration(
              hintText: isListening ? 'Parla…' : AppLocalizations.of(context)!.searchHint,
              hintStyle: TextStyle(
                color: isListening ? colors.accent : colors.textSecondary,
                fontSize: 16),
              border: InputBorder.none, isDense: true,
            ),
          ),
        ),
        if (controller.text.isNotEmpty)
          IconButton(
            icon: Icon(Icons.close, color: colors.textSecondary, size: 20),
            onPressed: onClear)
        else
          GestureDetector(
            onTap: onVoiceTap,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              margin: const EdgeInsets.only(right: 6),
              width: 40, height: 40,
              decoration: BoxDecoration(
                color: isListening ? colors.accent : colors.accentSoft,
                shape: BoxShape.circle,
              ),
              child: Icon(
                isListening ? Icons.mic_rounded : Icons.mic_none_rounded,
                color: isListening ? Colors.white : colors.accent,
                size: 20,
              ),
            ),
          ),
      ]),
    );
  }
}

class _SearchResults extends StatelessWidget {
  final List<NominatimResult> results;
  final bool isLoading;
  final RoadstrColors colors;
  final ValueChanged<NominatimResult> onSelect;
  const _SearchResults({required this.results, required this.isLoading,
      required this.colors, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2, borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.15),
            blurRadius: 12, offset: const Offset(0, 4))],
      ),
      child: isLoading
          ? Padding(padding: const EdgeInsets.all(16),
              child: Center(child: SizedBox(width: 20, height: 20,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: colors.accent))))
          : Material(
              color: Colors.transparent,
              child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: EdgeInsets.zero,
              itemCount: results.length,
              separatorBuilder: (_, __) =>
                  Divider(height: 0.5, color: colors.border),
              itemBuilder: (_, i) {
                final r = results[i];
                // categoryLabel: human-readable type (e.g. "Ristorante", "Museo")
                // Falls back to a trimmed address fragment when type is unmapped.
                final catLabel = r.categoryLabel;
                return ListTile(
                  tileColor: Colors.transparent,
                  // Emoji replaces the generic pin icon to convey feature type
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
              ), // ListView
            ), // Material
    );
  }
}

class _NavInstruction extends StatelessWidget {
  final RouteStep step;
  final RouteResult route;
  final int stepIdx;
  final RoadstrColors colors;
  const _NavInstruction({required this.step, required this.route,
      required this.stepIdx, required this.colors});

  @override
  Widget build(BuildContext context) {
    final land = MediaQuery.of(context).orientation == Orientation.landscape;
    final iconSz = land ? 20.0 : 26.0;
    final boxSz  = land ? 32.0 : 44.0;
    final fsMain = land ? 12.0 : 14.0;
    final fsSub  = land ? 11.0 : 12.0;
    final vPad   = land ?  6.0 : 12.0;
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 16, vertical: vPad),
      decoration: BoxDecoration(
        color: colors.surface2, borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.18),
            blurRadius: 12, offset: const Offset(0, 3))],
      ),
      child: Row(children: [
        Container(
          width: boxSz, height: boxSz,
          decoration: BoxDecoration(color: colors.accentSoft,
              borderRadius: BorderRadius.circular(10)),
          child: Icon(_directionIcon(step.direction),
              color: colors.accent, size: iconSz),
        ),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min, children: [
          Text(step.instruction, style: TextStyle(color: colors.textPrimary,
              fontSize: fsMain, fontWeight: FontWeight.w600),
              maxLines: land ? 1 : 2, overflow: TextOverflow.ellipsis),
          const SizedBox(height: 2),
          Text(_distLabel(step.distanceM, AppLocalizations.of(context)!.now),
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

  IconData _directionIcon(String direction) {
    switch (direction) {
      case 'arrive': return Icons.flag_rounded;
      case 'depart': return Icons.play_arrow_rounded;
      case 'roundabout':
      case 'rotary': return Icons.roundabout_right;
      case 'merge':  return Icons.merge;
      case 'fork':   return Icons.call_split;
      default:       return Icons.straight;
    }
  }
}

class _NavPanel extends StatelessWidget {
  final RouteResult route;
  final double speed;
  final double bottomInset;
  final RoadstrColors colors;
  final VoidCallback onStop;
  const _NavPanel({required this.route, required this.speed,
      required this.bottomInset, required this.colors, required this.onStop});

  @override
  Widget build(BuildContext context) {
    final land     = MediaQuery.of(context).orientation == Orientation.landscape;
    final speedoSz = land ? 70.0 : 110.0;
    final fsDist   = land ? 16.0 : 22.0;
    final fsDur    = land ? 12.0 : 14.0;
    final vTop     = land ?  6.0 : 14.0;
    final vBot     = land
        ? (bottomInset > 0 ? bottomInset + 4 : 8.0)
        : (bottomInset > 0 ? bottomInset     : 16.0);
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        border: Border(top: BorderSide(color: colors.border, width: 0.5)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.12),
            blurRadius: 12, offset: const Offset(0, -2))],
      ),
      padding: EdgeInsets.only(left: 16, right: 16, top: vTop, bottom: vBot),
      child: Row(children: [
        SpeedometerWidget(speedKmh: speed, size: speedoSz),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min, children: [
          Text(route.distanceLabel, style: TextStyle(color: colors.textPrimary,
              fontSize: fsDist, fontWeight: FontWeight.bold)),
          Text(route.durationLabel,
              style: TextStyle(color: colors.textSecondary, fontSize: fsDur)),
        ])),
        GestureDetector(onTap: onStop,
          child: Container(width: 48, height: 48,
            decoration: BoxDecoration(
              color: const Color(0xFFFF4444).withOpacity(0.12),
              shape: BoxShape.circle,
              border: Border.all(
                  color: const Color(0xFFFF4444).withOpacity(0.4)),
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
      boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.12),
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
    shadowColor: Colors.black.withOpacity(0.25),
    child: InkWell(onTap: onTap, customBorder: const CircleBorder(),
        child: SizedBox(width: 44, height: 44,
            child: Center(child: child))),
  );
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
    canvas.drawCircle(c, r, Paint()..color = accent.withOpacity(0.18));
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
          boxShadow: [BoxShadow(color: color.withOpacity(0.4),
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
