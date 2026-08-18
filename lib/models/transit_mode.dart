import 'package:flutter/material.dart';

/// How one leg of a journey is travelled.
///
/// The wire names come from the MOTIS routing API, which serves every region
/// from one worldwide dataset — so this enum has to describe a Tokyo suburban
/// line, a São Paulo bus and a Swiss funicular equally well. Nothing here is
/// specific to a country, and nothing should become so: the correct way to add
/// a system is a new [TransitMode], never a special case keyed off an agency
/// or a city name.
enum TransitMode {
  // ── Street legs ────────────────────────────────────────────────────────────
  walk('WALK'),
  bike('BIKE'),
  car('CAR'),

  // ── Transit legs ───────────────────────────────────────────────────────────
  tram('TRAM'),
  subway('SUBWAY'),

  /// The API also emits `METRO` for some networks. Treated as [subway] at the
  /// presentation layer; kept distinct so a response is never silently
  /// reshaped into something the server did not say.
  metro('METRO'),
  suburban('SUBURBAN'),
  regionalRail('REGIONAL_RAIL'),
  regionalFastRail('REGIONAL_FAST_RAIL'),
  longDistance('LONG_DISTANCE'),
  highspeedRail('HIGHSPEED_RAIL'),
  nightRail('NIGHT_RAIL'),
  rail('RAIL'),
  bus('BUS'),
  coach('COACH'),
  ferry('FERRY'),
  airplane('AIRPLANE'),
  funicular('FUNICULAR'),
  aerialLift('AERIAL_LIFT'),

  /// Demand-responsive services: shared taxis, ride pooling, flexible routes.
  onDemand('ODM'),

  /// A mode this build does not know about. Reaching this is not a bug — the
  /// routing service adds modes over time, and an itinerary that is 90%
  /// understandable is worth far more to a traveller than an error screen.
  other('OTHER');

  const TransitMode(this.wireName);

  /// Exact string used by the routing API.
  final String wireName;

  /// Parses a wire value, falling back to [other] for anything unrecognised.
  ///
  /// Deliberately total: a new mode appearing server-side must degrade to a
  /// generic icon, never throw in the middle of parsing a journey.
  static TransitMode fromWire(Object? value) {
    if (value is! String) return other;
    final needle = value.trim().toUpperCase();
    for (final mode in values) {
      if (mode.wireName == needle) return mode;
    }
    return other;
  }

  /// Whether this leg is carried by a scheduled service, as opposed to the
  /// traveller moving under their own power. Drives which fields are worth
  /// showing: a walking leg has no line number, headsign or departure time.
  bool get isTransit => switch (this) {
        TransitMode.walk || TransitMode.bike || TransitMode.car => false,
        _ => true,
      };

  /// Whether the leg is rail-bound, for grouping in the UI.
  bool get isRail => switch (this) {
        TransitMode.subway ||
        TransitMode.metro ||
        TransitMode.suburban ||
        TransitMode.regionalRail ||
        TransitMode.regionalFastRail ||
        TransitMode.longDistance ||
        TransitMode.highspeedRail ||
        TransitMode.nightRail ||
        TransitMode.rail =>
          true,
        _ => false,
      };

  /// Icon for this mode. Material's transport set covers every mode the API
  /// can return, so there is no need for bespoke artwork here.
  IconData get icon => switch (this) {
        TransitMode.walk => Icons.directions_walk_rounded,
        TransitMode.bike => Icons.directions_bike_rounded,
        TransitMode.car => Icons.directions_car_rounded,
        TransitMode.tram => Icons.tram_rounded,
        TransitMode.subway || TransitMode.metro => Icons.subway_rounded,
        TransitMode.suburban => Icons.directions_transit_rounded,
        TransitMode.regionalRail ||
        TransitMode.regionalFastRail ||
        TransitMode.rail =>
          Icons.train_rounded,
        TransitMode.longDistance ||
        TransitMode.highspeedRail ||
        TransitMode.nightRail =>
          Icons.directions_railway_rounded,
        TransitMode.bus => Icons.directions_bus_rounded,
        TransitMode.coach => Icons.airport_shuttle_rounded,
        TransitMode.ferry => Icons.directions_boat_rounded,
        TransitMode.airplane => Icons.flight_rounded,
        TransitMode.funicular || TransitMode.aerialLift => Icons.cable_rounded,
        TransitMode.onDemand => Icons.local_taxi_rounded,
        TransitMode.other => Icons.commute_rounded,
      };
}
