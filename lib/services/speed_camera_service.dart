import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';

/// A speed camera position sourced from OpenStreetMap (not user-reported).
class OsmSpeedCamera {
  final int id;
  final LatLng position;
  const OsmSpeedCamera({required this.id, required this.position});
}

/// Fetches known speed camera locations from OpenStreetMap via Overpass.
///
/// This is additive to — never a replacement for — the community-reported
/// Nostr [RoadCategory.speedCamera] events: OSM gives a baseline global
/// database (so the feature is useful from the very first install, before
/// any Roadstr user has reported anything), while Nostr reports stay more
/// current for cameras OSM hasn't mapped yet or that have moved.
///
/// Same throttle/cache/mirror-rotation pattern as [SpeedLimitService].
class SpeedCameraService {
  static const _endpoints = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
    'https://overpass.osm.ch/api/interpreter',
  ];
  static const _radiusM  = 3000;    // fetch cameras within 3 km of position
  static const _minMoveM = 800.0;   // min travel distance before re-querying
  static const _maxAgeMs = 120000;  // re-query after 2 min even without movement
  static const _retryMs  = 15000;   // back-off delay after a failed attempt

  List<OsmSpeedCamera> _cached = [];
  LatLng?   _lastQueryPos;
  bool      _fetching = false;
  DateTime? _lastSuccessAt;
  DateTime? _nextRetryAt;
  int       _endpointIdx = 0;

  /// The most recently fetched cameras near the last queried position.
  List<OsmSpeedCamera> get cachedCameras => _cached;

  void reset() {
    _cached         = [];
    _lastQueryPos   = null;
    _lastSuccessAt  = null;
    _nextRetryAt    = null;
    _fetching       = false;
  }

  Future<void> updateIfNeeded(LatLng pos) async {
    if (!_needsQuery(pos)) return;
    _fetching = true;
    try {
      _cached         = await _fetch(pos);
      _lastQueryPos   = pos;
      _lastSuccessAt  = DateTime.now();
      _nextRetryAt    = null;
      debugPrint('[SpeedCamera] Overpass → ${_cached.length} cameras nearby');
    } catch (e) {
      debugPrint('[SpeedCamera] Overpass error: $e');
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

  Future<List<OsmSpeedCamera>> _fetch(LatLng pos) async {
    // highway=speed_camera is the classic/most widely-used tag; enforcement
    // nodes with maxspeed cover newer mapping practice for fixed cameras.
    final query = '[out:json][timeout:6];'
        '(node["highway"="speed_camera"](around:$_radiusM,${pos.latitude},${pos.longitude});'
        'node["enforcement"="maxspeed"](around:$_radiusM,${pos.latitude},${pos.longitude}););'
        'out;';
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
    final out = <OsmSpeedCamera>[];
    for (final el in elements) {
      final id  = el['id'] as int?;
      final lat = (el['lat'] as num?)?.toDouble();
      final lon = (el['lon'] as num?)?.toDouble();
      if (id == null || lat == null || lon == null) continue;
      if (!lat.isFinite || !lon.isFinite) continue;
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
      out.add(OsmSpeedCamera(id: id, position: LatLng(lat, lon)));
    }
    return out;
  }
}
