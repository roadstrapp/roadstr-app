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
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:latlong2/latlong.dart';
import '../utils/fuzzy_match.dart';
import '../utils/geo.dart';
import '../utils/units.dart';
import 'bounded_http.dart';
import 'http_safety_policy.dart';
import 'roundabout_topology_service.dart';
import 'routing_request_protocol.dart';
import 'routing_response_protocol.dart';
import 'search_provider_protocol.dart';

export 'routing_response_protocol.dart';

/// OSRM bearing tolerance (degrees either side) used when rerouting a moving
/// vehicle — see [RoutingService.getRoutes]'s `originBearingDeg`.
///
/// Without a bearing hint, a reroute request carries only two coordinates —
/// where the car is, and the destination — and the engine is free to assume
/// it can be facing any direction at all, including one requiring an instant
/// reversal a moving vehicle cannot perform. On a long straight road with
/// nowhere legal to turn around, that produced a route the driver could not
/// follow, which they immediately deviated from again, triggering another
/// reroute — the same shape of route each time, which is what a field report
/// described as the map "just flipping over and over, without doing anything
/// concrete". Telling the engine the vehicle's actual course makes it route
/// forward from where the car really is heading, typically to the next
/// roundabout or turning point, instead of assuming the impossible.
///
/// 45°, not tighter: a moving vehicle's GPS course is not perfectly aligned
/// with the road, and a strict tolerance can make OSRM search much further
/// away for a matching segment than intended — the opposite of the point.
const rerouteBearingToleranceDeg =
    RoutingRequestProtocol.rerouteBearingToleranceDegrees;

extension RouteResultFormatting on RouteResult {
  String get distanceLabel => Units.fmtDist(totalDistanceM);
}

/// Selects which routing back-end to use. Stored as a string in Hive settings.
enum RoutingProvider { osrm, openRoute, graphHopper }

/// Stateless routing and geocoding helper. All methods are `static`.
class RoutingService {
  // Bounds on a route response. These are DoS guards (mainly against a
  // malicious self-hosted GraphHopper URL), NOT product limits — they must be
  // generous enough to never reject a legitimate journey. The byte limit,
  // enforced while streaming in BoundedHttp, is the real memory guard; the
  // point/step counts are secondary sanity checks.
  //
  // Sizing reference (measured live): a Boston→Miami *walking* route is
  // 2642 km → 79 930 polyline points, 3769 steps, 6.9 MB. Transcontinental
  // foot/bike routing is a real use case, so the limits sit well above that:
  // 250 000 points covers ~8000 km of fine-grained footpaths, and 32 MB /
  // 60 000 steps leave matching headroom.
  static const _maxRouteResponseBytes = 32 * 1024 * 1024;
  static final _roundaboutTopology = RoundaboutTopologyService();

  /// Adds the real total arm count to every roundabout step in [routes].
  ///
  /// The routing response itself normally knows only which ordinal exit to
  /// take. One batched OSM topology lookup supplies the independent arm count.
  /// Failure is deliberately non-fatal: the original routes are returned
  /// unchanged and the maneuver painter falls back to a regular generic sign.
  /// Most roundabouts one lookup will ask about.
  ///
  /// Two reasons, and the second is the one that matters. A city route through
  /// France or the UK can pass fifty roundabouts, and with alternatives on
  /// screen that is a query carrying a hundred `around:` clauses to a free,
  /// volunteer-run mirror — which would either time out or cost it real work.
  /// And every coordinate in that query is a piece of the user's itinerary,
  /// handed over in one request before they have even set off: the other
  /// Overpass lookups in this app disclose where the car *is*, progressively,
  /// not where it is going. Capping bounds both. The nearest roundabouts are
  /// kept, because those are the ones whose sign is about to be shown.
  static const _maxRoundaboutLookups = 24;

  /// The distinct roundabout locations one lookup would ask about, capped.
  /// Exposed so the bound can be tested without a network round trip.
  @visibleForTesting
  static List<LatLng> roundaboutLookupPoints(List<RouteResult> routes) =>
      _collectRoundabouts(routes).points;

  static ({
    List<LatLng> points,
    List<({int routeIndex, int stepIndex, int pointIndex})> refs,
  }) _collectRoundabouts(List<RouteResult> routes) {
    final points = <LatLng>[];
    final pointIndexByKey = <String, int>{};
    final refs = <({int routeIndex, int stepIndex, int pointIndex})>[];
    for (var routeIndex = 0; routeIndex < routes.length; routeIndex++) {
      final steps = routes[routeIndex].steps;
      for (var stepIndex = 0; stepIndex < steps.length; stepIndex++) {
        final step = steps[stepIndex];
        if (step.direction != 'roundabout' && step.direction != 'rotary') {
          continue;
        }
        final key = '${step.location.latitude.toStringAsFixed(5)},'
            '${step.location.longitude.toStringAsFixed(5)}';
        final existing = pointIndexByKey[key];
        if (existing == null && points.length >= _maxRoundaboutLookups) {
          continue; // beyond the cap: the sign falls back to the generic ring
        }
        final pointIndex = existing ??
            pointIndexByKey.putIfAbsent(key, () {
              points.add(step.location);
              return points.length - 1;
            });
        refs.add((
          routeIndex: routeIndex,
          stepIndex: stepIndex,
          pointIndex: pointIndex,
        ));
      }
    }
    return (points: points, refs: refs);
  }

  static Future<List<RouteResult>> enrichRoundaboutTopology(
      List<RouteResult> routes) async {
    final (:points, :refs) = _collectRoundabouts(routes);
    if (refs.isEmpty) return routes;

    final counts = await _roundaboutTopology.fetchArmCounts(points);
    if (counts.every((count) => count == null)) return routes;

    final changedSteps = <int, List<RouteStep>>{};
    for (final ref in refs) {
      final armCount = counts[ref.pointIndex];
      if (armCount == null) continue;
      final route = routes[ref.routeIndex];
      final step = route.steps[ref.stepIndex];
      // An incomplete OSM ring must never contradict the router's known exit.
      if (step.exitNumber != null && armCount < step.exitNumber!) continue;
      final steps = changedSteps.putIfAbsent(
          ref.routeIndex, () => List<RouteStep>.of(route.steps));
      steps[ref.stepIndex] = step.copyWith(roundaboutArmCount: armCount);
    }
    if (changedSteps.isEmpty) return routes;

    final enriched = List<RouteResult>.of(routes);
    for (final entry in changedSteps.entries) {
      final route = routes[entry.key];
      enriched[entry.key] = RouteResult(
        polyline: route.polyline,
        steps: entry.value,
        totalDistanceM: route.totalDistanceM,
        totalDurationS: route.totalDurationS,
        speedLimits: route.speedLimits,
        avoidance: route.avoidance,
        fromAvoidanceRouter: route.fromAvoidanceRouter,
      );
    }
    return enriched;
  }

  /// Short-lived cache for repeated submissions/refinements of the same
  /// search. Results are copied on return so callers cannot mutate the cache.
  static final _searchCache =
      <String, ({DateTime at, List<NominatimResult> results})>{};
  static const _searchCacheTtl = Duration(seconds: 45);

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
  static Future<List<NominatimResult>> search(String query,
      {LatLng? near}) async {
    final normalized = query.trim().toLowerCase();
    if (normalized.isEmpty) return [];
    final cacheKey = near == null
        ? normalized
        : '$normalized|${near.latitude.toStringAsFixed(2)},${near.longitude.toStringAsFixed(2)}';
    final cached = _searchCache[cacheKey];
    if (cached != null &&
        DateTime.now().difference(cached.at) < _searchCacheTtl) {
      return List<NominatimResult>.from(cached.results);
    }
    try {
      // Six results are enough for suggestions and reduce payload/parsing.
      // extratags=1 adds the raw OSM tags, in particular `brand` — see the
      // brand-aware re-ranking below.
      final request = SearchProviderProtocol.nominatimSearch(
        query,
        latitude: near?.latitude,
        longitude: near?.longitude,
      )!;
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: 2 * 1024 * 1024,
        timeout: const Duration(seconds: 5),
      );
      if (res.statusCode != 200) return [];
      final list = jsonDecode(res.body) as List;
      // Parse each entry defensively: one malformed result (bad coordinates,
      // missing field) must skip only itself — mapping the whole list in one
      // go would throw into the outer catch and discard EVERY result.
      final results = <NominatimResult>[];
      for (final e in list) {
        try {
          results.add(NominatimResult.fromJson(e as Map<String, dynamic>));
        } catch (_) {}
      }
      if (near != null) rankByBrandThenDistance(results, normalized, near);
      _searchCache[cacheKey] = (at: DateTime.now(), results: results);
      if (_searchCache.length > 24) {
        final oldest = _searchCache.entries
            .reduce((a, b) => a.value.at.isBefore(b.value.at) ? a : b)
            .key;
        _searchCache.remove(oldest);
      }
      return results;
    } catch (_) {
      return [];
    }
  }

  /// Re-ranks [results] in place so a strong OSM `brand` match against
  /// [normalizedQuery] always outranks a merely-closer result.
  ///
  /// Chain franchises are usually tagged with `brand` regardless of what a
  /// given location is called on the sign, and Nominatim's own "importance"
  /// ranking has no idea two same-named results are a franchise and an
  /// unrelated shop that merely shares generic wording — a search for a
  /// well-known franchise ("Mercatino dell'Usato") could otherwise put a
  /// same-worded but unrelated secondhand shop ahead of the real one just
  /// for being a few hundred metres closer. A strong brand match is treated
  /// as a harder signal than proximity and sorted first as a group;
  /// distance still decides within each group. Exposed separately (rather
  /// than inlined in [search]) so this ranking rule is testable without a
  /// network call.
  @visibleForTesting
  static void rankByBrandThenDistance(
      List<NominatimResult> results, String normalizedQuery, LatLng near) {
    double brandScore(NominatimResult r) =>
        r.brand == null ? 0.0 : FuzzyMatch.score(normalizedQuery, r.brand!);
    results.sort((a, b) {
      final aBrand = brandScore(a) >= 0.66;
      final bBrand = brandScore(b) >= 0.66;
      if (aBrand != bBrand) return aBrand ? -1 : 1;
      return const Distance()
          .as(LengthUnit.Meter, near, a.position)
          .compareTo(const Distance().as(LengthUnit.Meter, near, b.position));
    });
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
      return detail.display
          .split(',')
          .map((s) => s.trim())
          .where((s) => s.isNotEmpty)
          .take(parts)
          .join(', ');
    } catch (_) {
      return null;
    }
  }

  /// Reverse-geocodes [point] to a short, recognisable place label
  /// ("Via Roberto Ricci 12, Torino") for history entries and route labels.
  /// See [shortLabelFrom] for why this is not `reverseGeocode(parts: 1)`.
  static Future<String?> reverseGeocodeLabel(LatLng point) async {
    try {
      return (await reverseGeocodeDetail(point))?.label;
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
  ///   - `label`: a short, human-recognisable name for the place — see
  ///     [shortLabelFrom].
  static Future<
      ({
        String display,
        String? wikiQuery,
        String? openingHours,
        String label,
      })?> reverseGeocodeDetail(LatLng point) async {
    try {
      // extratags=1 adds the raw OSM tags of the matched element, including
      // `opening_hours` (parsed client-side for the open/closed badge). No
      // extra location is disclosed — the coordinate is already sent for the
      // reverse lookup itself.
      final request = SearchProviderProtocol.nominatimReverse(
        latitude: point.latitude,
        longitude: point.longitude,
      );
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: 2 * 1024 * 1024,
        timeout: const Duration(seconds: 5),
      );
      if (res.statusCode != 200) return null;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final display = data['display_name'] as String? ?? '';
      final addr = data['address'] as Map<String, dynamic>? ?? {};
      final extra = data['extratags'] as Map<String, dynamic>? ?? {};
      final openingHours = (extra['opening_hours'] as String?)?.trim();

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
        addr['tourism'] as String?,
        addr['amenity'] as String?,
        addr['historic'] as String?,
        addr['leisure'] as String?,
        addr['suburb'] as String?,
        addr['quarter'] as String?,
        addr['neighbourhood'] as String?,
      ].where((s) => s != null && s.isNotEmpty && !isNumber(s)).firstOrNull;

      // City / municipality for geographic disambiguation
      final city = [
        addr['city'] as String?,
        addr['town'] as String?,
        addr['village'] as String?,
        addr['municipality'] as String?,
        addr['county'] as String?,
      ].where((s) => s != null && s.isNotEmpty && !isNumber(s)).firstOrNull;

      // Combine POI name with city so that generic names like "Teodorico"
      // become "Teodorico Torino" — making the Wikipedia / web-search
      // fallback much more accurate without affecting the geo-based lookup.
      String? wikiQuery;
      if (poiName != null) {
        wikiQuery =
            (city != null && city != poiName) ? '$poiName $city' : poiName;
      } else {
        wikiQuery = city;
      }

      return (
        display: display,
        wikiQuery: wikiQuery,
        openingHours: (openingHours != null && openingHours.isNotEmpty)
            ? openingHours
            : null,
        label: shortLabelFrom(display, addr, name: data['name'] as String?),
      );
    } catch (_) {
      return null;
    }
  }

  /// Builds a short label a human can recognise in a history / favourites list.
  ///
  /// The naive `display_name.split(',').first` is wrong for most of Europe:
  /// Nominatim formats street addresses house-number-first, so it yields a bare
  /// "12" — every entry in the history then looks like an anonymous number next
  /// to a pin. This uses the structured `address` block instead and rebuilds
  /// "Via Roberto Ricci 12, Torino".
  ///
  /// [addr] is Nominatim's `address` object; [name] its top-level `name` field
  /// (set for POIs). [display] is only the last-resort fallback.
  static String shortLabelFrom(String display, Map<String, dynamic> addr,
      {String? name}) {
    // Every component comes from a third-party geocoder and ends up persisted
    // in the history box and rendered in one-line list tiles. Bound each part
    // and strip control characters at the door, the same way OsmPoiDetails
    // treats raw OSM tags.
    const maxPart = 80;
    String? str(Object? v) {
      if (v is! String) return null;
      final s = v.replaceAll(RegExp(r'[\u0000-\u001f]'), ' ').trim();
      if (s.isEmpty) return null;
      return s.length <= maxPart ? s : '${s.substring(0, maxPart)}…';
    }

    final city = str(addr['city']) ??
        str(addr['town']) ??
        str(addr['village']) ??
        str(addr['hamlet']) ??
        str(addr['municipality']);
    final road = str(addr['road']) ?? str(addr['pedestrian']);
    final houseNo = str(addr['house_number']);

    // A named POI wins: "Ospedale Santa Maria delle Croci" beats its street.
    final poi = str(name) ??
        str(addr['amenity']) ??
        str(addr['shop']) ??
        str(addr['tourism']) ??
        str(addr['historic']) ??
        str(addr['leisure']);
    if (poi != null && !RegExp(r'^\d+$').hasMatch(poi)) {
      return city != null && city != poi ? '$poi, $city' : poi;
    }

    if (road != null) {
      final street = houseNo != null ? '$road $houseNo' : road;
      return city != null ? '$street, $city' : street;
    }

    // No street either (open country, a square, a place node): fall back to the
    // first display component that is not a bare house number.
    final parts = display
        .split(',')
        .map((p) => str(p))
        .whereType<String>()
        .where((p) => !RegExp(r'^\d+$').hasMatch(p))
        .toList();
    if (parts.isEmpty) return city ?? display.split(',').first.trim();
    return parts.length > 1 && city != null && parts.first != city
        ? '${parts.first}, $city'
        : parts.first;
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
  static Future<RouteResult?> getRoute(LatLng origin, LatLng destination,
      {RoutingProvider provider = RoutingProvider.osrm,
      String? apiKey,
      String? graphhopperServer,
      String lang = 'en',
      String vehicle = 'driving'}) async {
    try {
      if (provider == RoutingProvider.openRoute && apiKey != null) {
        final request = RoutingRequestProtocol.openRouteService(
          origin: RoutingRequestPoint(origin.latitude, origin.longitude),
          destination:
              RoutingRequestPoint(destination.latitude, destination.longitude),
          apiKey: apiKey,
          languageCode: lang,
          vehicle: vehicle,
        );
        final res = await BoundedHttp.post(
          request.uri,
          headers: request.headers,
          body: request.body,
          maxBytes: _maxRouteResponseBytes,
          timeout: const Duration(seconds: 10),
        );
        if (res.statusCode != 200) {
          throw RoutingException(
              statusCode: res.statusCode,
              body: res.body,
              message: 'OpenRouteService HTTP error');
        }

        if (res.bodyBytes.length > _maxRouteResponseBytes) {
          throw RoutingException(message: 'Routing response too large');
        }
        return RoutingResponseProtocol.parseOpenRouteService(
          res.body,
          fallbackOrigin: origin,
        );
      }

      if (provider == RoutingProvider.graphHopper) {
        // GraphHopper: support public API (apiKey) or self-hosted server (graphhopperServer)
        final server =
            RoutingRequestProtocol.graphHopperEndpoint(graphhopperServer);
        validateGraphhopperServerUrl(server);
        final request = RoutingRequestProtocol.graphHopperRoute(
          origin: RoutingRequestPoint(origin.latitude, origin.longitude),
          destination:
              RoutingRequestPoint(destination.latitude, destination.longitude),
          server: server,
          languageCode: lang,
          vehicle: vehicle,
          apiKey: apiKey,
        );

        final res = await BoundedHttp.get(
          request.uri,
          headers: request.headers,
          maxBytes: _maxRouteResponseBytes,
          timeout: const Duration(seconds: 12),
        );
        if (res.statusCode != 200) {
          throw RoutingException(
              statusCode: res.statusCode,
              body: res.body,
              message: 'GraphHopper HTTP error');
        }
        if (res.bodyBytes.length > _maxRouteResponseBytes) {
          throw RoutingException(message: 'Routing response too large');
        }
        final route = RoutingResponseProtocol.parseGraphHopper(
          res.body,
          fallbackOrigin: origin,
        );
        debugPrint('[Routing] GH speedLimits: ${route.speedLimits.length} entries'
            ' (non-null: ${route.speedLimits.where((entry) => entry.speedKmh != null).length})');
        return route;
      }

      // Fallback / default: OSRM — choose the right public server for the mode.
      final request = RoutingRequestProtocol.osrmRoute(
        origin: RoutingRequestPoint(origin.latitude, origin.longitude),
        destination:
            RoutingRequestPoint(destination.latitude, destination.longitude),
        vehicle: vehicle,
      );

      // NOTE: no annotations=maxspeed — that is a Mapbox Directions extension,
      // vanilla OSRM (including FOSSGIS) rejects it with 400. Speed limits
      // come from SpeedLimitService (Overpass) instead.
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: _maxRouteResponseBytes,
        timeout: const Duration(seconds: 10),
      );
      if (res.statusCode != 200) {
        throw RoutingException(
            statusCode: res.statusCode,
            body: res.body,
            message: 'OSRM HTTP error');
      }
      if (res.bodyBytes.length > _maxRouteResponseBytes) {
        throw RoutingException(message: 'Routing response too large');
      }
      return RoutingResponseProtocol.parseOsrmRoutes(
        res.body,
        languageCode: lang,
      ).first;
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
  ///
  /// [originBearingDeg], when given, tells OSRM which way the vehicle is
  /// actually facing at [origin], with a tolerance wide enough for a normal
  /// turn but not an instant reversal — see [rerouteBearingToleranceDeg] for
  /// why this specific value and what happens without it.
  /// Most intermediate stops a journey may carry.
  ///
  /// Five points in total including the destination, which is what the planner
  /// offers. The ceiling is not arbitrary: every extra point multiplies the
  /// router's work, and a routing engine asked for a dozen stops starts
  /// returning a route that is technically optimal and useless to drive.
  static const maxWaypoints =
      RoutingRequestProtocol.maxIntermediateWaypoints;

  static Future<List<RouteResult>> getRoutes(LatLng origin, LatLng destination,
      {RoutingProvider provider = RoutingProvider.osrm,
      String? apiKey,
      String? graphhopperServer,
      String lang = 'en',
      String vehicle = 'driving',
      double? originBearingDeg,
      List<LatLng> via = const [],
      @visibleForTesting Uri? endpoint}) async {
    if (provider != RoutingProvider.osrm) {
      final single = await getRoute(origin, destination,
          provider: provider,
          apiKey: apiKey,
          graphhopperServer: graphhopperServer,
          lang: lang,
          vehicle: vehicle);
      return single != null ? [single] : [];
    }
    try {
      // Trailing `;` deliberately leaves the destination unconstrained — only
      // the origin (the vehicle's current position and facing) is pinned.
      // Verified against this exact endpoint: the same two points 400 m apart
      // resolve to a 2 m degenerate route with no bearing given, and to a
      // realistic ~460 m route via the next junction once the origin bearing
      // is constrained to face away from the direct line.
      // OSRM takes any number of semicolon-separated coordinates and visits
      // them in order. Alternatives are requested only when no stop pins the
      // route; the shared protocol preserves that distinction and the cap.
      final request = RoutingRequestProtocol.osrmRoute(
        origin: RoutingRequestPoint(origin.latitude, origin.longitude),
        destination:
            RoutingRequestPoint(destination.latitude, destination.longitude),
        vehicle: vehicle,
        via: [
          for (final point in via)
            RoutingRequestPoint(point.latitude, point.longitude),
        ],
        requestAlternatives: true,
        originBearingDegrees: originBearingDeg,
        endpoint: endpoint?.toString(),
      );

      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: _maxRouteResponseBytes,
        timeout: const Duration(seconds: 10),
      );
      if (res.statusCode != 200) {
        throw RoutingException(
            statusCode: res.statusCode,
            body: res.body,
            message: 'OSRM HTTP error');
      }
      if (res.bodyBytes.length > _maxRouteResponseBytes) {
        throw RoutingException(message: 'Routing response too large');
      }
      return RoutingResponseProtocol.parseOsrmRoutes(
        res.body,
        languageCode: lang,
      );
    } on RoutingException {
      rethrow;
    } catch (e) {
      throw RoutingException(message: e.toString());
    }
  }

  /// Whether a bearing-constrained reroute went somewhere it should not have.
  ///
  /// A bearing hint fixes routes that assumed an impossible instant reversal,
  /// but the same mechanism can misfire the other way: if the road network
  /// genuinely offers nothing matching that facing nearby, OSRM may search
  /// much further afield to satisfy the constraint rather than admit defeat.
  /// The caller's job is to catch that and fall back to an unconstrained
  /// reroute instead — this is the check that tells it to.
  ///
  /// Generous on purpose: a real detour around a river, a rail line or a
  /// gated estate can legitimately run several times the straight-line
  /// distance, and this must never be the reason a genuinely necessary detour
  /// gets rejected. It exists to catch the pathological case, not to second-
  /// guess an ordinary one.
  static bool isImplausibleReroute(
      double routeDistanceM, double straightLineDistanceM) {
    const floorM = 5000.0;
    const factor = 8.0;
    return routeDistanceM > straightLineDistanceM * factor + floorM;
  }

  /// How close a route has to come to a reported jam to count as still going
  /// through it.
  ///
  /// Wide enough to cover the road itself — both carriageways, and the slack
  /// between where a driver drops a report and where the queue actually is —
  /// but deliberately narrower than a city block, because the parallel street
  /// one block over IS the detour being looked for and must not be rejected
  /// as "still passing the jam".
  static const jamAvoidanceRadiusM = 80.0;

  /// Whether [polyline] comes within [radiusM] of [point].
  ///
  /// Used to tell a genuine detour around a reported jam from the same road
  /// handed back again: asking a deterministic engine to recalculate the same
  /// two points returns the same route, so the alternatives have to be checked
  /// rather than trusted.
  static bool passesNear(List<LatLng> polyline, LatLng point,
      {double radiusM = jamAvoidanceRadiusM}) {
    if (polyline.isEmpty) return false;
    if (polyline.length == 1) {
      return Geo.distanceM(polyline.first, point) <= radiusM;
    }
    for (var i = 0; i < polyline.length - 1; i++) {
      if (Geo.distanceToSegmentM(point, polyline[i], polyline[i + 1]) <=
          radiusM) {
        return true;
      }
    }
    return false;
  }

  /// Calculates the extra driving route shown by the combined avoidance
  /// switch. A hard exclusion is attempted first. If the graph cannot connect
  /// the endpoints without those road classes, a documented OsmAnd-style soft
  /// preference is used so the user still receives the best feasible route.
  ///
  /// This intentionally does not send OSRM's optional `exclude` parameter:
  /// the public OSRM profiles used by Roadstr do not configure the necessary
  /// excludable sets and return HTTP 400 ("Exclude flag combination is not
  /// supported", verified live). Valhalla applies OSM access/toll/road
  /// classification rules per region and reports whether the result still
  /// contains a highway or toll segment. That result drives the UI wording.
  ///
  /// The travel time, however, does **not** come from Valhalla: see
  /// [_retimedThroughOsrm].
  static Future<RouteResult> getHighwayAndTollAvoidanceRoute(
      LatLng origin, LatLng destination,
      {String lang = 'en',
      @visibleForTesting Uri? endpoint,
      @visibleForTesting Uri? retimeEndpoint}) async {
    final RouteResult route;
    try {
      route = await _getValhallaAvoidanceRoute(
        origin,
        destination,
        lang: lang,
        endpoint: endpoint,
        hardExclusion: true,
      );
    } on RoutingException {
      return _retimedThroughOsrm(
        await _getValhallaAvoidanceRoute(
          origin,
          destination,
          lang: lang,
          endpoint: endpoint,
          hardExclusion: false,
        ),
        endpoint: retimeEndpoint,
        stubbedValhalla: endpoint != null,
      );
    }
    return _retimedThroughOsrm(route,
        endpoint: retimeEndpoint, stubbedValhalla: endpoint != null);
  }

  /// Calculates a route that strongly disfavours unpaved "off-road" tracks —
  /// the routing side of a field report where the default OSRM route sent a
  /// driver down one. Unlike [getHighwayAndTollAvoidanceRoute] there is no
  /// hard exclude to attempt first: Valhalla has no boolean "never a track"
  /// switch for car costing, only the continuous `use_tracks` weight, so this
  /// is a single request and the result is always reported as a preference,
  /// never a guarantee — see [RouteAvoidance.offRoadAvoided]. A destination
  /// only reachable via a short track (a driveway, a farm gate) still gets a
  /// route rather than none at all, which is the point: this steers the
  /// driver away from the roads that nearly caused a crash without ever
  /// refusing to navigate at all.
  ///
  /// Re-timed through OSRM for the same reason as the highway/toll route —
  /// see [_retimedThroughOsrm].
  static Future<RouteResult> getOffRoadAvoidanceRoute(
      LatLng origin, LatLng destination,
      {String lang = 'en',
      @visibleForTesting Uri? endpoint,
      @visibleForTesting Uri? retimeEndpoint}) async {
    final route = await _getValhallaAutoRoute(
      origin,
      destination,
      lang: lang,
      endpoint: endpoint,
      costingPolicy: ValhallaCostingPolicy.avoidTracks,
      classify: (_) => RouteAvoidance.offRoadAvoided,
    );
    return _retimedThroughOsrm(route,
        endpoint: retimeEndpoint, stubbedValhalla: endpoint != null);
  }

  /// One waypoint roughly every this many metres when re-timing a route.
  ///
  /// Measured against live servers on routes from 34 km to 645 km: at 5 km
  /// spacing OSRM reproduces the Valhalla road almost exactly everywhere
  /// (Torino→Florence, Caltabellotta→Messina, Udine→Genoa, Munich→Vienna).
  /// Sparser sampling lets it wander (25 waypoints over Udine→Genoa drifted
  /// 2.6 km off), and denser sampling is worse, not better: waypoints closer
  /// than a couple of kilometres start snapping onto parallel service roads
  /// and inject detours of their own.
  static const _retimeSpacingM = 5000.0;

  /// Waypoint ceiling for one re-timing request. 120 keeps the URL near 2 kB.
  static const _retimeMaxWaypoints = 120;

  /// Re-times the avoidance route with OSRM — the engine that timed every
  /// other route on screen — instead of trusting Valhalla's clock.
  ///
  /// Valhalla and OSRM disagree profoundly about how fast a secondary road is
  /// driven. Measured live: the motorway-free Torino→Florence route is
  /// 135.4 km in 200 minutes according to Valhalla and 143 according to OSRM;
  /// Caltabellotta→Messina 621 vs 454; Udine→Genoa 639 vs 559. Since the other
  /// cards are OSRM's, Valhalla's number turned a sensible "a few minutes
  /// longer than the motorway" into an absurd hour and a half — the avoidance
  /// option looked like a detour it never made.
  ///
  /// OSRM cannot exclude motorways, but it can be *forced along* a geometry:
  /// routed through waypoints sampled from the Valhalla shape it drives the
  /// same roads and reports its own timing for them.
  ///
  /// The check is per leg, not per route. Each leg spans one ~5 km slice of
  /// the Valhalla shape, so comparing the leg's length with that slice's arc
  /// length says whether OSRM really followed it there. Legs that match
  /// contribute OSRM's time; legs that don't keep Valhalla's share for that
  /// slice. A single stretch OSRM refuses to follow — they exist, and they are
  /// regional — therefore costs the accuracy of that stretch alone instead of
  /// the whole re-timing. If less than half the distance could be verified, or
  /// anything fails, the Valhalla figures are kept unchanged.
  static Future<RouteResult> _retimedThroughOsrm(RouteResult route,
      {Uri? endpoint, bool stubbedValhalla = false}) async {
    // A stubbed Valhalla with no stubbed OSRM means a test: never reach out to
    // the public server behind the test's back.
    if (stubbedValhalla && endpoint == null) return route;
    final line = route.polyline;
    if (line.length < 2 || route.totalDistanceM <= 0) return route;
    try {
      // Cumulative distance along the shape: the arc length of every slice.
      final cumulative = List<double>.filled(line.length, 0);
      // How many sea crossings precede each point, so a slice containing one
      // can be recognised in O(1) below.
      final crossings = List<int>.filled(line.length, 0);
      for (var i = 1; i < line.length; i++) {
        final step = Geo.distanceM(line[i - 1], line[i]);
        cumulative[i] = cumulative[i - 1] + step;
        crossings[i] = crossings[i - 1] + (step > _maxRoadStepM ? 1 : 0);
      }
      final shapeLength = cumulative.last;
      if (shapeLength <= 0) return route;

      final sampleCount = (shapeLength / _retimeSpacingM).round().clamp(
                3,
                _retimeMaxWaypoints - 1,
              ) +
          1;
      final indices = [
        for (var i = 0; i < sampleCount; i++)
          (i * (line.length - 1) / (sampleCount - 1)).round(),
      ];
      final waypoints = indices
          .map((i) => '${line[i].longitude.toStringAsFixed(5)},'
              '${line[i].latitude.toStringAsFixed(5)}')
          .join(';');

      final request = RoutingRequestProtocol.osrmRetime(
        waypoints: waypoints,
        endpoint: endpoint?.toString(),
      );
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: _maxRouteResponseBytes,
        timeout: const Duration(seconds: 30),
      );
      if (res.statusCode != 200) return route;
      final legs = RoutingResponseProtocol.parseOsrmRetimeLegs(res.body);
      if (legs == null || legs.length != sampleCount - 1) return route;

      var seconds = 0.0;
      var verifiedM = 0.0;
      var ferryM = 0.0;
      for (var i = 0; i < legs.length; i++) {
        final arcM = cumulative[indices[i + 1]] - cumulative[indices[i]];
        if (arcM <= 0) continue;
        final legM = legs[i].distanceM;
        final legS = legs[i].durationS;
        if (legM == null || legS == null || !legM.isFinite || !legS.isFinite) {
          return route;
        }
        // A slice containing a sea crossing keeps Valhalla's time: it reads the
        // ferry's own scheduled duration from OSM, where a road router can only
        // guess at a speed — and OSRM may not even take the same boat.
        final crossesWater = crossings[indices[i + 1]] > crossings[indices[i]];
        // 20 % (or 150 m on very short slices) of slack absorbs the difference
        // between two engines' geometry; a leg that left the road entirely is
        // far outside it.
        if (!crossesWater &&
            (legM - arcM).abs() <= math.max(150.0, 0.2 * arcM)) {
          seconds += legS;
          verifiedM += arcM;
        } else {
          seconds += route.totalDurationS * arcM / shapeLength;
          if (crossesWater) ferryM += arcM;
        }
      }
      // Sea crossings are excluded from the "did we verify enough?" budget:
      // a Naples→Palermo route is 80 % boat, and the 20 % of driving around it
      // is still worth timing properly.
      final roadLength = shapeLength - ferryM;
      if (verifiedM < 0.5 * roadLength || seconds <= 0) {
        debugPrint('[Avoidance] re-timing rejected: only '
            '${(verifiedM / math.max(roadLength, 1) * 100).round()} % of '
            '${(roadLength / 1000).round()} road km followed the same road');
        return route;
      }
      debugPrint('[Avoidance] re-timed ${(roadLength / 1000).round()} road km '
          '(${(verifiedM / math.max(roadLength, 1) * 100).round()} % verified '
          'over ${legs.length} legs'
          '${ferryM > 0 ? ', ${(ferryM / 1000).round()} km by sea' : ''}): '
          '${(route.totalDurationS / 60).round()} min '
          '→ ${(seconds / 60).round()} min');

      return RouteResult(
        polyline: route.polyline,
        steps: route.steps,
        totalDistanceM: route.totalDistanceM,
        totalDurationS: seconds,
        speedLimits: route.speedLimits,
        avoidance: route.avoidance,
        fromAvoidanceRouter: true,
      );
    } catch (_) {
      return route; // best-effort: a failed re-timing is not a failed route
    }
  }

  /// True when two routes drive down the same roads.
  ///
  /// The avoidance route comes from Valhalla while every other route on screen
  /// comes from the user's provider (OSRM by default), and the two engines
  /// disagree substantially about travel speed on secondary roads. When the
  /// recommended route already avoids motorways and tolls, the avoidance
  /// router returns that very same road — and showing it a second time with
  /// Valhalla's ETA reads as "the identical route now takes 47 minutes
  /// longer". This check lets the caller recognise the duplicate and keep a
  /// single card with a single, comparable ETA.
  ///
  /// Two routes match when their lengths agree within 2 % and every sampled
  /// point of each polyline lies within [toleranceM] of the other polyline —
  /// so a route that only detours around one toll section is *not* a match.
  static bool followSameRoads(RouteResult a, RouteResult b,
      {double toleranceM = 45}) {
    if (a.polyline.length < 2 || b.polyline.length < 2) return false;
    final la = a.totalDistanceM, lb = b.totalDistanceM;
    if (la <= 0 || lb <= 0) return false;
    if ((la - lb).abs() / math.max(la, lb) > 0.02) return false;
    return _insideCorridor(a.polyline, b.polyline, toleranceM) &&
        _insideCorridor(b.polyline, a.polyline, toleranceM);
  }

  /// True when [samples] evenly spaced points of [line] all lie within
  /// [toleranceM] of [reference].
  static bool _insideCorridor(
      List<LatLng> line, List<LatLng> reference, double toleranceM,
      {int samples = 48}) {
    final step = math.max(1, (line.length / samples).floor());
    for (var i = 0; i < line.length; i += step) {
      if (Geo.distanceToPolylineM(line[i], reference) > toleranceM) {
        return false;
      }
    }
    return Geo.distanceToPolylineM(line.last, reference) <= toleranceM;
  }

  static Future<RouteResult> _getValhallaAvoidanceRoute(
      LatLng origin, LatLng destination,
      {required String lang,
      required Uri? endpoint,
      required bool hardExclusion}) {
    return _getValhallaAutoRoute(
      origin,
      destination,
      lang: lang,
      endpoint: endpoint,
      costingPolicy: hardExclusion
          ? ValhallaCostingPolicy.hardHighwayAndTollExclusion
          : ValhallaCostingPolicy.softHighwayAndTollAvoidance,
      classify: (summary) {
        final hasHighway = summary['has_highway'] == true;
        final hasToll = summary['has_toll'] == true;
        if (hardExclusion && (hasHighway || hasToll)) {
          throw RoutingException(
            message: 'Hard avoidance route still contains an excluded road',
          );
        }
        return hasHighway || hasToll
            ? RouteAvoidance.minimizedHighwaysAndTolls
            : RouteAvoidance.highwayAndTollFree;
      },
    );
  }

  /// Requests a route from Valhalla with [costingPolicy] applied to the
  /// `auto` profile, and parses it the same way regardless of which
  /// avoidance policy asked for it. [classify] turns the response summary
  /// into the [RouteAvoidance] the caller wants reported — it may also throw
  /// a [RoutingException] to reject a route that does not meet a hard
  /// requirement (as [_getValhallaAvoidanceRoute] does for a hard exclusion
  /// that Valhalla could not actually honour).
  ///
  /// Shared by [_getValhallaAvoidanceRoute] (highways/tolls) and
  /// [getOffRoadAvoidanceRoute] (`use_tracks`) — the request/parse plumbing
  /// neither cares about, only the costing knob and the resulting label do.
  static Future<RouteResult> _getValhallaAutoRoute(
      LatLng origin, LatLng destination,
      {required String lang,
      required Uri? endpoint,
      required ValhallaCostingPolicy costingPolicy,
      required RouteAvoidance Function(Map<String, dynamic> summary)
          classify}) async {
    try {
      final request = RoutingRequestProtocol.valhalla(
        origin: RoutingRequestPoint(origin.latitude, origin.longitude),
        destination:
            RoutingRequestPoint(destination.latitude, destination.longitude),
        languageCode: lang,
        costingPolicy: costingPolicy,
        endpoint: endpoint?.toString(),
      );
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: _maxRouteResponseBytes,
        timeout: const Duration(seconds: 25),
      );
      if (res.statusCode != 200) {
        throw RoutingException(
          statusCode: res.statusCode,
          body: res.body,
          message: 'Valhalla HTTP error',
        );
      }
      final parsed = RoutingResponseProtocol.parseValhalla(res.body);
      return parsed.route.withAvoidance(classify(parsed.summary));
    } on RoutingException {
      rethrow;
    } catch (e) {
      throw RoutingException(message: e.toString());
    }
  }

  /// A step in the route shape longer than this is not a road.
  ///
  /// Shape points sit on road nodes, so they are metres to hundreds of metres
  /// apart; across thirty live routes on six continents the widest gap on
  /// tarmac was 1.9 km. A jump of tens or hundreds of kilometres is a sea
  /// crossing: OSM draws a ferry route as a way with barely any nodes, so
  /// Naples→Palermo arrives as a single 305 km straight line. Those slices are
  /// real parts of the journey — they are simply not slices OSRM's road timing
  /// can say anything about. See [_retimedThroughOsrm].
  static const _maxRoadStepM = 25000.0;

  /// OpenRouteService rejects a `language` it does not know with an HTTP 400
  /// — and a rejected request means no route at all, not an English one — so
  /// the app's language is mapped onto ORS's own list, English otherwise. Its
  /// codes are not ISO 639-1 throughout: Greek is `gr`, Ukrainian `ua`.
  @visibleForTesting
  static String orsLanguage(String languageCode) =>
      RoutingRequestProtocol.openRouteServiceLanguage(languageCode);

  /// Removes straight `new name` pseudo-maneuvers while preserving distance.
  ///
  /// OSM commonly assigns several names to consecutive sections of one
  /// physical road. Routers expose each rename as a maneuver even though the
  /// driver does nothing. Speaking all of them creates a rapid sequence of
  /// contradictory-sounding instructions immediately before a real ramp.
  @visibleForTesting
  static List<RouteStep> coalescePassiveNameChanges(List<RouteStep> steps) =>
      RoutingResponseProtocol.coalescePassiveNameChanges(steps);

  /// Drops roundabout/exit decorations that are out of range instead of
  /// rejecting the route that carries them.
  ///
  /// These two fields decorate an icon: the exit count inside the roundabout
  /// symbol and the "199" on an exit sign. Refusing the whole route over one
  /// would leave the driver unable to navigate at all because a roundabout has
  /// thirteen arms, or because a router put something unexpected in a label —
  /// a far worse outcome than a roundabout icon with no number in it. Every
  /// other response check guards something that would actually
  /// break: NaN coordinates, absurd geometry, unbounded strings.
  @visibleForTesting
  static List<RouteStep> sanitiseDecorations(List<RouteStep> steps) =>
      RoutingResponseProtocol.sanitiseDecorations(steps);

  /// Refuses a self-hosted GraphHopper URL that would silently fail (or,
  /// were the app's cleartext policy ever loosened to make it "work",
  /// silently send origin/destination coordinates in plaintext).
  ///
  /// The Settings hint for this field is "http://localhost:8989/route" — the
  /// ordinary shape of a routing engine on the same device — and the app's
  /// network security config permits cleartext to exactly that host plus
  /// 127.0.0.1 and the Android emulator's 10.0.2.2 alias for it, nothing
  /// else. An http:// URL to any other host is refused outright rather than
  /// attempted and left to fail on the network layer with no clear reason,
  /// and rather than "fixed" by weakening the cleartext policy generally —
  /// a self-hosted server anywhere but this device needs real HTTPS.
  static void validateGraphhopperServerUrl(String server) {
    switch (RoutingEndpointPolicy.graphHopperDecision(server)) {
      case RoutingEndpointDecision.accepted:
        return;
      case RoutingEndpointDecision.invalid:
        throw RoutingException(message: 'Invalid GraphHopper server URL');
      case RoutingEndpointDecision.cleartextRejected:
        throw RoutingException(
            message: 'Self-hosted GraphHopper must use HTTPS, unless it is '
                'running on localhost/127.0.0.1');
    }
  }

  /// Test a GraphHopper server URL for connectivity and basic response.
  ///
  /// The probe route uses Null Island (0,0 → 0.1,0.1), which no real map
  /// covers — so a healthy server answers HTTP 400 "Cannot find point".
  /// That error PROVES the server is a reachable, routing-capable
  /// GraphHopper instance and is treated as success; only unreachable hosts,
  /// auth failures, or non-GraphHopper responses fail the test.
  static Future<void> testGraphHopperServer(String server,
      {String? apiKey}) async {
    validateGraphhopperServerUrl(server);
    final request = RoutingRequestProtocol.graphHopperProbe(
      server: server,
      apiKey: apiKey,
    );
    try {
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: 2 * 1024 * 1024,
        timeout: const Duration(seconds: 8),
      );
      if (res.statusCode == 400 &&
          (res.body.contains('Cannot find point') ||
              res.body.contains('PointNotFoundException') ||
              res.body.contains('PointOutOfBoundsException'))) {
        return; // reachable GraphHopper that simply doesn't cover Null Island
      }
      if (res.statusCode != 200) {
        throw RoutingException(
            statusCode: res.statusCode, body: res.body, message: 'Ping failed');
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (data['paths'] == null) {
        throw RoutingException(message: 'No paths in response', body: res.body);
      }
      return;
    } catch (e) {
      if (e is RoutingException) rethrow;
      throw RoutingException(message: e.toString());
    }
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
    required this.title,
    required this.extract,
    this.imageUrl,
    this.pageUrl,
  });
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
    double lat,
    double lon, {
    String lang = 'en',
    String? fallbackQuery,
    int radiusM = 500,
  }) async {
    try {
      // Step 1: geo-search Wikipedia → articles near the tapped coordinates.
      final geoUri = Uri.parse('https://$lang.wikipedia.org/w/api.php'
          '?action=query&list=geosearch'
          '&gscoord=${lat.toStringAsFixed(6)}|${lon.toStringAsFixed(6)}'
          '&gsradius=$radiusM&gslimit=3&format=json&origin=*');
      final geoRes = await BoundedHttp.get(
        geoUri,
        headers: {'User-Agent': 'Roadstr/1.0'},
        maxBytes: 2 * 1024 * 1024,
        timeout: const Duration(seconds: 5),
      );
      if (geoRes.statusCode == 200) {
        final geoData = jsonDecode(geoRes.body) as Map<String, dynamic>;
        final hits = (geoData['query']?['geosearch'] as List?) ?? [];
        Map<String, dynamic>? selected;
        for (final rawHit in hits.whereType<Map<String, dynamic>>()) {
          final title = rawHit['title'] as String?;
          final distanceM = (rawHit['dist'] as num?)?.toDouble();
          if (title == null || distanceM == null || !distanceM.isFinite) {
            continue;
          }
          // A nearby article is not necessarily an article ABOUT this POI.
          // Accept exact-coordinate landmarks, or require a meaningful token
          // shared with the Nominatim-derived place name. This prevents a small
          // shop from inheriting the article of a monument 150 m away.
          final normalizedTitle = title.toLowerCase();
          final normalizedQuery = fallbackQuery?.toLowerCase();
          final queryTokens = normalizedQuery
                  ?.split(RegExp(r'[\s,;:()\-\u2013\u2014/]+'))
                  .where((token) => token.length >= 4) ??
              const Iterable<String>.empty();
          final nameMatches = queryTokens.any(normalizedTitle.contains);
          if (distanceM <= 35 || nameMatches) {
            selected = rawHit;
            break;
          }
        }
        if (selected != null) {
          final title = Uri.encodeComponent(selected['title'] as String);
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
  /// retries in English. Disambiguation pages are skipped because their
  /// `extract` is not useful for the place-info panel.
  static Future<WikiSummary?> fetchWikiSummary(String query,
      {String lang = 'en'}) async {
    try {
      final encoded = Uri.encodeComponent(query);
      for (final l in [lang, if (lang != 'en') 'en']) {
        final uri = Uri.parse(
            'https://$l.wikipedia.org/api/rest_v1/page/summary/$encoded');
        final res = await BoundedHttp.get(
          uri,
          headers: {'User-Agent': 'Roadstr/1.0'},
          maxBytes: 2 * 1024 * 1024,
          timeout: const Duration(seconds: 5),
        );
        if (res.statusCode != 200) continue;
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final extract = (data['extract'] as String?) ?? '';
        if (extract.isEmpty || data['type'] == 'disambiguation') continue;
        return WikiSummary(
          title: data['title'] as String? ?? query,
          extract: extract,
          imageUrl: (data['thumbnail'] as Map<String, dynamic>?)?['source']
              as String?,
          pageUrl: ((data['content_urls'] as Map?)?['mobile'] as Map?)?['page']
              as String?,
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
  /// geo-disambiguated Wikipedia query (e.g. "Teodorico Torino").
  final String? city;

  /// Raw OSM `opening_hours` string when the source carries it (Overpass POI
  /// results). Parsed client-side into an open/closed badge; null when absent.
  final String? openingHours;

  /// Straight-line distance from the user, in metres, when the result came
  /// from a "nearby" lookup. Null for ordinary search results, where the
  /// origin of the query is not necessarily the user's position.
  final double? distanceM;

  /// OSM `brand` tag (from `extratags`), when the source carries one — chain
  /// franchises are usually tagged this way regardless of what the location
  /// itself is named on the sign. Used to tell a franchise location apart
  /// from an unrelated shop that merely shares generic wording in its name
  /// (a search for "Mercatino Usato" — a well-known Italian franchise — can
  /// otherwise return an unrelated secondhand shop above the real one; see
  /// [RoutingService.search]'s brand-aware re-ranking).
  final String? brand;

  const NominatimResult({
    required this.displayName,
    required this.shortName,
    required this.position,
    this.cls,
    this.type,
    this.city,
    this.openingHours,
    this.distanceM,
    this.brand,
  });

  /// Longest a name or address coming from a remote geocoder may be before it
  /// is cut. Nothing on the other side of these APIs is under our control: a
  /// compromised or simply broken endpoint can answer with a kilobyte-long
  /// "street name", which a `ListTile` will happily try to lay out and Hive
  /// will happily store in the search history.
  static const _maxRemoteTextChars = 300;

  /// Trims [value] and caps it at [max] characters. Returns null for anything
  /// that is not a usable non-empty string.
  static String? clampRemoteText(dynamic value,
      [int max = _maxRemoteTextChars]) {
    if (value is! String) return null;
    final clean = value.trim();
    if (clean.isEmpty) return null;
    return clean.length <= max ? clean : clean.substring(0, max);
  }

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
          'city' || 'town' => '🏙️',
          'village' || 'hamlet' => '🏘️',
          'suburb' || 'neighbourhood' => '🏡',
          _ => '📍',
        };
      case 'amenity':
        return switch (type) {
          'restaurant' || 'fast_food' || 'food_court' => '🍽️',
          'cafe' || 'coffee_shop' => '☕',
          'bar' || 'pub' || 'nightclub' => '🍺',
          'hospital' || 'clinic' || 'doctors' => '🏥',
          'pharmacy' => '💊',
          'school' || 'kindergarten' => '🏫',
          'university' || 'college' => '🎓',
          'bank' || 'atm' => '🏦',
          'fuel' || 'charging_station' => '⛽',
          'parking' => '🅿️',
          'police' => '👮',
          'post_office' => '📮',
          'library' => '📚',
          'theatre' || 'cinema' => '🎭',
          'place_of_worship' => '⛪',
          'marketplace' => '🛒',
          'townhall' => '🏛️',
          _ => '📍',
        };
      case 'tourism':
        return switch (type) {
          'museum' => '🏛️',
          'hotel' || 'hostel' || 'motel' || 'guest_house' => '🏨',
          'attraction' || 'monument' || 'viewpoint' => '🗺️',
          'artwork' || 'gallery' => '🎨',
          'camp_site' => '⛺',
          'theme_park' || 'zoo' => '🎡',
          _ => '🗺️',
        };
      case 'shop':
        return switch (type) {
          'supermarket' || 'convenience' => '🛒',
          'bakery' => '🥖',
          'clothes' || 'fashion' => '👗',
          'electronics' => '📱',
          'books' => '📚',
          'florist' => '💐',
          _ => '🛍️',
        };
      case 'office':
        return switch (type) {
          'government' || 'administrative' => '🏛️',
          'company' || 'commercial' => '🏢',
          'ngo' || 'association' => '🏢',
          _ => '🏢',
        };
      case 'building':
        return switch (type) {
          'public' || 'government' => '🏛️',
          'hospital' => '🏥',
          'school' || 'university' => '🎓',
          _ => '🏗️',
        };
      case 'natural':
        return switch (type) {
          'beach' => '🏖️',
          'water' || 'lake' => '💧',
          'peak' || 'hill' => '⛰️',
          'wood' || 'forest' => '🌲',
          _ => '🌿',
        };
      case 'leisure':
        return switch (type) {
          'park' || 'garden' => '🌳',
          'sports_centre' || 'stadium' => '🏟️',
          'swimming_pool' => '🏊',
          _ => '🎭',
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
      case 'highway':
        return 'Road';
      case 'place':
        return switch (type) {
          'city' => 'City',
          'town' => 'Town',
          'village' => 'Village',
          _ => 'Place',
        };
      case 'amenity':
        return switch (type) {
          'restaurant' => 'Restaurant',
          'fast_food' => 'Fast food',
          'cafe' => 'Café',
          'bar' || 'pub' => 'Bar / Pub',
          'hospital' => 'Hospital',
          'pharmacy' => 'Pharmacy',
          'school' => 'School',
          'university' => 'University',
          'bank' => 'Bank',
          'atm' => 'ATM',
          'fuel' => 'Petrol station',
          'parking' => 'Parking',
          'police' => 'Police',
          'post_office' => 'Post office',
          'library' => 'Library',
          'theatre' => 'Theatre',
          'cinema' => 'Cinema',
          'place_of_worship' => 'Place of worship',
          'townhall' => 'Town hall',
          _ => 'Service',
        };
      case 'tourism':
        return switch (type) {
          'museum' => 'Museum',
          'hotel' || 'hostel' || 'motel' => 'Hotel',
          'attraction' || 'monument' => 'Attraction / Monument',
          'artwork' => 'Artwork',
          'gallery' => 'Gallery',
          _ => 'Tourism',
        };
      case 'shop':
        return 'Shop';
      case 'office':
        return 'Office';
      case 'historic':
        return 'Historic site';
      case 'leisure':
        return 'Leisure';
      case 'natural':
        return 'Natural area';
      case 'railway':
        return 'Railway / Station';
      case 'aeroway':
        return 'Airport';
      default:
        return null;
    }
  }

  factory NominatimResult.fromJson(Map<String, dynamic> j) {
    final lat = double.tryParse(j['lat'] as String) ?? double.nan;
    final lon = double.tryParse(j['lon'] as String) ?? double.nan;
    if (!lat.isFinite ||
        !lon.isFinite ||
        lat < -90 ||
        lat > 90 ||
        lon < -180 ||
        lon > 180) {
      throw const FormatException('Nominatim: invalid coordinates');
    }
    final display = clampRemoteText(j['display_name']);
    if (display == null) throw const FormatException('Nominatim: no name');
    final clsVal = j['class'] as String?;

    // addressdetails=1 gives structured address components. Use them to build
    // a meaningful shortName instead of the raw first comma-token (often just
    // a house number like "1" for street addresses).
    final addr = (j['address'] as Map<String, dynamic>?) ?? {};
    final road = clampRemoteText(addr['road'], 120);
    final houseNo = clampRemoteText(addr['house_number'], 24);
    final city = clampRemoteText(
        addr['city'] ??
            addr['town'] ??
            addr['village'] ??
            addr['hamlet'] ??
            addr['municipality'],
        120);

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

    final extratags = (j['extratags'] as Map<String, dynamic>?) ?? {};

    return NominatimResult(
      displayName: display,
      shortName: short,
      position: LatLng(lat, lon),
      cls: clampRemoteText(clsVal, 80),
      type: clampRemoteText(j['type'], 80),
      city: city,
      brand: clampRemoteText(extratags['brand'], 160),
    );
  }
}
