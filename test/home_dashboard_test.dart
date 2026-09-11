import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/l10n/app_localizations.dart';
import 'package:roadstr/models/favorite_place.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/design/roadstr_glass.dart';
import 'package:roadstr/widgets/home/home_dashboard.dart';

void main() {
  Widget host({
    required VoidCallback onNavigate,
    required VoidCallback onLocate,
  }) {
    final theme = AppTheme.build(AppThemeId.lightNostr);
    return MaterialApp(
      theme: theme,
      locale: const Locale('it'),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Align(
          alignment: Alignment.bottomCenter,
          child: HomeDashboard(
            colors: theme.extension<RoadstrColors>()!,
            bottomInset: 0,
            favorites: const [
              FavoritePlace(
                label: 'Casa',
                address: 'Via Roma',
                position: LatLng(45, 9),
              ),
            ],
            onNavigate: onNavigate,
            onLocate: onLocate,
            onParking: () {},
            onActivity: () {},
            onEvents: () {},
            onFavoriteTap: (_) {},
          ),
        ),
      ),
    );
  }

  testWidgets('starts compact and reveals real shortcuts on demand',
      (tester) async {
    var navigations = 0;
    var locations = 0;
    await tester.pumpWidget(host(
      onNavigate: () => navigations++,
      onLocate: () => locations++,
    ));
    await tester.pumpAndSettle();

    expect(find.text('Pronto a partire?'), findsOneWidget);
    expect(find.text('Casa'), findsNothing);
    expect(find.text('La mia posizione'), findsNothing);

    await tester.tap(find.byType(RoadstrBrandMark));
    await tester.pumpAndSettle();

    expect(find.text('Casa'), findsOneWidget);
    expect(find.text('La mia posizione'), findsOneWidget);

    await tester.tap(find.text('La mia posizione'));
    expect(locations, 1);

    await tester.tap(find.text('Naviga').last);
    expect(navigations, 1);
  });

  testWidgets('remains usable on a compact phone', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(320, 568);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(host(onNavigate: () {}, onLocate: () {}));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(RoadstrBrandMark));
    await tester.pumpAndSettle();

    expect(find.text('Parcheggio'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
