import 'package:latlong2/latlong.dart';

import 'geo.dart';

/// Which way the map should face, decided from consecutive GPS fixes.
///
/// The direction of travel is computed from two positions (dead reckoning)
/// rather than taken from the GPS provider's own heading, because that one
/// reflects how the phone is held. Two things make it harder than subtracting
/// coordinates, and both are why this is a class with memory rather than a
/// formula:
///
///   * fixes jitter, so two samples taken metres apart can point anywhere;
///   * Android's fused provider ROAD-SNAPS fixes, and next to a parallel or
///     opposite carriageway it can glue several consecutive fixes onto that
///     other road, producing a steady — and completely wrong — reversed
///     bearing that no single-sample check can catch.
///
/// Extracted from the GPS tick handler, where it was six levels of nesting
/// inside a 90-line method and could not be tested at all.
class HeadingFilter {
  /// Below this speed a two-fix bearing means nothing; the provider heading is
  /// all there is.
  static const _minSpeedKmh = 3.0;

  /// Movement under this is indistinguishable from noise. Scaled by the fix's
  /// own accuracy, with a floor for optimistic accuracy reports.
  static const _minMoveM = 8.0;

  /// Beyond this the new bearing is a reversal, not a turn.
  static const _reversalDeg = 100.0;

  /// Two reversal samples this close together are the same U-turn, not noise.
  static const _agreementDeg = 45.0;

  /// A bearing opposing the route's local direction by more than this, while
  /// still on the route, is a road-snap artifact.
  static const _againstRouteDeg = 100.0;

  /// How far off the route the fix may be for its local bearing to be a
  /// trustworthy reference.
  static const _onRouteM = 35.0;

  /// Rejected reversals tolerated before one is accepted anyway, so the
  /// heading can never lock up for good.
  ///
  /// Above [_smoothingMinSpeedKmh] the easing stage pins an on-route heading
  /// to the route anyway, so what this valve releases is the state machine,
  /// not necessarily the visible heading: below that speed — crawling in
  /// traffic, where the easing is off — it is what lets a real reversal
  /// through.
  static const _maxHeldTicks = 5;

  /// Below this the vehicle is manoeuvring, not following the route's line.
  static const _smoothingMinSpeedKmh = 5.0;

  /// A disagreement with the route wider than this is not smoothed away, it is
  /// replaced: at that angle the bearing is noise, not a turn in progress.
  static const _snapToRouteDeg = 110.0;

  /// How much of the remaining angle is taken per fix while easing.
  static const _smoothingFactor = 0.35;

  double? _pendingReversal;
  int _heldTicks = 0;

  /// Forgets the pending reversal. Call when a journey starts or ends.
  void reset() {
    _pendingReversal = null;
    _heldTicks = 0;
  }

  /// The heading to steer the map by for a fix at [to].
  ///
  /// [routeLocalBearingAt] returns the route's own direction at a position and
  /// how far that position is from it, or null when there is no active route.
  double resolve({
    required double current,
    required LatLng? from,
    required LatLng to,
    required double speedKmh,
    required double accuracyM,
    required double? providerHeading,
    required bool navigating,
    ({double distM, double bearing})? Function(LatLng)? routeLocalBearingAt,
  }) {
    final fallback = (providerHeading != null &&
            providerHeading.isFinite &&
            providerHeading > 0)
        ? providerHeading
        : current;
    if (from == null || speedKmh <= _minSpeedKmh) return fallback;
    if (Geo.distanceM(from, to) <= _reliabilityFloor(accuracyM)) {
      return fallback;
    }

    final bearing = Geo.bearingBetween(from, to);
    if (!bearing.isFinite) return current;

    // An ordinary change of direction: take it.
    if (!navigating || angleBetween(bearing, current) <= _reversalDeg) {
      reset();
      return _towardRoute(
          bearing, to, speedKmh, navigating, routeLocalBearingAt);
    }
    if (_contradictsRoute(bearing, to, routeLocalBearingAt)) {
      _heldTicks++;
      _pendingReversal = null;
      return current;
    }
    // A genuine U-turn keeps producing the reversed bearing on the next fix;
    // a multipath glitch does not.
    final pending = _pendingReversal;
    if (pending != null && angleBetween(bearing, pending) <= _agreementDeg) {
      reset();
      return _towardRoute(
          bearing, to, speedKmh, navigating, routeLocalBearingAt);
    }
    _pendingReversal = bearing;
    return current;
  }

  /// Eases [heading] toward the route's own direction while driving on it.
  ///
  /// At an urban junction the two-fix bearing is at its noisiest, and the
  /// route is the one thing known to be right there: a large disagreement is
  /// snapped to it, a small one is approached gradually so real turns still
  /// look like turns rather than steps.
  double _towardRoute(
    double heading,
    LatLng at,
    double speedKmh,
    bool navigating,
    ({double distM, double bearing})? Function(LatLng)? routeLocalBearingAt,
  ) {
    if (!navigating || speedKmh <= _smoothingMinSpeedKmh) return heading;
    final local = routeLocalBearingAt?.call(at);
    if (local == null || local.distM > _onRouteM) return heading;
    var delta = (local.bearing - heading) % 360;
    if (delta > 180) delta -= 360;
    if (delta < -180) delta += 360;
    if (delta.abs() > _snapToRouteDeg) return local.bearing;
    return (heading + delta * _smoothingFactor + 360) % 360;
  }

  static double _reliabilityFloor(double accuracyM) =>
      accuracyM * 0.8 > _minMoveM ? accuracyM * 0.8 : _minMoveM;

  /// True when the route is known, the fix is on it, and [bearing] opposes it
  /// — and the safety valve has not run out.
  bool _contradictsRoute(
    double bearing,
    LatLng at,
    ({double distM, double bearing})? Function(LatLng)? routeLocalBearingAt,
  ) {
    if (_heldTicks >= _maxHeldTicks) return false;
    final local = routeLocalBearingAt?.call(at);
    if (local == null || local.distM > _onRouteM) return false;
    return angleBetween(bearing, local.bearing) > _againstRouteDeg;
  }

  /// Smallest angle between two bearings, 0–180°.
  static double angleBetween(double a, double b) {
    final diff = (a - b).abs() % 360;
    return diff > 180 ? 360 - diff : diff;
  }
}
