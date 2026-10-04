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
  final interaction = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapInteraction.kt',
  );
  final routeSession = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeRouteOverlaySession.kt',
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
    expect(shellSource, contains('pointOverlaySession.replace('));
  });

  test('native alternative tap keeps Flutter strict 60 metre oracle', () {
    final mapSource = flutterMap.readAsStringSync();
    final interactionSource = interaction.readAsStringSync();
    final routeSource = routeSession.readAsStringSync();

    expect(mapSource, contains('int _nearestAlternative(LatLng tap)'));
    expect(mapSource, contains('const thresholdM = 60.0'));
    expect(mapSource, contains('var bestD = thresholdM'));
    expect(mapSource, contains('if (d < bestD)'));
    expect(
      interactionSource,
      contains('ALTERNATIVE_TAP_THRESHOLD_METERS = 60.0'),
    );
    expect(interactionSource, contains('if (meters < nearestMeters)'));
    expect(routeSource, contains('fun selectAlternativeAt('));
    expect(routeSource, contains('revision != currentRevision'));
  });

  test('private host emits typed map gestures and unregisters listeners', () {
    final mapSource = flutterMap.readAsStringSync();
    final overlaySource = overlay.readAsStringSync();
    final hostSource = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(mapSource, contains('child: GestureDetector('));
    expect(mapSource, contains('_showRoadEventDetail(ev)'));
    expect(mapSource, contains('event is MapEventLongClick'));
    expect(overlaySource, contains('fun hitRoadEvent(tap: LatLng)'));
    expect(hostSource, contains('addOnMapClickListener(mapClickListener)'));
    expect(
      hostSource,
      contains('addOnMapLongClickListener(mapLongClickListener)'),
    );
    expect(hostSource, contains('removeOnMapClickListener(mapClickListener)'));
    expect(
      hostSource,
      contains('removeOnMapLongClickListener(mapLongClickListener)'),
    );
    expect(hostSource, contains('NativeMapInteractionPolicy.tap('));
    expect(shellSource, contains('onMapInteraction = { interaction ->'));
    expect(shellSource, contains('routeSession.selectAlternativeAt('));
    expect(shellSource, isNot(contains('_showRoadEventDetail')));
  });
}
