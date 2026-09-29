import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final session = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeRouteOverlaySession.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');

  test('native session preserves Flutter route-progress decisions', () {
    final kotlin = session.readAsStringSync();
    final dart = flutterMap.readAsStringSync();

    expect(dart, contains('_routeProgressPolyline({required bool completed})'));
    expect(dart, contains('_remainingRouteRuns()'));
    expect(dart, contains('final showRouteProgress = _isNavigating'));
    expect(kotlin, contains('fun submitRoute('));
    expect(kotlin, contains('fun updateProgress('));
    expect(kotlin, contains('fun updateRestrictions('));
    expect(kotlin, contains('clamped < this.progressMeters'));
    expect(kotlin, contains('cursorRestricted'));
  });

  test('native session fences alternative selection before progress', () {
    final kotlin = session.readAsStringSync();
    final dart = flutterMap.readAsStringSync();

    expect(dart, contains('final selectedAlt ='));
    expect(dart, contains('final alternativePolylines = _showAlternatives'));
    expect(dart, contains('if (i != _selectedAlt) _alternatives[i].polyline'));
    expect(kotlin, contains('fun submitAlternatives('));
    expect(kotlin, contains('fun selectAlternative('));
    expect(kotlin, contains('fun commitSelectedAlternative('));
    expect(
      kotlin,
      contains('route == null || alternatives.isNotEmpty()'),
    );
  });

  test(
      'private shell owns the reactive session without activating product data',
      () {
    final kotlin = session.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(kotlin, contains('StateFlow<NativeRouteOverlaySessionState>'));
    expect(shellSource, contains('NativeRouteOverlaySession'));
    expect(shellSource, contains('routeSession.state.collectAsState()'));
    expect(shellSource, contains('NativeMapLibreHost('));
    expect(shellSource, isNot(contains('NativeLocation')));
    expect(shellSource, isNot(contains('lastKnownLocation')));
  });
}
