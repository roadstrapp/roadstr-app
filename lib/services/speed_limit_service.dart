import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:latlong2/latlong.dart';
import 'overpass_client.dart';

/// Queries the Overpass API for the posted speed limit of the road
/// the user is currently on. This is the primary speed-limit source:
/// vanilla OSRM has no maxspeed annotation (that is a Mapbox extension),
/// so route responses never carry limits.
///
/// A limit is shown only when the geometrically nearest road has an explicit,
/// unambiguous numeric `maxspeed` (or numeric zone) tag. Guessing from road
/// class is unsafe internationally and can show a motorway limit while the
/// driver is on a parallel service road.
///
/// Call [updateIfNeeded] on every GPS tick — it is a no-op when position
/// hasn't changed enough. Read [cachedLimit] synchronously.
class SpeedLimitService {
  static const _radiusM = 60; // OSM way search radius (metres)
  static const _minMoveM = 100.0; // min travel distance before re-querying
  static const _maxAgeMs = 60000; // re-query after 60 s even without movement
  static const _retryMs = 15000; // back-off delay after a failed attempt
  // Consecutive empty results tolerated before clearing the cached limit.
  // At a turn or roundabout the GPS fix can transiently sit outside every
  // tagged way's search radius (junction geometry, drifted fix mid-turn) —
  // treating one empty query as "this road has no limit" flashes the sign
  // off and back on every time that happens. Requiring a couple of misses
  // in a row before clearing distinguishes a genuine no-data road from a
  // momentary miss near a junction.
  static const _maxConsecutiveMisses = 2;

  int? _cachedLimit;
  LatLng? _lastQueryPos;
  bool _fetching = false;
  DateTime? _lastSuccessAt;
  DateTime? _nextRetryAt;
  final _overpass = OverpassClient();
  int _missCount = 0;

  /// The most recently fetched speed limit (km/h), or null if unknown.
  int? get cachedLimit => _cachedLimit;

  /// Clears the cache — call when starting a new navigation session so stale
  /// limits from the previous route don't bleed in.
  void reset() {
    _cachedLimit = null;
    _lastQueryPos = null;
    _lastSuccessAt = null;
    _nextRetryAt = null;
    _fetching = false;
    _missCount = 0;
  }

  /// Triggers an async Overpass query if position has changed enough.
  /// Silently no-ops when: already fetching, inside retry back-off, or
  /// position is within [_minMoveM] of the last successful query.
  Future<void> updateIfNeeded(LatLng pos) async {
    if (!_needsQuery(pos)) return;
    _fetching = true;
    try {
      final result = await _fetchLimit(pos);
      if (result != null) {
        _cachedLimit = result;
        _missCount = 0;
      } else if (++_missCount >= _maxConsecutiveMisses) {
        // Only clear after repeated misses — see _maxConsecutiveMisses.
        _cachedLimit = null;
      }
      _lastQueryPos = pos;
      _lastSuccessAt = DateTime.now();
      _nextRetryAt = null;
      debugPrint(
          '[SpeedLimit] Overpass → ${result ?? "no limit (miss $_missCount)"} km/h');
    } catch (e) {
      debugPrint('[SpeedLimit] Overpass error: $e');
      // Rotate to the next mirror before backing off.
      _overpass.rotate();
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
    // Geometry is essential: tags alone cannot distinguish the driven road
    // from a parallel motorway, bridge or service road inside the same radius.
    final query = '[out:json][timeout:5];'
        'way[highway~"^(motorway|trunk|primary|secondary|tertiary'
        '|unclassified|residential|living_street|motorway_link|trunk_link'
        '|primary_link|secondary_link|tertiary_link)\$"]'
        '(around:$_radiusM,${pos.latitude},${pos.longitude});'
        'out tags geom;';
    final elements = await _overpass.fetchElements(query,
        maxBytes: 5 * 1024 * 1024, timeout: const Duration(seconds: 7));
    if (elements.isEmpty) return null;

    final candidates = <({double distanceM, Map<String, dynamic> tags})>[];
    for (final element in elements) {
      final tags = (element['tags'] as Map?)?.cast<String, dynamic>();
      final geometry = element['geometry'] as List?;
      if (tags == null || geometry == null || geometry.length < 2) continue;
      var nearest = double.infinity;
      for (var i = 0; i < geometry.length - 1; i++) {
        final a = geometry[i] as Map;
        final b = geometry[i + 1] as Map;
        final distance = _segmentDistanceM(
          pos,
          LatLng((a['lat'] as num).toDouble(), (a['lon'] as num).toDouble()),
          LatLng((b['lat'] as num).toDouble(), (b['lon'] as num).toDouble()),
        );
        if (distance < nearest) nearest = distance;
      }
      candidates.add((distanceM: nearest, tags: tags));
    }
    if (candidates.isEmpty) return null;
    candidates.sort((a, b) => a.distanceM.compareTo(b.distanceM));

    // Several OSM ways can describe the same physical carriageway. Consider
    // only ways effectively tied with the nearest one; never skip an untagged
    // nearby street to borrow a limit from a more distant parallel road.
    final nearestDistance = candidates.first.distanceM;
    final limits = candidates
        .where((c) => c.distanceM <= nearestDistance + 4)
        .map((c) => _limitFromTags(c.tags, pos))
        .whereType<int>()
        .toSet();
    return limits.length == 1 ? limits.single : null;
  }

  /// Resolves a way's speed limit without country-wide guesses.
  static int? _limitFromTags(Map<String, dynamic> tags, LatLng pos) {
    final type = (tags['maxspeed:type'] as String?)?.toLowerCase() ?? '';
    final numericIsMph = type.startsWith('us:') ||
        type.startsWith('gb:') ||
        _usesMphByDefault(pos);
    final explicit = tags['maxspeed'] as String?;
    if (explicit != null) {
      final kmh = _parseMaxspeed(explicit, numericIsMph: numericIsMph);
      if (kmh != null) return kmh;
      // "none"/"signals" etc. — deliberately no limit, stop here for this way.
      return null;
    }
    for (final key in const [
      'zone:traffic',
      'maxspeed:type',
      'source:maxspeed'
    ]) {
      final zone = (tags[key] as String?)?.toLowerCase();
      if (zone == null) continue;
      final kmh = _zoneDefault(zone, numericIsMph: numericIsMph);
      if (kmh != null) return kmh;
    }
    return null;
  }

  /// Accepts only zone values that contain the numeric limit explicitly.
  /// Semantic values such as `IT:urban` or `DE:rural` require a maintained
  /// jurisdiction/date-aware legal table and therefore remain unknown.
  static int? _zoneDefault(String zone, {bool numericIsMph = false}) {
    final match =
        RegExp(r'(?:^|:)(?:zone)?(\d{1,3})(?:$|[^0-9])').firstMatch(zone);
    final value = int.tryParse(match?.group(1) ?? '');
    if (value == null || value < 5 || value > 300) return null;
    return numericIsMph ? (value * 1.60934).round() : value;
  }

  /// Parses an OSM maxspeed tag value to km/h.
  /// Returns null for special values (none / unlimited / walk / variable).
  static int? _parseMaxspeed(String raw, {bool numericIsMph = false}) {
    final s = raw.trim().toLowerCase();
    if (s == 'none' ||
        s == 'unlimited' ||
        s == 'walk' ||
        s == 'living_street' ||
        s == 'signals' ||
        s == 'variable') {
      return null;
    }
    // Zone shorthand used directly in maxspeed (e.g. "IT:urban").
    final zoned = _zoneDefault(s, numericIsMph: numericIsMph);
    if (zoned != null) return zoned;
    // "30 mph" → km/h
    final mphM = RegExp(r'^(\d+)\s*mph$').firstMatch(s);
    if (mphM != null) {
      return (int.parse(mphM.group(1)!) * 1.60934).round();
    }
    // Reject conditional/compound values (`50 @ wet`, `50;70`) instead of
    // silently treating their first number as an unconditional limit.
    final numM = RegExp(r'^(\d{1,3})(?:\s*(?:km/h|kph))?$').firstMatch(s);
    if (numM != null) {
      final value = int.parse(numM.group(1)!);
      if (value < 5 || value > 300) return null;
      return numericIsMph ? (value * 1.60934).round() : value;
    }
    return null;
  }

  /// Numeric maxspeed tags use the local road unit.  Most countries using
  /// mph are identifiable without a network lookup; explicit `mph` tags and
  /// `maxspeed:type=US:*`/`GB:*` remain authoritative above this fallback.
  static bool _usesMphByDefault(LatLng pos) {
    final lat = pos.latitude, lon = pos.longitude;
    final usa = lat >= 24.3 && lat <= 49.5 && lon >= -125.0 && lon <= -66.0;
    final uk = lat >= 49.8 && lat <= 59.0 && lon >= -8.5 && lon <= 2.0;
    final liberia = lat >= 4.0 && lat <= 9.0 && lon >= -12.0 && lon <= -7.0;
    final myanmar = lat >= 9.5 && lat <= 28.5 && lon >= 92.0 && lon <= 101.5;
    return usa || uk || liberia || myanmar;
  }

  static double _segmentDistanceM(LatLng p, LatLng a, LatLng b) {
    const metresPerDegree = 111320.0;
    final cosLat = math.cos(p.latitude * math.pi / 180);
    final dx = (b.longitude - a.longitude) * metresPerDegree * cosLat;
    final dy = (b.latitude - a.latitude) * metresPerDegree;
    final px = (p.longitude - a.longitude) * metresPerDegree * cosLat;
    final py = (p.latitude - a.latitude) * metresPerDegree;
    final lengthSquared = dx * dx + dy * dy;
    final t = lengthSquared == 0
        ? 0.0
        : ((px * dx + py * dy) / lengthSquared).clamp(0.0, 1.0);
    final ex = px - t * dx;
    final ey = py - t * dy;
    return math.sqrt(ex * ex + ey * ey);
  }
}
