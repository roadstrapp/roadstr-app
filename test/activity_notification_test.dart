import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/models/activity_notification.dart';
import 'package:roadstr/models/road_event.dart';

void main() {
  group('ActivityNotification persistence', () {
    test('round-trips a zap and its read state', () {
      const original = ActivityNotification.zap(
        id: 'zap-event',
        createdAt: 1234,
        amountSat: 21,
        isRead: true,
      );

      final decoded = ActivityNotification.fromMap(original.toMap());

      expect(decoded, isNotNull);
      expect(decoded!.id, 'zap-event');
      expect(decoded.type, ActivityNotificationType.zap);
      expect(decoded.amountSat, 21);
      expect(decoded.createdAt, 1234);
      expect(decoded.isRead, isTrue);
    });

    test('round-trips a report reaction and can mark it read', () {
      const original = ActivityNotification.confirmed(
        id: 'confirmation-event',
        createdAt: 5678,
        category: RoadCategory.roadClosure,
      );

      final decoded = ActivityNotification.fromMap(original.toMap())!.asRead();

      expect(decoded.type, ActivityNotificationType.confirmed);
      expect(decoded.category, RoadCategory.roadClosure);
      expect(decoded.isRead, isTrue);
    });

    test('rejects malformed stored values', () {
      expect(ActivityNotification.fromMap({'type': 'zap'}), isNull);
      expect(
        ActivityNotification.fromMap(
            {'id': 'x', 'type': 'unknown', 'createdAt': 1}),
        isNull,
      );
    });
  });
}
