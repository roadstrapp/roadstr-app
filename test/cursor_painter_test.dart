import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/widgets/cursor_painter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('CursorStyle preference', () {
    test('defaults safely and persists only driving styles', () {
      expect(CursorStyle.fromStorage(null), CursorStyle.arrow);
      expect(CursorStyle.fromStorage('not-a-style'), CursorStyle.arrow);
      expect(CursorStyle.fromStorage('formula1'), CursorStyle.formula1);
      expect(CursorStyle.fromStorage('city'), CursorStyle.city);

      // Mode-specific sprites can never be injected through the saved
      // driving preference.
      expect(CursorStyle.fromStorage('bicycle'), CursorStyle.arrow);
      expect(CursorStyle.fromStorage('ostrich'), CursorStyle.arrow);
      expect(CursorStyle.drivingStyles, hasLength(6));
      expect(CursorStyle.drivingStyles, isNot(contains(CursorStyle.bicycle)));
      expect(CursorStyle.drivingStyles, isNot(contains(CursorStyle.ostrich)));
    });

    test('uses dedicated sprites only during walking and cycling routes', () {
      expect(
        CursorStyle.resolve(
          isNavigating: true,
          transportMode: 'driving',
          storedDrivingStyle: 'electric',
        ),
        CursorStyle.electric,
      );
      expect(
        CursorStyle.resolve(
          isNavigating: true,
          transportMode: 'walking',
          storedDrivingStyle: 'electric',
        ),
        CursorStyle.ostrich,
      );
      expect(
        CursorStyle.resolve(
          isNavigating: true,
          transportMode: 'cycling',
          storedDrivingStyle: 'electric',
        ),
        CursorStyle.bicycle,
      );
      expect(
        CursorStyle.resolve(
          isNavigating: false,
          transportMode: 'cycling',
          storedDrivingStyle: 'electric',
        ),
        CursorStyle.electric,
      );
    });
  });

  test('all cursor assets are bundled and non-empty', () async {
    for (final style in CursorStyle.values) {
      final data = await rootBundle.load(style.assetPath);
      expect(data.lengthInBytes, greaterThan(0), reason: style.assetPath);
    }
  });

  testWidgets('all styles fit the standard 48 logical pixel marker',
      (tester) async {
    const sheetKey = Key('cursor-sheet');
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: RepaintBoundary(
              key: sheetKey,
              child: ColoredBox(
                color: const Color(0xFFF4F1FA),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: CursorStyle.values
                      .map(
                        (style) => SizedBox.square(
                          dimension: 64,
                          child: Center(
                            child: CursorWidget(style: style, size: 48),
                          ),
                        ),
                      )
                      .toList(growable: false),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    final context = tester.element(find.byKey(sheetKey));
    await tester.runAsync(() async {
      for (final style in CursorStyle.values.where((style) => style.isPng)) {
        await precacheImage(AssetImage(style.assetPath), context);
      }
    });
    await tester.pumpAndSettle();

    await expectLater(
      find.byKey(sheetKey),
      matchesGoldenFile('goldens/cursor_styles.png'),
    );
  });
}
