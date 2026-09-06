import 'package:latlong2/latlong.dart';
import 'package:flutter/foundation.dart';
import 'overpass_client.dart';

/// A traffic-light position sourced from OpenStreetMap's `highway=
/// traffic_signals` node tag.
class OsmTrafficLight {
  final int id;
  final LatLng position;
  const OsmTrafficLight({required this.id, required this.position});
}

/// Fetches known traffic-light locations from OpenStreetMap via Overpass, so
/// the map can show a traffic-light icon at each intersection the way Google
/// Maps does — same throttle/cache/mirror-rotation pattern as
/// [SpeedCameraService], but with a tighter radius and a client-side result
/// cap: signals are far denser than speed cameras (every controlled
/// intersection in a city, vs a handful of cameras), so an unbounded query
/// over the same 3 km radius would both flood the map with icons and risk a
/// much heavier Overpass response.
class TrafficLightService {
  static const _radiusM = 1500;
  static const _minMoveM = 500.0; // min travel distance before re-querying
  static const _maxAgeMs = 120000; // re-query after 2 min even without movement
  static const _retryMs = 15000; // back-off delay after a failed attempt
  static const _maxResults = 400; // guard against dense urban intersections

  List<OsmTrafficLight> _cached = [];
  LatLng? _lastQueryPos;
  bool _fetching = false;
  DateTime? _lastSuccessAt;
  DateTime? _nextRetryAt;
  final _overpass = OverpassClient();

  /// The most recently fetched traffic lights near the last queried position.
  List<OsmTrafficLight> get cachedLights => _cached;

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
      debugPrint('[TrafficLight] Overpass → ${_cached.length} signals nearby');
    } catch (e) {
      debugPrint('[TrafficLight] Overpass error: $e');
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

  Future<List<OsmTrafficLight>> _fetch(LatLng pos) async {
    final lat = OverpassClient.coord(pos.latitude);
    final lon = OverpassClient.coord(pos.longitude);
    // "out skel" is just id + coordinates — no tags needed for a plain icon,
    // keeping the response as light as the density of results allows.
    final query = '[out:json][timeout:8];'
        'node["highway"="traffic_signals"](around:$_radiusM,$lat,$lon);'
        'out skel;';
    final elements = await _overpass.fetchElements(query,
        maxBytes: 2 * 1024 * 1024, timeout: const Duration(seconds: 8));
    final out = <OsmTrafficLight>[];
    for (final el in elements) {
      if (out.length >= _maxResults) break;
      final id = el['id'] as int?;
      final lat = (el['lat'] as num?)?.toDouble();
      final lon = (el['lon'] as num?)?.toDouble();
      if (id == null || lat == null || lon == null) continue;
      if (!lat.isFinite || !lon.isFinite) continue;
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
      out.add(OsmTrafficLight(id: id, position: LatLng(lat, lon)));
    }
    return out;
  }
}
