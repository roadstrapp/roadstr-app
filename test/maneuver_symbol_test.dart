import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/nav/maneuver_symbol.dart';

RouteStep step(
  String direction, {
  String modifier = '',
  int? exit,
}) =>
    RouteStep(
      instruction: '$direction $modifier',
      direction: direction,
      modifier: modifier,
      distanceM: 100,
      location: const LatLng(45, 9),
      exitNumber: exit,
    );

void main() {
  test('maps motorway actions to distinct, directional visuals', () {
    expect(
      ManeuverVisual.fromStep(step('off ramp', modifier: 'right')).kind,
      ManeuverVisualKind.exitRight,
    );
    expect(
      ManeuverVisual.fromStep(step('off ramp', modifier: 'left')).kind,
      ManeuverVisualKind.exitLeft,
    );
    expect(
      ManeuverVisual.fromStep(step('on ramp', modifier: 'right')).kind,
      ManeuverVisualKind.rampRight,
    );
    expect(
      ManeuverVisual.fromStep(step('merge', modifier: 'left')).kind,
      ManeuverVisualKind.mergeLeft,
    );
  });

  test('roundabout visual retains the requested exit number', () {
    final visual = ManeuverVisual.fromStep(step('roundabout', exit: 3));
    expect(visual.kind, ManeuverVisualKind.roundabout);
    expect(visual.roundaboutExit, 3);
  });

  test('an unknown roundabout exit stays unknown instead of becoming 1', () {
    // Defaulting to 1 does not say "no count available", it says "first exit"
    // — a wrong instruction whenever the router omitted the count or gave one
    // the route validator dropped.
    final visual = ManeuverVisual.fromStep(step('roundabout'));
    expect(visual.kind, ManeuverVisualKind.roundabout);
    expect(visual.roundaboutExit, isNull);
  });

  test('an out-of-range exit is clamped, not silently trusted', () {
    expect(ManeuverVisual.fromStep(step('roundabout', exit: 40)).roundaboutExit,
        12);
  });

  test('maps every turn modifier without left/right ambiguity', () {
    expect(
      ManeuverVisual.fromStep(step('turn', modifier: 'sharp left')).kind,
      ManeuverVisualKind.sharpLeft,
    );
    expect(
      ManeuverVisual.fromStep(step('turn', modifier: 'slight right')).kind,
      ManeuverVisualKind.slightRight,
    );
    expect(
      ManeuverVisual.fromStep(step('turn', modifier: 'uturn right')).kind,
      ManeuverVisualKind.uTurnRight,
    );
  });

  testWidgets('all maneuver families render as one coherent vector set',
      (tester) async {
    final theme = AppTheme.build(AppThemeId.lightNostr);
    final colors = theme.extension<RoadstrColors>()!;
    final steps = [
      step('continue', modifier: 'straight'),
      step('turn', modifier: 'slight left'),
      step('turn', modifier: 'left'),
      step('turn', modifier: 'sharp left'),
      step('turn', modifier: 'slight right'),
      step('turn', modifier: 'right'),
      step('turn', modifier: 'sharp right'),
      step('turn', modifier: 'uturn left'),
      step('turn', modifier: 'uturn right'),
      step('fork', modifier: 'left'),
      step('fork', modifier: 'right'),
      step('merge', modifier: 'left'),
      step('merge', modifier: 'right'),
      step('on ramp', modifier: 'left'),
      step('on ramp', modifier: 'right'),
      step('off ramp', modifier: 'left'),
      step('off ramp', modifier: 'right'),
      step('roundabout', exit: 3),
      step('arrive'),
      step('depart'),
      step('ferry'),
    ];

    await tester.pumpWidget(MaterialApp(
      theme: theme,
      home: Material(
        color: colors.surface1,
        child: Center(
          child: RepaintBoundary(
            child: ColoredBox(
              color: colors.surface1,
              child: SizedBox(
                width: 420,
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final value in steps)
                      ManeuverSymbol(
                        step: value,
                        size: 76,
                        colors: colors,
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    ));

    expect(find.byType(ManeuverSymbol), findsNWidgets(steps.length));
    await expectLater(
      find.byType(RepaintBoundary).last,
      matchesGoldenFile('goldens/maneuver_symbols.png'),
    );
  });
}
