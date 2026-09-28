import 'native_navigation_bridge.dart';

/// Compile-time rollout boundary for the first production-screen canary.
///
/// Ordinary builds keep this disabled. A controlled build can opt in with
/// `--dart-define=ROADSTR_NATIVE_NAVIGATION=true` without persisting a user
/// setting or changing startup behavior for existing installs.
abstract final class NativeNavigationRollout {
  static const String dartDefine = 'ROADSTR_NATIVE_NAVIGATION';
  static const bool enabledByDefault = bool.fromEnvironment(
    dartDefine,
    defaultValue: false,
  );

  static NativeNavigationOwnership create({
    NativeNavigationBridge bridge = const NativeNavigationBridge(),
    bool enabled = enabledByDefault,
  }) =>
      NativeNavigationOwnership(bridge: bridge, enabled: enabled);
}

/// Owns the native foreground GPS service for one Dart navigation session.
///
/// The owner is disabled by default. When enabled, it is deliberately the
/// only layer that decides whether a start or stop call crosses the bridge;
/// this keeps screen rebuilds, reroutes and duplicate lifecycle callbacks from
/// producing duplicate Android service commands.
class NativeNavigationOwnership {
  NativeNavigationOwnership({
    NativeNavigationBridge bridge = const NativeNavigationBridge(),
    this.enabled = false,
  }) : _bridge = bridge;

  final NativeNavigationBridge _bridge;
  final bool enabled;
  bool _ownsForegroundGps = false;
  bool _disposed = false;

  /// True after the native service has accepted a start for this owner.
  bool get ownsForegroundGps => _ownsForegroundGps;

  /// Serialises starts, stops and disposal so a lifecycle race cannot reorder
  /// platform calls. A failed operation is absorbed only by the queue; the
  /// original error is still returned to its caller.
  Future<void>? _transition;

  /// Starts native GPS once when this owner is explicitly enabled.
  ///
  /// A disabled or disposed owner is a no-op and returns `false`. A platform
  /// failure leaves ownership false, allowing a later explicit retry.
  Future<bool> start() => _run(() async {
        if (!enabled || _disposed || _ownsForegroundGps) {
          return _ownsForegroundGps;
        }
        final started = await _bridge.startForegroundGps();
        if (started) _ownsForegroundGps = true;
        return _ownsForegroundGps;
      });

  /// Stops native GPS only when this owner started it.
  ///
  /// If Android rejects the stop, ownership remains true so the caller can
  /// observe the error and retry instead of silently losing cleanup state.
  Future<void> stop() => _run(() async {
        if (!enabled || !_ownsForegroundGps) return;
        await _bridge.stopForegroundGps();
        _ownsForegroundGps = false;
      });

  /// Reconciles local navigation intent with the Android service runtime.
  ///
  /// A recreated owner adopts an already-running service while navigation is
  /// active, starts it when missing, and stops an orphan when navigation is no
  /// longer active. Failed cleanup keeps ownership true so a later lifecycle
  /// callback can retry it.
  Future<bool> reconcile({required bool navigationActive}) => _run(() async {
        if (!enabled || _disposed) return false;

        final running = await _bridge.isForegroundGpsRunning();
        _ownsForegroundGps = running;
        if (navigationActive) {
          if (running) return true;
          final started = await _bridge.startForegroundGps();
          _ownsForegroundGps = started;
          return started;
        }

        if (running) {
          await _bridge.stopForegroundGps();
          _ownsForegroundGps = false;
        }
        return false;
      });

  /// Releases this owner and makes future starts no-ops.
  Future<void> dispose() => _run(() async {
        if (_disposed) return;
        if (_ownsForegroundGps) {
          await _bridge.stopForegroundGps();
          _ownsForegroundGps = false;
        }
        _disposed = true;
      });

  /// Canary-only start that cannot interrupt the established Flutter journey.
  Future<void> startBestEffort() async {
    try {
      await start();
    } catch (_) {
      // Explicit start remains retryable because ownership was not claimed.
    }
  }

  /// Canary-only stop that retains ownership when Android rejects cleanup.
  Future<void> stopBestEffort() async {
    try {
      await stop();
    } catch (_) {
      // A later stop or dispose can retry the still-owned service.
    }
  }

  /// Canary-only lifecycle reconciliation that cannot interrupt the UI.
  Future<void> reconcileBestEffort({required bool navigationActive}) async {
    try {
      await reconcile(navigationActive: navigationActive);
    } catch (_) {
      // Local ownership remains conservative and a later resume can retry.
    }
  }

  /// Final screen-teardown attempt that never surfaces into widget disposal.
  Future<void> disposeBestEffort() async {
    try {
      await dispose();
    } catch (_) {
      // Android process teardown remains the final service boundary.
    }
  }

  Future<T> _run<T>(Future<T> Function() operation) {
    final previous = _transition;
    final next = _runAfter(previous, operation);
    _transition = _absorb(next);
    return next;
  }

  Future<T> _runAfter<T>(
    Future<void>? previous,
    Future<T> Function() operation,
  ) async {
    if (previous != null) {
      try {
        await previous;
      } catch (_) {
        // The queue absorbs failures; the operation that produced one already
        // delivered it to its own caller.
      }
    }
    return operation();
  }

  Future<void> _absorb<T>(Future<T> operation) async {
    try {
      await operation;
    } catch (_) {
      // Keep the queue usable after a platform failure.
    }
  }
}
