import '../utils/heading_filter.dart';
import 'routing_response_protocol.dart';

/// Routing back-end selected in Settings and persisted by enum name.
enum RoutingProvider { osrm, openRoute, graphHopper }

enum RoutingRouteAttempt { constrained, unconstrained }

enum RoutingAttemptOutcome { success, routingFailure }

typedef RoutingOrchestrationDecision = ({
  RoutingRouteAttempt? nextAttempt,
  List<RouteResult>? finalRoutes,
  bool propagateFailure,
});

/// Stateful, socket-free policy for one mid-navigation reroute.
///
/// Only a moving OSRM request with a real bearing starts constrained. A
/// constrained routing failure, empty result or implausible detour is retried
/// once without the bearing. Other providers and stationary/unknown headings
/// go directly to the unconstrained terminal attempt. Duplicate, premature
/// and post-terminal outcomes are ignored.
class RoutingOrchestrationProtocol {
  RoutingOrchestrationProtocol({
    required RoutingProvider provider,
    required double speedKmh,
    required double? originBearingDegrees,
    required this.straightLineDistanceM,
  })  : originBearingDegrees = originBearingDegrees,
        _expectedAttempt = provider == RoutingProvider.osrm &&
                originBearingDegrees != null &&
                HeadingFilter.usesTravelHeading(speedKmh)
            ? RoutingRouteAttempt.constrained
            : RoutingRouteAttempt.unconstrained;

  final double? originBearingDegrees;
  final double straightLineDistanceM;
  RoutingRouteAttempt _expectedAttempt;
  bool _completed = false;

  RoutingRouteAttempt get initialAttempt => _expectedAttempt;
  bool get isCompleted => _completed;

  RoutingOrchestrationDecision accept(
    RoutingRouteAttempt attempt,
    RoutingAttemptOutcome outcome, [
    List<RouteResult> routes = const [],
  ]) {
    if (_completed || attempt != _expectedAttempt) return _none;

    if (attempt == RoutingRouteAttempt.constrained) {
      final retry = outcome == RoutingAttemptOutcome.routingFailure ||
          routes.isEmpty ||
          isImplausibleReroute(
            _shortestDistance(routes),
            straightLineDistanceM,
          );
      if (retry) {
        _expectedAttempt = RoutingRouteAttempt.unconstrained;
        return (
          nextAttempt: RoutingRouteAttempt.unconstrained,
          finalRoutes: null,
          propagateFailure: false,
        );
      }
      _completed = true;
      return (
        nextAttempt: null,
        finalRoutes: List.unmodifiable(routes),
        propagateFailure: false,
      );
    }

    _completed = true;
    if (outcome == RoutingAttemptOutcome.routingFailure) {
      return (
        nextAttempt: null,
        finalRoutes: null,
        propagateFailure: true,
      );
    }
    return (
      nextAttempt: null,
      finalRoutes: List.unmodifiable(routes),
      propagateFailure: false,
    );
  }

  /// Whether a bearing-constrained reroute went somewhere it should not have.
  static bool isImplausibleReroute(
    double routeDistanceM,
    double straightLineDistanceM,
  ) {
    const floorM = 5000.0;
    const factor = 8.0;
    return routeDistanceM > straightLineDistanceM * factor + floorM;
  }

  static double _shortestDistance(List<RouteResult> routes) {
    var shortest = routes.first.totalDistanceM;
    for (final route in routes.skip(1)) {
      if (route.totalDistanceM < shortest) shortest = route.totalDistanceM;
    }
    return shortest;
  }

  static const RoutingOrchestrationDecision _none = (
    nextAttempt: null,
    finalRoutes: null,
    propagateFailure: false,
  );
}
