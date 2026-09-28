import 'dart:convert';

import '../models/search_history_item.dart';

/// Deterministic storage and recency policy for confirmed search destinations.
///
/// Hive owns the encrypted persistence transaction. This boundary owns only
/// the value shape shared with native code: tolerant decoding, coordinate
/// deduplication, recency ordering, list bounds and exact JSON strings.
abstract final class SearchHistoryProtocol {
  static const storageKey = 'searchHistory';
  static const maxLoadedItems = 100;
  static const maxStoredItems = 5;
  static const duplicateCoordinateDelta = 0.0001;

  static List<SearchHistoryItem> decodeStored(Object? raw) {
    if (raw is! List) return const [];
    final decoded = <SearchHistoryItem>[];
    for (final value in raw) {
      if (decoded.length == maxLoadedItems) break;
      if (value is! String) continue;
      try {
        final json = jsonDecode(value);
        if (json is! Map<String, dynamic>) continue;
        final item = SearchHistoryItem.fromJsonSafe(json);
        if (item != null) decoded.add(item);
      } catch (_) {
        // Persisted data is untrusted. One malformed row must not hide later
        // valid destinations or prevent the map screen from starting.
      }
    }
    return decoded;
  }

  static List<SearchHistoryItem> prepend(
    SearchHistoryItem item,
    Iterable<SearchHistoryItem> current,
  ) {
    final updated = <SearchHistoryItem>[item];
    for (final existing in current) {
      final samePosition =
          (existing.position.latitude - item.position.latitude).abs() <=
                  duplicateCoordinateDelta &&
              (existing.position.longitude - item.position.longitude).abs() <=
                  duplicateCoordinateDelta;
      if (!samePosition) updated.add(existing);
      if (updated.length == maxStoredItems) break;
    }
    return updated;
  }

  static String encodeItem(SearchHistoryItem item) => jsonEncode(item.toJson());

  static List<String> encodeStored(Iterable<SearchHistoryItem> history) =>
      history.take(maxStoredItems).map(encodeItem).toList(growable: false);
}
