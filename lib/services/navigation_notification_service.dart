// Navigation turn-by-turn notification service for Roadstr.
//
// Shows a persistent Android notification in the notification drawer while
// the user is navigating. The notification displays the current maneuver
// instruction as the title and the distance to the next maneuver as the body.
//
// Design decisions:
//   - Importance.low / Priority.low: avoids playing a sound or vibrating
//     the device on every step update, which would be extremely annoying while
//     driving.
//   - ongoing: true: marks the notification as non-dismissible by the user.
//     The OS also uses this flag to prevent the notification from being cleared
//     by "Clear all", mirroring the behaviour of Google Maps and Waze.
//   - Lazy initialisation via _ensureInit: avoids doing I/O during the
//     service constructor, keeping startup fast.
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Manages the persistent navigation notification shown in the Android
/// notification shade during active turn-by-turn navigation.
class NavigationNotificationService {
  static const _channelId = 'roadstr_navigation';
  static const _channelName = 'Navigation';

  /// Fixed notification ID — reusing the same ID causes [show] to update the
  /// existing notification in place rather than posting a new one each step.
  static const _notifId = 42;

  final _plugin = FlutterLocalNotificationsPlugin();
  bool _initialized = false;

  /// Initializes the plugin on first use (lazy). Safe to call repeatedly.
  Future<void> _ensureInit() async {
    if (_initialized) return;
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
    await _plugin.initialize(
      settings: const InitializationSettings(android: android),
    );
    _initialized = true;
  }

  /// Shows or updates the navigation notification.
  ///
  /// [instruction]: the current maneuver description (e.g. "Turn right on Main St.").
  /// [distance]: human-readable distance to the next maneuver (e.g. "200 m").
  Future<void> show(String instruction, String distance) async {
    await _ensureInit();
    await _plugin.show(
      id: _notifId,
      title: instruction,
      body: distance,
      notificationDetails: NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId, _channelName,
          // Low importance = silent update; no sound/vibration on each step.
          importance: Importance.low,
          priority: Priority.low,
          // ongoing = non-dismissible; remains in the shade until navigation stops.
          ongoing: true,
          onlyAlertOnce: true,
          autoCancel: false,
          // Hide street names and maneuver text on a locked device.
          visibility: NotificationVisibility.private,
          icon: '@mipmap/ic_launcher',
        ),
      ),
    );
  }

  /// Dismisses the navigation notification. Called when the user stops navigation.
  Future<void> cancel() async {
    if (!_initialized) return;
    await _plugin.cancel(id: _notifId);
  }
}
