import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/search_ranking_protocol.dart';
import 'package:roadstr/services/search_response_protocol.dart';

import '../tools/kotlin_rewrite/generate_search_ranking_fixture.dart'
    as fixture;

NominatimResult _result(
  String name, {
  double lat = 45,
  double lon = 9,
}) =>
    NominatimResult(
      displayName: name,
      shortName: name,
      position: LatLng(lat, lon),
    );

void main() {
  test('shared search ranking fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildSearchRankingFixture(),
    );
  });

  test('execution plan keeps phase, proximity and query bounds explicit', () {
    expect(
      SearchRankingProtocol.executionPlan(
        ' \u00a0 ',
        settled: true,
        hasNear: true,
      ),
      isNull,
    );
    final fast = SearchRankingProtocol.executionPlan(
      '  museum  ',
      settled: false,
      hasNear: true,
    )!;
    expect(fast.query, 'museum');
    expect(fast.useNominatim, isFalse);
    expect(fast.usePhoton, isTrue);
    expect(fast.usePoi, isTrue);
    expect(fast.allowRelaxedRetry, isFalse);

    final capped = SearchRankingProtocol.executionPlan(
      'x' * 250,
      settled: true,
      hasNear: false,
    )!;
    expect(capped.query, hasLength(SearchRankingProtocol.maxQueryLength));
  });

  test('Nominatim keeps duplicate precedence before cross-provider ranking',
      () {
    final nominatim = _result('Nominatim', lat: 45, lon: 9);
    final photon = _result('Photon copy', lat: 45.00005, lon: 9.00005);
    expect(
      SearchRankingProtocol.rankGeocoders(
        'anything',
        [nominatim],
        [photon],
        const LatLng(45, 9),
      ),
      [same(nominatim)],
    );
  });

  test('relaxed retry is allowed only for an empty settled result set', () {
    final settled = SearchRankingProtocol.executionPlan(
      'via roberto ricci',
      settled: true,
      hasNear: true,
    )!;
    expect(
      SearchRankingProtocol.relaxedRetryQuery(settled, const [], const []),
      'via ricci',
    );
    expect(
      SearchRankingProtocol.relaxedRetryQuery(
        settled,
        [_result('Found')],
        const [],
      ),
      isNull,
    );
  });
}
