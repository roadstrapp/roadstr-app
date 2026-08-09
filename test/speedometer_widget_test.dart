import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:roadstr/theme/app_theme.dart';
import 'package:roadstr/widgets/speedometer_widget.dart';

void main() {
  setUpAll(() async {
    Hive.init((await Directory.systemTemp.createTemp('roadstr-speedo')).path);
    await Hive.openBox('settings');
  });

  tearDownAll(Hive.close);

  test('classic remains the persisted default', () {
    expect(SpeedometerStyle.fromStorage(null), SpeedometerStyle.classic);
    expect(SpeedometerStyle.fromStorage('unknown'), SpeedometerStyle.classic);
    expect(SpeedometerStyle.fromStorage('sport'), SpeedometerStyle.sport);
  });

  testWidgets('all five styles keep exactly the requested dimensions',
      (tester) async {
    const size = 110.0;
    final theme = AppTheme.build(AppThemeId.darkNostr);
    await tester.pumpWidget(MaterialApp(
      theme: theme,
      home: Material(
        child: Row(
          children: [
            for (final style in SpeedometerStyle.values)
              SpeedometerWidget(
                key: ValueKey(style),
                speedKmh: 88,
                speedLimit: 50,
                size: size,
                style: style,
              ),
          ],
        ),
      ),
    ));

    for (final style in SpeedometerStyle.values) {
      expect(
          tester.getSize(find.byKey(ValueKey(style))), const Size.square(size));
    }
  });

  testWidgets('the five styles remain visually distinct', (tester) async {
    const size = 110.0;
    final theme = AppTheme.build(AppThemeId.darkNostr);
    await tester.pumpWidget(MaterialApp(
      theme: theme,
      home: Material(
        color: theme.scaffoldBackgroundColor,
        child: Center(
          child: RepaintBoundary(
            child: ColoredBox(
              color: theme.scaffoldBackgroundColor,
              child: SizedBox(
                width: 370,
                child: Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    for (final style in SpeedometerStyle.values)
                      SpeedometerWidget(
                        speedKmh: 88,
                        size: size,
                        style: style,
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    ));

    await expectLater(
      find.byType(RepaintBoundary).last,
      matchesGoldenFile('goldens/speedometer_styles.png'),
    );
  });
}
