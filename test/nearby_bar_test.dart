import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/l10n/app_localizations.dart';
import 'package:roadstr/services/poi_search_service.dart';
import 'package:roadstr/services/routing_service.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/search/search_panel.dart';

void main() {
  setUpAll(() async {
    // Units reads the imperial flag from the settings box.
    Hive.init((await Directory.systemTemp.createTemp('roadstr')).path);
    await Hive.openBox('settings');
  });

  Widget host(Widget child, {Locale locale = const Locale('en')}) {
    final theme = AppTheme.build(AppThemeId.lightNostr);
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
      home: Scaffold(body: SingleChildScrollView(child: child)),
    );
  }

  testWidgets('offers every category and reports the tapped one',
      (tester) async {
    final tapped = <NearbyCategory>[];
    await tester.pumpWidget(host(NearbyBar(
      colors: AppTheme.build(AppThemeId.lightNostr).extension<RoadstrColors>()!,
      onSelect: tapped.add,
    )));
    await tester.pumpAndSettle();

    expect(find.text('Nearby'), findsOneWidget);
    expect(find.text('Fuel'), findsOneWidget);
    expect(find.text('Police'), findsOneWidget);

    await tester.tap(find.text('Fuel'));
    // The row scrolls: reach a category that starts off screen.
    await tester.dragUntilVisible(find.text('Police'), find.byType(NearbyBar),
        const Offset(-120, 0));
    await tester.tap(find.text('Police'));
    expect(tapped, [NearbyCategory.fuel, NearbyCategory.police]);
  });

  testWidgets('without a fix the categories are inert and say so',
      (tester) async {
    final tapped = <NearbyCategory>[];
    await tester.pumpWidget(host(NearbyBar(
      colors: AppTheme.build(AppThemeId.lightNostr).extension<RoadstrColors>()!,
      enabled: false,
      onSelect: tapped.add,
    )));
    await tester.pumpAndSettle();

    expect(find.text('Waiting for GPS'), findsOneWidget);
    await tester.tap(find.text('Fuel'));
    expect(tapped, isEmpty);
  });

  testWidgets('category names follow the app language', (tester) async {
    await tester.pumpWidget(host(
      NearbyBar(
        colors:
            AppTheme.build(AppThemeId.lightNostr).extension<RoadstrColors>()!,
        onSelect: (_) {},
      ),
      locale: const Locale('it'),
    ));
    await tester.pumpAndSettle();
    expect(find.text('Nei paraggi'), findsOneWidget);
    expect(find.text('Carburante'), findsOneWidget);
  });

  testWidgets('results show how far away each place is', (tester) async {
    await tester.pumpWidget(host(SearchResultsList(
      results: const [
        NominatimResult(
          displayName: 'Q8',
          shortName: 'Q8',
          position: LatLng(45.07, 7.69),
          cls: 'amenity',
          type: 'fuel',
          distanceM: 1240,
        ),
      ],
      isLoading: false,
      colors: AppTheme.build(AppThemeId.lightNostr).extension<RoadstrColors>()!,
      onSelect: (_) {},
      onSelectFavorite: (_) {},
    )));
    await tester.pumpAndSettle();
    expect(find.text('Q8'), findsOneWidget);
    expect(find.text('1.2 km'), findsOneWidget);
  });

  testWidgets('an empty nearby search says so instead of showing nothing',
      (tester) async {
    await tester.pumpWidget(host(SearchResultsList(
      results: const [],
      isLoading: false,
      colors: AppTheme.build(AppThemeId.lightNostr).extension<RoadstrColors>()!,
      emptyMessage: 'Nothing found within 5 km',
      onSelect: (_) {},
      onSelectFavorite: (_) {},
    )));
    await tester.pumpAndSettle();
    expect(find.text('Nothing found within 5 km'), findsOneWidget);
  });
}
