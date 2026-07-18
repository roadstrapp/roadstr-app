import 'dart:convert';
import 'dart:math' as math;
import 'package:latlong2/latlong.dart';
import 'bounded_http.dart';
import 'routing_service.dart' show NominatimResult;

/// Structured, bounded information extracted from the tags of one OSM POI.
///
/// OSM and Overpass responses are untrusted network input. Every text field is
/// length-limited here, before it reaches widgets or external intents. Arbitrary
/// websites are retained only when they use HTTPS and contain no credentials.
class OsmPoiDetails {
  final String? name;
  final String category;
  final String? description;
  final String? address;
  final String? openingHours;
  final String? operatorName;
  final String? cuisine;
  final String? wheelchair;
  final String? phone;
  final String? email;
  final Uri? website;

  const OsmPoiDetails({
    required this.name,
    required this.category,
    this.description,
    this.address,
    this.openingHours,
    this.operatorName,
    this.cuisine,
    this.wheelchair,
    this.phone,
    this.email,
    this.website,
  });

  static const _categoryKeys = [
    'amenity',
    'shop',
    'tourism',
    'historic',
    'leisure',
    'office',
    'craft',
    'healthcare',
    'railway',
    'aeroway',
    'natural',
  ];

  /// Parses a bounded public-information view from raw OSM [tags].
  /// Exposed for deterministic tests; callers normally use [nearestDetails].
  static OsmPoiDetails? fromOsmTags(
    Map<String, dynamic> tags, {
    String languageCode = 'en',
  }) {
    String? text(String key, int max) {
      final value = tags[key];
      if (value is! String) return null;
      final clean = value.replaceAll(RegExp(r'[\u0000-\u001f]'), ' ').trim();
      if (clean.isEmpty) return null;
      return clean.length <= max ? clean : '${clean.substring(0, max)}…';
    }

    String? categoryValue;
    for (final key in _categoryKeys) {
      categoryValue = text(key, 80);
      if (categoryValue != null) break;
    }
    if (categoryValue == null) return null;

    final street = text('addr:street', 120);
    final house = text('addr:housenumber', 30);
    final postcode = text('addr:postcode', 20);
    final city = text('addr:city', 100) ??
        text('addr:town', 100) ??
        text('addr:village', 100);
    final addressParts = <String>[
      if (street != null) '$street${house == null ? '' : ' $house'}',
      if (postcode != null || city != null)
        [postcode, city].whereType<String>().join(' '),
    ].where((part) => part.isNotEmpty).toList();

    final websiteText = text('contact:website', 500) ?? text('website', 500);
    final websiteUri = websiteText == null ? null : Uri.tryParse(websiteText);
    final safeWebsite = websiteUri != null &&
            websiteUri.scheme == 'https' &&
            websiteUri.host.isNotEmpty &&
            !websiteUri.hasPort &&
            websiteUri.userInfo.isEmpty
        ? websiteUri
        : null;

    final rawWheelchair = text('wheelchair', 20);
    final wheelchair =
        const {'yes', 'no', 'limited', 'designated'}.contains(rawWheelchair)
            ? rawWheelchair
            : null;
    final rawEmail = text('contact:email', 254) ?? text('email', 254);
    final email = rawEmail != null &&
            RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(rawEmail)
        ? rawEmail
        : null;

    String humanize(String value) {
      final spaced = value.replaceAll('_', ' ').trim();
      return spaced.isEmpty
          ? spaced
          : '${spaced[0].toUpperCase()}${spaced.substring(1)}';
    }

    final cuisineRaw = text('cuisine', 120);
    return OsmPoiDetails(
      name: text('name:$languageCode', 160) ?? text('name', 160),
      category: humanize(categoryValue),
      description:
          text('description:$languageCode', 500) ?? text('description', 500),
      address: addressParts.isEmpty ? null : addressParts.join(', '),
      openingHours: text('opening_hours', 300),
      operatorName: text('operator', 160) ?? text('brand', 160),
      cuisine: cuisineRaw
          ?.split(';')
          .map((part) => humanize(part))
          .where((part) => part.isNotEmpty)
          .join(', '),
      wheelchair: wheelchair,
      phone: text('contact:phone', 80) ?? text('phone', 80),
      email: email,
      website: safeWebsite,
    );
  }
}

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
    'grocery': ['shop=supermarket'],
    'alimentari': ['shop=supermarket', 'shop=convenience'],
    // cinema / entertainment
    'cinema': ['amenity=cinema'], 'movie theater': ['amenity=cinema'],
    'teatro': ['amenity=theatre'], 'theatre': ['amenity=theatre'],
    'theater': ['amenity=theatre'],
    // fuel
    'benzinaio': ['amenity=fuel'], 'distributore': ['amenity=fuel'],
    'gas station': ['amenity=fuel'], 'petrol station': ['amenity=fuel'],
    'fuel': ['amenity=fuel'],
    'colonnina elettrica': ['amenity=charging_station'],
    'ev charging': ['amenity=charging_station'],
    // food & drink
    'ristorante': ['amenity=restaurant'], 'restaurant': ['amenity=restaurant'],
    'pizzeria': ['amenity=restaurant;cuisine=pizza'],
    'bar': ['amenity=bar'], 'pub': ['amenity=pub'],
    'caffe': ['amenity=cafe'], 'caffè': ['amenity=cafe'],
    'cafe': ['amenity=cafe'], 'coffee': ['amenity=cafe'],
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
    'chiesa': ['amenity=place_of_worship'],
    'church': ['amenity=place_of_worship'],
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

  /// Returns structured information for the most plausible OSM POI near
  /// [center]. [preferredName] strongly biases the selection so a named search
  /// result is not replaced by a different business in the same building.
  Future<OsmPoiDetails?> nearestDetails(
    LatLng center, {
    String? preferredName,
    String languageCode = 'en',
    int radiusM = 40,
  }) async {
    final lat = center.latitude, lon = center.longitude;
    final query = '[out:json][timeout:8];'
        'nwr(around:$radiusM,$lat,$lon)'
        '[~"^(amenity|shop|tourism|historic|leisure|office|craft|healthcare|railway|aeroway|natural)\$"~"."];'
        'out center 25;';
    for (var attempt = 0; attempt < _endpoints.length; attempt++) {
      try {
        final res = await BoundedHttp.post(
          Uri.parse(_endpoints[_endpointIdx]),
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'Roadstr/1.0 (navigation app)',
          },
          body: 'data=${Uri.encodeQueryComponent(query)}',
          maxBytes: 10 * 1024 * 1024,
          timeout: const Duration(seconds: 8),
        );
        if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final elements =
            (data['elements'] as List?)?.cast<Map<String, dynamic>>() ?? [];
        OsmPoiDetails? best;
        double bestScore = double.infinity;
        final wanted = preferredName == null ? null : _normalize(preferredName);
        for (final el in elements) {
          final tags = (el['tags'] as Map?)?.cast<String, dynamic>();
          if (tags == null) continue;
          final details = OsmPoiDetails.fromOsmTags(
            tags,
            languageCode: languageCode,
          );
          if (details == null) continue;
          double? elat = (el['lat'] as num?)?.toDouble();
          double? elon = (el['lon'] as num?)?.toDouble();
          if (elat == null || elon == null) {
            final ctr = el['center'] as Map?;
            elat = (ctr?['lat'] as num?)?.toDouble();
            elon = (ctr?['lon'] as num?)?.toDouble();
          }
          if (elat == null ||
              elon == null ||
              !elat.isFinite ||
              !elon.isFinite) {
            continue;
          }
          final d = _distM(center, LatLng(elat, elon));
          if (d > radiusM) continue;
          var score = d;
          final candidateName =
              details.name == null ? null : _normalize(details.name!);
          if (wanted != null && candidateName != null) {
            if (candidateName == wanted) {
              score -= radiusM * 2;
            } else if (candidateName.contains(wanted) ||
                wanted.contains(candidateName)) {
              score -= radiusM;
            }
          }
          if (score < bestScore) {
            bestScore = score;
            best = details;
          }
        }
        return best;
      } catch (_) {
        _endpointIdx = (_endpointIdx + 1) % _endpoints.length;
      }
    }
    return null;
  }

  /// Returns the OSM `building` polygon that contains [point] (or whose
  /// perimeter lies within ~15 m of it), or null when none is mapped.
  ///
  /// Used for arrival detection: OSM traces building outlines, so "the GPS
  /// entered the destination's building footprint" is a far better arrival
  /// signal than any fixed radius — the router's arrive-point sits on the
  /// road, while the user actually stops at the door or inside a courtyard.
  Future<List<LatLng>?> buildingPolygonAt(LatLng point) async {
    final lat = point.latitude, lon = point.longitude;
    // around: measures distance to the way's OUTLINE, not its interior — a
    // destination point deep inside a large footprint is far from every wall.
    // 60 m covers buildings up to ~120 m across (geocoded centroids of most
    // large stores/stations); the picker below then prefers the polygon that
    // CONTAINS the point over the merely-nearest one. Relations included:
    // historic palazzi with courtyards are multipolygons, not simple ways.
    // (is_in(): would be exact, but its area generation is heavy enough that
    // public mirrors routinely 504 on it — verified live; not worth it for a
    // best-effort arrival hint.)
    final query = '[out:json][timeout:8];'
        '(way["building"](around:60,$lat,$lon);'
        'relation["building"](around:60,$lat,$lon););out geom 8;';
    for (var attempt = 0; attempt < _endpoints.length; attempt++) {
      try {
        final res = await BoundedHttp.post(
          Uri.parse(_endpoints[_endpointIdx]),
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'Roadstr/1.0 (navigation app)',
          },
          body: 'data=${Uri.encodeQueryComponent(query)}',
          maxBytes: 10 * 1024 * 1024,
          timeout: const Duration(seconds: 8),
        );
        if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final elements =
            (data['elements'] as List?)?.cast<Map<String, dynamic>>() ?? [];
        List<LatLng>? containing;
        List<LatLng>? nearest;
        double nearestD = 15.0; // max perimeter distance to accept
        for (final el in elements) {
          final poly = _elementRing(el);
          if (poly == null) continue;
          if (_pointInPolygon(point, poly)) {
            containing = poly;
            break;
          }
          for (int i = 0; i < poly.length - 1; i++) {
            final d = _segDistM(point, poly[i], poly[i + 1]);
            if (d < nearestD) {
              nearestD = d;
              nearest = poly;
            }
          }
        }
        return containing ?? nearest;
      } catch (_) {
        _endpointIdx = (_endpointIdx + 1) % _endpoints.length;
      }
    }
    return null;
  }

  /// Outer ring of a building element: a way's own geometry, or a relation's
  /// outer members stitched end-to-end (same approach as ZtlService).
  /// Inner rings (courtyards) are ignored: standing in the courtyard of the
  /// destination palazzo still counts as arrived.
  static List<LatLng>? _elementRing(Map<String, dynamic> el) {
    final type = el['type'] as String?;
    if (type == 'way') {
      final geom = el['geometry'] as List?;
      if (geom == null || geom.length < 4) return null; // not a closed ring
      return geom
          .map((g) => LatLng(
              (g['lat'] as num).toDouble(), (g['lon'] as num).toDouble()))
          .toList();
    }
    if (type == 'relation') {
      final members = el['members'] as List? ?? [];
      final outer = <LatLng>[];
      for (final m in members) {
        if ((m['role'] as String?) != 'outer') continue;
        final geom = m['geometry'] as List?;
        if (geom == null) continue;
        final pts = geom
            .map((g) => LatLng(
                (g['lat'] as num).toDouble(), (g['lon'] as num).toDouble()))
            .toList();
        if (outer.isNotEmpty &&
            pts.isNotEmpty &&
            (outer.last.latitude - pts.first.latitude).abs() < 1e-7 &&
            (outer.last.longitude - pts.first.longitude).abs() < 1e-7) {
          outer.addAll(pts.skip(1));
        } else {
          outer.addAll(pts);
        }
      }
      return outer.length >= 4 ? outer : null;
    }
    return null;
  }

  /// Ray-casting point-in-polygon.
  static bool _pointInPolygon(LatLng p, List<LatLng> polygon) {
    final n = polygon.length;
    if (n < 3) return false;
    bool inside = false;
    final x = p.longitude, y = p.latitude;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      final xi = polygon[i].longitude, yi = polygon[i].latitude;
      final xj = polygon[j].longitude, yj = polygon[j].latitude;
      if (((yi > y) != (yj > y)) &&
          (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
        inside = !inside;
      }
    }
    return inside;
  }

  /// Distance in metres from [p] to segment [a]-[b] (equirectangular approx).
  static double _segDistM(LatLng p, LatLng a, LatLng b) {
    const degM = 111320.0;
    final cosLat = math.cos(p.latitude * math.pi / 180);
    final dx = (b.longitude - a.longitude) * degM * cosLat;
    final dy = (b.latitude - a.latitude) * degM;
    final ex = (p.longitude - a.longitude) * degM * cosLat;
    final ey = (p.latitude - a.latitude) * degM;
    final len2 = dx * dx + dy * dy;
    final t = len2 == 0 ? 0.0 : ((ex * dx + ey * dy) / len2).clamp(0.0, 1.0);
    final cx = ex - t * dx, cy = ey - t * dy;
    return math.sqrt(cx * cx + cy * cy);
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
        final res = await BoundedHttp.post(
          Uri.parse(_endpoints[_endpointIdx]),
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'Roadstr/1.0 (navigation app)',
          },
          body: 'data=${Uri.encodeQueryComponent(query)}',
          maxBytes: 10 * 1024 * 1024,
          timeout: const Duration(seconds: 8),
        );
        if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final elements =
            (data['elements'] as List?)?.cast<Map<String, dynamic>>() ?? [];
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
    if (lat == null || lon == null || !lat.isFinite || !lon.isFinite) {
      return null;
    }
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
      openingHours: (tags?['opening_hours'] as String?)?.trim(),
    );
  }

  static double _distM(LatLng a, LatLng b) =>
      const Distance().as(LengthUnit.Meter, a, b);

  static String _normalize(String s) {
    var n = s.trim().toLowerCase();
    const accents = {
      'à': 'a',
      'á': 'a',
      'è': 'e',
      'é': 'e',
      'ì': 'i',
      'í': 'i',
      'ò': 'o',
      'ó': 'o',
      'ù': 'u',
      'ú': 'u',
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
