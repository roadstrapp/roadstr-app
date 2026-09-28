import 'dart:math' as math;

import '../utils/geo.dart';
import 'routing_response_protocol.dart';

enum RoutingAvoidanceMode { highwaysAndTolls, offRoad }

enum RoutingAvoidanceAttempt { hard, soft, tracks }

enum RoutingAvoidanceAttemptOutcome { success, routingFailure }

typedef RoutingAvoidanceDecision = ({
  RoutingAvoidanceAttempt? nextAttempt,
  RouteResult? finalRoute,
  bool propagateFailure,
});

/// Socket-free orchestration for one Valhalla avoidance request.
///
/// Highway/toll avoidance first requires a hard exclusion and retries once
/// with the documented soft preference only when that attempt cannot produce
/// an acceptable route. Track avoidance is one terminal preference request.
/// Duplicate, premature and post-terminal outcomes are ignored.
class RoutingAvoidanceProtocol {
  RoutingAvoidanceProtocol(RoutingAvoidanceMode mode)
      : _expectedAttempt = mode == RoutingAvoidanceMode.highwaysAndTolls
            ? RoutingAvoidanceAttempt.hard
            : RoutingAvoidanceAttempt.tracks;

  RoutingAvoidanceAttempt _expectedAttempt;
  bool _completed = false;

  RoutingAvoidanceAttempt get initialAttempt => _expectedAttempt;
  bool get isCompleted => _completed;

  RoutingAvoidanceDecision accept(
    RoutingAvoidanceAttempt attempt,
    RoutingAvoidanceAttemptOutcome outcome, [
    RouteResult? route,
  ]) {
    if (_completed || attempt != _expectedAttempt) return _none;

    if (attempt == RoutingAvoidanceAttempt.hard &&
        outcome == RoutingAvoidanceAttemptOutcome.routingFailure) {
      _expectedAttempt = RoutingAvoidanceAttempt.soft;
      return (
        nextAttempt: RoutingAvoidanceAttempt.soft,
        finalRoute: null,
        propagateFailure: false,
      );
    }

    _completed = true;
    if (outcome == RoutingAvoidanceAttemptOutcome.routingFailure) {
      return (
        nextAttempt: null,
        finalRoute: null,
        propagateFailure: true,
      );
    }
    if (route == null) {
      throw StateError('A successful avoidance attempt requires a route');
    }
    return (
      nextAttempt: null,
      finalRoute: route,
      propagateFailure: false,
    );
  }

  static const RoutingAvoidanceDecision _none = (
    nextAttempt: null,
    finalRoute: null,
    propagateFailure: false,
  );
}

class RoutingRetimePlan {
  const RoutingRetimePlan({
    required this.cumulativeDistancesM,
    required this.seaCrossings,
    required this.sampleIndices,
    required this.waypoints,
    required this.shapeLengthM,
  });

  final List<double> cumulativeDistancesM;
  final List<int> seaCrossings;
  final List<int> sampleIndices;
  final String waypoints;
  final double shapeLengthM;
}

/// Pure OSRM re-timing policy shared with the native rewrite.
///
/// It samples the accepted Valhalla geometry at roughly 5 km intervals, then
/// admits OSRM's duration independently for each slice whose distance proves
/// that both engines followed the same road. Unverified slices retain their
/// proportional Valhalla time; ferry slices are deliberately never timed by
/// the road router. If less than half of the drivable shape is verified, the
/// original route is returned unchanged.
class RoutingRetimePolicy {
  const RoutingRetimePolicy._();

  static const spacingM = 5000.0;
  static const maxWaypoints = 120;
  static const maxRoadStepM = 25000.0;

  static RoutingRetimePlan? buildPlan(RouteResult route) {
    final line = route.polyline;
    if (line.length < 2 || route.totalDistanceM <= 0) return null;

    final cumulative = List<double>.filled(line.length, 0);
    final crossings = List<int>.filled(line.length, 0);
    for (var i = 1; i < line.length; i++) {
      final step = Geo.distanceM(line[i - 1], line[i]);
      if (!step.isFinite) return null;
      cumulative[i] = cumulative[i - 1] + step;
      crossings[i] = crossings[i - 1] + (step > maxRoadStepM ? 1 : 0);
    }
    final shapeLength = cumulative.last;
    if (!shapeLength.isFinite || shapeLength <= 0) return null;

    final sampleCount = (shapeLength / spacingM).round().clamp(
              3,
              maxWaypoints - 1,
            ) +
        1;
    final indices = List<int>.unmodifiable([
      for (var i = 0; i < sampleCount; i++)
        (i * (line.length - 1) / (sampleCount - 1)).round(),
    ]);
    final waypoints = indices
        .map((i) => '${line[i].longitude.toStringAsFixed(5)},'
            '${line[i].latitude.toStringAsFixed(5)}')
        .join(';');

    return RoutingRetimePlan(
      cumulativeDistancesM: List<double>.unmodifiable(cumulative),
      seaCrossings: List<int>.unmodifiable(crossings),
      sampleIndices: indices,
      waypoints: waypoints,
      shapeLengthM: shapeLength,
    );
  }

  static RouteResult apply(
    RouteResult route,
    RoutingRetimePlan plan,
    List<OsrmRetimeLeg>? legs,
  ) {
    if (legs == null || legs.length != plan.sampleIndices.length - 1) {
      return route;
    }

    var seconds = 0.0;
    var verifiedM = 0.0;
    var ferryM = 0.0;
    for (var i = 0; i < legs.length; i++) {
      final start = plan.sampleIndices[i];
      final end = plan.sampleIndices[i + 1];
      final arcM =
          plan.cumulativeDistancesM[end] - plan.cumulativeDistancesM[start];
      if (arcM <= 0) continue;

      final legM = legs[i].distanceM;
      final legS = legs[i].durationS;
      if (legM == null || legS == null || !legM.isFinite || !legS.isFinite) {
        return route;
      }

      final crossesWater = plan.seaCrossings[end] > plan.seaCrossings[start];
      if (!crossesWater && (legM - arcM).abs() <= math.max(150.0, 0.2 * arcM)) {
        seconds += legS;
        verifiedM += arcM;
      } else {
        seconds += route.totalDurationS * arcM / plan.shapeLengthM;
        if (crossesWater) ferryM += arcM;
      }
    }

    final roadLength = plan.shapeLengthM - ferryM;
    if (verifiedM < 0.5 * roadLength || seconds <= 0) return route;

    return RouteResult(
      polyline: route.polyline,
      steps: route.steps,
      totalDistanceM: route.totalDistanceM,
      totalDurationS: seconds,
      speedLimits: route.speedLimits,
      avoidance: route.avoidance,
      fromAvoidanceRouter: true,
    );
  }
}
