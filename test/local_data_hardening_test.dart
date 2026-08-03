import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/models/favorite_place.dart';
import 'package:roadstr/models/search_history_item.dart';

void main() {
  test('favorite import rejects oversized remote or file-backed text', () {
    expect(
      FavoritePlace.fromMapSafe({
        'label': 'L' * (FavoritePlace.maxLabelChars + 1),
        'address': 'Via Roma',
        'lat': 45.0,
        'lon': 9.0,
      }),
      isNull,
    );
    expect(
      FavoritePlace.fromMapSafe({
        'label': 'Casa',
        'address': 'A' * (FavoritePlace.maxAddressChars + 1),
        'lat': 45.0,
        'lon': 9.0,
      }),
      isNull,
    );
  });

  test('favorite import trims normal labels and addresses', () {
    final favorite = FavoritePlace.fromMapSafe({
      'label': '  Casa  ',
      'address': '  Via Roma  ',
      'lat': 45.0,
      'lon': 9.0,
    });
    expect(favorite?.label, 'Casa');
    expect(favorite?.address, 'Via Roma');
  });

  test('persisted history rejects unbounded labels', () {
    expect(
      SearchHistoryItem.fromJsonSafe({
        'label': 'X' * 301,
        'lat': 45.0,
        'lon': 9.0,
      }),
      isNull,
    );
  });
}
