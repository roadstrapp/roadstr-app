import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:latlong2/latlong.dart';

import 'overpass_client.dart';

/// Reads the real road topology of roundabouts from OpenStreetMap.
///
/// Routing engines normally return only the ordinal of the exit to take. That
/// is not the number of arms around the whole roundabout: "take the third
/// exit" can describe a four-, five- or six-arm junction. This service batches
/// every roundabout in a route into one Overpass request and counts the road
/// arms connected to the mapped roundabout ring.
class RoundaboutTopologyService {
  RoundaboutTopologyService({OverpassClient? overpass})
      : _overpass = overpass ?? OverpassClient();

  static const _searchRadiusM = 70;
  static const _maxResponseBytes = 5 * 1024 * 1024;
  static const _maxSupportedArms = 20;

  // Paths and cycleways crossing a roundabout must not become extra car-road
  // arms in the navigation sign. Service roads are retained because they are
  // often genuine exits to fuel stations, car parks and industrial sites.
  static const _roadClasses = <String>{
    'motorway',
    'trunk',
    'primary',
    'secondary',
    'tertiary',
    'unclassified',
    'residential',
    'living_street',
    'service',
    'road',
    'motorway_link',
    'trunk_link',
    'primary_link',
    'secondary_link',
    'tertiary_link',
  };

  final OverpassClient _overpass;

  /// Returns one arm count for every point in [roundabouts].
  ///
  /// A null entry means that OSM did not contain a usable ring near that
  /// point, or that every public Overpass mirror failed. Callers should keep
  /// the route and render a conservative fallback rather than fail navigation.
  Future<List<int?>> fetchArmCounts(List<LatLng> roundabouts) async {
    if (roundabouts.isEmpty) return const [];
    final query = _query(roundabouts);
    try {
      final elements = await _overpass.fetchElementsHedged(
        query,
        maxBytes: _maxResponseBytes,
        timeout: const Duration(seconds: 8),
      );
      if (elements == null) return List<int?>.filled(roundabouts.length, null);
      return parseArmCounts(elements, roundabouts);
    } catch (error) {
      debugPrint('[Roundabout] topology lookup failed: $error');
      return List<int?>.filled(roundabouts.length, null);
    }
  }

  static String _query(List<LatLng> points) {
    final searches = StringBuffer();
    for (final point in points) {
      searches
        ..write('way(around:$_searchRadiusM,')
        ..write(OverpassClient.coord(point.latitude))
        ..write(',')
        ..write(OverpassClient.coord(point.longitude))
        ..write(')["junction"="roundabout"];');
    }
    return '[out:json][timeout:7];'
        '(${searches.toString()})->.roundabouts;'
        'node(w.roundabouts)->.roundaboutNodes;'
        'way(bn.roundaboutNodes)["highway"]->.connectedRoads;'
        '(.roundabouts;.roundaboutNodes;.connectedRoads;);'
        'out body;';
  }

  /// Pure parser kept public for deterministic topology regression tests.
  @visibleForTesting
  static List<int?> parseArmCounts(
    List<Map<String, dynamic>> elements,
    List<LatLng> points,
  ) {
    final nodes = <int, LatLng>{};
    final roundaboutWays = <int, Set<int>>{};
    final roadWays = <Set<int>>[];

    for (final element in elements) {
      final type = element['type']?.toString();
      final id = (element['id'] as num?)?.toInt();
      if (type == 'node' && id != null) {
        final lat = (element['lat'] as num?)?.toDouble();
        final lon = (element['lon'] as num?)?.toDouble();
        if (lat != null &&
            lon != null &&
            lat.isFinite &&
            lon.isFinite &&
            lat >= -90 &&
            lat <= 90 &&
            lon >= -180 &&
            lon <= 180) {
          nodes[id] = LatLng(lat, lon);
        }
        continue;
      }
      if (type != 'way' || id == null) continue;
      final nodeIds = (element['nodes'] as List?)
              ?.whereType<num>()
              .map((value) => value.toInt())
              .toSet() ??
          const <int>{};
      if (nodeIds.isEmpty) continue;
      final tags = (element['tags'] as Map?)?.cast<String, dynamic>() ??
          const <String, dynamic>{};
      if (tags['junction'] == 'roundabout') {
        roundaboutWays[id] = nodeIds;
        continue;
      }
      if (_roadClasses.contains(tags['highway']?.toString())) {
        roadWays.add(nodeIds);
      }
    }

    final components = _roundaboutComponents(roundaboutWays, nodes, roadWays);
    const distance = Distance();
    return points.map((point) {
      _RoundaboutComponent? nearest;
      var nearestM = double.infinity;
      for (final component in components) {
        for (final node in component.nodes) {
          final metres = distance.as(LengthUnit.Meter, point, node);
          if (metres < nearestM) {
            nearestM = metres;
            nearest = component;
          }
        }
      }
      return nearestM <= _searchRadiusM ? nearest?.armCount : null;
    }).toList(growable: false);
  }

  static List<_RoundaboutComponent> _roundaboutComponents(
    Map<int, Set<int>> ways,
    Map<int, LatLng> nodes,
    List<Set<int>> roadWays,
  ) {
    final waysByNode = <int, Set<int>>{};
    for (final entry in ways.entries) {
      for (final node in entry.value) {
        waysByNode.putIfAbsent(node, () => <int>{}).add(entry.key);
      }
    }

    final pending = ways.keys.toSet();
    final out = <_RoundaboutComponent>[];
    while (pending.isNotEmpty) {
      final queue = <int>[pending.first];
      final componentWays = <int>{};
      final componentNodes = <int>{};
      while (queue.isNotEmpty) {
        final wayId = queue.removeLast();
        if (!pending.remove(wayId)) continue;
        componentWays.add(wayId);
        final wayNodes = ways[wayId] ?? const <int>{};
        componentNodes.addAll(wayNodes);
        for (final node in wayNodes) {
          for (final neighbour in waysByNode[node] ?? const <int>{}) {
            if (pending.contains(neighbour)) queue.add(neighbour);
          }
        }
      }

      final geometry = componentNodes.map((id) => nodes[id]).nonNulls.toList();
      if (geometry.length < 3) continue;
      final connectedNodeIds = <int>{};
      for (final road in roadWays) {
        connectedNodeIds.addAll(road.intersection(componentNodes));
      }
      final connected = connectedNodeIds
          .map((id) => nodes[id])
          .nonNulls
          .toList(growable: false);
      if (connected.length < 3) continue;

      final center = LatLng(
        geometry.map((p) => p.latitude).reduce((a, b) => a + b) /
            geometry.length,
        geometry.map((p) => p.longitude).reduce((a, b) => a + b) /
            geometry.length,
      );
      final bearings = connected
          .map((point) => _bearingDegrees(center, point))
          .toList(growable: false);
      const distanceCalc = Distance();
      final radii = geometry
          .map((p) => distanceCalc.as(LengthUnit.Meter, center, p))
          .where((value) => value > 1)
          .toList(growable: false);
      if (radii.isEmpty) continue;
      final radius = radii.reduce((a, b) => a + b) / radii.length;
      final arms = _clusteredBearingCount(bearings, radius);
      if (arms == null || arms < 3 || arms > _maxSupportedArms) continue;
      out.add(_RoundaboutComponent(nodes: geometry, armCount: arms));
    }
    return out;
  }

  static double _bearingDegrees(LatLng center, LatLng point) {
    final meanLat = (center.latitude + point.latitude) * math.pi / 360;
    final x = (point.longitude - center.longitude) * math.cos(meanLat);
    final y = point.latitude - center.latitude;
    var angle = math.atan2(x, y) * 180 / math.pi;
    if (angle < 0) angle += 360;
    return angle;
  }

  /// Counts the road arms, or returns null when the count cannot be trusted.
  ///
  /// A single road usually meets a roundabout twice — one node for the
  /// entrance, one for the exit — and on a dual carriageway those two nodes
  /// sit several metres apart on the ring. How far apart they look *in
  /// degrees* depends entirely on how big the roundabout is: twelve metres
  /// subtends about 23° on a 30 m ring and 74° on a 10 m one. A fixed
  /// threshold therefore cannot work, and the 14° it used to be merged almost
  /// nothing: a four-exit roundabout with three dual-carriageway approaches
  /// came out as seven arms, which is what the sign then drew.
  ///
  /// The threshold is now derived from the ring's own radius. And when the
  /// result is still ambiguous — arms closer together than any real junction
  /// would place them — this returns null so the caller falls back to the
  /// generic ring. On a road, a symbol showing the wrong junction is worse
  /// than one showing no detail.
  static int? _clusteredBearingCount(List<double> bearings, double radiusM) {
    if (bearings.length < 2) return bearings.length;
    // Chord of ~13 m: the span between the two carriageways of one approach,
    // including the splitter island. Clamped so a tiny ring cannot swallow
    // genuine arms and a huge one still merges its own pairs.
    final chordRatio = (13.0 / (2 * radiusM)).clamp(0.0, 1.0);
    final mergeWithinDegrees =
        (2 * math.asin(chordRatio) * 180 / math.pi).clamp(18.0, 50.0);
    final sorted = [...bearings]..sort();
    final groups = <List<double>>[
      [sorted.first]
    ];
    for (final angle in sorted.skip(1)) {
      if (angle - groups.last.last <= mergeWithinDegrees) {
        groups.last.add(angle);
      } else {
        groups.add([angle]);
      }
    }
    if (groups.length > 1 &&
        groups.first.first + 360 - groups.last.last <= mergeWithinDegrees) {
      groups.first.insertAll(0, groups.removeLast());
    }
    if (groups.length < 2) return groups.length;
    // Sanity check on the result: real arms are evenly spread, and even an
    // eight-arm junction leaves 45° between them. Anything tighter means the
    // merge did not separate approaches from carriageways, and the count is a
    // guess — say so rather than draw it.
    final centres = groups
        .map((g) => g.reduce((a, b) => a + b) / g.length)
        .toList(growable: false);
    var minGap = 360.0;
    for (var i = 0; i < centres.length; i++) {
      final next = centres[(i + 1) % centres.length];
      var gap = next - centres[i];
      if (gap < 0) gap += 360;
      if (gap < minGap) minGap = gap;
    }
    return minGap < 35 ? null : groups.length;
  }
}

class _RoundaboutComponent {
  final List<LatLng> nodes;
  final int armCount;

  const _RoundaboutComponent({required this.nodes, required this.armCount});
}
