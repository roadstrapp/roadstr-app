import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/l10n/app_localizations.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/route/route_panels.dart';

void main() {
  final colors =
      AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!;

  Widget host(Widget child) => MaterialApp(
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: child),
      );

  Widget planner({
    required int stops,
    VoidCallback? onAddStop,
    void Function(int)? onRemoveStop,
    void Function(int, int)? onReorder,
  }) =>
      host(RoutePlannerBar(
        fromCtrl: TextEditingController(),
        stopCtrls: [
          for (var i = 0; i < stops; i++) TextEditingController(),
        ],
        activeField: 0,
        hasGps: true,
        canCalculate: true,
        isSearching: false,
        transportMode: 'driving',
        colors: colors,
        onFromTap: () {},
        onMyLocation: () {},
        onClose: () {},
        onCalculate: () {},
        onFromChanged: (_) {},
        onModeChanged: (_) {},
        onStopTap: (_) {},
        onStopChanged: (_, __) {},
        onAddStop: onAddStop,
        onRemoveStop: onRemoveStop ?? (_) {},
        onReorderStops: onReorder ?? (_, __) {},
      ));

  group('a plain A-to-B journey', () {
    testWidgets('shows one destination field and no clutter around it',
        (tester) async {
      await tester.pumpWidget(planner(stops: 1));

      // A single destination cannot be dragged anywhere or removed — there
      // would be no journey left — so neither control should be offered.
      expect(find.byIcon(Icons.drag_handle_rounded), findsNothing);
      expect(find.byIcon(Icons.close_rounded), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('offers to add a stop', (tester) async {
      var added = 0;
      await tester.pumpWidget(planner(stops: 1, onAddStop: () => added++));
      await tester.tap(find.text('Add stop'));
      expect(added, 1);
    });
  });

  group('a journey with stops', () {
    testWidgets('gives every row a grip and a remove button', (tester) async {
      await tester.pumpWidget(planner(stops: 3));
      // The grip is what tells the user the order can be changed at all; the
      // feature is invisible without it.
      expect(find.byIcon(Icons.drag_handle_rounded), findsNWidgets(3));
      expect(find.byIcon(Icons.close_rounded), findsNWidgets(3));
    });

    testWidgets('removing a row reports its index', (tester) async {
      final removed = <int>[];
      await tester.pumpWidget(planner(stops: 3, onRemoveStop: removed.add));
      await tester.tap(find.byIcon(Icons.close_rounded).at(1));
      expect(removed, [1]);
    });

    testWidgets('renders the maximum five rows without overflowing',
        (tester) async {
      await tester.pumpWidget(planner(stops: 5));
      expect(find.byIcon(Icons.drag_handle_rounded), findsNWidgets(5));
      // An overflow paints an error stripe and throws in tests, which is the
      // failure mode a fixed-height list is most likely to hit.
      expect(tester.takeException(), isNull);
    });

    testWidgets('hides the add button once the ceiling is reached',
        (tester) async {
      await tester.pumpWidget(planner(stops: 5));
      expect(find.text('Add stop'), findsNothing);
    });
  });
}
