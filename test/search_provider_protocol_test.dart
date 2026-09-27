import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/search_provider_protocol.dart';

import '../tools/kotlin_rewrite/generate_search_provider_requests_fixture.dart'
    as fixture;

void main() {
  test('shared search-provider request fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildSearchProviderRequestsFixture(),
    );
  });

  test('keeps Nominatim and form encodings distinct', () {
    final nominatim = SearchProviderProtocol.nominatimSearch('A B!')!;
    final photon = SearchProviderProtocol.photonSearch('A B!')!;
    expect(nominatim.uri.query, contains('q=A%20B!'));
    expect(photon.uri.query, contains('q=A+B%21'));
  });

  test('keeps Photon privacy rounding and language allowlist', () {
    final supported = SearchProviderProtocol.photonSearch(
      'museum',
      latitude: 45.0703,
      longitude: 7.6869,
      languageCode: 'fr',
    )!;
    expect(supported.uri.query, contains('lat=45.07&lon=7.69'));
    expect(supported.uri.query, contains('lang=fr'));

    final unsupported = SearchProviderProtocol.photonSearch(
      'museo',
      languageCode: 'it',
    )!;
    expect(unsupported.uri.queryParameters.containsKey('lang'), isFalse);
  });

  test('keeps exactly the worldwide Overpass mirrors and request headers', () {
    expect(SearchProviderProtocol.overpassMirrors, hasLength(2));
    expect(
      SearchProviderProtocol.overpassMirrors.any((m) => m.contains('osm.ch')),
      isFalse,
    );
    final request = SearchProviderProtocol.overpass(
      SearchProviderProtocol.overpassMirrors.first,
      '[out:json];node(1);out;',
    );
    expect(request.method, SearchProviderHttpMethod.post);
    expect(
        request.headers['Content-Type'], 'application/x-www-form-urlencoded');
    expect(request.headers['User-Agent'], 'Roadstr/1.0 (navigation app)');
    expect(request.body, startsWith('data='));
  });

  test('rejects half-specified provider bias before networking', () {
    expect(
      () => SearchProviderProtocol.nominatimSearch('x', latitude: 45),
      throwsArgumentError,
    );
    expect(
      () => SearchProviderProtocol.photonSearch('x', longitude: 7),
      throwsArgumentError,
    );
  });
}
