import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/l10n/app_localizations.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/nav/nav_hud.dart';

void main() {
  setUpAll(() async {
    Hive.init((await Directory.systemTemp.createTemp('roadstr-nav-hud')).path);
    final box = await Hive.openBox('settings');
    await box.put('imperialUnits', true);
  });

  tearDownAll(Hive.close);

  RouteStep step(
    String instruction,
    String direction, {
    String modifier = '',
    double distanceM = 100,
  }) =>
      RouteStep(
        instruction: instruction,
        direction: direction,
        modifier: modifier,
        distanceM: distanceM,
        location: const LatLng(45, 9),
      );

  Widget host({
    required RouteStep current,
    RouteStep? next,
    required double nextDistanceM,
    required Locale locale,
  }) {
    final theme = AppTheme.build(AppThemeId.lightNostr);
    final route = RouteResult(
      polyline: const [LatLng(45, 9), LatLng(45.1, 9.1)],
      steps: [current, if (next != null) next],
      totalDistanceM: 20000,
      totalDurationS: 1200,
    );
    return MaterialApp(
      theme: theme,
      locale: locale,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Align(
          alignment: Alignment.topCenter,
          child: NavInstruction(
            step: current,
            nextStep: next,
            distToNextStepM: nextDistanceM,
            route: route,
            stepIdx: 0,
            colors: theme.extension<RoadstrColors>()!,
            distToNextM: 17700,
          ),
        ),
      ),
    );
  }

  testWidgets('a distant arrival is future tense, not already arrived',
      (tester) async {
    await tester.pumpWidget(host(
      current: step(
        'Sei arrivato a destinazione',
        'arrive',
        modifier: 'right',
      ),
      nextDistanceM: 0,
      locale: const Locale('it'),
    ));

    expect(
      find.text('Arriverai a destinazione sulla destra'),
      findsOneWidget,
    );
    expect(find.text('Sei arrivato a destinazione'), findsNothing);
    expect(find.text('11 mi'), findsOneWidget);
  });

  testWidgets('next-step preview shows distance to it, not its segment length',
      (tester) async {
    await tester.pumpWidget(host(
      current: step(
        'Take exit 199',
        'off ramp',
        modifier: 'right',
        distanceM: 480,
      ),
      next: step(
        'Merge onto I-95',
        'merge',
        modifier: 'left',
        distanceM: 80467,
      ),
      nextDistanceM: 480,
      locale: const Locale('en'),
    ));

    expect(find.text('0.3 mi'), findsOneWidget);
    expect(find.text('50 mi'), findsNothing);
  });
}
