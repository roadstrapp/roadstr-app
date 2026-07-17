import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'routing_service.dart' show NominatimResult;

/// Finds points of interest "near me" by category (supermarket, cinema,
/// pharmacy…) or brand/name (Famila, Esselunga…) via the free Overpass API.
///
/// This complements [RoutingService.search] (Nominatim): Nominatim ranks by
/// its own global "importance" score, which for a generic term like "cinema"
/// can put a same-named business on the other side of the world above the
/// actual cinema 500 m away. Overpass instead queries the OSM tag directly
/// within a radius of the user and is inherently local — the category path
/// below is the fix for that specific bug class.
class PoiSearchService {
  // NB: overpass.osm.ch removed — Switzerland-only extract, returns empty
  // success for Italy (see SpeedLimitService._endpoints for the full story).
  static const _endpoints = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
  ];
  static const _radiusM = 8000;

  /// Category keyword -> OSM tag filters (any one may match). Keys are
  /// lowercase, accent-stripped. Currently covers Italian + English; extend
  /// this map to add more languages/categories.
  static final Map<String, List<String>> _categories = {
    // supermarket / grocery
    'supermercato': ['shop=supermarket'], 'supermarket': ['shop=supermarket'],
    'grocery': ['shop=supermarket'], 'alimentari': ['shop=supermarket', 'shop=convenience'],
    // cinema / entertainment
    'cinema': ['amenity=cinema'], 'movie theater': ['amenity=cinema'],
    'teatro': ['amenity=theatre'], 'theatre': ['amenity=theatre'], 'theater': ['amenity=theatre'],
    // fuel
    'benzinaio': ['amenity=fuel'], 'distributore': ['amenity=fuel'],
    'gas station': ['amenity=fuel'], 'petrol station': ['amenity=fuel'], 'fuel': ['amenity=fuel'],
    'colonnina elettrica': ['amenity=charging_station'], 'ev charging': ['amenity=charging_station'],
    // food & drink
    'ristorante': ['amenity=restaurant'], 'restaurant': ['amenity=restaurant'],
    'pizzeria': ['amenity=restaurant;cuisine=pizza'],
    'bar': ['amenity=bar'], 'pub': ['amenity=pub'],
    'caffe': ['amenity=cafe'], 'caffè': ['amenity=cafe'], 'cafe': ['amenity=cafe'], 'coffee': ['amenity=cafe'],
    'fast food': ['amenity=fast_food'],
    // health
    'farmacia': ['amenity=pharmacy'], 'pharmacy': ['amenity=pharmacy'],
    'ospedale': ['amenity=hospital'], 'hospital': ['amenity=hospital'],
    'pronto soccorso': ['amenity=hospital'],
    // money
    'bancomat': ['amenity=atm'], 'atm': ['amenity=atm'],
    'banca': ['amenity=bank'], 'bank': ['amenity=bank'],
    // parking / transit
    'parcheggio': ['amenity=parking'], 'parking': ['amenity=parking'],
    'stazione': ['railway=station'], 'train station': ['railway=station'],
    'aeroporto': ['aeroway=aerodrome'], 'airport': ['aeroway=aerodrome'],
    // lodging
    'hotel': ['tourism=hotel'], 'albergo': ['tourism=hotel'],
    // misc
    'scuola': ['amenity=school'], 'school': ['amenity=school'],
    'posta': ['amenity=post_office'], 'post office': ['amenity=post_office'],
    'chiesa': ['amenity=place_of_worship'], 'church': ['amenity=place_of_worship'],
    'supermercati': ['shop=supermarket'],
  };

  int _endpointIdx = 0;

  /// Searches for POIs matching [query] near [center]. Returns an empty list
  /// if [query] matches no known category and no brand/name hits are found —
  /// callers should fall back to (or merge with) [RoutingService.search].
  Future<List<NominatimResult>> search(String query, LatLng center) async {
    final normalized = _normalize(query);
    final tags = _categories[normalized];
    try {
      if (tags != null) {
        return await _queryTags(tags, center);
      }
      // No category match: try it as a brand/business name (e.g. "Famila",
      // "Esselunga"). Only worth the round trip for non-trivial queries.
      if (normalized.length >= 3) {
        return await _queryName(query, center);
      }
      return [];
    } catch (_) {
      return [];
    }
  }

  Future<List<NominatimResult>> _queryTags(
      List<String> tagFilters, LatLng center) async {
    final clauses = tagFilters.map((f) {
      // "amenity=cinema" or "amenity=restaurant;cuisine=pizza" (extra filters
      // joined with ';' apply as additional exact-match tag constraints).
      final parts = f.split(';').map((p) {
        final kv = p.split('=');
        return '["${kv[0]}"="${kv[1]}"]';
      }).join();
      return 'node$parts(around:$_radiusM,${center.latitude},${center.longitude});'
          'way$parts(around:$_radiusM,${center.latitude},${center.longitude});';
    }).join();
    final query = '[out:json][timeout:8];($clauses);out center 20;';
    return _run(query, center);
  }

  Future<List<NominatimResult>> _queryName(String raw, LatLng center) async {
    final safe = _qlSafeRegex(raw);
    final query = '[out:json][timeout:8];'
        '(node["shop"]["name"~"$safe",i](around:$_radiusM,${center.latitude},${center.longitude});'
        'node["amenity"]["name"~"$safe",i](around:$_radiusM,${center.latitude},${center.longitude});'
        'node["brand"~"$safe",i](around:$_radiusM,${center.latitude},${center.longitude});'
        'way["shop"]["name"~"$safe",i](around:$_radiusM,${center.latitude},${center.longitude});'
        'way["amenity"]["name"~"$safe",i](around:$_radiusM,${center.latitude},${center.longitude});'
        ');out center 15;';
    return _run(query, center);
  }

  Future<List<NominatimResult>> _run(String query, LatLng center) async {
    for (var attempt = 0; attempt < _endpoints.length; attempt++) {
      try {
        final res = await http.post(
          Uri.parse(_endpoints[_endpointIdx]),
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'Roadstr/1.0 (navigation app)',
          },
          body: 'data=${Uri.encodeQueryComponent(query)}',
        ).timeout(const Duration(seconds: 8));
        if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final elements = (data['elements'] as List?)?.cast<Map<String, dynamic>>() ?? [];
        final results = elements
            .map((e) => _toResult(e, center))
            .whereType<NominatimResult>()
            .toList();
        results.sort((a, b) =>
            _distM(center, a.position).compareTo(_distM(center, b.position)));
        return results;
      } catch (_) {
        _endpointIdx = (_endpointIdx + 1) % _endpoints.length;
      }
    }
    return [];
  }

  static NominatimResult? _toResult(Map<String, dynamic> el, LatLng center) {
    final tags = (el['tags'] as Map?)?.cast<String, dynamic>();
    final name = tags?['name'] as String?;
    if (name == null || name.isEmpty) return null;
    double? lat = (el['lat'] as num?)?.toDouble();
    double? lon = (el['lon'] as num?)?.toDouble();
    if (lat == null || lon == null) {
      final c = el['center'] as Map?;
      lat = (c?['lat'] as num?)?.toDouble();
      lon = (c?['lon'] as num?)?.toDouble();
    }
    if (lat == null || lon == null || !lat.isFinite || !lon.isFinite) return null;
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;

    final cls = tags?['shop'] != null
        ? 'shop'
        : tags?['amenity'] != null
            ? 'amenity'
            : tags?['tourism'] != null
                ? 'tourism'
                : null;
    final type = tags?['shop'] as String? ??
        tags?['amenity'] as String? ??
        tags?['tourism'] as String?;

    return NominatimResult(
      displayName: name,
      shortName: name,
      position: LatLng(lat, lon),
      cls: cls,
      type: type,
    );
  }

  static double _distM(LatLng a, LatLng b) =>
      const Distance().as(LengthUnit.Meter, a, b);

  static String _normalize(String s) {
    var n = s.trim().toLowerCase();
    const accents = {
      'à': 'a', 'á': 'a', 'è': 'e', 'é': 'e', 'ì': 'i', 'í': 'i',
      'ò': 'o', 'ó': 'o', 'ù': 'u', 'ú': 'u',
    };
    accents.forEach((k, v) => n = n.replaceAll(k, v));
    return n;
  }

  /// Escapes [raw] for safe embedding inside an Overpass QL regex string
  /// literal: neutralizes regex metacharacters first (so "a.b*" is matched
  /// literally, not as a pattern), then escapes any remaining `"` for the QL
  /// string boundary.
  static String _qlSafeRegex(String raw) =>
      RegExp.escape(raw).replaceAll('"', '\\"');
}
