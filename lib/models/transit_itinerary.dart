import 'package:flutter/material.dart';
import 'package:latlong2/latlong.dart';

import '../utils/polyline.dart';
import 'transit_mode.dart';

/// One stage of a journey: a walk to the stop, a ride, a walk to the door.
class TransitLeg {
  final TransitMode mode;

  /// Metres. The routing API omits this on some scheduled legs — it knows the
  /// timetable, not always the track geometry — so it is nullable rather than
  /// silently zero, which would make a summed journey distance a lie.
  final double? distanceMeters;
  final Duration duration;
  final DateTime startTime;
  final DateTime endTime;

  /// Stop or place this leg leaves from and arrives at. The API uses the
  /// sentinels `START` and `END` for the traveller's own endpoints; those are
  /// normalised to null here so the UI can substitute a localised label
  /// instead of showing an English keyword in every language.
  final String? fromName;
  final String? toName;

  /// Line as the passenger sees it printed: `S3`, `M42`, `Victoria`.
  final String? routeShortName;
  final String? routeLongName;

  /// Operator, e.g. the local transport authority. Shown so a traveller can
  /// tell which company's ticket or app applies.
  final String? agencyName;

  /// Direction sign on the front of the vehicle. Often the only way to pick
  /// the right platform, so it is worth surfacing prominently.
  final String? headsign;

  /// Official line colours from the operator's own published data, when it
  /// supplies them.
  final Color? routeColor;
  final Color? routeTextColor;

  /// Whether the times came from a live feed rather than the printed
  /// timetable. Drives the "times may not be current" caveat in the UI.
  final bool realTime;

  final List<LatLng> geometry;

  const TransitLeg({
    required this.mode,
    required this.duration,
    required this.startTime,
    required this.endTime,
    this.distanceMeters,
    this.fromName,
    this.toName,
    this.routeShortName,
    this.routeLongName,
    this.agencyName,
    this.headsign,
    this.routeColor,
    this.routeTextColor,
    this.realTime = false,
    this.geometry = const [],
  });

  /// Best single label for the line, or null on a leg the traveller walks.
  String? get displayLine {
    final short = routeShortName?.trim();
    if (short != null && short.isNotEmpty) return short;
    final long = routeLongName?.trim();
    if (long != null && long.isNotEmpty) return long;
    return null;
  }

  static TransitLeg? fromJson(Map<String, dynamic> json) {
    final start = _parseTime(json['startTime']);
    final end = _parseTime(json['endTime']);
    if (start == null || end == null) return null;

    final geometryJson = json['legGeometry'];
    return TransitLeg(
      mode: TransitMode.fromWire(json['mode']),
      duration: Duration(seconds: _asInt(json['duration']) ?? 0),
      startTime: start,
      endTime: end,
      distanceMeters: _asDouble(json['distance']),
      fromName: _placeName(json['from']),
      toName: _placeName(json['to']),
      routeShortName: _asText(json['routeShortName']),
      routeLongName: _asText(json['routeLongName']),
      agencyName: _asText(json['agencyName']),
      headsign: _asText(json['headsign']),
      routeColor: _parseColor(json['routeColor']),
      routeTextColor: _parseColor(json['routeTextColor']),
      realTime: json['realTime'] == true,
      geometry: geometryJson is Map<String, dynamic>
          ? _parseGeometry(geometryJson)
          : const [],
    );
  }

  static List<LatLng> _parseGeometry(Map<String, dynamic> json) {
    final points = json['points'];
    if (points is! String) return const [];
    // Honour the server's stated precision — see [decodePolyline].
    return decodePolyline(points, precision: _asInt(json['precision']) ?? 5);
  }
}

/// A complete door-to-door journey: usually walk, ride, walk.
class TransitItinerary {
  final Duration duration;
  final DateTime startTime;
  final DateTime endTime;

  /// Number of times the traveller changes vehicle.
  final int transfers;
  final List<TransitLeg> legs;

  const TransitItinerary({
    required this.duration,
    required this.startTime,
    required this.endTime,
    required this.transfers,
    required this.legs,
  });

  /// Legs actually carried by a scheduled service, in order.
  List<TransitLeg> get transitLegs =>
      legs.where((leg) => leg.mode.isTransit).toList();

  /// Total distance the traveller covers on foot, in metres.
  double get walkingDistanceMeters => legs
      .where((leg) => leg.mode == TransitMode.walk)
      .fold(0.0, (sum, leg) => sum + (leg.distanceMeters ?? 0));

  /// Where the traveller boards: the stop the first scheduled leg departs
  /// from, and when. This is the one thing a journey cannot be acted on
  /// without — the itinerary is useless if you do not know which station to
  /// walk to — so it is surfaced rather than left buried in the leg list.
  ({String name, DateTime time})? get boarding {
    for (final leg in legs) {
      if (!leg.mode.isTransit) continue;
      final name = leg.fromName;
      return name == null ? null : (name: name, time: leg.startTime);
    }
    return null;
  }

  /// Time spent walking before boarding. Worth showing on its own: a
  /// twenty-minute approach changes whether the journey is worth taking.
  Duration get accessWalk {
    var total = Duration.zero;
    for (final leg in legs) {
      if (leg.mode.isTransit) break;
      total += leg.duration;
    }
    return total;
  }

  /// True when no scheduled service is involved — the router decided walking
  /// the whole way beats waiting. Worth flagging rather than presenting as a
  /// transit result.
  bool get isWalkOnly => transitLegs.isEmpty;

  /// True when every scheduled leg carries live data. Anything less and the
  /// UI warns that times come from a published timetable.
  bool get isFullyRealTime {
    final scheduled = transitLegs;
    return scheduled.isNotEmpty && scheduled.every((leg) => leg.realTime);
  }

  static TransitItinerary? fromJson(Map<String, dynamic> json) {
    final start = _parseTime(json['startTime']);
    final end = _parseTime(json['endTime']);
    final rawLegs = json['legs'];
    if (start == null || end == null || rawLegs is! List) return null;

    final legs = <TransitLeg>[];
    for (final raw in rawLegs) {
      if (raw is! Map<String, dynamic>) continue;
      final leg = TransitLeg.fromJson(raw);
      if (leg != null) legs.add(leg);
    }
    // An itinerary with no readable leg describes no journey at all.
    if (legs.isEmpty) return null;

    return TransitItinerary(
      duration: Duration(seconds: _asInt(json['duration']) ?? 0),
      startTime: start,
      endTime: end,
      transfers: _asInt(json['transfers']) ?? 0,
      legs: legs,
    );
  }
}

// ── Defensive parsing helpers ───────────────────────────────────────────────
// Every one of these tolerates a missing or wrong-typed field. The routing
// service aggregates thousands of independently maintained public feeds, so
// partial and slightly malformed records are the normal case, not an
// exceptional one — and a traveller is far better served by an itinerary with
// a blank operator name than by no itinerary at all.

DateTime? _parseTime(Object? value) =>
    value is String ? DateTime.tryParse(value)?.toLocal() : null;

int? _asInt(Object? value) => value is num ? value.round() : null;

double? _asDouble(Object? value) => value is num ? value.toDouble() : null;

String? _asText(Object? value) {
  if (value is! String) return null;
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

/// `START` and `END` are the API's markers for the requested coordinates
/// rather than real stop names; the caller supplies a localised label.
String? _placeName(Object? place) {
  if (place is! Map) return null;
  final name = _asText(place['name']);
  if (name == null || name == 'START' || name == 'END') return null;
  return name;
}

/// Feed-supplied colours arrive as bare RGB hex, with or without a leading
/// `#`, and occasionally as something unusable. Anything that is not six hex
/// digits is dropped so the UI falls back to its own palette.
Color? _parseColor(Object? value) {
  if (value is! String) return null;
  final hex = value.startsWith('#') ? value.substring(1) : value;
  if (hex.length != 6) return null;
  final parsed = int.tryParse(hex, radix: 16);
  return parsed == null ? null : Color(0xFF000000 | parsed);
}
