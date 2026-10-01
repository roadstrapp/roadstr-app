import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_home_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeHomePresentation.kt',
  );
  final chrome = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/NativeHomeChrome.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 home resource sets are current and complete', () {
    final generated = buildAndroidHomeResources();

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
      expect(strings, hasLength(10), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(10));
    }
  });

  test('native visibility and bounds preserve both Flutter map oracles', () {
    final raster = File('lib/screens/map_screen.dart').readAsStringSync();
    final mapLibre =
        File('lib/screens/maplibre_map_screen.dart').readAsStringSync();
    final dashboard =
        File('lib/widgets/home/home_dashboard.dart').readAsStringSync();
    final bottomBar = File('lib/widgets/map/map_chrome.dart').readAsStringSync();
    final native = presentation.readAsStringSync();

    for (final source in [raster, mapLibre]) {
      expect(source, contains('!_isNavigating'));
      expect(source, contains('!_showSearch'));
      expect(source, contains('!_showPlanner'));
      expect(source, contains('!_showAlternatives'));
      expect(source, contains('!_showTransit'));
    }
    expect(raster, contains('!_showPreview'));
    expect(raster, contains('!_isCalculating'));
    expect(mapLibre, contains('!_showPlaceInfo'));
    expect(mapLibre, contains('_route == null'));
    expect(dashboard, contains('itemCount: widget.favorites.length.clamp(0, 5)'));
    expect(bottomBar, contains("unread > 99 ? '99+' : '\$unread'"));

    expect(native, contains('MAX_VISIBLE_FAVORITES = 5'));
    expect(native, contains('MAX_UNREAD_COUNT = 100'));
    expect(native, contains('!input.navigating'));
    expect(native, contains('!input.searchVisible'));
    expect(native, contains('!input.previewVisible'));
    expect(native, contains('!input.calculating'));
    expect(native, contains('!input.hasRoute'));
  });

  test('Compose home chrome is bounded accessible and callback only', () {
    final source = chrome.readAsStringSync();

    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('widthIn(max = 640.dp)'));
    expect(source, contains('heightIn(min = 48.dp)'));
    expect(source, contains('AnimatedVisibility('));
    expect(source, contains('LazyRow('));
    expect(source, contains('Role.Button'));
    expect(source, contains('contentDescription ='));
    expect(source, contains('NativeHomeAction.Notifications'));
    expect(source, contains('NativeHomeAction.Profile'));
    expect(source, contains('NativeHomeAction.Menu'));
    expect(source, isNot(contains('Hive.')));
    expect(source, isNot(contains('LocationManager')));
    expect(source, isNot(contains('NostrRelayService')));
    expect(source, isNot(contains('NotificationManager')));
    expect(source, isNot(contains('Image.network')));
  });

  test('private shell packages home chrome without feeding product data', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeHomeSession()'));
    expect(source, contains('NativeHomeChrome('));
    expect(source, contains('homeSession::toggleExpanded'));
    expect(source, contains('homeSession.action(revision, action)'));
    expect(source, contains('homeSession.selectFavorite(revision, id)'));
    expect(source, isNot(contains('homeSession.replace(')));
  });
}
