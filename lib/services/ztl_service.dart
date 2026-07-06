import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';

/// A single ZTL zone: name + closed polygon of LatLng vertices.
class ZtlZone {
  final String name;
  final List<LatLng> polygon;
  const ZtlZone({required this.name, required this.polygon});
}

/// Fetches and caches ZTL (Zona a Traffico Limitato) polygons from the
/// Overpass API. Refreshes when the user moves >2 km from the last query
/// point. All failures are silent — ZTL data is best-effort.
class ZtlService {
  static final ZtlService _instance = ZtlService._();
  static ZtlService get instance => _instance;
  ZtlService._();

  List<ZtlZone> _zones = [];
  LatLng? _lastQueryPos;
  bool _fetching = false;

  /// Returns the list of ZTL zones currently loaded (may be empty).
  List<ZtlZone> get zones => _zones;

  /// If the user has moved >2 km from the last query point, re-fetches
  /// ZTL polygons from Overpass. Call on each GPS update; silently no-ops
  /// when already fetching or when position hasn't changed enough.
  Future<void> updateIfNeeded(LatLng pos) async {
    if (_fetching) return;
    if (_lastQueryPos != null &&
        const Distance().as(LengthUnit.Kilometer, pos, _lastQueryPos!) < 2) {
      return;
    }
    _fetching = true;
    try {
      final zones = await _fetchZtl(pos);
      _zones = zones;
      _lastQueryPos = pos;
      debugPrint('[ZTL] loaded ${zones.length} zones near '
          '${pos.latitude.toStringAsFixed(4)},${pos.longitude.toStringAsFixed(4)}');
    } catch (e) {
      debugPrint('[ZTL] fetch failed: $e');
    } finally {
      _fetching = false;
    }
  }

  /// Returns true when [pos] is inside any loaded ZTL zone.
  bool isInsideZtl(LatLng pos) {
    for (final z in _zones) {
      if (_pointInPolygon(pos, z.polygon)) return true;
    }
    return false;
  }

  /// Returns the name of the ZTL zone containing [pos], or null.
  String? ztlNameAt(LatLng pos) {
    for (final z in _zones) {
      if (_pointInPolygon(pos, z.polygon)) return z.name;
    }
    return null;
  }

  // ── Overpass fetch ────────────────────────────────────────────────────────

  static Future<List<ZtlZone>> _fetchZtl(LatLng pos) async {
    final lat = pos.latitude;
    final lng = pos.longitude;
    // Query for OSM relations/ways tagged as ZTL zones within 3 km.
    // Matches: boundary=restricted_area OR name~"ZTL" (case-insensitive)
    // combined with access=no / motor_vehicle=no.
    final query = '''
[out:json][timeout:20];
(
  relation(around:3000,$lat,$lng)["boundary"="restricted_area"];
  relation(around:3000,$lat,$lng)["name"~"ZTL","i"]["access"!="yes"];
  way(around:3000,$lat,$lng)["name"~"ZTL","i"]["area"="yes"];
);
out geom;
''';

    final uri = Uri.parse('https://overpass-api.de/api/interpreter');
    final res = await http
        .post(uri,
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'data=${Uri.encodeComponent(query)}')
        .timeout(const Duration(seconds: 20));

    if (res.statusCode != 200) return [];
    final json = jsonDecode(res.body) as Map<String, dynamic>;
    final elements = json['elements'] as List? ?? [];
    return _parseElements(elements);
  }

  static List<ZtlZone> _parseElements(List elements) {
    final zones = <ZtlZone>[];
    for (final el in elements) {
      final type = el['type'] as String?;
      final tags = el['tags'] as Map<String, dynamic>? ?? {};
      final name = (tags['name'] as String?) ?? 'ZTL';

      if (type == 'way') {
        final geom = el['geometry'] as List?;
        if (geom == null || geom.length < 3) continue;
        final poly = _geomToLatLng(geom);
        if (poly.length >= 3) zones.add(ZtlZone(name: name, polygon: poly));
      } else if (type == 'relation') {
        // Build polygon from the outer member ways' geometry.
        final members = el['members'] as List? ?? [];
        final outer = <LatLng>[];
        for (final m in members) {
          if ((m['role'] as String?) != 'outer') continue;
          final geom = m['geometry'] as List?;
          if (geom == null) continue;
          final pts = _geomToLatLng(geom);
          // Append, avoiding duplicate junction points.
          if (outer.isNotEmpty && pts.isNotEmpty &&
              _samePoint(outer.last, pts.first)) {
            outer.addAll(pts.skip(1));
          } else {
            outer.addAll(pts);
          }
        }
        if (outer.length >= 3) zones.add(ZtlZone(name: name, polygon: outer));
      }
    }
    return zones;
  }

  static List<LatLng> _geomToLatLng(List geom) => geom
      .map((g) => LatLng(
          (g['lat'] as num).toDouble(), (g['lon'] as num).toDouble()))
      .toList();

  static bool _samePoint(LatLng a, LatLng b) =>
      (a.latitude - b.latitude).abs() < 1e-7 &&
      (a.longitude - b.longitude).abs() < 1e-7;

  // ── Ray-casting polygon containment ──────────────────────────────────────

  /// Returns true when [p] is inside [polygon] (ray-casting algorithm).
  static bool _pointInPolygon(LatLng p, List<LatLng> polygon) {
    final n = polygon.length;
    if (n < 3) return false;
    bool inside = false;
    final x = p.longitude;
    final y = p.latitude;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      final xi = polygon[i].longitude, yi = polygon[i].latitude;
      final xj = polygon[j].longitude, yj = polygon[j].latitude;
      if (((yi > y) != (yj > y)) &&
          (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
        inside = !inside;
      }
    }
    return inside;
  }
}
