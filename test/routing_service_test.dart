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

  test('straight road-name changes are not exposed as fake maneuvers', () {
    const point = LatLng(45, 9);
    final cleaned = RoutingService.coalescePassiveNameChanges([
      const RouteStep(
        instruction: 'Continue on Industrial Road',
        direction: 'continue',
        modifier: 'straight',
        distanceM: 300,
        location: point,
      ),
      const RouteStep(
        instruction: 'Continue on Airport Connector',
        direction: 'new name',
        modifier: 'straight',
        distanceM: 220,
        location: point,
      ),
      const RouteStep(
        instruction: 'Take exit 199',
        direction: 'off ramp',
        modifier: 'right',
        distanceM: 100,
        location: point,
      ),
    ]);

    expect(cleaned, hasLength(2));
    expect(cleaned.first.instruction, 'Continue on Industrial Road');
    expect(cleaned.first.distanceM, 520);
    expect(cleaned.last.direction, 'off ramp');
  });

  test('Valhalla preserves a right motorway exit instead of calling it a turn',
      () async {
    const points = [
      LatLng(45.0, 9.0),
      LatLng(45.001, 9.0),
      LatLng(45.002, 9.001),
      LatLng(45.003, 9.002),
    ];
    server.listen((request) async {
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({
        'trip': {
          'status': 0,
          'summary': {
            'has_highway': false,
            'has_toll': false,
            'length': 0.4,
            'time': 60,
          },
          'legs': [
            {
              'shape': _encodePolyline6(points),
              'maneuvers': [
                {
                  'type': 1,
                  'instruction': 'Start',
                  'length': 0.2,
                  'begin_shape_index': 0,
                },
                {
                  'type': 7,
                  'instruction': 'Continue on renamed road',
                  'length': 0.1,
                  'begin_shape_index': 1,
                },
                {
                  'type': 20,
                  'instruction': 'Take exit 199',
                  'length': 0.1,
                  'begin_shape_index': 2,
                  'sign': {
                    'exit_number_elements': [
                      {'text': '199'}
                    ],
                  },
                },
                {
                  'type': 4,
                  'instruction': 'Arrive',
                  'length': 0,
                  'begin_shape_index': 3,
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
      endpoint: endpoint(),
    );

    expect(route.steps, hasLength(3));
    expect(route.steps.first.distanceM, 300);
    expect(route.steps[1].direction, 'off ramp');
    expect(route.steps[1].modifier, 'right');
    expect(route.steps[1].exitLabel, '199');
  });

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

  group('re-timing through OSRM', () {
    // A straight 1.113 km line: at one waypoint per 5 km it is sampled into
    // the minimum of 4 waypoints, i.e. 3 legs of ~371 m each.
    final shape = [
      for (var i = 0; i <= 100; i++) LatLng(45.0 + i * 0.0001, 9.0),
    ];
    const valhallaSeconds = 3000.0; // 50 minutes for 1.1 km — the sort of
    // figure that made the avoidance card unusable.

    String valhallaBody() => jsonEncode({
          'trip': {
            'status': 0,
            'summary': {
              'has_highway': false,
              'has_toll': false,
              'length': 1.113,
              'time': valhallaSeconds,
            },
            'legs': [
              {
                'shape': _encodePolyline6(shape),
                'maneuvers': [
                  {
                    'type': 1,
                    'instruction': 'Parti.',
                    'length': 1.113,
                    'begin_shape_index': 0,
                  },
                  {
                    'type': 4,
                    'instruction': 'Sei arrivato.',
                    'length': 0.0,
                    'begin_shape_index': 100,
                  },
                ],
              },
            ],
          },
        });

    /// Serves Valhalla on /route and a stubbed OSRM on /osrm, the latter
    /// answering with the given per-leg distances (m) and durations (s).
    void serve(List<double> legDistances, List<double> legDurations) {
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path.startsWith('/osrm')) {
          request.response.write(jsonEncode({
            'code': 'Ok',
            'routes': [
              {
                'distance': legDistances.reduce((a, b) => a + b),
                'duration': legDurations.reduce((a, b) => a + b),
                'legs': [
                  for (var i = 0; i < legDistances.length; i++)
                    {'distance': legDistances[i], 'duration': legDurations[i]},
                ],
              },
            ],
          }));
        } else {
          request.response.write(valhallaBody());
        }
        await request.response.close();
      });
    }

    Future<RouteResult> avoidanceRoute() =>
        RoutingService.getHighwayAndTollAvoidanceRoute(
          shape.first,
          shape.last,
          endpoint: endpoint(),
          retimeEndpoint:
              Uri.parse('http://${server.address.host}:${server.port}/osrm'),
        );

    test('adopts OSRM timing when every leg followed the same road', () async {
      serve([370, 378, 370], [400, 400, 400]);
      final route = await avoidanceRoute();
      expect(route.totalDurationS, 1200); // not Valhalla's 3000
      expect(route.totalDistanceM, 1113); // geometry stays Valhalla's
      expect(route.polyline, hasLength(shape.length));
      expect(route.avoidsHighwaysAndTolls, isTrue);
      expect(route.fromAvoidanceRouter, isTrue);
    });

    test('keeps Valhalla time for the one leg OSRM would not follow', () async {
      // The middle leg is 5 km long where the route only covers 378 m: OSRM
      // left the road there, so that slice keeps Valhalla's share (~1020 s)
      // while the other two contribute OSRM's 400 s each.
      serve([370, 5000, 370], [400, 999, 400]);
      final route = await avoidanceRoute();
      expect(route.totalDurationS, closeTo(1820, 10));
    });

    test('keeps Valhalla timing when too little could be verified', () async {
      serve([5000, 5000, 5000], [100, 100, 100]);
      final route = await avoidanceRoute();
      expect(route.totalDurationS, valhallaSeconds);
    });

    test('keeps Valhalla timing when the re-timing request fails', () async {
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path.startsWith('/osrm')) {
          request.response.statusCode = 503;
        } else {
          request.response.write(valhallaBody());
        }
        await request.response.close();
      });
      final route = await avoidanceRoute();
      expect(route.totalDurationS, valhallaSeconds);
    });

    test('keeps Valhalla time across a sea crossing', () async {
      // A ferry: OSM draws it as a way with almost no nodes, so it arrives as
      // one huge straight jump (Naples→Palermo is a single 305 km line). Only
      // Valhalla knows the boat's scheduled duration, so that slice keeps its
      // time no matter what OSRM says about driving round the coast.
      final withFerry = [
        for (var i = 0; i <= 50; i++) LatLng(45.0 + i * 0.0001, 9.0),
        for (var i = 0; i <= 50; i++) LatLng(47.0 + i * 0.0001, 9.0),
      ];
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path.startsWith('/osrm')) {
          // Four waypoints, three legs; OSRM drives the long way round for the
          // middle one and claims it took ten minutes.
          request.response.write(jsonEncode({
            'code': 'Ok',
            'routes': [
              {
                'distance': 3000.0,
                'duration': 1800.0,
                'legs': [
                  {'distance': 550.0, 'duration': 300.0},
                  {'distance': 900.0, 'duration': 600.0},
                  {'distance': 550.0, 'duration': 300.0},
                ],
              },
            ],
          }));
        } else {
          request.response.write(jsonEncode({
            'trip': {
              'status': 0,
              'summary': {
                'has_highway': false,
                'has_toll': false,
                'length': 223.5,
                'time': 9000.0,
              },
              'legs': [
                {
                  'shape': _encodePolyline6(withFerry),
                  'maneuvers': [
                    {
                      'type': 1,
                      'instruction': 'Parti.',
                      'length': 223.5,
                      'begin_shape_index': 0,
                    },
                  ],
                },
              ],
            },
          }));
        }
        await request.response.close();
      });
      final route = await avoidanceRoute();
      // The crossing dominates: its Valhalla share is kept, so the total stays
      // far above the half-hour OSRM claimed for the whole thing.
      expect(route.totalDurationS, greaterThan(8000));
      expect(route.polyline, hasLength(withFerry.length));
    });
  });

  group('followSameRoads', () {
    RouteResult routeOf(List<LatLng> points, double lengthM) => RouteResult(
          polyline: points,
          steps: const [],
          totalDistanceM: lengthM,
          totalDurationS: 600,
        );

    // A straight 1 km north-south line, sampled every ~11 m.
    List<LatLng> line({double lonOffsetDeg = 0}) => [
          for (var i = 0; i <= 100; i++)
            LatLng(45.0 + i * 0.0001, 9.0 + lonOffsetDeg),
        ];

    test('recognises the very same road returned by both engines', () {
      // Same geometry, different ETA: exactly the Valhalla-vs-OSRM case.
      final a = routeOf(line(), 1113);
      final b = routeOf(line(), 1120);
      expect(RoutingService.followSameRoads(a, b), isTrue);
    });

    test('a parallel road 100 m away is not the same road', () {
      // 0.00127° of longitude ≈ 100 m at 45° latitude.
      final a = routeOf(line(), 1113);
      final b = routeOf(line(lonOffsetDeg: 0.00127), 1113);
      expect(RoutingService.followSameRoads(a, b), isFalse);
    });

    test('a detour that changes the length is not the same road', () {
      final a = routeOf(line(), 1113);
      final b = routeOf(line(), 1400);
      expect(RoutingService.followSameRoads(a, b), isFalse);
    });

    test('a route without geometry never matches', () {
      expect(
          RoutingService.followSameRoads(
              routeOf(const [], 1113), routeOf(line(), 1113)),
          isFalse);
    });
  });

  group('decorative fields never cost the whole route', () {
    const point = LatLng(45, 9);

    RouteStep step({int? exitNumber, String? exitLabel}) => RouteStep(
          instruction: 'Take the exit',
          direction: 'off ramp',
          modifier: 'right',
          distanceM: 100,
          location: point,
          exitNumber: exitNumber,
          exitLabel: exitLabel,
        );

    test('an out-of-range roundabout exit is dropped, not fatal', () {
      // A roundabout with thirteen arms is rare, not impossible, and the exit
      // count only decorates an icon. Rejecting the route over it would leave
      // the driver unable to navigate at all.
      final out = RoutingService.sanitiseDecorations([step(exitNumber: 13)]);
      expect(out.single.exitNumber, isNull);
      expect(out.single.instruction, 'Take the exit',
          reason: 'the maneuver itself survives');
      expect(
          RoutingService.sanitiseDecorations([step(exitNumber: 0)])
              .single
              .exitNumber,
          isNull);
    });

    test('a plausible exit number is kept exactly', () {
      for (final n in [1, 7, 12]) {
        expect(
            RoutingService.sanitiseDecorations([step(exitNumber: n)])
                .single
                .exitNumber,
            n,
            reason: '$n');
      }
    });

    test('an overlong exit label is dropped, not fatal', () {
      expect(
          RoutingService.sanitiseDecorations([step(exitLabel: 'X' * 33)])
              .single
              .exitLabel,
          isNull);
      expect(
          RoutingService.sanitiseDecorations([step(exitLabel: '199')])
              .single
              .exitLabel,
          '199');
    });

    test('an untouched list is returned as-is, so nothing is rebuilt', () {
      final steps = [step(exitNumber: 3, exitLabel: '199')];
      expect(
          identical(RoutingService.sanitiseDecorations(steps), steps), isTrue);
    });
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
