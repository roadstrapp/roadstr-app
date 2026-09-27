import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_response_protocol.dart';

import '../tools/kotlin_rewrite/generate_routing_responses_fixture.dart'
    as fixture;

void main() {
  test('shared routing response fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildRoutingResponsesFixture(),
    );
  });

  test('route validation coalesces passive names and sanitises decorations',
      () {
    final route = RoutingResponseProtocol.validate(RouteResult(
      polyline: const [LatLng(45, 9), LatLng(45.1, 9.1)],
      steps: const [
        RouteStep(
          instruction: 'Continue',
          direction: 'continue',
          distanceM: 100,
          location: LatLng(45, 9),
          roadName: 'Old name',
          roadRef: 'A1',
        ),
        RouteStep(
          instruction: 'New name',
          direction: 'new name',
          modifier: 'straight',
          distanceM: 50,
          location: LatLng(45.05, 9.05),
        ),
        RouteStep(
          instruction: 'Roundabout',
          direction: 'roundabout',
          distanceM: 100,
          location: LatLng(45.1, 9.1),
          exitNumber: 21,
          roundaboutArmCount: 2,
          exitLabel: '123456789012345678901234567890123',
          roadName: 'Removed with invalid decorations',
          roadRef: 'SS1',
        ),
      ],
      totalDistanceM: 250,
      totalDurationS: 30,
    ));

    expect(route.steps, hasLength(2));
    expect(route.steps.first.distanceM, 150);
    expect(route.steps.first.roadName, 'Old name');
    expect(route.steps.first.roadRef, 'A1');
    expect(route.steps.last.exitNumber, isNull);
    expect(route.steps.last.roundaboutArmCount, isNull);
    expect(route.steps.last.exitLabel, isNull);
    expect(route.steps.last.roadName, isEmpty);
    expect(route.steps.last.roadRef, isEmpty);
  });

  test('route validation rejects invalid coordinates and speed limits', () {
    RouteResult route({
      List<LatLng> points = const [LatLng(45, 9), LatLng(45.1, 9.1)],
      List<SpeedLimitEntry> speeds = const [],
    }) =>
        RouteResult(
          polyline: points,
          steps: const [
            RouteStep(
              instruction: 'Continue',
              direction: 'continue',
              distanceM: 100,
              location: LatLng(45, 9),
            ),
          ],
          totalDistanceM: 100,
          totalDurationS: 10,
          speedLimits: speeds,
        );

    expect(
      () => RoutingResponseProtocol.validate(
        route(points: const [LatLng(91, 9), LatLng(45, 9)]),
      ),
      throwsA(isA<RoutingException>()),
    );
    expect(
      () => RoutingResponseProtocol.validate(
        route(speeds: const [
          (distFromStartM: 50, speedKmh: 30),
          (distFromStartM: 40, speedKmh: 50),
        ]),
      ),
      throwsA(isA<RoutingException>()),
    );
  });

  test('response parser limits and route helpers remain explicit', () {
    expect(RoutingResponseProtocol.maxRoutePoints, 250000);
    expect(RoutingResponseProtocol.maxRouteSteps, 60000);
    expect(kMaxRoundaboutArms, 20);

    const route = RouteResult(
      polyline: [LatLng(45, 9), LatLng(45.1, 9.1)],
      steps: [
        RouteStep(
          instruction: 'Continue',
          direction: 'continue',
          distanceM: 1000,
          location: LatLng(45, 9),
        ),
      ],
      totalDistanceM: 1000,
      totalDurationS: 3660,
      speedLimits: [
        (distFromStartM: 0, speedKmh: 30),
        (distFromStartM: 500, speedKmh: null),
        (distFromStartM: 800, speedKmh: 90),
      ],
    );
    expect(route.durationLabel, '1h 1min');
    expect(route.speedLimitAt(499), 30);
    expect(route.speedLimitAt(500), isNull);
    expect(route.speedLimitAt(999), 90);
  });
}
