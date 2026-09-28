import 'package:flutter/services.dart';

/// Stable Dart-side contract for the opt-in native navigation bridge.
///
/// The bridge remains separate from [GpsService]. The opt-in screen owner
/// explicitly decides when the native foreground service becomes part of a
/// navigation session. Android [PlatformException] values are left intact so
/// permission and start failures remain observable to that caller.
abstract final class NativeNavigationBridgeContract {
  static const String channelName = 'app.roadstr/native_navigation';
  static const String startForegroundGps = 'startForegroundGps';
  static const String stopForegroundGps = 'stopForegroundGps';
  static const String isForegroundGpsRunning = 'isForegroundGpsRunning';
  static const String permissionDeniedCode = 'permission_denied';
  static const String startFailedCode = 'start_failed';
}

/// Explicit caller for the Android native GPS foreground service canary.
///
/// Supplying a channel makes the boundary deterministic in tests and keeps
/// the platform transport replaceable when the ViewModel migration begins.
class NativeNavigationBridge {
  const NativeNavigationBridge({
    MethodChannel channel = const MethodChannel(
      NativeNavigationBridgeContract.channelName,
    ),
  }) : _channel = channel;

  final MethodChannel _channel;

  /// Starts native foreground GPS after Android permission has been granted.
  ///
  /// Returns `false` only if a platform implementation returns no result or
  /// an explicit false value. Android failures are surfaced as-is.
  Future<bool> startForegroundGps() async {
    final started = await _channel.invokeMethod<bool>(
      NativeNavigationBridgeContract.startForegroundGps,
    );
    return started ?? false;
  }

  /// Stops the native foreground GPS service.
  Future<void> stopForegroundGps() async {
    await _channel.invokeMethod<void>(
      NativeNavigationBridgeContract.stopForegroundGps,
    );
  }

  /// Reports whether this Android process currently owns foreground GPS.
  Future<bool> isForegroundGpsRunning() async {
    final running = await _channel.invokeMethod<bool>(
      NativeNavigationBridgeContract.isForegroundGpsRunning,
    );
    return running ?? false;
  }
}
