import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/models/transit_itinerary.dart';
import 'package:roadstr/models/transit_mode.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/transit_service.dart';
import 'package:roadstr/utils/polyline.dart';

/// The fixture is a real, unedited response from the public routing service
/// for a short city hop, trimmed only to two itineraries. Parsing is tested
/// against what the server actually sends rather than against a hand-written
/// idea of it — the two drift, and only one of them ships to users.
Map<String, dynamic> _fixture() => jsonDecode(
        File('test/fixtures/transit_plan_berlin.json').readAsStringSync())
    as Map<String, dynamic>;

void main() {
  group('TransitMode', () {
    test('parses the wire names the router actually emits', () {
      expect(TransitMode.fromWire('WALK'), TransitMode.walk);
      expect(TransitMode.fromWire('SUBWAY'), TransitMode.subway);
      expect(TransitMode.fromWire('REGIONAL_RAIL'), TransitMode.regionalRail);
      expect(TransitMode.fromWire('HIGHSPEED_RAIL'), TransitMode.highspeedRail);
    });

    test('degrades unknown or malformed modes instead of throwing', () {
      // The router gains modes over time. An itinerary that is mostly
      // understandable must still reach the traveller.
      expect(TransitMode.fromWire('TELEPORT'), TransitMode.other);
      expect(TransitMode.fromWire(null), TransitMode.other);
      expect(TransitMode.fromWire(42), TransitMode.other);
    });

    test('separates self-powered legs from scheduled services', () {
      expect(TransitMode.walk.isTransit, isFalse);
      expect(TransitMode.bike.isTransit, isFalse);
      expect(TransitMode.bus.isTransit, isTrue);
      expect(TransitMode.ferry.isTransit, isTrue);
      // Unknown modes count as transit: they arrived on a leg the router
      // scheduled, so hiding them would silently shorten the journey.
      expect(TransitMode.other.isTransit, isTrue);
    });

    test('every mode has an icon', () {
      for (final mode in TransitMode.values) {
        expect(mode.icon, isA<IconData>(), reason: 'missing icon for $mode');
      }
    });
  });

  group('polyline decoding', () {
    test('honours the precision the server states', () {
      // Same string, two precisions. This is the whole reason precision is a
      // required argument: the transit router encodes at 7 while the format is
      // commonly documented as 5, and reading a 7 as a 5 does not nudge the
      // line — it multiplies every coordinate by 100, producing a latitude of
      // 5252°, which is not a place on Earth at all.
      final encoded =
          (_fixture()['itineraries'][0]['legs'][1]['legGeometry']
              as Map<String, dynamic>)['points'] as String;

      final correct = decodePolyline(encoded, precision: 7);
      final wrong = decodePolyline(encoded, precision: 5);

      expect(correct, isNotEmpty);
      expect(correct.first.latitude, closeTo(52.5, 0.3));
      expect(correct.first.longitude, closeTo(13.4, 0.3));
      // Proof the mistake is catastrophic rather than cosmetic: the result is
      // not merely a different place, it is off the globe.
      expect(wrong.first.latitude, closeTo(5252.5, 1));
      expect(wrong.first.latitude.abs(), greaterThan(90));
    });

    test('returns what it decoded when the input is truncated', () {
      final full = _fixture()['itineraries'][0]['legs'][0]['legGeometry']
          as Map<String, dynamic>;
      final encoded = full['points'] as String;
      final clipped = decodePolyline(
          encoded.substring(0, encoded.length ~/ 2),
          precision: 7);
      // A partial shape still draws most of the leg; throwing would lose the
      // entire itinerary over a damaged field.
      expect(clipped, isNotEmpty);
    });

    test('an empty geometry is not an error', () {
      expect(decodePolyline('', precision: 7), isEmpty);
    });
  });

  group('itinerary parsing', () {
    test('reads a real response into legs, lines and operators', () {
      final itinerary =
          TransitItinerary.fromJson(_fixture()['itineraries'][0]);

      expect(itinerary, isNotNull);
      expect(itinerary!.legs, hasLength(3));
      expect(itinerary.transfers, 0);

      // Walk → ride → walk, exactly the door-to-door shape asked for.
      expect(itinerary.legs.first.mode, TransitMode.walk);
      expect(itinerary.legs.last.mode, TransitMode.walk);

      final ride = itinerary.transitLegs.single;
      expect(ride.mode, TransitMode.metro);
      expect(ride.displayLine, 'S3');
      expect(ride.agencyName, 'S-Bahn Berlin GmbH');
      expect(ride.headsign, isNotNull);
      expect(ride.geometry, isNotEmpty);
    });

    test('keeps the operator line colour when the feed publishes one', () {
      final itinerary =
          TransitItinerary.fromJson(_fixture()['itineraries'][0])!;
      final ride = itinerary.transitLegs.single;
      // '0066ad' in the feed, opaque once parsed.
      expect(ride.routeColor, const Color(0xFF0066AD));
      expect(ride.routeTextColor, const Color(0xFFFFFFFF));
    });

    test('replaces the START/END sentinels with nulls', () {
      final itinerary =
          TransitItinerary.fromJson(_fixture()['itineraries'][0])!;
      // Those are API keywords, not place names; showing them verbatim would
      // put English in every translation.
      expect(itinerary.legs.first.fromName, isNull);
      expect(itinerary.legs.last.toName, isNull);
      expect(itinerary.legs.first.toName, isNotNull);
    });

    test('sums only the walking legs', () {
      final itinerary =
          TransitItinerary.fromJson(_fixture()['itineraries'][0])!;
      final expected = (itinerary.legs.first.distanceMeters ?? 0) +
          (itinerary.legs.last.distanceMeters ?? 0);
      expect(itinerary.walkingDistanceMeters, closeTo(expected, 0.001));
      expect(itinerary.walkingDistanceMeters, greaterThan(0));
    });

    test('survives missing and wrong-typed fields', () {
      // Thousands of independently maintained feeds guarantee ragged records.
      final itinerary = TransitItinerary.fromJson({
        'duration': 600,
        'startTime': '2026-08-19T08:00:00Z',
        'endTime': '2026-08-19T08:10:00Z',
        'legs': [
          {
            'mode': 'BUS',
            'duration': 600,
            'startTime': '2026-08-19T08:00:00Z',
            'endTime': '2026-08-19T08:10:00Z',
            'distance': null,
            'routeColor': 'not-a-colour',
            'agencyName': '   ',
            'from': {'name': 'START'},
          },
        ],
      });

      expect(itinerary, isNotNull);
      final leg = itinerary!.legs.single;
      expect(leg.mode, TransitMode.bus);
      expect(leg.distanceMeters, isNull, reason: 'unknown must not become 0');
      expect(leg.routeColor, isNull, reason: 'unusable colour is dropped');
      expect(leg.agencyName, isNull, reason: 'blank is not a name');
    });

    test('rejects an itinerary with no readable leg', () {
      expect(
        TransitItinerary.fromJson({
          'startTime': '2026-08-19T08:00:00Z',
          'endTime': '2026-08-19T08:10:00Z',
          'legs': <dynamic>[],
        }),
        isNull,
      );
      // Missing times describe no journey either.
      expect(TransitItinerary.fromJson({'legs': <dynamic>[]}), isNull);
    });

    test('flags a walk-only result rather than calling it transit', () {
      final walkOnly = TransitItinerary.fromJson({
        'duration': 900,
        'startTime': '2026-08-19T08:00:00Z',
        'endTime': '2026-08-19T08:15:00Z',
        'legs': [
          {
            'mode': 'WALK',
            'duration': 900,
            'startTime': '2026-08-19T08:00:00Z',
            'endTime': '2026-08-19T08:15:00Z',
            'distance': 1200.0,
          },
        ],
      })!;
      expect(walkOnly.isWalkOnly, isTrue);
      expect(walkOnly.transitLegs, isEmpty);
    });
  });

  group('request parameters', () {
    // Captures the query the service builds without touching the network.
    Uri capture() {
      late Uri seen;
      const service = TransitService(endpoint: 'https://example.test/plan');
      seen = service.debugBuildUri(
        from: const LatLng(44.4184, 12.1966),
        to: const LatLng(44.5058, 11.3428),
        departure: DateTime.utc(2026, 8, 19, 6),
      );
      return seen;
    }

    test('asks for a walking radius wider than the router default', () {
      final q = capture().queryParameters;
      // The router defaults to 900 s, which assumes the journey starts almost
      // on a stop. Measured on a real regional corridor: from 900 m away the
      // default returned no service at all, while 1800 s returned three
      // trains. Letting this silently fall back to the default would put that
      // "no public transport here" answer back in front of the user.
      expect(q['maxPreTransitTime'], '1800');
      expect(q['maxPostTransitTime'], '1800');
    });

    test('sends the endpoints and a UTC departure', () {
      final q = capture().queryParameters;
      expect(q['fromPlace'], '44.4184,12.1966');
      expect(q['toPlace'], '44.5058,11.3428');
      expect(q['time'], endsWith('Z'), reason: 'the router expects UTC');
      expect(q['numItineraries'], '3');
    });
  });

  group('boarding hub', () {
    test('names the stop to walk to and when it leaves', () {
      final itinerary =
          TransitItinerary.fromJson(_fixture()['itineraries'][0])!;
      final board = itinerary.boarding!;
      // The router picks the stop that actually yields the journey, which is
      // not always the nearest one; the traveller has to be told which.
      expect(board.name, 'S+U Berlin Hauptbahnhof');
      expect(board.time, itinerary.transitLegs.first.startTime);
    });

    test('counts only the walking before boarding', () {
      final itinerary =
          TransitItinerary.fromJson(_fixture()['itineraries'][0])!;
      // The walk at the far end must not inflate the approach time — that is
      // the number that decides whether the departure can be caught.
      expect(itinerary.accessWalk, itinerary.legs.first.duration);
      expect(itinerary.accessWalk, lessThan(itinerary.duration));
    });

    test('is absent when nothing is scheduled', () {
      final walkOnly = TransitItinerary.fromJson({
        'duration': 900,
        'startTime': '2026-08-19T08:00:00Z',
        'endTime': '2026-08-19T08:15:00Z',
        'legs': [
          {
            'mode': 'WALK',
            'duration': 900,
            'startTime': '2026-08-19T08:00:00Z',
            'endTime': '2026-08-19T08:15:00Z',
          },
        ],
      })!;
      expect(walkOnly.boarding, isNull);
    });
  });
}
