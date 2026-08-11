import 'package:latlong2/latlong.dart';

import 'geo.dart';

/// Which way the map should face, decided from consecutive GPS fixes.
///
/// Two questions, both answered here so the answers cannot drift apart:
///
///   * *which way* is the vehicle pointing — [resolve], from two positions
///     (dead reckoning) with the provider's course-over-ground as a fallback;
///   * *is it moving at all* — [isMoving], which is what decides whether the
///     cursor follows that course or the magnetometer.
///
/// Neither answer ever comes from the magnetometer, which reports how the
/// phone is held: in a cradle turned sideways, or in a pocket, that is
/// perpendicular to the road. The compass is only meaningful standing still,
/// where there is no course to speak of.
///
/// Two things make movement bearing harder than subtracting coordinates, and
/// both are why this is a class with memory rather than a formula:
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
  static const minTravelHeadingSpeedKmh = 3.0;

  /// Speeds at which the vehicle's course takes over from the phone's compass,
  /// and hands back.
  ///
  /// Two values rather than one because a single threshold is not a decision,
  /// it is a coin toss repeated twice a second: a car crawling in traffic sits
  /// exactly on it, and the heading source flips between GPS course and
  /// magnetometer on every fix. On screen that is a map that cannot make up
  /// its mind which way to face. The gap between the two is the hysteresis.
  static const movingEnterKmh = 5.0;
  static const movingExitKmh = 2.0;

  /// How long a "moving" verdict survives without a fresh fix. The stream
  /// simply stops in a tunnel, in an underground car park, or when the
  /// permission is revoked; without an expiry the last verdict would stand
  /// forever and the compass would never take over again.
  static const _motionStaleness = Duration(seconds: 6);

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
  bool _moving = false;
  DateTime? _lastMotionAt;

  /// A route-local bearing that disagreed sharply with an already-trusted GPS
  /// bearing, waiting for a second fix to confirm it before [_towardRoute]
  /// acts on it. See the field's use for why this exists.
  double? _pendingRouteSnap;

  /// Forgets the pending reversal. Call when a journey starts or ends.
  ///
  /// The motion state is deliberately *not* cleared: whether the vehicle is
  /// moving is a fact about the world, not about the journey, and starting
  /// navigation while already driving must not hand the heading back to the
  /// magnetometer for the first few fixes.
  void reset() {
    _pendingReversal = null;
    _heldTicks = 0;
    _pendingRouteSnap = null;
  }

  /// Feeds one speed sample and returns whether the device counts as moving.
  /// Call exactly once per GPS fix — it advances the hysteresis.
  bool updateMotion(double speedKmh) {
    _lastMotionAt = DateTime.now();
    if (!speedKmh.isFinite || speedKmh < 0) return _moving = false;
    if (_moving) {
      if (speedKmh < movingExitKmh) _moving = false;
    } else if (speedKmh > movingEnterKmh) {
      _moving = true;
    }
    return _moving;
  }

  /// Whether the vehicle is moving, without disturbing the hysteresis.
  ///
  /// This is the read side for everything that is not the GPS stream — the
  /// magnetometer callback and the widget tree — so that exactly one caller
  /// advances the state and the rest only observe it.
  bool get isMoving {
    final last = _lastMotionAt;
    if (last == null) return false;
    if (DateTime.now().difference(last) > _motionStaleness) return false;
    return _moving;
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
            // Geolocator maps an unavailable Android bearing to 0.0, which is
            // indistinguishable from true north. Hold the last course until
            // the position baseline is long enough to calculate north safely.
            providerHeading > 0)
        ? providerHeading % 360
        : current;
    if (from == null || !usesTravelHeading(speedKmh)) return fallback;
    if (!hasReliableMovement(from, to, accuracyM)) {
      return fallback;
    }

    final bearing = Geo.bearingBetween(from, to);
    if (!bearing.isFinite) return current;

    // An ordinary change of direction: take it.
    if (!navigating || angleBetween(bearing, current) <= _reversalDeg) {
      _resetReversal();
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
      _resetReversal();
      return _towardRoute(
          bearing, to, speedKmh, navigating, routeLocalBearingAt);
    }
    _pendingReversal = bearing;
    return current;
  }

  /// Clears only the reversal-detection state. Used on every accepted fix —
  /// as opposed to [reset], which is the per-journey entry point and also
  /// forgets [_pendingRouteSnap]. That corroboration state has to survive
  /// across ordinary accepted fixes to do its job: it is asking "did the last
  /// fix *and* this one both point at the same unexpected route bearing", and
  /// both fixes take the "ordinary change of direction" branch above, which
  /// runs every single GPS tick.
  void _resetReversal() {
    _pendingReversal = null;
    _heldTicks = 0;
  }

  /// Eases [heading] toward the route's own direction while driving on it.
  ///
  /// At an urban junction the two-fix bearing is at its noisiest, and the
  /// route is the one thing known to be right there: a large disagreement is
  /// snapped to it, a small one is approached gradually so real turns still
  /// look like turns rather than steps.
  ///
  /// [routeLocalBearingAt] answers with the nearest polyline *segment* by raw
  /// distance alone, with no idea which way the road actually runs there.
  /// Around a roundabout, or at a junction where two arms both pass within
  /// the on-route radius, the nearest segment can belong to a different arm
  /// than the one the driver is on — reporting a bearing pointing the wrong
  /// way, sometimes close to the reverse of the real one. [heading] going in
  /// is the two-fix GPS bearing, already checked against a reversal by the
  /// caller; snapping it away on one disagreeing sample would let a single
  /// bad nearest-segment match overrule a value that had already earned some
  /// trust. So a large disagreement is held rather than acted on until the
  /// same route bearing repeats on the next fix — a real sharp turn keeps
  /// producing it, a stray match by the roundabout ring does not.
  double _towardRoute(
    double heading,
    LatLng at,
    double speedKmh,
    bool navigating,
    ({double distM, double bearing})? Function(LatLng)? routeLocalBearingAt,
  ) {
    if (!navigating || speedKmh <= _smoothingMinSpeedKmh) {
      _pendingRouteSnap = null;
      return heading;
    }
    final local = routeLocalBearingAt?.call(at);
    if (local == null || local.distM > _onRouteM) {
      _pendingRouteSnap = null;
      return heading;
    }
    var delta = (local.bearing - heading) % 360;
    if (delta > 180) delta -= 360;
    if (delta < -180) delta += 360;
    if (delta.abs() > _snapToRouteDeg) {
      final pending = _pendingRouteSnap;
      if (pending != null &&
          angleBetween(local.bearing, pending) <= _agreementDeg) {
        _pendingRouteSnap = null;
        return local.bearing;
      }
      _pendingRouteSnap = local.bearing;
      return heading;
    }
    _pendingRouteSnap = null;
    return (heading + delta * _smoothingFactor + 360) % 360;
  }

  /// Whether two fixes at this speed can yield a bearing at all. This is the
  /// dead-reckoning floor used inside [resolve] — to decide which *source*
  /// should drive the cursor, use [isMoving], which has hysteresis.
  static bool usesTravelHeading(double speedKmh) =>
      speedKmh.isFinite && speedKmh > minTravelHeadingSpeedKmh;

  /// Consecutive high-rate fixes are often less than the noise floor apart.
  /// Callers use this to retain the last useful origin until enough movement
  /// has accumulated instead of resetting the baseline on every tiny sample.
  static bool hasReliableMovement(LatLng from, LatLng to, double accuracyM) =>
      Geo.distanceM(from, to) > _reliabilityFloor(accuracyM);

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
