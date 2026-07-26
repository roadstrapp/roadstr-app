import 'dart:convert';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:latlong2/latlong.dart';
import 'bounded_http.dart';

/// A speed camera position sourced from OpenStreetMap (not user-reported).
class OsmSpeedCamera {
  final int id;
  final LatLng position;
  /// OSM's optional maxspeed tag, normalized to km/h.  Null means that the
  /// camera is known but its limit is not mapped.
  final int? speedLimitKmh;
  const OsmSpeedCamera({
    required this.id,
    required this.position,
    this.speedLimitKmh,
  });
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
  // NB: overpass.osm.ch removed — Switzerland-only extract, returns empty
  // success for Italy (see SpeedLimitService._endpoints for the full story).
  static const _endpoints = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
  ];
  static const _radiusM = 3000; // fetch cameras within 3 km of position
  static const _minMoveM = 800.0; // min travel distance before re-querying
  static const _maxAgeMs = 120000; // re-query after 2 min even without movement
  static const _retryMs = 15000; // back-off delay after a failed attempt

  List<OsmSpeedCamera> _cached = [];
  LatLng? _lastQueryPos;
  bool _fetching = false;
  DateTime? _lastSuccessAt;
  DateTime? _nextRetryAt;
  int _endpointIdx = 0;

  /// The most recently fetched cameras near the last queried position.
  List<OsmSpeedCamera> get cachedCameras => _cached;

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
    // Camera nodes frequently have no maxspeed of their own.  Include the
    // nearby tagged road ways in the same request so the voice alert can read
    // the limit belonging to that road rather than guessing from the camera.
    final query = '[out:json][timeout:8];'
        '(node["highway"="speed_camera"](around:$_radiusM,${pos.latitude},${pos.longitude});'
        'node["enforcement"="maxspeed"](around:$_radiusM,${pos.latitude},${pos.longitude});)->.cameras;'
        '(.cameras; way(around.cameras:30)["highway"]["maxspeed"];);'
        'out tags geom;';
    final res = await BoundedHttp.post(
      Uri.parse(_endpoints[_endpointIdx]),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'User-Agent': 'Roadstr/1.0 (navigation app)',
      },
      body: 'data=${Uri.encodeQueryComponent(query)}',
      maxBytes: 5 * 1024 * 1024,
      timeout: const Duration(seconds: 8),
    );

    if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
    final data = jsonDecode(res.body) as Map<String, dynamic>;
    final elements =
        (data['elements'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final nodes = <({int id, LatLng position, Map<String, dynamic> tags})>[];
    final ways = <({int? speedLimitKmh, List<LatLng> geometry})>[];
    for (final el in elements) {
      final type = el['type'] as String?;
      final tags = (el['tags'] as Map?)?.cast<String, dynamic>() ?? {};
      if (type == 'way') {
        final geometry = (el['geometry'] as List?)
                ?.whereType<Map>()
                .map((p) => LatLng(
                    (p['lat'] as num).toDouble(), (p['lon'] as num).toDouble()))
                .toList() ??
            const <LatLng>[];
        if (geometry.length >= 2) {
          final maxspeedType = tags['maxspeed:type']?.toString().toLowerCase();
          ways.add((
            speedLimitKmh: _parseMaxspeed(
              tags['maxspeed']?.toString(),
              numericIsMph: (maxspeedType?.startsWith('us:') ?? false) ||
                  (maxspeedType?.startsWith('gb:') ?? false) ||
                  _usesMphByDefault(pos),
            ),
            geometry: geometry,
          ));
        }
        continue;
      }
      final id = el['id'] as int?;
      final lat = (el['lat'] as num?)?.toDouble();
      final lon = (el['lon'] as num?)?.toDouble();
      if (id == null || lat == null || lon == null) continue;
      if (!lat.isFinite || !lon.isFinite) continue;
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
      nodes.add((id: id, position: LatLng(lat, lon), tags: tags));
    }
    final out = <OsmSpeedCamera>[];
    for (final node in nodes) {
      int? limit = _parseMaxspeed(node.tags['maxspeed']?.toString());
      if (limit == null) {
        var nearest = double.infinity;
        for (final way in ways) {
          final d = _distanceToPolyline(node.position, way.geometry);
          if (d < nearest) {
            nearest = d;
            limit = way.speedLimitKmh;
          }
        }
        if (nearest > 30) limit = null;
      }
      out.add(OsmSpeedCamera(
        id: node.id,
        position: node.position,
        speedLimitKmh: limit,
      ));
    }
    return out;
  }

  static int? _parseMaxspeed(String? raw, {bool numericIsMph = false}) {
    if (raw == null) return null;
    final value = raw.trim().toLowerCase();
    final mph = RegExp(r'^(\d{1,3})\s*mph$').firstMatch(value);
    if (mph != null) return (int.parse(mph.group(1)!) * 1.60934).round();
    final kmh = RegExp(r'^(\d{1,3})(?:\s*(?:km/h|kph))?$').firstMatch(value);
    if (kmh == null) return null;
    final parsed = int.parse(kmh.group(1)!);
    if (parsed < 5 || parsed > 300) return null;
    return numericIsMph ? (parsed * 1.60934).round() : parsed;
  }

  static bool _usesMphByDefault(LatLng pos) {
    final lat = pos.latitude, lon = pos.longitude;
    return (lat >= 24.3 && lat <= 49.5 && lon >= -125 && lon <= -66) ||
        (lat >= 49.8 && lat <= 59 && lon >= -8.5 && lon <= 2) ||
        (lat >= 4 && lat <= 9 && lon >= -12 && lon <= -7) ||
        (lat >= 9.5 && lat <= 28.5 && lon >= 92 && lon <= 101.5);
  }

  static double _distanceToPolyline(LatLng p, List<LatLng> polyline) {
    var best = double.infinity;
    for (var i = 0; i < polyline.length - 1; i++) {
      best = math.min(best, _distanceToSegment(p, polyline[i], polyline[i + 1]));
    }
    return best;
  }

  static double _distanceToSegment(LatLng p, LatLng a, LatLng b) {
    const degM = 111320.0;
    final cosLat = math.cos(p.latitude * math.pi / 180);
    final dx = (b.longitude - a.longitude) * degM * cosLat;
    final dy = (b.latitude - a.latitude) * degM;
    final px = (p.longitude - a.longitude) * degM * cosLat;
    final py = (p.latitude - a.latitude) * degM;
    final len2 = dx * dx + dy * dy;
    final t = len2 == 0 ? 0.0 : ((px * dx + py * dy) / len2).clamp(0.0, 1.0);
    final ex = px - t * dx;
    final ey = py - t * dy;
    return math.sqrt(ex * ex + ey * ey);
  }
}
