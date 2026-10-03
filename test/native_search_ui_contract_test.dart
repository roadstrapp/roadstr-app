import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_search_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/search/'
    'NativeSearchPresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/search/'
    'NativeSearchOverlay.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 Flutter search translations generate current Android resources',
      () {
    final generated = buildAndroidSearchResources();

    expect(generated, hasLength(27));
    expect(generated.keys.where((path) => path.contains('/values/')),
        hasLength(1));
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = RegExp(
        r'<string name="([^"]+)">([\s\S]*?)</string>',
      ).allMatches(entry.value).toList();
      expect(strings, hasLength(19), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(19));
    }
  });

  test('native search presentation keeps Flutter catalogue and work bounds',
      () {
    final dartPanel =
        File('lib/widgets/search/search_panel.dart').readAsStringSync();
    final dartPoi =
        File('lib/services/poi_search_service.dart').readAsStringSync();
    final dartFavorite =
        File('lib/models/favorite_place.dart').readAsStringSync();
    final kotlin = presentation.readAsStringSync();

    for (final value in <String>[
      "fuel(['amenity=fuel'], '⛽')",
      "restaurant(['amenity=restaurant'], '🍽️')",
      "charging(['amenity=charging_station'], '🔌')",
    ]) {
      expect(dartPoi, contains(value));
    }
    expect(dartPoi, contains('static const _maxNearbyResults = 25'));
    expect(dartFavorite, contains('static const maxStoredItems = 1000'));
    expect(dartPanel, contains('Units.fmtDist(distance)'));
    expect(kotlin, contains('const val MAX_NEARBY_RESULTS = 25'));
    expect(kotlin, contains('const val MAX_FAVORITES = 1_000'));
    expect(kotlin, contains('UnitFormatter(imperial).formatDistance(it)'));
    expect(kotlin, contains('SearchRankingProtocol.MAX_QUERY_LENGTH'));
  });

  test('Compose search overlay exposes bounded accessible panel states', () {
    final source = panel.readAsStringSync();

    for (final value in <String>[
      'NativeSearchUiStatus.Loading',
      'NativeSearchUiStatus.Results',
      'NativeSearchUiStatus.EmptyNearby',
      'NativeSearchNearbyCategory.entries',
    ]) {
      expect(source, contains(value));
    }
    expect(source, contains('.heightIn(max = 460.dp)'));
    expect(source, contains('.imePadding()'));
    expect(source, contains('paneTitle ='));
    expect(source, contains('contentDescription ='));
    expect(source, contains('liveRegion = LiveRegionMode.Polite'));
    expect(source, isNot(contains('NativeSearchService')));
    expect(source, isNot(contains('NativeSearchHistoryStore')));
  });

  test('private shell drives search through an injected provider-neutral gateway',
      () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeSearchSession('));
    expect(source, contains('NativeSearchOverlay('));
    expect(source, contains('journeyGateway: NativeShellJourneyGateway? = null'));
    expect(source, contains('journeyCoordinator?.updateSearchQuery(query)'));
    expect(source, contains('journeyCoordinator?.submitSearch(query, gpsSearchPoint)'));
    expect(source, contains('journeyCoordinator?.submitNearby(category, gpsSearchPoint)'));
    expect(source, isNot(contains('NativeSearchService')));
    expect(source, isNot(contains('NativeSearchHistoryStore')));
  });
}
