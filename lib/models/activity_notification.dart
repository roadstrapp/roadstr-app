import 'road_event.dart';

/// A locally-detected event about the user's own Nostr activity: someone
/// zapped them, or confirmed/disputed one of their road reports. Emitted only
/// while logged in (Amber or nsec) — see [NostrRelayService.enableActivityNotifications].
enum ActivityNotificationType { zap, confirmed, denied }

class ActivityNotification {
  /// Nostr event id. Used to keep reconnects from creating duplicate inbox rows.
  final String id;
  final ActivityNotificationType type;

  /// Unix timestamp (seconds) from the verified Nostr event.
  final int createdAt;

  final bool isRead;

  /// Zap amount in satoshi. Only set for [ActivityNotificationType.zap].
  final int? amountSat;

  /// Category of the user's own report that was confirmed/denied. Only set
  /// for [ActivityNotificationType.confirmed]/[ActivityNotificationType.denied].
  final RoadCategory? category;

  const ActivityNotification._(
    this.id,
    this.type,
    this.createdAt, {
    this.amountSat,
    this.category,
    this.isRead = false,
  });

  const ActivityNotification.zap({
    required String id,
    required int createdAt,
    required int amountSat,
    bool isRead = false,
  }) : this._(id, ActivityNotificationType.zap, createdAt,
            amountSat: amountSat, isRead: isRead);

  const ActivityNotification.confirmed({
    required String id,
    required int createdAt,
    required RoadCategory category,
    bool isRead = false,
  }) : this._(id, ActivityNotificationType.confirmed, createdAt,
            category: category, isRead: isRead);

  const ActivityNotification.denied({
    required String id,
    required int createdAt,
    required RoadCategory category,
    bool isRead = false,
  }) : this._(id, ActivityNotificationType.denied, createdAt,
            category: category, isRead: isRead);

  ActivityNotification asRead() => ActivityNotification._(
        id,
        type,
        createdAt,
        amountSat: amountSat,
        category: category,
        isRead: true,
      );

  Map<String, dynamic> toMap() => {
        'id': id,
        'type': type.name,
        'createdAt': createdAt,
        'isRead': isRead,
        if (amountSat != null) 'amountSat': amountSat,
        if (category != null) 'category': category!.nostrKey,
      };

  static ActivityNotification? fromMap(Map<dynamic, dynamic> map) {
    final id = map['id'];
    final typeName = map['type'];
    final createdAt = map['createdAt'];
    if (id is! String || typeName is! String || createdAt is! int) return null;
    final isRead = map['isRead'] == true;
    switch (typeName) {
      case 'zap':
        final amount = map['amountSat'];
        if (amount is! int) return null;
        return ActivityNotification.zap(
            id: id, createdAt: createdAt, amountSat: amount, isRead: isRead);
      case 'confirmed':
      case 'denied':
        final rawCategory = map['category'];
        if (rawCategory is! String) return null;
        final category = RoadCategory.fromKey(rawCategory);
        return typeName == 'confirmed'
            ? ActivityNotification.confirmed(
                id: id,
                createdAt: createdAt,
                category: category,
                isRead: isRead)
            : ActivityNotification.denied(
                id: id,
                createdAt: createdAt,
                category: category,
                isRead: isRead);
      default:
        return null;
    }
  }
}
