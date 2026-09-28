import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_orchestration_protocol.dart';
import 'package:roadstr/services/routing_service.dart';

import '../tools/kotlin_rewrite/generate_routing_orchestration_fixture.dart'
    as fixture;

RouteResult _route(double distanceM) => RouteResult(
      polyline: const [LatLng(45, 9), LatLng(45.01, 9.01)],
      steps: const [],
      totalDistanceM: distanceM,
      totalDurationS: 60,
    );

void main() {
  test('shared routing orchestration fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildRoutingOrchestrationFixture(),
    );
  });

  test('only moving OSRM with a bearing starts constrained', () {
    RoutingRouteAttempt initial(
      RoutingProvider provider,
      double speed,
      double? bearing,
    ) =>
        RoutingOrchestrationProtocol(
          provider: provider,
          speedKmh: speed,
          originBearingDegrees: bearing,
          straightLineDistanceM: 1000,
        ).initialAttempt;

    expect(
      initial(RoutingProvider.osrm, 3.01, 90),
      RoutingRouteAttempt.constrained,
    );
    expect(
      initial(RoutingProvider.osrm, 3, 90),
      RoutingRouteAttempt.unconstrained,
    );
    expect(
      initial(RoutingProvider.osrm, 30, null),
      RoutingRouteAttempt.unconstrained,
    );
    expect(
      initial(RoutingProvider.openRoute, 30, 90),
      RoutingRouteAttempt.unconstrained,
    );
  });

  test('terminal routes are immutable and late outcomes are ignored', () {
    final state = RoutingOrchestrationProtocol(
      provider: RoutingProvider.osrm,
      speedKmh: 30,
      originBearingDegrees: 90,
      straightLineDistanceM: 1000,
    );
    final accepted = state.accept(
      RoutingRouteAttempt.constrained,
      RoutingAttemptOutcome.success,
      [_route(2000)],
    );
    expect(() => accepted.finalRoutes!.clear(), throwsUnsupportedError);

    final late = state.accept(
      RoutingRouteAttempt.unconstrained,
      RoutingAttemptOutcome.success,
      [_route(999)],
    );
    expect(late.nextAttempt, isNull);
    expect(late.finalRoutes, isNull);
    expect(late.propagateFailure, isFalse);
    expect(state.isCompleted, isTrue);
  });

  test('executor retries a constrained routing failure without a bearing',
      () async {
    final bearings = <double?>[];
    final routes = await RoutingService.orchestrateReroute(
      provider: RoutingProvider.osrm,
      speedKmh: 30,
      originBearingDeg: 273,
      straightLineDistanceM: 1000,
      request: (bearing) async {
        bearings.add(bearing);
        if (bearing != null) {
          throw RoutingException(message: 'no edge matches the bearing');
        }
        return [_route(2200)];
      },
    );

    expect(bearings, [273, null]);
    expect(routes.single.totalDistanceM, 2200);
  });

  test('executor retries an implausible constrained result exactly once',
      () async {
    final bearings = <double?>[];
    final routes = await RoutingService.orchestrateReroute(
      provider: RoutingProvider.osrm,
      speedKmh: 30,
      originBearingDeg: 90,
      straightLineDistanceM: 1000,
      request: (bearing) async {
        bearings.add(bearing);
        return bearing == null ? [_route(2500)] : [_route(13001)];
      },
    );

    expect(bearings, [90, null]);
    expect(routes.single.totalDistanceM, 2500);
  });

  test('a plausible constrained result is terminal and preserves order',
      () async {
    final bearings = <double?>[];
    final routes = await RoutingService.orchestrateReroute(
      provider: RoutingProvider.osrm,
      speedKmh: 30,
      originBearingDeg: 90,
      straightLineDistanceM: 1000,
      request: (bearing) async {
        bearings.add(bearing);
        return [_route(3000), _route(2000)];
      },
    );

    expect(bearings, [90]);
    expect(routes.map((route) => route.totalDistanceM), [3000, 2000]);
  });

  test('non-routing failures propagate without a hidden second request',
      () async {
    final bearings = <double?>[];
    await expectLater(
      RoutingService.orchestrateReroute(
        provider: RoutingProvider.osrm,
        speedKmh: 30,
        originBearingDeg: 90,
        straightLineDistanceM: 1000,
        request: (bearing) async {
          bearings.add(bearing);
          throw StateError('caller timeout');
        },
      ),
      throwsStateError,
    );
    expect(bearings, [90]);
  });

  test('a direct unconstrained routing failure remains terminal', () async {
    final bearings = <double?>[];
    await expectLater(
      RoutingService.orchestrateReroute(
        provider: RoutingProvider.graphHopper,
        speedKmh: 30,
        originBearingDeg: 90,
        straightLineDistanceM: 1000,
        request: (bearing) async {
          bearings.add(bearing);
          throw RoutingException(message: 'provider unavailable');
        },
      ),
      throwsA(isA<RoutingException>()),
    );
    expect(bearings, [null]);
  });
}
