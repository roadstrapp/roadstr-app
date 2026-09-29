import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');
  final renderer = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapRouteRenderer.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native route layer constants retain the Flutter MapLibre oracle', () {
    final dart = flutterMap.readAsStringSync();
    final kotlin = renderer.readAsStringSync();

    expect(dart, contains('const _kZtlRed = Color(0xFFE53935)'));
    expect(dart, contains('width: routeWidthPx(18)'));
    expect(dart, contains('width: routeWidthPx(9)'));
    expect(dart, contains('.withValues(alpha: 0.28)'));
    expect(kotlin, contains('const val ZTL_RED_ARGB = 0xFFE53935'));
    expect(kotlin, contains('const val COMPLETED_GREY_ARGB = 0xFF9E9E9E'));
    expect(kotlin, contains('const val HALO_ALPHA = 0.28'));
  });

  test('private shell installs empty route sources without GPS or user data',
      () {
    final rendererSource = renderer.readAsStringSync();
    final hostSource = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(rendererSource, contains('roadstr-route-active-source'));
    expect(rendererSource, contains('roadstr-route-completed-source'));
    expect(rendererSource, contains('roadstr-route-active-halo'));
    expect(rendererSource, contains('roadstr-route-active-core'));
    expect(hostSource, contains('styleGeneration.accepts(generation)'));
    expect(hostSource, contains('routeRenderer.attach(loadedStyle)'));
    expect(shellSource, contains('NativeRouteOverlaySession'));
    expect(shellSource, contains('routeState.snapshot'));
    expect(shellSource, isNot(contains('NativeLocation')));
    expect(shellSource, isNot(contains('lastKnownLocation')));
  });
}
