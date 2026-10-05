import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final mainSource = File('lib/main.dart');
  final settingsSource = File('lib/screens/settings_screen.dart');
  final legacySource = File('lib/screens/map_screen.dart');
  final modernSource = File('lib/screens/maplibre_map_screen.dart');
  final compatibility = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapEngineCompatibility.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('persisted map-engine selection retains the Flutter wire values', () {
    final main = mainSource.readAsStringSync();
    final settings = settingsSource.readAsStringSync();
    final native = compatibility.readAsStringSync();

    expect(main, contains("defaultValue: 'maplibre'"));
    expect(main, contains("==\n                    'maplibre'"));
    expect(settings, contains("v ? 'maplibre' : 'osm'"));
    expect(native, contains('MapLibre("maplibre")'));
    expect(native, contains('LegacyRaster("osm")'));
    expect(native, contains('?: MapLibre'));
  });

  test('native profiles preserve both Flutter camera envelopes', () {
    final legacy = legacySource.readAsStringSync();
    final modern = modernSource.readAsStringSync();
    final native = compatibility.readAsStringSync();

    for (final oracle in [
      'initialZoom: 6.0',
      'minZoom: 2.0',
      'maxZoom: 19.0',
    ]) {
      expect(legacy, contains(oracle));
    }
    expect(modern, contains('initZoom: 17'));
    expect(modern, contains('initPitch: 40'));
    for (final value in [
      'initialZoom = 6.0',
      'minimumZoom = 2.0',
      'maximumZoom = 19.0',
      'initialPitchDegrees = 0.0',
      'initialZoom = 17.0',
      'initialPitchDegrees = 40.0',
    ]) {
      expect(native, contains(value));
    }
  });

  test('single native host applies mode bounds and custom tile input', () {
    final source = host.readAsStringSync();

    expect(source, contains('mapEngine: NativeMapEngine'));
    expect(source, contains('tileUrl: String'));
    expect(source, contains('.minZoomPreference(engineProfile.minimumZoom)'));
    expect(source, contains('.maxZoomPreference(engineProfile.maximumZoom)'));
    expect(source,
        contains('setMaxPitchPreference(engineProfile.maximumPitchDegrees)'));
    expect(source,
        contains('setTiltGesturesEnabled(engineProfile.tiltGesturesEnabled)'));
    expect(source, contains('NativeMapCameraRenderer(engineProfile)'));
    expect(source, isNot(contains('FlutterMap')));
  });

  test('private shell defaults safely without opening persisted settings', () {
    final source = shell.readAsStringSync();

    expect(source, contains('mapEngine = when (settingsState.values.mapEngine)'));
    expect(source, contains('NativeMapEngine.MapLibre'));
    // A custom source is used only if the policy accepts it; otherwise the default.
    expect(source, contains('NativeTileUrlPolicy.requireAccepted(settingsState.values.mapTileUrl)'));
    expect(source, contains('getOrDefault(NativeMapStyle.DEFAULT_TILE_URL)'));
    expect(source, isNot(contains('Hive')));
    expect(source, isNot(contains('FlutterSecureStorage')));
  });
}
