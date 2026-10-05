/// Deterministic request construction shared by the live Flutter services and
/// the native rewrite parity fixture.
///
/// This class deliberately does not execute HTTP. Keeping provider-specific
/// endpoints, headers and encoding here lets both runtimes prove the exact
/// outbound contract before the Kotlin network engine is connected.
enum SearchProviderHttpMethod { get, post }

class SearchProviderRequest {
  final SearchProviderHttpMethod method;
  final Uri uri;
  final Map<String, String> headers;
  final String? body;

  const SearchProviderRequest({
    required this.method,
    required this.uri,
    required this.headers,
    this.body,
  });
}

class SearchProviderProtocol {
  static const nominatimSearchEndpoint =
      'https://nominatim.openstreetmap.org/search';
  static const nominatimReverseEndpoint =
      'https://nominatim.openstreetmap.org/reverse';
  static const photonEndpoint = 'https://photon.komoot.io/api/';

  /// overpass.osm.ch is intentionally absent: it contains Switzerland only
  /// and reports an empty success for queries elsewhere instead of failing.
  ///
  /// overpass.openstreetmap.fr is absent too: it now answers every request
  /// from an app with 403 "only available to white-listed usages". The two
  /// extra instances are run by the same operator as the main one, so adding
  /// them widens the fallback without adding another party that sees where
  /// the driver is.
  static const overpassMirrors = [
    'https://overpass-api.de/api/interpreter',
    'https://lz4.overpass-api.de/api/interpreter',
    'https://z.overpass-api.de/api/interpreter',
  ];

  static const photonMaxQueryLength = 200;
  static const photonSupportedLanguages = {'en', 'de', 'fr'};

  static const _roadstrHeaders = {'User-Agent': 'Roadstr/1.0'};
  static const _overpassHeaders = {
    'Content-Type': 'application/x-www-form-urlencoded',
    'User-Agent': 'Roadstr/1.0 (navigation app)',
  };

  static SearchProviderRequest? nominatimSearch(
    String query, {
    double? latitude,
    double? longitude,
  }) {
    if ((latitude == null) != (longitude == null)) {
      throw ArgumentError('Nominatim bias requires both coordinates');
    }
    final q = query.trim();
    if (q.isEmpty) return null;
    final viewbox = latitude == null
        ? ''
        : '&viewbox=${longitude! - 0.25},${latitude + 0.25},'
            '${longitude + 0.25},${latitude - 0.25}&bounded=0';
    return SearchProviderRequest(
      method: SearchProviderHttpMethod.get,
      uri: Uri.parse('$nominatimSearchEndpoint'
          '?q=${Uri.encodeComponent(q)}'
          '&format=json&limit=6&addressdetails=1&extratags=1'
          '&polygon_geojson=0$viewbox'),
      headers: _roadstrHeaders,
    );
  }

  static SearchProviderRequest nominatimReverse({
    required double latitude,
    required double longitude,
  }) =>
      SearchProviderRequest(
        method: SearchProviderHttpMethod.get,
        uri: Uri.parse('$nominatimReverseEndpoint'
            '?lat=$latitude&lon=$longitude'
            '&format=json&addressdetails=1&extratags=1'),
        headers: _roadstrHeaders,
      );

  static SearchProviderRequest? photonSearch(
    String query, {
    double? latitude,
    double? longitude,
    String languageCode = 'en',
    int limit = 8,
  }) {
    if ((latitude == null) != (longitude == null)) {
      throw ArgumentError('Photon bias requires both coordinates');
    }
    final q = query.trim();
    if (q.isEmpty || q.length > photonMaxQueryLength) return null;
    final bias = latitude == null
        ? ''
        : '&lat=${latitude.toStringAsFixed(2)}'
            '&lon=${longitude!.toStringAsFixed(2)}'
            '&location_bias_scale=0.3&zoom=12';
    final lang = photonSupportedLanguages.contains(languageCode)
        ? '&lang=$languageCode'
        : '';
    return SearchProviderRequest(
      method: SearchProviderHttpMethod.get,
      uri: Uri.parse('$photonEndpoint?q=${Uri.encodeQueryComponent(q)}'
          '&limit=$limit$bias$lang'),
      headers: _roadstrHeaders,
    );
  }

  static SearchProviderRequest overpass(String mirror, String query) =>
      SearchProviderRequest(
        method: SearchProviderHttpMethod.post,
        uri: Uri.parse(mirror),
        headers: _overpassHeaders,
        body: 'data=${Uri.encodeQueryComponent(query)}',
      );
}
