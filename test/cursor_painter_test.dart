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
      expect(CursorStyle.fromStorage('classic500'), CursorStyle.classic500);

      // Mode-specific sprites can never be injected through the saved
      // driving preference.
      expect(CursorStyle.fromStorage('bicycle'), CursorStyle.arrow);
      expect(CursorStyle.fromStorage('ostrich'), CursorStyle.arrow);
      expect(CursorStyle.drivingStyles, hasLength(7));
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

  group('CursorColor preference', () {
    test('defaults safely and exposes the seven rainbow colours', () {
      expect(CursorColor.fromStorage(null), CursorColor.violet);
      expect(CursorColor.fromStorage('not-a-colour'), CursorColor.violet);
      expect(CursorColor.fromStorage('red'), CursorColor.red);
      expect(CursorColor.values, hasLength(7));
      expect(CursorColor.values.first, CursorColor.violet);
      expect(CursorColor.values.last, CursorColor.red);
      expect(CursorColor.violet.colorFilter, isNull);
      expect(CursorColor.red.colorFilter, isNotNull);
    });
  });

  test('ostrich animation supplies two complete 30 fps frame sequences',
      () async {
    expect(OstrichAnimationAssets.frameCount, 150);
    expect(
      OstrichAnimationAssets.framePath(running: true, frame: 0),
      'assets/cursors/ostrich_run/frame-000.png',
    );
    expect(
      OstrichAnimationAssets.framePath(running: false, frame: 149),
      'assets/cursors/ostrich_idle/frame-149.png',
    );
    expect(
      OstrichAnimationAssets.framePaths(running: true),
      hasLength(150),
    );

    for (final path in [
      OstrichAnimationAssets.framePath(running: true, frame: 0),
      OstrichAnimationAssets.framePath(running: true, frame: 149),
      OstrichAnimationAssets.framePath(running: false, frame: 0),
      OstrichAnimationAssets.framePath(running: false, frame: 149),
    ]) {
      final data = await rootBundle.load(path);
      expect(data.lengthInBytes, greaterThan(0), reason: path);
    }
  });

  test('all cursor assets are bundled and non-empty', () async {
    for (final style in CursorStyle.values) {
      final data = await rootBundle.load(style.assetPath);
      expect(data.lengthInBytes, greaterThan(0), reason: style.assetPath);
    }
  });

  testWidgets('walking ostrich switches between run and phone sequences',
      (tester) async {
    Widget buildOstrich({required bool isMoving}) => MaterialApp(
          home: Scaffold(
            body: CursorWidget(
              style: CursorStyle.ostrich,
              animateOstrich: true,
              ostrichIsMoving: isMoving,
              ostrichSpeedKmh: 4.8,
            ),
          ),
        );

    await tester.pumpWidget(buildOstrich(isMoving: true));
    var image = tester.widget<Image>(find.byType(Image));
    expect((image.image as AssetImage).assetName,
        'assets/cursors/ostrich_run/frame-000.png');

    // Do not use pumpAndSettle: the cursor deliberately repeats forever.
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(buildOstrich(isMoving: false));
    image = tester.widget<Image>(find.byType(Image));
    expect((image.image as AssetImage).assetName,
        'assets/cursors/ostrich_idle/frame-000.png');
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.takeException(), isNull);
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

  testWidgets('all rainbow colours preserve the vehicle icon', (tester) async {
    const sheetKey = Key('cursor-colour-sheet');
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
                  children: CursorColor.values
                      .map(
                        (color) => SizedBox.square(
                          dimension: 64,
                          child: Center(
                            child: CursorWidget(
                              style: CursorStyle.classic500,
                              cursorColor: color,
                              size: 48,
                            ),
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
      await precacheImage(
        const AssetImage('assets/cursors/classic500.png'),
        context,
      );
    });
    await tester.pumpAndSettle();

    await expectLater(
      find.byKey(sheetKey),
      matchesGoldenFile('goldens/cursor_colours.png'),
    );
  });
}
