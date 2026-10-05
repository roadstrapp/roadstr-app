import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

String _read(String path) =>
    File('android/app/src/main/kotlin/app/roadstr/$path').readAsStringSync();

void main() {
  final shell = _read('feature/home/NativeRoadstrShell.kt');
  final parking = _read('feature/saved/NativeParkingPanel.kt');

  test('the parking action opens a parking-only panel, not saved places', () {
    final action = RegExp(
      r'NativeHomeAction\.Parking -> \{(.*?)\n\s*\}',
      dotAll: true,
    ).firstMatch(shell)!.group(1)!;

    expect(action, contains('parkingPanelVisible = true'));
    expect(action, isNot(contains('savedPlacesSession')));
    expect(parking, isNot(contains('favorites')));
    expect(parking, contains('native_parking_save_here'));
    expect(parking, contains('native_saved_parking_navigate'));
    expect(parking, contains('native_saved_parking_remove'));
  });

  test('a long press asks what to do instead of silently parking', () {
    final longPress = RegExp(
      r'is NativeMapInteraction\.MapLongPress -> \{(.*?)\n\s*\}',
      dotAll: true,
    ).firstMatch(shell)!.group(1)!;

    expect(longPress, contains('contextMenuPoint = interaction.point'));
    expect(longPress, isNot(contains('onParkingChanged')));
    expect(parking, contains('fun NativeMapContextMenu'));
    expect(parking, contains('native_map_whats_here'));
    expect(shell, contains('onWhatsHere'));
  });

  test('"what is here" and a tap share one inspection with Wikipedia', () {
    expect(shell, contains('val inspectPlace: (NativeMapPoint) -> Unit'));
    expect(shell, contains('inspectPlace(interaction.point)'));
    expect(shell, contains('inspectPlace(point)'));
    final inspect = RegExp(
      r'val inspectPlace: \(NativeMapPoint\) -> Unit = \{(.*?)\n        \}\n',
      dotAll: true,
    ).firstMatch(shell)!.group(1)!;
    expect(inspect, contains('reverseGeocode'));
    expect(inspect, contains('wikipediaArticle'));
    expect(inspect, contains('detail.poiName != null'));
    expect(inspect, contains('openingHours = detail?.openingHours'));
  });

  test('back closes the parking panel and the context menu first', () {
    expect(shell, contains('contextMenuPoint != null ->'));
    expect(shell, contains('parkingPanelVisible ->'));
  });

  test('sheets close by dragging their handle down', () {
    final drag = _read('core/ui/SheetDragToDismiss.kt');
    final events = _read('feature/report/NativeRoadEventPanels.kt');

    expect(drag, contains('detectVerticalDragGestures'));
    expect(
      RegExp('SheetGrabHandle\\(drag.handle\\)').allMatches(events).length,
      2,
    );
    expect(
      RegExp(r'\.then\(drag\.sheet\)').allMatches(events).length,
      2,
    );
    expect(parking, contains('SheetGrabHandle(drag.handle)'));
  });

  test('Activity opens the route history; Notifications keeps the inbox', () {
    final activity = RegExp(
      r'NativeHomeAction\.Activity -> \{(.*?)\n\s*\}',
      dotAll: true,
    ).firstMatch(shell)!.group(1)!;
    final notifications = RegExp(
      r'NativeHomeAction\.Notifications -> \{(.*?)\n\s*\}\n',
      dotAll: true,
    ).firstMatch(shell)!.group(1)!;

    expect(activity, contains('historyVisible = true'));
    expect(activity, isNot(contains('activityInboxSession')));
    expect(notifications, contains('activityInboxSession'));
  });

  test('a started route is recorded and a history row acts like a map tap', () {
    final history = _read('feature/history/NativeRouteHistoryPanel.kt');

    expect(shell, contains('NativeRouteHistoryProtocol.record('));
    expect(shell, contains('inspectPlace(entry.point)'));
    expect(shell, contains('cameraSession.focus(entry.point'));
    expect(shell, contains('NativeRouteHistoryProtocol.remove('));
    expect(shell, contains('onClear = { updateRouteHistory(emptyList()) }'));
    expect(history, contains('NATIVE_ROUTE_HISTORY_VISIBLE_ROWS = 5'));
    expect(history, contains('native_history_clear'));
    expect(history, contains('native_history_remove'));
    expect(history, contains('SheetGrabHandle(drag.handle)'));
  });

  test('route history never leaves the device unencrypted', () {
    final host = File(
      'native-android/app/src/main/kotlin/app/roadstr/roadtest/'
      'NativeRoadTestActivity.kt',
    ).readAsStringSync();
    final protocol = _read('feature/history/NativeRouteHistory.kt');

    expect(host, contains('"roadtest_route_history"'));
    expect(host, contains('NativeRoadTestProtectedPreferences('));
    expect(host, contains('historyPreferences.write("routes"'));
    expect(host, isNot(contains('putString("routes"')));
    expect(protocol, isNot(contains('Log.')));
    expect(protocol, isNot(contains('File(')));
    expect(protocol, isNot(contains('SharedPreferences')));
  });
}
