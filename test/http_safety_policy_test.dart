import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/http_safety_policy.dart';

import '../tools/kotlin_rewrite/generate_http_safety_fixture.dart' as fixture;

void main() {
  test('shared HTTP safety fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildHttpSafetyFixture(),
    );
  });

  test('streaming body accounting accepts the exact byte ceiling', () {
    expect(
      BoundedHttpPolicy.acceptsChunk(
        receivedBytes: 3,
        chunkBytes: 5,
        maxBytes: 8,
      ),
      isTrue,
    );
    expect(
      BoundedHttpPolicy.acceptsChunk(
        receivedBytes: 8,
        chunkBytes: 1,
        maxBytes: 8,
      ),
      isFalse,
    );
  });

  test('invalid response budgets fail before networking', () {
    expect(
      () => BoundedHttpPolicy.acceptsContentLength(0, 0),
      throwsArgumentError,
    );
  });

  test('GraphHopper policy preserves the shipped cleartext decision', () {
    expect(
      RoutingEndpointPolicy.graphHopperDecision(
        'https://graphhopper.example.com/route',
      ),
      RoutingEndpointDecision.accepted,
    );
    expect(
      RoutingEndpointPolicy.graphHopperDecision(
        'http://localhost:8989/route',
      ),
      RoutingEndpointDecision.accepted,
    );
    expect(
      RoutingEndpointPolicy.graphHopperDecision(
        'http://192.168.1.50:8989/route',
      ),
      RoutingEndpointDecision.cleartextRejected,
    );
    expect(
      RoutingEndpointPolicy.graphHopperDecision(
        'https://user@graphhopper.example.com/route',
      ),
      RoutingEndpointDecision.accepted,
    );
  });
}
