import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');
  final session = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapCameraSession.kt',
  );
  final renderer = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapCameraRenderer.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native camera session preserves the Flutter follow policy', () {
    final dart = flutterMap.readAsStringSync();
    final kotlin = session.readAsStringSync();

    expect(
      dart,
      contains('_followTickInterval = Duration(milliseconds: 33)'),
    );
    expect(dart, contains('_deadReckoningCapMs = 3000'));
    expect(dart, contains('event.reason == CameraChangeReason.apiGesture'));
    expect(dart, contains('pitch: _isNavigating ? 55.0 : 40.0'));
    expect(kotlin, contains('FOLLOW_FRAME_MILLIS = 50L'));
    expect(kotlin, contains('DEAD_RECKONING_CAP_MILLIS = 3_000L'));
    expect(kotlin, contains('FREE_DRIVE_PITCH = 40.0'));
    expect(kotlin, contains('NAVIGATION_PITCH = 55.0'));
    expect(kotlin, contains('fun onUserGesture()'));
  });

  test('private shell renders camera commands without owning location', () {
    final rendererSource = renderer.readAsStringSync();
    final hostSource = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(rendererSource, contains('CameraUpdateFactory.newCameraPosition'));
    expect(rendererSource, contains('liveMap.moveCamera(update)'));
    expect(rendererSource, contains('liveMap.easeCamera('));
    expect(hostSource, contains('REASON_API_GESTURE'));
    expect(hostSource, contains('cameraRenderer.attach(readyMap)'));
    expect(hostSource, contains('cameraRenderer.update(command)'));
    expect(shellSource, contains('NativeMapCameraSession'));
    expect(shellSource, contains('cameraState.frameActive'));
    expect(shellSource, contains('SystemClock.elapsedRealtime()'));
    expect(shellSource, isNot(contains('NativeLocation')));
    expect(shellSource, isNot(contains('lastKnownLocation')));
  });
}
