// GPS positioning service for Roadstr.
//
// Wraps the geolocator package to provide a broadcast stream of GpsData
// samples. An Android foreground service notification is shown while the stream
// is active so that the OS does not kill the location listener when the app is
// in the background during active navigation.
import 'dart:async';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

/// A snapshot of the device's GPS state at a given instant.
class GpsData {
  final LatLng position;
  /// Speed in km/h (converted from the native m/s value; clamped to 0 if negative).
  final double speedKmh;
  /// Horizontal accuracy radius in metres; see [isReliable].
  final double accuracy;
  /// Compass bearing in degrees (0–360, clockwise from north). `null` if the
  /// device could not determine heading (e.g. stationary without magnetometer).
  final double? heading;
  final double altitude;
  final DateTime timestamp;

  const GpsData({
    required this.position,
    required this.speedKmh,
    required this.accuracy,
    this.heading,
    required this.altitude,
    required this.timestamp,
  });

  /// Returns `true` when the horizontal accuracy is better than 30 m —
  /// sufficient for turn-by-turn navigation decisions.
  bool get isReliable => accuracy < 30;
}

/// Manages the device GPS subscription and exposes a typed [GpsData] stream.
class GpsService {
  final _controller = StreamController<GpsData>.broadcast();
  Stream<GpsData> get stream => _controller.stream;
  StreamSubscription<Position>? _subscription;
  GpsData? _lastData;
  GpsData? get lastData => _lastData;

  /// Android-specific location settings used by `geolocator`.
  ///
  /// - `distanceFilter: 0` — emit every sample; the app does its own filtering.
  /// - `intervalDuration: 500 ms` — 2 Hz updates balances battery and navigation
  ///   smoothness.
  /// - `foregroundNotificationConfig`: required to keep the location listener
  ///   alive when the screen is off or the app is minimised. `enableWakeLock`
  ///   prevents the CPU from sleeping mid-navigation.
  static final _locationSettings = AndroidSettings(
    accuracy: LocationAccuracy.bestForNavigation,
    distanceFilter: 0,
    intervalDuration: const Duration(milliseconds: 500),
    foregroundNotificationConfig: const ForegroundNotificationConfig(
      notificationText: 'NostrNav is using GPS',
      notificationTitle: 'Navigation active',
      enableWakeLock: true,
    ),
  );

  /// Returns `true` if the device's location service (GPS hardware) is enabled.
  /// Call this before [start] to decide whether to prompt the user.
  Future<bool> isServiceEnabled() => Geolocator.isLocationServiceEnabled();

  /// Opens the Android system location settings screen so the user can enable GPS.
  Future<void> openSettings() => Geolocator.openLocationSettings();

  /// Starts the GPS stream.
  ///
  /// Requests [LocationPermission] if not already granted.
  /// Returns `true` on success, `false` if the service is disabled or permission
  /// was denied/revoked.
  Future<bool> start() async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return false;
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) return false;
    }
    if (permission == LocationPermission.deniedForever) return false;
    _subscription = Geolocator.getPositionStream(
      locationSettings: _locationSettings,
    ).listen(_onPosition, onError: _onError);
    return true;
  }

  void _onPosition(Position pos) {
    // Guard against NaN/Infinity coordinates that some Android devices emit
    // during the initial GPS fix acquisition phase. Passing a NaN value into
    // LatLng would cause an assertion failure (debug) or silent map corruption
    // (release). We discard those readings and wait for a valid fix.
    if (!pos.latitude.isFinite  || !pos.longitude.isFinite  ||
        pos.latitude  < -90 || pos.latitude  > 90 ||
        pos.longitude < -180 || pos.longitude > 180) return;

    final double speedKmh = (pos.speed < 0 ? 0.0 : pos.speed * 3.6).toDouble();
    final data = GpsData(
      position: LatLng(pos.latitude, pos.longitude),
      speedKmh: speedKmh,
      accuracy: pos.accuracy,
      heading: pos.heading >= 0 ? pos.heading : null,
      altitude: pos.altitude,
      timestamp: pos.timestamp,
    );
    _lastData = data;
    _controller.add(data);
  }

  void _onError(Object error) {
    // ignore: avoid_print
    print('GPS error: $error');
  }

  Future<void> stop() async {
    await _subscription?.cancel();
    _subscription = null;
  }

  void dispose() {
    stop();
    _controller.close();
  }
}
