import 'dart:convert';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';

/// A single ZTL zone: name (null when OSM has no `name` tag on the element)
/// + closed polygon of LatLng vertices.
class ZtlZone {
  final String? name;
  final List<LatLng> polygon;
  const ZtlZone({required this.name, required this.polygon});
}

/// A single restricted road: name + open polyline of LatLng vertices.
///
/// This is how Italian ZTLs are ACTUALLY mapped in OSM: not as named
/// polygons, but as per-way access restrictions (`motor_vehicle=permit`,
/// `access=destination|no`, `highway=pedestrian`) on each street inside the
/// zone. Verified live on Ravenna's centro storico (2026-07): 120+ restricted
/// ways, zero ZTL-named polygons.
class ZtlWay {
  final String? name;
  final List<LatLng> points;
  const ZtlWay({required this.name, required this.points});
}

/// Fetches and caches limited-traffic-zone data from the Overpass API:
/// restricted ways (the standard Italian ZTL mapping) plus legacy ZTL
/// polygons where a city has them. Refreshes when the user moves >2 km from
/// the last query point. All failures are silent — ZTL data is best-effort.
class ZtlService {
  static final ZtlService _instance = ZtlService._();
  static ZtlService get instance => _instance;
  ZtlService._();

  /// Overpass mirrors, rotated on failure. NB: overpass.osm.ch was REMOVED —
  /// it is a Switzerland-only extract that returns empty (HTTP 200, zero
  /// elements) for every Italian location, silently blanking the feature
  /// whenever the rotation landed on it. Only worldwide mirrors belong here.
  static const _endpoints = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
  ];
  static const _retryMs = 15000;

  /// A GPS position within this distance of a restricted way counts as
  /// "inside the ZTL". Matched to typical urban GPS accuracy (5–15 m):
  /// large enough to catch driving down the street itself, small enough not
  /// to trigger when merely passing the mouth of a side street.
  static const _wayProximityM = 12.0;

  List<ZtlZone> _zones = [];
  List<ZtlWay> _restrictedWays = [];
  LatLng? _lastQueryPos;
  bool _fetching = false;
  DateTime? _nextRetryAt;
  int _endpointIdx = 0;

  /// Returns the list of ZTL zones currently loaded (may be empty).
  List<ZtlZone> get zones => _zones;

  /// Returns the restricted ways currently loaded (may be empty).
  List<ZtlWay> get restrictedWays => _restrictedWays;

  /// If the user has moved >2 km from the last query point, re-fetches
  /// ZTL data from Overpass. Call on each GPS update; silently no-ops
  /// when already fetching, inside the failure back-off window, or when
  /// position hasn't changed enough.
  Future<void> updateIfNeeded(LatLng pos) async {
    if (_fetching) return;
    // Back-off after a failure: without it, a fast network error would make
    // every 2 Hz GPS tick retry immediately (this method is called from the
    // GPS stream), hammering the endpoint and the battery.
    if (_nextRetryAt != null && DateTime.now().isBefore(_nextRetryAt!)) return;
    if (_lastQueryPos != null &&
        const Distance().as(LengthUnit.Kilometer, pos, _lastQueryPos!) < 2) {
      return;
    }
    _fetching = true;
    try {
      final (:zones, :ways) = await _fetchZtl(pos, _endpoints[_endpointIdx]);
      _zones = zones;
      _restrictedWays = ways;
      _lastQueryPos = pos;
      _nextRetryAt = null;
      debugPrint('[ZTL] loaded ${zones.length} zones, ${ways.length} restricted ways');
    } catch (e) {
      debugPrint('[ZTL] fetch failed: $e');
      _endpointIdx = (_endpointIdx + 1) % _endpoints.length;
      _nextRetryAt = DateTime.now().add(const Duration(milliseconds: _retryMs));
    } finally {
      _fetching = false;
    }
  }

  /// Returns true when [pos] is inside any loaded ZTL zone or on (within
  /// [_wayProximityM] of) any restricted way.
  bool isInsideZtl(LatLng pos) {
    for (final z in _zones) {
      if (_pointInPolygon(pos, z.polygon)) return true;
    }
    for (final w in _restrictedWays) {
      if (_nearPolyline(pos, w.points, _wayProximityM)) return true;
    }
    return false;
  }

  /// Returns the name of the ZTL zone or restricted way at [pos], or null.
  String? ztlNameAt(LatLng pos) {
    for (final z in _zones) {
      if (_pointInPolygon(pos, z.polygon)) return z.name;
    }
    for (final w in _restrictedWays) {
      if (_nearPolyline(pos, w.points, _wayProximityM)) return w.name;
    }
    return null;
  }

  /// Returns the local OFFICIAL acronym for a limited-traffic-zone at [pos],
  /// or null when the country has no single recognized national term —
  /// callers should fall back to a generic translated label rather than
  /// guessing.
  ///
  /// Verified 2026-07-16 (Wikipedia "Limited traffic zone" + French and
  /// Portuguese municipal sources): Italy and France both use the same
  /// official term "ZTL" (zona a traffico limitato / zone à trafic limité —
  /// Nantes 2012, Paris 2024). Portugal uses "ZAC" (zona de acesso
  /// condicionado / ZAAC, Lisbon and Porto). Spain, Germany, the UK, Poland
  /// etc. have equivalent restricted zones too, but no single national
  /// acronym — city ordinances use different local names (APR in Valencia;
  /// Germany's Umweltzone is a distinct emissions-based concept, not an
  /// access ban) — so a fabricated acronym there would be less accurate
  /// than the generic translated phrase already used as fallback.
  static String? officialAcronymFor(LatLng pos) {
    final lat = pos.latitude, lon = pos.longitude;
    // Portugal (mainland).
    if (lat >= 36.8 && lat <= 42.2 && lon >= -9.6 && lon <= -6.1) return 'ZAC';
    // France (mainland + Corsica).
    if (lat >= 41.2 && lat <= 51.2 && lon >= -5.3 && lon <= 9.7) return 'ZTL';
    // Italy (mainland + islands).
    if (lat >= 35.4 && lat <= 47.2 && lon >= 6.5 && lon <= 18.8) return 'ZTL';
    return null;
  }

  // ── Overpass fetch ────────────────────────────────────────────────────────

  static Future<({List<ZtlZone> zones, List<ZtlWay> ways})> _fetchZtl(
      LatLng pos, String endpoint) async {
    final lat = pos.latitude;
    final lng = pos.longitude;
    // Two data sources in one query:
    //
    // 1. Restricted DRIVABLE ways — the standard Italian ZTL mapping.
    //    Values: `permit` (authorised residents only — the classic ZTL),
    //    `destination`/`no` (closed to through traffic), `delivery` (goods
    //    ZTL). `private`/`customers` are deliberately EXCLUDED: those mark
    //    private courtyards and parking lots, not municipal traffic zones,
    //    and would fire false alarms at every private driveway.
    //    highway=pedestrian is included with no access filter (a pedestrian
    //    street is car-restricted by definition).
    //
    // 2. Legacy ZTL polygons for the few cities that map them as areas.
    //    NB: the case-insensitive regex flag is `,i` WITHOUT quotes — the
    //    previous `,"i"` was an Overpass QL parse error that made every
    //    single ZTL request fail since the feature shipped.
    final query = '''
[out:json][timeout:20];
(
  way[highway~"^(living_street|residential|unclassified|service|tertiary|secondary|primary)\$"]
     [~"^(access|motor_vehicle|vehicle|motorcar)\$"~"^(no|destination|permit|delivery)\$"]
     (around:2000,$lat,$lng);
  way[highway=pedestrian](around:2000,$lat,$lng);
  relation(around:3000,$lat,$lng)["boundary"~"^(restricted_area|limited_traffic_zone|low_emission_zone)\$"];
  relation(around:3000,$lat,$lng)["name"~"ZTL",i]["access"!="yes"];
  way(around:3000,$lat,$lng)["name"~"ZTL",i]["area"="yes"];
);
out geom;
''';

    final res = await http
        .post(Uri.parse(endpoint),
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              'User-Agent': 'Roadstr/1.0 (navigation app)',
            },
            body: 'data=${Uri.encodeComponent(query)}')
        .timeout(const Duration(seconds: 25));

    // Non-200 must THROW (not return empty) so updateIfNeeded rotates mirror
    // and backs off; returning empty would be recorded as a "successful"
    // query with zero zones and suppress retries for the next 2 km.
    if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
    final json = jsonDecode(res.body) as Map<String, dynamic>;
    final elements = json['elements'] as List? ?? [];
    return _parseElements(elements);
  }

  static ({List<ZtlZone> zones, List<ZtlWay> ways}) _parseElements(
      List elements) {
    final zones = <ZtlZone>[];
    final ways = <ZtlWay>[];
    for (final el in elements) {
      final type = el['type'] as String?;
      final tags = el['tags'] as Map<String, dynamic>? ?? {};
      // Left null when OSM has no `name` tag — callers fall back to a
      // country-appropriate label (see [officialAcronymFor]) instead of a
      // hardcoded "ZTL", which is meaningless outside Italy.
      final name = tags['name'] as String?;

      if (type == 'way') {
        final geom = el['geometry'] as List?;
        if (geom == null || geom.length < 2) continue;
        final pts = _geomToLatLng(geom);
        if (tags['area'] == 'yes' && pts.length >= 3) {
          zones.add(ZtlZone(name: name, polygon: pts));
        } else if (tags.containsKey('highway') && pts.length >= 2) {
          ways.add(ZtlWay(name: name, points: pts));
        }
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
    return (zones: zones, ways: ways);
  }

  static List<LatLng> _geomToLatLng(List geom) => geom
      .map((g) => LatLng(
          (g['lat'] as num).toDouble(), (g['lon'] as num).toDouble()))
      .toList();

  static bool _samePoint(LatLng a, LatLng b) =>
      (a.latitude - b.latitude).abs() < 1e-7 &&
      (a.longitude - b.longitude).abs() < 1e-7;

  // ── Geometry helpers ──────────────────────────────────────────────────────

  /// True when [p] lies within [maxM] metres of the [polyline].
  /// Equirectangular approximation — accurate to well under 1% at these
  /// distances, and cheap enough to run against a few hundred ways per tick.
  static bool _nearPolyline(LatLng p, List<LatLng> polyline, double maxM) {
    const degM = 111320.0;
    final cosLat = math.cos(p.latitude * math.pi / 180);
    for (int i = 0; i < polyline.length - 1; i++) {
      final a = polyline[i];
      final b = polyline[i + 1];
      // Cheap bounding pre-check: skip segments whose both endpoints are far.
      if ((a.latitude - p.latitude).abs() * degM > maxM + 200 &&
          (b.latitude - p.latitude).abs() * degM > maxM + 200) {
        continue;
      }
      final dx = (b.longitude - a.longitude) * degM * cosLat;
      final dy = (b.latitude - a.latitude) * degM;
      final ex = (p.longitude - a.longitude) * degM * cosLat;
      final ey = (p.latitude - a.latitude) * degM;
      final len2 = dx * dx + dy * dy;
      final t = len2 == 0 ? 0.0 : ((ex * dx + ey * dy) / len2).clamp(0.0, 1.0);
      final cx = ex - t * dx;
      final cy = ey - t * dy;
      if (cx * cx + cy * cy <= maxM * maxM) return true;
    }
    return false;
  }

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
