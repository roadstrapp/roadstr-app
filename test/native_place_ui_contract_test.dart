import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_place_strings.dart';

void main() {
  final parser = File(
    'android/app/src/main/kotlin/app/roadstr/core/search/'
    'OsmPlaceDetailsProtocol.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/place/'
    'NativePlaceDetailsPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 Flutter place translations generate current Android resources',
      () {
    final generated = buildAndroidPlaceResources();

    expect(generated, hasLength(27));
    expect(
      generated.keys.where((path) => path.contains('/values/')),
      hasLength(1),
    );
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = RegExp(
        r'<string name="([^"]+)">([\s\S]*?)</string>',
      ).allMatches(entry.value).toList();
      expect(strings, hasLength(52), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(52));
      for (final name in <String>[
        'native_place_search_engine',
        'native_place_opens_at',
        'native_place_closes_at',
      ]) {
        expect(
          strings.singleWhere((match) => match.group(1) == name).group(2),
          contains(r'%1$s'),
          reason: '${entry.key}:$name',
        );
      }
    }
  });

  test('native OSM parser preserves Flutter bounds and contextual fields', () {
    final dart =
        File('lib/services/poi_search_service.dart').readAsStringSync();
    final kotlin = parser.readAsStringSync();

    for (final value in <String>[
      "text('description:\$languageCode', 500)",
      "text('opening_hours', 300)",
      "text('contact:website', 500)",
      "'charging_station' => OsmPoiKind.chargingStation",
      "'parking' || 'parking_entrance' => OsmPoiKind.parking",
    ]) {
      expect(dart, contains(value));
    }
    expect(kotlin, contains('text("description:\$safeLanguage", 500)'));
    expect(kotlin, contains('text("opening_hours", 300)'));
    expect(kotlin, contains('text("contact:website", 500)'));
    expect(kotlin, contains('OsmPlaceKind.ChargingStation'));
    expect(kotlin, contains('OsmPlaceKind.Parking'));
    expect(kotlin, contains('uri.scheme == "https"'));
    expect(kotlin, contains('uri.port == -1'));
    expect(kotlin, contains('uri.userInfo == null'));
  });

  test('Compose place sheet is bounded accessible and side effect free', () {
    final source = panel.readAsStringSync();

    for (final value in <String>[
      'NativePlaceUiStatus.Loading',
      'NativePlaceUiStatus.Ready',
      'OsmPlaceKind.Parking',
      'OsmPlaceKind.ChargingStation',
      'PlaceOpeningBadge(',
    ]) {
      expect(source, contains(value));
    }
    expect(source, contains('.heightIn(max = maxHeight * 0.8f)'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('this.paneTitle = paneTitle'));
    expect(source, contains('liveRegion = LiveRegionMode.Polite'));
    expect(source, contains('.sizeIn(minHeight = 48.dp)'));
    expect(source, isNot(contains('NativePlaceService')));
    expect(source, isNot(contains('Intent(')));
    expect(source, isNot(contains('AsyncImage')));
  });

  test('private shell wires place details to the host, not to providers', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativePlaceSession()'));
    expect(source, contains('NativePlaceDetailsPanel('));
    expect(source, contains('onNavigate = {'));
    expect(source, contains('onOpenWebsite = { uri -> onOpenExternal(uri.toString()) }'));
    expect(source, contains('onOpenArticle = { article ->'));
    expect(source, contains('onSearchWeb = { query ->'));
    expect(source, isNot(contains('OsmPlaceDetailsProtocol')));
    expect(source, isNot(contains('NativePlaceService')));
  });
}
