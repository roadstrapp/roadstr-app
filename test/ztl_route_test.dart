import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/ztl_service.dart';

/// Builds a straight run of points heading east from [start].
List<LatLng> _line(LatLng start, {required int count, double stepDeg = 0.0005}) =>
    [for (var i = 0; i < count; i++) LatLng(start.latitude, start.longitude + i * stepDeg)];

void main() {
  final service = ZtlService.instance;

  tearDown(() => service.debugSetRestrictedWays(const []));

  group('route analysis', () {
    test('a street the route drives along is reported as transited', () {
      final route = _line(const LatLng(44.50, 11.34), count: 20);
      // Same corridor, offset by well under the on-street threshold.
      service.debugSetRestrictedWays([
        ZtlWay(
          name: 'Via Centrale',
          points: _line(const LatLng(44.50002, 11.3405), count: 8),
        ),
      ]);

      final result = service.analyseRoute(route);
      expect(result.transited.map((w) => w.name), ['Via Centrale']);
      expect(result.nearby, isEmpty);
    });

    test('a street merely near the route is reported separately', () {
      final route = _line(const LatLng(44.50, 11.34), count: 20);
      // ~60 m north: close enough to tempt a driver, not on the route.
      service.debugSetRestrictedWays([
        ZtlWay(
          name: 'Vicolo Chiuso',
          points: _line(const LatLng(44.50054, 11.3405), count: 8),
        ),
      ]);

      final result = service.analyseRoute(route);
      expect(result.transited, isEmpty);
      expect(result.nearby.map((w) => w.name), ['Vicolo Chiuso']);
    });

    test('a distant street is reported neither way', () {
      final route = _line(const LatLng(44.50, 11.34), count: 20);
      // ~2 km away: not the driver's problem, and painting it red would make
      // the warning meaningless in any historic centre.
      service.debugSetRestrictedWays([
        ZtlWay(
          name: 'Altrove',
          points: _line(const LatLng(44.52, 11.3405), count: 8),
        ),
      ]);

      final result = service.analyseRoute(route);
      expect(result.transited, isEmpty);
      expect(result.nearby, isEmpty);
    });

    test('merely crossing a restricted street is a warning, not a red route',
        () {
      // A main road crossing a restricted side street at a junction. The car
      // passes through the intersection without entering the zone, so this is
      // deliberately NOT "transited": turning the whole route red at every
      // junction with a restricted street would recreate the constant,
      // ignorable alarm this feature exists to replace.
      final route = [
        const LatLng(44.50, 11.34),
        const LatLng(44.50, 11.36),
      ];
      service.debugSetRestrictedWays([
        ZtlWay(name: 'Traversa', points: [
          const LatLng(44.4996, 11.35),
          const LatLng(44.5004, 11.35),
        ]),
      ]);

      final result = service.analyseRoute(route);
      expect(result.transited, isEmpty);
      expect(result.nearby.map((w) => w.name), ['Traversa'],
          reason: 'the driver should still be told it is restricted');
    });

    test('a street is never reported as both transited and nearby', () {
      final route = _line(const LatLng(44.50, 11.34), count: 20);
      service.debugSetRestrictedWays([
        ZtlWay(name: 'Via Centrale', points: _line(const LatLng(44.50002, 11.3405), count: 8)),
        ZtlWay(name: 'Vicolo Chiuso', points: _line(const LatLng(44.50054, 11.3405), count: 8)),
      ]);

      final result = service.analyseRoute(route);
      final both = result.transited.toSet().intersection(result.nearby.toSet());
      expect(both, isEmpty);
      expect(result.transited, hasLength(1));
      expect(result.nearby, hasLength(1));
    });
  });

  group('degenerate input', () {
    test('an empty or single-point route yields nothing', () {
      service.debugSetRestrictedWays([
        ZtlWay(name: 'Via Centrale', points: _line(const LatLng(44.50, 11.34), count: 8)),
      ]);
      expect(service.analyseRoute(const []).transited, isEmpty);
      expect(service.analyseRoute([const LatLng(44.50, 11.34)]).nearby, isEmpty);
    });

    test('no loaded ZTL data is not an error', () {
      // Overpass failures are silent by design; the route must still draw.
      final result = service.analyseRoute(_line(const LatLng(44.50, 11.34), count: 5));
      expect(result.transited, isEmpty);
      expect(result.nearby, isEmpty);
    });

    test('a one-point restricted way is skipped rather than crashing', () {
      service.debugSetRestrictedWays([
        ZtlWay(name: 'Degenere', points: [const LatLng(44.50, 11.34)]),
      ]);
      final result = service.analyseRoute(_line(const LatLng(44.50, 11.34), count: 5));
      expect(result.transited, isEmpty);
      expect(result.nearby, isEmpty);
    });
  });

  group('per-point classification', () {
    test('only the restricted stretch is marked', () {
      // A route running east: the middle third overlaps a restricted street,
      // the rest does not. This is the shape of a real journey — a couple of
      // restricted blocks inside an otherwise ordinary route — and the whole
      // point is that the ends must stay unmarked.
      final route = _line(const LatLng(44.50, 11.34), count: 30);
      service.debugSetRestrictedWays([
        ZtlWay(
          name: 'Via Diaz',
          points: _line(const LatLng(44.50002, 11.345), count: 10),
        ),
      ]);

      final flags = service.classifyPoints(route);
      expect(flags, hasLength(route.length));
      expect(flags.first, isFalse, reason: 'the approach is an ordinary road');
      expect(flags.last, isFalse, reason: 'and so is the road after it');
      expect(flags.any((f) => f), isTrue,
          reason: 'the restricted stretch must be marked');

      // Contiguous: a restricted street produces one run, not a dotted mess.
      final firstOn = flags.indexOf(true);
      final lastOn = flags.lastIndexOf(true);
      expect(flags.sublist(firstOn, lastOn + 1).every((f) => f), isTrue);
    });

    test('no restricted data marks nothing', () {
      final route = _line(const LatLng(44.50, 11.34), count: 10);
      expect(service.classifyPoints(route).any((f) => f), isFalse);
    });

    test('an empty route yields no flags', () {
      expect(service.classifyPoints(const []), isEmpty);
    });
  });

  group('passing-by advisory', () {
    test('reports a restricted street beside the car', () {
      service.debugSetRestrictedWays([
        ZtlWay(name: 'Via Diaz', points: _line(const LatLng(44.50, 11.34), count: 10)),
      ]);
      // ~30 m away: driving past it, not on it.
      final near = service.nearestRestrictedWay(const LatLng(44.50027, 11.3405));
      expect(near?.name, 'Via Diaz');
    });

    test('stays silent when nothing restricted is close', () {
      service.debugSetRestrictedWays([
        ZtlWay(name: 'Via Diaz', points: _line(const LatLng(44.50, 11.34), count: 10)),
      ]);
      // ~200 m away: far enough that warning about it would be constant noise
      // in any historic centre.
      expect(service.nearestRestrictedWay(const LatLng(44.5018, 11.3405)), isNull);
    });
  });
}
