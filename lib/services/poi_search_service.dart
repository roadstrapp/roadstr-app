import 'package:latlong2/latlong.dart';
import '../utils/fuzzy_match.dart';
import '../utils/geo.dart';
import 'overpass_client.dart';
import 'routing_service.dart' show NominatimResult;

enum OsmPoiKind {
  parking,
  chargingStation,
  fuelStation,
  lodging,
  foodAndDrink,
  other,
}

/// The things a driver stops for, offered as one tap instead of a typed query.
///
/// Every one of them is a well-established OSM tag with worldwide coverage —
/// no bespoke data and no third-party directory. The order is the order they
/// appear in the UI, which is roughly how often a driver needs them.
enum NearbyCategory {
  fuel(['amenity=fuel'], '⛽'),
  supermarket(['shop=supermarket', 'shop=convenience'], '🛒'),
  atm(['amenity=atm', 'amenity=bank'], '🏧'),
  pharmacy(['amenity=pharmacy'], '💊'),
  hospital(['amenity=hospital', 'amenity=clinic'], '🏥'),
  police(['amenity=police'], '👮'),
  postOffice(['amenity=post_office'], '📮'),
  parking(['amenity=parking'], '🅿️'),
  charging(['amenity=charging_station'], '🔌');

  /// OSM tag filters; a POI matching any one of them belongs to the category.
  final List<String> filters;

  /// Shown on the category button and on unnamed results.
  final String emoji;

  const NearbyCategory(this.filters, this.emoji);
}

/// One EV connector advertised by an OSM charging station.
class OsmEvConnector {
  final String type;
  final int? count;
  final String? output;

  const OsmEvConnector({required this.type, this.count, this.output});
}

/// Structured, bounded information extracted from the tags of one OSM POI.
///
/// OSM and Overpass responses are untrusted network input. Every text field is
/// length-limited here, before it reaches widgets or external intents. Arbitrary
/// websites are retained only when they use HTTPS and contain no credentials.
class OsmPoiDetails {
  final String? name;
  final String category;
  final OsmPoiKind kind;
  final String? description;
  final String? address;
  final String? openingHours;
  final String? operatorName;
  final String? cuisine;
  final String? wheelchair;
  final String? phone;
  final String? email;
  final Uri? website;
  final bool acceptsBitcoin;
  final bool acceptsLightning;
  final String? access;
  final String? fee;
  final String? charge;
  final int? capacity;
  final String? maxStay;
  final String? parkingType;
  final List<OsmEvConnector> evConnectors;
  final Set<String> fuels;
  final String? stars;
  final String? smoking;
  final String? outdoorSeating;
  final String? takeaway;

  const OsmPoiDetails({
    required this.name,
    required this.category,
    this.kind = OsmPoiKind.other,
    this.description,
    this.address,
    this.openingHours,
    this.operatorName,
    this.cuisine,
    this.wheelchair,
    this.phone,
    this.email,
    this.website,
    this.acceptsBitcoin = false,
    this.acceptsLightning = false,
    this.access,
    this.fee,
    this.charge,
    this.capacity,
    this.maxStay,
    this.parkingType,
    this.evConnectors = const [],
    this.fuels = const {},
    this.stars,
    this.smoking,
    this.outdoorSeating,
    this.takeaway,
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

    String? knownValue(String key, Set<String> allowed) {
      final value = text(key, 80)?.toLowerCase();
      return value != null && allowed.contains(value) ? value : null;
    }

    bool isYes(String key) {
      final value = text(key, 20)?.toLowerCase();
      return value == 'yes' || value == 'only' || value == 'accepted';
    }

    int? positiveInt(String key, {int max = 100000}) {
      final value = text(key, 20);
      if (value == null || !RegExp(r'^\d{1,6}$').hasMatch(value)) return null;
      final parsed = int.tryParse(value);
      return parsed != null && parsed > 0 && parsed <= max ? parsed : null;
    }

    final amenity = text('amenity', 80)?.toLowerCase();
    final tourism = text('tourism', 80)?.toLowerCase();
    final kind = switch (amenity) {
      'parking' || 'parking_entrance' => OsmPoiKind.parking,
      'charging_station' => OsmPoiKind.chargingStation,
      'fuel' => OsmPoiKind.fuelStation,
      'restaurant' ||
      'cafe' ||
      'bar' ||
      'pub' ||
      'fast_food' =>
        OsmPoiKind.foodAndDrink,
      _
          when const {
            'hotel',
            'motel',
            'hostel',
            'guest_house',
            'apartment',
          }.contains(tourism) =>
        OsmPoiKind.lodging,
      _ => OsmPoiKind.other,
    };

    final connectors = <OsmEvConnector>[];
    if (kind == OsmPoiKind.chargingStation) {
      for (final type in const ['type2', 'chademo', 'type2_combo']) {
        final raw = text('socket:$type', 20)?.toLowerCase();
        if (raw == null || raw == 'no' || raw == '0') continue;
        final parsedCount =
            RegExp(r'^\d{1,3}$').hasMatch(raw) ? int.tryParse(raw) : null;
        final count =
            parsedCount != null && parsedCount > 0 ? parsedCount : null;
        if (count == null && raw != 'yes') continue;
        connectors.add(OsmEvConnector(
          type: type,
          count: count,
          output: text('socket:$type:output', 60),
        ));
      }
    }

    final fuels = <String>{};
    if (kind == OsmPoiKind.fuelStation) {
      if (isYes('fuel:diesel')) fuels.add('diesel');
      if (isYes('fuel:octane_95')) fuels.add('octane_95');
    }

    final starsRaw = kind == OsmPoiKind.lodging ? text('stars', 10) : null;
    final stars =
        starsRaw != null && RegExp(r'^[1-7](?:[sS+])?$').hasMatch(starsRaw)
            ? starsRaw.toUpperCase()
            : null;

    final cuisineRaw = text('cuisine', 120);
    return OsmPoiDetails(
      name: text('name:$languageCode', 160) ?? text('name', 160),
      category: humanize(categoryValue),
      kind: kind,
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
      acceptsBitcoin: isYes('payment:bitcoin'),
      acceptsLightning: isYes('payment:lightning'),
      access: knownValue('access', const {
        'private',
        'customers',
        'permit',
        'no',
        'destination',
      }),
      fee: (kind == OsmPoiKind.parking || kind == OsmPoiKind.chargingStation)
          ? text('fee', 80)
          : null,
      charge: (kind == OsmPoiKind.parking || kind == OsmPoiKind.chargingStation)
          ? text('charge', 120)
          : null,
      capacity:
          (kind == OsmPoiKind.parking || kind == OsmPoiKind.chargingStation)
              ? positiveInt('capacity')
              : null,
      maxStay: kind == OsmPoiKind.parking ? text('maxstay', 80) : null,
      parkingType: kind == OsmPoiKind.parking
          ? knownValue('parking', const {
              'surface',
              'underground',
              'multi-storey',
              'street_side',
              'lane',
              'rooftop',
            })
          : null,
      evConnectors: List.unmodifiable(connectors),
      fuels: Set.unmodifiable(fuels),
      stars: stars,
      smoking: kind == OsmPoiKind.foodAndDrink
          ? knownValue('smoking', const {
              'yes',
              'no',
              'outside',
              'separated',
              'isolated',
              'dedicated',
            })
          : null,
      outdoorSeating: kind == OsmPoiKind.foodAndDrink
          ? knownValue('outdoor_seating', const {'yes', 'no'})
          : null,
      takeaway: kind == OsmPoiKind.foodAndDrink
          ? knownValue('takeaway', const {'yes', 'no', 'only'})
          : null,
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
  PoiSearchService({OverpassClient? overpass})
      : _overpass = overpass ?? OverpassClient();

  final OverpassClient _overpass;

  // A local category search does not need an 8 km Overpass extract.  Named
  // streets and businesses are handled by Nominatim; this query is only the
  // nearby-category supplement, so keeping it to 4 km makes responses much
  // smaller and considerably faster.
  static const _radiusM = 4000;

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
    'polizia': ['amenity=police'], 'police': ['amenity=police'],
    'carabinieri': ['amenity=police'], 'commissariato': ['amenity=police'],
    'polizei': ['amenity=police'], 'policia': ['amenity=police'],
    'chiesa': ['amenity=place_of_worship'],
    'church': ['amenity=place_of_worship'],
    'supermercati': ['shop=supermarket'],
  };

  /// How far "around me" reaches, tried in order.
  ///
  /// The near ring first: in a city it already holds far more than a screenful,
  /// it comes back smaller and quicker, and — the reason it exists — Overpass
  /// has no notion of "nearest", it just returns the first N matches it finds.
  /// Asking for 5 km in central Berlin and keeping the closest 25 of whatever
  /// came back would therefore be picking 25 arbitrary car parks, not the 25
  /// nearest. Where the near ring is thin (a village, a motorway at night) the
  /// wide one runs as well, and there the wider answer is small anyway.
  static const nearbyRings = [1500, 5000];

  /// Enough results in the near ring to not bother widening.
  static const _enoughNearbyResults = 10;

  /// The most one screen of results can usefully show.
  static const _maxNearbyResults = 25;

  /// Elements one query may return. Only [_maxNearbyResults] are kept, but the
  /// selection is only as good as the pool it chooses from.
  static const _nearbyElementCap = 120;

  /// Everything of [category] around [center], nearest first.
  ///
  /// Unlike [search], results without a `name` tag are **kept**: a cash machine
  /// in a wall or an unbranded filling station is exactly what the driver is
  /// looking for, and dropping it because OSM has no name for it would make the
  /// feature look empty in half of Europe. Those entries are labelled with
  /// [unnamedLabel] — the caller passes the translated category name.
  Future<List<NominatimResult>> nearby(
    NearbyCategory category,
    LatLng center, {
    required String unnamedLabel,
  }) async {
    // Re-tapping a category, or coming back to it after looking at a result,
    // must not cost another round trip — and must not hammer a free mirror.
    final cacheKey = '${category.name}@${_cacheCell(center)}';
    final cached = _nearbyCache[cacheKey];
    if (cached != null && DateTime.now().isBefore(cached.until)) {
      return cached.results;
    }

    var found = const <NominatimResult>[];
    for (final radiusM in nearbyRings) {
      final ring = await _nearbyRing(category, center, radiusM, unnamedLabel);
      // Never trade a good answer for a worse one: a wider ring that comes
      // back empty means the request failed, not that the places found in the
      // near ring stopped existing.
      if (ring.length > found.length) found = ring;
      if (found.length >= _enoughNearbyResults) break;
    }
    if (found.isEmpty) return const [];

    if (_nearbyCache.length > 40) _nearbyCache.clear();
    _nearbyCache[cacheKey] = (
      results: found,
      until: DateTime.now().add(const Duration(minutes: 5)),
    );
    return found;
  }

  /// One ring of [nearby]: the nearest [_maxNearbyResults] within [radiusM].
  Future<List<NominatimResult>> _nearbyRing(NearbyCategory category,
      LatLng center, int radiusM, String unnamedLabel) async {
    final lat = OverpassClient.coord(center.latitude);
    final lon = OverpassClient.coord(center.longitude);
    final clauses = category.filters.map((filter) {
      final kv = filter.split('=');
      final tag = '["${kv[0]}"="${kv[1]}"]';
      return 'node$tag(around:$radiusM,$lat,$lon);'
          'way$tag(around:$radiusM,$lat,$lon);';
    }).join();
    final query =
        '[out:json][timeout:8];($clauses);out center $_nearbyElementCap;';
    try {
      final elements = await _overpass.fetchElementsHedged(query,
          maxBytes: 6 * 1024 * 1024, timeout: const Duration(seconds: 10));
      if (elements == null) return const [];
      final results = <NominatimResult>[];
      final seen = <String>{};
      for (final element in elements) {
        final result = _toResult(element, center, fallbackName: unnamedLabel);
        if (result == null) continue;
        // Overpass returns the same shop as both a node and a building way
        // often enough to be noticeable in a single sweep.
        final key = '${result.shortName}@'
            '${result.position.latitude.toStringAsFixed(4)},'
            '${result.position.longitude.toStringAsFixed(4)}';
        if (!seen.add(key)) continue;
        results.add(result);
      }
      results.sort((a, b) => (a.distanceM ?? 0).compareTo(b.distanceM ?? 0));
      return results.take(_maxNearbyResults).toList();
    } catch (_) {
      return const [];
    }
  }

  /// Cache key cell: ~500 m, so results are reused while the driver is around
  /// the same place but recomputed once they have actually moved on. Distances
  /// shown against a fix half a cell away are off by at most a few hundred
  /// metres, which does not change which petrol station is the near one.
  static String _cacheCell(LatLng p) =>
      '${(p.latitude * 200).round()},${(p.longitude * 200).round()}';

  /// Short-lived nearby results, keyed by category and position cell.
  final _nearbyCache =
      <String, ({List<NominatimResult> results, DateTime until})>{};

  /// Searches for POIs matching [query] near [center]. Returns an empty list
  /// if [query] matches no known category and no brand/name hits are found —
  /// callers should fall back to (or merge with) [RoutingService.search].
  Future<List<NominatimResult>> search(String query, LatLng center) async {
    final tags = _categoryFor(query);
    try {
      if (tags != null) {
        return await _queryTags(tags, center);
      }
      // Named streets and businesses are already covered by Nominatim. Do not
      // issue a second large Overpass regex query for every keystroke: the
      // category path above is the only supplement that needs local OSM tags.
      return [];
    } catch (_) {
      return [];
    }
  }

  /// Resolves [query] to a category's OSM tag filters, tolerating misspellings
  /// and simple plurals ("farmacie" → "farmacia", "hotle" → "hotel").
  ///
  /// The tolerance is deliberately narrow — the candidate must be within two
  /// characters of the keyword — because a false positive here fires a 4 km
  /// Overpass query and injects unrelated POIs into the suggestion list.
  /// Prefix matches are therefore NOT accepted: "barcellona" must not be read
  /// as "bar".
  static List<String>? _categoryFor(String query) {
    final normalized = FuzzyMatch.normalize(query);
    if (normalized.isEmpty) return null;
    final exact = _categories[normalized];
    if (exact != null) return exact;
    if (normalized.length < 4) return null;

    List<String>? best;
    var bestScore = 0.8;
    for (final entry in _categories.entries) {
      if ((entry.key.length - normalized.length).abs() > 2) continue;
      final s = FuzzyMatch.wordScore(normalized, entry.key);
      if (s > bestScore) {
        bestScore = s;
        best = entry.value;
      }
    }
    return best;
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
    final lat = OverpassClient.coord(center.latitude);
    final lon = OverpassClient.coord(center.longitude);
    final query = '[out:json][timeout:8];'
        'nwr(around:$radiusM,$lat,$lon)'
        '[~"^(amenity|shop|tourism|historic|leisure|office|craft|healthcare|railway|aeroway|natural)\$"~"."];'
        'out center 25;';
    final elements = await _overpass.fetchElementsAnyMirror(query,
        maxBytes: 10 * 1024 * 1024, timeout: const Duration(seconds: 8));
    if (elements == null) return null;
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
      if (elat == null || elon == null || !elat.isFinite || !elon.isFinite) {
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
  }

  /// Returns the OSM `building` polygon that contains [point] (or whose
  /// perimeter lies within ~15 m of it), or null when none is mapped.
  ///
  /// Used for arrival detection: OSM traces building outlines, so "the GPS
  /// entered the destination's building footprint" is a far better arrival
  /// signal than any fixed radius — the router's arrive-point sits on the
  /// road, while the user actually stops at the door or inside a courtyard.
  Future<List<LatLng>?> buildingPolygonAt(LatLng point) async {
    final lat = OverpassClient.coord(point.latitude);
    final lon = OverpassClient.coord(point.longitude);
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
    final elements = await _overpass.fetchElementsAnyMirror(query,
        maxBytes: 10 * 1024 * 1024, timeout: const Duration(seconds: 8));
    if (elements == null) return null;
    List<LatLng>? containing;
    List<LatLng>? nearest;
    double nearestD = 15.0; // max perimeter distance to accept
    for (final el in elements) {
      final poly = _elementRing(el);
      if (poly == null) continue;
      if (Geo.pointInPolygon(point, poly)) {
        containing = poly;
        break;
      }
      for (int i = 0; i < poly.length - 1; i++) {
        final d = Geo.distanceToSegmentM(point, poly[i], poly[i + 1]);
        if (d < nearestD) {
          nearestD = d;
          nearest = poly;
        }
      }
    }
    return containing ?? nearest;
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

  Future<List<NominatimResult>> _queryTags(
      List<String> tagFilters, LatLng center) async {
    final lat = OverpassClient.coord(center.latitude);
    final lon = OverpassClient.coord(center.longitude);
    final clauses = tagFilters.map((f) {
      // "amenity=cinema" or "amenity=restaurant;cuisine=pizza" (extra filters
      // joined with ';' apply as additional exact-match tag constraints).
      final parts = f.split(';').map((p) {
        final kv = p.split('=');
        return '["${kv[0]}"="${kv[1]}"]';
      }).join();
      return 'node$parts(around:$_radiusM,$lat,$lon);'
          'way$parts(around:$_radiusM,$lat,$lon);';
    }).join();
    final query = '[out:json][timeout:5];($clauses);out center 12;';
    return _run(query, center);
  }

  Future<List<NominatimResult>> _run(String query, LatLng center) async {
    final elements = await _overpass.fetchElementsAnyMirror(query,
        maxBytes: 6 * 1024 * 1024, timeout: const Duration(seconds: 5));
    if (elements == null) return [];
    final results = elements
        .map((e) => _toResult(e, center))
        .whereType<NominatimResult>()
        .toList();
    results.sort((a, b) =>
        _distM(center, a.position).compareTo(_distM(center, b.position)));
    return results;
  }

  /// Converts one Overpass element into a search result.
  ///
  /// Without [fallbackName] an unnamed element is dropped (a typed search for
  /// "Esselunga" has nothing to say about a nameless building); with one it is
  /// kept under that label — see [nearby].
  static NominatimResult? _toResult(Map<String, dynamic> el, LatLng center,
      {String? fallbackName}) {
    final tags = (el['tags'] as Map?)?.cast<String, dynamic>();
    var name = tags?['name'] as String?;
    if (name == null || name.isEmpty) {
      // Branded but unnamed (very common for fuel and ATMs) reads better as
      // the brand than as a generic label.
      name = (tags?['brand'] as String?)?.trim();
    }
    if (name == null || name.isEmpty) name = fallbackName;
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

    final position = LatLng(lat, lon);
    return NominatimResult(
      displayName: name,
      shortName: name,
      position: position,
      cls: cls,
      type: type,
      openingHours: (tags?['opening_hours'] as String?)?.trim(),
      distanceM: _distM(center, position),
    );
  }

  static double _distM(LatLng a, LatLng b) =>
      const Distance().as(LengthUnit.Meter, a, b);

  /// Lowercase, accent-folded, punctuation-stripped form used for name
  /// comparisons. Delegates to the shared search normaliser so POI matching and
  /// suggestion ranking always agree on what "the same text" means.
  static String _normalize(String s) => FuzzyMatch.normalize(s);
}
