import 'dart:math' as math;

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

  /// Index of the point in [polyline] nearest to [position], scanning all of
  /// it.
  ///
  /// Fine for a one-off answer. It is **not** fine to call on every GPS fix of
  /// a long drive — it is a haversine per vertex, and an OSRM route is
  /// thousands of vertices (tens of thousands across a country) — which is
  /// what [nearestIndexNear] is for. It also cannot tell two passes over the
  /// same road apart: on a route that doubles back over itself, or loops, the
  /// nearest vertex may belong to the pass the driver is not on.
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

  /// Index of the point nearest to [position], looking first only in a window
  /// of vertices around [hint] — where the driver was last — instead of the
  /// whole route.
  ///
  /// A vehicle advances a few metres between fixes, so the answer is always
  /// close to the previous one; scanning the entire polyline for it twice a
  /// second, for the whole drive, was cost proportional to how long the route
  /// is for no gain. The window is [back] vertices behind and [ahead] in
  /// front, counted in vertices rather than metres so it stays generous on a
  /// motorway (sparse vertices) and cheap in a town (dense ones).
  ///
  /// It is only trusted when the best vertex in it is within [acceptM] of the
  /// position. Otherwise — a GPS jump, a fix after a long tunnel, a reroute
  /// that replaced the polyline — the window says nothing useful and this
  /// falls back to the full scan, so it can only ever be faster, never wrong
  /// where the full scan would have been right.
  ///
  /// And where the two differ, this is the better one: on a route that
  /// crosses itself, the nearest vertex overall may sit on the other pass;
  /// staying near the hint stays on the pass being driven.
  static int nearestIndexNear(
    List<LatLng> polyline,
    LatLng position, {
    required int hint,
    int back = 30,
    int ahead = 600,
    double acceptM = 60,
  }) {
    if (polyline.isEmpty) return 0;
    final from = math.max(0, hint - back);
    final to = math.min(polyline.length - 1, hint + ahead);
    if (from <= to) {
      var best = -1;
      var bestDist = double.infinity;
      for (var i = from; i <= to; i++) {
        final d = Geo.distanceM(polyline[i], position);
        if (d < bestDist) {
          bestDist = d;
          best = i;
        }
      }
      if (best >= 0 && bestDist <= acceptM) return best;
    }
    return nearestIndex(polyline, position);
  }

  /// The polyline index of each of [points], which must lie along [polyline]
  /// in order — the manoeuvre points of a route's steps.
  ///
  /// The obvious way, [nearestIndex] for each point, is a scan of the whole
  /// polyline per step: on a long route that is hundreds of steps times tens
  /// of thousands of vertices, all on the UI thread at the moment navigation
  /// starts. Because the points come in route order, each search can start
  /// where the last one ended, and a manoeuvre point is a vertex of the
  /// polyline, so it stops at the first one within [exactM]. Overall that is
  /// one pass over the polyline instead of one per step.
  ///
  /// A point with no good match within [maxAhead] vertices of the previous
  /// one — not actually on the polyline — falls back to the full scan, as it
  /// always was. And by never looking backwards it also puts a point on the
  /// pass the route reaches it on, where a full scan of a route that loops
  /// back to its start would put its final point at index 0.
  static List<int> nearestIndicesAlong(
    List<LatLng> polyline,
    List<LatLng> points, {
    double exactM = 1.0,
    double acceptM = 60,
    int maxAhead = 4000,
  }) {
    final result = <int>[];
    var cursor = 0;
    for (final point in points) {
      var best = -1;
      var bestDist = double.infinity;
      final end = math.min(polyline.length - 1, cursor + maxAhead);
      for (var i = cursor; i <= end; i++) {
        final d = Geo.distanceM(polyline[i], point);
        if (d < bestDist) {
          bestDist = d;
          best = i;
          if (d <= exactM) break;
        }
      }
      final index = (best >= 0 && bestDist <= acceptM)
          ? best
          : nearestIndex(polyline, point);
      result.add(index);
      cursor = index;
    }
    return result;
  }
}
