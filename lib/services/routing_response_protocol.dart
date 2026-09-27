import 'dart:convert';

import 'package:latlong2/latlong.dart';

import 'nav_phrases.dart';

const int kMaxRoundaboutArms = 20;

/// One speed-limit zone along a route. It applies until the next entry.
typedef SpeedLimitEntry = ({double distFromStartM, int? speedKmh});

enum RouteAvoidance {
  none,
  highwayAndTollFree,
  minimizedHighwaysAndTolls,
  offRoadAvoided,
}

class RouteStep {
  final String instruction;
  final String direction;
  final String modifier;
  final double distanceM;
  final LatLng location;
  final int? exitNumber;
  final int? roundaboutArmCount;
  final String? exitLabel;
  final String roadName;
  final String roadRef;

  bool get isUrbanStreet => roadName.isNotEmpty && roadRef.isEmpty;

  const RouteStep({
    required this.instruction,
    required this.direction,
    this.modifier = '',
    required this.distanceM,
    required this.location,
    this.exitNumber,
    this.roundaboutArmCount,
    this.exitLabel,
    this.roadName = '',
    this.roadRef = '',
  });

  RouteStep copyWith({
    String? instruction,
    String? direction,
    String? modifier,
    double? distanceM,
    LatLng? location,
    int? exitNumber,
    int? roundaboutArmCount,
    String? exitLabel,
    String? roadName,
    String? roadRef,
  }) =>
      RouteStep(
        instruction: instruction ?? this.instruction,
        direction: direction ?? this.direction,
        modifier: modifier ?? this.modifier,
        distanceM: distanceM ?? this.distanceM,
        location: location ?? this.location,
        exitNumber: exitNumber ?? this.exitNumber,
        roundaboutArmCount: roundaboutArmCount ?? this.roundaboutArmCount,
        exitLabel: exitLabel ?? this.exitLabel,
        roadName: roadName ?? this.roadName,
        roadRef: roadRef ?? this.roadRef,
      );
}

class RouteResult {
  final List<LatLng> polyline;
  final List<RouteStep> steps;
  final double totalDistanceM;
  final double totalDurationS;
  final List<SpeedLimitEntry> speedLimits;
  final RouteAvoidance avoidance;
  final bool fromAvoidanceRouter;

  const RouteResult({
    required this.polyline,
    required this.steps,
    required this.totalDistanceM,
    required this.totalDurationS,
    this.speedLimits = const [],
    this.avoidance = RouteAvoidance.none,
    this.fromAvoidanceRouter = false,
  });

  RouteResult withAvoidance(RouteAvoidance value) => RouteResult(
        polyline: polyline,
        steps: steps,
        totalDistanceM: totalDistanceM,
        totalDurationS: totalDurationS,
        speedLimits: speedLimits,
        avoidance: value,
        fromAvoidanceRouter: fromAvoidanceRouter,
      );

  bool get isHighwayAndTollAvoidance =>
      avoidance == RouteAvoidance.highwayAndTollFree ||
      avoidance == RouteAvoidance.minimizedHighwaysAndTolls;

  bool get avoidsHighwaysAndTolls =>
      avoidance == RouteAvoidance.highwayAndTollFree;

  bool get isOffRoadAvoidance => avoidance == RouteAvoidance.offRoadAvoided;

  int? speedLimitAt(double elapsedM) {
    if (speedLimits.isEmpty) return null;
    int? result;
    for (final entry in speedLimits) {
      if (entry.distFromStartM > elapsedM) break;
      result = entry.speedKmh;
    }
    return result;
  }

  String get durationLabel {
    final minutes = (totalDurationS / 60).round();
    if (minutes < 60) return '$minutes min';
    final hours = minutes ~/ 60;
    final remainder = minutes % 60;
    return '${hours}h ${remainder}min';
  }
}

class ValhallaRouteResponse {
  final RouteResult route;
  final Map<String, dynamic> summary;

  const ValhallaRouteResponse({required this.route, required this.summary});
}

class OsrmRetimeLeg {
  final double? distanceM;
  final double? durationS;

  const OsrmRetimeLeg({required this.distanceM, required this.durationS});
}

class RoutingException implements Exception {
  final int? statusCode;
  final String message;
  final String? body;

  RoutingException({this.statusCode, required this.message, this.body});

  @override
  String toString() =>
      'RoutingException(statusCode: $statusCode, message: $message)';
}

/// Socket-free decoding and normalization of every shipped routing response.
class RoutingResponseProtocol {
  static const maxRoutePoints = 250000;
  static const maxRouteSteps = 60000;

  static RouteResult parseOpenRouteService(
    String body, {
    required LatLng fallbackOrigin,
  }) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      final features = data['features'] as List?;
      if (features == null || features.isEmpty) {
        throw RoutingException(
          message: 'OpenRouteService response missing features',
          body: body,
        );
      }
      final feature = features.first as Map<String, dynamic>;
      final properties = feature['properties'] as Map<String, dynamic>;
      final summary = properties['summary'] as Map<String, dynamic>?;
      final segments = properties['segments'] as List?;
      final geometry = feature['geometry'] as Map<String, dynamic>;
      final rawCoordinates = geometry['coordinates'] as List;
      _checkPointCount(rawCoordinates.length);
      final coordinates = rawCoordinates.map(_coordinate).toList();

      final steps = <RouteStep>[];
      if (segments != null && segments.isNotEmpty) {
        final segment = segments.first as Map<String, dynamic>;
        for (final rawStep in segment['steps'] as List? ?? const []) {
          final step = rawStep as Map<String, dynamic>;
          final instruction = step['instruction'] as String? ?? '';
          final distance = (step['distance'] as num?)?.toDouble() ?? 0.0;
          final type = _intValue(step['type']) ?? 6;
          final maneuver = _orsManeuver(type);
          final waypoints = step['way_points'] as List?;
          final location = waypoints != null && waypoints.isNotEmpty
              ? coordinates[
                  (waypoints.first as int).clamp(0, coordinates.length - 1)]
              : (coordinates.isNotEmpty ? coordinates.first : fallbackOrigin);
          steps.add(RouteStep(
            instruction: instruction,
            direction: maneuver.direction,
            modifier: maneuver.modifier,
            distanceM: distance,
            location: location,
            exitNumber: maneuver.direction == 'roundabout'
                ? ((step['exit_number'] as num?)?.toInt() ??
                    parseExitNumber(instruction))
                : null,
          ));
        }
      }

      return validate(RouteResult(
        polyline: coordinates,
        steps: steps,
        totalDistanceM: (summary?['distance'] as num?)?.toDouble() ?? 0.0,
        totalDurationS: (summary?['duration'] as num?)?.toDouble() ?? 0.0,
      ));
    } on RoutingException {
      rethrow;
    } catch (error) {
      throw RoutingException(message: error.toString());
    }
  }

  static RouteResult parseGraphHopper(
    String body, {
    required LatLng fallbackOrigin,
  }) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      final paths = data['paths'] as List?;
      if (paths == null || paths.isEmpty) {
        throw RoutingException(
          message: 'GraphHopper response missing paths',
          body: body,
        );
      }
      final path = paths.first as Map<String, dynamic>;
      final points = path['points'] as Map<String, dynamic>?;
      final coordinates = <LatLng>[];
      if (points?['coordinates'] != null) {
        final rawCoordinates = points!['coordinates'] as List;
        _checkPointCount(rawCoordinates.length);
        coordinates.addAll(rawCoordinates.map(_coordinate));
      }

      final steps = <RouteStep>[];
      for (final rawInstruction in path['instructions'] as List? ?? const []) {
        final instruction = rawInstruction as Map<String, dynamic>;
        final text = instruction['text'] as String? ?? '';
        final distance = (instruction['distance'] as num?)?.toDouble() ?? 0.0;
        final sign = _intValue(instruction['sign']) ?? 0;
        final maneuver = _graphHopperManeuver(sign);
        final index = (instruction['interval'] as List?)?.first as int? ?? 0;
        final location = index >= 0 && index < coordinates.length
            ? coordinates[index]
            : (coordinates.isNotEmpty ? coordinates.first : fallbackOrigin);
        steps.add(RouteStep(
          instruction: text,
          direction: maneuver.direction,
          modifier: maneuver.modifier,
          distanceM: distance,
          location: location,
          exitNumber: maneuver.direction == 'roundabout'
              ? ((instruction['exit_number'] as num?)?.toInt() ??
                  parseExitNumber(text))
              : null,
        ));
      }

      final speedLimits = <SpeedLimitEntry>[];
      try {
        final details = path['details'] as Map<String, dynamic>?;
        final intervals = details?['max_speed'] as List?;
        if (intervals != null && coordinates.isNotEmpty) {
          const distance = Distance();
          final cumulative = <double>[0.0];
          for (var index = 1; index < coordinates.length; index++) {
            cumulative.add(cumulative.last +
                distance.as(
                  LengthUnit.Meter,
                  coordinates[index - 1],
                  coordinates[index],
                ));
          }
          for (final rawInterval in intervals) {
            final interval = rawInterval as List;
            final fromIndex =
                (interval[0] as num).toInt().clamp(0, coordinates.length - 1);
            final value = interval[2];
            speedLimits.add((
              distFromStartM: cumulative[fromIndex],
              speedKmh: value is num && value > 0 ? value.toInt() : null,
            ));
          }
        }
      } catch (_) {}

      return validate(RouteResult(
        polyline: coordinates,
        steps: steps,
        totalDistanceM: (path['distance'] as num?)?.toDouble() ?? 0.0,
        totalDurationS: (path['time'] as num?) != null
            ? (path['time'] as num).toDouble() / 1000.0
            : 0.0,
        speedLimits: speedLimits,
      ));
    } on RoutingException {
      rethrow;
    } catch (error) {
      throw RoutingException(message: error.toString());
    }
  }

  static List<RouteResult> parseOsrmRoutes(
    String body, {
    String languageCode = 'en',
  }) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      if (data['code'] != 'Ok') {
        throw RoutingException(
          message: 'OSRM returned error code: ${data['code']}',
        );
      }
      final routes = data['routes'] as List?;
      if (routes == null || routes.isEmpty) {
        throw RoutingException(message: 'OSRM response missing routes');
      }
      return routes
          .map((route) => _parseOsrmRoute(
                route as Map<String, dynamic>,
                languageCode,
              ))
          .toList();
    } on RoutingException {
      rethrow;
    } catch (error) {
      throw RoutingException(message: error.toString());
    }
  }

  static ValhallaRouteResponse parseValhalla(String body) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      final trip = data['trip'] as Map<String, dynamic>?;
      if (trip == null || trip['status'] != 0) {
        throw RoutingException(
          message: 'Valhalla returned no route',
          body: body,
        );
      }
      final summary = trip['summary'] as Map<String, dynamic>? ?? const {};
      final legs = trip['legs'] as List? ?? const [];
      if (legs.isEmpty) {
        throw RoutingException(message: 'Valhalla response missing legs');
      }

      final coordinates = <LatLng>[];
      final steps = <RouteStep>[];
      for (final rawLeg in legs) {
        final leg = rawLeg as Map<String, dynamic>;
        final legCoordinates =
            decodeValhallaPolyline(leg['shape'] as String? ?? '');
        if (legCoordinates.isEmpty) {
          throw RoutingException(message: 'Valhalla response missing shape');
        }
        final sharesEndpoint =
            coordinates.isNotEmpty && coordinates.last == legCoordinates.first;
        final coordinateOffset =
            sharesEndpoint ? coordinates.length - 1 : coordinates.length;
        if (sharesEndpoint) {
          coordinates.addAll(legCoordinates.skip(1));
        } else {
          coordinates.addAll(legCoordinates);
        }
        _checkPointCount(coordinates.length);

        for (final rawManeuver in leg['maneuvers'] as List? ?? const []) {
          final maneuver = rawManeuver as Map<String, dynamic>;
          final localIndex =
              (maneuver['begin_shape_index'] as num?)?.toInt() ?? 0;
          final pointIndex =
              (coordinateOffset + localIndex).clamp(0, coordinates.length - 1);
          final type = (maneuver['type'] as num?)?.toInt() ?? 0;
          final mapped = _valhallaManeuver(type);
          steps.add(RouteStep(
            instruction: (maneuver['instruction'] as String?)?.trim() ?? '',
            direction: mapped.direction,
            modifier: mapped.modifier,
            distanceM: ((maneuver['length'] as num?)?.toDouble() ?? 0.0) * 1000,
            location: coordinates[pointIndex],
            exitNumber: (maneuver['roundabout_exit_count'] as num?)?.toInt(),
            exitLabel: _valhallaExitLabel(maneuver),
          ));
        }
      }

      final route = validate(RouteResult(
        polyline: coordinates,
        steps: steps,
        totalDistanceM: ((summary['length'] as num?)?.toDouble() ?? 0.0) * 1000,
        totalDurationS: (summary['time'] as num?)?.toDouble() ?? 0.0,
        fromAvoidanceRouter: true,
      ));
      return ValhallaRouteResponse(route: route, summary: summary);
    } on RoutingException {
      rethrow;
    } catch (error) {
      throw RoutingException(message: error.toString());
    }
  }

  static List<OsrmRetimeLeg>? parseOsrmRetimeLegs(String body) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      if (data['code'] != 'Ok') return null;
      final routes = data['routes'] as List?;
      if (routes == null || routes.isEmpty) return null;
      final route = routes.first as Map<String, dynamic>;
      final legs = route['legs'] as List?;
      if (legs == null) return null;
      return legs.map((rawLeg) {
        final leg = rawLeg as Map<String, dynamic>;
        return OsrmRetimeLeg(
          distanceM: (leg['distance'] as num?)?.toDouble(),
          durationS: (leg['duration'] as num?)?.toDouble(),
        );
      }).toList();
    } catch (_) {
      return null;
    }
  }

  static RouteResult validate(RouteResult route) {
    final cleanedSteps =
        sanitiseDecorations(coalescePassiveNameChanges(route.steps));
    if (!identical(cleanedSteps, route.steps)) {
      route = RouteResult(
        polyline: route.polyline,
        steps: cleanedSteps,
        totalDistanceM: route.totalDistanceM,
        totalDurationS: route.totalDurationS,
        speedLimits: route.speedLimits,
        avoidance: route.avoidance,
        fromAvoidanceRouter: route.fromAvoidanceRouter,
      );
    }
    if (route.polyline.length < 2 ||
        route.steps.isEmpty ||
        route.steps.length > maxRouteSteps ||
        !route.totalDistanceM.isFinite ||
        route.totalDistanceM <= 0 ||
        route.totalDistanceM > 50000000 ||
        !route.totalDurationS.isFinite ||
        route.totalDurationS < 0 ||
        route.totalDurationS > 366 * 86400) {
      throw RoutingException(message: 'Malformed or incomplete route');
    }
    for (final point in route.polyline) {
      if (!point.latitude.isFinite ||
          !point.longitude.isFinite ||
          point.latitude < -90 ||
          point.latitude > 90 ||
          point.longitude < -180 ||
          point.longitude > 180) {
        throw RoutingException(message: 'Route contains invalid coordinates');
      }
    }
    for (final step in route.steps) {
      if (!step.distanceM.isFinite ||
          step.distanceM < 0 ||
          step.instruction.length > 1000 ||
          step.direction.length > 100 ||
          step.modifier.length > 100 ||
          !step.location.latitude.isFinite ||
          !step.location.longitude.isFinite ||
          step.location.latitude < -90 ||
          step.location.latitude > 90 ||
          step.location.longitude < -180 ||
          step.location.longitude > 180) {
        throw RoutingException(message: 'Route contains an invalid maneuver');
      }
    }
    var previousDistance = -1.0;
    for (final entry in route.speedLimits) {
      if (!entry.distFromStartM.isFinite ||
          entry.distFromStartM < previousDistance ||
          entry.distFromStartM > route.totalDistanceM ||
          (entry.speedKmh != null &&
              (entry.speedKmh! <= 0 || entry.speedKmh! > 500))) {
        throw RoutingException(message: 'Route contains invalid speed limits');
      }
      previousDistance = entry.distFromStartM;
    }
    return route;
  }

  static List<RouteStep> coalescePassiveNameChanges(List<RouteStep> steps) {
    if (steps.length < 2) return steps;
    var changed = false;
    final output = <RouteStep>[];
    for (final step in steps) {
      final passiveRename = step.direction == 'new name' &&
          (step.modifier.isEmpty || step.modifier == 'straight');
      if (!passiveRename || output.isEmpty) {
        output.add(step);
        continue;
      }
      changed = true;
      final previous = output.removeLast();
      output.add(previous.copyWith(
        distanceM: previous.distanceM + step.distanceM,
      ));
    }
    return changed ? output : steps;
  }

  static List<RouteStep> sanitiseDecorations(List<RouteStep> steps) {
    var changed = false;
    final output = <RouteStep>[];
    for (final step in steps) {
      final badNumber = step.exitNumber != null &&
          (step.exitNumber! < 1 || step.exitNumber! > kMaxRoundaboutArms);
      final badArmCount = step.roundaboutArmCount != null &&
          (step.roundaboutArmCount! < 3 ||
              step.roundaboutArmCount! > kMaxRoundaboutArms ||
              (!badNumber &&
                  step.exitNumber != null &&
                  step.roundaboutArmCount! < step.exitNumber!));
      final label = step.exitLabel;
      final badLabel = label != null && label.length > 32;
      if (!badNumber && !badArmCount && !badLabel) {
        output.add(step);
        continue;
      }
      changed = true;
      output.add(RouteStep(
        instruction: step.instruction,
        direction: step.direction,
        modifier: step.modifier,
        distanceM: step.distanceM,
        location: step.location,
        exitNumber: badNumber ? null : step.exitNumber,
        roundaboutArmCount: badArmCount ? null : step.roundaboutArmCount,
        exitLabel: badLabel ? null : label,
      ));
    }
    return changed ? output : steps;
  }

  static List<LatLng> decodeValhallaPolyline(String encoded) {
    final points = <LatLng>[];
    var index = 0;
    var latitude = 0;
    var longitude = 0;

    int readDelta() {
      var result = 0;
      var shift = 0;
      int byte;
      do {
        if (index >= encoded.length || shift > 30) {
          throw RoutingException(message: 'Malformed Valhalla shape');
        }
        byte = encoded.codeUnitAt(index++) - 63;
        if (byte < 0 || byte > 63) {
          throw RoutingException(message: 'Malformed Valhalla shape');
        }
        result |= (byte & 0x1f) << shift;
        shift += 5;
      } while (byte >= 0x20);
      return (result & 1) != 0 ? ~(result >> 1) : result >> 1;
    }

    while (index < encoded.length) {
      latitude += readDelta();
      longitude += readDelta();
      points.add(LatLng(latitude / 1e6, longitude / 1e6));
      _checkPointCount(points.length);
    }
    return points;
  }

  static int? parseExitNumber(String instruction) {
    final numeric = RegExp(
      r'\b(1[0-2]|[1-9])(?:°|º|ª|st|nd|rd|th)',
      caseSensitive: false,
    ).firstMatch(instruction);
    if (numeric != null) return int.tryParse(numeric.group(1)!);
    const ordinals = {
      'first': 1,
      'prima': 1,
      'première': 1,
      'primera': 1,
      'primeira': 1,
      'second': 2,
      'seconda': 2,
      'deuxième': 2,
      'segunda': 2,
      'third': 3,
      'terza': 3,
      'troisième': 3,
      'tercera': 3,
      'terceira': 3,
      'fourth': 4,
      'quarta': 4,
      'quatrième': 4,
      'cuarta': 4,
      'fifth': 5,
      'quinta': 5,
      'cinquième': 5,
      'sixth': 6,
      'sesta': 6,
      'sixième': 6,
      'sexta': 6,
    };
    final lower = instruction.toLowerCase();
    for (final entry in ordinals.entries) {
      if (lower.contains(entry.key)) return entry.value;
    }
    return null;
  }

  static RouteResult _parseOsrmRoute(
    Map<String, dynamic> route,
    String languageCode,
  ) {
    final legs = route['legs'] as List?;
    if (legs == null || legs.isEmpty) {
      throw RoutingException(message: 'OSRM response missing legs');
    }
    final leg = legs.first as Map<String, dynamic>;
    final geometry = route['geometry'] as Map<String, dynamic>;
    final rawCoordinates = geometry['coordinates'] as List;
    _checkPointCount(rawCoordinates.length);
    final coordinates = rawCoordinates.map(_coordinate).toList();

    final steps = <RouteStep>[];
    for (final rawStep in leg['steps'] as List? ?? const []) {
      final step = rawStep as Map<String, dynamic>;
      final maneuver = step['maneuver'] as Map<String, dynamic>;
      final rawLocation = maneuver['location'] as List;
      final providerDirection = maneuver['type'] as String? ?? 'straight';
      final providerModifier = maneuver['modifier'] as String? ?? '';
      final correctedModifier =
          _correctedModifier(step, providerDirection, providerModifier);
      final resolvedDirection = providerDirection == 'continue' &&
              correctedModifier != providerModifier &&
              correctedModifier != 'straight'
          ? 'turn'
          : providerDirection;
      steps.add(RouteStep(
        instruction: _buildInstruction(step, languageCode),
        direction: resolvedDirection,
        modifier: correctedModifier,
        distanceM: (step['distance'] as num).toDouble(),
        location: _coordinate(rawLocation),
        exitNumber: (maneuver['exit'] as num?)?.toInt(),
        exitLabel: (step['exits'] as String?)?.trim(),
        roadName: (step['name'] as String? ?? '').trim(),
        roadRef: (step['ref'] as String? ?? '').trim(),
      ));
    }
    return validate(RouteResult(
      polyline: coordinates,
      steps: steps,
      totalDistanceM: (route['distance'] as num).toDouble(),
      totalDurationS: (route['duration'] as num).toDouble(),
    ));
  }

  static String _buildInstruction(
    Map<String, dynamic> step,
    String languageCode,
  ) {
    final maneuver = step['maneuver'] as Map<String, dynamic>;
    final type = maneuver['type'] as String? ?? '';
    final providerModifier = maneuver['modifier'] as String? ?? '';
    final modifier = _correctedModifier(step, type, providerModifier);
    final name = (step['name'] as String? ?? '').trim();
    final ref = (step['ref'] as String? ?? '').trim();
    final refFirst = ref.split(RegExp(r'[;,]')).first.trim();
    final roadName = refFirst.isNotEmpty ? refFirst : name;
    String phrase(String key) => navPhrase(languageCode, key);
    final road = roadName.isEmpty ? '' : '${phrase('on')}$roadName';
    String withRoad(String key) => '${phrase(key)}$road';
    String? turnFor(String value) => switch (value) {
          'left' => withRoad('turnLeft'),
          'right' => withRoad('turnRight'),
          'slight left' => withRoad('keepLeft'),
          'slight right' => withRoad('keepRight'),
          'sharp left' => withRoad('sharpLeft'),
          'sharp right' => withRoad('sharpRight'),
          'uturn' => withRoad('uturn'),
          _ => null,
        };

    return switch (type) {
      'depart' => withRoad('depart'),
      'arrive' => phrase('arrive'),
      'turn' => turnFor(modifier) ?? withRoad('continueStraight'),
      'new name' => turnFor(modifier) ?? withRoad('continueOn'),
      'continue' => turnFor(modifier) ?? withRoad('continueStraight'),
      'merge' => withRoad('merge'),
      'on ramp' => switch (modifier) {
          'left' => withRoad('rampLeft'),
          'right' => withRoad('rampRight'),
          _ => withRoad('takeRamp'),
        },
      'off ramp' => _buildExitInstruction(
          step,
          refFirst,
          road,
          phrase,
          withRoad,
        ),
      'fork' => withRoad(modifier.contains('left') ? 'forkLeft' : 'forkRight'),
      'end of road' =>
        withRoad(modifier.contains('left') ? 'endLeft' : 'endRight'),
      'roundabout' ||
      'rotary' =>
        '${phrase(type == 'rotary' ? 'rotary' : 'roundabout').replaceAll('{n}', '${(maneuver['exit'] as num?)?.toInt() ?? 1}')}$road',
      _ => withRoad('continueStraight'),
    };
  }

  static String _buildExitInstruction(
    Map<String, dynamic> step,
    String refFirst,
    String road,
    String Function(String) phrase,
    String Function(String) withRoad,
  ) {
    final exits = (step['exits'] as String?)?.trim();
    final label = exits != null && exits.isNotEmpty
        ? exits
        : (refFirst.isNotEmpty ? refFirst : null);
    if (label != null) {
      return '${phrase('exitLabelled').replaceAll('{label}', label)}$road';
    }
    return withRoad('exitPlain');
  }

  static String _correctedModifier(
    Map<String, dynamic> step,
    String type,
    String providerModifier,
  ) {
    if (type == 'roundabout' || type == 'rotary') return providerModifier;
    final intersections = step['intersections'] as List?;
    final first = intersections?.whereType<Map>().firstOrNull;
    final bearings = (first?['bearings'] as List?)
        ?.whereType<num>()
        .map((value) => value.toDouble())
        .toList();
    final inIndex = (first?['in'] as num?)?.toInt();
    final outIndex = (first?['out'] as num?)?.toInt();
    if (bearings == null ||
        inIndex == null ||
        outIndex == null ||
        inIndex < 0 ||
        outIndex < 0 ||
        inIndex >= bearings.length ||
        outIndex >= bearings.length) {
      return providerModifier;
    }
    final inboundTravelBearing = (bearings[inIndex] + 180) % 360;
    var delta = (bearings[outIndex] - inboundTravelBearing) % 360;
    if (delta > 180) delta -= 360;
    if (delta < -180) delta += 360;
    final magnitude = delta.abs();
    if (magnitude < 18 || magnitude > 160) return providerModifier;
    if (delta < 0) return magnitude > 110 ? 'sharp left' : 'left';
    return magnitude > 110 ? 'sharp right' : 'right';
  }

  static ({String direction, String modifier}) _valhallaManeuver(int type) =>
      switch (type) {
        1 => (direction: 'depart', modifier: ''),
        2 => (direction: 'depart', modifier: 'right'),
        3 => (direction: 'depart', modifier: 'left'),
        4 => (direction: 'arrive', modifier: ''),
        5 => (direction: 'arrive', modifier: 'right'),
        6 => (direction: 'arrive', modifier: 'left'),
        7 => (direction: 'new name', modifier: 'straight'),
        8 || 22 => (direction: 'continue', modifier: 'straight'),
        9 => (direction: 'turn', modifier: 'slight right'),
        10 => (direction: 'turn', modifier: 'right'),
        11 => (direction: 'turn', modifier: 'sharp right'),
        12 => (direction: 'turn', modifier: 'uturn right'),
        13 => (direction: 'turn', modifier: 'uturn left'),
        14 => (direction: 'turn', modifier: 'sharp left'),
        15 => (direction: 'turn', modifier: 'left'),
        16 => (direction: 'turn', modifier: 'slight left'),
        17 => (direction: 'on ramp', modifier: 'straight'),
        18 => (direction: 'on ramp', modifier: 'right'),
        19 => (direction: 'on ramp', modifier: 'left'),
        20 => (direction: 'off ramp', modifier: 'right'),
        21 => (direction: 'off ramp', modifier: 'left'),
        23 => (direction: 'fork', modifier: 'right'),
        24 => (direction: 'fork', modifier: 'left'),
        25 => (direction: 'merge', modifier: ''),
        26 || 27 => (direction: 'roundabout', modifier: ''),
        28 || 29 => (direction: 'ferry', modifier: 'straight'),
        _ => (direction: 'continue', modifier: 'straight'),
      };

  static ({String direction, String modifier}) _orsManeuver(int type) =>
      switch (type) {
        0 => (direction: 'turn', modifier: 'left'),
        1 => (direction: 'turn', modifier: 'right'),
        2 => (direction: 'turn', modifier: 'sharp left'),
        3 => (direction: 'turn', modifier: 'sharp right'),
        4 => (direction: 'turn', modifier: 'slight left'),
        5 => (direction: 'turn', modifier: 'slight right'),
        6 => (direction: 'continue', modifier: 'straight'),
        7 => (direction: 'roundabout', modifier: ''),
        8 => (direction: 'continue', modifier: 'straight'),
        9 => (direction: 'turn', modifier: 'uturn left'),
        10 => (direction: 'arrive', modifier: ''),
        11 => (direction: 'depart', modifier: ''),
        12 => (direction: 'fork', modifier: 'left'),
        13 => (direction: 'fork', modifier: 'right'),
        _ => (direction: 'continue', modifier: 'straight'),
      };

  static ({String direction, String modifier}) _graphHopperManeuver(int sign) =>
      switch (sign) {
        -8 => (direction: 'turn', modifier: 'uturn left'),
        -7 => (direction: 'fork', modifier: 'left'),
        -3 => (direction: 'turn', modifier: 'sharp left'),
        -2 => (direction: 'turn', modifier: 'left'),
        -1 => (direction: 'turn', modifier: 'slight left'),
        0 => (direction: 'continue', modifier: 'straight'),
        1 => (direction: 'turn', modifier: 'slight right'),
        2 => (direction: 'turn', modifier: 'right'),
        3 => (direction: 'turn', modifier: 'sharp right'),
        4 => (direction: 'arrive', modifier: ''),
        5 => (direction: 'continue', modifier: 'straight'),
        6 => (direction: 'roundabout', modifier: ''),
        7 => (direction: 'fork', modifier: 'right'),
        8 => (direction: 'turn', modifier: 'uturn right'),
        _ => (direction: 'continue', modifier: 'straight'),
      };

  static String? _valhallaExitLabel(Map<String, dynamic> maneuver) {
    final sign = maneuver['sign'] as Map?;
    final elements = sign?['exit_number_elements'] as List?;
    if (elements == null || elements.isEmpty || elements.first is! Map) {
      return null;
    }
    final text = (elements.first as Map)['text']?.toString().trim();
    return text == null || text.isEmpty ? null : text;
  }

  static LatLng _coordinate(dynamic raw) {
    final coordinate = raw as List;
    return LatLng(
      (coordinate[1] as num).toDouble(),
      (coordinate[0] as num).toDouble(),
    );
  }

  static int? _intValue(dynamic value) =>
      value is num ? value.toInt() : int.tryParse(value?.toString() ?? '');

  static void _checkPointCount(int count) {
    if (count > maxRoutePoints) {
      throw RoutingException(message: 'Route contains too many points');
    }
  }
}
