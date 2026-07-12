// Routing, geocoding, and Wikipedia lookup service for Roadstr.
//
// Supports three routing back-ends selectable by the user in Settings:
//   - OSRM (default) — free, no API key required, supports up to 3 route
//     alternatives via ?alternatives=3.
//   - OpenRouteService — requires an API key; POST-based GeoJSON response.
//   - GraphHopper — supports both the public API (API key) and self-hosted
//     instances (custom server URL).
//
// Geocoding uses the Nominatim OpenStreetMap API (addressdetails=1) for both
// forward search and reverse geocoding.
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import '../utils/units.dart';

/// A single turn-by-turn navigation step produced by a routing provider.
class RouteStep {
  /// Human-readable instruction in the requested language (e.g. "Turn right on Via Roma").
  final String instruction;
  /// Maneuver type from the routing provider (e.g. 'turn', 'roundabout', 'arrive').
  final String direction;
  /// Turn modifier — sub-type of a 'turn' maneuver: 'left', 'right', 'slight left',
  /// 'slight right', 'sharp left', 'sharp right', 'straight', 'uturn'.
  /// Empty string when not applicable (depart, arrive, etc.).
  final String modifier;
  /// Horizontal distance from this step's maneuver point to the next, in metres.
  final double distanceM;
  /// The geographic point where this maneuver begins.
  final LatLng location;
  /// Exit number for roundabout/rotary maneuvers (1-based). Null for other types.
  final int? exitNumber;

  const RouteStep({
    required this.instruction,
    required this.direction,
    this.modifier = '',
    required this.distanceM,
    required this.location,
    this.exitNumber,
  });
}

/// The complete result of a route calculation: polyline + turn-by-turn steps.
/// One speed-limit zone along the route: starts at [distFromStartM] metres
/// from the route origin and applies until the next entry.
typedef SpeedLimitEntry = ({double distFromStartM, int? speedKmh});

class RouteResult {
  /// Ordered list of coordinates forming the route polyline to draw on the map.
  final List<LatLng> polyline;
  /// Turn-by-turn navigation instructions.
  final List<RouteStep> steps;
  final double totalDistanceM;
  final double totalDurationS;
  /// Speed-limit zones sorted by distance from route start.
  /// Populated for OSRM (annotations=maxspeed) and GraphHopper (details=max_speed).
  /// Empty for providers that do not expose speed-limit data.
  final List<SpeedLimitEntry> speedLimits;

  const RouteResult({
    required this.polyline,
    required this.steps,
    required this.totalDistanceM,
    required this.totalDurationS,
    this.speedLimits = const [],
  });

  /// Returns the posted speed limit at [elapsedM] metres from the route start,
  /// or null when the limit is unknown or no data is available.
  int? speedLimitAt(double elapsedM) {
    if (speedLimits.isEmpty) return null;
    int? result;
    for (final e in speedLimits) {
      if (e.distFromStartM > elapsedM) break;
      result = e.speedKmh;
    }
    return result;
  }

  String get distanceLabel => Units.fmtDist(totalDistanceM);

  String get durationLabel {
    final m = (totalDurationS / 60).round();
    if (m < 60) return '$m min';
    final h = m ~/ 60;
    final rem = m % 60;
    return '${h}h ${rem}min';
  }
}

/// Route preferences — avoidance and weighting options forwarded to the
/// routing provider. Provider support varies:
///   [avoidHighways]: OSRM (via `exclude=motorway`), ORS, GH self-hosted
///   [avoidTolls]:    OSRM (via `exclude=toll`; may return no-route on toll-only networks), ORS, GH self-hosted
///   [preferShorter]: GraphHopper (`weighting=shortest`), ORS (`preference=shortest`)
class RoutePreferences {
  final bool avoidHighways;
  final bool avoidTolls;
  final bool preferShorter;
  const RoutePreferences({
    this.avoidHighways = false,
    this.avoidTolls    = false,
    this.preferShorter = false,
  });
}

/// Selects which routing back-end to use. Stored as a string in Hive settings.
enum RoutingProvider { osrm, openRoute, graphHopper }

/// Stateless routing and geocoding helper. All methods are `static`.
class RoutingService {
  /// OSRM driving endpoint — the FOSSGIS community server supports car, foot
  /// and bike profiles AND returns maxspeed annotations; unlike the lightweight
  /// demo at router.project-osrm.org which rejects annotations=maxspeed.
  static const _osrmDriving = 'https://routing.openstreetmap.de/routed-car/route/v1/driving';

  /// OSRM walking/cycling endpoints — same community server, different profiles.
  static const _osrmWalking  = 'https://routing.openstreetmap.de/routed-foot/route/v1/foot';
  static const _osrmBike     = 'https://routing.openstreetmap.de/routed-bike/route/v1/bike';
  static const _nominatim = 'https://nominatim.openstreetmap.org/search';
  /// ORS base — the profile segment ('driving-car', 'foot-walking'…) is appended.
  static const _orsBase = 'https://api.openrouteservice.org/v2/directions/';
  static const _graphhopperPublic = 'https://graphhopper.com/api/1/route';

  // ── Vehicle / transport-mode helpers ──────────────────────────────────────

  /// Returns the correct OSRM base URL for the requested transport mode.
  /// The two servers are separate because router.project-osrm.org only exposes
  /// the car profile, while routing.openstreetmap.de has foot and bike too.
  static String _osrmEndpoint(String vehicle) {
    if (vehicle == 'walking') return _osrmWalking;
    if (vehicle == 'cycling') return _osrmBike;
    return _osrmDriving;
  }

  /// Translates the vehicle string to the GraphHopper vehicle parameter.
  static String _ghVehicle(String vehicle) {
    if (vehicle == 'walking') return 'foot';
    if (vehicle == 'cycling') return 'bike';
    return 'car';
  }

  /// Translates the vehicle string to the OpenRouteService profile segment.
  static String _orsProfile(String vehicle) {
    if (vehicle == 'walking') return 'foot-walking';
    if (vehicle == 'cycling') return 'cycling-regular';
    return 'driving-car';
  }

  /// Searches for addresses and POIs via the Nominatim geocoding API.
  ///
  /// Returns at most 8 results. The `addressdetails=1` parameter is included so
  /// that [NominatimResult.fromJson] can parse the structured address components.
  ///
  /// When [near] is given, results are biased toward that location: a
  /// `viewbox` around it is sent to Nominatim (soft bias — `bounded=0` never
  /// excludes valid matches elsewhere), and the returned list is re-sorted by
  /// distance from [near]. Without this, a generic term like "cinema" can
  /// rank a same-named business on the other side of the world above the one
  /// 500 m away, since Nominatim's own ranking is a global "importance"
  /// score, not a proximity score.
  static Future<List<NominatimResult>> search(String query, {LatLng? near}) async {
    if (query.trim().isEmpty) return [];
    try {
      final viewbox = near != null
          ? '&viewbox=${near.longitude - 0.5},${near.latitude + 0.5},'
              '${near.longitude + 0.5},${near.latitude - 0.5}&bounded=0'
          : '';
      final uri = Uri.parse('$_nominatim'
          '?q=${Uri.encodeComponent(query)}'
          // limit=8 gives a richer POI list without excessive bandwidth.
          // polygon_geojson=0 skips shape data we don't need, keeping responses lean.
          '&format=json&limit=8&addressdetails=1&polygon_geojson=0$viewbox');
      final res = await http.get(uri,
          headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 5));
      if (res.statusCode != 200) return [];
      final list = jsonDecode(res.body) as List;
      final results = list.map((e) => NominatimResult.fromJson(e)).toList();
      if (near != null) {
        results.sort((a, b) =>
            const Distance().as(LengthUnit.Meter, near, a.position)
                .compareTo(const Distance().as(LengthUnit.Meter, near, b.position)));
      }
      return results;
    } catch (_) {
      return [];
    }
  }

  /// Converts [point] to a human-readable address string.
  ///
  /// Delegates to [reverseGeocodeDetail] and takes the first [parts] comma-
  /// separated components of the `display_name` field. Passing `parts: 1`
  /// returns only the road or POI name; `parts: 4` gives a fuller address.
  static Future<String?> reverseGeocode(LatLng point, {int parts = 1}) async {
    try {
      final detail = await reverseGeocodeDetail(point);
      if (detail == null) return null;
      return detail.display.split(',')
          .map((s) => s.trim()).where((s) => s.isNotEmpty)
          .take(parts).join(', ');
    } catch (_) {
      return null;
    }
  }

  /// Extended reverse geocode that fetches Nominatim's structured address
  /// breakdown (`addressdetails=1`).
  ///
  /// Returns a record with:
  ///   - `display`: the full formatted address string.
  ///   - `wikiQuery`: the best candidate term to use as a Wikipedia search query.
  ///     Priority: named POI → tourism → amenity → historic → leisure → suburb →
  ///     quarter → neighbourhood → city → town → village → municipality → county.
  ///     Numeric-only strings (house numbers) are excluded to avoid Wikipedia
  ///     results like "Via 5" instead of "Rome".
  static Future<({String display, String? wikiQuery})?>
      reverseGeocodeDetail(LatLng point) async {
    try {
      final uri = Uri.parse(
          'https://nominatim.openstreetmap.org/reverse'
          '?lat=${point.latitude}&lon=${point.longitude}'
          '&format=json&addressdetails=1');
      final res = await http
          .get(uri, headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 5));
      if (res.statusCode != 200) return null;
      final data    = jsonDecode(res.body) as Map<String, dynamic>;
      final display = data['display_name'] as String? ?? '';
      final addr    = data['address'] as Map<String, dynamic>? ?? {};

      // Choose the best Wikipedia search term in priority order:
      // 1. POI name (e.g. "Colosseum")
      // 2. Neighbourhood / street with a meaningful name
      // 3. City name
      // NEVER house numbers or plain numeric strings
      bool isNumber(String? s) =>
          s == null || RegExp(r'^\d+$').hasMatch(s.trim());

      // Best POI-level name (high priority, excluding place hierarchy)
      final poiName = [
        data['name'] as String?,
        addr['tourism']       as String?,
        addr['amenity']       as String?,
        addr['historic']      as String?,
        addr['leisure']       as String?,
        addr['suburb']        as String?,
        addr['quarter']       as String?,
        addr['neighbourhood'] as String?,
      ].where((s) => s != null && s.isNotEmpty && !isNumber(s)).firstOrNull;

      // City / municipality for geographic disambiguation
      final city = [
        addr['city']         as String?,
        addr['town']         as String?,
        addr['village']      as String?,
        addr['municipality'] as String?,
        addr['county']       as String?,
      ].where((s) => s != null && s.isNotEmpty && !isNumber(s)).firstOrNull;

      // Combine POI name with city so that generic names like "Teodorico"
      // become "Teodorico Ravenna" — making the Wikipedia / web-search
      // fallback much more accurate without affecting the geo-based lookup.
      String? wikiQuery;
      if (poiName != null) {
        wikiQuery = (city != null && city != poiName)
            ? '$poiName $city'
            : poiName;
      } else {
        wikiQuery = city;
      }

      return (display: display, wikiQuery: wikiQuery);
    } catch (_) {
      return null;
    }
  }

  /// Calculates a single driving route from [origin] to [destination].
  ///
  /// Provider selection:
  ///   - [RoutingProvider.osrm]: free public OSRM instance; no key needed.
  ///   - [RoutingProvider.openRoute]: POST-based; requires [apiKey].
  ///   - [RoutingProvider.graphHopper]: GET-based; uses [graphhopperServer] for
  ///     self-hosted or [apiKey] for the public API.
  ///
  /// All providers support the `lang` parameter for localised instruction text.
  /// Throws [RoutingException] on HTTP errors or malformed responses.
  static Future<RouteResult?> getRoute(
      LatLng origin, LatLng destination,
      {RoutingProvider provider = RoutingProvider.osrm,
      String? apiKey,
      String? graphhopperServer,
      String lang = 'en',
      String vehicle = 'driving',
      RoutePreferences prefs = const RoutePreferences()}) async {
    try {
      if (provider == RoutingProvider.openRoute && apiKey != null) {
        // OpenRouteService (POST JSON)
        final uri = Uri.parse('$_orsBase${_orsProfile(vehicle)}');
        final avoidList = <String>[
          if (prefs.avoidHighways) 'highways',
          if (prefs.avoidTolls)    'tollways',
        ];
        final body = jsonEncode({
          'coordinates': [
            [origin.longitude, origin.latitude],
            [destination.longitude, destination.latitude]
          ],
          'language': lang,
          'instructions': true,
          if (prefs.preferShorter)   'preference': 'shortest',
          if (avoidList.isNotEmpty)  'options': {'avoid_features': avoidList},
        });
        final res = await http
            .post(uri,
                headers: {
                  'Authorization': apiKey,
                  'Content-Type': 'application/json',
                  'User-Agent': 'Roadstr/1.0'
                },
                body: body)
            .timeout(const Duration(seconds: 10));
        if (res.statusCode != 200) {
          throw RoutingException(statusCode: res.statusCode, body: res.body, message: 'OpenRouteService HTTP error');
        }

        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final feat = (data['features'] as List).first as Map<String, dynamic>;
        final props = feat['properties'] as Map<String, dynamic>;
        final summary = props['summary'] as Map<String, dynamic>?;
        final segments = props['segments'] as List?;

        final coords = (feat['geometry']['coordinates'] as List)
            .map((c) => LatLng((c[1] as num).toDouble(), (c[0] as num).toDouble()))
            .toList();

        final steps = <RouteStep>[];
        if (segments != null && segments.isNotEmpty) {
          final seg = segments.first as Map<String, dynamic>;
          final sList = seg['steps'] as List? ?? [];
          for (final s in sList) {
            final step = s as Map<String, dynamic>;
            final instr = (step['instruction'] as String?) ?? '';
            final dist = (step['distance'] as num?)?.toDouble() ?? 0.0;
            final type = step['type']?.toString() ?? 'step';
            LatLng loc;
            final way = (step['way_points'] as List?);
            if (way != null && way.isNotEmpty) {
              final idx = (way.first as int).clamp(0, coords.length - 1);
              loc = coords[idx];
            } else {
              loc = coords.isNotEmpty ? coords.first : LatLng(origin.latitude, origin.longitude);
            }
            final orsIsRoundabout = type == 'roundabout';
            steps.add(RouteStep(
              instruction: instr,
              direction: type,
              distanceM: dist,
              location: loc,
              exitNumber: orsIsRoundabout ? _parseExitNumber(instr) : null,
            ));
          }
        }

        return RouteResult(
          polyline: coords,
          steps: steps,
          totalDistanceM: (summary?['distance'] as num?)?.toDouble() ?? 0.0,
          totalDurationS: (summary?['duration'] as num?)?.toDouble() ?? 0.0,
        );
      }

      if (provider == RoutingProvider.graphHopper) {
        // GraphHopper: support public API (apiKey) or self-hosted server (graphhopperServer)
        final server = (graphhopperServer?.trim().isNotEmpty ?? false)
            ? graphhopperServer!.trim()
            : _graphhopperPublic;
        final parts = <String>[
          'point=${origin.latitude},${origin.longitude}',
          'point=${destination.latitude},${destination.longitude}',
          'vehicle=${_ghVehicle(vehicle)}',
          'locale=${lang == 'it' ? 'it' : 'en'}',
          'instructions=true',
          'points_encoded=false',
          'details=max_speed',
        ];
        if (prefs.preferShorter)  parts.add('weighting=shortest');
        if (prefs.avoidHighways)  parts.add('avoid%5B%5D=motorway');
        if (prefs.avoidTolls)     parts.add('avoid%5B%5D=toll');
        if (apiKey != null && server == _graphhopperPublic) {
          parts.add('key=${Uri.encodeQueryComponent(apiKey)}');
        }
        final uri = Uri.parse(server).replace(query: parts.join('&'));

        final res = await http.get(uri, headers: {'User-Agent': 'Roadstr/1.0'})
            .timeout(const Duration(seconds: 12));
        if (res.statusCode != 200) {
          throw RoutingException(statusCode: res.statusCode, body: res.body, message: 'GraphHopper HTTP error');
        }
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        if (data['paths'] == null || (data['paths'] as List).isEmpty) {
          throw RoutingException(message: 'GraphHopper response missing paths', body: res.body);
        }
        final path = (data['paths'] as List).first as Map<String, dynamic>;
        final points = path['points'] as Map<String, dynamic>?;
        final coords = <LatLng>[];
        if (points != null && points['coordinates'] != null) {
          for (final c in points['coordinates'] as List) {
            coords.add(LatLng((c[1] as num).toDouble(), (c[0] as num).toDouble()));
          }
        }
        final instructions = path['instructions'] as List? ?? [];
        final steps = <RouteStep>[];
        for (final instr in instructions) {
          final m = instr as Map<String, dynamic>;
          final text = (m['text'] as String?) ?? '';
          final dist = (m['distance'] as num?)?.toDouble() ?? 0.0;
          final sign = m['sign']?.toString() ?? '';
          final idx = (m['interval'] as List?)?.first as int? ?? 0;
          final loc = idx >= 0 && idx < coords.length ? coords[idx] : (coords.isNotEmpty ? coords.first : LatLng(origin.latitude, origin.longitude));
          final isRoundabout = sign == '6' || sign == 'roundabout' || sign == 'rotary';
          steps.add(RouteStep(instruction: text, direction: sign, distanceM: dist, location: loc,
              exitNumber: isRoundabout ? _parseExitNumber(text) : null));
        }

        // GraphHopper details=max_speed: [[fromIdx, toIdx, valueKmh], ...]
        // where indices are into the coords array.
        final ghSpeedLimits = <SpeedLimitEntry>[];
        try {
          final details = path['details'] as Map<String, dynamic>?;
          final msIntervals = details?['max_speed'] as List?;
          if (msIntervals != null && coords.isNotEmpty) {
            // Build cumulative distance array for coord → distance lookup.
            const distCalc = Distance();
            final cumDist = <double>[0.0];
            for (int i = 1; i < coords.length; i++) {
              cumDist.add(cumDist.last +
                  distCalc.as(LengthUnit.Meter, coords[i-1], coords[i]));
            }
            for (final iv in msIntervals) {
              final interval = iv as List;
              final fromIdx = (interval[0] as num).toInt().clamp(0, coords.length - 1);
              final val = interval[2];
              final int? speedKmh = val is num && val > 0 ? val.toInt() : null;
              ghSpeedLimits.add((distFromStartM: cumDist[fromIdx], speedKmh: speedKmh));
            }
          }
        } catch (_) {}

        debugPrint('[Routing] GH speedLimits: ${ghSpeedLimits.length} entries'
            ' (non-null: ${ghSpeedLimits.where((e) => e.speedKmh != null).length})');
        return RouteResult(
          polyline: coords,
          steps: steps,
          totalDistanceM: (path['distance'] as num?)?.toDouble() ?? 0.0,
          totalDurationS: (path['time'] as num?)?.toDouble() != null ? ((path['time'] as num).toDouble() / 1000.0) : 0.0,
          speedLimits: ghSpeedLimits,
        );
      }

      // Fallback / default: OSRM — choose the right public server for the mode.
      final osrmExcludeParts = <String>[
        if (prefs.avoidHighways) 'motorway',
        if (prefs.avoidTolls)    'toll',
      ];
      final osrmExclude = osrmExcludeParts.isNotEmpty
          ? '&exclude=${osrmExcludeParts.join(',')}'
          : '';
      final baseCoords = '${_osrmEndpoint(vehicle)}/'
          '${origin.longitude},${origin.latitude};'
          '${destination.longitude},${destination.latitude}'
          '?overview=full&geometries=geojson&steps=true$osrmExclude';

      // NOTE: no annotations=maxspeed — that is a Mapbox Directions extension,
      // vanilla OSRM (including FOSSGIS) rejects it with 400. Speed limits
      // come from SpeedLimitService (Overpass) instead.
      final res = await http
          .get(Uri.parse(baseCoords),
              headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) {
        throw RoutingException(
            statusCode: res.statusCode,
            body: res.body,
            message: 'OSRM HTTP error');
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (data['code'] != 'Ok') {
        throw RoutingException(
            message: 'OSRM returned error code: ${data['code']}');
      }
      return _parseOsrmRoute(
          (data['routes'] as List).first as Map<String, dynamic>, lang);
    } on RoutingException {
      rethrow;
    } catch (e) {
      throw RoutingException(message: e.toString());
    }
  }

  /// Calculates a driving route and returns up to 3 alternatives.
  ///
  /// Only OSRM supports the `alternatives=3` query parameter natively.
  /// For GraphHopper and OpenRouteService this wraps [getRoute] and returns a
  /// single-element list. The map screen uses the list to show an alternative
  /// selection panel when the route is longer than 5 km.
  static Future<List<RouteResult>> getRoutes(
      LatLng origin, LatLng destination,
      {RoutingProvider provider = RoutingProvider.osrm,
      String? apiKey,
      String? graphhopperServer,
      String lang = 'en',
      String vehicle = 'driving',
      RoutePreferences prefs = const RoutePreferences()}) async {
    if (provider != RoutingProvider.osrm) {
      final single = await getRoute(origin, destination,
          provider: provider,
          apiKey: apiKey,
          graphhopperServer: graphhopperServer,
          lang: lang,
          vehicle: vehicle,
          prefs: prefs);
      return single != null ? [single] : [];
    }
    try {
      final osrmExcludePartsAlt = <String>[
        if (prefs.avoidHighways) 'motorway',
        if (prefs.avoidTolls)    'toll',
      ];
      final osrmExclude = osrmExcludePartsAlt.isNotEmpty
          ? '&exclude=${osrmExcludePartsAlt.join(',')}'
          : '';
      final baseCoords = '${_osrmEndpoint(vehicle)}/'
          '${origin.longitude},${origin.latitude};'
          '${destination.longitude},${destination.latitude}'
          '?overview=full&geometries=geojson&steps=true&alternatives=3$osrmExclude';

      final res = await http
          .get(Uri.parse(baseCoords),
              headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) {
        throw RoutingException(
            statusCode: res.statusCode,
            body: res.body,
            message: 'OSRM HTTP error');
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (data['code'] != 'Ok') {
        throw RoutingException(
            message: 'OSRM returned error code: ${data['code']}');
      }
      return (data['routes'] as List)
          .map((r) => _parseOsrmRoute(r as Map<String, dynamic>, lang))
          .toList();
    } on RoutingException {
      rethrow;
    } catch (e) {
      throw RoutingException(message: e.toString());
    }
  }

  static RouteResult _parseOsrmRoute(Map<String, dynamic> route, [String lang = 'en']) {
    final leg = (route['legs'] as List).first as Map<String, dynamic>;
    final coords = (route['geometry']['coordinates'] as List)
        .map((c) => LatLng((c[1] as num).toDouble(), (c[0] as num).toDouble()))
        .toList();

    final steps = <RouteStep>[];
    for (final s in leg['steps'] as List) {
      final step = s as Map<String, dynamic>;
      final maneuver = step['maneuver'] as Map<String, dynamic>;
      final loc = maneuver['location'] as List;
      steps.add(RouteStep(
        instruction: _buildInstruction(step, lang),
        direction: maneuver['type'] as String? ?? 'straight',
        modifier: maneuver['modifier'] as String? ?? '',
        distanceM: (step['distance'] as num).toDouble(),
        location: LatLng(
            (loc[1] as num).toDouble(), (loc[0] as num).toDouble()),
        exitNumber: maneuver['exit'] as int?,
      ));
    }

    // ── Speed-limit annotations ─────────────────────────────────────────────
    // OSRM returns per-OSM-segment maxspeed + segment distance when
    // annotations=maxspeed,distance is requested. Build a cumulative list so
    // the navigator can look up the posted limit at any elapsed distance.
    final speedLimits = <SpeedLimitEntry>[];
    try {
      final ann = leg['annotation'] as Map<String, dynamic>?;
      if (ann != null) {
        final msRaw  = ann['maxspeed'] as List?;
        final dstRaw = ann['distance'] as List?;
        if (msRaw != null && dstRaw != null) {
          double cumM = 0;
          for (int i = 0; i < msRaw.length && i < dstRaw.length; i++) {
            final ms = msRaw[i];
            int? speedKmh;
            if (ms is Map) {
              final spd = ms['speed'];
              if (spd is num) speedKmh = spd.toInt();
              // "none" / "unlimited" → leave null (no posted limit)
            }
            speedLimits.add((distFromStartM: cumM, speedKmh: speedKmh));
            cumM += (dstRaw[i] as num).toDouble();
          }
        }
      }
    } catch (_) {} // annotation parsing is best-effort

    debugPrint('[Routing] OSRM speedLimits: ${speedLimits.length} entries'
        ' (non-null: ${speedLimits.where((e) => e.speedKmh != null).length})');
    return RouteResult(
      polyline: coords,
      steps: steps,
      totalDistanceM: (route['distance'] as num).toDouble(),
      totalDurationS: (route['duration'] as num).toDouble(),
      speedLimits: speedLimits,
    );
  }

  /// Test a GraphHopper server URL for connectivity and basic response
  static Future<void> testGraphHopperServer(String server, {String? apiKey}) async {
    final parts = <String>[
      'point=0.0,0.0',
      'point=0.1,0.1',
      'vehicle=car',
      'locale=it',
      'instructions=false',
      'points_encoded=false',
    ];
    if (apiKey != null && apiKey.isNotEmpty && server == _graphhopperPublic) {
      parts.add('key=${Uri.encodeQueryComponent(apiKey)}');
    }
    final uri = Uri.parse(server).replace(query: parts.join('&'));
    try {
      final res = await http.get(uri, headers: {'User-Agent': 'Roadstr/1.0'}).timeout(const Duration(seconds: 8));
      if (res.statusCode != 200) {
        throw RoutingException(statusCode: res.statusCode, body: res.body, message: 'Ping failed');
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (data['paths'] == null) throw RoutingException(message: 'No paths in response', body: res.body);
      return;
    } catch (e) {
      if (e is RoutingException) rethrow;
      throw RoutingException(message: e.toString());
    }
  }

  static String _buildInstruction(Map<String, dynamic> step, [String lang = 'en']) {
    final maneuver = step['maneuver'] as Map<String, dynamic>;
    final type     = maneuver['type'] as String? ?? '';
    final modifier = maneuver['modifier'] as String? ?? '';
    final name     = (step['name'] as String?) ?? '';
    final prep     = lang == 'it' ? ' su ' : ' on ';
    final road     = name.isNotEmpty ? '$prep$name' : '';
    final it       = lang == 'it';

    switch (type) {
      case 'depart':
        return it ? 'Parti$road' : 'Start$road';
      case 'arrive':
        return it ? 'Sei arrivato a destinazione' : 'You have arrived at your destination';
      case 'turn':
        switch (modifier) {
          case 'left':         return it ? 'Svolta a sinistra$road'       : 'Turn left$road';
          case 'right':        return it ? 'Svolta a destra$road'         : 'Turn right$road';
          case 'slight left':  return it ? 'Tieni la sinistra$road'       : 'Keep left$road';
          case 'slight right': return it ? 'Tieni la destra$road'         : 'Keep right$road';
          case 'sharp left':   return it ? 'Svolta netta a sinistra$road' : 'Sharp left$road';
          case 'sharp right':  return it ? 'Svolta netta a destra$road'   : 'Sharp right$road';
          case 'uturn':        return it ? 'Fai inversione di marcia$road': 'Make a U-turn$road';
          default:             return it ? 'Continua dritto$road'          : 'Continue straight$road';
        }
      case 'new name': return it ? 'Continua$road'         : 'Continue$road';
      case 'merge':    return it ? 'Immettiti$road'        : 'Merge onto$road';
      case 'on ramp':  return it ? 'Prendi la rampa$road'  : 'Take the ramp$road';
      case 'off ramp': {
        // OSRM puts highway exit numbers in step['exits'] (a string like "12" or "12A"),
        // not maneuver['exit'] which is only populated for roundabouts.
        final exits = (step['exits'] as String?)?.trim();
        final ref   = (step['ref'] as String?)?.split(';').first.trim();
        final label = (exits != null && exits.isNotEmpty) ? exits
                    : (ref   != null && ref.isNotEmpty)   ? ref
                    : null;
        if (label != null) {
          return it ? 'Esci all\'uscita $label$road' : 'Take exit $label$road';
        }
        return it ? 'Esci dalla rampa$road' : 'Take the exit$road';
      }
      case 'fork':
        return modifier.contains('left')
            ? (it ? 'Al bivio tieni la sinistra$road' : 'At the fork keep left$road')
            : (it ? 'Al bivio tieni la destra$road'   : 'At the fork keep right$road');
      case 'end of road':
        return modifier.contains('left')
            ? (it ? 'Alla fine della strada svolta a sinistra$road' : 'At the end of the road turn left$road')
            : (it ? 'Alla fine della strada svolta a destra$road'   : 'At the end of the road turn right$road');
      case 'roundabout': {
        final exit = maneuver['exit'] as int? ?? 1;
        return it ? 'Alla rotonda prendi la $exit° uscita$road'
                  : 'At the roundabout take exit $exit$road';
      }
      case 'rotary': {
        final exit = maneuver['exit'] as int? ?? 1;
        return it ? 'Alla rotatoria prendi la $exit° uscita$road'
                  : 'At the rotary take exit $exit$road';
      }
      default: return it ? 'Continua dritto$road' : 'Continue straight$road';
    }
  }

  /// Extracts the roundabout exit number from an instruction string.
  /// Handles ordinal digits (1°, 2ª, 1st, 2nd) and spelled-out forms in
  /// the supported navigation languages.
  static int? _parseExitNumber(String instruction) {
    // Numeric ordinal: "1°", "2ª", "3rd", "4th" etc.
    final numMatch = RegExp(r'\b([1-9])[°ªaero]').firstMatch(instruction);
    if (numMatch != null) return int.tryParse(numMatch.group(1)!);
    // Spelled-out ordinals (covers it/es/fr/pt/en)
    const ordinals = {
      'first': 1, 'prima': 1, 'première': 1, 'primera': 1, 'primeira': 1,
      'second': 2, 'seconda': 2, 'deuxième': 2, 'segunda': 2,
      'third': 3, 'terza': 3, 'troisième': 3, 'tercera': 3, 'terceira': 3,
      'fourth': 4, 'quarta': 4, 'quatrième': 4, 'cuarta': 4,
      'fifth': 5, 'quinta': 5, 'cinquième': 5,
      'sixth': 6, 'sesta': 6, 'sixième': 6, 'sexta': 6,
    };
    final lower = instruction.toLowerCase();
    for (final e in ordinals.entries) {
      if (lower.contains(e.key)) return e.value;
    }
    return null;
  }
}

// ── Wikipedia ─────────────────────────────────────────────────────────────────

/// Wikipedia article summary returned by the REST v1 summary API.
class WikiSummary {
  final String title;
  final String extract;
  final String? imageUrl;
  final String? pageUrl;
  const WikiSummary({
    required this.title, required this.extract,
    this.imageUrl, this.pageUrl,
  });
}

/// Thrown by [RoutingService.getRoute] and [RoutingService.getRoutes] when the
/// routing provider returns an HTTP error or an unexpected response body.
class RoutingException implements Exception {
  final int? statusCode;
  final String message;
  final String? body;
  RoutingException({this.statusCode, required this.message, this.body});

  @override
  String toString() => 'RoutingException(statusCode: $statusCode, message: $message)';
}

/// Extension on [RoutingService] that adds geo-aware Wikipedia lookups.
///
/// Uses the Wikipedia Action API (`list=geosearch`) to find articles near a
/// coordinate, then fetches the full summary via the REST v1 summary endpoint.
/// Requested language is tried first; English is used as fallback.
extension WikiSearch on RoutingService {
  /// Searches Wikipedia for an article near ([lat], [lon]) within [radiusM] metres.
  ///
  /// **Why geo-search first (gscoord)?**
  /// A name-only search for "Santa Maria" would return hundreds of disambiguated
  /// results. The `geosearch` API (`gscoord=lat|lon`) returns articles whose
  /// coordinates fall within the radius, uniquely identifying the local landmark.
  ///
  /// If no article is found within [radiusM], falls back to a title-search using
  /// [fallbackQuery] (typically the best term from Nominatim's address breakdown).
  static Future<WikiSummary?> fetchWikiNearby(
      double lat, double lon, {
      String lang = 'it',
      String? fallbackQuery,
      int radiusM = 500,
  }) async {
    try {
      // Step 1: geo-search Wikipedia → articles near the tapped coordinates.
      final geoUri = Uri.parse(
          'https://$lang.wikipedia.org/w/api.php'
          '?action=query&list=geosearch'
          '&gscoord=${lat.toStringAsFixed(6)}|${lon.toStringAsFixed(6)}'
          '&gsradius=$radiusM&gslimit=3&format=json&origin=*');
      final geoRes = await http
          .get(geoUri, headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 5));
      if (geoRes.statusCode == 200) {
        final geoData = jsonDecode(geoRes.body) as Map<String, dynamic>;
        final hits = (geoData['query']?['geosearch'] as List?) ?? [];
        if (hits.isNotEmpty) {
          final title = Uri.encodeComponent(
              (hits.first as Map<String, dynamic>)['title'] as String);
          final summary = await fetchWikiSummary(title, lang: lang);
          if (summary != null) return summary;
        }
      }
    } catch (_) {}
    // Step 2: fall back to name-based title search when geo-search finds nothing.
    if (fallbackQuery != null) {
      return fetchWikiSummary(fallbackQuery, lang: lang);
    }
    return null;
  }

  /// Fetches a Wikipedia article summary by title using the REST v1 summary API.
  ///
  /// Tries [lang] first; if the article is missing or is a disambiguation page,
  /// retries with the alternate language (it ↔ en). Disambiguation pages are
  /// skipped because their `extract` is not useful for the place-info panel.
  static Future<WikiSummary?> fetchWikiSummary(String query,
      {String lang = 'it'}) async {
    try {
      final encoded = Uri.encodeComponent(query);
      for (final l in [lang, lang == 'it' ? 'en' : 'it']) {
        final uri = Uri.parse(
            'https://$l.wikipedia.org/api/rest_v1/page/summary/$encoded');
        final res = await http
            .get(uri, headers: {'User-Agent': 'Roadstr/1.0'})
            .timeout(const Duration(seconds: 5));
        if (res.statusCode != 200) continue;
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final extract = (data['extract'] as String?) ?? '';
        if (extract.isEmpty || data['type'] == 'disambiguation') continue;
        return WikiSummary(
          title:    data['title'] as String? ?? query,
          extract:  extract,
          imageUrl: (data['thumbnail'] as Map<String, dynamic>?)?['source']
              as String?,
          pageUrl:  ((data['content_urls'] as Map?)
              ?['mobile'] as Map?)?['page'] as String?,
        );
      }
      return null;
    } catch (_) {
      return null;
    }
  }
}

/// A geocoded location result from the Nominatim search API.
class NominatimResult {
  /// Full formatted address as returned by Nominatim (may be very long).
  final String displayName;
  /// Shortened display name — the first comma-separated component of [displayName].
  /// Used in search suggestion lists and navigation history labels.
  final String shortName;
  final LatLng position;

  /// Nominatim `class` field — broad feature category (e.g. 'amenity', 'tourism',
  /// 'highway', 'shop', 'office'). Used to select the result emoji.
  final String? cls;

  /// Nominatim `type` field — specific sub-type within [cls] (e.g. 'restaurant',
  /// 'museum', 'residential'). Used together with [cls] for fine-grained emoji.
  final String? type;

  /// City / town / village from the structured address — used to build a
  /// geo-disambiguated Wikipedia query (e.g. "Teodorico Ravenna").
  final String? city;

  const NominatimResult({
    required this.displayName,
    required this.shortName,
    required this.position,
    this.cls,
    this.type,
    this.city,
  });

  /// Returns an emoji that visually represents the feature category so users
  /// can distinguish POI types (restaurants, monuments, roads…) at a glance.
  String get emoji => _categoryEmoji(cls, type);

  /// A short human-readable category label shown below the result name.
  /// Falls back to the second address component if the type is not mapped.
  String get categoryLabel {
    if (cls == 'highway') {
      // shortName already contains "Road HouseNo, City"; show broader context
      // (district / region) from the remaining displayName components.
      final parts = displayName.split(',').map((p) => p.trim()).toList();
      // Skip house-number (parts[0]) and road (parts[1]); take up to 2 more
      // for "Quartiere, Città" style context without repeating shortName.
      final ctx = parts.skip(2).where((p) => p.isNotEmpty).take(2).join(', ');
      return ctx;
    }
    final mapped = _categoryLabel(cls, type);
    if (mapped != null) return mapped;
    final parts = displayName.split(',');
    return parts.length > 1 ? parts[1].trim() : '';
  }

  static String _categoryEmoji(String? cls, String? type) {
    switch (cls) {
      case 'highway':
        return '🛣️';
      case 'place':
        return switch (type) {
          'city' || 'town'         => '🏙️',
          'village' || 'hamlet'    => '🏘️',
          'suburb' || 'neighbourhood' => '🏡',
          _                        => '📍',
        };
      case 'amenity':
        return switch (type) {
          'restaurant' || 'fast_food' || 'food_court' => '🍽️',
          'cafe' || 'coffee_shop'  => '☕',
          'bar' || 'pub' || 'nightclub' => '🍺',
          'hospital' || 'clinic' || 'doctors' => '🏥',
          'pharmacy'               => '💊',
          'school' || 'kindergarten' => '🏫',
          'university' || 'college'  => '🎓',
          'bank' || 'atm'          => '🏦',
          'fuel' || 'charging_station' => '⛽',
          'parking'                => '🅿️',
          'police'                 => '👮',
          'post_office'            => '📮',
          'library'                => '📚',
          'theatre' || 'cinema'    => '🎭',
          'place_of_worship'       => '⛪',
          'marketplace'            => '🛒',
          'townhall'               => '🏛️',
          _                        => '📍',
        };
      case 'tourism':
        return switch (type) {
          'museum'                 => '🏛️',
          'hotel' || 'hostel' || 'motel' || 'guest_house' => '🏨',
          'attraction' || 'monument' || 'viewpoint' => '🗺️',
          'artwork' || 'gallery'   => '🎨',
          'camp_site'              => '⛺',
          'theme_park' || 'zoo'   => '🎡',
          _                        => '🗺️',
        };
      case 'shop':
        return switch (type) {
          'supermarket' || 'convenience' => '🛒',
          'bakery'                 => '🥖',
          'clothes' || 'fashion'   => '👗',
          'electronics'            => '📱',
          'books'                  => '📚',
          'florist'                => '💐',
          _                        => '🛍️',
        };
      case 'office':
        return switch (type) {
          'government' || 'administrative' => '🏛️',
          'company' || 'commercial' => '🏢',
          'ngo' || 'association'   => '🏢',
          _                        => '🏢',
        };
      case 'building':
        return switch (type) {
          'public' || 'government' => '🏛️',
          'hospital'               => '🏥',
          'school' || 'university' => '🎓',
          _                        => '🏗️',
        };
      case 'natural':
        return switch (type) {
          'beach'                  => '🏖️',
          'water' || 'lake'        => '💧',
          'peak' || 'hill'         => '⛰️',
          'wood' || 'forest'       => '🌲',
          _                        => '🌿',
        };
      case 'leisure':
        return switch (type) {
          'park' || 'garden'       => '🌳',
          'sports_centre' || 'stadium' => '🏟️',
          'swimming_pool'          => '🏊',
          _                        => '🎭',
        };
      case 'historic':
        return '🏛️';
      case 'railway':
        return '🚉';
      case 'aeroway':
        return '✈️';
      case 'waterway':
        return '🌊';
      case 'landuse':
        return '🗺️';
      default:
        return '📍';
    }
  }

  static String? _categoryLabel(String? cls, String? type) {
    switch (cls) {
      case 'highway':        return 'Road';
      case 'place':
        return switch (type) {
          'city'    => 'City',
          'town'    => 'Town',
          'village' => 'Village',
          _         => 'Place',
        };
      case 'amenity':
        return switch (type) {
          'restaurant'   => 'Restaurant',
          'fast_food'    => 'Fast food',
          'cafe'         => 'Café',
          'bar' || 'pub' => 'Bar / Pub',
          'hospital'     => 'Hospital',
          'pharmacy'     => 'Pharmacy',
          'school'       => 'School',
          'university'   => 'University',
          'bank'         => 'Bank',
          'atm'          => 'ATM',
          'fuel'         => 'Petrol station',
          'parking'      => 'Parking',
          'police'       => 'Police',
          'post_office'  => 'Post office',
          'library'      => 'Library',
          'theatre'      => 'Theatre',
          'cinema'       => 'Cinema',
          'place_of_worship' => 'Place of worship',
          'townhall'     => 'Town hall',
          _              => 'Service',
        };
      case 'tourism':
        return switch (type) {
          'museum'     => 'Museum',
          'hotel' || 'hostel' || 'motel' => 'Hotel',
          'attraction' || 'monument' => 'Attraction / Monument',
          'artwork'    => 'Opera d\'arte',
          'gallery'    => 'Gallery',
          _            => 'Tourism',
        };
      case 'shop':          return 'Shop';
      case 'office':        return 'Office';
      case 'historic':      return 'Historic site';
      case 'leisure':       return 'Leisure';
      case 'natural':       return 'Natural area';
      case 'railway':       return 'Railway / Station';
      case 'aeroway':       return 'Airport';
      default:              return null;
    }
  }

  factory NominatimResult.fromJson(Map<String, dynamic> j) {
    final lat = double.tryParse(j['lat'] as String) ?? double.nan;
    final lon = double.tryParse(j['lon'] as String) ?? double.nan;
    if (!lat.isFinite || !lon.isFinite ||
        lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      throw const FormatException('Nominatim: invalid coordinates');
    }
    final display = j['display_name'] as String;
    final clsVal  = j['class'] as String?;

    // addressdetails=1 gives structured address components. Use them to build
    // a meaningful shortName instead of the raw first comma-token (often just
    // a house number like "1" for street addresses).
    final addr    = (j['address'] as Map<String, dynamic>?) ?? {};
    final road    = addr['road'] as String?;
    final houseNo = addr['house_number'] as String?;
    final city    = (addr['city']     ?? addr['town']  ??
                     addr['village']  ?? addr['hamlet'] ??
                     addr['municipality']) as String?;

    String short;
    if (road != null && houseNo != null) {
      // Address with house number (buildings, residences — any class):
      // European order puts road first: "Via Roma 1, Milano".
      short = '$road $houseNo';
      if (city != null) short += ', $city';
    } else if (road != null && clsVal == 'highway') {
      // Road segment without a specific building number.
      short = road;
      if (city != null) short += ', $city';
    } else {
      // POI / place / city: first displayName component is already the name.
      short = display.split(',').first.trim();
    }

    final cityVal = (addr['city']      ?? addr['town']   ??
                     addr['village']   ?? addr['hamlet'] ??
                     addr['municipality']) as String?;

    return NominatimResult(
      displayName: display,
      shortName:   short,
      position:    LatLng(lat, lon),
      cls:         clsVal,
      type:        j['type']   as String?,
      city:        cityVal,
    );
  }
}
