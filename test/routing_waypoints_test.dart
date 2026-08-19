import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_service.dart';

/// A minimal OSRM reply, enough for the parser to accept it.
String _osrmBody() => jsonEncode({
      'code': 'Ok',
      'routes': [
        {
          'distance': 1000.0,
          'duration': 120.0,
          'geometry': {
            'coordinates': [
              [9.0, 45.0],
              [9.1, 45.1],
            ],
          },
          'legs': [
            {
              'steps': [
                {
                  'maneuver': {
                    'type': 'depart',
                    'modifier': 'straight',
                    'location': [9.0, 45.0],
                  },
                  'name': 'Start Road',
                  'distance': 500.0,
                },
                {
                  'maneuver': {
                    'type': 'arrive',
                    'modifier': 'straight',
                    'location': [9.1, 45.1],
                  },
                  'name': 'End Road',
                  'distance': 0.0,
                },
              ],
            },
          ],
        },
      ],
    });

void main() {
  late HttpServer server;
  late List<String> requested;

  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    requested = [];
    server.listen((req) async {
      requested.add(req.uri.toString());
      req.response.headers.contentType = ContentType.json;
      req.response.write(_osrmBody());
      await req.response.close();
    });
  });

  tearDown(() => server.close(force: true));

  Uri endpoint() =>
      Uri.parse('http://${server.address.host}:${server.port}/route');

  const origin = LatLng(45.0, 9.0);
  const destination = LatLng(45.5, 9.5);

  group('waypoints in the request', () {
    test('a plain journey sends only origin and destination', () async {
      await RoutingService.getRoutes(origin, destination, endpoint: endpoint());
      expect(requested.single, contains('9.0,45.0;9.5,45.5'));
    });

    test('stops are inserted between them, in order', () async {
      await RoutingService.getRoutes(
        origin,
        destination,
        via: const [LatLng(45.1, 9.1), LatLng(45.2, 9.2)],
        endpoint: endpoint(),
      );
      // Order is the whole feature: the same three points in a different
      // sequence are a different journey, which is what dragging them
      // rearranges.
      expect(requested.single, contains('9.0,45.0;9.1,45.1;9.2,45.2;9.5,45.5'));
    });

    test('reordering the stops changes the request', () async {
      await RoutingService.getRoutes(origin, destination,
          via: const [LatLng(45.1, 9.1), LatLng(45.2, 9.2)],
          endpoint: endpoint());
      final first = requested.single;
      requested.clear();

      await RoutingService.getRoutes(origin, destination,
          via: const [LatLng(45.2, 9.2), LatLng(45.1, 9.1)],
          endpoint: endpoint());
      expect(requested.single, isNot(first));
    });

    test('more stops than allowed are dropped rather than sent', () async {
      await RoutingService.getRoutes(
        origin,
        destination,
        via: const [
          LatLng(45.1, 9.1),
          LatLng(45.2, 9.2),
          LatLng(45.3, 9.3),
          LatLng(45.4, 9.4),
          LatLng(45.45, 9.45), // one past the ceiling
        ],
        endpoint: endpoint(),
      );
      expect(requested.single, isNot(contains('9.45,45.45')));
      expect(requested.single, contains('9.4,45.4'));
    });
  });

  group('alternatives', () {
    test('are requested for a plain journey', () async {
      await RoutingService.getRoutes(origin, destination, endpoint: endpoint());
      expect(requested.single, contains('alternatives=3'));
    });

    test('are not requested once the route is pinned through a stop', () async {
      // The router declines to compute alternatives for a multi-stop journey,
      // so asking only buys a round trip to be refused.
      await RoutingService.getRoutes(origin, destination,
          via: const [LatLng(45.1, 9.1)], endpoint: endpoint());
      expect(requested.single, isNot(contains('alternatives')));
    });
  });

  group('the reply still parses', () {
    test('a journey with stops returns a usable route', () async {
      final routes = await RoutingService.getRoutes(origin, destination,
          via: const [LatLng(45.1, 9.1)], endpoint: endpoint());
      expect(routes, isNotEmpty);
      expect(routes.first.polyline, isNotEmpty);
      expect(routes.first.steps, isNotEmpty);
    });
  });

  group('the ceiling', () {
    test('leaves room for five points including the destination', () {
      // The planner offers five search bars in total; four of them are stops.
      expect(RoutingService.maxWaypoints, 4);
    });
  });
}
