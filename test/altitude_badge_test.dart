import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/map/map_chrome.dart';

void main() {
  setUpAll(() async {
    Hive.init((await Directory.systemTemp.createTemp('roadstr-altitude')).path);
    await Hive.openBox('settings');
  });
  tearDownAll(Hive.close);

  final colors = AppTheme.build(AppThemeId.darkNostr).extension<RoadstrColors>()!;

  Widget host(double altitudeM) => MaterialApp(
        home: Scaffold(
          body: Align(
            alignment: Alignment.centerRight,
            child: AltitudeBadge(altitudeM: altitudeM, colors: colors),
          ),
        ),
      );

  testWidgets('a four-digit reading does not clip or overflow',
      (tester) async {
    // 1234 m is an ordinary mountain-pass altitude, not an edge case — the
    // badge has no maxWidth of its own, so it must simply grow to fit.
    await tester.pumpWidget(host(1234));
    expect(tester.takeException(), isNull);
    expect(find.text('1234 m'), findsOneWidget);
  });

  testWidgets('negative altitude (below sea level) still renders',
      (tester) async {
    await tester.pumpWidget(host(-11));
    expect(tester.takeException(), isNull);
    expect(find.text('-11 m'), findsOneWidget);
  });
}
