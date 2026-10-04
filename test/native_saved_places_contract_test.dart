import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_saved_places_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/saved/'
    'NativeSavedPlacesPresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/saved/'
    'NativeSavedPlacesPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 saved-place translations generate current Android resources',
      () {
    final generated = buildAndroidSavedPlacesResources();

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
      expect(strings, hasLength(9), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(9));
      expect(_value(strings, 'native_saved_imported'), contains(r'%1$d'));
    }
  });

  test('native protocol preserves Flutter favorite and parking contracts', () {
    final model = File('lib/models/favorite_place.dart').readAsStringSync();
    final settings =
        File('lib/screens/settings_screen.dart').readAsStringSync();
    final map = File('lib/screens/maplibre_map_screen.dart').readAsStringSync();
    final kotlin = presentation.readAsStringSync();

    expect(model, contains('static const maxLabelChars = 200'));
    expect(model, contains('static const maxAddressChars = 500'));
    expect(model, contains('static const maxStoredItems = 1000'));
    expect(settings, contains('_maxFavoritesImportBytes = 5 * 1024 * 1024'));
    expect(
        settings,
        contains(
            "final dup = _favorites.indexWhere((e) => e.label == f.label)"));
    expect(map, contains("get('parking_position')"));
    expect(map, contains("'ts': DateTime.now().millisecondsSinceEpoch"));

    expect(kotlin, contains('const val MAX_LABEL_CHARS = 200'));
    expect(kotlin, contains('const val MAX_ADDRESS_CHARS = 500'));
    expect(kotlin, contains('const val MAX_STORED_ITEMS = 1_000'));
    expect(kotlin, contains('const val MAX_IMPORT_BYTES = 5 * 1024 * 1024'));
    expect(kotlin, contains('it.label == value.label'));
    expect(kotlin, contains('NativeMapPointOverlayKind.Parking'));
    expect(kotlin, contains('BoundedJsonParser(raw).parse()'));
  });

  test('saved-place panel is bounded accessible and side-effect free', () {
    final source = panel.readAsStringSync();
    final state = presentation.readAsStringSync();

    expect(source, contains('LazyColumn'));
    expect(source, contains('heightIn(max = maxHeight * 0.82f)'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle = title'));
    expect(source, contains('liveRegion = LiveRegionMode.Polite'));
    expect(source, contains('.sizeIn(minWidth = 48.dp, minHeight = 48.dp)'));
    expect(source, contains('onNavigateParking'));
    expect(source, isNot(contains('FilePicker')));
    expect(source, isNot(contains('Intent(')));
    expect(source, isNot(contains('Hive')));
    expect(
      state,
      contains('with no persistence, picker, route or sync adapter'),
    );
    expect(state, isNot(contains('NativePublicSnapshotStore')));
    expect(state, isNot(contains('FavoritesSyncService')));
  });

  test('private shell wires saved places to the host, not to storage', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeSavedPlacesSession()'));
    expect(source, contains('NativeSavedPlacesPanel('));
    expect(source, contains('onExport = { nostrHost?.exportFavorites() }'));
    expect(source, contains('onImport = { nostrHost?.requestImport() }'));
    expect(source, contains('onNavigateParking = { parking ->'));
    expect(source, isNot(contains("Hive.box('settings')")));
    expect(source, isNot(contains('FilePicker')));
  });
}

String? _value(List<RegExpMatch> values, String name) =>
    values.singleWhere((match) => match.group(1) == name).group(2);
