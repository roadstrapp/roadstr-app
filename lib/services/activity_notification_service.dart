// Persistent, deliberately silent inbox for the logged-in user's Nostr
// activity. Activity is never posted as an Android notification: while driving,
// no social event may compete with turn-by-turn guidance for attention.
import 'package:hive/hive.dart';

import '../models/activity_notification.dart';

class ActivityNotificationService {
  static const _maxEntries = 100;

  static String storageKey(String pubkey) => 'activity_inbox_$pubkey';

  Box get _box => Hive.box('settings');

  List<ActivityNotification> notificationsFor(String pubkey) {
    final raw = _box.get(storageKey(pubkey), defaultValue: <dynamic>[]);
    if (raw is! List) return const [];
    final items = <ActivityNotification>[];
    for (final value in raw) {
      if (value is! Map) continue;
      final item = ActivityNotification.fromMap(value);
      if (item != null) items.add(item);
    }
    items.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return items;
  }

  int unreadCount(String pubkey) =>
      notificationsFor(pubkey).where((item) => !item.isRead).length;

  /// Stores a verified Nostr activity event without displaying any banner,
  /// overlay, sound or Android system notification.
  Future<void> record(String pubkey, ActivityNotification notification) async {
    final items = notificationsFor(pubkey);
    if (items.any((item) => item.id == notification.id)) return;
    items.insert(0, notification);
    if (items.length > _maxEntries) {
      items.removeRange(_maxEntries, items.length);
    }
    await _box.put(
        storageKey(pubkey), items.map((item) => item.toMap()).toList());
  }

  Future<void> markAllRead(String pubkey) async {
    final items = notificationsFor(pubkey);
    if (!items.any((item) => !item.isRead)) return;
    await _box.put(storageKey(pubkey),
        items.map((item) => item.asRead().toMap()).toList());
  }
}
