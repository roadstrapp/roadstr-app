// First real slice of the MapLibre rendering engine — reachable via the
// "mapEngine" setting (Impostazioni → Mappa), not merged in behind
// kDebugMode like the throwaway PoC it grew out of.
//
// Deliberately incomplete: this is Phase 2 of the incremental migration in
// docs/rendering-engine-decision.md §7 (style + base camera), not a
// replacement for MapScreen. No ZTL, no speed cameras, no POI/favourites,
// no routing, no voice guidance — those are later phases, each migrated
// and tested on its own. Switching the setting to "maplibre" trades all of
// that away for tilt/rotate and native dark-mode styling; the settings
// copy says so.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:maplibre/maplibre.dart';
import 'package:provider/provider.dart';

import '../services/gps_service.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../widgets/cursor_painter.dart';
import '../widgets/map/map_markers.dart';

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
  // the next GPS fix. No smoothing or rate-capping yet, unlike MapScreen's
  // tuned follow ticker — this slice proves the seam, not the feel.
  bool _followUser = true;
  GpsData? _lastFix;
  bool? _stylingDark;

  @override
  void initState() {
    super.initState();
    unawaited(_gps.start());
    _gpsSub = _gps.stream.listen(_onGps);
  }

  @override
  void dispose() {
    _gpsSub?.cancel();
    unawaited(_gps.dispose());
    super.dispose();
  }

  void _onGps(GpsData data) {
    _lastFix = data;
    if (!mounted) return;
    setState(() {}); // refresh the status readout
    final controller = _controller;
    if (controller == null || !_followUser) return;
    unawaited(controller.animateCamera(
      center: Geographic(lon: data.position.longitude, lat: data.position.latitude),
      bearing: data.heading,
      nativeDuration: const Duration(milliseconds: 400),
    ));
  }

  void _recenter() {
    setState(() => _followUser = true);
    final fix = _lastFix;
    final controller = _controller;
    if (fix == null || controller == null) return;
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
          children: [
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
          child: Row(children: [
            Material(
              color: c.surface2.withValues(alpha: 0.92),
              shape: const CircleBorder(),
              child: IconButton(
                icon: Icon(Icons.arrow_back, color: c.textPrimary),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ),
            const Spacer(),
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
