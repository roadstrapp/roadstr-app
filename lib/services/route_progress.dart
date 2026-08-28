import 'package:latlong2/latlong.dart';

import '../utils/geo.dart';

/// Pure geometry for tracking progress along a route polyline — no Flutter,
/// no map engine, so it is testable without rendering anything and reusable
/// regardless of which screen is driving navigation.
///
/// Deliberately simpler than MapScreen's own progress tracking
/// (_stepProgressM / _routeProgressM / _segmentNearestInProgress): nearest
/// vertex rather than nearest point-on-segment, and no progress-based
/// disambiguation between candidate segments near roundabouts. Good enough
/// to drive step advancement and a distance-to-maneuver number; not the
/// same precision the tuned camera-heading logic needs.
class RouteProgress {
  RouteProgress._();

  /// Cumulative distance (metres) from [polyline]'s start to each of its
  /// points, same length as [polyline]. `result[0]` is always 0.
  static List<double> cumulativeDistances(List<LatLng> polyline) {
    final cum = List<double>.filled(polyline.length, 0);
    for (var i = 1; i < polyline.length; i++) {
      cum[i] = cum[i - 1] + Geo.distanceM(polyline[i - 1], polyline[i]);
    }
    return cum;
  }

  /// Index of the point in [polyline] nearest to [position].
  ///
  /// Brute-force: fine at a few hundred points and a 2 Hz fix rate, which is
  /// what an ordinary route and GPS stream look like. A route long enough
  /// for this to matter would need a spatial index, not a rewrite of this
  /// method — see MapScreen's own comments on why nearest-by-progress beats
  /// nearest-by-distance near roundabouts, a refinement this does not have.
  static int nearestIndex(List<LatLng> polyline, LatLng position) {
    var best = 0;
    var bestDist = double.infinity;
    for (var i = 0; i < polyline.length; i++) {
      final d = Geo.distanceM(polyline[i], position);
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    }
    return best;
  }
}
