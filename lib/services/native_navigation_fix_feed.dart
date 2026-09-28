import 'dart:async';

import 'package:flutter/services.dart';

/// Stable EventChannel contract for normalized fixes from the native service.
abstract final class NativeNavigationFixContract {
  static const String channelName = 'app.roadstr/native_navigation_fixes';
  static const String latitude = 'latitude';
  static const String longitude = 'longitude';
  static const String speedKmh = 'speedKmh';
  static const String accuracy = 'accuracy';
  static const String heading = 'heading';
  static const String altitude = 'altitude';
  static const String timestampMillis = 'timestampMillis';
}

/// Normalized, value-only fix emitted by the opt-in native GPS service.
class NativeNavigationFix {
  const NativeNavigationFix({
    required this.latitude,
    required this.longitude,
    required this.speedKmh,
    required this.accuracy,
    required this.heading,
    required this.altitude,
    required this.timestamp,
  });

  final double latitude;
  final double longitude;
  final double speedKmh;
  final double accuracy;
  final double? heading;
  final double altitude;
  final DateTime timestamp;

  bool get isReliable => accuracy < 30;

  factory NativeNavigationFix.fromEvent(Object? event) {
    if (event is! Map<Object?, Object?>) {
      throw const FormatException('Native navigation fix must be a map');
    }

    final latitude = _finiteDouble(event, NativeNavigationFixContract.latitude);
    final longitude =
        _finiteDouble(event, NativeNavigationFixContract.longitude);
    final speedKmh = _finiteDouble(event, NativeNavigationFixContract.speedKmh);
    final accuracy = _number(event, NativeNavigationFixContract.accuracy);
    final altitude = _finiteDouble(event, NativeNavigationFixContract.altitude);
    final rawHeading = event[NativeNavigationFixContract.heading];
    final heading = rawHeading == null
        ? null
        : _finiteDouble(event, NativeNavigationFixContract.heading);
    final timestampMillis = event[NativeNavigationFixContract.timestampMillis];

    if (latitude < -90 || latitude > 90) {
      throw const FormatException('Native navigation latitude is invalid');
    }
    if (longitude < -180 || longitude > 180) {
      throw const FormatException('Native navigation longitude is invalid');
    }
    if (speedKmh < 0) {
      throw const FormatException('Native navigation speed is invalid');
    }
    if (accuracy.isNaN || accuracy < 0) {
      throw const FormatException('Native navigation accuracy is invalid');
    }
    if (heading != null && heading < 0) {
      throw const FormatException('Native navigation heading is invalid');
    }
    if (timestampMillis is! int || timestampMillis < 0) {
      throw const FormatException('Native navigation timestamp is invalid');
    }

    return NativeNavigationFix(
      latitude: latitude,
      longitude: longitude,
      speedKmh: speedKmh,
      accuracy: accuracy,
      heading: heading,
      altitude: altitude,
      timestamp: DateTime.fromMillisecondsSinceEpoch(
        timestampMillis,
        isUtc: true,
      ),
    );
  }

  static double _number(Map<Object?, Object?> event, String key) {
    final value = event[key];
    if (value is! num) {
      throw FormatException('Native navigation $key is missing');
    }
    return value.toDouble();
  }

  static double _finiteDouble(Map<Object?, Object?> event, String key) {
    final value = _number(event, key);
    if (!value.isFinite) {
      throw FormatException('Native navigation $key is not finite');
    }
    return value;
  }
}

abstract interface class NativeNavigationFixSource {
  Stream<NativeNavigationFix> get fixes;
}

/// Typed Dart adapter for the native navigation EventChannel.
class NativeNavigationFixBridge implements NativeNavigationFixSource {
  const NativeNavigationFixBridge({
    EventChannel channel = const EventChannel(
      NativeNavigationFixContract.channelName,
    ),
  }) : _channel = channel;

  final EventChannel _channel;

  @override
  Stream<NativeNavigationFix> get fixes =>
      _channel.receiveBroadcastStream().map(NativeNavigationFix.fromEvent);
}

/// Silent canary consumer; it never drives production map or navigation state.
class NativeNavigationFixFeed {
  NativeNavigationFixFeed({
    NativeNavigationFixSource source = const NativeNavigationFixBridge(),
    this.enabled = false,
  }) : _source = source;

  final NativeNavigationFixSource _source;
  final bool enabled;
  StreamSubscription<NativeNavigationFix>? _subscription;
  bool _disposed = false;
  NativeNavigationFix? _latestFix;
  int _receivedFixCount = 0;
  int _streamErrorCount = 0;

  NativeNavigationFix? get latestFix => _latestFix;
  int get receivedFixCount => _receivedFixCount;
  int get streamErrorCount => _streamErrorCount;

  /// Starts one shadow subscription only when the compile-time canary is on.
  void start() {
    if (!enabled || _disposed || _subscription != null) return;
    try {
      _subscription = _source.fixes.listen(
        (fix) {
          if (_disposed) return;
          _latestFix = fix;
          _receivedFixCount++;
        },
        onError: (_) {
          if (!_disposed) _streamErrorCount++;
        },
      );
    } catch (_) {
      _streamErrorCount++;
    }
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    final subscription = _subscription;
    _subscription = null;
    _latestFix = null;
    try {
      await subscription?.cancel();
    } catch (_) {
      // Engine teardown may race EventChannel cancellation. Shadow cleanup
      // must never surface into widget disposal.
      _streamErrorCount++;
    }
  }
}
