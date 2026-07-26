import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/overpass_client.dart';
import 'package:roadstr/services/poi_search_service.dart';

void main() {
  late HttpServer server;
  late List<String> bodies;
  late String payload;
  late int port;

  setUp(() async {
    bodies = [];
    payload = jsonEncode({'elements': const []});
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    port = server.port;
    server.listen((request) async {
      bodies.add(await utf8.decoder.bind(request).join());
      request.response.headers.contentType = ContentType.json;
      request.response.write(payload);
      await request.response.close();
    });
  });

  tearDown(() => server.close(force: true));

  PoiSearchService serviceForServer() => PoiSearchService(
      overpass: _LocalOverpass(
          'http://${server.address.host}:${server.port}/api/interpreter'));

  // Rome, but nothing here depends on it: the query is built around whatever
  // position it is handed.
  const here = LatLng(41.9028, 12.4964);

  Map<String, dynamic> node(int id, double lat, double lon,
          Map<String, String> tags) =>
      {'type': 'node', 'id': id, 'lat': lat, 'lon': lon, 'tags': tags};

  String queryAt(int i) =>
      Uri.decodeQueryComponent(bodies[i].split('data=').last);

  test('asks the near ring first, then widens when it is thin', () async {
    // The stub answers with nothing, so the near ring is not enough.
    await serviceForServer()
        .nearby(NearbyCategory.fuel, here, unnamedLabel: 'Fuel');
    expect(bodies, hasLength(2));
    expect(queryAt(0), contains('around:1500,41.9028000,12.4964000'));
    expect(queryAt(1), contains('around:5000,41.9028000,12.4964000'));
    expect(queryAt(0), contains('"amenity"="fuel"'));
    // Both nodes and ways: a filling station is mapped either way.
    expect(queryAt(0), contains('node['));
    expect(queryAt(0), contains('way['));
  });

  test('a full near ring is answer enough — no 5 km sweep', () async {
    payload = jsonEncode({
      'elements': [
        for (var i = 0; i < 12; i++)
          node(i, 41.9028 + i * 0.0005, 12.4964, const {'amenity': 'fuel'}),
      ],
    });
    final results = await serviceForServer()
        .nearby(NearbyCategory.fuel, here, unnamedLabel: 'Fuel');
    expect(bodies, hasLength(1));
    expect(queryAt(0), contains('around:1500,'));
    expect(results, hasLength(12));
  });

  test('coordinates never reach Overpass in exponent form', () async {
    // On the Greenwich meridian a longitude like 5e-7 would be a syntax error.
    await serviceForServer().nearby(
        NearbyCategory.atm, const LatLng(51.4779, 0.0000005),
        unnamedLabel: 'ATM');
    expect(queryAt(0), isNot(contains('e-')));
    expect(queryAt(0), contains('51.4779000,0.0000005'));
  });

  test('the position drives the query, anywhere on earth', () async {
    const antipodes = [
      LatLng(-33.8688, 151.2093), // Sydney
      LatLng(64.1466, -21.9426), // Reykjavík
      LatLng(-1.2921, 36.8219), // Nairobi
    ];
    final service = serviceForServer();
    for (final position in antipodes) {
      await service.nearby(NearbyCategory.hospital, position,
          unnamedLabel: 'Hospital');
    }
    // Two rings per call, and both are built around the position given.
    expect(bodies, hasLength(antipodes.length * 2));
    for (var i = 0; i < antipodes.length; i++) {
      final position = antipodes[i];
      final coordinates = '${position.latitude.toStringAsFixed(7)},'
          '${position.longitude.toStringAsFixed(7)}';
      expect(queryAt(i * 2), contains('around:1500,$coordinates'));
      expect(queryAt(i * 2 + 1), contains('around:5000,$coordinates'));
    }
  });

  test('keeps unnamed places under the category label, nearest first',
      () async {
    payload = jsonEncode({
      'elements': [
        // ~1.1 km north, no name at all — a cash machine in a wall.
        node(1, 41.9128, 12.4964, const {'amenity': 'atm'}),
        // ~330 m north, named.
        node(2, 41.9058, 12.4964, const {'amenity': 'bank', 'name': 'Intesa'}),
        // ~550 m north, unnamed but branded.
        node(3, 41.9078, 12.4964, const {'amenity': 'atm', 'brand': 'BNL'}),
      ],
    });
    final results = await serviceForServer()
        .nearby(NearbyCategory.atm, here, unnamedLabel: 'ATM');

    expect(results.map((r) => r.shortName), ['Intesa', 'BNL', 'ATM']);
    expect(results.first.distanceM, closeTo(333, 40));
    expect(results.last.distanceM, closeTo(1112, 60));
    // Sorted by distance, so each one is further than the one before it.
    for (var i = 1; i < results.length; i++) {
      expect(results[i].distanceM! >= results[i - 1].distanceM!, isTrue);
    }
  });

  test('the same shop mapped twice appears once', () async {
    payload = jsonEncode({
      'elements': [
        node(1, 41.9058, 12.4964, const {'shop': 'supermarket', 'name': 'Conad'}),
        {
          'type': 'way',
          'id': 2,
          'center': {'lat': 41.90581, 'lon': 12.49641},
          'tags': const {'shop': 'supermarket', 'name': 'Conad'},
        },
      ],
    });
    final results = await serviceForServer()
        .nearby(NearbyCategory.supermarket, here, unnamedLabel: 'Supermarket');
    expect(results, hasLength(1));
  });

  test('a second tap on the same category is served from cache', () async {
    payload = jsonEncode({
      'elements': [
        node(1, 41.9058, 12.4964, const {'amenity': 'pharmacy', 'name': 'X'}),
      ],
    });
    final service = serviceForServer();
    await service.nearby(NearbyCategory.pharmacy, here, unnamedLabel: 'P');
    final afterFirst = bodies.length;
    await service.nearby(NearbyCategory.pharmacy, here, unnamedLabel: 'P');
    expect(bodies, hasLength(afterFirst));

    // Far enough away to be a different place: that must hit the network.
    await service.nearby(NearbyCategory.pharmacy, const LatLng(41.95, 12.55),
        unnamedLabel: 'P');
    expect(bodies.length, greaterThan(afterFirst));
  });

  test('a failing wide ring does not discard what the near ring found',
      () async {
    // Five hits in the near ring — under the "enough" threshold, so the wide
    // ring runs too and fails. The five must survive.
    payload = jsonEncode({
      'elements': [
        for (var i = 0; i < 5; i++)
          node(i, 41.9038 + i * 0.0002, 12.4964, const {'amenity': 'fuel'}),
      ],
    });
    final service = serviceForServer();
    var served = 0;
    await server.close(force: true);
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    server.listen((request) async {
      bodies.add(await utf8.decoder.bind(request).join());
      served++;
      if (served > 1) {
        request.response.statusCode = 503;
      } else {
        request.response.headers.contentType = ContentType.json;
        request.response.write(payload);
      }
      await request.response.close();
    });
    final results = await service.nearby(NearbyCategory.fuel, here,
        unnamedLabel: 'Fuel');
    // The near ring, then the wide one (whose failure is hedged to the second
    // mirror slot, so more than one request may be spent on it).
    expect(served, greaterThanOrEqualTo(2));
    expect(results, hasLength(5));
  });

  test('a failing mirror yields no results rather than a wrong answer',
      () async {
    final service = serviceForServer(); // captures the address while it is up
    await server.close(force: true);
    final results = await service.nearby(NearbyCategory.police, here,
        unnamedLabel: 'Police');
    expect(results, isEmpty);
  });
}

/// [OverpassClient] pinned to the local test server for every mirror slot, so
/// no request can escape to the public instances.
class _LocalOverpass extends OverpassClient {
  _LocalOverpass(this.url);
  final String url;

  @override
  List<String> get availableMirrors =>
      List.filled(OverpassClient.mirrors.length, url);
}
