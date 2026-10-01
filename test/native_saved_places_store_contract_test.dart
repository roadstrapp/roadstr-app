import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final store = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativeSavedPlacesStore.kt',
  );
  final snapshot = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'NativeSnapshotStore.kt',
  );
  final marker = File(
    'android/app/src/main/kotlin/app/roadstr/storage/'
    'FileNativePersistence.kt',
  );

  test('native saved-place import preserves Flutter Hive shapes', () {
    final model = File('lib/models/favorite_place.dart').readAsStringSync();
    final mapLibre =
        File('lib/screens/maplibre_map_screen.dart').readAsStringSync();
    final native = store.readAsStringSync();

    expect(model, contains("stored in Hive under key\n/// 'favorites'"));
    expect(mapLibre, contains("get('favorites', defaultValue: <dynamic>[]"));
    expect(
      mapLibre,
      contains("_favorites.map((f) => jsonEncode(f.toMap())).toList()"),
    );
    expect(mapLibre, contains("get('parking_position') as String?"));
    expect(mapLibre, contains("'ts': DateTime.now().millisecondsSinceEpoch"));
    expect(native, contains('NativeSavedPlacesProtocol.decodeStoredFavorites'));
    expect(native, contains('NativeSavedPlacesProtocol.decodeParking'));
    expect(native, contains('MAX_STORED_ITEMS'));
  });

  test('saved places use an independent encrypted recoverable file', () {
    final source = store.readAsStringSync();

    expect(source, contains('"saved_places_v1.bin"'));
    expect(source, contains('"RSTRSAV1"'));
    expect(source, contains('"RSTRSVG1"'));
    expect(source, contains('"roadstr-native-saved-places-v1"'));
    expect(source, contains('AndroidKeystoreAead(keyAlias)'));
    expect(source, contains('RecoverableAtomicFile('));
    expect(source, contains('file.stage('));
    expect(source, contains('file.commit()'));
    expect(source, contains('Native saved-places reopen verification failed'));
    expect(source, contains('store is not initialized'));
    expect(source, isNot(contains('Hive.')));
    expect(source, isNot(contains('SharedPreferences')));
  });

  test('migration excludes coordinates publicly and binds saved ciphertext',
      () {
    final snapshotSource = snapshot.readAsStringSync();
    final markerSource = marker.readAsStringSync();
    final runtime = File(
      'android/app/src/main/kotlin/app/roadstr/migration/'
      'NativeMigrationRuntime.kt',
    ).readAsStringSync();

    expect(snapshotSource, contains('it != SAVED_FAVORITES_KEY'));
    expect(snapshotSource, contains('it != SAVED_PARKING_KEY'));
    expect(snapshotSource, contains('savedPlacesStore?.stageLegacy'));
    expect(snapshotSource, contains('savedPlacesStore?.verifyLegacy'));
    expect(markerSource, contains('"RSTRMIG3"'));
    expect(markerSource,
        contains('savedPlacesVerifier?.committedCiphertextDigest()'));
    expect(markerSource, contains('SAVED_PLACES_DIGEST_DOMAIN'));
    expect(runtime, contains('FileNativeSavedPlacesStore(paths)'));
    expect(runtime, contains('savedPlacesVerifier = savedPlacesStore'));
  });

  test('saved-place persistence remains dormant outside migration', () {
    final shell = File(
      'android/app/src/main/kotlin/app/roadstr/feature/home/'
      'NativeRoadstrShell.kt',
    ).readAsStringSync();
    final activity = File(
      'android/app/src/main/kotlin/app/roadstr/MainActivity.kt',
    ).readAsStringSync();

    expect(shell, isNot(contains('FileNativeSavedPlacesStore')));
    expect(activity, isNot(contains('FileNativeSavedPlacesStore')));
    expect(activity, isNot(contains('NATIVE_SAVED_PLACES_FILE')));
  });
}
