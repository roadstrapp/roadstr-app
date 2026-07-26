import 'dart:io';

import 'package:flutter/material.dart';
import 'package:hive/hive.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/l10n/app_localizations.dart';
import 'package:roadstr/models/road_event.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/sheets/road_event_sheets.dart';

/// Regression test for the red screen ("_dependents.isEmpty is not true")
/// raised when the owner updated the speed limit of an existing speed-camera
/// report.
void main() {
  setUpAll(() async {
    Hive.init((await Directory.systemTemp.createTemp('roadstr')).path);
    await Hive.openBox('settings');
  });

  testWidgets('editing the speed limit of an existing report does not crash',
      (tester) async {
    final event = RoadEvent(
      id: 'a' * 64,
      pubkey: 'b' * 64,
      category: RoadCategory.speedCamera,
      position: const LatLng(44.4, 12.2),
      createdAt: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      expiresAt: null,
      comment: '',
      speedLimit: 50,
    );

    final theme = AppTheme.build(AppThemeId.lightNostr);
    await tester.pumpWidget(MaterialApp(
      theme: theme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: ElevatedButton(
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                backgroundColor: Colors.transparent,
                builder: (_) => RoadEventDetailSheet(
                  event: event,
                  colors: theme.extension<RoadstrColors>()!,
                  isLoggedIn: true,
                  isOwner: true,
                  onEditSpeedLimit: (limit, requestId) async {
                    event.speedLimit = limit;
                  },
                ),
              ),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    ));

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_rounded));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '70');
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();

    expect(event.speedLimit, 70);
    expect(tester.takeException(), isNull);

    // The sheet fires relay lookups on open (each relay in turn, 6 s apiece);
    // let every timeout elapse so the test does not fail on pending timers.
    await tester.pump(const Duration(seconds: 90));
    await tester.pumpAndSettle();
  });
}
