import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';

/// Queries the Overpass API for the posted speed limit of the road
/// the user is currently on. This is the primary speed-limit source:
/// vanilla OSRM has no maxspeed annotation (that is a Mapbox extension),
/// so route responses never carry limits.
///
/// Many roads — especially Italian urban streets — have no explicit
/// `maxspeed` tag because the limit is implied by law. When the tag is
/// missing the limit is inferred from `zone:traffic` / `maxspeed:type`
/// (e.g. "IT:urban" → 50) or, failing that, from the highway class
/// (motorway → 130, trunk → 110, residential → 50).
///
/// Call [updateIfNeeded] on every GPS tick — it is a no-op when position
/// hasn't changed enough. Read [cachedLimit] synchronously.
class SpeedLimitService {
  /// Public Overpass endpoints, tried in order. The main instance is often
  /// "too busy"; the mirrors keep the feature alive when it rejects us.
  static const _endpoints = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
    'https://overpass.osm.ch/api/interpreter',
  ];
  static const _radiusM  = 40;      // OSM way search radius (metres)
  static const _minMoveM = 100.0;   // min travel distance before re-querying
  static const _maxAgeMs = 60000;   // re-query after 60 s even without movement
  static const _retryMs  = 15000;   // back-off delay after a failed attempt

  int?      _cachedLimit;
  LatLng?   _lastQueryPos;
  bool      _fetching = false;
  DateTime? _lastSuccessAt;
  DateTime? _nextRetryAt;
  int       _endpointIdx = 0;

  /// The most recently fetched speed limit (km/h), or null if unknown.
  int? get cachedLimit => _cachedLimit;

  /// Clears the cache — call when starting a new navigation session so stale
  /// limits from the previous route don't bleed in.
  void reset() {
    _cachedLimit    = null;
    _lastQueryPos   = null;
    _lastSuccessAt  = null;
    _nextRetryAt    = null;
    _fetching       = false;
  }

  /// Triggers an async Overpass query if position has changed enough.
  /// Silently no-ops when: already fetching, inside retry back-off, or
  /// position is within [_minMoveM] of the last successful query.
  Future<void> updateIfNeeded(LatLng pos) async {
    if (!_needsQuery(pos)) return;
    _fetching = true;
    try {
      _cachedLimit    = await _fetchLimit(pos);
      _lastQueryPos   = pos;
      _lastSuccessAt  = DateTime.now();
      _nextRetryAt    = null;
      debugPrint('[SpeedLimit] Overpass → ${_cachedLimit ?? "no limit"} km/h');
    } catch (e) {
      debugPrint('[SpeedLimit] Overpass error: $e');
      // Rotate to the next mirror before backing off.
      _endpointIdx = (_endpointIdx + 1) % _endpoints.length;
      _nextRetryAt = DateTime.now().add(const Duration(milliseconds: _retryMs));
    } finally {
      _fetching = false;
    }
  }

  bool _needsQuery(LatLng pos) {
    if (_fetching) return false;
    final now = DateTime.now();
    if (_nextRetryAt != null && now.isBefore(_nextRetryAt!)) return false;
    if (_lastQueryPos == null) return true;
    final moved = const Distance().as(LengthUnit.Meter, _lastQueryPos!, pos);
    if (moved > _minMoveM) return true;
    if (_lastSuccessAt == null) return true;
    return now.difference(_lastSuccessAt!).inMilliseconds > _maxAgeMs;
  }

  Future<int?> _fetchLimit(LatLng pos) async {
    // No [maxspeed] filter: untagged roads still yield a class-based limit.
    final query = '[out:json][timeout:5];'
        'way[highway~"^(motorway|trunk|primary|secondary|tertiary'
        '|unclassified|residential|living_street|motorway_link|trunk_link'
        '|primary_link|secondary_link|tertiary_link)\$"]'
        '(around:$_radiusM,${pos.latitude},${pos.longitude});'
        'out tags;';
    final res = await http.post(
      Uri.parse(_endpoints[_endpointIdx]),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'User-Agent': 'Roadstr/1.0 (navigation app)',
      },
      body: 'data=${Uri.encodeQueryComponent(query)}',
    ).timeout(const Duration(seconds: 7));

    if (res.statusCode != 200) {
      throw Exception('HTTP ${res.statusCode}');
    }
    final data = jsonDecode(res.body) as Map<String, dynamic>;
    final elements = (data['elements'] as List?)?.cast<Map<String, dynamic>>();
    if (elements == null || elements.isEmpty) return null;

    // Sort by highway priority so a motorway next to a service road wins.
    const priority = {
      'motorway': 0, 'motorway_link': 0,
      'trunk': 1,    'trunk_link': 1,
      'primary': 2,  'primary_link': 2,
      'secondary': 3, 'secondary_link': 3,
      'tertiary': 4,  'tertiary_link': 4,
      'unclassified': 5,
      'residential': 6,
      'living_street': 7,
    };
    elements.sort((a, b) {
      final ta = ((a['tags'] as Map?)?['highway'] as String?) ?? '';
      final tb = ((b['tags'] as Map?)?['highway'] as String?) ?? '';
      return (priority[ta] ?? 9).compareTo(priority[tb] ?? 9);
    });

    for (final el in elements) {
      final tags = el['tags'] as Map?;
      if (tags == null) continue;
      final kmh = _limitFromTags(tags.cast<String, dynamic>());
      if (kmh != null) return kmh;
    }
    return null;
  }

  /// Resolves a way's speed limit with decreasing confidence:
  /// 1. explicit `maxspeed` tag
  /// 2. `zone:traffic` / `maxspeed:type` / `source:maxspeed` legal-default zones
  /// 3. highway-class default (Italian road code)
  static int? _limitFromTags(Map<String, dynamic> tags) {
    final explicit = tags['maxspeed'] as String?;
    if (explicit != null) {
      final kmh = _parseMaxspeed(explicit);
      if (kmh != null) return kmh;
      // "none"/"signals" etc. — deliberately no limit, stop here for this way.
      return null;
    }
    for (final key in const ['zone:traffic', 'maxspeed:type', 'source:maxspeed']) {
      final zone = (tags[key] as String?)?.toLowerCase();
      if (zone == null) continue;
      final kmh = _zoneDefault(zone);
      if (kmh != null) return kmh;
    }
    return _classDefault(tags['highway'] as String?);
  }

  /// Maps legal-default zone values ("IT:urban", "DE:rural", "IT:zone30" …)
  /// to km/h. Returns null for unknown or unlimited zones.
  static int? _zoneDefault(String zone) {
    if (zone.contains('zone30') || zone.contains(':30')) return 30;
    if (zone.contains('living_street')) return 30;
    if (zone.contains('urban'))    return 50;
    if (zone.contains('rural'))    return 90;
    if (zone.contains('trunk'))    return 110;
    if (zone.contains('motorway')) return 130;
    return null;
  }

  /// Implicit limit from highway class (Italian defaults). Roads whose limit
  /// depends on urban/rural context (primary…unclassified) return null —
  /// guessing 90 inside a town would be worse than showing nothing.
  static int? _classDefault(String? highway) {
    switch (highway) {
      case 'motorway':      return 130;
      case 'trunk':         return 110;
      case 'residential':   return 50;
      case 'living_street': return 30;
      default:              return null;
    }
  }

  /// Parses an OSM maxspeed tag value to km/h.
  /// Returns null for special values (none / unlimited / walk / variable).
  static int? _parseMaxspeed(String raw) {
    final s = raw.trim().toLowerCase();
    if (s == 'none' || s == 'unlimited' || s == 'walk' ||
        s == 'living_street' || s == 'signals' || s == 'variable') {
      return null;
    }
    // Zone shorthand used directly in maxspeed (e.g. "IT:urban").
    final zoned = _zoneDefault(s);
    if (zoned != null) return zoned;
    // "30 mph" → km/h
    final mphM = RegExp(r'^(\d+)\s*mph$').firstMatch(s);
    if (mphM != null) {
      return (int.parse(mphM.group(1)!) * 1.60934).round();
    }
    // "50" or "50 km/h" or "50 kph"
    final numM = RegExp(r'^(\d+)').firstMatch(s);
    if (numM != null) return int.tryParse(numM.group(1)!);
    return null;
  }
}
