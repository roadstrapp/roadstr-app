import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_avoidance_protocol.dart';
import 'package:roadstr/services/routing_service.dart';

import '../tools/kotlin_rewrite/generate_routing_avoidance_fixture.dart'
    as fixture;

RouteResult _route({double distanceM = 3000, double durationS = 300}) =>
    RouteResult(
      polyline: const [
        LatLng(0, 0),
        LatLng(0, 0.00898311175),
        LatLng(0, 0.0179662235),
        LatLng(0, 0.02694933525),
      ],
      steps: const [
        RouteStep(
          instruction: 'Continue',
          direction: 'continue',
          distanceM: 3000,
          location: LatLng(0, 0),
        ),
      ],
      totalDistanceM: distanceM,
      totalDurationS: durationS,
      speedLimits: const [(distFromStartM: 0, speedKmh: 50)],
      avoidance: RouteAvoidance.highwayAndTollFree,
    );

void main() {
  test('shared routing avoidance fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildRoutingAvoidanceFixture(),
    );
  });

  test('retiming preserves all route payload except duration and provenance',
      () {
    final route = _route();
    final plan = RoutingRetimePolicy.buildPlan(route)!;
    final result = RoutingRetimePolicy.apply(
      route,
      plan,
      const [
        OsrmRetimeLeg(distanceM: 1000, durationS: 40),
        OsrmRetimeLeg(distanceM: 1000, durationS: 50),
        OsrmRetimeLeg(distanceM: 1000, durationS: 60),
      ],
    );

    expect(result.totalDurationS, closeTo(150, 1e-6));
    expect(result.fromAvoidanceRouter, isTrue);
    expect(identical(result.polyline, route.polyline), isTrue);
    expect(identical(result.steps, route.steps), isTrue);
    expect(identical(result.speedLimits, route.speedLimits), isTrue);
    expect(result.totalDistanceM, route.totalDistanceM);
    expect(result.avoidance, route.avoidance);
  });

  test('retiming plans expose immutable sampling state', () {
    final plan = RoutingRetimePolicy.buildPlan(_route())!;
    expect(() => plan.sampleIndices.clear(), throwsUnsupportedError);
    expect(() => plan.cumulativeDistancesM.clear(), throwsUnsupportedError);
    expect(() => plan.seaCrossings.clear(), throwsUnsupportedError);
  });

  test('executor retries hard routing failure, then retimes exactly once',
      () async {
    final attempts = <RoutingAvoidanceAttempt>[];
    var retimes = 0;
    final result = await RoutingService.orchestrateAvoidance(
      mode: RoutingAvoidanceMode.highwaysAndTolls,
      request: (attempt) async {
        attempts.add(attempt);
        if (attempt == RoutingAvoidanceAttempt.hard) {
          throw RoutingException(message: 'hard exclusion disconnected');
        }
        return _route(distanceM: 202);
      },
      retime: (route) async {
        retimes++;
        return route;
      },
    );

    expect(
        attempts, [RoutingAvoidanceAttempt.hard, RoutingAvoidanceAttempt.soft]);
    expect(result.totalDistanceM, 202);
    expect(retimes, 1);
  });

  test('executor sends off-road avoidance directly to tracks', () async {
    final attempts = <RoutingAvoidanceAttempt>[];
    await RoutingService.orchestrateAvoidance(
      mode: RoutingAvoidanceMode.offRoad,
      request: (attempt) async {
        attempts.add(attempt);
        return _route();
      },
      retime: (route) async => route,
    );
    expect(attempts, [RoutingAvoidanceAttempt.tracks]);
  });

  test('soft routing failure and non-routing failures propagate', () async {
    final routingAttempts = <RoutingAvoidanceAttempt>[];
    await expectLater(
      RoutingService.orchestrateAvoidance(
        mode: RoutingAvoidanceMode.highwaysAndTolls,
        request: (attempt) async {
          routingAttempts.add(attempt);
          throw RoutingException(message: 'unavailable');
        },
        retime: (route) async => route,
      ),
      throwsA(isA<RoutingException>()),
    );
    expect(routingAttempts,
        [RoutingAvoidanceAttempt.hard, RoutingAvoidanceAttempt.soft]);

    final programmingAttempts = <RoutingAvoidanceAttempt>[];
    await expectLater(
      RoutingService.orchestrateAvoidance(
        mode: RoutingAvoidanceMode.highwaysAndTolls,
        request: (attempt) async {
          programmingAttempts.add(attempt);
          throw StateError('caller cancelled');
        },
        retime: (route) async => route,
      ),
      throwsStateError,
    );
    expect(programmingAttempts, [RoutingAvoidanceAttempt.hard]);
  });
}
