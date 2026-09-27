import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/search_response_protocol.dart';

import '../tools/kotlin_rewrite/generate_search_responses_fixture.dart'
    as fixture;

void main() {
  test('shared search response fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildSearchResponsesFixture(),
    );
  });

  test('Nominatim and Photon skip one malformed result without losing peers',
      () {
    final nominatim = SearchResponseProtocol.parseNominatimSearch(jsonEncode([
      {'lat': 'bad', 'lon': '9', 'display_name': 'Bad'},
      {
        'lat': '45',
        'lon': '9',
        'display_name': 'Kept, Italy',
        'class': 'amenity',
        'type': 'cafe',
      },
    ]));
    expect(nominatim.map((result) => result.shortName), ['Kept']);

    final photon = SearchResponseProtocol.parsePhoton(jsonEncode({
      'features': [
        {
          'geometry': const {
            'coordinates': [9]
          }
        },
        {
          'geometry': const {
            'coordinates': [9, 45]
          },
          'properties': const {'name': 'Kept'},
        },
      ],
    }));
    expect(photon.map((result) => result.shortName), ['Kept']);
  });

  test('reverse parsing keeps labels bounded and strips controls', () {
    final detail = SearchResponseProtocol.parseNominatimReverse(jsonEncode({
      'display_name': '12, Via Roma, Milano',
      'address': {
        'road': 'Via\u0000Roma\u001f',
        'house_number': '12',
        'city': 'Milano',
      },
      'extratags': {'opening_hours': '  Mo-Fr 08:00-18:00  '},
    }));
    expect(detail, isNotNull);
    expect(detail!.label, 'Via Roma 12, Milano');
    expect(detail.openingHours, 'Mo-Fr 08:00-18:00');
  });

  test('Overpass filters envelope values and normalizes node or way centers',
      () {
    final elements = SearchResponseProtocol.parseOverpassElements(jsonEncode({
      'elements': [
        null,
        42,
        {
          'type': 'node',
          'lat': 41.9,
          'lon': 12.5,
          'tags': {'amenity': 'cafe', 'name': 'Node'},
        },
        {
          'type': 'way',
          'center': {'lat': 41.91, 'lon': 12.51},
          'tags': {'brand': 'Way'},
        },
      ],
    }));
    expect(elements, hasLength(2));
    final results = elements
        .map((element) => SearchResponseProtocol.overpassElementToResult(
              element,
              const LatLng(41.9, 12.5),
            ))
        .whereType<NominatimResult>()
        .toList();
    expect(results.map((result) => result.shortName), ['Node', 'Way']);
    expect(results.first.distanceM, 0);
    expect(results.last.distanceM, greaterThan(0));
  });
}
