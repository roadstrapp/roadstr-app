import 'dart:math' as math;

import 'package:latlong2/latlong.dart';

/// Planar geometry on WGS84 coordinates.
///
/// These primitives existed in four copies across the map screen, the POI
/// service and the ZTL service. Keeping one copy is not only tidier: the
/// copies had drifted, and the map screen's segment-distance function applied
/// the latitude compression to the wrong axis (see [projectOnSegment]).
///
/// Everything here uses an **equirectangular** projection: degrees are scaled
/// to metres around the query point. Over the distances this app measures
/// (metres to a few kilometres) the error against a great-circle computation
/// is far below GPS noise, and it costs a couple of multiplications instead of
/// several trigonometric calls per polyline segment — which matters when
/// scanning a route of 100 000 points on every GPS tick.
class Geo {
  const Geo._();

  /// Metres per degree of latitude. Longitude degrees are shorter by
  /// cos(latitude), which is what [_cosLat] accounts for.
  static const metresPerDegree = 111320.0;

  static double _cosLat(double latitude) =>
      math.cos(latitude * math.pi / 180);

  /// True when [p] lies inside [polygon] (ray casting, even-odd rule).
  ///
  /// The polygon may be open or closed; a ring of fewer than three points can
  /// contain nothing and returns false.
  static bool pointInPolygon(LatLng p, List<LatLng> polygon) {
    final n = polygon.length;
    if (n < 3) return false;
    var inside = false;
    final x = p.longitude, y = p.latitude;
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

  /// Projects [p] onto segment [a]→[b].
  ///
  /// Returns the distance in metres to the closest point of the segment, and
  /// `t` — where that closest point falls along it, 0 at [a] and 1 at [b].
  ///
  /// The `cos(latitude)` factor scales the **longitude** difference: a degree
  /// of longitude shrinks toward the poles while a degree of latitude does
  /// not. Applying it to the latitude instead (as one earlier copy of this
  /// code did) understates north-south distances by ~28 % at Italian
  /// latitudes and overstates east-west ones by ~39 %.
  static ({double distM, double t}) projectOnSegment(
      LatLng p, LatLng a, LatLng b) {
    final cosLat = _cosLat(p.latitude);
    final dx = (b.longitude - a.longitude) * metresPerDegree * cosLat;
    final dy = (b.latitude - a.latitude) * metresPerDegree;
    final px = (p.longitude - a.longitude) * metresPerDegree * cosLat;
    final py = (p.latitude - a.latitude) * metresPerDegree;
    final len2 = dx * dx + dy * dy;
    // Degenerate segment (a == b): the projection is a itself.
    final t = len2 == 0 ? 0.0 : ((px * dx + py * dy) / len2).clamp(0.0, 1.0);
    final ex = px - t * dx;
    final ey = py - t * dy;
    return (distM: math.sqrt(ex * ex + ey * ey), t: t);
  }

  /// Distance in metres from [p] to the closest point of segment [a]→[b].
  static double distanceToSegmentM(LatLng p, LatLng a, LatLng b) =>
      projectOnSegment(p, a, b).distM;

  /// Distance in metres from [p] to the closest point of the polyline [poly],
  /// or [double.infinity] for a polyline with fewer than two points.
  static double distanceToPolylineM(LatLng p, List<LatLng> poly) {
    var best = double.infinity;
    for (var i = 0; i < poly.length - 1; i++) {
      final d = projectOnSegment(p, poly[i], poly[i + 1]).distM;
      if (d < best) best = d;
    }
    return best;
  }

  /// Initial great-circle bearing from [from] to [to], in degrees clockwise
  /// from north (0–360).
  static double bearingBetween(LatLng from, LatLng to) {
    final lat1 = from.latitude * math.pi / 180;
    final lat2 = to.latitude * math.pi / 180;
    final dLon = (to.longitude - from.longitude) * math.pi / 180;
    final y = math.sin(dLon) * math.cos(lat2);
    final x = math.cos(lat1) * math.sin(lat2) -
        math.sin(lat1) * math.cos(lat2) * math.cos(dLon);
    return (math.atan2(y, x) * 180 / math.pi + 360) % 360;
  }
}
