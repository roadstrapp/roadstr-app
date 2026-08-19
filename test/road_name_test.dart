import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/map/map_chrome.dart';

RouteStep step({String name = '', String ref = ''}) => RouteStep(
      instruction: 'x',
      direction: 'turn',
      distanceM: 100,
      location: const LatLng(44.5, 11.34),
      roadName: name,
      roadRef: ref,
    );

void main() {
  group('town street or numbered road', () {
    test('a named street with no code is a town street', () {
      expect(step(name: 'Via Roma').isUrbanStreet, isTrue);
      expect(step(name: 'Piazza Goffredo Mameli').isUrbanStreet, isTrue);
    });

    test('anything carrying a road code is not', () {
      // The distinction drives the label under the cursor: repeating a
      // motorway code there for two hundred kilometres is noise, while a
      // street name is genuinely hard to know from inside a car.
      expect(step(name: 'Strada Statale 3 bis Tiberina', ref: 'SS3bis')
          .isUrbanStreet, isFalse);
      expect(step(ref: 'A14').isUrbanStreet, isFalse);
    });

    test('a step with no road at all is neither', () {
      expect(step().isUrbanStreet, isFalse);
    });
  });

  group('the label under the cursor', () {
    final colors =
        AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!;

    Widget host(String name) => MaterialApp(
          home: Scaffold(
            body: Center(child: CurrentStreetLabel(name: name, colors: colors)),
          ),
        );

    testWidgets('sizes itself to the name', (tester) async {
      await tester.pumpWidget(host('Via Roma'));
      final short = tester.getSize(find.byType(CurrentStreetLabel)).width;

      await tester.pumpWidget(host('Via Torquato Tasso'));
      final long = tester.getSize(find.byType(CurrentStreetLabel)).width;

      // The whole point: padding the short name out to the long one's width
      // leaves a plaque with a word floating in the middle of it.
      expect(long, greaterThan(short));
    });

    testWidgets('a very long name is capped rather than run off screen',
        (tester) async {
      await tester.pumpWidget(host(
          'Circonvallazione alla Rotonda dei Goti e delle Vecchie Mura'));
      final width = tester.getSize(find.byType(CurrentStreetLabel)).width;
      final screen = tester.view.physicalSize.width / tester.view.devicePixelRatio;
      expect(width, lessThanOrEqualTo(screen * 0.52 + 1));
      expect(tester.takeException(), isNull);
    });

    testWidgets('stays clear of the speed-limit sign beside it', (tester) async {
      // The sign occupies the left 94 logical pixels at the same height. A
      // centred label wider than the remaining space slides under it, and a
      // covered speed limit is a worse loss than a truncated street name.
      await tester.pumpWidget(host(
          'Circonvallazione alla Rotonda dei Goti e delle Vecchie Mura'));
      final size = tester.getSize(find.byType(CurrentStreetLabel));
      final screen =
          tester.view.physicalSize.width / tester.view.devicePixelRatio;
      final leftEdge = (screen - size.width) / 2;
      expect(leftEdge, greaterThan(94),
          reason: 'the label must start to the right of the sign');
    });

    testWidgets('does not intercept taps meant for the map', (tester) async {
      // It sits over the map; swallowing gestures there would make the area
      // under it dead to panning.
      await tester.pumpWidget(host('Via Roma'));
      // Material inserts IgnorePointers of its own, so assert on ours: the one
      // wrapping the label itself.
      expect(
        find.ancestor(
          of: find.byType(Container),
          matching: find.byType(IgnorePointer),
        ),
        findsWidgets,
      );
      final ignore = tester.widget<IgnorePointer>(find
          .descendant(
              of: find.byType(CurrentStreetLabel),
              matching: find.byType(IgnorePointer))
          .first);
      expect(ignore.ignoring, isTrue);
    });
  });
}
