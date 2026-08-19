import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:roadstr/l10n/app_localizations.dart';
import 'package:roadstr/models/transit_itinerary.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/route/route_panels.dart';
import 'package:roadstr/widgets/transit_itinerary_widget.dart';

TransitItinerary _berlinItinerary() {
  final json = jsonDecode(
          File('test/fixtures/transit_plan_berlin.json').readAsStringSync())
      as Map<String, dynamic>;
  return TransitItinerary.fromJson(json['itineraries'][0])!;
}

Widget _host(Widget child, {Locale locale = const Locale('en')}) => MaterialApp(
      locale: locale,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        // Without this the framework warns that the locale is unsupported by
        // every delegate, and the warning surfaces as a test exception.
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: child),
    );

void main() {
  late Directory tempDir;

  setUpAll(() async {
    // Units.fmtDist reads the metric/imperial preference from Hive at call
    // time, so the box has to exist before the card renders a distance.
    tempDir = await Directory.systemTemp.createTemp('roadstr_transit_test');
    Hive.init(tempDir.path);
    await Hive.openBox('settings');
  });

  tearDownAll(() async {
    await Hive.close();
    await tempDir.delete(recursive: true);
  });

  testWidgets('shows the line, the duration and a direct-journey label',
      (tester) async {
    await tester.pumpWidget(_host(TransitItineraryCard(
      itinerary: _berlinItinerary(),
      colors: AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!,
    )));

    // The line badge a passenger looks for on the platform.
    expect(find.text('S3'), findsOneWidget);
    // 780 s in the fixture.
    expect(find.text('13m'), findsOneWidget);
    // Zero transfers must read as "Direct", not as "0 changes".
    expect(find.text('Direct'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('warns that times come from a timetable when not live',
      (tester) async {
    await tester.pumpWidget(_host(TransitItineraryCard(
      itinerary: _berlinItinerary(),
      colors: AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!,
    )));
    // The fixture's legs are all realTime:false, so the caveat must appear —
    // presenting timetable data as live is what makes a missed connection
    // feel like the app lied.
    expect(find.text('Times come from published timetables'), findsOneWidget);
  });

  testWidgets('renders in a non-Latin locale without overflowing',
      (tester) async {
    await tester.pumpWidget(_host(
      SizedBox(
        width: 320,
        child: TransitItineraryCard(
          itinerary: _berlinItinerary(),
          colors: AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!,
        ),
      ),
      locale: const Locale('ja'),
    ));
    expect(find.text('直通'), findsOneWidget);
    // An overflow paints an error stripe and throws in tests.
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty state offers a retry only when the request failed',
      (tester) async {
    final colors = AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!;

    await tester.pumpWidget(
        _host(TransitEmptyState(colors: colors, failed: false)));
    expect(find.text('No public transport data for this area'), findsOneWidget);
    expect(find.byType(TextButton), findsNothing,
        reason: 'no amount of retrying creates a timetable that does not exist');

    await tester.pumpWidget(_host(
        TransitEmptyState(colors: colors, failed: true, onRetry: () {})));
    expect(find.text('Could not load public transport'), findsOneWidget);
    expect(find.byType(TextButton), findsOneWidget);
  });

  group('on-foot sub-choice', () {
    Widget planner(String mode, void Function(String) onMode) => _host(
          RoutePlannerBar(
            fromCtrl: TextEditingController(),
            stopCtrls: [TextEditingController()],
            activeField: 0,
            hasGps: true,
            canCalculate: true,
            isSearching: false,
            transportMode: mode,
            colors:
                AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!,
            onFromTap: () {},
            onMyLocation: () {},
            onClose: () {},
            onCalculate: () {},
            onFromChanged: (_) {},
            onModeChanged: onMode,
            onStopTap: (_) {},
            onStopChanged: (_, __) {},
            onRemoveStop: (_) {},
            onReorderStops: (_, __) {},
          ),
        );

    testWidgets('is hidden while driving', (tester) async {
      await tester.pumpWidget(planner('driving', (_) {}));
      expect(find.text('Transit'), findsNothing);
    });

    testWidgets('opens under the walking chip and switches to transit',
        (tester) async {
      final picked = <String>[];
      await tester.pumpWidget(planner('walking', picked.add));

      // The sub-choice appears only once the journey is on foot.
      expect(find.text('Transit'), findsOneWidget);

      await tester.tap(find.text('Transit'));
      expect(picked, ['transit'],
          reason: 'tapping the sub-option must select the transit mode');
    });

    testWidgets('keeps the walking chip lit while transit is selected',
        (tester) async {
      await tester.pumpWidget(planner('transit', (_) {}));
      // A transit journey begins and ends on foot, so the parent stays
      // selected — and the sub-choice must remain reachable to go back.
      expect(find.text('Transit'), findsOneWidget);
      expect(find.text('On foot'), findsNWidgets(2));
      expect(tester.takeException(), isNull);
    });
  });
}
