import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');
  final flutterMarkers = File('lib/widgets/map/map_markers.dart');
  final flutterRoadEvents = File('lib/models/road_event.dart');
  final session = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapPointOverlaySession.kt',
  );
  final overlay = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapPointOverlayView.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native point catalogue preserves Flutter sizes gates and symbols', () {
    final mapSource = flutterMap.readAsStringSync();
    final markerSource = flutterMarkers.readAsStringSync();
    final roadSource = flutterRoadEvents.readAsStringSync();
    final native = session.readAsStringSync();

    expect(mapSource, contains('(_camState?.zoom ?? 17) >= 11'));
    expect(mapSource, contains('size: const Size(36, 36)'));
    expect(mapSource, contains('size: const Size(30, 30)'));
    expect(mapSource, contains('size: const Size(38, 38)'));
    expect(mapSource, contains('(_camState?.zoom ?? 17) >= 15'));
    expect(mapSource, contains('size: const Size(24, 24)'));
    expect(mapSource, contains('size: const Size(22, 22)'));
    expect(mapSource, contains('(_camState?.zoom ?? 17) >= 16'));
    expect(mapSource, contains('size: const Size(20, 20)'));
    expect(markerSource, contains("emoji: '🚦'"));
    expect(markerSource, contains("emoji: '🚸'"));
    expect(markerSource, contains("emoji: '〰️'"));
    expect(roadSource, contains("RoadCategory.police => '👮'"));
    expect(native, contains('RoadPolice("👮", 0xFF2563EB, 36.0, 11.0'));
    expect(native, contains('OsmSpeedCamera("📷", 0xFF7C3AED, 30.0'));
    expect(native, contains('TrafficLight("🚦", 0xFF70D69B, 24.0, 15.0'));
    expect(native, contains('Crosswalk("🚸", 0xFFFFB347, 20.0, 16.0'));
  });

  test('private host owns a bounded dormant projected point overlay', () {
    final sessionSource = session.readAsStringSync();
    final overlaySource = overlay.readAsStringSync();
    final hostSource = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(sessionSource, contains('MAX_MARKERS = 4_096'));
    expect(sessionSource, contains('marker ids must be unique'));
    expect(sessionSource, contains('revision <= _state.value.revision'));
    expect(overlaySource, contains('projection.toScreenLocation'));
    expect(overlaySource, contains('cameraPosition.zoom'));
    expect(overlaySource, contains('IMPORTANT_FOR_ACCESSIBILITY_NO'));
    expect(hostSource, contains('pointOverlay.refreshProjection()'));
    expect(hostSource, contains('pointOverlay.attach(readyMap)'));
    expect(shellSource, contains('NativeMapPointOverlaySession'));
    expect(shellSource, contains('pointOverlay = pointOverlayState'));
    expect(shellSource, isNot(contains('cachedCameras')));
    expect(shellSource, isNot(contains('RoadEvent')));
  });
}
