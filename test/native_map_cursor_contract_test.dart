import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');
  final flutterCursor = File('lib/widgets/cursor_painter.dart');
  final session = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapCursorSession.kt',
  );
  final overlay = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapCursorOverlayView.kt',
  );
  final destinationOverlay = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapDestinationOverlayView.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native cursor preserves the Flutter MapLibre geometry oracle', () {
    final dart = flutterMap.readAsStringSync();
    final cursorDart = flutterCursor.readAsStringSync();
    final kotlin = session.readAsStringSync();
    final view = overlay.readAsStringSync();

    expect(dart, contains('static const _kCursorScale = 1.4'));
    expect(dart, contains('width: 48'));
    expect(dart, contains('height: 76'));
    expect(dart, contains('canvas.translate(24, 36 + t * 14)'));
    expect(dart, contains('canvas.scale(1 - t * 0.1, 0.5 + t * 0.3)'));
    expect(cursorDart, contains('violet(Color(0xFF8B3DFF), 0)'));
    expect(kotlin, contains('const val WIDTH_DP = 48.0'));
    expect(kotlin, contains('const val HEIGHT_DP = 76.0'));
    expect(kotlin, contains('const val DISPLAY_SCALE = 1.4'));
    expect(kotlin, contains('DEFAULT_COLOR_ARGB = 0xFF8B3DFF'));
    expect(view, contains('moveTo(24f, 5.5f)'));
    expect(view, contains('lineTo(36f, 33f)'));
  });

  test('private host reprojects a dormant cursor without owning location', () {
    final hostSource = host.readAsStringSync();
    final overlaySource = overlay.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(hostSource, contains('FrameLayout(context)'));
    expect(hostSource, contains('addView(mapView, matchParent)'));
    expect(hostSource, contains('addView('));
    expect(hostSource, contains('cursorOverlay'));
    expect(hostSource, contains('addOnCameraMoveListener(cameraMoveListener)'));
    expect(overlaySource, contains('projection.toScreenLocation'));
    expect(overlaySource, contains('IMPORTANT_FOR_ACCESSIBILITY_NO'));
    expect(shellSource, contains('NativeMapCursorSession'));
    // The vehicle is drawn where the camera frame says, not only at each fix.
    expect(shellSource, contains('cursorSnapshot = remember(cursorState, cameraState.displaySequence)'));
    expect(shellSource, isNot(contains('NativeLocation')));
    expect(shellSource, isNot(contains('lastKnownLocation')));
  });

  test('native map caps rendering and anchors a themed destination pin', () {
    final hostSource = host.readAsStringSync();
    final destinationSource = destinationOverlay.readAsStringSync();

    expect(hostSource, contains('MAXIMUM_RENDER_FPS = 30'));
    expect(hostSource, contains('RenderingRefreshMode.WHEN_DIRTY'));
    expect(hostSource, contains('destinationOverlay.refreshProjection()'));
    expect(destinationSource, contains('NativeMapDestinationPinSnapshot'));
    expect(destinationSource, contains('projection.toScreenLocation'));
    expect(destinationSource, contains('value.colorArgb'));
  });
}
