import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final appGradle = File('android/app/build.gradle.kts');
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native host pins the Flutter renderer MapLibre SDK exactly', () {
    final build = appGradle.readAsStringSync();

    expect(build, contains('org.maplibre.gl:android-sdk-opengl'));
    expect(build, contains('strictly("13.5.2")'));
    expect(build, contains('lifecycle-runtime-compose:2.10.0'));
  });

  test('private shell owns a lifecycle-safe raster host without location', () {
    final source = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(
        source, contains('MapLibre.getInstance(context.applicationContext)'));
    expect(source, contains('MAPLIBRE_VERSION = "13.5.2"'));
    expect(source, contains('MapView(context, options)'));
    expect(source, contains('.textureMode(true)'));
    expect(source, contains('Style.Builder().fromJson(requestedJson)'));
    expect(source, contains('styleGeneration.accepts(generation)'));
    expect(source, contains('NativeMapLifecycleEvent.Destroy'));
    expect(source, isNot(contains('LocationComponent')));
    expect(shellSource, contains('NativeMapLibreHost('));
    expect(shellSource, isNot(contains('MainActivity')));
  });
}
