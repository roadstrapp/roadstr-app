import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:latlong2/latlong.dart';
import 'overpass_client.dart';
import '../utils/geo.dart';

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
/// zone. Verified live on an Italian historic centre (2026-07): 120+ restricted
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

  static const _retryMs = 15000;

  /// A GPS position within this distance of a restricted way counts as
  /// "inside the ZTL". Matched to typical urban GPS accuracy (5–15 m):
  /// large enough to catch driving down the street itself, small enough not
  /// to trigger when merely passing the mouth of a side street.
  static const _wayProximityM = 12.0;

  /// A restricted street within this distance of the route is worth pointing
  /// out even though the route avoids it. Wide enough to cover the side
  /// streets a driver can actually see and be tempted by, tight enough not to
  /// paint half a historic centre red on every journey through a city.
  static const _routeVicinityM = 120.0;

  List<ZtlZone> _zones = [];
  List<ZtlWay> _restrictedWays = [];
  LatLng? _lastQueryPos;
  bool _fetching = false;
  DateTime? _nextRetryAt;
  final _overpass = OverpassClient();

  /// Returns the list of ZTL zones currently loaded (may be empty).
  List<ZtlZone> get zones => _zones;


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
      final (:zones, :ways) = await _fetchZtl(pos);
      _zones = zones;
      _restrictedWays = ways;
      _lastQueryPos = pos;
      _nextRetryAt = null;
      _overpass.noteSuccess();
      debugPrint(
          '[ZTL] loaded ${zones.length} zones, ${ways.length} restricted ways');
    } catch (e) {
      debugPrint('[ZTL] fetch failed: $e');
      // ...and the back-off grows with each consecutive failure, so a mirror
      // that is down for the whole drive is asked a handful of times, not
      // every 15 s. See [OverpassClient.failureBackoff].
      _overpass.rotate();
      _overpass.noteFailure(e);
      _nextRetryAt = DateTime.now().add(_overpass
          .failureBackoff(base: const Duration(milliseconds: _retryMs)));
    } finally {
      _fetching = false;
    }
  }

  /// Returns true when [pos] is inside any loaded ZTL zone or on (within
  /// [_wayProximityM] of) any restricted way.
  bool isInsideZtl(LatLng pos) {
    for (final z in _zones) {
      if (Geo.pointInPolygon(pos, z.polygon)) return true;
    }
    for (final w in _restrictedWays) {
      if (_nearPolyline(pos, w.points, _wayProximityM)) return true;
    }
    return false;
  }

  /// Whether [p] lies on a restricted street.
  ///
  /// Distinct from [isInsideZtl], which also counts legacy ZTL polygons: this
  /// asks only "is this point on a street that is itself restricted", which is
  /// what decides whether a stretch of route is drawn as restricted.
  bool isOnRestrictedWay(LatLng p) {
    for (final w in _restrictedWays) {
      if (_nearPolyline(p, w.points, _wayProximityM)) return true;
    }
    return false;
  }

  /// Marks each point of [points] as on a restricted street or not.
  ///
  /// Returned per point rather than as a single verdict for the whole route:
  /// a journey is normally a few restricted blocks inside an otherwise
  /// ordinary route, and colouring the entire route for them would say
  /// something false about most of it.
  List<bool> classifyPoints(List<LatLng> points) {
    if (_restrictedWays.isEmpty) {
      return List<bool>.filled(points.length, false);
    }
    return [for (final p in points) isOnRestrictedWay(p)];
  }

  /// The restricted street nearest to [pos] within [withinM], or null.
  ///
  /// Drives the advisory shown while driving *past* a restricted street, so it
  /// is keyed to where the driver is now — a street two kilometres further
  /// along the route is not yet anybody's problem.
  ZtlWay? nearestRestrictedWay(LatLng pos, {double withinM = 60.0}) {
    for (final w in _restrictedWays) {
      if (_nearPolyline(pos, w.points, withinM)) return w;
    }
    return null;
  }

  /// Test seam: injects restricted ways without an Overpass round trip.
  @visibleForTesting
  void debugSetRestrictedWays(List<ZtlWay> ways) => _restrictedWays = ways;

  /// How a planned route relates to the restricted streets around it.
  ///
  /// Two very different warnings come out of this, and conflating them would
  /// make both useless:
  ///
  /// * [transited] — restricted streets the route actually drives along. The
  ///   driver is being routed into a zone they may not legally enter.
  /// * [nearby] — restricted streets close to the route but not on it. Nothing
  ///   is wrong; the point is that these look like an inviting shortcut on the
  ///   map, and are not. A driver deviating on their own initiative is exactly
  ///   who this is for.
  ({List<ZtlWay> transited, List<ZtlWay> nearby}) analyseRoute(
    List<LatLng> route, {
    double transitedM = _wayProximityM,
    double nearbyM = _routeVicinityM,
  }) {
    if (route.length < 2 || _restrictedWays.isEmpty) {
      return (transited: const [], nearby: const []);
    }
    final transited = <ZtlWay>[];
    final nearby = <ZtlWay>[];

    for (final way in _restrictedWays) {
      if (way.points.length < 2) continue;
      // Checked in both directions: route vertices against the street, and
      // street vertices against the route. A route polyline can run for
      // hundreds of metres between vertices, so testing only its own points
      // would drive straight through a short restricted street without ever
      // sampling near it.
      final onRoute = way.points.any((p) => _nearPolyline(p, route, transitedM)) ||
          route.any((p) => _nearPolyline(p, way.points, transitedM));
      if (onRoute) {
        transited.add(way);
        continue;
      }
      final isNear = way.points.any((p) => _nearPolyline(p, route, nearbyM));
      if (isNear) nearby.add(way);
    }
    return (transited: transited, nearby: nearby);
  }

  /// Returns the name of the ZTL zone or restricted way at [pos], or null.
  String? ztlNameAt(LatLng pos) {
    for (final z in _zones) {
      if (Geo.pointInPolygon(pos, z.polygon)) return z.name;
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

  Future<({List<ZtlZone> zones, List<ZtlWay> ways})> _fetchZtl(
      LatLng pos) async {
    final lat = OverpassClient.coord(pos.latitude);
    final lng = OverpassClient.coord(pos.longitude);
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
    // 1b. CONDITIONAL restrictions. This is how most Italian ZTLs are really
    //    tagged, because they only apply during certain hours:
    //      motor_vehicle:conditional = no @ (Mo-Sa 07:00-20:00)
    //    None of the plain tags in (1) match those ways, so entire zones were
    //    invisible to the app — the reported "ZTL not recognised". Only the
    //    value before the @ is matched here; the opening-hours expression is
    //    not parsed, so the zone is treated as restricted whenever it exists.
    //    Also picks up zone-level tagging (`zone:traffic`, `boundary=
    //    traffic_zone`) used in Italy, Germany and Austria.
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
  way[highway~"^(living_street|residential|unclassified|service|tertiary|secondary|primary|pedestrian)\$"]
     [~"^(access|motor_vehicle|vehicle|motorcar):conditional\$"~"^(no|destination|permit|delivery)"]
     (around:2000,$lat,$lng);
  way(around:3000,$lat,$lng)["zone:traffic"~"(urban|restricted|limited)",i];
  relation(around:3000,$lat,$lng)["zone:traffic"~"(urban|restricted|limited)",i];
  relation(around:3000,$lat,$lng)["boundary"="traffic_zone"];
  relation(around:3000,$lat,$lng)["boundary"~"^(restricted_area|limited_traffic_zone|low_emission_zone)\$"];
  relation(around:3000,$lat,$lng)["name"~"ZTL",i]["access"!="yes"];
  way(around:3000,$lat,$lng)["name"~"ZTL",i]["area"="yes"];
);
out geom;
''';

    // A failed request must THROW (not return empty) so updateIfNeeded rotates
    // the mirror and backs off; returning empty would be recorded as a
    // "successful" query with zero zones and suppress retries for the next
    // 2 km. OverpassClient.fetchElements throws — do not swallow it here.
    final elements = await _overpass.fetchElements(query,
        maxBytes: 20 * 1024 * 1024, timeout: const Duration(seconds: 25));
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
          if (outer.isNotEmpty &&
              pts.isNotEmpty &&
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
      .map((g) =>
          LatLng((g['lat'] as num).toDouble(), (g['lon'] as num).toDouble()))
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

}
