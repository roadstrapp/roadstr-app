// Throwaway proof-of-concept — lives only on the experiment/maplibre-poc
// branch, never merged to main. Answers the open questions from
// docs/rendering-engine-decision.md §7 with a real device instead of more
// reading: does the two-finger pitch gesture actually work, does a raster
// OSM tile survive being tilted, and can the same raster-hue-rotate paint
// trick that replaced the CARTO dependency be done natively instead of
// through a Flutter ColorFilter.
//
// Reachable from Settings, behind kDebugMode, so it can never end up in a
// release build even by accident.
import 'package:flutter/material.dart';
import 'package:maplibre/maplibre.dart';

import '../theme/app_theme.dart';

const _roadstrTileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

// The invert+hue-rotate+desaturate combination from map_screen.dart's
// _darkTileBuilder was a Flutter ColorFilter over a widget, which only
// works because flutter_map renders tiles as Flutter widgets. MapLibre is
// a platform view — nothing to wrap in ColorFiltered — but the style spec
// has raster paint properties for exactly this, applied natively instead.
// raster-hue-rotate takes degrees directly, so 180 is the same rotation;
// there is no separate "invert" property, so brightness-min/max is used to
// flip and compress the range instead, and saturation is turned down the
// same way the Flutter version did.
const _lightStyle = '''
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
    { "id": "osm", "type": "raster", "source": "osm" }
  ]
}
''';

const _darkStyle = '''
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
      "source": "osm",
      "paint": {
        "raster-hue-rotate": 180,
        "raster-brightness-min": 1,
        "raster-brightness-max": 0,
        "raster-saturation": -0.5,
        "raster-contrast": 0.1
      }
    }
  ]
}
''';

class MaplibrePocScreen extends StatefulWidget {
  const MaplibrePocScreen({super.key});

  @override
  State<MaplibrePocScreen> createState() => _MaplibrePocScreenState();
}

class _MaplibrePocScreenState extends State<MaplibrePocScreen> {
  MapController? _controller;
  bool _dark = false;
  double _pitch = 45;
  double _bearing = 0;
  double _zoom = 17;

  // Polled rather than driven purely off onEvent: proving the gesture
  // updates live on screen, without also having to reverse-engineer which
  // MapEvent subtype the pitch gesture actually emits, is the point here.
  void _pollCamera() {
    final cam = _controller?.camera;
    if (cam == null || !mounted) return;
    setState(() {
      _pitch = cam.pitch;
      _bearing = cam.bearing;
      _zoom = cam.zoom;
    });
  }

  @override
  Widget build(BuildContext context) {
    final c = AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!;
    return Scaffold(
      body: Stack(children: [
        MapLibreMap(
          options: MapOptions(
            initStyle: _lightStyle,
            // Same starting point every Roadstr nav camera uses: 17/45/0.
            initCenter: const Geographic(lon: 12.5, lat: 42.5),
            initZoom: 17,
            initPitch: 45,
            initBearing: 0,
            minPitch: 0,
            maxPitch: 60,
            gestures: const MapGestures.all(),
          ),
          onMapCreated: (controller) => setState(() => _controller = controller),
          onEvent: (_) => _pollCamera(),
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
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: c.surface2.withValues(alpha: 0.92),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: c.border, width: 0.5),
              ),
              child: Text(
                'pitch ${_pitch.toStringAsFixed(0)}°  '
                'bearing ${_bearing.toStringAsFixed(0)}°  '
                'zoom ${_zoom.toStringAsFixed(1)}',
                style: TextStyle(
                    color: c.textPrimary,
                    fontSize: 12,
                    fontFeatures: const [FontFeature.tabularFigures()]),
              ),
            ),
          ]),
        ),
        Positioned(
          bottom: MediaQuery.of(context).padding.bottom + 16,
          left: 12,
          right: 12,
          child: Wrap(spacing: 8, runSpacing: 8, children: [
            _pocButton('Nav tilt (45°)', c,
                () => _controller?.animateCamera(pitch: 45, bearing: 0, zoom: 17)),
            _pocButton('Top-down (0°)', c,
                () => _controller?.animateCamera(pitch: 0)),
            _pocButton('Rotate 90°', c,
                () => _controller?.animateCamera(bearing: _bearing + 90)),
            _pocButton(_dark ? 'Style: dark' : 'Style: light', c, () {
              setState(() => _dark = !_dark);
              _controller?.setStyle(_dark ? _darkStyle : _lightStyle);
            }),
          ]),
        ),
      ]),
    );
  }

  Widget _pocButton(String label, RoadstrColors c, VoidCallback? onTap) =>
      FilledButton(
        style: FilledButton.styleFrom(
          backgroundColor: c.surface2.withValues(alpha: 0.92),
          foregroundColor: c.textPrimary,
          side: BorderSide(color: c.border, width: 0.5),
        ),
        onPressed: onTap,
        child: Text(label),
      );
}
