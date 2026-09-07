import 'package:latlong2/latlong.dart';
import 'package:flutter/foundation.dart';
import 'overpass_client.dart';

/// What kind of road-surface hazard/marking an [OsmCrossingHazard] is.
enum CrossingHazardKind {
  /// A pedestrian crossing (`highway=crossing`), any sub-type.
  crosswalk,

  /// A speed bump/hump/table/cushion/etc. (`traffic_calming=*`).
  speedBump,
}

/// A pedestrian crossing or speed bump sourced from OpenStreetMap.
class OsmCrossingHazard {
  final int id;
  final LatLng position;
  final CrossingHazardKind kind;
  const OsmCrossingHazard({
    required this.id,
    required this.position,
    required this.kind,
  });
}

/// Fetches pedestrian crossings and speed bumps from OpenStreetMap via
/// Overpass — tags verified directly against OsmAnd's own rendering style
/// (`highway=crossing`, `traffic_calming=bump/hump/cushion/chicane/
/// rumble_strip/table/choker/island`), the same request the user pointed to
/// ("like on osmand+"). Same throttle/cache/mirror-rotation pattern as
/// [SpeedCameraService]/[TrafficLightService] — a live OSM-baseline overlay,
/// not a community-reported one.
class CrossingHazardService {
  static const _radiusM = 1500;
  static const _minMoveM = 500.0;
  static const _maxAgeMs = 120000;
  static const _retryMs = 15000;
  static const _maxResults = 400;

  List<OsmCrossingHazard> _cached = [];
  LatLng? _lastQueryPos;
  bool _fetching = false;
  DateTime? _lastSuccessAt;
  DateTime? _nextRetryAt;
  final _overpass = OverpassClient();

  List<OsmCrossingHazard> get cachedHazards => _cached;

  void reset() {
    _cached = [];
    _lastQueryPos = null;
    _lastSuccessAt = null;
    _nextRetryAt = null;
    _fetching = false;
  }

  Future<void> updateIfNeeded(LatLng pos) async {
    if (!_needsQuery(pos)) return;
    _fetching = true;
    try {
      _cached = await _fetch(pos);
      _lastQueryPos = pos;
      _lastSuccessAt = DateTime.now();
      _nextRetryAt = null;
      _overpass.noteSuccess();
      debugPrint(
          '[CrossingHazard] Overpass → ${_cached.length} crossings/bumps nearby');
    } catch (e) {
      debugPrint('[CrossingHazard] Overpass error: $e');
      _overpass.rotate();
      _overpass.noteFailure(e);
      _nextRetryAt = DateTime.now().add(_overpass
          .failureBackoff(base: const Duration(milliseconds: _retryMs)));
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

  Future<List<OsmCrossingHazard>> _fetch(LatLng pos) async {
    final lat = OverpassClient.coord(pos.latitude);
    final lon = OverpassClient.coord(pos.longitude);
    // "body" verbosity, not "skel"/"tags" alone: tags are needed to tell a
    // crosswalk apart from a speed bump, and — verified against a live
    // Overpass query, since "tags" verbosity turned out to omit coordinates
    // entirely for nodes — only "body" reliably returns both lat/lon and
    // tags together.
    final query = '[out:json][timeout:8];'
        '(node["highway"="crossing"](around:$_radiusM,$lat,$lon);'
        'node["traffic_calming"](around:$_radiusM,$lat,$lon););'
        'out body;';
    final elements = await _overpass.fetchElements(query,
        maxBytes: 2 * 1024 * 1024, timeout: const Duration(seconds: 8));
    final out = <OsmCrossingHazard>[];
    for (final el in elements) {
      if (out.length >= _maxResults) break;
      final id = el['id'] as int?;
      final lat = (el['lat'] as num?)?.toDouble();
      final lon = (el['lon'] as num?)?.toDouble();
      if (id == null || lat == null || lon == null) continue;
      if (!lat.isFinite || !lon.isFinite) continue;
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
      final tags = (el['tags'] as Map?)?.cast<String, dynamic>() ?? {};
      final kind = tags.containsKey('traffic_calming')
          ? CrossingHazardKind.speedBump
          : CrossingHazardKind.crosswalk;
      out.add(OsmCrossingHazard(id: id, position: LatLng(lat, lon), kind: kind));
    }
    return out;
  }
}
