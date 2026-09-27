import 'dart:convert';

enum RoutingRequestHttpMethod { get, post }

enum ValhallaCostingPolicy {
  hardHighwayAndTollExclusion,
  softHighwayAndTollAvoidance,
  avoidTracks,
}

class RoutingRequestPoint {
  final double latitude;
  final double longitude;

  const RoutingRequestPoint(this.latitude, this.longitude);
}

class RoutingProviderRequest {
  final RoutingRequestHttpMethod method;
  final Uri uri;
  final Map<String, String> headers;
  final String? body;

  const RoutingProviderRequest({
    required this.method,
    required this.uri,
    required this.headers,
    this.body,
  });
}

/// Socket-free construction of every request used by Roadstr's route engines.
class RoutingRequestProtocol {
  static const osrmDrivingEndpoint =
      'https://routing.openstreetmap.de/routed-car/route/v1/driving';
  static const osrmWalkingEndpoint =
      'https://routing.openstreetmap.de/routed-foot/route/v1/foot';
  static const osrmCyclingEndpoint =
      'https://routing.openstreetmap.de/routed-bike/route/v1/bike';
  static const openRouteServiceBase =
      'https://api.openrouteservice.org/v2/directions/';
  static const graphHopperPublicEndpoint =
      'https://graphhopper.com/api/1/route';

  /// Public keyless service operated by the German OpenStreetMap community.
  /// The bare `valhalla.openstreetmap.de/route` host serves the HTML demo;
  /// routing JSON is provided by the numbered host below.
  static const valhallaEndpoint = 'https://valhalla1.openstreetmap.de/route';

  static const maxIntermediateWaypoints = 4;
  static const rerouteBearingToleranceDegrees = 45;

  static const _userAgentHeaders = {'User-Agent': 'Roadstr/1.0'};

  static String osrmEndpoint(String vehicle) => switch (vehicle) {
        'walking' => osrmWalkingEndpoint,
        'cycling' => osrmCyclingEndpoint,
        _ => osrmDrivingEndpoint,
      };

  static String openRouteServiceProfile(String vehicle) => switch (vehicle) {
        'walking' => 'foot-walking',
        'cycling' => 'cycling-regular',
        _ => 'driving-car',
      };

  static String graphHopperVehicle(String vehicle) => switch (vehicle) {
        'walking' => 'foot',
        'cycling' => 'bike',
        _ => 'car',
      };

  static String graphHopperEndpoint(String? configured) =>
      (configured?.trim().isNotEmpty ?? false)
          ? configured!.trim()
          : graphHopperPublicEndpoint;

  static RoutingProviderRequest openRouteService({
    required RoutingRequestPoint origin,
    required RoutingRequestPoint destination,
    required String apiKey,
    required String languageCode,
    required String vehicle,
  }) =>
      RoutingProviderRequest(
        method: RoutingRequestHttpMethod.post,
        uri: Uri.parse(
          '$openRouteServiceBase${openRouteServiceProfile(vehicle)}',
        ),
        headers: {
          'Authorization': apiKey,
          'Content-Type': 'application/json',
          'User-Agent': 'Roadstr/1.0',
        },
        body: jsonEncode({
          'coordinates': [
            [origin.longitude, origin.latitude],
            [destination.longitude, destination.latitude],
          ],
          'language': openRouteServiceLanguage(languageCode),
          'instructions': true,
        }),
      );

  static RoutingProviderRequest graphHopperRoute({
    required RoutingRequestPoint origin,
    required RoutingRequestPoint destination,
    required String server,
    required String languageCode,
    required String vehicle,
    String? apiKey,
  }) {
    final parts = <String>[
      'point=${origin.latitude},${origin.longitude}',
      'point=${destination.latitude},${destination.longitude}',
      'vehicle=${graphHopperVehicle(vehicle)}',
      'locale=${Uri.encodeQueryComponent(languageCode)}',
      'instructions=true',
      'points_encoded=false',
      'details=max_speed',
    ];
    if (apiKey != null && server == graphHopperPublicEndpoint) {
      parts.add('key=${Uri.encodeQueryComponent(apiKey)}');
    }
    return RoutingProviderRequest(
      method: RoutingRequestHttpMethod.get,
      uri: Uri.parse(server).replace(query: parts.join('&')),
      headers: _userAgentHeaders,
    );
  }

  static RoutingProviderRequest graphHopperProbe({
    required String server,
    String? apiKey,
  }) {
    final parts = <String>[
      'point=0.0,0.0',
      'point=0.1,0.1',
      'vehicle=car',
      'locale=it',
      'instructions=false',
      'points_encoded=false',
    ];
    if (apiKey != null &&
        apiKey.isNotEmpty &&
        server == graphHopperPublicEndpoint) {
      parts.add('key=${Uri.encodeQueryComponent(apiKey)}');
    }
    return RoutingProviderRequest(
      method: RoutingRequestHttpMethod.get,
      uri: Uri.parse(server).replace(query: parts.join('&')),
      headers: _userAgentHeaders,
    );
  }

  static RoutingProviderRequest osrmRoute({
    required RoutingRequestPoint origin,
    required RoutingRequestPoint destination,
    required String vehicle,
    List<RoutingRequestPoint> via = const [],
    bool requestAlternatives = false,
    double? originBearingDegrees,
    String? endpoint,
  }) {
    final stops = [
      for (final point in via.take(maxIntermediateWaypoints))
        '${point.longitude},${point.latitude}',
    ];
    final alternatives =
        requestAlternatives && stops.isEmpty ? '&alternatives=3' : '';
    final bearings = originBearingDegrees == null
        ? ''
        : '&bearings=${originBearingDegrees.round() % 360},'
            '$rerouteBearingToleranceDegrees;';
    final base = endpoint ?? osrmEndpoint(vehicle);
    return RoutingProviderRequest(
      method: RoutingRequestHttpMethod.get,
      uri: Uri.parse('$base/'
          '${origin.longitude},${origin.latitude};'
          '${stops.isEmpty ? '' : '${stops.join(';')};'}'
          '${destination.longitude},${destination.latitude}'
          '?overview=full&geometries=geojson&steps=true$alternatives$bearings'),
      headers: _userAgentHeaders,
    );
  }

  static RoutingProviderRequest valhalla({
    required RoutingRequestPoint origin,
    required RoutingRequestPoint destination,
    required String languageCode,
    required ValhallaCostingPolicy costingPolicy,
    String? endpoint,
  }) {
    final costingOptions = switch (costingPolicy) {
      ValhallaCostingPolicy.hardHighwayAndTollExclusion => const {
          'exclude_highways': true,
          'exclude_tolls': true,
        },
      ValhallaCostingPolicy.softHighwayAndTollAvoidance => const {
          'use_highways': 0,
          'use_tolls': 0,
          'toll_booth_penalty': 900,
        },
      ValhallaCostingPolicy.avoidTracks => const {'use_tracks': 0},
    };
    final payload = jsonEncode({
      'locations': [
        {'lat': origin.latitude, 'lon': origin.longitude},
        {'lat': destination.latitude, 'lon': destination.longitude},
      ],
      'costing': 'auto',
      'costing_options': {'auto': costingOptions},
      'units': 'kilometers',
      'language': valhallaLanguage(languageCode),
    });
    final base = Uri.parse(endpoint ?? valhallaEndpoint);
    return RoutingProviderRequest(
      method: RoutingRequestHttpMethod.get,
      uri: base.replace(queryParameters: {'json': payload}),
      headers: _userAgentHeaders,
    );
  }

  static RoutingProviderRequest osrmRetime({
    required String waypoints,
    String? endpoint,
  }) =>
      RoutingProviderRequest(
        method: RoutingRequestHttpMethod.get,
        uri: Uri.parse('${endpoint ?? osrmDrivingEndpoint}/$waypoints'
            '?overview=false&steps=false'),
        headers: _userAgentHeaders,
      );

  static String openRouteServiceLanguage(String languageCode) {
    const codes = <String, String>{
      'cs': 'cs',
      'de': 'de',
      'en': 'en',
      'es': 'es',
      'fr': 'fr',
      'el': 'gr',
      'hu': 'hu',
      'it': 'it',
      'ja': 'ja',
      'nl': 'nl',
      'pl': 'pl',
      'pt': 'pt',
      'ro': 'ro',
      'ru': 'ru',
      'tr': 'tr',
      'uk': 'ua',
      'zh': 'zh',
    };
    return codes[languageCode.toLowerCase()] ?? 'en';
  }

  static String valhallaLanguage(String languageCode) {
    const locales = <String, String>{
      'bg': 'bg-BG',
      'cs': 'cs-CZ',
      'da': 'da-DK',
      'de': 'de-DE',
      'el': 'el-GR',
      'en': 'en-US',
      'es': 'es-ES',
      'et': 'et-EE',
      'fi': 'fi-FI',
      'fr': 'fr-FR',
      'hu': 'hu-HU',
      'it': 'it-IT',
      'ja': 'ja-JP',
      'nl': 'nl-NL',
      'pl': 'pl-PL',
      'pt': 'pt-BR',
      'ro': 'ro-RO',
      'ru': 'ru-RU',
      'sk': 'sk-SK',
      'sl': 'sl-SI',
      'sv': 'sv-SE',
      'zh': 'zh-CN',
    };
    return locales[languageCode.toLowerCase()] ?? 'en-US';
  }
}
