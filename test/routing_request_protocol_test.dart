import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/routing_request_protocol.dart';

import '../tools/kotlin_rewrite/generate_routing_requests_fixture.dart'
    as fixture;

void main() {
  const origin = RoutingRequestPoint(45.0703, 7.6869);
  const destination = RoutingRequestPoint(45.4642, 9.19);

  test('shared routing request fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildRoutingRequestsFixture(),
    );
  });

  test('OSRM caps stops, suppresses alternatives and normalizes bearing', () {
    final request = RoutingRequestProtocol.osrmRoute(
      origin: origin,
      destination: destination,
      vehicle: 'driving',
      via: const [
        RoutingRequestPoint(45.1, 8.1),
        RoutingRequestPoint(45.2, 8.2),
        RoutingRequestPoint(45.3, 8.3),
        RoutingRequestPoint(45.4, 8.4),
        RoutingRequestPoint(45.5, 8.5),
      ],
      requestAlternatives: true,
      originBearingDegrees: -0.5,
    );
    expect(request.uri.toString(), contains('8.4,45.4'));
    expect(request.uri.toString(), isNot(contains('8.5,45.5')));
    expect(request.uri.queryParameters.containsKey('alternatives'), isFalse);
    expect(request.uri.toString(), contains('bearings=359,45;'));
  });

  test('GraphHopper sends keys only to the exact public endpoint', () {
    final public = RoutingRequestProtocol.graphHopperRoute(
      origin: origin,
      destination: destination,
      server: RoutingRequestProtocol.graphHopperPublicEndpoint,
      languageCode: 'it',
      vehicle: 'driving',
      apiKey: 'key +&?',
    );
    expect(public.uri.queryParameters['key'], 'key +&?');

    final selfHosted = RoutingRequestProtocol.graphHopperRoute(
      origin: origin,
      destination: destination,
      server: 'https://routes.example/route',
      languageCode: 'it',
      vehicle: 'driving',
      apiKey: 'must-not-leak',
    );
    expect(selfHosted.uri.queryParameters.containsKey('key'), isFalse);
  });

  test('ORS and Valhalla preserve provider-specific language mappings', () {
    expect(RoutingRequestProtocol.openRouteServiceLanguage('EL'), 'gr');
    expect(RoutingRequestProtocol.openRouteServiceLanguage('uk'), 'ua');
    expect(RoutingRequestProtocol.openRouteServiceLanguage('xx'), 'en');
    expect(RoutingRequestProtocol.valhallaLanguage('PT'), 'pt-BR');
    expect(RoutingRequestProtocol.valhallaLanguage('xx'), 'en-US');
  });

  test('Valhalla hard, soft and track policies remain distinct', () {
    Map<String, dynamic> options(ValhallaCostingPolicy policy) {
      final request = RoutingRequestProtocol.valhalla(
        origin: origin,
        destination: destination,
        languageCode: 'it',
        costingPolicy: policy,
      );
      final payload = jsonDecode(request.uri.queryParameters['json']!)
          as Map<String, dynamic>;
      return (payload['costing_options'] as Map<String, dynamic>)['auto']
          as Map<String, dynamic>;
    }

    expect(
      options(ValhallaCostingPolicy.hardHighwayAndTollExclusion),
      {'exclude_highways': true, 'exclude_tolls': true},
    );
    expect(
      options(ValhallaCostingPolicy.softHighwayAndTollAvoidance),
      {'use_highways': 0, 'use_tolls': 0, 'toll_booth_penalty': 900},
    );
    expect(
      options(ValhallaCostingPolicy.avoidTracks),
      {'use_tracks': 0},
    );
  });
}
