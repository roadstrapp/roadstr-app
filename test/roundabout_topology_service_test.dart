import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/roundabout_topology_service.dart';

void main() {
  test('counts four road arms independently of the selected exit', () {
    final elements = _roundabout(arms: 4, center: const LatLng(45, 9));
    final counts = RoundaboutTopologyService.parseArmCounts(
        elements, const [LatLng(45, 9)]);
    expect(counts, [4]);
  });

  test('recognises a five-arm roundabout as a pentagonal topology', () {
    final elements = _roundabout(arms: 5, center: const LatLng(45, 9));
    final counts = RoundaboutTopologyService.parseArmCounts(
        elements, const [LatLng(45, 9)]);
    expect(counts, [5]);
  });

  test('ignores footways crossing the ring', () {
    final elements = _roundabout(arms: 4, center: const LatLng(45, 9));
    elements.add({
      'type': 'way',
      'id': 900,
      'nodes': [1, 990],
      'tags': {'highway': 'footway'},
    });
    final counts = RoundaboutTopologyService.parseArmCounts(
        elements, const [LatLng(45, 9)]);
    expect(counts, [4]);
  });

  test('returns null when no mapped roundabout is near the maneuver', () {
    final elements = _roundabout(arms: 4, center: const LatLng(45, 9));
    final counts = RoundaboutTopologyService.parseArmCounts(
        elements, const [LatLng(46, 10)]);
    expect(counts, [isNull]);
  });
  test('dual-carriageway approaches count once, not twice', () {
    // The field report: a four-exit roundabout drew a seven-arm sign. An
    // approach arriving as two one-way carriageways touches the ring at two
    // nodes, and the old fixed 14-degree merge left them separate. Four
    // exits, three of them dual — seven arms.
    final counts = RoundaboutTopologyService.parseArmCounts(
      _dualCarriagewayRoundabout(
          arms: 4, dualArms: 3, center: const LatLng(45, 9)),
      const [LatLng(45, 9)],
    );
    expect(counts, [4]);
  });

  test('every approach dual still counts once each', () {
    final counts = RoundaboutTopologyService.parseArmCounts(
      _dualCarriagewayRoundabout(
          arms: 4, dualArms: 4, center: const LatLng(45, 9)),
      const [LatLng(45, 9)],
    );
    expect(counts, [4]);
  });
}

List<Map<String, dynamic>> _roundabout({
  required int arms,
  required LatLng center,
}) {
  const radiusDegrees = 0.0001;
  final elements = <Map<String, dynamic>>[];
  final ringNodeIds = <int>[];
  for (var i = 0; i < arms; i++) {
    final angle = 2 * math.pi * i / arms;
    final id = i + 1;
    ringNodeIds.add(id);
    elements.add({
      'type': 'node',
      'id': id,
      'lat': center.latitude + radiusDegrees * math.sin(angle),
      'lon': center.longitude + radiusDegrees * math.cos(angle),
    });
  }
  elements.add({
    'type': 'way',
    'id': 100,
    'nodes': [...ringNodeIds, ringNodeIds.first],
    'tags': {'highway': 'secondary', 'junction': 'roundabout'},
  });
  for (var i = 0; i < arms; i++) {
    elements.add({
      'type': 'way',
      'id': 200 + i,
      'nodes': [ringNodeIds[i], 1000 + i],
      'tags': {'highway': 'residential'},
    });
  }
  return elements;
}

/// A roundabout where the first [dualArms] approaches arrive as two separate
/// one-way carriageways, meeting the ring about 30 degrees apart.
List<Map<String, dynamic>> _dualCarriagewayRoundabout({
  required int arms,
  required int dualArms,
  required LatLng center,
}) {
  const radiusDegrees = 0.0001; // ~11 m ring
  const halfSplit = 15 * math.pi / 180;
  final elements = <Map<String, dynamic>>[];
  final ringNodeIds = <int>[];
  var nodeId = 1;
  var roadId = 200;

  void ringNode(double angle) {
    elements.add({
      'type': 'node',
      'id': nodeId,
      'lat': center.latitude + radiusDegrees * math.sin(angle),
      'lon': center.longitude + radiusDegrees * math.cos(angle),
    });
    ringNodeIds.add(nodeId);
    elements.add({
      'type': 'way',
      'id': roadId++,
      'nodes': [nodeId, 5000 + nodeId],
      'tags': {'highway': 'secondary'},
    });
    nodeId++;
  }

  for (var i = 0; i < arms; i++) {
    final angle = 2 * math.pi * i / arms;
    if (i < dualArms) {
      ringNode(angle - halfSplit);
      ringNode(angle + halfSplit);
    } else {
      ringNode(angle);
    }
  }

  elements.add({
    'type': 'way',
    'id': 100,
    'nodes': [...ringNodeIds, ringNodeIds.first],
    'tags': {'highway': 'secondary', 'junction': 'roundabout'},
  });
  return elements;
}
