import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');
  final flutterModes = File('lib/models/transit_mode.dart');
  final overlay = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeTransitOverlay.kt',
  );
  final renderer = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapTransitRenderer.kt',
  );
  final session = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeTransitOverlaySession.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native transit styling retains the Flutter per-leg oracle', () {
    final dart = flutterMap.readAsStringSync();
    final nativeOverlay = overlay.readAsStringSync();
    final nativeRenderer = renderer.readAsStringSync();

    expect(dart, contains('leg.routeColor ?? c.accent'));
    expect(dart, contains('c.textSecondary.withValues(alpha: 0.7)'));
    expect(dart, contains('routeWidthPx(leg.mode.isTransit ? 7 : 4)'));
    expect(nativeOverlay, contains('TRANSIT_LOGICAL_WIDTH = 7.0'));
    expect(nativeOverlay, contains('STREET_LOGICAL_WIDTH = 4.0'));
    expect(nativeRenderer, contains('const val STREET_ALPHA = 0.7'));
    expect(nativeRenderer,
        contains('Expression.toColor(Expression.get(COLOR_PROPERTY))'));
    expect(nativeRenderer, contains('Property.LINE_CAP_ROUND'));
  });

  test(
      'native transit mode catalogue stays aligned with worldwide Flutter modes',
      () {
    final dart = flutterModes.readAsStringSync();
    final native = overlay.readAsStringSync();

    for (final wireName in <String>[
      'WALK',
      'BIKE',
      'CAR',
      'TRAM',
      'SUBWAY',
      'METRO',
      'SUBURBAN',
      'REGIONAL_RAIL',
      'REGIONAL_FAST_RAIL',
      'LONG_DISTANCE',
      'HIGHSPEED_RAIL',
      'NIGHT_RAIL',
      'RAIL',
      'BUS',
      'COACH',
      'FERRY',
      'AIRPLANE',
      'FUNICULAR',
      'AERIAL_LIFT',
      'ODM',
      'OTHER',
    ]) {
      expect(dart, contains("('$wireName')"));
      expect(native, contains('("$wireName",'));
    }
    expect(dart,
        contains('TransitMode.walk || TransitMode.bike || TransitMode.car'));
    expect(native, contains('Walk("WALK", false)'));
    expect(native, contains('Other("OTHER", true)'));
  });

  test('private shell owns a bounded dormant transit renderer', () {
    final nativeSession = session.readAsStringSync();
    final nativeRenderer = renderer.readAsStringSync();
    final hostSource = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(nativeSession, contains('revision <= this.revision'));
    expect(nativeSession, contains('MAX_TRANSIT_POINTS'));
    expect(nativeRenderer, contains('roadstr-transit-source'));
    expect(nativeRenderer, contains('roadstr-transit-legs'));
    expect(hostSource, contains('transitRenderer.attach(loadedStyle)'));
    expect(
      hostSource.indexOf('transitRenderer.attach(loadedStyle)'),
      lessThan(hostSource.indexOf('routeRenderer.attach(loadedStyle)')),
    );
    expect(shellSource, contains('NativeTransitOverlaySession'));
    expect(shellSource, contains('transitOverlay = transitState'));
    expect(shellSource, isNot(contains('TransitService')));
  });
}
