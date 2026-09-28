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

import 'native_navigation_bridge.dart';

/// Decides whether a navigation notification update is worth posting.
///
/// The map screens call [NavigationNotificationService.show] on every GPS fix
/// — about twice a second for the whole drive — and the distance label is
/// rounded to the metre, so the text genuinely differs on nearly every call
/// at speed. Posting each one is not free: every `notify()` is a binder call
/// into the system server, a re-render in SystemUI, and a fresh copy pushed to
/// anything mirroring notifications — a smartwatch over Bluetooth, Android
/// Auto — for a number nobody can read that fast on a lock screen.
///
/// A new manoeuvre always goes out at once; only the distance ticking down
/// under an unchanged instruction is rate-limited.
class NotificationThrottle {
  NotificationThrottle({this.minInterval = const Duration(seconds: 3)});

  /// The least time between two updates that differ only in distance.
  final Duration minInterval;

  String? _instruction;
  String? _distance;
  DateTime? _postedAt;

  bool shouldPost(String instruction, String distance, DateTime now) {
    final postedAt = _postedAt;
    if (postedAt == null || instruction != _instruction) {
      return _record(instruction, distance, now);
    }
    if (distance == _distance) return false;
    if (now.difference(postedAt) < minInterval) return false;
    return _record(instruction, distance, now);
  }

  bool _record(String instruction, String distance, DateTime now) {
    _instruction = instruction;
    _distance = distance;
    _postedAt = now;
    return true;
  }

  /// Forget what was last posted, so the first update of the next trip is
  /// never mistaken for a repeat of the last update of this one.
  void reset() {
    _instruction = null;
    _distance = null;
    _postedAt = null;
  }
}

/// Manages the persistent navigation notification shown in the Android
/// notification shade during active turn-by-turn navigation.
class NavigationNotificationService {
  NavigationNotificationService({
    NativeNavigationBridge nativeBridge = const NativeNavigationBridge(),
    bool mirrorNative = false,
  })  : _nativeBridge = nativeBridge,
        _mirrorNative = mirrorNative;

  static const _channelId = 'roadstr_navigation';
  static const _channelName = 'Navigation';

  /// Fixed notification ID — reusing the same ID causes [show] to update the
  /// existing notification in place rather than posting a new one each step.
  static const _notifId = 42;

  final _plugin = FlutterLocalNotificationsPlugin();
  final _throttle = NotificationThrottle();
  final NativeNavigationBridge _nativeBridge;
  final bool _mirrorNative;
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
    if (!_throttle.shouldPost(instruction, distance, DateTime.now())) return;
    Future<void>? nativeUpdate;
    if (_mirrorNative) {
      nativeUpdate = _mirrorNativeUpdate(instruction, distance);
    }
    await _ensureInit();
    await nativeUpdate;
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
    _throttle.reset();
    if (_mirrorNative) {
      await _resetNativeNotification();
    }
    if (!_initialized) return;
    await _plugin.cancel(id: _notifId);
  }

  Future<void> _mirrorNativeUpdate(
    String instruction,
    String distance,
  ) async {
    try {
      await _nativeBridge.updateNavigationNotification(
        instruction: instruction,
        distance: distance,
      );
    } catch (_) {
      // The established Flutter notification remains authoritative.
    }
  }

  Future<void> _resetNativeNotification() async {
    try {
      await _nativeBridge.resetNavigationNotification();
    } catch (_) {
      // Native cleanup is best-effort while the canary remains shadow-only.
    }
  }
}
