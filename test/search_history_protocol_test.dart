import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/models/search_history_item.dart';
import 'package:roadstr/services/search_history_protocol.dart';

import '../tools/kotlin_rewrite/generate_search_history_fixture.dart'
    as fixture;

SearchHistoryItem _item(String label, double latitude, double longitude) =>
    SearchHistoryItem(label, LatLng(latitude, longitude));

void main() {
  test('shared search history fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildSearchHistoryFixture(),
    );
  });

  test('decoder skips hostile rows and continues to valid history', () {
    final decoded = SearchHistoryProtocol.decodeStored([
      null,
      7,
      '{',
      '[]',
      '{"label":"Bad","lat":91,"lon":9}',
      '{"label":"Casa","lat":45,"lon":9}',
    ]);
    expect(decoded, hasLength(1));
    expect(decoded.single.label, 'Casa');
    expect(decoded.single.position, const LatLng(45, 9));
  });

  test('prepend moves nearby destination to front and retains five items', () {
    final updated = SearchHistoryProtocol.prepend(
      _item('Casa nuova', 45, 9),
      [
        _item('Casa vecchia', 45.00001, 9.00001),
        for (var i = 1; i <= 5; i++) _item('Old $i', i * 1.0, i * 1.0),
      ],
    );
    expect(updated.map((item) => item.label), [
      'Casa nuova',
      'Old 1',
      'Old 2',
      'Old 3',
      'Old 4',
    ]);
  });

  test('encoder preserves the existing Hive JSON shape exactly', () {
    expect(
      SearchHistoryProtocol.encodeStored([
        _item('Caffè 🚗\n"Centro"', -0.0, 1e-7),
      ]),
      ['{"label":"Caffè 🚗\\n\\"Centro\\"","lat":-0.0,"lon":1e-7}'],
    );
  });

  test('storage and input limits remain explicit', () {
    expect(SearchHistoryProtocol.maxLoadedItems, 100);
    expect(SearchHistoryProtocol.maxStoredItems, 5);
    expect(SearchHistoryItem.maxLabelLength, 300);
    expect(SearchHistoryProtocol.duplicateCoordinateDelta, 0.0001);
  });
}
