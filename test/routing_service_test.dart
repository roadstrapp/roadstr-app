import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_service.dart';

void main() {
  late HttpServer server;

  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  });

  tearDown(() => server.close(force: true));

  Uri endpoint() =>
      Uri.parse('http://${server.address.host}:${server.port}/route');

  test('requests hard motorway/toll exclusions and marks verified route',
      () async {
    const points = [LatLng(45.0, 9.0), LatLng(45.001, 9.002)];
    server.listen((request) async {
      final payload = jsonDecode(request.uri.queryParameters['json']!)
          as Map<String, dynamic>;
      final auto = (payload['costing_options'] as Map<String, dynamic>)['auto']
          as Map<String, dynamic>;
      expect(auto['exclude_highways'], isTrue);
      expect(auto['exclude_tolls'], isTrue);
      expect(payload['language'], 'it-IT');

      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({
        'trip': {
          'status': 0,
          'summary': {
            'has_highway': false,
            'has_toll': false,
            'length': 0.25,
            'time': 42.0,
          },
          'legs': [
            {
              'shape': _encodePolyline6(points),
              'maneuvers': [
                {
                  'type': 1,
                  'instruction': 'Parti.',
                  'length': 0.25,
                  'begin_shape_index': 0,
                },
                {
                  'type': 4,
                  'instruction': 'Sei arrivato.',
                  'length': 0.0,
                  'begin_shape_index': 1,
                },
              ],
            },
          ],
        },
      }));
      await request.response.close();
    });

    final route = await RoutingService.getHighwayAndTollAvoidanceRoute(
      points.first,
      points.last,
      lang: 'it',
      endpoint: endpoint(),
    );

    expect(route.avoidsHighwaysAndTolls, isTrue);
    expect(route.avoidance, RouteAvoidance.highwayAndTollFree);
    expect(route.totalDistanceM, 250);
    expect(route.totalDurationS, 42);
    expect(route.polyline, hasLength(2));
    expect(route.polyline.last.latitude, closeTo(points.last.latitude, 1e-6));
    expect(route.polyline.last.longitude, closeTo(points.last.longitude, 1e-6));
    expect(route.steps.last.direction, 'arrive');
  });

  test('falls back to soft penalties and reports an unavoidable section',
      () async {
    const points = [LatLng(45.0, 9.0), LatLng(45.001, 9.002)];
    var requests = 0;
    server.listen((request) async {
      requests++;
      final payload = jsonDecode(request.uri.queryParameters['json']!)
          as Map<String, dynamic>;
      final auto = (payload['costing_options'] as Map<String, dynamic>)['auto']
          as Map<String, dynamic>;
      request.response.headers.contentType = ContentType.json;
      if (requests == 1) {
        expect(auto['exclude_highways'], isTrue);
        expect(auto['exclude_tolls'], isTrue);
        request.response.write(jsonEncode({
          'trip': {'status': 171},
        }));
      } else {
        expect(auto['exclude_highways'], isNull);
        expect(auto['exclude_tolls'], isNull);
        expect(auto['use_highways'], 0);
        expect(auto['use_tolls'], 0);
        expect(auto['toll_booth_penalty'], 900);
        request.response.write(jsonEncode({
          'trip': {
            'status': 0,
            'summary': {
              'has_highway': true,
              'has_toll': false,
              'length': 0.25,
              'time': 42.0,
            },
            'legs': [
              {
                'shape': _encodePolyline6(points),
                'maneuvers': [
                  {
                    'type': 1,
                    'instruction': 'Depart.',
                    'length': 0.25,
                    'begin_shape_index': 0,
                  },
                  {
                    'type': 4,
                    'instruction': 'Arrive.',
                    'length': 0.0,
                    'begin_shape_index': 1,
                  },
                ],
              },
            ],
          },
        }));
      }
      await request.response.close();
    });

    final route = await RoutingService.getHighwayAndTollAvoidanceRoute(
      points.first,
      points.last,
      endpoint: endpoint(),
    );

    expect(requests, 2);
    expect(route.isHighwayAndTollAvoidance, isTrue);
    expect(route.avoidsHighwaysAndTolls, isFalse);
    expect(route.avoidance, RouteAvoidance.minimizedHighwaysAndTolls);
  });

  test('rejects malformed responses from both avoidance attempts', () async {
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({
        'trip': {
          'status': 0,
          'summary': {
            'has_highway': true,
            'has_toll': false,
            'length': 1,
            'time': 60,
          },
          'legs': const [],
        },
      }));
      await request.response.close();
    });

    await expectLater(
      RoutingService.getHighwayAndTollAvoidanceRoute(
        const LatLng(45, 9),
        const LatLng(45.01, 9.01),
        endpoint: endpoint(),
      ),
      throwsA(isA<RoutingException>()),
    );
  });
}

String _encodePolyline6(List<LatLng> points) {
  final out = StringBuffer();
  var lastLat = 0;
  var lastLon = 0;
  for (final point in points) {
    final lat = (point.latitude * 1e6).round();
    final lon = (point.longitude * 1e6).round();
    _encodeDelta(out, lat - lastLat);
    _encodeDelta(out, lon - lastLon);
    lastLat = lat;
    lastLon = lon;
  }
  return out.toString();
}

void _encodeDelta(StringBuffer out, int delta) {
  var value = delta < 0 ? ~(delta << 1) : delta << 1;
  while (value >= 0x20) {
    out.writeCharCode((0x20 | (value & 0x1f)) + 63);
    value >>= 5;
  }
  out.writeCharCode(value + 63);
}
