import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/nav/maneuver_symbol.dart';

/// Hunting a crash reported while approaching a roundabout. A painter that
/// throws takes the whole navigation screen with it, so every combination of
/// exit ordinal and arm count the pipeline can produce is exercised here —
/// including the ones the route validator is supposed to make impossible,
/// because "supposed to" is what this is testing.
void main() {
  late RoadstrColors colors;

  setUpAll(() {
    colors = AppTheme.build(AppThemeId.lightNostr).extension<RoadstrColors>()!;
  });

  void paint(RouteStep step) {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    ManeuverSymbolPainter(
      visual: ManeuverVisual.fromStep(step),
      accent: colors.accent,
      muted: colors.textSecondary,
      surface: colors.surface2,
    ).paint(canvas, const Size(100, 100));
    recorder.endRecording();
  }

  RouteStep roundabout({int? exit, int? arms}) => RouteStep(
        instruction: 'Roundabout',
        direction: 'roundabout',
        modifier: '',
        distanceM: 100,
        location: const LatLng(45, 9),
        exitNumber: exit,
        roundaboutArmCount: arms,
      );

  test('every exit/arm combination paints without throwing', () {
    final failures = <String>[];
    final values = <int?>[null, -5, 0, 1, 2, 3, 4, 7, 12, 20, 21, 99, 1000];
    for (final exit in values) {
      for (final arms in values) {
        try {
          paint(roundabout(exit: exit, arms: arms));
        } catch (e) {
          failures.add('exit=$exit arms=$arms -> $e');
        }
      }
    }
    expect(failures, isEmpty, reason: failures.join('\n'));
  });

  test('an exit beyond the arm count still paints', () {
    // The enrichment refuses to attach an arm count lower than the router's
    // exit — but the two arrive from different sources, so the painter must
    // not depend on that holding.
    expect(() => paint(roundabout(exit: 7, arms: 3)), returnsNormally);
    expect(() => paint(roundabout(exit: 20, arms: 3)), returnsNormally);
  });

  test('every maneuver kind paints at extreme sizes', () {
    for (final direction in const [
      'roundabout', 'rotary', 'turn', 'off ramp', 'on ramp', 'merge', 'fork',
      'arrive', 'depart', 'ferry', 'continue', 'new name', 'use lane', ''
    ]) {
      for (final modifier in const [
        '', 'left', 'right', 'slight left', 'sharp right', 'uturn', 'straight'
      ]) {
        final step = RouteStep(
          instruction: '$direction $modifier',
          direction: direction,
          modifier: modifier,
          distanceM: 100,
          location: const LatLng(45, 9),
          exitNumber: 3,
          roundaboutArmCount: 5,
        );
        for (final size in const [1.0, 24.0, 100.0, 512.0]) {
          final recorder = ui.PictureRecorder();
          expect(
            () => ManeuverSymbolPainter(
              visual: ManeuverVisual.fromStep(step),
              accent: colors.accent,
              muted: colors.textSecondary,
              surface: colors.surface2,
            ).paint(Canvas(recorder), Size(size, size)),
            returnsNormally,
            reason: '$direction/$modifier at $size',
          );
          recorder.endRecording();
        }
      }
    }
  });
}
